package org.aimas.ami.contextrep.functions;

import com.hp.hpl.jena.sparql.expr.ExprEvalException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;

public class timestampPermitsContinuity extends FunctionBase2 {
	private static final long THRESHOLD = 5000;
	
	
	@Override
	public NodeValue exec(NodeValue v1, NodeValue v2) {
		if (!v1.isDateTime()) {
			throw new ExprEvalException("timestamp value: argument not a datetime: " + v1) ;
		}
		
		if (!v2.isDateTime()) {
			throw new ExprEvalException("timestamp value: argument not a datetime: " + v2) ;
		}
		
		long val1 = v1.getDateTime().toGregorianCalendar().getTimeInMillis();
		long val2 = v2.getDateTime().toGregorianCalendar().getTimeInMillis();
		
		
		
		if (Math.abs(val1 - val2) <= THRESHOLD) {
			return NodeValue.makeBoolean(true);
		}
		
		return NodeValue.makeBoolean(false);
	}
	
}
