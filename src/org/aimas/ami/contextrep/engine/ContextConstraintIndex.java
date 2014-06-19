package org.aimas.ami.contextrep.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.model.BinaryContextAssertion;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.utils.ConstraintsWrapper;
import org.aimas.ami.contextrep.utils.ContextAssertionFinder;
import org.aimas.ami.contextrep.utils.ContextAssertionGraph;
import org.aimas.ami.contextrep.utils.spin.ContextSPINQueryFinder;
import org.aimas.ami.contextrep.vocabulary.ConsertConstraint;
import org.topbraid.spin.model.Construct;
import org.topbraid.spin.model.ElementList;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.model.Template;
import org.topbraid.spin.model.TemplateCall;
import org.topbraid.spin.system.SPINModuleRegistry;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.vocabulary.SPIN;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

public class ContextConstraintIndex {
	private Map<ContextAssertion, ConstraintsWrapper> assertion2ConstraintMap;
	
	ContextConstraintIndex() {
		assertion2ConstraintMap = new HashMap<>();
	}
	
	public void addAssertionConstraint(ContextAssertion assertion, ConstraintsWrapper constraints) {
		assertion2ConstraintMap.put(assertion, constraints);
	}
	
	public ConstraintsWrapper getConstraints(ContextAssertion assertion) {
		return assertion2ConstraintMap.get(assertion);
	}
	
	
	/**
	 * Create an index of uniqueness or value constraints attached to a ContextAssertion. It provides a mapping
	 * between a ContextAssertion and the list of constraints attached to it.
	 * @param contextModelConstraints The ontology (with transitive inference enabled) that defines this context model
	 * @param contextAssertionIndex The index of ContextAssertions defined in the Context Model
	 * @return An {@link ContextAssertionIndex} instance that holds the index structure.
	 */
	public static ContextConstraintIndex create(ContextAssertionIndex contextAssertionIndex, OntModel contextModelConstraints) {
	    // create the ConstraintIndex instance
		ContextConstraintIndex constraintIndex = new ContextConstraintIndex();
		
		// add the spl:, spin: and sp: namespaces to the constraints model to be able to detect them
		OntModel extendedConstraintModel = Loader.ensureSPINImported(contextModelConstraints);
		//System.out.println(extendedConstraintModel.listImportedOntologyURIs());
		
		// make sure to register the templates as they will be searched for when collecting the constraints
		SPINModuleRegistry.get().registerAll(extendedConstraintModel, null);
		
		//String constrTemplateURI = "http://pervasive.semanticweb.org/ont/2013/09/adhocmeeting/models#uniquePersonLocationConstraint";
		//System.out.println(SPINModuleRegistry.get().getTemplate(constrTemplateURI, null).getBody());
		
		List<ContextAssertion> assertionList = contextAssertionIndex.getContextAssertions();
	    for (ContextAssertion assertion : assertionList) {
	    	if (assertion.isBinary()) {
	    		BinaryContextAssertion binaryAssertion = (BinaryContextAssertion) assertion;
	    		
	    		// for a binary assertion get the domain and range and see if they have any assigned constraints
	    		// we do this because for binary assertions (i.e. OWL object- or datatype properties) we
	    		// attach the constraints to one of the role playing Entities
	    		Resource domain = binaryAssertion.getDomainEntityResource();
	    		Resource range = binaryAssertion.getRangeEntityResource();
	    		
	    		Map<CommandWrapper, Map<String,RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String,RDFNode>>();
	    		
	    		Map<Resource, List<CommandWrapper>> constraintsMap = new HashMap<>();
	    		if (domain != null && domain.isURIResource()) {
	    			Resource anchorResource = domain;
	    			
	    			constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
                                  anchorResource, ConsertConstraint.CONSTRAINT, true, initialTemplateBindings, true));

	    			//constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
	    			//		anchorResource, ConsertConstraint.CONSTRAINT, true, true));
	    		}
	    		
	    		if (range != null && range.isURIResource() && (constraintsMap == null || constraintsMap.isEmpty())) {
	    			Resource anchorResource = range;
	    			constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
                            anchorResource, ConsertConstraint.CONSTRAINT, true, initialTemplateBindings, true));
	    			
	    			//constraintsMap.putAll(ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, 
	    			//		anchorResource, ConsertConstraint.CONSTRAINT, true, true));
	    		}
	    		
	    		if (constraintsMap != null && !constraintsMap.isEmpty()) {
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
	    		Resource anchorResource = assertion.getOntologyResource();
	    		Map<CommandWrapper, Map<String,RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String,RDFNode>>();
	    		
	    		Map<Resource, List<CommandWrapper>> constraintsMap = 
		    			ContextSPINQueryFinder.getClass2QueryMap(extendedConstraintModel, extendedConstraintModel, anchorResource, SPIN.constraint, true, initialTemplateBindings, true);
		    		
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
	    
		return constraintIndex;
    }
	
	
	/**
	 * For a binary assertion the constraints are added to the subject of the Property defining the assertion.
	 * Since the collection process gathers all constraints associated to the subject Resource we must filter out
	 * the ones who do not relate to <code>binaryAssertion</code>.
	 */
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
		
			/*
			 * The constraints are constructed as CONSTRUCT statements that create an instance of spin:ConstraintViolation
			 */
			Construct constructCommand =  spinCommand.as(Construct.class);
			ElementList whereElements = constructCommand.getWhere();
			Map<String, RDFNode> templateBindings = initialTemplateBindings.get(cmd);
			//Map<String, RDFNode> templateBindings = cmd.getTemplateBinding();
			
			ContextAssertionFinder ruleBodyFinder = new ContextAssertionFinder(whereElements, contextAssertionIndex, domainConstraintModel, templateBindings);
			
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
}
