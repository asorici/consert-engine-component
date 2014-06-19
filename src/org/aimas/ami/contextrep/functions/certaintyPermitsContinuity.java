package org.aimas.ami.contextrep.functions;

import com.hp.hpl.jena.sparql.expr.ExprEvalException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;

public class certaintyPermitsContinuity extends FunctionBase2 {
	private static final double THRESHOLD = 0.1; 
	
	@Override
	public NodeValue exec(NodeValue v1, NodeValue v2) {
		if (!v1.isDouble()) {
			throw new ExprEvalException("certainty value: argument not a double: " + v1) ;
		}
		
		if (!v2.isDouble()) {
			throw new ExprEvalException("certainty value: argument not a double: " + v2) ;
		}
		
		double val1 = v1.getDouble();
		double val2 = v2.getDouble();
		
		if (Math.abs(val1 - val2) <= THRESHOLD) {
			return NodeValue.makeBoolean(true);
		}
		
		return NodeValue.makeBoolean(false);
	}
	
}
