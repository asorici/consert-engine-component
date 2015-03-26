package org.aimas.ami.contextrep.functions;

import java.util.Calendar;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.utils.ContextModelUtils;

import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.Function;
import com.hp.hpl.jena.sparql.function.FunctionBase0;
import com.hp.hpl.jena.sparql.function.FunctionFactory;

public class now extends FunctionBase0 {

	private Engine consertEngine;
	
	public now(Engine consertEngine) {
		this.consertEngine = consertEngine;
	}
	
	@Override
	public NodeValue exec() {
		Calendar now = consertEngine.now();
		
		//now.setTimeZone(TimeZone.getTimeZone("Europe/Bucharest"));
		//SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		//formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		//System.out.println("["+ getClass().getName() +"] CALL FOR NOW: " + formatter.format(now.getTime()));
		
		String nowLex = ContextModelUtils.calendarToXSDString(now);
		NodeValue nv = NodeValue.makeDateTime(nowLex);
		
		//NodeValue nv = NodeValue.makeDateTime(now);
		//System.out.println("["+ getClass().getName() +"] CALL FOR NOW NODE VALUE: " + nv);
		
		return nv;
	}
	
	public static class nowFactory implements FunctionFactory {
		private Engine consertEngine;
		
		public nowFactory(Engine consertEngine) {
			this.consertEngine = consertEngine;
		}
		
		@Override
        public Function create(String paramString) {
	        return new now(consertEngine);
        }
	}
}
