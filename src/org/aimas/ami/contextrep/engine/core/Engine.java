package org.aimas.ami.contextrep.engine.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Dictionary;
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
import org.aimas.ami.contextrep.resources.TimeService;
import org.aimas.ami.contextrep.utils.ContextModelLoader;
import org.aimas.ami.contextrep.utils.ResourceManager;
import org.osgi.service.log.LogService;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.ReasonerRegistry;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.tdb.base.file.Location;

public class Engine {
	// ========== CONSERT Engine configuration properties ==========
	public static final String CONFIG_FILENAME = "etc/cmm/engine.properties";
	private Properties configurationProperties;
	private String applicationIdentifier;
	
	public Properties getConfiguration() {
		return configurationProperties;
	}
	
	public void setApplicationIdentifier(String appId) {
		applicationIdentifier = appId;
	}
	
	public String getApplicationIdentifier() {
		return applicationIdentifier;
	}
	
	// ========== CONSERT Engine configuration properties ==========
	private ResourceManager engineResourceManager;
	
	public void setResourceManager(ResourceManager manager) {
		this.engineResourceManager = manager;
	}
	
	public ResourceManager getResourceManager() {
		return engineResourceManager;
	}
	
	// ========== CONSERT Engine time service ==========
	private TimeService engineTimeService;
	
	public void setTimeService(TimeService timeService) {
		this.engineTimeService = timeService;
	}
	
	public TimeService getTimeService() {
		return engineTimeService;
	}
	
	public long currentTimeMillis() {
		return engineTimeService.getCurrentTimeMillis();
	}
	
	public Calendar now() {
		return engineTimeService.getCalendarInstance();
	}
	
	// ========== CONSERT Engine log service ==========
	private LogService logService;
	
	public void setLogService(LogService logService) {
	    this.logService = logService;
    }
	
	public LogService getLogger() {
		return logService;
	}
	
	// ========== CONSERT Engine storage ==========
	/** Path to persistent TDB contextStore */
	private Location contextStorePersistentLocation;
	
	/** Path to in-memory TDB contextStore used at runtime */
	private Location contextStoreRuntimeLocation;
	
	/** Reference to the EntityStore */
	private InfModel entityStore;
	
	// ========== CONSERT Engine Domain Context Model ==========
	private ContextModelLoader contextModelLoader;
	
	/** Map of rdfs ontology models for each module of the context model: 
	 * 	core, annotations, constraints, functions, rules */
	private Map<String, OntModel> rdfsContextModelMap;
	
	
	// ========== CONSERT Engine Internal Data Structures ==========
	
	/** Index of Context Model ContextAssertions */
	private ContextAssertionIndex contextAssertionIndex;
	
	/** Index of Context Model ContextAnnotations */
	private ContextAnnotationIndex contextAnnotationIndex;
		
	/** Index of Context Model ContextConstraints */
	private ContextConstraintIndex contextConstraintIndex;
	
	/** ContextAssertion Derivation Rule Dictionary */
	private DerivationRuleDictionary derivationRuleDictionary;
	
	/** Index of custom ARQ and SPIN functions */
	private FunctionIndex customFunctionIndex;
	
	// ========== CONSERT Engine Internal Execution Elements ==========
	
	// Reasoners
	Reasoner entityStoreReasoner;
	
	public Reasoner getEntityStoreReasoner() {
		return entityStoreReasoner;
	}
	
	// Execution: ContextAssertion insertion, inference-hook and query execution
	private InsertionService insertionService;
	private InferenceService inferenceService;
	private QueryService queryService;
	
	// Subscription Monitor
	private SubscriptionMonitor subscriptionMonitor;
	
	
	// ========================= INTIALIZATION =========================
	////////////////////////////////////////////////////////////////////
	Properties readConfiguration(String configurationFilename) 
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
	
	
	Properties readConfiguration(InputStream configStream) throws EngineConfigException {
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
	
	
	private void validate(Properties engineConfiguration) throws EngineConfigException {
	   // NOTHING TO DO YET
    }
	
	
	public void init(Dictionary<String, String> modelDefinitionFileDict, boolean printDurations) throws EngineConfigException {
		init(CONFIG_FILENAME, modelDefinitionFileDict, printDurations);
	}
	
	
	public void init(String configFile, Dictionary<String, String> modelDefinitionFileDict, boolean printDurations) throws EngineConfigException {
		long timestamp = currentTimeMillis();
		
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
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();
		
		// ==================== setup CONSERT Engine execution monitoring ====================
		Loader.configureExecutionMonitoring(configurationProperties);
		
		// ==================== prepare the Context Model ====================
		// this has the side effect of also configuring the ontology document managers for
		// CONSERT ontology, SPIN ontology set and Context Domain ontology
		try {
			contextModelLoader = new ContextModelLoader(engineResourceManager, modelDefinitionFileDict);
	        contextModelLoader.loadModel();
		}
        catch (ContextModelConfigException e) {
        	throw new EngineConfigException("Failed to load Context Model.", e);
        }
		
		
		if (printDurations) {
			System.out.println("Task: load context model modules. Duration: " + 
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();

		
		// ==================== register custom elements (datatypes, functions) ====================
		DatatypeIndex.registerCustomDatatypes();
		if (printDurations) {
			System.out.println("Task: register custom RDF datatypes. Duration: " + 
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();
		
		// register custom functions (defined either by SPARQL queries or custom Java classes)
		customFunctionIndex = new FunctionIndex(this);
		customFunctionIndex.registerCustomFunctions(contextModelLoader.getFunctionContextModel());
		
		if (printDurations) {
			System.out.println("Task: register custom SPARQL functions. Duration: " + 
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();
		
		
		// ==================== Build CONSERT Engine index data structures ==================== 
		// build the ContextAssertion index
		OntModel baseCoreModule = contextModelLoader.getCoreContextModel();
		OntModel rdfsCoreModule = contextModelLoader.getRDFSInferenceModel(baseCoreModule);
		contextAssertionIndex = ContextAssertionIndex.create(rdfsCoreModule);
		if (printDurations) {
			System.out.println("Task: create the ContextAssertionIndex. Duration: " + 
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();
		
		//build the ContextAnnotation index
		OntModel baseAnnotationModule = contextModelLoader.getAnnotationContextModel();
		OntModel rdfsAnnotationModule = contextModelLoader.getRDFSInferenceModel(baseAnnotationModule);
		contextAnnotationIndex = ContextAnnotationIndex.create(rdfsAnnotationModule);
		//System.out.println(contextAnnotationIndex.getAllStructuredAnnotations());
		
		if (printDurations) {
			System.out.println("Task: create the ContextAnnotationIndex. Duration: " + 
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();
		
		// build the ContextConstraint index
		contextConstraintIndex = ContextConstraintIndex.create(this, contextAssertionIndex, contextModelLoader.getConstraintContextModel());
		if (printDurations) {
			System.out.println("Task: create the ContextConstraintIndex. Duration: " + 
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();
		System.out.println("#### Constraint Index : ");
		System.out.println(contextConstraintIndex.getAllConstraints());
		System.out.println();
		
		// build the Derivation Rule dictionary
		derivationRuleDictionary = DerivationRuleDictionary.create(this, contextAssertionIndex, contextModelLoader.getRuleContextModel());
		if (printDurations) {
			System.out.println("Task: compute derivation rule dictionary. Duration: " + 
				(currentTimeMillis() - timestamp) + " ms");
		}
		timestamp = currentTimeMillis();
		//System.out.println("#### Derivation Rule Map : ");
		//System.out.println(derivationRuleDictionary.getAssertion2QueryMap());
		
		
		// register custom TDB UpdateEgine to listen for ContextAssertion insertions
		//ContextAssertionUpdateEngine.register();
		
		// ==================== Create CONSERT Engine execution services ==================== 
		// create the named graph for ContextEntities and EntityDescriptions together with the
		// corresponding EntityStore reasoner.
		setupEntityStore(contextDataset, baseCoreModule);
				
		
		insertionService = initInsertionService(configurationProperties);
		inferenceService = createInferenceService(configurationProperties);
		queryService = createQueryService(configurationProperties);
		
		subscriptionMonitor = new SubscriptionMonitor(this);
	}
	
	
	/**
	 * Close context model and sync to persistent TDB-backed context data store before closing
	 */
	public void close(boolean persist) {
		// shutdown the executors and await their task termination
		insertionService.close();
		inferenceService.close();
		queryService.close();
		
		closeContextModel();
		
		if (persist) {
			syncToPersistent(getRuntimeContextStore());
		}
	}
		
	private void closeContextModel() {
		// close the basic context model modules
		contextModelLoader.closeModel();
    }
	
	private void syncToPersistent(Dataset contextDataset) {
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
	 * Create the named graph that acts as the store for ContextEntity and EntityDescription instances.
	 * Additionally, setup the REASONER that is used to perform OWL-MICRO realization of inserted 
	 * ContextEntity and EntityDescription instances.
	 * @param contextStoreDataset The TDB-backed dataset that holds the graphs
	 */
	void setupEntityStore(Dataset contextStoreDataset, OntModel coreContextModel) {
		// The EntityStore is a OWL-MICRO inference model, so we first need to setup
		// our entityStoreReasoner.
		//entityStoreReasoner = ReasonerRegistry.getOWLMicroReasoner();
		entityStoreReasoner = ReasonerRegistry.getRDFSSimpleReasoner();
		entityStoreReasoner = entityStoreReasoner.bindSchema(coreContextModel);
		
		/*
		// Now create an initially empty Model and then generate the resulting InfModel
		//Model initialData = ModelFactory.createDefaultModel();
		//entityStore = ModelFactory.createInfModel(entityStoreReasoner, initialData);
		
		// Lastly, add this entityStore model to the ContextStore dataset
		//contextStoreDataset.addNamedModel(ConsertCore.ENTITY_STORE_URI, entityStore);
		//TDB.sync(contextStoreDataset);
		 */
	}
	
	
	/**
	 * Clean out all statements from the named graphs in the persistent ContextStore 
	 */
	public void cleanPersistentContextStore() {
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
	
	
	public Dataset getPersistentContextStore() {
		return TDBFactory.createDataset(contextStorePersistentLocation);
	}
	
	
	public Dataset getRuntimeContextStore() {
		/*
		 *  We're doing it like this such that every thread that asks for the dataset will
		 *  get its own object. Synchronization happens on TDB.sync()
		 */
		return TDBFactory.createDataset(contextStoreRuntimeLocation);
		//return contextDataset;
	}
	
	
	// ######################## Access CONSERT Engine Context Model and Indexes ########################
	public ContextModelLoader getModelLoader() {
		return contextModelLoader;
	}
	
	public ContextAssertionIndex getContextAssertionIndex() {
		return contextAssertionIndex;
	}
	
	public ContextAnnotationIndex getContextAnnotationIndex() {
		return contextAnnotationIndex;
	}
	
	public ContextConstraintIndex getConstraintIndex() {
		return contextConstraintIndex;
	}
	
	public DerivationRuleDictionary getDerivationRuleDictionary() {
		return derivationRuleDictionary;
	}
	
	
	// ############################## Access Internal Execution handlers ############################## 
	public InsertionService getInsertionService() {
		return insertionService;
	}
	
	
	public InferenceService getInferenceService() {
		return inferenceService;
	}
	
	
	public QueryService getQueryService() {
		return queryService;
	}
	
	
	public SubscriptionMonitor subscriptionMonitor() {
		return subscriptionMonitor;
	}
	
	
	private InsertionService initInsertionService(Properties execConfiguration) {
		InsertionService insertionService = new InsertionService(this);
		insertionService.init(execConfiguration);
		
		return insertionService;
	}
	
	private InferenceService createInferenceService(Properties execConfiguration) {
		InferenceService inferenceService = new InferenceService(this);
		inferenceService.init(execConfiguration);
		
		return inferenceService;
	}
	
	
	private QueryService createQueryService(Properties execConfiguration) {
		QueryService queryService = new QueryService(this);
		queryService.init(execConfiguration);
		
		return queryService;
    }
}
