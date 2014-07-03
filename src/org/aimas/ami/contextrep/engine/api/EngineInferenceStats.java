package org.aimas.ami.contextrep.engine.api;

import java.util.Map;

import org.aimas.ami.contextrep.model.ContextAssertion;

public interface EngineInferenceStats {
	
	public long runWindowWidth();
	
	/**
	 * Get the ContextAssertion type that was most recently successfully derived. 
	 * @return
	 */
	public ContextAssertion lastDerivation();
	
	/**
	 * Return a map detailing the number of currently enqueued queries for each type of 
	 * derived ContextAssertion that the CONSERT Engine knows about.
	 * @return Popularity map of derived ContextAssertions
	 */
	public Map<ContextAssertion, Integer> currentPopularity();
	
	/**
	 * Get a map detailing the ratio of successful execution over number of execution requests
	 * made during the last RUN_WINDOW milliseconds.
	 * @return
	 */
	public Map<ContextAssertion, Double> ratioSuccessfulExec(); 
}
