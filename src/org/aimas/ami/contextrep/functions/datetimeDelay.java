package org.aimas.ami.contextrep.functions;

import java.util.Calendar;
import java.util.TimeZone;

import org.aimas.ami.contextrep.utils.ContextModelUtils;

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
		
		
		//Calendar timestamp = v1.getDateTime().toGregorianCalendar(TimeZone.getTimeZone("Europe/Bucharest"), null, null);
		Calendar timestamp = v1.getDateTime().toGregorianCalendar(TimeZone.getTimeZone("GMT"), null, null);
		int delay = v2.getInteger().intValue();
		
		//SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		//formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
	    //System.out.println("["+ getClass().getName() + "] calling delay with timestamp: " + formatter.format(timestamp.getTime()));
		
		Calendar delayedTimestamp = (Calendar)timestamp.clone();
		delayedTimestamp.add(Calendar.SECOND, delay);
		
		String delayedTimestampLex = ContextModelUtils.calendarToXSDString(delayedTimestamp);
		NodeValue nv = NodeValue.makeDateTime(delayedTimestampLex);
		
		//System.out.println("["+ getClass().getName() + "] obtained node value: " + nv);
		
		return nv;
	}

}
