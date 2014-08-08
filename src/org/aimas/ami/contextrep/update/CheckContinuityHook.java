package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.utils.ContextAnnotationUtil;
import org.aimas.ami.contextrep.engine.utils.ContextUpdateUtil;
import org.aimas.ami.contextrep.model.ContextAnnotation;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.StructuredAnnotation;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;
import org.aimas.ami.contextrep.vocabulary.ConsertFunctions;
import org.openjena.atlas.lib.Pair;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.arq.SPINARQFunction;
import org.topbraid.spin.model.Select;
import org.topbraid.spin.model.Template;
import org.topbraid.spin.system.SPINModuleRegistry;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.TypeMapper;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;
import com.hp.hpl.jena.sparql.function.FunctionRegistry;
import com.hp.hpl.jena.tdb.TDB;

public class CheckContinuityHook extends ContextUpdateHook {
	
	public CheckContinuityHook(ContextAssertion contextAssertion, Node contextAssertionUUID) {
		super(contextAssertion, contextAssertionUUID);
	}
	
	@Override
	public ContinuityResult exec(Dataset contextStoreDataset) {
		long start = Engine.currentTimeMillis();
		
		System.out.println("======== CHECKING CONTINUITY AVAILABALE FOR assertion <" + contextAssertion.getOntologyResource().getLocalName() + ">. "
		        + "with new AssertionUUID: " + contextAssertionUUID);
		
		// get context assertion store URI
		String assertionStoreURI = contextAssertion.getAssertionStoreURI();
		Resource newAssertionUUIDRes = ResourceFactory.createResource(contextAssertionUUID.getURI());
		
		
		// get the contextAssertion store model and the validityAnnotation
		Model assertionStoreModel = contextStoreDataset.getNamedModel(assertionStoreURI);
		RDFNode newValidityPeriod = 
			ContextAnnotationUtil.getStructuredAnnotationValue(ConsertAnnotation.HAS_VALIDITY, newAssertionUUIDRes, assertionStoreModel);
		
		// create a list of available (assertionUUID, assertionValidity) pairs 
		// that marks whose validity period can be extended with the current one 
		// (because the contents of the assertions is the same as that of newAssertionUUID)
		List<ContinuityWrapper> availableContinuityPairs = new ArrayList<>();
		
		if (newValidityPeriod != null) {
			Template closeEnoughValidity = SPINModuleRegistry.get().getTemplate(ConsertFunctions.CLOSE_ENOUGH_VALIDITY_TEMPLATE.getURI(), null);
			
			// We use the CLOSE_ENOUGH_VALIDITY_TEMPLATE query to get a list of ContextAssertion identifiers
			// which have the same CONTENT and the same SOURCE as the newly inserted one and which lie
			// within the acceptable VALIDITY INTERVAL DISTANCE one from another
			com.hp.hpl.jena.query.Query arq = ARQFactory.get().createQuery((Select) closeEnoughValidity.getBody());
			
			// arq.setPrefix("contextassertion:",
			// ContextAssertionVocabulary.NS);
			// arq.setPrefix("functions:",
			// ContextAssertionVocabulary.FUNCTIONS_NS);
			// arq.setBaseURI(Config.getContextModelNamespace());
			QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, contextStoreDataset);
			
			// set the value of the arguments
			QuerySolutionMap arqBindings = new QuerySolutionMap();
			arqBindings.add("contextAssertionResource", ResourceFactory.createResource(contextAssertion.getOntologyResource().getURI()));
			arqBindings.add("contextAssertionStore", ResourceFactory.createResource(assertionStoreURI));
			arqBindings.add("newAssertionUUID", ResourceFactory.createResource(contextAssertionUUID.getURI()));
			arqBindings.add("newValidityPeriod", newValidityPeriod);
			
			qexec.setInitialBinding(arqBindings); // Pre-assign the required
												  // arguments
			
			// qexec.getContext().set(ARQ.symLogExec, Explain.InfoLevel.FINE) ;
			try {
				ResultSet rs = qexec.execSelect();
				
				/*
				 * we will now go through the results and make a list of the
				 * pairs (assertionUUID, assertionValidity) which can be
				 * extended with the current (newAssertionUUID, newValidityPeriod)
				 */
				while (rs.hasNext()) {
					QuerySolution qs = rs.next();
					RDFNode assertionUUID = qs.get("assertionUUID");
					RDFNode validity = qs.get("validity");
					
					//System.out.println("CONTINUITY AVAILABALE FOR assertion <" + contextAssertion + ">. "
					//        + "AssertionUUID: " + assertionUUID
					//        + ", for duration: " + validity);
					
					availableContinuityPairs.add(new ContinuityWrapper(assertionUUID, validity));
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
				
				return new ContinuityResult(contextAssertion, ex, false);
				// TODO: performance collect
				//long end = System.currentTimeMillis();
				//return new ContinuityResult(start, (int)(end - start), true, false);
				
			}
			finally {
				qexec.close();
			}
		}
		
		if (!availableContinuityPairs.isEmpty()) {
			// Now that we have ContextAssertions that we can extend from a content and temporal point of view, 
			// let us see if they pass the `permits continuity test' for each structured annotation that they may have 
			System.out.println("======== THERE ARE " + availableContinuityPairs.size() + " candidates for CONTINUITY for <" + contextAssertion.getOntologyResource().getLocalName() + ">. "
					+ "new AssertionUUID: " + contextAssertionUUID);
			
			
			
			// If all get continuity candidates pass the annotation tests, then at the end we can remove the newly inserted triples
			boolean allUpdated = true;
			
			Pair<Map<ContextAnnotation, Pair<Statement, Set<Statement>>>, Map<StructuredAnnotation, NodeValue>> newAssertionAnnotations = 
				analyzeNewAssertionAnnotations(newAssertionUUIDRes, assertionStoreModel);
			
			// We create this mapping because the basic annotations involve copying over entire portions of annotation content
			// Since at the end we delete the newly inserted content, we save this part before deleting and re-assert only the
			// required basic annotation content
			Map<Resource, Map<ContextAnnotation, Pair<Boolean, Pair<Statement, Set<Statement>>>>> basicAnnotationRevisionMap = 
				new HashMap<Resource, Map<ContextAnnotation,Pair<Boolean,Pair<Statement,Set<Statement>>>>>();
			
			for (ContinuityWrapper pairWrapper : availableContinuityPairs) {
				Resource extendibleAssertionUUIDRes = ResourceFactory.createResource(pairWrapper.assertionUUID.asNode().getURI());
				System.out.println("======== CONTINUITY might be available for assertion <" + contextAssertion.getOntologyResource().getLocalName() + ">. "
						+ "new AssertionUUID: " + contextAssertionUUID + " with existing AssertionUUID: " + extendibleAssertionUUIDRes);
				
				Pair<Map<ContextAnnotation, Pair<Boolean, Pair<Statement, Set<Statement>>>>, Map<StructuredAnnotation, NodeValue>> revisedAnnotations = 
					analyzeAnnotationContinuity(newAssertionAnnotations, extendibleAssertionUUIDRes, assertionStoreModel, contextStoreDataset);
				
				if (revisedAnnotations != null) {
					Map<ContextAnnotation, Pair<Boolean, Pair<Statement, Set<Statement>>>> revisedBasicAnnotations = revisedAnnotations.car();
					Map<StructuredAnnotation, NodeValue> revisedStructuredAnnotations = revisedAnnotations.cdr();
					
					// add the validity JOIN to the revised structured annotations
					CalendarIntervalList newValidityIntervals = (CalendarIntervalList) newValidityPeriod.asLiteral().getValue();
					CalendarIntervalList validityIntervals = (CalendarIntervalList) pairWrapper.assertionValidity.asLiteral().getValue();
					
					CalendarIntervalList mergedValidityIntervals = validityIntervals.joinCloseEnough(newValidityIntervals, CalendarInterval.MAX_GAP_MILLIS);
					Literal mergedValidityLiteral = ResourceFactory.createTypedLiteral(mergedValidityIntervals);
					revisedStructuredAnnotations.put(Engine.getContextAnnotationIndex().getStructuredByResource(ConsertAnnotation.TEMPORAL_VALIDITY), 
						NodeValue.makeNode(mergedValidityLiteral.asNode()));
					
					/*
					 * Now that we have the revised annotations it's time to update the
					 * assertion store model. We have to: - update the existing assertion with the
					 * joined values for each annotation - at the end remove the newly inserted context assertion
					 */
					for (StructuredAnnotation structAnn : revisedStructuredAnnotations.keySet()) {
						// search the structure annotation value statement in the extendible assertion
						Statement annStmt = assertionStoreModel.getProperty(extendibleAssertionUUIDRes, structAnn.getBindingProperty());
						Statement annValStmt = assertionStoreModel.getProperty(annStmt.getResource(), ConsertAnnotation.HAS_STRUCTURED_VALUE);
						
						NodeValue joinedVal = revisedStructuredAnnotations.get(structAnn);
						RDFDatatype annDatatype = TypeMapper.getInstance().getTypeByName(joinedVal.getDatatypeURI());
						RDFNode joinedValLiteral = assertionStoreModel.createTypedLiteral(joinedVal.asNode().getLiteralValue(), annDatatype);
						
						assertionStoreModel.remove(annValStmt);
						assertionStoreModel.add(annStmt.getResource(), ConsertAnnotation.HAS_STRUCTURED_VALUE, joinedValLiteral);
					}
					
					// Save the basic annotation revisions for later re-assertion
					basicAnnotationRevisionMap.put(extendibleAssertionUUIDRes, revisedBasicAnnotations);
					
					System.out.println("CONTINUITY AVAILABALE FOR assertion <" + contextAssertion.getOntologyResource().getLocalName() + ">. "
					        + "AssertionUUID: " + pairWrapper.assertionUUID
					        + ", for duration: " + pairWrapper.assertionValidity);
				}
				else {
					allUpdated = false;
				}
			}
			
			
			if (allUpdated) {
				// now remove the newly inserted triples. we just remove the named
				// graphs altogether
				contextStoreDataset.removeNamedModel(contextAssertionUUID.getURI());
				
				Set<Statement> newAssertionStatements = ContextUpdateUtil.getAllMetaPropertiesFor(newAssertionUUIDRes, assertionStoreModel);
				assertionStoreModel.remove(new LinkedList<Statement>(newAssertionStatements));
				
				// Here is where we need to re-assert the saved basic annotations for each candidate that was extended
				for (Resource extendibleAssertionUUIDRes : basicAnnotationRevisionMap.keySet()) {
					Map<ContextAnnotation, Pair<Boolean, Pair<Statement, Set<Statement>>>> revisedBasicAnnotations = 
							basicAnnotationRevisionMap.get(extendibleAssertionUUIDRes);
				
					for (ContextAnnotation basicAnn : revisedBasicAnnotations.keySet()) {
						boolean replace = revisedBasicAnnotations.get(basicAnn).car();
						Pair<Statement, Set<Statement>> revisedAnnInstance = revisedBasicAnnotations.get(basicAnn).cdr(); 
						Statement annBindStmt = revisedAnnInstance.car();
						Set<Statement> annContents = revisedAnnInstance.cdr();
						
						if (!replace) {
							assertionStoreModel.add(extendibleAssertionUUIDRes, annBindStmt.getPredicate(), annBindStmt.getObject());
							assertionStoreModel.add(new LinkedList<Statement>(annContents));
						}
						else {
							Pair<Statement, Set<Statement>> extendibleBasicAnnInstance = 
								ContextAnnotationUtil.getAnnotationFor(basicAnn.getBindingProperty(), extendibleAssertionUUIDRes, assertionStoreModel);
							assertionStoreModel.remove(extendibleBasicAnnInstance.car());
							assertionStoreModel.remove(new LinkedList<Statement>(extendibleBasicAnnInstance.cdr()));
							
							assertionStoreModel.add(extendibleAssertionUUIDRes, annBindStmt.getPredicate(), annBindStmt.getObject());
							assertionStoreModel.add(new LinkedList<Statement>(annContents));
						}
					}
				}
			}
			
			return new ContinuityResult(contextAssertion, null, true);
			// TODO: performance collect
			//long end = System.currentTimeMillis();
			//return new ContinuityResult(start, (int)(end - start), false, true);
		}
		
		// finally sync the changes
		TDB.sync(contextStoreDataset);
		
		return new ContinuityResult(contextAssertion, null, false);
		// TODO: performance collect
		//long end = System.currentTimeMillis();
		//return new ContinuityResult(start, (int)(end - start), false, false);
	}
	
	
	private Pair<Map<ContextAnnotation, Pair<Statement, Set<Statement>>>, Map<StructuredAnnotation, NodeValue>> analyzeNewAssertionAnnotations(
            Resource newAssertionUUIDRes, Model assertionStoreModel) {
		
		Map<StructuredAnnotation, NodeValue> newAssertionStructuredAnnotations = new HashMap<StructuredAnnotation, NodeValue>();
		Map<ContextAnnotation, Pair<Statement, Set<Statement>>> newAssertionBasicAnnotations = 
			new HashMap<ContextAnnotation, Pair<Statement,Set<Statement>>>();
		
		Set<OntProperty> structuredAnnProperties = Engine.getContextAnnotationIndex().getStructuredAnnotationProperties();
		for (OntProperty structuredAnnProp : structuredAnnProperties) {
			StructuredAnnotation structuredAnn = 
				(StructuredAnnotation)ContextAnnotationUtil.getAnnotationType(structuredAnnProp, newAssertionUUIDRes, assertionStoreModel);
			
			if (structuredAnn != null) {	// if the new ContextAssertion has the type of ContextAnnotation bound by this structuredAnnProp
				NodeValue newAssertionAnnVal = NodeValue.makeNode( ContextAnnotationUtil.getStructuredAnnotationValue(structuredAnnProp, 
					newAssertionUUIDRes, assertionStoreModel).asNode());
				
				newAssertionStructuredAnnotations.put(structuredAnn, newAssertionAnnVal);
			}
		}
		
		Set<OntProperty> basicAnnProperties = Engine.getContextAnnotationIndex().getBasicAnnotationProperties();
		for (OntProperty basicAnnProp : basicAnnProperties) {
			ContextAnnotation basicAnn = ContextAnnotationUtil.getAnnotationType(basicAnnProp, newAssertionUUIDRes, assertionStoreModel); 
			if (basicAnn != null) {		// if the new ContextAssertion has the type of basic ContextAnnotation bound by this basicAnnProp
				Pair<Statement, Set<Statement>> basicAnnContents = 
					ContextAnnotationUtil.getAnnotationFor(basicAnnProp, newAssertionUUIDRes, assertionStoreModel);
				newAssertionBasicAnnotations.put(basicAnn, basicAnnContents);
			}
		}
		
	    return new Pair<Map<ContextAnnotation,Pair<Statement,Set<Statement>>>, Map<StructuredAnnotation,NodeValue>>(newAssertionBasicAnnotations, 
	    		newAssertionStructuredAnnotations);
    }
	
	
	private Pair<Map<ContextAnnotation, Pair<Boolean, Pair<Statement, Set<Statement>>>>, Map<StructuredAnnotation, NodeValue>> analyzeAnnotationContinuity(
			Pair<Map<ContextAnnotation, Pair<Statement, Set<Statement>>>, Map<StructuredAnnotation, NodeValue>> newAssertionAnnotations, 
			Resource extendibleAssertionUUIDRes, Model assertionStoreModel, Dataset contextStoreDataset) {
		
		Map<StructuredAnnotation, NodeValue> revisedStructuredAnnotations = new HashMap<StructuredAnnotation, NodeValue>();
		Map<ContextAnnotation, Pair<Boolean, Pair<Statement, Set<Statement>>>> revisedBasicAnnotations = 
				new HashMap<ContextAnnotation, Pair<Boolean, Pair<Statement,Set<Statement>>>>();
		
		Map<ContextAnnotation, Pair<Statement, Set<Statement>>> newAssertionBasicAnnotations = newAssertionAnnotations.car(); 
		Map<StructuredAnnotation, NodeValue> newAssertionStructuredAnnotations = newAssertionAnnotations.cdr();
		
		boolean continuityPermitted = true;
		
		// First go through all structured annotation properties of the new assertion, check that they are all also found on the extendible assertion
		// and that they all allow continuity for these two ContextAssertion instances
		for (StructuredAnnotation structuredAnn : newAssertionStructuredAnnotations.keySet()) {
			OntProperty structuredAnnProp = structuredAnn.getBindingProperty();
			
			if (!ConsertAnnotation.HAS_VALIDITY.equals(structuredAnnProp)) {
				// check that the extensible assertion instance also has statements for this annotation property
				Statement extendibleAssertionAnnStmt = assertionStoreModel.getProperty(extendibleAssertionUUIDRes, structuredAnnProp);
				boolean extendibleAssertionHasAnn = extendibleAssertionAnnStmt != null && extendibleAssertionAnnStmt.getObject().isResource();
				
				if (!extendibleAssertionHasAnn) {
					continuityPermitted = false;
					break;
				}
				else {
					NodeValue newAssertionAnnVal = newAssertionStructuredAnnotations.get(structuredAnn);
					NodeValue extendibleAssertionAnnVal = NodeValue.makeNode( ContextAnnotationUtil.getStructuredAnnotationValue(structuredAnnProp, 
						extendibleAssertionUUIDRes, assertionStoreModel).asNode());
					
					// Get the permits continuity function implementation. We now that permits continuity functions 
					// take two arguments and return a boolean value so we pass can make a cast in order to 
					// invoke the function execution
					String permitsContinuityFuncURI = structuredAnn.getPermitsContinuityFunctionURI();
					FunctionBase2 permitsContinuityFunc = 
						(FunctionBase2)FunctionRegistry.get().get(permitsContinuityFuncURI).create(permitsContinuityFuncURI);
					
					NodeValue permitsVal = permitsContinuityFunc.exec(newAssertionAnnVal, extendibleAssertionAnnVal);
					boolean permits = permitsVal.getBoolean();
					
					if (!permits) {		// if the annotation values DO NOT permit continuity, then stop
						System.out.println(structuredAnn + " DOES NOT PERMIT CONTINUITY FOR " + extendibleAssertionUUIDRes);
						
						continuityPermitted = false;
						break;
					}
					else {		// otherwise, compute the JOINED value
						String joinOpFunc = structuredAnn.getJoinOperatorURI();
						Object joinOp = FunctionRegistry.get().get(joinOpFunc).create(joinOpFunc);
						if (joinOp instanceof SPINARQFunction) {
							SPINARQFunction joinOpSpin = (SPINARQFunction)joinOp;
							Model defaultModel = ModelFactory.createDefaultModel();
							RDFDatatype annDatatype = TypeMapper.getInstance().getTypeByName(newAssertionAnnVal.getDatatypeURI());
							
							RDFNode newAnnValNode = defaultModel.createTypedLiteral(newAssertionAnnVal.asNode().getLiteralValue(), annDatatype);
							RDFNode extendibleAnnValNode = defaultModel.createTypedLiteral(extendibleAssertionAnnVal.asNode().getLiteralValue(), annDatatype);
							
							QuerySolutionMap bindings = new QuerySolutionMap();
							bindings.add("arg1", newAnnValNode);
							bindings.add("arg2", extendibleAnnValNode);
							
							NodeValue joinedVal = joinOpSpin.executeBody(contextStoreDataset, 
									ModelFactory.createDefaultModel(), bindings);
							revisedStructuredAnnotations.put(structuredAnn, joinedVal);
						}
						else if (joinOp instanceof FunctionBase2) {
							FunctionBase2 joinOpJena = (FunctionBase2)joinOp;
							NodeValue joinedVal = joinOpJena.exec(extendibleAssertionAnnVal, newAssertionAnnVal);
							revisedStructuredAnnotations.put(structuredAnn, joinedVal);
						}
						else {
							System.out.println("######### THIS SHOULD NOT HAPPEN TO A DOG!");
						}
					}
				}
			}
		}
		
		if (!continuityPermitted) {
			return null;
		}
		
		// Now visit all basic annotation properties that the new assertion has.
		// If the extendible assertion has a value for the annotation, replace it with the new one.
		// Otherwise, just add it to the set of basic annotations for the extended assertion.
		for (ContextAnnotation basicAnn : newAssertionBasicAnnotations.keySet()) {
			OntProperty basicAnnProp = basicAnn.getBindingProperty();
			Pair<Statement,Set<Statement>> basicAnnContents = newAssertionBasicAnnotations.get(basicAnn);
			Statement extendibleAssertionAnnStmt = assertionStoreModel.getProperty(extendibleAssertionUUIDRes, basicAnnProp);
			
			// if both have it, replace the old one with the new one
			if (extendibleAssertionAnnStmt != null) {
				revisedBasicAnnotations.put(basicAnn, new Pair<Boolean, Pair<Statement,Set<Statement>>>(true, basicAnnContents));
			}
			else {	// otherwise just add it
				revisedBasicAnnotations.put(basicAnn, new Pair<Boolean, Pair<Statement,Set<Statement>>>(false, basicAnnContents));
			}
		}
		
		return new Pair<Map<ContextAnnotation,Pair<Boolean,Pair<Statement,Set<Statement>>>>, Map<StructuredAnnotation,NodeValue>>(revisedBasicAnnotations, 
				revisedStructuredAnnotations);
    }

	@Override
	public String toString() {
		String response = "";
		
		response += "Executing CHECK_VALIDITIY_CONTINUITY for contextAssertionResource: " + contextAssertion 
				 + " in graphUUID: " + contextAssertionUUID.getURI();
		
		return response;
	}
	
	private static class ContinuityWrapper {
		RDFNode assertionUUID; 
		RDFNode assertionValidity;
		
		ContinuityWrapper(RDFNode assertionUUID, RDFNode assertionValidity) {
	        this.assertionUUID = assertionUUID;
	        this.assertionValidity = assertionValidity;
        }
	}
}
