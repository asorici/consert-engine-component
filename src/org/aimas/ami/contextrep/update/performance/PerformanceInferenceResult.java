package org.aimas.ami.contextrep.update.performance;

import java.util.List;

import org.aimas.ami.contextrep.model.ContextAssertion;

public class PerformanceInferenceResult extends PerformanceHookResult {
	private List<ContextAssertion> inferredAssertions;
	
	public PerformanceInferenceResult(long startTime, int duration, ContextAssertion triggerAssertion, 
			Exception error, List<ContextAssertion> inferredAssertions) {
		super(startTime, duration, triggerAssertion, error);
		
		this.inferredAssertions = inferredAssertions;
	}
	
	public List<ContextAssertion> getInferredAssertions() {
		return inferredAssertions;
	}
}
