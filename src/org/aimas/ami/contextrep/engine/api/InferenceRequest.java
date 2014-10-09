package org.aimas.ami.contextrep.engine.api;


public interface InferenceRequest {
	
	public long getEnqueueTimestamp();
	
	public ContextDerivationRule getDerivationRule();
	
}
