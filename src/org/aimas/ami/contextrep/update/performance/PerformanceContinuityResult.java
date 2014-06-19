package org.aimas.ami.contextrep.update.performance;

import org.aimas.ami.contextrep.model.ContextAssertion;


public class PerformanceContinuityResult extends PerformanceHookResult {

	public PerformanceContinuityResult(long startTime, int duration, ContextAssertion assertion, Exception error) {
	    super(startTime, duration, assertion, error);
    }
	
}
