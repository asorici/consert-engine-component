package org.aimas.ami.contextrep.engine.api;

import java.util.Map;

import com.hp.hpl.jena.rdf.model.Resource;

public interface EngineQueryStats {
	/**
	 * Return a map detailing the number of currently enqueued queries for each type of 
	 * ContextAssertion that the CONSERT Engine knows about.
	 * @return Popularity map of ContextAssertions
	 */
	//public Map<ContextAssertion, Integer> currentPopularity();
	
	/**
	 * Return a map detailing the number of queries received during the last RUN_WINDOW milliseconds,
	 * for each type of ContextAssertion (given as ontology resource) that the CONSERT Engine knows about.
	 * @return ContextAssertions query popularity map
	 */
	public Map<Resource, Integer> nrQueries();
	
	/**
	 * Return a map detailing the current number of subscriptions registered for each type of 
	 * ContextAssertion (given as ontology resource) that the CONSERT Engine knows about.
	 * @return ContextAssertion subscriptions count map
	 */
	public Map<Resource, Integer> nrSubscriptions();
	
	/**
	 * Return the time since the last query was received for a each ContextAssertion 
	 * (given as ontology resource) that the CONSERT Engine knows about.
	 * @return Map of time since last received query per ContextAssertion or null if no query 
	 * received for that ConsertAssertion.
	 */
	public Map<Resource, Long> timeSinceLastQuery();
	
	
	/**
	 * Return the number of successful query executions during the last RUN_WINDOW for each ContextAssertion 
	 * type (given as ontology resource) that the CONSERT Engine knows about.
	 * @return Map of number of successful query executions per ContextAssertion or null if no query 
	 * received for that ConsertAssertion.
	 */
	public Map<Resource, Integer> nrSuccessfulQueries();
	
	
}
