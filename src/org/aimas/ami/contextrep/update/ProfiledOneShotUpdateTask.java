package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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
import org.aimas.ami.contextrep.engine.utils.ContextStoreUtil;
import org.aimas.ami.contextrep.engine.utils.ContextUpdateUtil;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.aimas.ami.contextrep.model.ViolationAssertionWrapper;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateRequest;

/**
 * This represents the update task concerning a one-shot profiled ContextAssertion update.
 * The one-shot part refers to the fact that this update is not part of a regular update process.
 * As such, we do not subject this ContextAssertion update to a Continuity Check. 
 * Other than that, all other checks (constraint, continuity and inference) are applicable.
 * 
 * @author alex
 *
 */
public class ProfiledOneShotUpdateTask implements Callable<InsertResult> {
	
	private Engine consertEngine;
	
	private UpdateRequest request;
	private InsertionResultNotifier resultNotifier;
	
	private boolean inferenceProbable;
	
	
	public ProfiledOneShotUpdateTask(Engine consertEngine, UpdateRequest profiledAssertionRequest, InsertionResultNotifier notifier) {
		this.consertEngine = consertEngine;
		
		this.request = profiledAssertionRequest;
		this.resultNotifier = notifier;
		
		this.inferenceProbable = false;
	}

	@Override
    public InsertResult call() throws Exception {
		// STEP 1: start a new WRITE transaction on the contextStoreDataset
		Dataset contextDataset = consertEngine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.WRITE);
		
		// STEP 2: analyze request
		List<Node> updatedContextStores = new ArrayList<Node>(analyzeRequest(contextDataset, null));
		
		ContextAssertion insertedAssertion = null;
		Node insertedAssertionUUID = null;
		//Node originalAssertionUUID = null;
		
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
				// For this type of insertion we do not perform a ContinuityCheck
				
				// STEP 5A: check for constraints - we set the update mode to TIME-BASED since that is safest
				constraintResult = (ConstraintResult) new CheckConstraintHook(consertEngine, request, insertedAssertion, insertedAssertionUUID, InsertionHandler.TIME_BASED_UPDATE_MODE).exec(contextDataset);
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
									InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), false, false); 
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
							Resource firstAssertionUUID = ResourceFactory.createResource(violation.getViolatingAssertions()[0].getAssertionInstanceURI());
							
							ContextAssertion secondAssertion = violation.getViolatingAssertions()[1].getAssertion();
							Resource secondAssertionUUID = ResourceFactory.createResource(violation.getViolatingAssertions()[1].getAssertionInstanceURI());
							
							ViolationAssertionWrapper keptAssertionWrapper = resolutionService.resolveViolation(violation, contextStoreSnapshot);
							if (keptAssertionWrapper == null) {
								// we need to delete both instances
								ContextUpdateUtil.deleteContextAssertionInstance(firstAssertion, firstAssertionUUID, contextDataset);
								ContextUpdateUtil.deleteContextAssertionInstance(secondAssertion, secondAssertionUUID, contextDataset);
								
								// commit the changes and return insertion failure
								contextDataset.commit();
								InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), false, false); 
								if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
								return res;
							}
							else {
								if (!keptAssertionWrapper.getAssertionInstanceURI().equals(insertedAssertionUUID.getURI())) {
									// If we must delete the newly inserted ContextAssertion instance, abandon the transaction and return insertion failure
									InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), false, false); 
									if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
									return res;
								}
								else {
									// Otherwise an existing assertion instance must be deleted and afterward we continue normally with the checks
									// We have to determine which of the two it is
									if (keptAssertionWrapper.getAssertionInstanceURI().equals(firstAssertionUUID.getURI())) {
										ContextUpdateUtil.deleteContextAssertionInstance(secondAssertion, secondAssertionUUID, contextDataset);
									}
									else if (keptAssertionWrapper.getAssertionInstanceURI().equals(secondAssertionUUID.getURI())) {
										ContextUpdateUtil.deleteContextAssertionInstance(firstAssertion, firstAssertionUUID, contextDataset);
									}
								}
							}
						}
					}
				}
				
				// STEP 5B: if all is well up to here, check for inheritance - we give the check a TIME-BASED update mode since that is safer.
				inheritanceResult = (AssertionInheritanceResult) new CheckAssertionInheritanceHook(consertEngine, request, insertedAssertion, insertedAssertionUUID, InsertionHandler.TIME_BASED_UPDATE_MODE).exec(contextDataset);
				ExecutionMonitor.getInstance().logInheritanceCheckDuration(request.hashCode(), inheritanceResult.getDuration());
				
				if (inheritanceResult.hasError()) {
					inheritanceResult.getError().printStackTrace();
					
					InsertResult res = new InsertResult(request, new InsertException(constraintResult.getError()), null, false, false); 
					if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
					return res;
				}
			
				// STEP 5C: if all good up to here, add inference checks for the new assertion, if probable
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
			InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), false, inheritanceResult.inherits()); 
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
	
	
	private void enqueueInferenceChecks(ContextAssertion insertedAssertion, Node insertedAssertionUUID) {
		DerivationRuleDictionary ruleDict = consertEngine.getDerivationRuleDictionary();
		List<DerivationRuleWrapper> derivations = ruleDict.getDerivationsForBodyAssertion(insertedAssertion);
		
		for (DerivationRuleWrapper derivationCommand : derivations) {
			ContextAssertion derivedAssertion = derivationCommand.getDerivedAssertion();
			
			// if the rules for this derivedAssertion are active, submit the inference request
			if (consertEngine.getDerivationRuleDictionary().isDerivedAssertionActive(derivedAssertion)) {
				CheckInferenceHook inferenceHook = new CheckInferenceHook(consertEngine, request, insertedAssertion, insertedAssertionUUID, derivationCommand);
				
				ExecutionMonitor.getInstance().logInferenceEnqueue(request.hashCode());
				consertEngine.getInferenceService().executeRequest(inferenceHook);
			}
		}
    }
}
