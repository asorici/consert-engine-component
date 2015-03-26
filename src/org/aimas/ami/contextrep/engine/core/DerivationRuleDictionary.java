package org.aimas.ami.contextrep.engine.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.utils.ContextAssertionFinder;
import org.aimas.ami.contextrep.engine.utils.ContextAssertionGraph;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
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
	private Map<Resource, List<DerivationRuleWrapper>> entity2RuleMap;
	private Map<DerivationRuleWrapper, Resource> rule2EntityMap;
	private Map<ContextAssertion, List<DerivationRuleWrapper>> assertion2RuleMap;
	
	/**
	 * The map that specifies if the rules that derive a ContextAssertion are active or not
	 */
	private Map<ContextAssertion, Boolean> derivedAssertionActivationMap;
	
	private boolean activeByDefault = false;
	
	/**
	 * The mapping that binds a derived ContextAssertion with the list of derivation rules that derive it.
	 */
	private Map<ContextAssertion, List<DerivationRuleWrapper>> derivationRules;
	
	DerivationRuleDictionary() {
		entity2RuleMap = new HashMap<Resource, List<DerivationRuleWrapper>>();
		assertion2RuleMap = new HashMap<ContextAssertion, List<DerivationRuleWrapper>>();
		rule2EntityMap = new HashMap<DerivationRuleWrapper, Resource>();
		
		derivationRules = new HashMap<ContextAssertion, List<DerivationRuleWrapper>>();
		derivedAssertionActivationMap = new HashMap<ContextAssertion, Boolean>();
	}
	
	/* IMPORTANT TODO:
	 * 	- Order the derivation commands by their specificity - that is from the most specific 
	 * 	  derived context assertion to the most general one 
	 */
	
	public Map<ContextAssertion, List<DerivationRuleWrapper>> getAssertion2QueryMap() {
		return assertion2RuleMap;
	}
	
	public Map<Resource, List<DerivationRuleWrapper>> getEntity2QueryMap() {
		return entity2RuleMap;
	}
	
	public List<DerivationRuleWrapper> getDerivedAssertionRules(ContextAssertion derivedAssertion) {
		return derivationRules.get(derivedAssertion);
	}
	
	public List<DerivationRuleWrapper> getDerivationsForEntity(Resource entity) {
		return entity2RuleMap.get(entity);
	}
	
	public List<DerivationRuleWrapper> getDerivationsForBodyAssertion(ContextAssertion assertion) {
		return assertion2RuleMap.get(assertion);
	}
	
	public Resource getEntityForDerivation(DerivationRuleWrapper derivationWrapper) {
		return rule2EntityMap.get(derivationWrapper);
	}
	
	/**
	 * Return whether the derivation rules for a given <code>derivedAssertion</code> are active or not.
	 * @param derivedAssertion
	 * @return The activation status for rules that infer <code>derivedAssertion</code>, or the value
	 * 		of <code>activeByDefault</code>, if no specification is given for the <code>derivedAssertion</code>
	 */
	public boolean isDerivedAssertionActive(ContextAssertion derivedAssertion) {
		synchronized(derivedAssertionActivationMap) {
			Boolean active = derivedAssertionActivationMap.get(derivedAssertion);
			return active == null ? activeByDefault : active;
		}
	}
	
	public void setActiveByDefault(boolean active) {
		activeByDefault = active;
	}
	
	public void setDerivedAssertionActive(ContextAssertion derivedAssertion, boolean active) {
		synchronized(derivedAssertionActivationMap) {
			derivedAssertionActivationMap.put(derivedAssertion, active);
		}
	}
	
	public void addCommandForEntity(Resource entityResource, DerivationRuleWrapper derivationWrapper) {
		// add the derivation rule to the entity2Rule map
		List<DerivationRuleWrapper> entityCommands = entity2RuleMap.get(entityResource);
		if (entityCommands == null) {
			entityCommands = new ArrayList<DerivationRuleWrapper>();
			entityCommands.add(derivationWrapper);
			entity2RuleMap.put(entityResource, entityCommands);
		}
		else {
			entityCommands.add(derivationWrapper);
		}
		
		// add it to the list of derivation rules
		ContextAssertion derivedAssertion = derivationWrapper.getDerivedAssertion();
		List<DerivationRuleWrapper> rules = derivationRules.get(derivedAssertion);
		if (rules == null) {
			rules = new ArrayList<DerivationRuleWrapper>();
			rules.add(derivationWrapper);
			derivationRules.put(derivedAssertion, rules);
		}
		else {
			rules.add(derivationWrapper);
		}
	}
	
	public void setEntityForDerivation(DerivationRuleWrapper derivationWrapper, Resource entityResource) {
		rule2EntityMap.put(derivationWrapper, entityResource);
	}
	
	
	public void addDerivationForAssertion(ContextAssertion assertion, DerivationRuleWrapper derivedWrapper) {
		List<DerivationRuleWrapper> assertionCommands = assertion2RuleMap.get(assertion);
		if (assertionCommands == null) {
			assertionCommands = new ArrayList<DerivationRuleWrapper>();
			assertionCommands.add(derivedWrapper);
			assertion2RuleMap.put(assertion, assertionCommands);
		}
		else {
			assertionCommands.add(derivedWrapper);
		}
	}
	
	public void appendEntityQueryMap(Map<Resource, List<DerivationRuleWrapper>> map) {
		entity2RuleMap.putAll(map);
	}
	
	public void appendAssertionQueryMap(Map<ContextAssertion, List<DerivationRuleWrapper>> map) {
		assertion2RuleMap.putAll(map);
	}
	
	
	/**
	 * Create a DerivationRule dictionary which maps each ContextAssertion to the SPIN rules 
	 * in which it plays a role. The SPIN rules are selected from those attached
	 * by a <code>spin:deriveassertion</code> property to a ContextEntity of the context model given
	 * by <code>basicContextModel</code>.
	 * @param consertEngine The CONSERT Engine instance for which the derivation dictionary is built
	 * @param contextAssertionIndex The index of ContextAssertions defined in the Context Model
	 * @param contextModelRules The ontology defining Rules module of the domain Context Model
	 * @return A {@link DerivationRuleDictionary} instance which contains maps of ContextEntity to 
	 * list of SPIN Rules and ContextAssertion to list of SPIN Rules.
	 */
	public static DerivationRuleDictionary create(Engine consertEngine, ContextAssertionIndex contextAssertionIndex, OntModel contextModelRules) {
		DerivationRuleDictionary dict = new DerivationRuleDictionary();
		
		Map<CommandWrapper, Map<String,RDFNode>> initialTemplateBindings = new HashMap<CommandWrapper, Map<String,RDFNode>>();
		
		// build the extended Rules Module including the SPL, SP and SPIN namespaces
		OntModel extendedRulesModel = consertEngine.getModelLoader().ensureSPINImported(contextModelRules);
		
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
					new ContextAssertionFinder(whereElements, contextAssertionIndex, templateBindings);
				
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
				DerivationRuleWrapper derivationWrapper = new DerivationRuleWrapper(derivedAssertion, cmd, templateBindings);
				
				for (ContextAssertionGraph assertionGraph : bodyContextAssertions) {
					derivationWrapper.addBodyAssertion(assertionGraph.getAssertion());
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
