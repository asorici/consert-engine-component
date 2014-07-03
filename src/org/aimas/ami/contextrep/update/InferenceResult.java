package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

public class InferenceResult extends HookResult {
	
	//private List<ContextAssertion> inferredAssertions;
	private ContextAssertion derivedAssertion;
	
	public InferenceResult(ContextAssertion triggerAssertion, Exception error, 
			//List<ContextAssertion> inferredAssertions) {
			ContextAssertion derivedAssertion) {
	    
		super(triggerAssertion, error);
	    this.derivedAssertion = derivedAssertion;
	    //this.inferredAssertions = inferredAssertions;
	    
    }

	public boolean inferencePossible() {
		//return inferredAssertions != null;
		return derivedAssertion != null;
	}
	
	
	public boolean hasInferred() {
		//return inferredAssertions != null && !inferredAssertions.isEmpty();
		return derivedAssertion != null;
	}	
	
	/*
	public List<ContextAssertion> getInferredAssertions() {
		return inferredAssertions;
	}
	*/
	
	public ContextAssertion getDerivedAssertion() {
		return derivedAssertion;
	}
}
