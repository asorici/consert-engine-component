package org.aimas.ami.contextrep.update;

import java.util.LinkedList;
import java.util.List;

import org.aimas.ami.contextrep.engine.core.ContextARQFactory;
import org.aimas.ami.contextrep.engine.core.ContextConstraintIndex;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.utils.ConstraintWrapper;
import org.aimas.ami.contextrep.engine.utils.ContextStoreUtil;
import org.aimas.ami.contextrep.engine.utils.spin.ContextSPINConstraints;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.statistics.SPINStatistics;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.update.UpdateRequest;

public class CheckConstraintHook extends ContextUpdateHook {
	
	public CheckConstraintHook(Engine consertEngine, UpdateRequest insertionRequest, ContextAssertion contextAssertion, 
			Node contextAssertionUUID, int updateMode) {
		super(consertEngine, insertionRequest, contextAssertion, contextAssertionUUID, updateMode);
	}
	
	@Override
	public ConstraintResult doHook(Dataset contextStoreDataset) {
		// see if this context assertion has any constraints attached
		ContextConstraintIndex constraintIndex = consertEngine.getConstraintIndex();
		List<ConstraintWrapper> constraints = constraintIndex.getConstraints(contextAssertion);  
		
		if (constraints != null) {
			//if (consertEngine.getApplicationIdentifier().contains("AlicePersonal")) {
			//	System.out.println("["+ getClass().getSimpleName() +"] CHECKING CONSTRAINTS FOR CONTEXTASSERTION " + contextAssertion.getOntologyResource());
			//}
			
			/* 
			 * The uniqueness and value context constraints operate on individual ContextAssertions.
			 * This is why the query model for constraint checking can be composed of only those
			 * parts of the ContextStore that relate to the ContextAssertion:
			 * 		- the individual ContextAssertion instances, wrapped in their named graphs
			 * 		- the ContextAssertion Store 
			 * 		- the EntityStore for the ContextStore
			 */
			// Model constraintContextModel = ContextStoreUtil.unionModelForAssertion(consertEngine, contextAssertion, contextStoreDataset);
			
			// The above is true, but since we have the whole contextStore under transaction and IntegrityConstraints may operate on two different types of assertion
			// we just create a UnionModel of the entire contextStore like we do for ContextDerivationRules.
			Model constraintContextModel = ContextStoreUtil.getUnionModel(contextStoreDataset);
			
			ARQFactory.set(new ContextARQFactory(contextStoreDataset));
			
			List<SPINStatistics> stats = new LinkedList<SPINStatistics>();
			
			List<ContextConstraintViolation> constraintViolations = 
				ContextSPINConstraints.check(consertEngine, constraintContextModel, contextAssertion, contextAssertionUUID, 
						insertionRequest, constraints, stats);
			
			if (!constraintViolations.isEmpty()) {
				// TODO: do something useful with the detected violations
				System.out.println("[" + getClass().getSimpleName() + "] INFO: Constraint violations ("+ constraintViolations.size() +
						") detected for assertion: " + contextAssertion);
				
				return new ConstraintResult(contextAssertion, null, constraintViolations);
			}
		}
		
		return new ConstraintResult(contextAssertion, null, null);
	}
}
