package org.aimas.ami.contextrep.update;

import java.util.List;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;

public class ConstraintResult extends HookResult {
	
	private List<ContextConstraintViolation> constraintViolations;
	
	public ConstraintResult(ContextAssertion assertion, Exception error, 
			List<ContextConstraintViolation> constraintViolations) {
	    super(assertion, error);
	    this.constraintViolations = constraintViolations;
    }
	
	
	public boolean hasViolation() {
		return constraintViolations != null && !constraintViolations.isEmpty();
	}
	
	public List<ContextConstraintViolation> getViolations() {
		return constraintViolations;
	}
}
