package org.aimas.ami.contextrep.update;

import java.util.List;

import org.aimas.ami.contextrep.model.ContextAssertion;

public class InferenceResult extends HookResult {
	
	private List<ContextAssertion> inferredAssertions;
	
	public InferenceResult(ContextAssertion triggerAssertion, Exception error, 
			List<ContextAssertion> inferredAssertions) {
	    super(triggerAssertion, error);
	    this.inferredAssertions = inferredAssertions;
    }

	public boolean inferencePossible() {
		return inferredAssertions != null;
	}

	public boolean hasInferred() {
		return inferredAssertions != null && !inferredAssertions.isEmpty();
	}	
	
	
	public List<ContextAssertion> getInferredAssertions() {
		return inferredAssertions;
	}
}
