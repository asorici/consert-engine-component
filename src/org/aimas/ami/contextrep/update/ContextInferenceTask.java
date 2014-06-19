package org.aimas.ami.contextrep.update;

import java.util.concurrent.Callable;

import org.aimas.ami.contextrep.engine.Engine;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;


public class ContextInferenceTask implements Callable<InferenceResult> {
	private CheckInferenceHook inferenceHook;
	
	/** 
	 * The ID corresponding to the assertion insert that triggered this inference hook. 
	 * Used only during performance tests.
	 */
	private int referenceID;
	
	public void setReferenceID(int id) {
		referenceID = id;
	}
	
	public int getReferenceID() {
		return referenceID;
	}
	
	
	public ContextInferenceTask(CheckInferenceHook inferenceHook) {
		this.inferenceHook = inferenceHook;
	}
	
	@Override
	public InferenceResult call() {
		long start = System.currentTimeMillis();
		
		InferenceResult inferenceHookResult = null;
		
		// for test purposes increment atomic counter
		// TODO: performance collector RunTest.enqueuedInferenceTracker.getAndIncrement();
		
		// STEP 1: start a new READ transaction on the contextStoreDataset
		Dataset contextDataset = Engine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.READ);
		//contextDataset.getLock().enterCriticalSection(true);
		
		try {
			// STEP 2: execute inference hook
			inferenceHookResult = inferenceHook.exec(contextDataset);
			
			if (inferenceHookResult.hasError()) {
				System.out.println("Inference ERROR!");
			}
		}
		finally {
			contextDataset.end();
			//contextDataset.getLock().leaveCriticalSection();
		}
		
		return inferenceHookResult;
		// TODO: performance collection
		//long end = System.currentTimeMillis();
		//return new AssertionInferenceResult(referenceID, start, (int)(end - start), inferenceHook.getContextAssertion(), inferenceHookResult);
	}
}
