package org.aimas.ami.contextrep.update.performance;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.update.HookResult;

public class PerformanceHookResult extends HookResult {
	long startTime;
	int duration;
	
	public PerformanceHookResult(long startTime, int duration, ContextAssertion assertion, Exception error) {
		super(assertion, error);
		this.startTime = startTime;
		this.duration = duration;
	}
	
	public long getStartTime() {
		return startTime;
	}

	public int getDuration() {
		return duration;
	}
	
	
	public long getEnd() {
		return startTime + duration;
	}
}
