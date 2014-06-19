package org.aimas.ami.contextrep.functions;

import java.util.Calendar;

import com.hp.hpl.jena.sparql.expr.ExprEvalException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;

public class datetimeDelay extends FunctionBase2 {
	
	@Override
	public NodeValue exec(NodeValue v1, NodeValue v2) {	
		
		if (!v1.isDateTime()) {
			throw new ExprEvalException("timestamp: argument not a datetime: " + v1) ;
		}
		
		if (!v2.isInteger()) {
			throw new ExprEvalException("delay in seconds: argument not an integer value: " + v2) ;
		}
		
		Calendar timestamp = v1.getDateTime().toGregorianCalendar();
		int delay = v2.getInteger().intValue();
		
		Calendar delayedTimestamp = (Calendar)timestamp.clone();
		delayedTimestamp.add(Calendar.SECOND, delay);
		
		NodeValue nv = NodeValue.makeDateTime(delayedTimestamp); 
		return nv;
	}

}
