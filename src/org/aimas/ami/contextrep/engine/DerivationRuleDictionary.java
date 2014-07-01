package org.aimas.ami.contextrep.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.utils.ContextAssertionFinder;
import org.aimas.ami.contextrep.utils.ContextAssertionGraph;
import org.aimas.ami.contextrep.utils.DerivedAssertionWrapper;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.aimas.ami.contextrep.vocabulary.ConsertRules;
import org.topbraid.spin.model.Construct;
import org.topbraid.spin.model.ElementList;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.model.Template;
import org.topbraid.spin.model.TemplateCall;
import org.topbraid.spin.model.TripleTemplate;
import org.topbraid.spin.system.SPINModuleRegistry;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.SPINQueryFinder;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

public class DerivationRuleDictionary {
	private Map<Resource, List<DerivedAssertionWrapper>> entity2RuleMap;
	private Map<DerivedAssertionWrapper, Resource> rule2EntityMap;
	private Map<ContextAssertion, List<DerivedAssertionWrapper>> assertion2RuleMap;
	
	DerivationRuleDictionary() {
		entity2RuleMap = new HashMap<>();
		assertion2RuleMap = new HashMap<>();
		rule2EntityMap = new HashMap<>();
	}
	
	/* IMPORTANT TODO:
	 * 	- Order the derivation commands by their specificity - that is from the most specific 
	 * 	  derived context assertion to the most general one
	 * 
	 *  - include a mapping from Derived Assertion to list of body assertions - this list is to be
	 *    used in computing the schedule for running the associated derivation rule when using
	 *    the Prioritization Policy for Context Derivation Rules
	 */
	
	public Map<ContextAssertion, List<DerivedAssertionWrapper>> getAssertion2QueryMap() {
		return assertion2RuleMap;
	}
	
	public Map<Resource, List<DerivedAssertionWrapper>> getEntity2QueryMap() {
		return entity2RuleMap;
	}
	
	public List<DerivedAssertionWrapper> getDerivationsForEntity(Resource entity) {
		return entity2RuleMap.get(entity);
	}
	
	public List<DerivedAssertionWrapper> getDerivationsForAssertion(ContextAssertion assertion) {
		return assertion2RuleMap.get(assertion);
	}
	
	public Resource getEntityForDerivation(DerivedAssertionWrapper derivationWrapper) {
		return rule2EntityMap.get(derivationWrapper);
	}
	
	public void addCommandForEntity(Resource entityResource, DerivedAssertionWrapper derivationWrapper) {
		List<DerivedAssertionWrapper> entityCommands = entity2RuleMap.get(entityResource);
		if (entityCommands == null) {
			entityCommands = new ArrayList<DerivedAssertionWrapper>();
			entityCommands.add(derivationWrapper);
			entity2RuleMap.put(entityResource, entityCommands);
		}
		else {
			entityCommands.add(derivationWrapper);
		}
	}
	
	public void setEntityForDerivation(DerivedAssertionWrapper derivationWrapper, Resource entityResource) {
		rule2EntityMap.put(derivationWrapper, entityResource);
	}
	
	public void addDerivationForAssertion(ContextAssertion assertion, DerivedAssertionWrapper derivedWrapper) {
		List<DerivedAssertionWrapper> assertionCommands = assertion2RuleMap.get(assertion);
		if (assertionCommands == null) {
			assertionCommands = new ArrayList<DerivedAssertionWrapper>();
			assertionCommands.add(derivedWrapper);
			assertion2RuleMap.put(assertion, assertionCommands);
		}
		else {
			assertionCommands.add(derivedWrapper);
		}
	}
	
	public void appendEntityQueryMap(Map<Resource, List<DerivedAssertionWrapper>> map) {
		entity2RuleMap.putAll(map);
	}
	
	public void appendAssertionQueryMap(Map<ContextAssertion, List<DerivedAssertionWrapper>> map) {
		assertion2RuleMap.putAll(map);
	}
	
	
	/**
	 * Create a DerivationRule dictionary which maps each ContextAssertion to the SPIN rules 
	 * in which it plays a role. The SPIN rules are selected from those attached
	 * by a <code>spin:deriveassertion</code> property to a ContextEntity of the context model given
	 * by <code>basicContextModel</code>.
	 * @param contextAssertionIndex The index of ContextAssertions defined in the Context Model
	 * @param contextModelRules The ontology defining Rules module of the domain Context Model
	 * @return A {@link DerivationRuleDictionary} instance which contains maps of ContextEntity to 
	 * list of SPIN Rules and ContextAssertion to list of SPIN Rules.
	 */
	public static DerivationRuleDictionary create(ContextAssertionIndex contextAssertionIndex, OntModel contextModelRules) {
		DerivationRuleDictionary dict = new DerivationRuleDictionary();
		
		Map<CommandWrapper, Map<String,RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String,RDFNode>>();
		
		// build the extended Rules Module including the SPL, SP and SPIN namespaces
		OntModel extendedRulesModel = Loader.ensureSPINImported(contextModelRules);
		
		// make sure to register the templates as they will be searched for when collecting the constraints
		SPINModuleRegistry.get().registerAll(extendedRulesModel, null);
		
		// Collect spin:deriveassertion rules in the extended Rules module of the domain Context Model 
		Map<Resource,List<CommandWrapper>> cls2Query = SPINQueryFinder.getClass2QueryMap(
			extendedRulesModel, extendedRulesModel, ConsertRules.DERIVE_ASSERTION, false, initialTemplateBindings, false);
		
		//Map<Resource,List<CommandWrapper>> cls2Query = SPINQueryFinder.getClass2QueryMap(
		//	extendedRulesModel, extendedRulesModel, ConsertRules.DERIVE_ASSERTION, false, false);
		
		
		// build map for ContextAssertion to SPIN:Rule list
		for (Resource res : cls2Query.keySet()) {
			//System.out.println(res.getURI() + ": ");
			//System.out.println(res.getClass().getName() + ": ");
			List<CommandWrapper> cmds = cls2Query.get(res);
			
			for (CommandWrapper cmd : cmds) {
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
				 * The derivation rules are constructed as sp:Construct statement with a SP.templates predicate
				 * defining the TemplateTriples in the head of the CONSTRUCT statement
				 */
				Construct constructCommand =  spinCommand.as(Construct.class);
				ElementList whereElements = constructCommand.getWhere();
				List<TripleTemplate> constructedTriples = constructCommand.getTemplates();
				
				Map<String, RDFNode> templateBindings = initialTemplateBindings.get(cmd);
				//Map<String, RDFNode> templateBindings = cmd.getTemplateBinding();
				ContextAssertion derivedAssertion = null; 
				
				ContextAssertionFinder ruleBodyFinder = 
					new ContextAssertionFinder(whereElements, contextAssertionIndex, extendedRulesModel, templateBindings);
				
				// run context assertion rule body finder and collect results
				ruleBodyFinder.run();
				Set<ContextAssertionGraph> bodyContextAssertions = ruleBodyFinder.getResult();
				
				// look through asserted triples as part of the CONSTRUCT for the derived assertion and retrieve
				// its resource type
				for (TripleTemplate tpl : constructedTriples) {
					if (tpl.getPredicate().equals(ConsertCore.CONTEXT_ASSERTION_RESOURCE)) {
						
						RDFNode assertionRes = tpl.getObjectResource();
						if (SPINFactory.isVariable(assertionRes)) {
							String varName = SPINFactory.asVariable(assertionRes).getName();
							if (templateBindings != null && templateBindings.get(varName) != null) { 
								assertionRes = templateBindings.get(varName);
							}
						}
						
						OntResource derivedAssertionResource = extendedRulesModel.getOntResource(assertionRes.asResource());
						derivedAssertion = contextAssertionIndex.getAssertionFromResource(derivedAssertionResource);
						
						break;	// break when we find the contextassertion:assertionResource property, as there is only one
					}
				}
				
				// there is only one head ContextAssertion - the derived one
				DerivedAssertionWrapper derivationWrapper = new DerivedAssertionWrapper(derivedAssertion, cmd, templateBindings);
				
				for (ContextAssertionGraph assertionGraph : bodyContextAssertions) {
					// System.out.println(assertion.getAssertionResource().getURI() + ": " + assertion.getAssertionType());
					dict.addDerivationForAssertion(assertionGraph.getAssertion(), derivationWrapper);
				}
				
				// add all ContextEntity to SPIN:Rule list mappings
				dict.addCommandForEntity(res, derivationWrapper);
				
				// add reverse map from SPIN:Rule to ContextEntity
				dict.setEntityForDerivation(derivationWrapper, res);
			}
		}
		
		return dict;
	}
}
