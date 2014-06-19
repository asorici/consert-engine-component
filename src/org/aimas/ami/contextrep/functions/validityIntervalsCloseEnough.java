package org.aimas.ami.contextrep.functions;

import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.datatype.CalendarIntervalListType;

import com.hp.hpl.jena.sparql.expr.ExprEvalException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;

public class validityIntervalsCloseEnough extends FunctionBase2 {

	@Override
	public NodeValue exec(NodeValue v1, NodeValue v2) {
		if (!v1.isLiteral()) {
			throw new ExprEvalException("interval: argument not a literal: " + v1) ;
		}
		
		if (!v2.isLiteral()) {
			throw new ExprEvalException("interval: argument not a literal: " + v2) ;
		}
		
		if (!v1.getDatatypeURI().equals(CalendarIntervalListType.intervalListTypeURI)) {
			throw new ExprEvalException("interval: argument not a intervalListType: " + v1);
		}
		
		if (!v2.getDatatypeURI().equals(CalendarIntervalListType.intervalListTypeURI)) {
			throw new ExprEvalException("interval: argument not a intervalListType: " + v2);
		}
		
		CalendarIntervalList intervalList1 = (CalendarIntervalList)v1.asNode().getLiteral().getValue();
		CalendarIntervalList intervalList2 = (CalendarIntervalList)v2.asNode().getLiteral().getValue();
		
		if (intervalList1.closeEnoughTo(intervalList2)) {
			return NodeValue.booleanReturn(true);
		}
		
		return NodeValue.booleanReturn(false);
	}
}
