package org.aimas.ami.contextrep.engine.api;

import java.util.Calendar;

import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.reasoner.ValidityReport;

public interface CommandHandler {
	
	public ValidityReport triggerOntReasoning(ContextAssertion assertion) throws CommandException;
	
	public ValidityReport triggerOntReasoning(OntResource assertionResource) throws CommandException;
	
	/**
	 * Clean (remove) all ContextAssertion instances of type <code>assertion</code> older than <code>timeThreshold</code>
	 * from the runtime ContextStore. Synchronization with persistent ContextStore (if active) will occur. 
	 * @param assertion
	 * @param timeThreshold
	 */
	public void cleanRuntimeContextStore(ContextAssertion assertion, Calendar timeThreshold) throws CommandException;
	
	/**
	 * Clean (remove) all ContextAssertion instances defined by the ontology resource <code>assertionResource</code> 
	 * older than <code>timeThreshold</code> from the runtime ContextStore. 
	 * Synchronization with persistent ContextStore (if active) will occur. 
	 * @param assertion
	 * @param timeThreshold
	 */
	public void cleanRuntimeContextStore(OntResource assertionResource, Calendar timeThreshold) throws CommandException;
	
	
	/**
	 * Clean (remove) all ContextAssertion instances older than <code>timeThreshold</code> from the runtime ContextStore. 
	 * Synchronization with persistent ContextStore (if active) will occur.@param timeThreshold
	 */
	public void cleanRuntimeContextStore(Calendar timeThreshold) throws CommandException;
}
