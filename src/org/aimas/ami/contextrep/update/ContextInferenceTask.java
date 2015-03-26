package org.aimas.ami.contextrep.update;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.execution.ExecutionMonitor;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;


public class ContextInferenceTask implements Callable<InferenceResult> {
	private Engine consertEngine;
	private CheckInferenceHook inferenceHook;
	
	public ContextInferenceTask(Engine consertEngine, CheckInferenceHook inferenceHook) {
		this.consertEngine = consertEngine;
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
		Dataset contextDataset = consertEngine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.READ);
			
		try {
			// STEP 2: execute inference hook
			inferenceHookResult = (InferenceResult) inferenceHook.exec(contextDataset);
			DerivationRuleWrapper derivationRule = inferenceHook.getDerivationRule();
			Set<ContextAssertion> bodyAssertions = new HashSet<ContextAssertion>(derivationRule.getBodyAssertions());
			
			
			if (inferenceHookResult.hasError()) {
				System.out.println("Inference ERROR!");
				
				// notify both the inference and query (for the rule body assertions) statistics collectors
				consertEngine.getInferenceService().markInferenceExecution(derivationRule, false);
				consertEngine.getQueryService().markQueryExecution(bodyAssertions, false);
			}
			else {
				// notify both the inference and query (for the rule body assertions) statistics collectors
				consertEngine.getInferenceService().markInferenceExecution(derivationRule, inferenceHookResult.hasInferred());
				consertEngine.getQueryService().markQueryExecution(bodyAssertions, inferenceHookResult.hasInferred());
			}
		}
		finally {
			contextDataset.end();
		}
		
		return inferenceHookResult;
	}
}
