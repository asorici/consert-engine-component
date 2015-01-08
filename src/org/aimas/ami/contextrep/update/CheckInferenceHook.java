package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.core.ContextARQFactory;
import org.aimas.ami.contextrep.engine.core.DerivationRuleDictionary;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.execution.ExecutionMonitor;
import org.aimas.ami.contextrep.engine.utils.ContextAnnotationUtil;
import org.aimas.ami.contextrep.engine.utils.ContextAssertionUtil;
import org.aimas.ami.contextrep.engine.utils.ContextStoreUtil;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.engine.utils.GraphUUIDGenerator;
import org.aimas.ami.contextrep.engine.utils.spin.ContextSPINInferences;
import org.aimas.ami.contextrep.engine.utils.spin.ContextSPINInferences.ContextInferenceResult;
import org.aimas.ami.contextrep.model.ContextAnnotation;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.aimas.ami.contextrep.vocabulary.ConsertRules;
import org.openjena.atlas.lib.Pair;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.inference.DefaultSPINRuleComparator;
import org.topbraid.spin.inference.SPINRuleComparator;
import org.topbraid.spin.util.CommandWrapper;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.core.Quad;
import com.hp.hpl.jena.sparql.modify.request.QuadDataAcc;
import com.hp.hpl.jena.sparql.modify.request.UpdateCreate;
import com.hp.hpl.jena.sparql.modify.request.UpdateDataInsert;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateRequest;

public class CheckInferenceHook extends ContextUpdateHook {
	private static final String ASSERTION_UUID_PARAM = "contextAssertionUUID";
	private static final String ASSERTION_TYPE_PARAM = "contextAssertionType";
	
	private DerivationRuleWrapper derivationRule;
	
	public CheckInferenceHook(UpdateRequest triggeringRequest, ContextAssertion contextAssertion, 
			Node contextAssertionUUID, DerivationRuleWrapper derivationRule) {
		// WE CURRENTLY CONSIDER THAT A DERIVED ContextAssertion ALWAYS WORKS WITH A TIME-BASED UPDATE MODE
		super(triggeringRequest, contextAssertion, contextAssertionUUID, InsertionHandler.TIME_BASED_UPDATE_MODE);
		
		this.derivationRule = derivationRule;
	}
	
	public DerivationRuleWrapper getDerivationRule() {
		return derivationRule;
	}
	
	public ContextAssertion getDerivedAssertion() {
		return derivationRule.getDerivedAssertion();
	}
	
	public UpdateRequest getTriggeringRequest() {
	    return insertionRequest;
    }
	
	@Override
	public InferenceResult doHook(Dataset contextDataset) {
		//System.out.println("======== CHECKING INFERENCE FOR assertion <" + contextAssertion + ">. ");
		// get the context model
		OntModel contextModelCore = Engine.getModelLoader().getCoreContextModel();
		
		return attemptContextSPINInference(contextDataset, contextModelCore);
	}
	
	private InferenceResult attemptContextSPINInference(Dataset contextDataset, OntModel contextModelCore) {
		// get the query model as the union of the named graphs in our dataset
		//Model queryModel = contextDataset.getNamedModel(JenaVocabulary.UNION_GRAPH_URN);
		Model queryModel = ContextStoreUtil.getUnionModel(contextDataset);
		
		// do the inference procedure
		List<UpdateRequest> inferredContext = attemptDerivationRule(derivationRule, queryModel, contextModelCore, contextDataset);
		
		// report the results depending on inference outcome
		if (!inferredContext.isEmpty()) {
			for (UpdateRequest inferredAssertionReq : inferredContext) {
				ExecutionMonitor.getInstance().logDerivedInsertEnqueue(insertionRequest.hashCode(), inferredAssertionReq.hashCode());
				Engine.getInsertionService().executeRequest(inferredAssertionReq, null, updateMode);
			}
			
			return new InferenceResult(contextAssertion, null, derivationRule.getDerivedAssertion());
		}
		else {
			return new InferenceResult(contextAssertion, null, null);
		}
	}
	
	
	private List<UpdateRequest> attemptDerivationRule(DerivationRuleWrapper derivationWrapper, 
			Model queryModel, OntModel contextModelCore, Dataset contextDataset) {
		List<UpdateRequest> inferred = new ArrayList<UpdateRequest>();
		
		DerivationRuleDictionary ruleDict = Engine.getDerivationRuleDictionary();
		
		// Create Model for inferred triples
		//Model newTriples = ModelFactory.createDefaultModel(ReificationStyle.Minimal);
		Model newTriples = ModelFactory.createDefaultModel();
		
		Map<Resource, List<CommandWrapper>> cls2Query = new HashMap<Resource, List<CommandWrapper>>();
		Map<Resource, List<CommandWrapper>> cls2Constructor = new HashMap<Resource, List<CommandWrapper>>();
		SPINRuleComparator comparator = new DefaultSPINRuleComparator(queryModel);
		
		Resource entityRes = ruleDict.getEntityForDerivation(derivationWrapper);
		CommandWrapper cmd = derivationWrapper.getDerivationCommand();
		
		// create the initialTemplateBindings and ADD THE DEFAULT PARAMETERS: ?contextAssertionUUID and ?contextAssertionResource
		Map<CommandWrapper, Map<String, RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String, RDFNode>>();
		
		Map<String, RDFNode> commandBindings = new HashMap<String, RDFNode>();
		if (derivationWrapper.getCommandBindings() != null) {
			commandBindings.putAll(derivationWrapper.getCommandBindings());
		}
		commandBindings.put(ASSERTION_UUID_PARAM, ResourceFactory.createResource(contextAssertionUUID.getURI()));
		commandBindings.put(ASSERTION_TYPE_PARAM, contextAssertion.getOntologyResource());
		
		initialTemplateBindings.put(cmd, commandBindings);
		
		// create entityCommandWrappers required for SPIN inference API call
		List<CommandWrapper> entityCommandWrappers = new ArrayList<CommandWrapper>();
		entityCommandWrappers.add(cmd);
		cls2Query.put(entityRes, entityCommandWrappers);
		
		// perform inference
		//long timestamp = System.currentTimeMillis();
		ARQFactory.set(new ContextARQFactory(contextDataset));
		
		ContextInferenceResult inferenceResult = ContextSPINInferences.runContextInference(queryModel, newTriples,
			cls2Query, cls2Constructor, initialTemplateBindings, null, ConsertRules.DERIVE_ASSERTION, comparator);
		//ContextInferenceResult inferenceResult = ContextSPINInferences.runContextInference(queryModel, newTriples,
		//	cls2Query, cls2Constructor, null, ConsertRules.DERIVE_ASSERTION, comparator);
		
		if (inferenceResult != null && inferenceResult.isInferred()) {
			//if (derivationWrapper.getDerivedAssertion().getOntologyResource().getURI().contains("HostsAdHoc")) {
			//	System.out.println("[INFO] WE HAVE DEDUCED THE CONTEXT-ASSERTION " + derivationWrapper.getDerivedAssertion() 
			//		+ " following insertion of " + contextAssertion );
			//}
			//ScenarioInit.printStatements(newTriples);
			
			/* 
			 * If there was a deduction the CONSTRUCTED result is in the newTriples model. 
			 * Use it to create a new UpdateRequest to be executed by the assertionInsertExecutor.
			 * 
			 * Step 1: identify the blank nodes that assert the type of derived ContextAssertion. There may be several deductions
			 * since the rule might have fired several times. Each one will include a different binding for the contents of the derived
			 * ContextAssertion. The annotation values might be the same across firings, but not necessarily. We therefore need to create 
			 * a way to account for each triggered derivation and identify the individual assertions and their corresponding annotations
			 * within the newTriples model. We identify individual ContextAssertion instances by searching for 
			 * _:bnode :assertionResource <someDerivedAssertionResource> statements in the inferred newTriples. The _:bnode node will then
			 * help identify the annotation instances for this particular assertion instance. 
			 * The contents of the assertion are identified by a _:bnode :assertionContent _:reifStatementNode statement. 
			 * The _:reifStatementNode is the subject of a reification statement that represents the <i>key</i> statement for the ContextAssertion
			 * content. The nature of this statement depends on the arity of the derived ContextAssertion. Starting with this key statement, the 
			 * rest of the contents of the derived assertion can be identified and retrieved. 
			 */
			
			List<Statement> derivedAssertionStatements = newTriples.listStatements(null, ConsertCore.CONTEXT_ASSERTION_RESOURCE, (RDFNode)null).toList();
			//int nrInferenceInstances = derivedAssertionStatements.size();
			//System.out.println("[INFO] There are " + nrInferenceInstance + " derived assertion instances.");
			
			for (Statement derivedAssertionStmt : derivedAssertionStatements) {
				// TODO: add error handling if for some unknown reason the object of the derivedAssertionStatement is not a Resource
				OntResource derivedAssertionResource = contextModelCore.getOntResource(derivedAssertionStmt.getResource());
				ContextAssertion derivedAssertion = Engine.getContextAssertionIndex().getAssertionFromResource(derivedAssertionResource);
				
				// The subject of the derived assertion statement is the one to which all elements (assertion and annotation content) bind
				Resource derivationSubject = derivedAssertionStmt.getSubject();
				
				// Step 2: identify all statements having the annotationSubject as subject and do not contain the properties 
				// :assertionResource and :assertionContent. These are statements that link towards annotation instances attached to this assertion
				// The full extent of the annotation content may be larger, but these statements provide the required identifier (i.e. starting from
				// these statements, the entire annotation content can be retrieved)
				List<Statement> derivedAnnIdentifierStatements = newTriples.listStatements(new AnnotationStatementSelector(derivationSubject)).toList();
				
				Map<ContextAnnotation, Pair<Resource, Set<Statement>>> derivedAssertionAnnotations = new HashMap<ContextAnnotation, Pair<Resource,Set<Statement>>>();
				for (Statement annIdentifierStmt : derivedAnnIdentifierStatements) {
					Map<ContextAnnotation, Pair<Resource, Set<Statement>>> derivedAnnContents = ContextAnnotationUtil.getAnnotationContents(annIdentifierStmt, newTriples);
					derivedAssertionAnnotations.putAll(derivedAnnContents);
				}
				
				// Step 3: retrieve the :assertionContent statement that acts as root for getting all the contents of the derived assertion
				Statement contentStatement = newTriples.getProperty(derivationSubject, ConsertCore.CONTEXT_ASSERTION_CONTENT);
				Set<Statement> derivedAssertionContents = 
					ContextAssertionUtil.getDerivedAssertionContents(derivedAssertionResource, contentStatement, newTriples);
				
				
				// Step 4: create the new identifier graph and add the derived assertion statements in it creating an Update object
				Node graphUUIDNode = Node.createURI(GraphUUIDGenerator.createUUID(derivedAssertionResource));
				Update derivedAssertionCreateIdentifierUpdate = new UpdateCreate(graphUUIDNode);
				Update derivedAssertionContentUpdate = createDerivedContentUpdate(derivedAssertion, derivedAssertionContents, graphUUIDNode);
				
				// Step 5: create the derived assertion annotation Update object
				Update derivedAssertionAnnotationUpdate = 
					createDerivedAnnotationUpdate(derivedAssertion, derivedAssertionAnnotations, graphUUIDNode);
				
				// Step 6: create the update request and enqueue it
				UpdateRequest insertInferredRequest = UpdateFactory.create();
				insertInferredRequest.add(derivedAssertionCreateIdentifierUpdate);
				insertInferredRequest.add(derivedAssertionContentUpdate);
				insertInferredRequest.add(derivedAssertionAnnotationUpdate);
				
				inferred.add(insertInferredRequest);
			}
		}
		else if (inferenceResult != null && !inferenceResult.isInferred()) {
			//System.out.println("[INFO] NO INFERENCE RESULT ");
		}
		
		
		return inferred;
    }
	

	private Update createDerivedContentUpdate(ContextAssertion derivedAssertion, Set<Statement> derivedAssertionContents, Node graphUUIDNode) {
		QuadDataAcc assertionData = new QuadDataAcc();
		
		for (Statement s : derivedAssertionContents) {
			assertionData.addQuad(Quad.create(graphUUIDNode, s.asTriple()));
		}
		
		return new UpdateDataInsert(assertionData);
    }
	
	
	private Update createDerivedAnnotationUpdate(ContextAssertion derivedAssertion, 
			Map<ContextAnnotation, Pair<Resource, Set<Statement>>> derivedAssertionAnnotations, Node graphUUIDNode) {
		
		QuadDataAcc annotationData = new QuadDataAcc();
		Node derivedAssertionStore = Node.createURI(derivedAssertion.getAssertionStoreURI());
		
		for (ContextAnnotation annotation : derivedAssertionAnnotations.keySet()) {
			Pair<Resource, Set<Statement>> annotationPair = derivedAssertionAnnotations.get(annotation);
			Resource annotationContentRoot = annotationPair.car();
			Set<Statement> annotationContents = annotationPair.cdr();
			
			// for each annotation we need to create a quad of the form: <contentstore> graphUUIDNode <annotationProp> <annotationContentRoot>
			// then when we add the rest of the annotation contents they will bind to the annotationContentRoot
			annotationData.addQuad(Quad.create(derivedAssertionStore, graphUUIDNode, 
				annotation.getBindingProperty().asNode(), annotationContentRoot.asNode()));
			
			for (Statement s : annotationContents) {
				annotationData.addQuad(Quad.create(derivedAssertionStore, s.asTriple()));
			}
		}
		
		// at the end add the triples stating that the graphUUIDNode identifies a ContextAssertion with
		// :assertionResource <derivedAssertionResource> and of :assertionType :Derived
		annotationData.addQuad(Quad.create(derivedAssertionStore, graphUUIDNode, 
			ConsertCore.CONTEXT_ASSERTION_RESOURCE.asNode(), derivedAssertion.getOntologyResource().asNode()));
		
		annotationData.addQuad(Quad.create(derivedAssertionStore, graphUUIDNode, 
			ConsertCore.CONTEXT_ASSERTION_TYPE_PROPERTY.asNode(), ConsertCore.TYPE_DERIVED.asNode()));
			
		
		return new UpdateDataInsert(annotationData);
    }

	private class AnnotationStatementSelector implements Selector {
		private Resource annotationSubject;
		
		AnnotationStatementSelector(Resource annotationSubject) {
			this.annotationSubject = annotationSubject;
		}
		
		@Override
        public boolean test(Statement s) {
			if (s.getSubject().equals(annotationSubject) 
				&& !s.getPredicate().equals(ConsertCore.CONTEXT_ASSERTION_RESOURCE)
				&& !s.getPredicate().equals(ConsertCore.CONTEXT_ASSERTION_CONTENT)) {
				return true;
			}
			
			return false;
        }

		@Override
        public boolean isSimple() {
	        return false;
        }

		@Override
        public Resource getSubject() {
	        return annotationSubject;
        }

		@Override
        public Property getPredicate() {
	        return null;
        }

		@Override
        public RDFNode getObject() {
	        return null;
        }
	}
}
