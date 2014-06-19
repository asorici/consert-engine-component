package org.aimas.ami.contextrep.engine;

import org.aimas.ami.contextrep.model.ContextAssertion;

public interface ContextInsertListener {
	public void notifyAssertionInserted(ContextAssertion assertion);
}
