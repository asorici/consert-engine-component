package org.aimas.ami.contextrep.engine.api;

import java.util.List;

public interface InferencePriorityProvider {
	public static final int IMMEDIATE = 0;
	
	public void computePriorities(List<? extends InferenceRequest> requestList, EngineInferenceStats inferenceStats);
}
