package org.aimas.ami.contextrep.engine.utils;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.topbraid.spin.model.NamedGraph;

import com.hp.hpl.jena.rdf.model.Resource;

public interface ContextAssertionGraph extends NamedGraph {
	
	/**
	 * Get the ContextAssertion whose instance is contained in this graph 
	 */
	public ContextAssertion getAssertion();
	
	/**
	 * Gets the URI Resource or Variable that holds the identifier of this
	 * ContextAssertion.  If it's a Variable, then this method will typecast
	 * it into an instance of Variable.
	 * @return a URI Resource or Variable identifying this ContextAssertion 
	 */
	public Resource getAssertionIdentifier();
}
