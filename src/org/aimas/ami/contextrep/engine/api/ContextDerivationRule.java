package org.aimas.ami.contextrep.engine.api;

import org.aimas.ami.contextrep.model.ContextAssertion;

public interface ContextDerivationRule {
	public ContextAssertion getDerivedAssertion();
}
