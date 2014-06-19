package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

public class ContinuityResult extends HookResult {
	
	private boolean hasContinuity;
	
	public ContinuityResult(ContextAssertion assertion, Exception error, boolean hasContinuity) {
	    super(assertion, error);
	    this.hasContinuity = hasContinuity;
    }
	
	public boolean hasContinuity() {
		return hasContinuity;
	}
}
