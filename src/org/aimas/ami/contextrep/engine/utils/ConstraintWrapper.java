package org.aimas.ami.contextrep.engine.utils;

import java.util.Map;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.topbraid.spin.util.CommandWrapper;

import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class ConstraintWrapper {
	
	private CommandWrapper constraintCommand;
	private ContextAssertion constrainedAssertion;
	private Resource anchorResource;
	private Map<String,RDFNode> constraintBindings;
	
	
	public ConstraintWrapper(CommandWrapper constraintCommand, ContextAssertion constrainedAssertion, 
			Resource anchorResource, Map<String, RDFNode> constraintBindings) {
	    
	    this.constraintCommand = constraintCommand;
	    this.constrainedAssertion = constrainedAssertion;
	    this.anchorResource = anchorResource;
	    this.constraintBindings = constraintBindings;
    }
	
	
	/*
	public ConstraintsWrapper(List<CommandWrapper> constraintCommands, Resource anchorResource) {
	    this.constraintCommands = constraintCommands;
	    this.anchorResource = anchorResource;
    }
	*/
	
	public CommandWrapper getConstraintCommand() {
		return constraintCommand;
	}
	
	public ContextAssertion getConstrainedAssertion() {
		return constrainedAssertion;
	}
	
	public Resource getAnchorResource() {
		return anchorResource;
	}
	
	public Map<String, RDFNode> getConstraintBindings() {
		return constraintBindings;
	}
	
	@Override
	public int hashCode() {
		return constraintCommand.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		return (other != null && other instanceof ConstraintWrapper && 
			constraintCommand.equals(((ConstraintWrapper)other).getConstraintCommand()));
	}
	
	@Override
	public String toString() {
		Resource templateCall = constraintCommand.getSource();
		Resource templateType = templateCall.getPropertyResourceValue(RDF.type);
		
		if (templateType != null) {
			return templateType.getURI();
		}
		
		return "Unknown Constraint Template";
	}
}
