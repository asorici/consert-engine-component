package org.aimas.ami.contextrep.engine.api;

import java.util.Map;

public interface EngineInferenceStats {
	
	/**
	 * Get the ContextAssertion type that was most recently successfully derived. 
	 * @return
	 */
	public ContextDerivationRule lastDerivation();
	
	/**
	 * Get a map detailing the number of derivation rule calls made during the last RUN_WINDOW milliseconds
	 * @return
	 */
	public Map<ContextDerivationRule, Integer> nrDerivations();
	
	/**
	 * Get a map detailing the number of successful derivation rule calls during the last RUN_WINDOW milliseconds
	 * @return
	 */
	public Map<ContextDerivationRule, Integer> nrSuccessfulDerivations(); 
}
