package org.aimas.ami.contextrep.engine.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.engine.api.ConstraintResolutionService;
import org.aimas.ami.contextrep.engine.utils.ConstraintWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.vocabulary.ConsertConstraint;
import org.topbraid.spin.system.SPINModuleRegistry;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.SPINQueryFinder;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

public class ContextConstraintIndex {
	public static enum ConstraintType {
		Integrity, Uniqueness, Value
	}
	
	public static final String ANCHOR_RESOURCE_PARAM 		= "anchorResource";
	public static final String TRIGGER_ASSERTION_UUID_PARAM = "triggerAssertionUUID";
	public static final String TRIGGER_ASSERTION_TYPE_PARAM = "triggerAssertionType";
	
	private Map<ContextAssertion, List<ConstraintWrapper>> assertion2ConstraintMap;
	private Map<ContextAssertion, ConstraintResolutionService> uniquenessResolutionServiceMap;
	private Map<ContextAssertion, ConstraintResolutionService> integrityResolutionServiceMap;
	private Map<ContextAssertion, ConstraintResolutionService> valueResolutionServiceMap;
	
	private ConstraintResolutionService defaultUniquenessResolutionService;
	private ConstraintResolutionService defaultIntegrityResolutionService;
	
	ContextConstraintIndex() {
		assertion2ConstraintMap = new HashMap<ContextAssertion, List<ConstraintWrapper>>();
		
		uniquenessResolutionServiceMap = new HashMap<ContextAssertion, ConstraintResolutionService>();
		integrityResolutionServiceMap = new HashMap<ContextAssertion, ConstraintResolutionService>();
		valueResolutionServiceMap = new HashMap<ContextAssertion, ConstraintResolutionService>();
	}
	
	public void addAssertionConstraint(ContextAssertion assertion, ConstraintWrapper constraint) {
		List<ConstraintWrapper> assertionConstraints = assertion2ConstraintMap.get(assertion);
		
		if (assertionConstraints != null) {
			assertionConstraints.add(constraint);
		}
		else {
			assertionConstraints = new LinkedList<ConstraintWrapper>();
			assertionConstraints.add(constraint);
			assertion2ConstraintMap.put(assertion, assertionConstraints);
		}
	}
	
	public List<ConstraintWrapper> getConstraints(ContextAssertion assertion) {
		return assertion2ConstraintMap.get(assertion);
	}
	
	public Map<ContextAssertion, List<ConstraintWrapper>> getAllConstraints() {
		return assertion2ConstraintMap;
	}
	
	// ============================== Default constraint resolution services ============================ 
	public void setDefaultUniquenessResolutionService(ConstraintResolutionService uniquenessResolutionService) {
	    this.defaultUniquenessResolutionService = uniquenessResolutionService;
    }
	
	public ConstraintResolutionService getDefaultUniquenessResolutionService() {
	    return defaultUniquenessResolutionService;
    }
	
	public void setDefaultIntegrityResolutionService(ConstraintResolutionService integrityResolutionService) {
		this.defaultIntegrityResolutionService = integrityResolutionService;
    }
	
	public ConstraintResolutionService getDefaultIntegrityResolutionService() {
	    return defaultIntegrityResolutionService;
    }
	
	// ============================== ContextAssertion specific constraint resolution services ============================
	public void setUniquenessResolutionService(ContextAssertion assertion, ConstraintResolutionService resolutionService) {
		uniquenessResolutionServiceMap.put(assertion, resolutionService);
	}
	
	public ConstraintResolutionService getUniquenessResolutionService(ContextAssertion assertion) {
		return uniquenessResolutionServiceMap.get(assertion);
	}
	
	public void setIntegrityResolutionService(ContextAssertion assertion, ConstraintResolutionService resolutionService) {
		integrityResolutionServiceMap.put(assertion, resolutionService);
	}
	
	public ConstraintResolutionService getIntegrityResolutionService(ContextAssertion assertion) {
		return integrityResolutionServiceMap.get(assertion);
	}
	
	public void setValueResolutionService(ContextAssertion assertion, ConstraintResolutionService resolutionService) {
		valueResolutionServiceMap.put(assertion, resolutionService);
	}
	
	public ConstraintResolutionService getValueResolutionService(ContextAssertion assertion) {
		return valueResolutionServiceMap.get(assertion);
	}
		
	
	/**
	 * Create an index of uniqueness or value constraints attached to a ContextAssertion. It provides a mapping
	 * between a ContextAssertion and the list of constraints attached to it.
	 * @param consertEngine The CONSERT Engine instance for which the constraint index is built
	 * @param contextModelConstraints The ontology (with transitive inference enabled) that defines this context model
	 * @param contextAssertionIndex The index of ContextAssertions defined in the Context Model
	 * @return An {@link ContextAssertionIndex} instance that holds the index structure.
	 */
	public static ContextConstraintIndex create(Engine consertEngine, ContextAssertionIndex contextAssertionIndex, OntModel contextModelConstraints) {
	    // create the ConstraintIndex instance
		ContextConstraintIndex constraintIndex = new ContextConstraintIndex();
		
		// add the spl:, spin: and sp: namespaces to the constraints model to be able to detect them
		OntModel extendedConstraintModel = consertEngine.getModelLoader().ensureSPINImported(contextModelConstraints);
		//System.out.println(extendedConstraintModel.listImportedOntologyURIs());
		
		// make sure to register the templates as they will be searched for when collecting the constraints
		SPINModuleRegistry.get().registerAll(extendedConstraintModel, null);
		
		//if (consertEngine.getApplicationIdentifier().contains("AlicePersonal")) {
		//	String constrTemplateURI = "http://pervasive.semanticweb.org/ont/2015/01/alicepersonal/constraint#AvailabilityStatusConstraint";
		//	System.out.println(SPINModuleRegistry.get().getTemplate(constrTemplateURI, null).getBody());
		//}
		
		// Collect spin:contextconstraint rules in the extended constraint module of the domain Context Model 
		Map<CommandWrapper, Map<String,RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String,RDFNode>>();
		Map<Resource,List<CommandWrapper>> cls2Query = SPINQueryFinder.getClass2QueryMap(
				extendedConstraintModel, extendedConstraintModel, ConsertConstraint.CONSTRAINT, false, initialTemplateBindings, false);
		
		// Collected mapping is already established between the ContextAssertion type and the list of constraints defined for it.
		// What we need to do is inspect it, in order to create the ConstraintWrapper for each one (most important aspect is
		// setting the anchor resource to be used during execution of the constraint)
		for (Resource assertionRes : cls2Query.keySet()) {
			ContextAssertion assertion = contextAssertionIndex.getAssertionFromResource(assertionRes);
			List<CommandWrapper> assertionConstraints = cls2Query.get(assertionRes);
			
			for (CommandWrapper constraintCommand : assertionConstraints) {
				Map<String,RDFNode> constraintBindings = initialTemplateBindings.get(constraintCommand);
				Resource anchorResource = constraintBindings.get(ANCHOR_RESOURCE_PARAM).asResource();
				
				ConstraintWrapper constraintWrapper = new ConstraintWrapper(constraintCommand, assertion, anchorResource, constraintBindings);
				constraintIndex.addAssertionConstraint(assertion, constraintWrapper);
			}
		}
		
		/*
		List<ContextAssertion> assertionList = contextAssertionIndex.getContextAssertions();
	    for (ContextAssertion assertion : assertionList) {
	    	if (assertion.isBinary()) {
	    		BinaryContextAssertion binaryAssertion = (BinaryContextAssertion) assertion;
	    		
	    		Resource domain = binaryAssertion.getDomainEntityResource();
	    		Resource range = binaryAssertion.getRangeEntityResource();
	    		
	    		Map<CommandWrapper, Map<String,RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String,RDFNode>>();
	    		
	    		Map<Resource, List<CommandWrapper>> constraintsMap = new HashMap<Resource, List<CommandWrapper>>();
	    		
	    		if (domain != null && domain.isURIResource()) {
	    			Resource anchorResource = domain.inModel(extendedConstraintModel);
	    			
	    			constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
                                  anchorResource, ConsertConstraint.CONSTRAINT, true, initialTemplateBindings, true));

	    			//constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
	    			//		anchorResource, ConsertConstraint.CONSTRAINT, true, true));
	    		}
	    		
	    		if (range != null && range.isURIResource()) {
	    			Resource anchorResource = range.inModel(extendedConstraintModel);
	    			
	    			constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
                            anchorResource, ConsertConstraint.CONSTRAINT, true, initialTemplateBindings, true));
	    			
	    			//constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
	    			//		anchorResource, ConsertConstraint.CONSTRAINT, true, true));
	    		}
	    		
	    		if (constraintsMap != null && !constraintsMap.isEmpty()) {
	    			System.out.println("["+ ContextConstraintIndex.class.getSimpleName() +"] INFO: constraintsMap for assertion <" + assertion + ">: " + constraintsMap);
	    			
	    			for (Resource anchorResource : constraintsMap.keySet()) {
		    			List<CommandWrapper> constraints = constraintsMap.get(anchorResource);
		    			List<CommandWrapper> filteredConstraints = filterBinaryConstraintsFor(constraints, binaryAssertion, contextAssertionIndex, extendedConstraintModel,  initialTemplateBindings);
		    			//List<CommandWrapper> filteredConstraints = filterBinaryConstraintsFor(constraints, binaryAssertion, contextAssertionIndex, extendedConstraintModel);
		    			
		    			ConstraintsWrapper constraintsWrapper = new ConstraintsWrapper(filteredConstraints, anchorResource, initialTemplateBindings);
		    			//ConstraintsWrapper constraintsWrapper = new ConstraintsWrapper(filteredConstraints, anchorResource);
	    				constraintIndex.addAssertionConstraint(binaryAssertion, constraintsWrapper);
	    			}
	    		}
	    	}
	    	else {
	    		// we have a Unary or Nary assertion. Both are ontology classes, so the constraint can be directly
	    		// attached to them
	    		Resource anchorResource = assertion.getOntologyResource().inModel(extendedConstraintModel);
	    		Map<CommandWrapper, Map<String,RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String,RDFNode>>();
	    		
	    		Map<Resource, List<CommandWrapper>> constraintsMap = ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
		    			anchorResource, ConsertConstraint.CONSTRAINT, true, initialTemplateBindings, true);
		    		
	    		//Map<Resource, List<CommandWrapper>> constraintsMap = 
	    		//	ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, anchorResource, SPIN.constraint, true, true);
	    		
	    		
	    		if (constraintsMap != null && !constraintsMap.isEmpty()) {
	    			List<CommandWrapper> constraints = constraintsMap.get(anchorResource);
	    			ConstraintsWrapper constraintsWrapper = new ConstraintsWrapper(constraints, anchorResource, initialTemplateBindings);
	    			//ConstraintsWrapper constraintsWrapper = new ConstraintsWrapper(constraints, anchorResource);
    				constraintIndex.addAssertionConstraint(assertion, constraintsWrapper);
	    		}
	    	}
	    }
	    */
		
		return constraintIndex;
    }
	
	
	/**
	 * For a binary assertion the constraints are added to the subject of the Property defining the assertion.
	 * Since the collection process gathers all constraints associated to the subject Resource we must filter out
	 * the ones who do not relate to <code>binaryAssertion</code>.
	 */
	/*
	private static List<CommandWrapper> filterBinaryConstraintsFor(List<CommandWrapper> constraints,
            BinaryContextAssertion binaryAssertion, ContextAssertionIndex contextAssertionIndex, OntModel domainConstraintModel, 
            Map<CommandWrapper, Map<String, RDFNode>> initialTemplateBindings) {
		List<CommandWrapper> filteredConstraints = new ArrayList<>();
		
		for (CommandWrapper cmd : constraints) {
			org.topbraid.spin.model.Command spinCommand = null;
			Template template = null;
			
			TemplateCall templateCall = SPINFactory.asTemplateCall(cmd.getSource());
			if (templateCall != null) {
				template = templateCall.getTemplate();
				if(template != null) {
					spinCommand = template.getBody();
				}
			}
			else {
				spinCommand = SPINFactory.asCommand(cmd.getSource());
			}
		
			
			// The constraints are constructed as CONSTRUCT statements that create an instance of spin:ConstraintViolation
			Construct constructCommand =  spinCommand.as(Construct.class);
			ElementList whereElements = constructCommand.getWhere();
			Map<String, RDFNode> templateBindings = initialTemplateBindings.get(cmd);
			//Map<String, RDFNode> templateBindings = cmd.getTemplateBinding();
			
			ContextAssertionFinder ruleBodyFinder = new ContextAssertionFinder(whereElements, contextAssertionIndex, templateBindings);
			
			// run context assertion rule body finder and collect results
			ruleBodyFinder.run();
			Set<ContextAssertionGraph> bodyContextAssertions = ruleBodyFinder.getResult();
			
			// see if the list of collected context assertions contains our binaryAssertion
			initialTemplateBindings.remove(cmd);							// attempted remove of bindings for cmd
			for (ContextAssertionGraph assertionGraph : bodyContextAssertions) {
				if (assertionGraph.getAssertion().equals(binaryAssertion.getOntologyResource())) {
					// if the collected assertions really include our binaryAssertion
					filteredConstraints.add(cmd);							// add the command to the filtered ones
					initialTemplateBindings.put(cmd, templateBindings);		// and add the mapping back to the template bindings
					break;
				}
			}
		}
		
		return filteredConstraints;
    }
    */
}
