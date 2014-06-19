package org.aimas.ami.contextrep.engine.api;

import java.util.concurrent.TimeUnit;

import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.ontology.OntResource;

public interface StatsHandler {
	
	/* ================ Statistics for ContextAssertion updates ================ */
	public int updatesPerTimeUnit(ContextAssertion assertion, TimeUnit measureUnit);
	
	public int updatesPerTimeUnit(OntResource assertionResource, TimeUnit measureUnit);
	
	public int timeSinceLastUpdate(ContextAssertion assertion, TimeUnit measureUnit);
	
	public int timeSinceLastUpdate(OntResource assertionResource, TimeUnit measureUnit);
	
	
	
	/* ================ Statistics for ContextAssertion queries ================ */
	public int requestsPerTimeUnit(ContextAssertion assertion, TimeUnit measureUnit);
	
	public int requestsPerTimeUnit(OntResource assertionResource, TimeUnit measureUnit);
	
	public int subscriptionCount(ContextAssertion assertion);
	
	public int subscriptionCount(OntResource assertionResource);
	
	public int timeSinceLastQuery(ContextAssertion assertion, TimeUnit measureUnit);
	
	public int timeSinceLastQuery(OntResource assertionResource, TimeUnit measureUnit);
	
	
	/* ================ Statistics for time since last ontology reasoning event ================ */
	/**
	 * Get the time since ontology reasoning was last performed on this <code>assertion</code>.
	 * Return the time in the chosen <code>unit</code>. 
	 * @param assertion
	 * @param unit
	 * @return Time elapsed since last ontology reasoning for <code>assertion</code> expressed in <code>unit</code>
	 * 		time units.
	 */
	public int timeSinceReasoning(ContextAssertion assertion, TimeUnit measureUnit);
	
	
	/**
	 * Get the time since ontology reasoning was last performed on the ContextAssertion given by 
	 * the <code>assertionResource</code>.
	 * Return the time in the chosen <code>measureUnit</code>. 
	 * @param assertionResource
	 * @param unit
	 * @return Time elapsed since last ontology reasoning for <code>assertion</code> expressed in <code>unit</code>
	 * 		time units.
	 */
	public int timeSinceReasoning(OntResource assertionResource, TimeUnit measureUnit);
	
	
}
