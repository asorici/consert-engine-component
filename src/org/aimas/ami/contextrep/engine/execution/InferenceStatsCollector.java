package org.aimas.ami.contextrep.engine.execution;

import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;

public interface InferenceStatsCollector {
	public void markInferenceExecution(ContextDerivationRule rule, boolean successful);
}
