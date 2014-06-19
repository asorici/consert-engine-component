package org.aimas.ami.contextrep.utils;

import java.util.List;
import java.util.Map;

import org.topbraid.spin.util.CommandWrapper;

import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

public class ConstraintsWrapper {
	
	private List<CommandWrapper> constraintCommands;
	private Resource anchorResource;
	private Map<CommandWrapper, Map<String,RDFNode>> constraintTemplateBindings;
	
	
	public ConstraintsWrapper(List<CommandWrapper> constraintCommands, Resource anchorResource,
            Map<CommandWrapper, Map<String, RDFNode>> constraintTemplateBindings) {
	    
	    this.constraintCommands = constraintCommands;
	    this.anchorResource = anchorResource;
	    this.constraintTemplateBindings = constraintTemplateBindings;
    }
	
	
	/*
	public ConstraintsWrapper(List<CommandWrapper> constraintCommands, Resource anchorResource) {
	    this.constraintCommands = constraintCommands;
	    this.anchorResource = anchorResource;
    }
	*/
	
	public List<CommandWrapper> getConstraintCommands() {
		return constraintCommands;
	}
	
	
	public Resource getAnchorResource() {
		return anchorResource;
	}

	
	public Map<CommandWrapper, Map<String, RDFNode>> getConstraintTemplateBindings() {
		return constraintTemplateBindings;
	}
	
	
	@Override
	public int hashCode() {
		return anchorResource.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		return (other != null && other instanceof ConstraintsWrapper && 
			anchorResource.equals(((ConstraintsWrapper)other).getAnchorResource()));
	}
}
