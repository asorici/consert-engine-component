package org.aimas.ami.contextrep.engine;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.aimas.ami.contextrep.engine.api.ConfigException;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.tdb.base.file.Location;

public class Engine {
	// ========== CONSERT Engine configuration properties ==========
	public static final String CONFIG_FILENAME = "etc/config.properties";
	private static Properties configurationProperties;
	
	public static Properties getConfiguration() {
		return configurationProperties;
	}
	
	// ========== CONSERT Engine configuration properties ==========
	private static EngineResourceManager resourceManager;
	
	static void setResourceManager(EngineResourceManager manager) {
		resourceManager = manager;
	}
	
	static EngineResourceManager getResourceManager() {
		return resourceManager;
	}
	
	
	// ========== CONSERT Engine storage ==========
	/** Path to persistent TDB contextStore */
	private static Location contextStorePersistentLocation;
	
	/** Path to in-memory TDB contextStore used at runtime */
	private static Location contextStoreRuntimeLocation;
	
	
	// ========== CONSERT Engine Domain Context Model ==========
	/** Map of URIs for the modules of the context model: core, annotations, constraints, functions, rules */
	private static Map<String, String> contextModelURIMap;
	
	/** Map of basic ontology models for each module of the context model: 
	 * 	core, annotations, constraints, functions, rules */
	private static Map<String, OntModel> baseContextModelMap;
	
	/** Map of basic ontology models for each module of the context model: 
	 * 	core, annotations, constraints, functions, rules */
	private static Map<String, OntModel> rdfsContextModelMap;
	
	// ContextAssertion Derivation Rule Dictionary
	private static DerivationRuleDictionary derivationRuleDictionary;
	
	
	// ========== CONSERT Engine Internal Data Structures ==========
	
	/** Index of Context Model ContextAssertions */
	private static ContextAssertionIndex contextAssertionIndex;
	
	/** Index of Context Model ContextAnnotations */
	private static ContextAnnotationIndex contextAnnotationIndex;
		
	/** Index of Context Model ContextConstraints */
	private static ContextConstraintIndex contextConstraintIndex;
	
	
	// ========== CONSERT Engine Internal Execution Elements ==========
	
	// Execution: ContextAssertion insertion, inference-hook and query execution
	private static ThreadPoolExecutor assertionInsertExecutor;
	private static ThreadPoolExecutor assertionInferenceExecutor;
	private static ThreadPoolExecutor assertionQueryExecutor;
	
	// Subscription Monitor
	private static SubscriptionMonitor subscriptionMonitor;
	
	
	static Properties readConfiguration(String configurationFilename) 
			throws ConfigException {
		if (configurationFilename == null) {
			configurationFilename = CONFIG_FILENAME;
		}
		
		try {
			// load the properties file
			InputStream configStream = new FileInputStream(configurationFilename);
			return readConfiguration(configStream);
			
		} catch (FileNotFoundException e) {
			throw new ConfigException("configuration.properties file not found", e);
		} 
	}
	
	
	static Properties readConfiguration(InputStream configStream) throws ConfigException {
		try {
			// load the properties file
			Properties engineConfiguration = new Properties();
			engineConfiguration.load(configStream);
			
			validate(engineConfiguration);
			return engineConfiguration;
		}
        catch (IOException e) {
        	throw new ConfigException("configuration.properties could not be loaded", e);
        }
	}
	
	
	private static void validate(Properties engineConfiguration) throws ConfigException {
	    // Step 1) Check for the required entries detailing the CONSERT Ontology modules
		for (String moduleKey : ConfigKeys.CONSERT_ONT_MODULE_KEYS) {
			if (engineConfiguration.getProperty(moduleKey) == null) {
				throw new ConfigException("Configuration properties has no value for key: " + moduleKey);
			}
		}
		
		// Step 2) Check for existence of Context Model Core URI (this MUST exist)
		if (engineConfiguration.get(ConfigKeys.DOMAIN_ONT_CORE_URI) == null) {
				throw new ConfigException("No value for required" 
					+ "Context Domain core module key: " + ConfigKeys.DOMAIN_ONT_CORE_URI);
		}
		
		// Step 3) Check for existence of the Context Model document manager file key
		if (engineConfiguration.get(ConfigKeys.DOMAIN_ONT_DOCMGR_FILE) == null) {
			throw new ConfigException("Configuration properties has no value for "
					+ "Context Domain ontology document manager key: " + ConfigKeys.DOMAIN_ONT_DOCMGR_FILE);
		}
    }
	
	
	public static void init(boolean printDurations) throws ConfigException {
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
	public static void init(String configFile, boolean printDurations) throws ConfigException {
		long timestamp = System.currentTimeMillis();
		
		// ====================== read and store CONSERT Engine configuration ======================
		if (resourceManager == null) {
			throw new ConfigException("Engine resource manager not initialized.");
		}
		
		InputStream configurationStream = resourceManager.getResourceAsStream(configFile);
		if (configurationStream == null) {
			throw new ConfigException("Engine configuration file not found in resources.");
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
		
		
		// ==================== prepare the Context Models ====================
		// this has the side effect of also configuring the ontology document managers for
		// CONSERT ontology, SPIN ontology set and Context Domain ontology
		contextModelURIMap = Loader.getContextModelURIs(configurationProperties);
		baseContextModelMap = Loader.getContextModelModules(configurationProperties, resourceManager, contextModelURIMap); 
		
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
		FunctionIndex.registerCustomFunctions(baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_FUNCTIONS_URI));
		
		if (printDurations) {
			System.out.println("Task: register custom SPARQL functions. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		
		// ==================== Build CONSERT Engine index data structures ==================== 
		// create the named graph for ContextEntities and EntityDescriptions
		Loader.createEntityStoreGraph(contextDataset);
		
		// build the ContextAssertion index
		OntModel baseCoreModule = baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_CORE_URI);
		OntModel rdfsCoreModule = Loader.getRDFSInferenceModel(baseCoreModule);
		contextAssertionIndex = ContextAssertionIndex.create(rdfsCoreModule);
		if (printDurations) {
			System.out.println("Task: create the ContextAssertionIndex. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		//build the ContextAnnotation index
		OntModel baseAnnotationModule = baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_ANNOTATION_URI);
		OntModel rdfsAnnotationModule = Loader.getRDFSInferenceModel(baseAnnotationModule);
		contextAnnotationIndex = ContextAnnotationIndex.create(rdfsAnnotationModule);
		System.out.println(contextAnnotationIndex.getAllStructuredAnnotations());
		
		if (printDurations) {
			System.out.println("Task: create the ContextAnnotationIndex. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		// build the ContextConstraint index
		contextConstraintIndex = ContextConstraintIndex.create(contextAssertionIndex, baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_CONSTRAINT_URI));
		if (printDurations) {
			System.out.println("Task: create the ContextConstraintIndex. Duration: " + 
				(System.currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = System.currentTimeMillis();
		
		// build the Derivation Rule dictionary
		derivationRuleDictionary = DerivationRuleDictionary.create(contextAssertionIndex, baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_RULES_URI));
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
		assertionInsertExecutor = createInsertionExecutor(configurationProperties);
		assertionInferenceExecutor = createInferenceExecutor(configurationProperties);
		assertionQueryExecutor = createQueryExecutor(configurationProperties);
		
		subscriptionMonitor = new SubscriptionMonitor();
	}
	
	
	/**
	 * Close context model and sync to persistent TDB-backed context data store before closing
	 */
	public static void close(boolean persist) {
		// shutdown the executors and await their task termination
		assertionInsertExecutor.shutdown();
		assertionInferenceExecutor.shutdown();
		assertionQueryExecutor.shutdown();
		
		try {
	        assertionInsertExecutor.awaitTermination(10, TimeUnit.SECONDS);
	        assertionInferenceExecutor.awaitTermination(10, TimeUnit.SECONDS);
	        assertionQueryExecutor.awaitTermination(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
	        e.printStackTrace();
        }
		
		assertionInsertExecutor.shutdownNow();
		assertionInferenceExecutor.shutdownNow();
		assertionQueryExecutor.shutdownNow();
		
		closeContextModel();
		
		if (persist) {
			syncToPersistent(getRuntimeContextStore());
		}
	}
		
	private static void closeContextModel() {
		// close the basic context model modules
		for (String moduleKey : baseContextModelMap.keySet()) {
			OntModel m = baseContextModelMap.get(moduleKey);
			m.close();
		}
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
	public static OntModel getCoreContextModel() {
		return baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_CORE_URI);
	}
	
	public static OntModel getAnnotationContextModel() {
		return baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_ANNOTATION_URI);
	}
	
	public static OntModel getConstraintContextModel() {
		return baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_CONSTRAINT_URI);
	}
	
	public static OntModel getFunctionContextModel() {
		return baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_FUNCTIONS_URI);
	}
	
	public static OntModel getRuleContextModel() {
		return baseContextModelMap.get(ConfigKeys.DOMAIN_ONT_RULES_URI);
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
	public static ThreadPoolExecutor assertionInsertExecutor() {
		return assertionInsertExecutor;
	}
	
	
	public static ThreadPoolExecutor assertionInferenceExecutor() {
		return assertionInferenceExecutor;
	}
	
	
	public static ThreadPoolExecutor assertionQueryExecutor() {
		return assertionQueryExecutor;
	}
	
	
	public static SubscriptionMonitor subscriptionMonitor() {
		return subscriptionMonitor;
	}
	
	
	private static ThreadPoolExecutor createInsertionExecutor(Properties execConfiguration) {
		int assertionInsertThreadPoolSize = 1;
		
		try {
	        assertionInsertThreadPoolSize = Loader.getInsertThreadPoolSize(execConfiguration);
        }
        catch (ConfigException e) {
	        assertionInsertThreadPoolSize = 1;
        }
		
		return (ThreadPoolExecutor)Executors.newFixedThreadPool(assertionInsertThreadPoolSize, new ContextInsertThreadFactory());
	}
	
	private static ThreadPoolExecutor createInferenceExecutor(Properties execConfiguration) {
		int assertionInferenceThreadPoolSize = 1;
		
		try {
	        assertionInferenceThreadPoolSize = Loader.getInferenceThreadPoolSize(execConfiguration);
        }
        catch (ConfigException e) {
        	assertionInferenceThreadPoolSize = 1;
        }
		
		return (ThreadPoolExecutor)Executors.newFixedThreadPool(assertionInferenceThreadPoolSize, new ContextInferenceThreadFactory());
	}
	
	
	private static ThreadPoolExecutor createQueryExecutor(Properties execConfiguration) {
		int assertionQueryThreadPoolSize = 1;
		
		try {
	        assertionQueryThreadPoolSize = Loader.getQueryThreadPoolSize(execConfiguration);
        }
        catch (ConfigException e) {
        	assertionQueryThreadPoolSize = 1;
        }
		
		return (ThreadPoolExecutor)Executors.newFixedThreadPool(assertionQueryThreadPoolSize, new ContextQueryThreadFactory());
    }
	
}
