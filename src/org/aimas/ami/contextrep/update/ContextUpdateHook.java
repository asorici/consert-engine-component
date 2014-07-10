package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;

public abstract class ContextUpdateHook {
	protected ContextAssertion contextAssertion;
	protected Node contextAssertionUUID;
	
	
	public ContextUpdateHook(ContextAssertion contextAssertion, Node contextAssertionUUID) {
		this.contextAssertion = contextAssertion;
		this.contextAssertionUUID = contextAssertionUUID;
	}
	
	public ContextAssertion getContextAssertion() {
		return contextAssertion;
	}
	
	public Node getContextAssertionUUID() {
		return contextAssertionUUID;
	}
	
	public abstract HookResult exec(Dataset contextStoreDataset);
}
