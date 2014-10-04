package org.aimas.ami.contextrep.update;

import java.util.concurrent.Callable;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.execution.ExecutionMonitor;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;


public class ContextInferenceTask implements Callable<InferenceResult> {
	private CheckInferenceHook inferenceHook;
	
	public ContextInferenceTask(CheckInferenceHook inferenceHook) {
		this.inferenceHook = inferenceHook;
	}
	
	
	@Override
	public InferenceResult call() {
		ExecutionMonitor.getInstance().logInferenceExecStart(inferenceHook.getTriggeringRequest().hashCode());
		InferenceResult result = doInference();
		ExecutionMonitor.getInstance().logInferenceExecEnd(inferenceHook.getTriggeringRequest().hashCode(), result);
		
		return result;
	}
	
	
	private InferenceResult doInference() {
		InferenceResult inferenceHookResult = null;
		
		// STEP 1: start a new READ transaction on the contextStoreDataset
		Dataset contextDataset = Engine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.READ);
			
		try {
			// STEP 2: execute inference hook
			inferenceHookResult = (InferenceResult) inferenceHook.exec(contextDataset);
			
			if (inferenceHookResult.hasError()) {
				System.out.println("Inference ERROR!");
				
				// notify the inference statistics collector
				Engine.getInferenceService().markInferenceExecution(inferenceHook.getDerivationRule(), false);
			}
			else {
				// notify the inference statistics collector
				Engine.getInferenceService().markInferenceExecution(inferenceHook.getDerivationRule(), inferenceHookResult.hasInferred());
			}
		}
		finally {
			contextDataset.end();
		}
		
		return inferenceHookResult;
	}
}
