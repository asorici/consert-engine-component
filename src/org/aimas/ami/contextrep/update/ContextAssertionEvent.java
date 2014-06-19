package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.engine.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.graph.GraphEvents;
import com.hp.hpl.jena.graph.Node;

public class ContextAssertionEvent extends GraphEvents {
	public static final String CONTEXT_ASSERTION_ADDED = "context-assertion-added"; 
	public static final String CONTEXT_ASSERTION_REMOVED = "context-assertion-removed";
	public static final String CONTEXT_ANNOTATION_ADDED = "context-annotation-added";
	public static final String CONTEXT_ASSERTION_INFERRED = "context-assertion-inferred";
	
	public static final Object CONTEXT_ASSERTION_EXECUTE_HOOKS = new Object();
	
	public ContextAssertionEvent(String title, Node graphNode) {
	    super(title, graphNode);
    }
	
	public ContextAssertion getAssertion() {
		Node graphNode = (Node)content;
		return Engine.getContextAssertionIndex().getAssertionFromGraphUUID(graphNode);
	}
}
