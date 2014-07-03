package org.aimas.ami.contextrep.engine.api;


public interface InferenceRequest {
	
	public long getEnqueueTimestamp();
	
	public ContextDerivationRule getDerivationRule();
	
	public long getPriority();
	
	public void setPriority(long priority);
}
