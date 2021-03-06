package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.aimas.ami.contextrep.engine.api.InsertException;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionResultNotifier;
import org.aimas.ami.contextrep.engine.core.DerivationRuleDictionary;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.execution.ContextInsertNotifier;
import org.aimas.ami.contextrep.engine.execution.ExecutionMonitor;
import org.aimas.ami.contextrep.engine.utils.ContextStoreUtil;
import org.aimas.ami.contextrep.engine.utils.ContextUpdateUtil;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
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
	
	
	private UpdateRequest request;
	private InsertionResultNotifier resultNotifier;
	private boolean inferenceProbable;
	
	public ContextUpdateTask(UpdateRequest request, InsertionResultNotifier notifier) {
		this.request = request;
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
		Dataset contextDataset = Engine.getRuntimeContextStore();
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
				if (Engine.getContextAssertionIndex().isContextAssertionUUID(graphNode)) {
					// get the inserted assertion
					insertedAssertion = Engine.getContextAssertionIndex().getAssertionFromGraphUUID(graphNode);
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
				InfModel entityStoreInfModel = ModelFactory.createInfModel(Engine.getEntityStoreReasoner(), entityStore);
				
				Model newData = entityStoreInfModel.difference(entityStore);
				entityStore.add(newData);
			}
			
			// STEP 5: if there was an assertion instance update check the hooks in order
			if (insertedAssertion != null) {
				
				// STEP 5A: first check continuity
				continuityResult = (ContinuityResult) new CheckContinuityHook(request, insertedAssertion, insertedAssertionUUID).exec(contextDataset);
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
				
				// STEP 5B: check for constraints
				constraintResult = (ConstraintResult) new CheckConstraintHook(request, insertedAssertion, insertedAssertionUUID).exec(contextDataset);
				ExecutionMonitor.getInstance().logConstraintCheckDuration(request.hashCode(), constraintResult.getDuration());
				
				if (constraintResult.hasError()) {
					InsertResult res = new InsertResult(request, new InsertException(constraintResult.getError()), null, false, false); 
					if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
					return res;
				}
				else if (constraintResult.hasViolation()) {
					// ============================================================================
					// BIG TODO: IF WE DETECT A VIOLATION WE MUST INVOKE THE RESOLUTION SERVICE NOW
					// ============================================================================
					InsertResult res = new InsertResult(request, null, constraintResult.getViolations(), continuityResult.hasContinuity(), false); 
					if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
					return res;
				}
				
				// STEP 5C: if all is well up to here, check for inheritance
				inheritanceResult = (AssertionInheritanceResult) new CheckAssertionInheritanceHook(request, insertedAssertion, insertedAssertionUUID).exec(contextDataset);
				ExecutionMonitor.getInstance().logInheritanceCheckDuration(request.hashCode(), inheritanceResult.getDuration());
				
				if (inheritanceResult.hasError()) {
					inheritanceResult.getError().printStackTrace();
					
					InsertResult res = new InsertResult(request, new InsertException(constraintResult.getError()), null, false, false); 
					if (resultNotifier != null) resultNotifier.notifyInsertionResult(res);
					return res;
				}
			
				// STEP 5D: if all good up to here, add inference checks for the new assertion, if probable
				DerivationRuleDictionary ruleDict = Engine.getDerivationRuleDictionary();
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
				updatedContextStores = ContextUpdateUtil.getUpdatedGraphs(up, dataset, templateBindings, false);
			}
			else {
				updatedContextStores.addAll(ContextUpdateUtil.getUpdatedGraphs(up, dataset, templateBindings, false));
			}
		}
		
		return updatedContextStores;
	}
	
	
	private void enqueueInferenceChecks(ContextAssertion insertedAssertion, Node insertedAssertionUUID) {
		DerivationRuleDictionary ruleDict = Engine.getDerivationRuleDictionary();
		List<DerivationRuleWrapper> derivations = ruleDict.getDerivationsForBodyAssertion(insertedAssertion);
		
		for (DerivationRuleWrapper derivationCommand : derivations) {
			ContextAssertion derivedAssertion = derivationCommand.getDerivedAssertion();
			
			// if the rules for this derivedAssertion are active, submit the inference request
			if (Engine.getDerivationRuleDictionary().isDerivedAssertionActive(derivedAssertion)) {
				CheckInferenceHook inferenceHook = new CheckInferenceHook(request, insertedAssertion, insertedAssertionUUID, derivationCommand);
				
				ExecutionMonitor.getInstance().logInferenceEnqueue(request.hashCode());
				Engine.getInferenceService().executeRequest(inferenceHook);
			}
		}
    }
}
