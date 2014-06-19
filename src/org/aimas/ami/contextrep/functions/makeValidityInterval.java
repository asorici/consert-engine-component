package org.aimas.ami.contextrep.functions;

import java.util.Calendar;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.datatype.CalendarIntervalListType;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.sparql.expr.ExprEvalException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;

public class makeValidityInterval extends FunctionBase2 {
	
	@Override
	public NodeValue exec(NodeValue v1, NodeValue v2) {
		if (!v1.isDateTime()) {
			throw new ExprEvalException("interval start timestamp: argument not a datetime: " + v1) ;
		}
		
		if (!v2.isDateTime()) {
			throw new ExprEvalException("interval start timestamp: argument not a datetime: " + v2) ;
		}
		
		Calendar t1 = v1.getDateTime().toGregorianCalendar();
		Calendar t2 = v2.getDateTime().toGregorianCalendar();
		
		CalendarInterval interval = new CalendarInterval(t1, true, t2, true);
		CalendarIntervalList validity = new CalendarIntervalList();
		validity.add(interval);
		
		Node validityIntervalLiteral = Node.createUncachedLiteral(validity, CalendarIntervalListType.intervalListType);
		return NodeValue.makeNode(validityIntervalLiteral);
	}
}
