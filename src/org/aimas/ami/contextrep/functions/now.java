package org.aimas.ami.contextrep.functions;

import java.util.Calendar;
import java.util.TimeZone;

import org.aimas.ami.contextrep.engine.core.Engine;

import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase0;

public class now extends FunctionBase0 {


	@Override
	public NodeValue exec() {
		Calendar now = Engine.now();
		now.setTimeZone(TimeZone.getTimeZone("Europe/Bucharest"));
		//SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		//formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		//System.out.println("["+ getClass().getName() +"] CALL FOR NOW: " + formatter.format(now.getTime()));
		
		NodeValue nv = NodeValue.makeDateTime(now);
		return nv;
	}

}
