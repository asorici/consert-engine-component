package org.aimas.ami.contextrep.engine.api;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.model.ContextAssertion.ContextAssertionType;
import org.topbraid.spin.util.CommandWrapper;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.reasoner.ValidityReport;

public interface CommandHandler {
	
	// Access to ContextStore and Context Model
	////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Get access to a snapshot of the runtime ContextStore. This method should be used to get access
	 * to the ContextStore in order to run the CommandRules which implement domain specific 
	 * shift-of-attention policies.
	 * @return The {@link Dataset} representing the snapshot of the runtime ContextStore
	 */
	public Dataset getRuntimeContextStore();
	
	/**
	 * Get the type (sensed, profiled, derived or dynamic) of the ContextAssertion 
	 * identified by <code>assertionResource</code>
	 * @param assertionResource
	 * @return
	 */
	public ContextAssertionType getAssertionType(Resource assertionResource);
	
	/**
	 * Determine the ContextAssertions referenced in the body of rules that infer the assertion identified by
	 * <code>derivedAssertionRes</code>.
	 * @param derivedAssertionRes
	 * @return The set of referenced ContextAssertions identified by their ontology resources.
	 */
	public Set<Resource> getReferencedAssertions(Resource derivedAssertionRes);
	
	/**
	 * Determine the ContextAssertions referenced by a SPIN-encoded control command (used by the external 
	 * middleware to control the provisioning cycle of the CONSERT Engine).
	 * @param controlCommand
	 * @param templateBindings
	 * @return The set of referenced ContextAssertions identified by their ontology resources.
	 */
	public Set<Resource> getControlCommandAssertions(CommandWrapper controlCommand, Map<String, RDFNode> templateBindings);
	
	// Configuration of CONSERT Engine dynamic parameters 
	////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Set the default RUN_WINDOW width used for collecting query statistics.
	 * @param runWindow
	 */
	public void setDefaultQueryRunWindow(long runWindow);
	
	/**
	 * Set the RUN_WINDOW width used for collecting query statistics for the ContextAssertion 
	 * specified by <code>assertionResource</code>.
	 * @param assertionResource
	 * @param runWindow
	 */
	public void setSpecificQueryRunWindow(Resource assertionResource, long runWindow);
	
	/**
	 * Set the default RUN_WINDOW width used for collecting inference statistics.
	 * @param runWindow
	 */
	public void setDefaultInferenceRunWindow(long runWindow);
	
	/**
	 * Set the RUN_WINDOW width used for collecting inference statistics for the ContextAssertion 
	 * specified by <code>assertionResource</code>.
	 * @param assertionResource
	 * @param runWindow
	 */
	public void setSpecificInferenceRunWindow(Resource assertionResource, long runWindow);
	
	// Configuration of CONSERT Engine dynamic services (e.g. inference priority computation, constraint resolution service) 
	////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Set the InferenceRequest priority provider service type to be used by the 
	 * CONSERT Engine inference service. The CONSERT Engine component will look up the new service
	 * type (which should exist in the OSGi runtime) and set it accordingly.
	 * @param priorityProvider
	 */
	public void setInferenceSchedulingType(String priorityProviderType);
	
	/**
	 * Set the Uniqueness Constraint Resolution service type to be used by the CONSERT Engine constraint resolution service.
	 * The CONSERT Engine component will look up the new service type (which should exist in the OSGi runtime) and set it accordingly.
	 * @param resolutionServiceName The name of the resolution service which will be used to filter for the appropriate OSGi service at runtime.
	 */
	public void setDefaultUniquenessConstraintResolution(String resolutionServiceName);
	
	/**
	 * Set the Integrity Constraint Resolution service type to be used by the CONSERT Engine constraint resolution service.
	 * The CONSERT Engine component will look up the new service type (which should exist in the OSGi runtime) and set it accordingly.
	 * @param resolutionServiceName The name of the resolution service which will be used to filter for the appropriate OSGi service at runtime.
	 */
	public void setDefaultIntegrityConstraintResolution(String resolutionServiceName);
	
	/**
	 * Set a ContextAssertion specific Uniqueness Constraint Resolution service type to be used by the CONSERT Engine constraint resolution service.
	 * The CONSERT Engine component will look up the new service type (which should exist in the OSGi runtime) and set it accordingly.
	 * @param assertionResource The ontology resource identifying the type of ContextAssertion for which we assign constraint resolution service
	 * @param resolutionServiceName The name of the resolution service which will be used to filter for the appropriate OSGi service at runtime.
	 */
	public void setSpecificUniquenessConstraintResolution(Resource assertionResource, String resolutionServiceName);
	
	/**
	 * Set a ContextAssertion specific Integrity Constraint Resolution service type to be used by the CONSERT Engine constraint resolution service.
	 * The CONSERT Engine component will look up the new service type (which should exist in the OSGi runtime) and set it accordingly.
	 * @param assertionResource The ontology resource identifying the type of ContextAssertion for which we assign constraint resolution service
	 * @param resolutionServiceName The name of the resolution service which will be used to filter for the appropriate OSGi service at runtime.
	 */
	public void setSpecificIntegrityConstraintResolution(Resource assertionResource, String resolutionServiceName);
	
	/**
	 * Set a ContextAssertion specific Value Constraint Resolution service type to be used by the CONSERT Engine constraint resolution service.
	 * The CONSERT Engine component will look up the new service type (which should exist in the OSGi runtime) and set it accordingly.
	 * @param assertionResource The ontology resource identifying the type of ContextAssertion for which we assign constraint resolution service
	 * @param resolutionServiceName The name of the resolution service which will be used to filter for the appropriate OSGi service at runtime.
	 */
	public void setSpecificValueConstraintResolution(Resource assertionResource, String resolutionServiceName);
	
	
	// Command (reasoning, cleanup, insertion and inference activate/deactivate) execution triggers
	///////////////////////////////////////////////////////////////////////////////////////////////
	public void setAssertionInsertActiveByDefault(boolean activeByDefault);
	
	public void setAssertionInferenceActiveByDefault(boolean activeByDefault);
	
	/**
	 * Mark the state of update activity for the ContextAssertion given by <code>assertionResource</code>.
	 * @param assertionResource
	 * @param active Parameter set to <b>true</b> if updates for the ContextAssertion are enabled and <b>false</b>
	 * otherwise.
	 */
	public void setAssertionActive(Resource assertionResource, boolean active);
	
	/**
	 * Set the active/inactive state of <b>all</b> the known ContextDerivationRules which derive the 
	 * ContextAssertion specified by <code>derivedAssertionResource</code> 
	 * @param derivedAssertionResource
	 */
	public void setDerivationRuleActive(Resource derivedAssertionResource, boolean active);
	
	
	/**
	 * Trigger an ontology reasoning process for the ContextAssertion specified by 
	 * <code>assertionResource</code>. 
	 * @param assertionResource The ontology resource identifying the type of ContextAssertion for which
	 * to perform ontology reasoning.
	 * @return
	 * @throws CommandException
	 */
	public ValidityReport triggerOntReasoning(Resource assertionResource) throws CommandException;
	
	
	/**
	 * Clean (remove) all ContextAssertion instances defined by the ontology resource <code>assertionResource</code> 
	 * older than <code>timeThreshold</code> from the runtime ContextStore. 
	 * Synchronization with persistent ContextStore (if active) will occur. 
	 * @param assertion
	 * @param timeThreshold
	 */
	public void cleanRuntimeContextStore(Resource assertionResource, Calendar timeThreshold) throws CommandException;
	
	
	/**
	 * Clean (remove) all ContextAssertion instances older than <code>timeThreshold</code> from the runtime ContextStore. 
	 * Synchronization with persistent ContextStore (if active) will occur.@param timeThreshold
	 */
	public void cleanRuntimeContextStore(Calendar timeThreshold) throws CommandException;
}
