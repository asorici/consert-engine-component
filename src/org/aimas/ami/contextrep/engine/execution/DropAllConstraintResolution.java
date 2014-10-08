package org.aimas.ami.contextrep.engine.execution;

import org.aimas.ami.contextrep.engine.api.ConstraintResolutionService;
import org.aimas.ami.contextrep.engine.api.ContextStore;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.aimas.ami.contextrep.model.ViolationAssertionWrapper;

/**
 * The simple, default implementation for the ValueConstraint resolution service. We simply drop the newly inserted
 * ContextAssertion instance. 
 * @author alex
 *
 */
public class DropAllConstraintResolution implements ConstraintResolutionService {
	private static DropAllConstraintResolution instance;
	
	private DropAllConstraintResolution() {}
	
	@Override
	public ViolationAssertionWrapper resolveViolation(ContextConstraintViolation constraintViolation, ContextStore contextStore) {
		return null;
	}
	
	public static DropAllConstraintResolution getInstance() {
		if (instance == null) {
			instance = new DropAllConstraintResolution();
		}
		
		return instance;
	}
}
