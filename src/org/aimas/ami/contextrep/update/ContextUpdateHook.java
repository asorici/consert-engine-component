package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.update.UpdateRequest;

public abstract class ContextUpdateHook {
	
	protected UpdateRequest insertionRequest;
	protected ContextAssertion contextAssertion;
	protected Node contextAssertionUUID;
	protected int updateMode;
	
	public ContextUpdateHook(UpdateRequest updateRequest, ContextAssertion contextAssertion, 
			Node contextAssertionUUID, int updateMode) {
		this.insertionRequest = updateRequest;
		this.contextAssertion = contextAssertion;
		this.contextAssertionUUID = contextAssertionUUID;
		this.updateMode = updateMode;
	}
	
	public UpdateRequest getInsertionRequest() {
		return insertionRequest;
	}
	
	public ContextAssertion getContextAssertion() {
		return contextAssertion;
	}
	
	public Node getContextAssertionUUID() {
		return contextAssertionUUID;
	}
	
	public int getHookId() {
		return insertionRequest.hashCode();
	}
	
	public HookResult exec(Dataset contextStoreDataset) {
		long start = System.currentTimeMillis();
		HookResult result = doHook(contextStoreDataset);
		long end = System.currentTimeMillis();
		
		result.setDuration(end - start);
		return result;
	}
	
	public abstract HookResult doHook(Dataset contextStoreDataset);
}
