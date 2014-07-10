package org.aimas.ami.contextrep.update;

import java.util.LinkedList;
import java.util.List;

import org.aimas.ami.contextrep.engine.core.ContextARQFactory;
import org.aimas.ami.contextrep.engine.core.ContextConstraintIndex;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.core.Loader;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.aimas.ami.contextrep.utils.ConstraintsWrapper;
import org.aimas.ami.contextrep.utils.ContextStoreUtil;
import org.aimas.ami.contextrep.utils.spin.ContextSPINConstraints;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.statistics.SPINStatistics;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.update.UpdateRequest;

public class CheckConstraintHook extends ContextUpdateHook {
	private UpdateRequest insertionRequest;
	
	public CheckConstraintHook(ContextAssertion contextAssertion, Node contextAssertionUUID, UpdateRequest insertionRequest) {
		super(contextAssertion, contextAssertionUUID);
		this.insertionRequest = insertionRequest;
	}
	
	@Override
	public ConstraintResult exec(Dataset contextStoreDataset) {
		long start = System.currentTimeMillis();
		
		// see if this context assertion has any constraints attached
		ContextConstraintIndex constraintIndex = Engine.getConstraintIndex();
		ConstraintsWrapper constraints = constraintIndex.getConstraints(contextAssertion);  
		
		if (constraints != null) {
			/* 
			 * The uniqueness and value context constraints operate on individual ContextAssertions.
			 * This is why the query model for constraint checking can be composed of only those
			 * parts of the ContextStore that relate to the ContextAssertion:
			 * 		- the individual ContextAssertion instances, wrapped in their named graphs
			 * 		- the ContextAssertion Store 
			 * 		- the EntityStore for the ContextStore
			 * 
			 * In addition, we add the SPIN ontology set to be on the safe side (in case we call any
			 * functions or reference SPIN templates in the constraints)
			 */    
			Model assertionModel = ContextStoreUtil.unionModelForAssertion(contextAssertion, contextStoreDataset);
			Model constraintContextModel = Loader.ensureSPINImported(assertionModel);
			
			ARQFactory.set(new ContextARQFactory(contextStoreDataset));
			
			List<SPINStatistics> stats = new LinkedList<>();
			
			List<ContextConstraintViolation> constraintViolations = 
				ContextSPINConstraints.check(constraintContextModel, contextAssertion, contextAssertionUUID, 
						insertionRequest, constraints, stats);
			
			if (!constraintViolations.isEmpty()) {
				// TODO: do something useful with the detected violations
				//System.out.println("[INFO] Constraint violations detected for assertion: " + contextAssertion);
				
				return new ConstraintResult(contextAssertion, null, constraintViolations);
				// TODO: performance collect
				//long end = System.currentTimeMillis();
				//return new ConstraintResult(start, (int)(end - start), false, true, true);
			}
		}
		
		return new ConstraintResult(contextAssertion, null, null);
		// TODO: performance collect
		//long end = System.currentTimeMillis();
		//return new ConstraintResult(start, (int)(end - start), false, false, false);
	}
}
