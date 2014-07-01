package org.aimas.ami.contextrep.utils;

import java.util.Map;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.topbraid.spin.util.CommandWrapper;

import com.hp.hpl.jena.rdf.model.RDFNode;

public class DerivedAssertionWrapper {
	
	private CommandWrapper derivationCommand;
	private ContextAssertion derivedAssertion;
	private Map<String, RDFNode> commandBindings;
	
	public DerivedAssertionWrapper(ContextAssertion derivedAssertion, CommandWrapper derivationCommand, 
			Map<String, RDFNode> commandBindings) {
		this.derivedAssertion = derivedAssertion;
		this.derivationCommand = derivationCommand;
		this.commandBindings = commandBindings;
    }

	public CommandWrapper getDerivationCommand() {
		return derivationCommand;
	}

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
		return (other != null && other instanceof DerivedAssertionWrapper && 
			derivationCommand.equals(((DerivedAssertionWrapper)other).getDerivationCommand()));
	}
}
