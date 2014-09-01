package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

public class HookResult {
	protected ContextAssertion assertion;
	protected Exception execError;
	protected long duration = -1;
	
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
	
	public long getDuration() {
		return duration;
	}
	
	public void setDuration(long duration) {
		this.duration = duration;
	}
}
