package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.aimas.ami.contextrep.engine.DerivationRuleDictionary;
import org.aimas.ami.contextrep.engine.Engine;
import org.aimas.ami.contextrep.engine.api.InsertException;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.utils.ContextUpdateUtil;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateRequest;

public class ContextUpdateTask implements Callable<InsertResult> {
	private int assertionInsertID;
	
	private UpdateRequest request;
	
	public ContextUpdateTask(UpdateRequest request) {
		this.request = request;
	}
	
	
	public void setAssertionInsertID(int id) {
		assertionInsertID = id;
	}
	
	public int getAssertionInsertID() {
		return assertionInsertID;
	}
	
	
	@Override
    public InsertResult call() {
		long start = System.currentTimeMillis();
		
		// for testing increment the atomic counter
		// TODO performance collection: RunTest.executedInsertionsTracker.getAndIncrement();
		
		// STEP 1: start a new WRITE transaction on the contextStoreDataset
		Dataset contextDataset = Engine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.WRITE);
		
		// STEP 2: analyze request
		List<Node> updatedContextStores = new ArrayList<>(analyzeRequest(contextDataset, null));
		
		ContextAssertion insertedAssertion = null;
		Node insertedAssertionUUID = null;
		
		ContinuityResult continuityResult = null;
		ConstraintResult constraintResult = null;
		AssertionInheritanceResult inheritanceResult = null;
		
		CheckInferenceHook inferenceHook = null;
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
			
			// STEP 5: if there was an assertion instance update check the hooks in order
			if (insertedAssertion != null) {
				// STEP 5A: first check continuity
				continuityResult = new CheckContinuityHook(insertedAssertion, insertedAssertionUUID).exec(contextDataset);
				if (continuityResult.hasError()) {
					return new InsertResult(new InsertException(continuityResult.getError()), null, false, false);
				}
				
				// STEP 5B: check for constraints
				constraintResult = new CheckConstraintHook(insertedAssertion).exec(contextDataset);
				if (constraintResult.hasError()) {
					return new InsertResult(new InsertException(constraintResult.getError()), null, false, false);
				}
				else if (constraintResult.hasViolation()) {
					return new InsertResult(null, constraintResult.getViolations(), continuityResult.hasContinuity(), false);
				}
				
				// STEP 5C: if all is well up to here, check for inheritance
				inheritanceResult = new CheckAssertionInheritanceHook(insertedAssertion, insertedAssertionUUID).exec(contextDataset);
				if (inheritanceResult.hasError()) {
					return new InsertResult(new InsertException(constraintResult.getError()), null, false, false);
				}
			}
			
			// STEP 5D: if all good up to here, add inference checks for the new assertion
			DerivationRuleDictionary ruleDict = Engine.getDerivationRuleDictionary();
			if (ruleDict.getDerivationsForAssertion(insertedAssertion) != null) {
				inferenceHook = new CheckInferenceHook(insertedAssertion);
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
			if (cleanUpdate) {
				// TODO: notify the ContextInsertListener (the SubscriptionMonitor) of the newly inserted ContextAssertion type
				Engine.subscriptionMonitor().notifyAssertionInserted(insertedAssertion);
			}
		}
		
		// STEP 7: enqueue detected INFERENCE HOOK to assertionInferenceExecutor
		if (inferenceHook != null) {
			Future<InferenceResult> result = Engine.assertionInferenceExecutor().submit(new ContextInferenceTask(inferenceHook));
			// TODO: performance collection
		}
		
		return new InsertResult(null, constraintResult.getViolations(), continuityResult.hasContinuity(), inheritanceResult.inherits());
		// TODO: performance collection 
		//long end = System.currentTimeMillis();
		//return new AssertionInsertResult(assertionInsertID, start, (int)(end - start), insertedContextAssertions, continuityResults, constraintResults);
    }
	
	
	protected Collection<Node> analyzeRequest(Dataset dataset, Map<String, RDFNode> templateBindings) {
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
	
	
	protected List<ContinuityResult> execContinuityHooks(List<CheckContinuityHook> hooks, Dataset contextStoreDataset) {
		List<ContinuityResult> hookResults = new LinkedList<>();
		
		for (int i = 0; i < hooks.size(); i++) {
			CheckContinuityHook hook = hooks.get(i);
			
			ContinuityResult result = hook.exec(contextStoreDataset);
			if (result.hasError()) {
				System.out.println("Action ERROR!");
			}
			
			hookResults.add(result);
		}
		
		return hookResults;
	}
	
	
	protected List<ConstraintResult> execConstraintHooks(List<CheckConstraintHook> hooks, Dataset contextStoreDataset) {
		List<ConstraintResult> hookResults = new LinkedList<>();
		
		for (int i = 0; i < hooks.size(); i++) {
			CheckConstraintHook hook = hooks.get(i);
			
			ConstraintResult result = hook.exec(contextStoreDataset);
			if (result.hasError()) {
				System.out.println("Action ERROR!");
			}
			
			hookResults.add(result);
		}
		
		return hookResults;
	}
	
	
	private void enqueueInferenceHooks(List<CheckInferenceHook> inferenceHooks) {
	    for (CheckInferenceHook hook : inferenceHooks) {
	    	Future<InferenceResult> result = Engine.assertionInferenceExecutor().submit(new ContextInferenceTask(hook));
	    	
	    	// TODO: figure out performance collection
	    	//RunTest.inferenceTaskEnqueueTime.put(assertionInsertID, System.currentTimeMillis());
	    	//RunTest.inferenceResults.put(assertionInsertID, result);
	    }
    }
}
