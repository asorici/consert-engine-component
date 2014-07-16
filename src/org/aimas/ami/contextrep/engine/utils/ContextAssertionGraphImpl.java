package org.aimas.ami.contextrep.engine.utils;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.topbraid.spin.model.impl.NamedGraphImpl;

import com.hp.hpl.jena.enhanced.EnhGraph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.rdf.model.Resource;

public class ContextAssertionGraphImpl extends NamedGraphImpl implements
		ContextAssertionGraph {
	
	protected ContextAssertion contextAssertion;
	
	public ContextAssertionGraphImpl(Node node, EnhGraph graph, ContextAssertion assertionOntologyResource) {
		super(node, graph);
		this.contextAssertion = assertionOntologyResource;
	}

	@Override
	public ContextAssertion getAssertion() {
		return contextAssertion;
	}
	
	@Override
	public Resource getAssertionIdentifier() {
		return getNameNode();
	}
	
	
	@Override
	public String toString() {
		return contextAssertion.toString();
	}
}
