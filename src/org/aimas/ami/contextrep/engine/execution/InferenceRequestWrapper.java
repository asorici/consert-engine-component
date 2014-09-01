package org.aimas.ami.contextrep.engine.execution;

import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;
import org.aimas.ami.contextrep.engine.api.InferenceRequest;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.update.CheckInferenceHook;

public class InferenceRequestWrapper implements InferenceRequest, Comparable<InferenceRequest> {
	private long priority;
	
	long enqueueTimestamp;
	CheckInferenceHook inferenceRequest;
	
	
	public InferenceRequestWrapper(CheckInferenceHook inferenceRequest) {
		this.enqueueTimestamp = Engine.currentTimeMillis();
		this.inferenceRequest = inferenceRequest;
		
		// by default, priority of an inference request is its enqueue timestamp
		this.priority = enqueueTimestamp;
	}
	
	
	public CheckInferenceHook getInferenceRequest() {
		return inferenceRequest;
	}
	
	@Override
    public long getPriority() {
	    return priority;
    }


	@Override
    public void setPriority(long priority) {
	    this.priority = priority;
    }
	
	
	@Override
	public long getEnqueueTimestamp() {
		return enqueueTimestamp;
	}
	
	
	@Override
    public ContextDerivationRule getDerivationRule() {
	    return inferenceRequest.getDerivationRule();
    }
	
	
	@Override
    public int compareTo(InferenceRequest o) {
	    // since this is used in a priority queue if smallest first and we want 
		// higher priority to be first, we have to return the inverse comparisons
		
		if (priority < o.getPriority()) {
	    	return 1;
	    }
	    else if (priority > o.getPriority()) {
	    	return -1;
	    }
	    else {
	    	// if they are equal on priority, use the timestamp; older enqueues must be first
	    	if (enqueueTimestamp < o.getEnqueueTimestamp()) {
	    		return -1;
	    	}
	    	else if (enqueueTimestamp > o.getEnqueueTimestamp()) {
	    		return 1;
	    	}
	    }
	    
	    return 0;
    }
	
	
	@Override
    public int hashCode() {
        return inferenceRequest.getDerivationRule().hashCode();
    }
	
	
	@Override
    public boolean equals(Object obj) {
        if (this == obj) {
	        return true;
        }
        if (obj == null) {
	        return false;
        }
        if (!(obj instanceof InferenceRequestWrapper)) {
	        return false;
        }
        
        InferenceRequestWrapper other = (InferenceRequestWrapper) obj;
        if (!inferenceRequest.getDerivationRule().equals(other.getInferenceRequest().getDerivationRule())) {
	        return false;
        }
        
        return true;
    }
}
