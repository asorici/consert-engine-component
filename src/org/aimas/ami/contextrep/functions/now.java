package org.aimas.ami.contextrep.functions;

import java.util.Calendar;

import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase0;

public class now extends FunctionBase0 {


	@Override
	public NodeValue exec() {
		Calendar now = Calendar.getInstance();
		
		NodeValue nv = NodeValue.makeDateTime(now);
		return nv;
	}

}
