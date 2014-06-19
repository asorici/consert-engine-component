package org.aimas.ami.contextrep.engine.api;

import java.util.List;

import org.aimas.ami.contextrep.model.ContextConstraintViolation;

public class InsertResult {
	protected InsertException execError;
	
	protected List<ContextConstraintViolation> constraintViolations;
	
	protected boolean wasContinuous;
	protected boolean triggeredInheritance;
	
	
    public InsertResult(InsertException execError, List<ContextConstraintViolation> constraintViolations, 
    		boolean wasContinuous, boolean triggeredInheritance) {
	    this.execError = execError;
	    this.constraintViolations = constraintViolations;
	    this.wasContinuous = wasContinuous;
	    this.triggeredInheritance = triggeredInheritance;
    }


	public InsertException getExecError() {
		return execError;
	}
	
	public boolean hasExecError() {
		return execError != null;
	}
	
	public List<ContextConstraintViolation> getConstraintViolations() {
		return constraintViolations;
	}
	
	public boolean hasConstraintViolations() {
		return constraintViolations != null && !constraintViolations.isEmpty();
	}

	public boolean wasContinuous() {
		return wasContinuous;
	}
	
	public boolean triggeredInheritance() {
		return triggeredInheritance;
	}
}
