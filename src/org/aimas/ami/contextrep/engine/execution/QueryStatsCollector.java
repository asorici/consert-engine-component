package org.aimas.ami.contextrep.engine.execution;

import java.util.Set;

import org.aimas.ami.contextrep.model.ContextAssertion;

public interface QueryStatsCollector {
	public void markQueryExecution(Set<ContextAssertion> assertions, boolean successful);
}
