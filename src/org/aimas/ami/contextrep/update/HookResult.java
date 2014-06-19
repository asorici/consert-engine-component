package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

public class HookResult {
	ContextAssertion assertion;
	Exception execError;

	public HookResult(ContextAssertion assertion, Exception error) {
	    this.assertion = assertion;
		this.execError = error;
    }
	
	public ContextAssertion getHookAssertion() {
		return assertion;
	}
	
	public boolean hasError() {
		return execError != null;
	}
	
	
	public Exception getError() {
		return execError;
	}
}
