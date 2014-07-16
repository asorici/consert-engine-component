package org.aimas.ami.contextrep.engine.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.aimas.ami.contextrep.engine.api.EngineConfigException;
import org.aimas.ami.contextrep.engine.execution.InferenceService;
import org.aimas.ami.contextrep.engine.execution.InsertionService;
import org.aimas.ami.contextrep.engine.execution.QueryService;
import org.aimas.ami.contextrep.engine.execution.SubscriptionMonitor;
import org.aimas.ami.contextrep.model.exceptions.ContextModelConfigException;
import org.aimas.ami.contextrep.utils.ContextModelLoader;
import org.aimas.ami.contextrep.utils.ResourceManager;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.tdb.base.file.Location;

public class Engine {
	// ========== CONSERT Engine configuration properties ==========
	public static final String CONFIG_FILENAME = "etc/cmm/engine.properties";
	private static Properties configurationProperties;
	
	public static Properties getConfiguration() {
		return configurationProperties;
	}
	
	// ========== CONSERT Engine configuration properties ==========
	private static ResourceManager engineResourceManager;
	
	public static void setResourceManager(ResourceManager manager) {
		engineResourceManager = manager;
	}
	
	public static ResourceManager getResourceManager() {
		return engineResourceManager;
	}
	
	
	// ========== CONSERT Engine storage ==========
	/** Path to persistent TDB contextStore */
	private static Location contextStorePersistentLocation;
	
	/** Path to in-memory TDB contextStore used at runtime */
	private static Location contextStoreRuntimeLocation;
	
	
	// ========== CONSERT Engine Domain Context Model ==========
	private static ContextModelLoader contextModelLoader;
	
	/** Map of rdfs ontology models for each module of the context model: 
	 * 	core, annotations, constraints, functions, rules */
	private static Map<String, OntModel> rdfsContextModelMap;
	
	
	// ========== CONSERT Engine Internal Data Structures ==========
	
	/** Index of Context Model ContextAssertions */
	private static ContextAssertionIndex contextAssertionIndex;
	
	/** Index of Context Model ContextAnnotations */
	private static ContextAnnotationIndex contextAnnotationIndex;
		
	/** Index of Context Model ContextConstraints */
	private static ContextConstraintIndex contextConstraintIndex;
	
	/** ContextAssertion Derivation Rule Dictionary */
	private static DerivationRuleDictionary derivationRuleDictionary;
	
	
	// ========== CONSERT Engine Internal Execution Elements ==========
	
	// Execution: ContextAssertion insertion, inference-hook and query execution
	private static InsertionService insertionService;
	private static InferenceService inferenceService;
	private static QueryService queryService;
	
	// Subscription Monitor
	private static SubscriptionMonitor subscriptionMonitor;
	
	
	static Properties readConfiguration(String configurationFilename) 
			throws EngineConfigException {
		if (configurationFilename == null) {
			configurationFilename = CONFIG_FILENAME;
		}
		
		try {
			// load the properties file
			InputStream configStream = new FileInputStream(configurationFilename);
			return readConfiguration(configStream);
			
		} catch (FileNotFoundException e) {
			throw new EngineConfigException("etc/cmm/engine.properties file not found", e);
		} 
	}
	
	
	static Properties readConfiguration(InputStream configStream) throws EngineConfigException {
		try {
			// load the properties file
			Properties engineConfiguration = new Properties();
			engineConfiguration.load(configStream);
			
			validate(engineConfiguration);
			return engineConfiguration;
		}
        catch (IOException e) {
        	throw new EngineConfigException("etc/cmm/engine.properties could not be loaded", e);
        }
	}
	
	
	private static void validate(Properties engineConfiguration) throws EngineConfigException {
	   
    }
	
	
	public static void init(boolean printDurations) throws EngineConfigException {
		init(CONFIG_FILENAME, printDurations);
	}
	
	/**
	 * Do initialization setup:
	 * <ul>
	 * 	<li>parse configuration properties</li>
	 * 	<li>create/open storage dataset</li>
	 * 	<li>load basic context model</li>
	 * 	<li>build transitive, rdfs and mini-owl inference model from basic context model</li>
	 * 	<li>compute derivationRuleDictionary</li>
	 * </ul>
	 */
	//public static void init(Properties engineConfiguration, boolean printDurations) throws ConfigException {
	public static void init(String configFile, boolean printDurations) throws EngineConfigException {
		long timestamp = System.currentTimeMillis();
		
		// ====================== read and store CONSERT Engine configuration ======================
		if (engineResourceManager == null) {
			throw new EngineConfigException("Engine resource manager not initialized.");
		}
		
		InputStream configurationStream = engineResourceManager.getResourceAsStream(configFile);
		if (configurationStream == null) {
			throw new EngineConfigException("Engine configuration file not found in resources.");
		}
		
		configurationProperties = readConfiguration(configurationStream);
		
		// ==================== prepare contextStore storage locations ====================
		// retrieve the runtime memory location name and create the in-memory TDB contextStore dataset
		contextStoreRuntimeLocation = Loader.createRuntimeStoreLocation(configurationProperties);
		Dataset contextDataset = TDBFactory.createDataset(contextStoreRuntimeLocation);
		
		// retrieve the runtime memory location name and create the in-memory TDB contextStore dataset
		contextStorePersistentLocation = Loader.createPersistentStoreLocation(configurationProperties);
		
		if (printDurations) {
			System.out.println("Task: create the contextStore. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		
		// ==================== prepare the Context Model ====================
		// this has the side effect of also configuring the ontology document managers for
		// CONSERT ontology, SPIN ontology set and Context Domain ontology
		try {
	        contextModelLoader = new ContextModelLoader(engineResourceManager);
	        contextModelLoader.loadModel();
		}
        catch (ContextModelConfigException e) {
        	throw new EngineConfigException("Failed to load Context Model.", e);
        }
		
		
		if (printDurations) {
			System.out.println("Task: load context model modules. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();

		
		// ==================== register custom elements (datatypes, functions) ====================
		DatatypeIndex.registerCustomDatatypes();
		if (printDurations) {
			System.out.println("Task: register custom RDF datatypes. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		// register custom functions (defined either by SPARQL queries or custom Java classes)
		FunctionIndex.registerCustomFunctions(contextModelLoader.getFunctionContextModel());
		
		if (printDurations) {
			System.out.println("Task: register custom SPARQL functions. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		
		// ==================== Build CONSERT Engine index data structures ==================== 
		// create the named graph for ContextEntities and EntityDescriptions
		Loader.createEntityStoreGraph(contextDataset);
		
		// build the ContextAssertion index
		OntModel baseCoreModule = contextModelLoader.getCoreContextModel();
		OntModel rdfsCoreModule = contextModelLoader.getRDFSInferenceModel(baseCoreModule);
		contextAssertionIndex = ContextAssertionIndex.create(rdfsCoreModule);
		if (printDurations) {
			System.out.println("Task: create the ContextAssertionIndex. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		//build the ContextAnnotation index
		OntModel baseAnnotationModule = contextModelLoader.getAnnotationContextModel();
		OntModel rdfsAnnotationModule = contextModelLoader.getRDFSInferenceModel(baseAnnotationModule);
		contextAnnotationIndex = ContextAnnotationIndex.create(rdfsAnnotationModule);
		System.out.println(contextAnnotationIndex.getAllStructuredAnnotations());
		
		if (printDurations) {
			System.out.println("Task: create the ContextAnnotationIndex. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		// build the ContextConstraint index
		contextConstraintIndex = ContextConstraintIndex.create(contextAssertionIndex, contextModelLoader.getConstraintContextModel());
		if (printDurations) {
			System.out.println("Task: create the ContextConstraintIndex. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		// build the Derivation Rule dictionary
		derivationRuleDictionary = DerivationRuleDictionary.create(contextAssertionIndex, contextModelLoader.getRuleContextModel());
		if (printDurations) {
			System.out.println("Task: compute derivation rule dictionary. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		
		System.out.println("#### Derivation Rule Map : ");
		System.out.println(derivationRuleDictionary.getAssertion2QueryMap());
		timestamp = System.currentTimeMillis();
		
		// register custom TDB UpdateEgine to listen for ContextAssertion insertions
		//ContextAssertionUpdateEngine.register();
		
		
		// ==================== Create CONSERT Engine execution services ==================== 
		insertionService = initInsertionService(configurationProperties);
		inferenceService = createInferenceService(configurationProperties);
		queryService = createQueryService(configurationProperties);
		
		subscriptionMonitor = new SubscriptionMonitor();
	}
	
	
	/**
	 * Close context model and sync to persistent TDB-backed context data store before closing
	 */
	public static void close(boolean persist) {
		// shutdown the executors and await their task termination
		insertionService.close();
		inferenceService.close();
		queryService.close();
		
		closeContextModel();
		
		if (persist) {
			syncToPersistent(getRuntimeContextStore());
		}
	}
		
	private static void closeContextModel() {
		// close the basic context model modules
		contextModelLoader.closeModel();
    }
	
	private static void syncToPersistent(Dataset contextDataset) {
	    Dataset persistentContextStore = getPersistentContextStore();
	    persistentContextStore.begin(ReadWrite.WRITE);
	    try {
	    	Iterator<String> modelNameIt = contextDataset.listNames();
	    	while(modelNameIt.hasNext()) {
	    		String modelURI = modelNameIt.next();
	    		Model m = contextDataset.getNamedModel(modelURI);
	    		
	    		Model persistentModel = persistentContextStore.getNamedModel(modelURI);
	    		persistentModel.add(m);
	    	}
	    	
	    	persistentContextStore.commit();
	    }
	    finally {
	    	persistentContextStore.end();
	    }
    }
	
	
	
	/**
	 * Clean out all statements from the named graphs in the persistent ContextStore 
	 */
	public static void cleanPersistentContextStore() {
		Dataset contextStoreDataset = TDBFactory.createDataset(contextStorePersistentLocation); 
		
		Iterator<String> graphNameIt = contextStoreDataset.listNames();
		if (graphNameIt != null) {
			List<String> namedGraphs = new ArrayList<>();
			for ( ; graphNameIt.hasNext() ; ) {
				namedGraphs.add(graphNameIt.next());
			}
			
			for (String graphName : namedGraphs ) {
				contextStoreDataset.removeNamedModel(graphName);
			}
			
			TDB.sync(contextStoreDataset);
		}
	}
	
	
	public static Dataset getPersistentContextStore() {
		return TDBFactory.createDataset(contextStorePersistentLocation);
	}
	
	
	public static Dataset getRuntimeContextStore() {
		/*
		 *  We're doing it like this such that every thread that asks for the dataset will
		 *  get its own object. Synchronization happens on TDB.sync()
		 */
		return TDBFactory.createDataset(contextStoreRuntimeLocation);
		//return contextDataset;
	}
	
	
	// ######################## Access CONSERT Engine Context Model and Indexes ########################
	public static ContextModelLoader getModelLoader() {
		return contextModelLoader;
	}
	
	public static ContextAssertionIndex getContextAssertionIndex() {
		return contextAssertionIndex;
	}
	
	public static ContextAnnotationIndex getContextAnnotationIndex() {
		return contextAnnotationIndex;
	}
	
	public static ContextConstraintIndex getConstraintIndex() {
		return contextConstraintIndex;
	}
	
	public static DerivationRuleDictionary getDerivationRuleDictionary() {
		return derivationRuleDictionary;
	}
	
	
	// ############################## Access Internal Execution handlers ############################## 
	public static InsertionService getInsertionService() {
		return insertionService;
	}
	
	
	public static InferenceService getInferenceService() {
		return inferenceService;
	}
	
	
	public static QueryService getQueryService() {
		return queryService;
	}
	
	
	public static SubscriptionMonitor subscriptionMonitor() {
		return subscriptionMonitor;
	}
	
	
	private static InsertionService initInsertionService(Properties execConfiguration) {
		InsertionService instance = InsertionService.getInstance();
		instance.init(execConfiguration);
		
		return instance;
	}
	
	private static InferenceService createInferenceService(Properties execConfiguration) {
		InferenceService instance = InferenceService.getInstance();
		instance.init(execConfiguration);
		
		return instance;
	}
	
	
	private static QueryService createQueryService(Properties execConfiguration) {
		QueryService instance = QueryService.getInstance();
		instance.init(execConfiguration);
		
		return instance;
    }
	
}
