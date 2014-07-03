package org.aimas.ami.contextrep.engine.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.functions.timeValidityJoinOp;
import org.aimas.ami.contextrep.functions.timeValidityMeetOp;
import org.aimas.ami.contextrep.vocabulary.ConsertFunctions;

import com.hp.hpl.jena.sparql.function.FunctionRegistry;

public class AnnotationOperatorIndex {
	private static Map<String, Class<?>> customAnnotationOperators = new HashMap<String, Class<?>>();
	static {
		customAnnotationOperators.put(ConsertFunctions.NS + "timeValidityMeetOp", timeValidityMeetOp.class);
		customAnnotationOperators.put(ConsertFunctions.NS + "timeValidityJoinOp", timeValidityJoinOp.class);
	}
	
	public static Class<?> getOperatorClass(String operatorURI) {
		return customAnnotationOperators.get(operatorURI);
	}
	
	public static List<Class<?>> listRegisteredAnnotationOperators() {
		return new LinkedList<Class<?>>(customAnnotationOperators.values());
	}
	
	public static Map<String, Class<?>> getRegisteredAnnotationOperators() {
		return customAnnotationOperators;
	}
	
	/**
	 * Add an annotation operator to this index. Do not yet perform registration of the operator.
	 * @param operartorURI Operator URI
	 * @param operatorClass	Class of the custom Java implementation of this operator
	 */
	public static void addOperator(String operatorURI, Class<?> operatorClass) {
		addOperator(operatorURI, operatorClass, false);
	}
	
	/**
	 * Add an annotation operator to this index. If <code>register</code> is {@literal true} perform registration of the operator.
	 * @param operartorURI Operator URI
	 * @param operatorClass	Class of the custom Java implementation of this operator
	 * @param register Boolean flag telling whether to directly register the operator with the {@link FunctionRegister} of the Jena API
	 */
	public static void addOperator(String operatorURI, Class<?> operatorClass, boolean register) {
		customAnnotationOperators.put(operatorURI, operatorClass);
		
		if (register) {
			FunctionRegistry.get().put(operatorURI, operatorClass) ;
		}
	}
	
	/**
	 * Register the contents of the AnnotationOperator index with the {@link FunctionRegistry} of the Jena API. 
	 */
	static void registerCustomAnnotationOperators() {
		for (String operatorURI : customAnnotationOperators.keySet()) {
			FunctionRegistry.get().put(operatorURI, customAnnotationOperators.get(operatorURI)) ;
		}
	}
}
