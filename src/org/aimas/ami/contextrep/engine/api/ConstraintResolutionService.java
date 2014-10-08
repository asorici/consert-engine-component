package org.aimas.ami.contextrep.engine.api;

import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.aimas.ami.contextrep.model.ViolationAssertionWrapper;

import com.hp.hpl.jena.rdf.model.Resource;

public interface ConstraintResolutionService {
	public static final String RESOLUTION_TYPE = "type";
	
	/**
	 * Implementation of the resolution policy for a constraint conflict involving two ContextAssertion instances. The method will return the 
	 * URI identifier of the ContextAssertion instance which is to be kept after the resolution policy has been applied, or <code>null</code> if no instance is to be kept.
	 * @param constraintViolation	The violation instance containing the information related to the conflicting ContextAssertion instances, the UpdateRequest that triggered
	 * the new insertion, the {@link Resource} identifying the constraint template (the SPARQL expression defining the constraint violation).
	 * @param contextStore 		A wrapper over a snapshot of CONSERT Engine runtime ContextStore, taken at the moment this resolution service is invoked.
	 * @return The ViolationAssertionWrapper containing the details about the ContextAssertion instance which is kept, as part of the resolution policy, 
	 * 		or <code>null</code> if no instance is to be kept.
	 */
	public ViolationAssertionWrapper resolveViolation(ContextConstraintViolation constraintViolation, ContextStore contextStore);
}
