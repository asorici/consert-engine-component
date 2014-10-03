package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.graph.Node;

public class ContinuityResult extends HookResult {
	
	private Node extendedAssertionUUID;
	
	public ContinuityResult(ContextAssertion assertion, Exception error, Node extendedAssertionUUID) {
	    super(assertion, error);
	    this.extendedAssertionUUID = extendedAssertionUUID;
    }
	
	public boolean hasContinuity() {
		return extendedAssertionUUID != null;
	}
	
	public Node getExtendedAssertionUUID() {
		return extendedAssertionUUID;
	}
}
