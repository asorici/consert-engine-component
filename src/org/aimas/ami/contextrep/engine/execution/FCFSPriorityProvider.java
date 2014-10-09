package org.aimas.ami.contextrep.engine.execution;

import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;
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
	
	
	public static FCFSPriorityProvider getInstance() {
		if (instance == null) {
			instance = new FCFSPriorityProvider();
		}
		
		return instance;
	}

	@Override
    public Map<ContextDerivationRule, Integer> computePriorities(List<ContextDerivationRule> derivationRules, EngineInferenceStats inferenceStats) {
	    // A null map will imply a timestamp based comparison
		return null;
    }
}
