package org.aimas.ami.contextrep.functions;

import java.util.Calendar;
import java.util.TimeZone;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.datatype.CalendarIntervalListType;
import org.aimas.ami.contextrep.datatype.InfinityMarkerType;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.sparql.expr.ExprEvalException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;

public class makeValidityInterval extends FunctionBase2 {
	
	@Override
	public NodeValue exec(NodeValue v1, NodeValue v2) {
		if (!v1.isDateTime() && !v1.getDatatypeURI().equals(InfinityMarkerType.infinityMarkerTypeURI)) {
			throw new ExprEvalException("interval start timestamp - argument not a datetime nor infinity marker: " + v1) ;
		}
		
		if (!v2.isDateTime() && !v2.getDatatypeURI().equals(InfinityMarkerType.infinityMarkerTypeURI)) {
			throw new ExprEvalException("interval end timestamp: argument not a datetime nor infinity marker: " + v2) ;
		}
		
		Calendar t1 = null;
		Calendar t2 = null;
		
		if (v1.isDateTime()) {
			t1 = v1.getDateTime().toGregorianCalendar(TimeZone.getTimeZone("GMT"), null, null);
		}
		else {
			if (((String)v1.asNode().getLiteral().getValue()).equals(InfinityMarkerType.POSITIVE_INFTY)) {
				throw new ExprEvalException("interval start timestamp cannot be +infinity: " + v1) ;
			}
		}
		
		if (v2.isDateTime()) {
			t2 = v2.getDateTime().toGregorianCalendar(TimeZone.getTimeZone("GMT"), null, null);
		}
		else {
			if (((String)v2.asNode().getLiteral().getValue()).equals(InfinityMarkerType.NEGATIVE_INFTY)) {
				throw new ExprEvalException("interval end timestamp cannot be -infinity: " + v2) ;
			}
		}
		
		CalendarInterval interval = new CalendarInterval(t1, t1 != null, t2, t2 != null);
		CalendarIntervalList validity = new CalendarIntervalList();
		validity.add(interval);
		
		Node validityIntervalLiteral = Node.createUncachedLiteral(validity, CalendarIntervalListType.intervalListType);
		return NodeValue.makeNode(validityIntervalLiteral);
	}
}
