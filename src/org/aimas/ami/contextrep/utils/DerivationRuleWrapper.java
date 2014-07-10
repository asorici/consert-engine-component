package org.aimas.ami.contextrep.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.topbraid.spin.util.CommandWrapper;

import com.hp.hpl.jena.rdf.model.RDFNode;

public class DerivationRuleWrapper implements ContextDerivationRule {
	
	private CommandWrapper derivationCommand;
	private ContextAssertion derivedAssertion;
	private List<ContextAssertion> bodyAssertions;
	private Map<String, RDFNode> commandBindings;
	
	public DerivationRuleWrapper(ContextAssertion derivedAssertion, CommandWrapper derivationCommand, 
			Map<String, RDFNode> commandBindings) {
		this.derivedAssertion = derivedAssertion;
		this.bodyAssertions = new ArrayList<ContextAssertion>();
		
		this.derivationCommand = derivationCommand;
		this.commandBindings = commandBindings;
    }
	
	public void addBodyAssertion(ContextAssertion assertion) {
		bodyAssertions.add(assertion);
	}
	
	public List<ContextAssertion> getBodyAssertions() {
		return bodyAssertions;
	}
	
	public CommandWrapper getDerivationCommand() {
		return derivationCommand;
	}
	
	@Override
	public ContextAssertion getDerivedAssertion() {
		return derivedAssertion;
	}
	
	public Map<String, RDFNode> getCommandBindings() {
		return commandBindings;
	}
	
	@Override
	public int hashCode() {
		return derivationCommand.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		return (other != null && other instanceof DerivationRuleWrapper && 
			derivationCommand.equals(((DerivationRuleWrapper)other).getDerivationCommand()));
	}
}
