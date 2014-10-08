package org.aimas.ami.contextrep.engine.execution;

import java.util.List;

import org.aimas.ami.contextrep.engine.api.EngineInferenceStats;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.api.InferenceRequest;

/**
 * Provides a First Come - First Served (FCFS) priority ordering {@link InferenceRequest}s
 * @author alex
 */
public class FCFSPriorityProvider implements InferencePriorityProvider {
	private static FCFSPriorityProvider instance;
	
	private FCFSPriorityProvider() {}
	
	@Override
    public void computePriorities(List<? extends InferenceRequest> requestList, EngineInferenceStats inferenceStats) {
	    // effectively do nothing, as the request list is ordered by default by its enqueue timestamp (FCFS)
    }
	
	public static FCFSPriorityProvider getInstance() {
		if (instance == null) {
			instance = new FCFSPriorityProvider();
		}
		
		return instance;
	}
}
