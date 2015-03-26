package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.engine.api.ConstraintResolutionService;
import org.aimas.ami.contextrep.engine.api.InsertException;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.InsertionResultNotifier;
import org.aimas.ami.contextrep.engine.core.ContextStoreSnapshot;
import org.aimas.ami.contextrep.engine.core.DerivationRuleDictionary;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.execution.ContextInsertNotifier;
import org.aimas.ami.contextrep.engine.execution.ExecutionMonitor;
import org.aimas.ami.contextrep.engine.utils.ContextAnnotationUtil;
import org.aimas.ami.contextrep.engine.utils.ContextStoreUtil;
import org.aimas.ami.contextrep.engine.utils.ContextUpdateUtil;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.aimas.ami.contextrep.model.ViolationAssertionWrapper;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.aimas.ami.contextrep.vocabulary.ConsertFunctions;
import org.openjena.atlas.lib.Pair;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase1;
import com.hp.hpl.jena.sparql.function.FunctionRegistry;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateRequest;

public class ContextUpdateTask implements Callable<InsertResult> {
	private int assertionInsertID;
	
	public void setAssertionInsertID(int id) {
		assertionInsertID = id;
	}
	
	public int getAssertionInsertID() {
		return assertionInsertID;
	}
	
	private Engine consertEngine;
	
	private UpdateRequest request;
	private int updateMode;
	
	private InsertionResultNotifier resultNotifier;
	private boolean inferenceProbable;
	
	public ContextUpdateTask(Engine consertEngine, UpdateRequest request, InsertionResultNotifier notifier, int updateMode) {
		this.consertEngine = consertEngine;
		
		this.request = request;
		this.updateMode = updateMode;
		
		this.resultNotifier = notifier;
		this.inferenceProbable = false;
	}
	
	
	@Override
    public InsertResult call() {
		ExecutionMonitor.getInstance().logInsertExecStart(request.hashCode());
		InsertResult result = doInsert();
		ExecutionMonitor.getInstance().logInsertExecEnd(request.hashCode(), result);
		
		return result;
    }
	
	private InsertResult doInsert() {
		// STEP 1: start a new WRITE transaction on the contextStoreDataset
		Dataset contextDataset = consertEngine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.WRITE);
		
		// STEP 2: analyze request
		List<Node> updatedContextStores = new ArrayList<Node>(analyzeRequest(contextDataset, null));
		
		ContextAssertion insertedAssertion = null;
		Node insertedAssertionUUID = null;
		//Node originalAssertionUUID = null;
		
		ContinuityResult continuityResult = null;
		ConstraintResult constraintResult = null;
		AssertionInheritanceResult inheritanceResult = null;
		
		boolean cleanUpdate = false;
		
		try {
			// STEP 3: determine the inserted ContextAssertion based on the request analysis - the updates context stores
			// 		   since for each update there is only one corresponding ContextAssertion we can break at the first
			//		   match
			for (Node graphNode : updatedContextStores) {
				if (consertEngine.getContextAssertionIndex().isContextAssertionUUID(graphNode)) {
					// get the inserted assertion
					insertedAssertion = consertEngine.getContextAssertionIndex().getAssertionFromGraphUUID(graphNode);
					insertedAssertionUUID = graphNode;
					break;	// break since WE IMPOSE !!! that there be only one instance in the UpdateRequest
				}
			}
			
			// STEP 4: execute updates
			GraphStore graphStore = GraphStoreFactory.create(contextDataset);
			UpdateAction.execute(request, graphStore);
			
			// STEP 4bis: check if an update was made to the EntityStore and if so, apply OWL-Micro Reasoning
			boolean entityStoreUpdate = false;
			for (Node graphNode : updatedContextStores) {
				if (ContextStoreUtil.isEntityStore(graphNode)) {
					entityStoreUpdate = true;
					break;
				}
			}
			
			if (entityStoreUpdate) {
				// TODO: see if there's a better / more elegant way to do this
				Model entityStore = contextDataset.getNamedModel(ConsertCore.ENTITY_STORE_URI);
				InfModel entityStoreInfModel = ModelFactory.createInfModel(consertEngine.getEntityStoreReasoner(), entityStore);
				
				Model newData = entityStoreInfModel.difference(entityStore);
				entityStore.add(newData);
			}
			
			// STEP 5: if there was an assertion instance update, check the hooks in order
			if (insertedAssertion != null) {
				ExecutionMonitor.getInstance().logInsertExecType(request.hashCode(), insertedAssertion.getOntologyResource().getLocalName());
				
				// STEP 5A: check the update mode type - for a time-based update we perform a continuity check;
				// for a change-based update we have to change the end of the validity period for the previous 
				// instance of this ContextAssertion
				if (updateMode == InsertionHandler.TIME_BASED_UPDATE_MODE) {
					// time-based update => check continuity
					continuityResult = (ContinuityResult) new CheckContinuityHook(consertEngine, request, insertedAssertion, insertedAssertionUUID).exec(contextDataset);
					ExecutionMonitor.getInstance().logContinuityCheckDuration(request.hashCode(), continuityResult.getDuration());
					
					if (continuityResult.hasError()) {
						InsertResult res = new InsertResult(request, new InsertException(continuityResult.getError()), null, false, false); 
						if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
						return res;
					}
					else if (continuityResult.hasContinuity()) {
						// If we successfully extended a previous assertion, we need to update the insertedAssertionUUID for
						// the following checks
						insertedAssertionUUID = continuityResult.getExtendedAssertionUUID();
					}
				}
				else {
					// change-based update => mark the end of the validity for the previous ContextAssertion instance
					alterPreviousValidity(insertedAssertion, insertedAssertionUUID, contextDataset);
				}
				
				// STEP 5B: check for constraints
				constraintResult = (ConstraintResult) new CheckConstraintHook(consertEngine, request, insertedAssertion, insertedAssertionUUID, updateMode).exec(contextDataset);
				ExecutionMonitor.getInstance().logConstraintCheckDuration(request.hashCode(), constraintResult.getDuration());
				
				if (constraintResult.hasError()) {
					InsertResult res = new InsertResult(request, new InsertException(constraintResult.getError()), null, false, false); 
					if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
					return res;
				}
				else if (constraintResult.hasViolation()) {
					// IF WE DETECT A VIOLATION WE INVOKE THE RESOLUTION SERVICE NOW
					
					// wrap this transaction of the ContextStore as a snapshot
					ContextStoreSnapshot contextStoreSnapshot = new ContextStoreSnapshot(consertEngine, contextDataset);
					
					for (ContextConstraintViolation violation : constraintResult.getViolations()) {
						// If we are dealing with a value constraint violation
						if (violation.isValueConstraint()) {
							ConstraintResolutionService resolutionService = consertEngine.getConstraintIndex().getValueResolutionService(insertedAssertion);
							if (resolutionService != null) {
								if (resolutionService.resolveViolation(violation, contextStoreSnapshot) == null) {
									// the assertion instance was rejected, so what we do is just break off the transaction and notify failure of insertion
									InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), continuityResult.hasContinuity(), false); 
									if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
									return res;
								}
								// The resolution service must have applied a correction since it wants to keep the newly inserted value, 
								// so we just along
							}
						}
						else {
							// Otherwise we must distinguish between integrity and uniqueness constraint violations
							ConstraintResolutionService resolutionService = null;
							if (violation.isUniquenessConstraint()) {
								resolutionService = consertEngine.getConstraintIndex().getUniquenessResolutionService(insertedAssertion); 
								if (resolutionService == null) resolutionService = consertEngine.getConstraintIndex().getDefaultUniquenessResolutionService();
							}
							else if (violation.isIntegrityConstraint()) {
								resolutionService = consertEngine.getConstraintIndex().getUniquenessResolutionService(insertedAssertion); 
								if (resolutionService == null) resolutionService = consertEngine.getConstraintIndex().getDefaultUniquenessResolutionService();
							}
							
							ContextAssertion firstAssertion = violation.getViolatingAssertions()[0].getAssertion();
							Resource firstAssertionUUID = ResourceFactory.createResource(violation.getViolatingAssertions()[0].getAssertionInstanceUUID());
							
							ContextAssertion secondAssertion = violation.getViolatingAssertions()[1].getAssertion();
							Resource secondAssertionUUID = ResourceFactory.createResource(violation.getViolatingAssertions()[1].getAssertionInstanceUUID());
							
							ViolationAssertionWrapper keptAssertionWrapper = resolutionService.resolveViolation(violation, contextStoreSnapshot);
							if (keptAssertionWrapper == null) {
								// we need to delete both instances
								ContextUpdateUtil.deleteContextAssertionInstance(firstAssertion, firstAssertionUUID, contextDataset);
								ContextUpdateUtil.deleteContextAssertionInstance(secondAssertion, secondAssertionUUID, contextDataset);
								
								// commit the changes and return insertion failure
								contextDataset.commit();
								InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), continuityResult.hasContinuity(), false); 
								if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
								return res;
							}
							else {
								if (keptAssertionWrapper.getAssertionInstanceUUID().equals(insertedAssertionUUID)) {
									// If we must delete the newly inserted ContextAssertion instance, abandon the transaction and return insertion failure
									InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), continuityResult.hasContinuity(), false); 
									if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
									return res;
								}
								else {
									// Otherwise an existing assertion instance must be deleted and afterward we continue normally with the checks
									ContextUpdateUtil.deleteContextAssertionInstance(keptAssertionWrapper.getAssertion(), 
											ResourceFactory.createResource(keptAssertionWrapper.getAssertionInstanceUUID()), contextDataset);
								}
							}
						}
					}
				}
				
				// STEP 5C: if all is well up to here, check for inheritance
				inheritanceResult = (AssertionInheritanceResult) new CheckAssertionInheritanceHook(consertEngine, request, 
						insertedAssertion, insertedAssertionUUID, updateMode).exec(contextDataset);
				ExecutionMonitor.getInstance().logInheritanceCheckDuration(request.hashCode(), inheritanceResult.getDuration());
				
				if (inheritanceResult.hasError()) {
					inheritanceResult.getError().printStackTrace();
					
					InsertResult res = new InsertResult(request, new InsertException(constraintResult.getError()), null, false, false); 
					if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
					return res;
				}
			
				// STEP 5D: if all good up to here, add inference checks for the new assertion, if probable
				DerivationRuleDictionary ruleDict = consertEngine.getDerivationRuleDictionary();
				if (ruleDict.getDerivationsForBodyAssertion(insertedAssertion) != null) {
					inferenceProbable = true;
				}
			}
			
			// STEP 6: commit transaction
			contextDataset.commit();
			cleanUpdate = true;
		} 
		catch (Exception ex) {
			ex.printStackTrace();
			contextDataset.abort();
		}
		finally {
			contextDataset.end();
			if (cleanUpdate && insertedAssertion != null) {
				// Engine.subscriptionMonitor().notifyAssertionInserted(insertedAssertion);
				
				// Notify the ContextInsertNotifier of the  newly inserted ContextAssertion type, 
				// if there was one. This will in turn notify any of the connected CtxQueryHandler
				// agents that have registered with this CONSERT Engine
				ContextInsertNotifier.getInstance().notifyAssertionUpdated(insertedAssertion);
				
			}
		}
		
		// STEP 7: enqueue detected INFERENCE HOOK to assertionInferenceExecutor
		if (inferenceProbable) {
			enqueueInferenceChecks(insertedAssertion, insertedAssertionUUID);
		}
		
		if (insertedAssertion != null) {
			// STEP 8a: if we had an assertion insertion, return appropriate HookResults
			InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), continuityResult.hasContinuity(), inheritanceResult.inherits()); 
			if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
			return res; 
		}
		else {
			// STEP 8b: otherwise, it was just an EntityStore update and we return a plain answer
			InsertResult res = new InsertResult(request, null, null, false, false); 
			if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
			return res;
		}
	}
	
	
	private Collection<Node> analyzeRequest(Dataset dataset, Map<String, RDFNode> templateBindings) {
		Collection<Node> updatedContextStores = null;
		
		for (Update up : request.getOperations()) {
			if (updatedContextStores == null) {
				updatedContextStores = ContextUpdateUtil.getInsertionGraphs(consertEngine, up, dataset, templateBindings, false);
			}
			else {
				updatedContextStores.addAll(ContextUpdateUtil.getInsertionGraphs(consertEngine, up, dataset, templateBindings, false));
			}
		}
		
		return updatedContextStores;
	}
	
	
	private void alterPreviousValidity(ContextAssertion insertedAssertion, Node insertedAssertionUUID, Dataset contextStoreDataset) {
	    // Get the ContextAssertionStore of the inserted assertion type
		String assertionStoreURI = insertedAssertion.getAssertionStoreURI();
		Model assertionStoreModel = contextStoreDataset.getNamedModel(assertionStoreURI);
		Resource assertionUUIDResource = ResourceFactory.createResource(insertedAssertionUUID.getURI());
		
		// First determine the start of the validity interval for the new assertion instance
		Calendar newIntervalStart = null;
		RDFNode newValidityNode = ContextAnnotationUtil.getStructuredAnnotationValue(ConsertAnnotation.HAS_VALIDITY, 
				assertionUUIDResource, assertionStoreModel);
		
		if (newValidityNode != null) {
			CalendarIntervalList newValidityList = (CalendarIntervalList)newValidityNode.asLiteral().getValue();
			
			// for the newly inserted assertion instance, we access the first interval since there is only one
			CalendarInterval newInterval = newValidityList.get(0);
			newIntervalStart = newInterval.lowerLimit();
		}
		
		// Then get the previous instance of the same ContextAssertion type as that of the insertedAssertion
		FunctionBase1 mostRecentAssertionFunc = (FunctionBase1)FunctionRegistry.get()
				.get(ConsertFunctions.NS + "mostRecentAssertionInstance").create(ConsertFunctions.NS + "mostRecentAssertionInstance");
		NodeValue assertionType = NodeValue.makeNode(insertedAssertion.getOntologyResource().asNode());
		NodeValue mostRecentAssertionUUIDVal = mostRecentAssertionFunc.exec(assertionType);
		Resource previousAssertionUUIDRes = ResourceFactory.createResource(mostRecentAssertionUUIDVal.getNode().getURI());
		
		// Access the validity annotation
		Pair<Statement, Set<Statement>> previousValidityAnnotation = ContextAnnotationUtil.getAnnotationFor(ConsertAnnotation.HAS_VALIDITY, 
				previousAssertionUUIDRes, assertionStoreModel);
		
		if (previousValidityAnnotation != null) {
			Set<Statement> validityAnnotationStmts = previousValidityAnnotation.cdr();
			for (Statement st : validityAnnotationStmts) {
				if (st.getPredicate().equals(ConsertAnnotation.HAS_STRUCTURED_VALUE)) {
					CalendarIntervalList previousValidityList = (CalendarIntervalList)st.getLiteral().getValue();
					
					// we access the last interval in the list - since NORMALLY that is the one being updated
					// (in most envisioned use cases, there will only be one interval in the list anyway)
					CalendarInterval previousInterval = previousValidityList.get(previousValidityList.size() - 1);
					
					// create the updated interval and replace it in the assertionStore for the previous assertion instance
					CalendarInterval updatedInterval = new CalendarInterval(previousInterval.lowerLimit(), true, newIntervalStart, true);
					previousValidityList.insertAt(previousValidityList.size() - 1, updatedInterval);
					
					Literal updatedValidityLiteral = ResourceFactory.createTypedLiteral(previousValidityList);
					
					// replace the previous validity literal with the update one in the statement - SHOULD WORK
					st.changeObject(updatedValidityLiteral);
				}
			}
		}
    }
	
	
	private void enqueueInferenceChecks(ContextAssertion insertedAssertion, Node insertedAssertionUUID) {
		DerivationRuleDictionary ruleDict = consertEngine.getDerivationRuleDictionary();
		List<DerivationRuleWrapper> derivations = ruleDict.getDerivationsForBodyAssertion(insertedAssertion);
		
		for (DerivationRuleWrapper derivationCommand : derivations) {
			ContextAssertion derivedAssertion = derivationCommand.getDerivedAssertion();
			
			// if the rules for this derivedAssertion are active, submit the inference request
			if (consertEngine.getDerivationRuleDictionary().isDerivedAssertionActive(derivedAssertion)) {
				CheckInferenceHook inferenceHook = new CheckInferenceHook(consertEngine, request, insertedAssertion, insertedAssertionUUID, derivationCommand);
				
				ExecutionMonitor.getInstance().logInferenceEnqueue(request.hashCode());
				ExecutionMonitor.getInstance().logInferenceExecType(request.hashCode(), 
						derivedAssertion.getOntologyResource().getLocalName());
				consertEngine.getInferenceService().executeRequest(inferenceHook);
			}
		}
    }
}
