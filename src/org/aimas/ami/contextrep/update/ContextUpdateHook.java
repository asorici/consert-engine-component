package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.query.Dataset;

public abstract class ContextUpdateHook {
	protected ContextAssertion contextAssertion;
	
	
	public ContextUpdateHook(ContextAssertion contextAssertion) {
		this.contextAssertion = contextAssertion;
	}
	
	public ContextAssertion getContextAssertion() {
		return contextAssertion;
	}
	
	public abstract HookResult exec(Dataset contextStoreDataset);
}
