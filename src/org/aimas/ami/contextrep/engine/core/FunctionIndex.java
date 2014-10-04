package org.aimas.ami.contextrep.engine.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.functions.certaintyPermitsContinuity;
import org.aimas.ami.contextrep.functions.datetimeDelay;
import org.aimas.ami.contextrep.functions.getCurrentAgent;
import org.aimas.ami.contextrep.functions.makeValidityInterval;
import org.aimas.ami.contextrep.functions.minusInfty;
import org.aimas.ami.contextrep.functions.mostRecentAssertionInstance;
import org.aimas.ami.contextrep.functions.newGraphUUID;
import org.aimas.ami.contextrep.functions.now;
import org.aimas.ami.contextrep.functions.plusInfty;
import org.aimas.ami.contextrep.functions.timestampPermitsContinuity;
import org.aimas.ami.contextrep.functions.validityIntervalsCloseEnough;
import org.aimas.ami.contextrep.functions.validityIntervalsInclude;
import org.aimas.ami.contextrep.functions.validityIntervalsOverlap;
import org.aimas.ami.contextrep.functions.validityPermitsContinuity;
import org.aimas.ami.contextrep.vocabulary.ConsertFunctions;
import org.topbraid.spin.system.SPINModuleRegistry;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.sparql.function.FunctionRegistry;

public class FunctionIndex {
	private static Map<String, Class<?>> customFunctions = new HashMap<String, Class<?>>();
	static {
		// register now function
		customFunctions.put(ConsertFunctions.NS + "now", now.class) ;
		
		// register datetimeDelay function
		customFunctions.put(ConsertFunctions.NS + "datetimeDelay", datetimeDelay.class) ;
		
		// register makeValidityInterval function
		customFunctions.put(ConsertFunctions.NS + "makeValidityInterval", makeValidityInterval.class) ;
		
		// register validityIntervalsInclude function
		customFunctions.put(ConsertFunctions.NS + "validityIntervalsInclude", validityIntervalsInclude.class) ;
		
		// register validityIntervalsOverlap function
		customFunctions.put(ConsertFunctions.NS + "validityIntervalsOverlap", validityIntervalsOverlap.class) ;
		
		// register validityIntervalsOverlap function
		customFunctions.put(ConsertFunctions.NS + "validityIntervalsCloseEnough", validityIntervalsCloseEnough.class) ;
		
		// register getCurrentAgent function
		customFunctions.put(ConsertFunctions.NS + "getCurrentAgent", getCurrentAgent.class) ;
		
		// register newGraphUUID function
		customFunctions.put(ConsertFunctions.NS + "newGraphUUID", newGraphUUID.class) ;
		
		// register timestampPermitsContinuity function
		customFunctions.put(ConsertFunctions.NS + "timestampPermitsContinuity", timestampPermitsContinuity.class) ;
		
		// register certaintyPermitsContinuity function
		customFunctions.put(ConsertFunctions.NS + "certaintyPermitsContinuity", certaintyPermitsContinuity.class) ;
		
		// register validityPermitsContinuity function
		customFunctions.put(ConsertFunctions.NS + "validityPermitsContinuity", validityPermitsContinuity.class) ;
		
		// register plusInfty function
		customFunctions.put(ConsertFunctions.NS + "plusInfty", plusInfty.class) ;
		
		// register minusInfty function
		customFunctions.put(ConsertFunctions.NS + "minusInfty", minusInfty.class) ;
		
		// register mostRecentAssertionInstance function
		customFunctions.put(ConsertFunctions.NS + "mostRecentAssertionInstance", mostRecentAssertionInstance.class);
	}
	
	public static Class<?> getFunctionClass(String operatorURI) {
		return customFunctions.get(operatorURI);
	}
	
	
	public static List<Class<?>> listRegisteredFunctions() {
		return new LinkedList<Class<?>>(customFunctions.values());
	}
	
	public static Map<String, Class<?>> getFunctions() {
		return customFunctions;
	}
	
	/**
	 * Add a function to this index. Do not yet perform registration of the function.
	 * @param functionURI Function URI
	 * @param functionClass	Class of the custom Java implementation of this function
	 */
	public static void addFunction(String functionURI, Class<?> functionClass) {
		addFunction(functionURI, functionClass, false);
	}
	
	
	/**
	 * Add a function to this index. If <code>register</code> is {@literal true} perform registration of the function.
	 * @param functionURI Function URI
	 * @param functionClass Class of the custom Java implementation of this function
	 * @param register Boolean flag telling whether to directly register the function with the {@link FunctionRegister} of the Jena API
	 */
	public static void addFunction(String functionURI, Class<?> functionClass, boolean register) {
		customFunctions.put(functionURI, functionClass);
		
		if (register) {
			FunctionRegistry.get().put(functionURI, functionClass) ;
		}
	}
	
	/**
	 * Register all custom function definitions (filter functions and annotation operators) from 
	 * the specified Context Model function module.
	 * These are defined as instances of spin:Function. Their implementation is given either as a SPARQL query, 
	 * either as a custom Java class. The functions implemented as SPARQL queries are registered directly 
	 * with the {@link SPINModuleRegistry}, while the ones having a Java implementation are registered with the 
	 * {@link FunctionRegistry} of the Jena API.
	 * @param contextModelFunctions The ontology model containing the Function definitions
	 */
	static void registerCustomFunctions(OntModel contextModelFunctions) {
		// register SPIN system functions and templates 
		SPINModuleRegistry.get().init();
		
		// add the spin: and sp: namespaces to the functions module (they were not imported on initial load)
		OntModel extendedFunctionsModel = Engine.getModelLoader().ensureSPINImported(contextModelFunctions);
		
		/*
		Statement splMaxStmt = extendedFunctionsModel.getProperty(ResourceFactory.createResource(SPL.NS + "max"), SPIN.body);
		Statement whereStmt = extendedFunctionsModel.getProperty(splMaxStmt.getResource(), SP.where);
		Statement whereContent = extendedFunctionsModel.getProperty(whereStmt.getResource(), RDF.first);
		Statement exprStmt = extendedFunctionsModel.getProperty(whereContent.getResource(), SP.expression);
		Statement exprContent =  extendedFunctionsModel.getProperty(exprStmt.getResource(), RDF.first);
		
		for (Statement s : extendedFunctionsModel.listStatements(exprStmt.getResource(), (Property)null, (RDFNode)null).toList()) {
			System.out.println(s);
		}
		*/
		
		// register SPIN custom functions and templates which the Context Model Function module defines
		SPINModuleRegistry.get().registerAll(extendedFunctionsModel, null);
		
		// make a Jena registration of the Java classes that implement the custom annotation operators defined in the Context Model Function module
		AnnotationOperatorIndex.registerCustomAnnotationOperators();
		
		// make a Jena registration of the Java classes that implement custom filter functions defined in the Context Model Function module
		registerCustomFilterFunctions();
		
		// free up any resources used by the extendedFunctionsModel
		//extendedFunctionsModel.close();
	}
	
	private static void registerCustomFilterFunctions() {
		for (String functionURI : customFunctions.keySet()) {
			FunctionRegistry.get().put(functionURI, customFunctions.get(functionURI)) ;
		}
	}
}
