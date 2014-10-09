package org.aimas.ami.contextrep.engine.api;

import java.util.List;
import java.util.Map;

public interface InferencePriorityProvider {
	public static final String PRIORITY_PROVIDER_TYPE = "type";
	
	public static final int IMMEDIATE = 0;
	
	public Map<ContextDerivationRule, Integer> computePriorities(List<ContextDerivationRule> derivationRules, EngineInferenceStats inferenceStats);
}
