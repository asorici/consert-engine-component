package org.aimas.ami.contextrep.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.aimas.ami.contextrep.engine.api.ConfigException;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.topbraid.spin.util.JenaUtil;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPL;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.graph.compose.MultiUnion;
import com.hp.hpl.jena.ontology.OntDocumentManager;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.base.file.Location;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;

public class Loader {
	public static final String CONSERT_PERSISTENT_STORAGE_ASSEMBLER_FILE_DEFAULT = "etc/context-tdb-assembler.ttl";
	public static final String CONSERT_PERSISTENT_STORAGE_DIRECTORY_DEFAULT = "store";
	public static final String CONSERT_MEMORY_STORE_NAME_DEFAULT= "consert-store";
	public static final String CONSERT_ONT_DOCMGR_FILE_DEFAULT = "etc/consert-ont-policy.rdf";
	public static final String SPIN_ONT_DOCMGR_FILE_DEFAULT = "etc/spin-ont-policy.rdf";
	
	private static Map<String, OntDocumentManager> ontDocumentManagers;
	
	
	public static int getInsertThreadPoolSize(Properties execConfiguration) throws ConfigException {
		String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_INSERTION_THREADS, "1");
		
		try {
			return Integer.parseInt(sizeStr);
		}
		catch(NumberFormatException e) {
			throw new ConfigException("Illegal specification for integer size of insertion thread pool", e);
		}
	}
	
	
	public static int getInferenceThreadPoolSize(Properties execConfiguration) throws ConfigException {
		String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_INFERENCE_THREADS, "1");
		
		try {
			return Integer.parseInt(sizeStr);
		}
		catch(NumberFormatException e) {
			throw new ConfigException("Illegal specification for integer size of inference thread pool", e);
		}
	}
	
	
	public static int getQueryThreadPoolSize(Properties execConfiguration) throws ConfigException {
		String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_QUERY_THREADS, "1");
		
		try {
			return Integer.parseInt(sizeStr);
		}
		catch(NumberFormatException e) {
			throw new ConfigException("Illegal specification for integer size of query thread pool", e);
		}
	}
	
	
	/** Create the memory location name for the runtime contextStore that contains:
	 * <ul>
	 * 	<li> the general store: statement of the context model that refer
	 *   	 to context entities (rdf:type statements and EntityDescriptions) </li>
	 * 	<li> the named graphs that act as Stores for each TYPE of ContextAssertion </li>
	 * 	<li> the named graphs which act as identifiers of the ContextAssertions </li>
	 * </ul>
	 * 
	 *  
	 * @return The {@link Location} of the in-memory TDB backed Dataset that stores the ContextEntities, ContextAssertions and their Annotations
	 * @throws ConfigException if the configuration properties are not initialized (usually because the 
	 * <i>configuration.properties</i> was not found) 
	 */
	static Location createRuntimeStoreLocation(Properties storageConfiguration) throws ConfigException {
		if (storageConfiguration != null) {
			String storeMemoryLocation = storageConfiguration.getProperty(ConfigKeys.MEMORY_STORE_NAME, CONSERT_MEMORY_STORE_NAME_DEFAULT);
			
			TDB.init();
			//TDBFactory.createDataset(tdbStorageDirectory);
			
			return Location.mem(storeMemoryLocation);
			//return new Location(tdbStorageDirectory);
		}
		
		throw new ConfigException();
	}
	
	
	/** Create the directory location for the <i>persistent</i> contextStore that contains:
	 * <ul>
	 * 	<li> the general store: statement of the context model that refer
	 *   	 to context entities (rdf:type statements and EntityDescriptions) </li>
	 * 	<li> the named graphs that act as Stores for each TYPE of ContextAssertion </li>
	 * 	<li> the named graphs which act as identifiers of the ContextAssertions </li>
	 * </ul>
	 * 
	 *  
	 * @return The {@link Location} of the <i>persistent</i> TDB backed Dataset that stores the ContextEntities, ContextAssertions and their Annotations
	 * @throws ConfigException if the configuration properties are not initialized (usually because the 
	 * <i>configuration.properties</i> was not found) 
	 */
	static Location createPersistentStoreLocation(Properties storageConfig) throws ConfigException {
		if (storageConfig != null) {
			String storePersistentDirectory = storageConfig.getProperty(ConfigKeys.PERSISTENT_STORE_DIRECTORY, 
					CONSERT_PERSISTENT_STORAGE_DIRECTORY_DEFAULT);
			
			TDB.init();
			
			return new Location(storePersistentDirectory);
		}
		
		throw new ConfigException();
	}
	
	/**
	 * Create the named graph that acts as the store for ContextEntity and EntityDescription instances
	 * @param dataset The TDB-backed dataset that holds the graphs
	 */
	static void createEntityStoreGraph(Dataset dataset) {
		Model graphStore = dataset.getNamedModel(ConsertCore.ENTITY_STORE_URI);
		TDB.sync(graphStore);
	}
	
	/**
	 * Use the configuration file to create the map of base URIs for each module of the domain Context Model:
	 * <i>core, annotation, constraints, functions, rules</i>
	 * @return A map of the base URIs for each type of module within the current domain Context Model
	 */
	public static Map<String, String> getContextModelURIs(Properties contextModelConfig) throws ConfigException {
	    Map<String, String> contextModelURIMap = new HashMap<String, String>();
	    
	    /* build the mapping from context domain model keys to the corresponding URIs (if defined)
	     * If certain module keys are non-existent, only the default CONSERT Engine specific elements 
	     * (e.g. Annotations, Functions) will be loaded and indexed.
	     */
	    String domainCoreURI = contextModelConfig.getProperty(ConfigKeys.DOMAIN_ONT_CORE_URI);
	    String domainAnnotationURI = contextModelConfig.getProperty(ConfigKeys.DOMAIN_ONT_ANNOTATION_URI);
	    String domainConstraintURI = contextModelConfig.getProperty(ConfigKeys.DOMAIN_ONT_CONSTRAINT_URI);
	    String domainFunctionsURI = contextModelConfig.getProperty(ConfigKeys.DOMAIN_ONT_FUNCTIONS_URI);
	    String domainRulesURI = contextModelConfig.getProperty(ConfigKeys.DOMAIN_ONT_RULES_URI);
	    
	    // the Context Model core URI must exist
	    contextModelURIMap.put(ConfigKeys.DOMAIN_ONT_CORE_URI, domainCoreURI);
	    
	    if (domainAnnotationURI != null) 
	    	contextModelURIMap.put(ConfigKeys.DOMAIN_ONT_ANNOTATION_URI, domainAnnotationURI);
	    
	    if (domainConstraintURI != null) 
	    	contextModelURIMap.put(ConfigKeys.DOMAIN_ONT_CONSTRAINT_URI, domainConstraintURI);
	    
	    if (domainFunctionsURI != null) 
	    	contextModelURIMap.put(ConfigKeys.DOMAIN_ONT_FUNCTIONS_URI, domainFunctionsURI);
	    
	    if (domainRulesURI != null) 
	    	contextModelURIMap.put(ConfigKeys.DOMAIN_ONT_RULES_URI, domainRulesURI);
	    
	    
	    return contextModelURIMap;
    }
	
	
	/**
	 * Setup the document managers for the CONSERT, SPIN and Context Domain ontologies 
	 * with configuration files taken from the config.properties file
	 */
	private static void setupOntologyDocManagers(Properties contextModelConfig) throws ConfigException {
		ontDocumentManagers = new HashMap<String, OntDocumentManager>();
		
		String consertOntDocMgrFile = contextModelConfig.getProperty(ConfigKeys.CONSERT_ONT_DOCMGR_FILE, CONSERT_ONT_DOCMGR_FILE_DEFAULT);
		String spinOntDocMgrFile = contextModelConfig.getProperty(ConfigKeys.SPIN_ONT_DOCMGR_FILE, SPIN_ONT_DOCMGR_FILE_DEFAULT);
		String domainOntDocMgrFile = contextModelConfig.getProperty(ConfigKeys.DOMAIN_ONT_DOCMGR_FILE);
		
		// ======== create a document manager configuration for the CONSERT ontology ========
        Model consertDocMgrModel = ModelFactory.createDefaultModel();
        consertDocMgrModel.read(consertOntDocMgrFile);
        OntDocumentManager consertDocManager = new OntDocumentManager(consertDocMgrModel);
        ontDocumentManagers.put(ConfigKeys.CONSERT_ONT_DOCMGR_FILE, consertDocManager);
        
        // ======== create a document manager configuration for the SPIN ontology ========
        Model spinDocMgrModel = ModelFactory.createDefaultModel();
        spinDocMgrModel.read(spinOntDocMgrFile);
        OntDocumentManager spinDocManager = new OntDocumentManager(spinDocMgrModel);
        ontDocumentManagers.put(ConfigKeys.SPIN_ONT_DOCMGR_FILE, spinDocManager);
        
		// ======== create a document manager configuration for the Context Domain ========
        Model domainDocMgrModel = ModelFactory.createDefaultModel();
        
        // read the CONSERT and domain specific document manager config into it
        domainDocMgrModel.read(consertOntDocMgrFile);
        domainDocMgrModel.read(domainOntDocMgrFile);
        OntDocumentManager domainDocManager = new OntDocumentManager(domainDocMgrModel);
        ontDocumentManagers.put(ConfigKeys.DOMAIN_ONT_DOCMGR_FILE, domainDocManager);
        
        // ======== setup the global document manager to block no imports and define the path to everything ========
        Model globalDocMgrModel = ModelFactory.createDefaultModel();
        globalDocMgrModel.read(spinOntDocMgrFile);
        globalDocMgrModel.read(consertOntDocMgrFile);
        globalDocMgrModel.read(domainOntDocMgrFile);
        
        OntDocumentManager globalDocManager = OntDocumentManager.getInstance();
        globalDocManager.configure(globalDocMgrModel);
        
        // remove all the import restrictions in previous documentManagers
        for (Iterator<String> ignoreIt = consertDocManager.listIgnoredImports(); ignoreIt.hasNext();) {
        	String ignoredURI = ignoreIt.next();
        	globalDocManager.removeIgnoreImport(ignoredURI);
        }
        
        for (Iterator<String> ignoreIt = domainDocManager.listIgnoredImports(); ignoreIt.hasNext();) {
        	String ignoredURI = ignoreIt.next();
        	globalDocManager.removeIgnoreImport(ignoredURI);
        }
	}
	
	/**
	 * Get the {@link OntDocumentManager} for the specified configuration key.
	 * @param key The configuration key identifying the document manager file for
	 * either the <b>CONSERT ontology</b>, the <b>SPIN ontologies</b> or the <b>Context Domain ontology</b>
	 * @return The OntDocumentManager for the specified configuration key or <b>null</b> if the document managers
	 * have not yet been initialized or no document manager is found for the specified key.
	 */
	public static OntDocumentManager getOntDocumentManager(String configKey) {
		if (ontDocumentManagers != null) {
			return ontDocumentManagers.get(configKey);
		}
		
		return null;
	}
	
	
	/**
	 * Use the configuration file to create the map of ontology models (<b>basic, no inference</b>) 
	 * for each module of the domain Context Model: <i>core, annotation, constraints, functions, rules</i>
	 * @param contextModelURIMap The Context Model URI map built by calling {@code getContextModelURIs}
	 * @return A map of the models for each type of module within the current domain Context Model
	 * @see getContextModelURIs
	 */
	public static Map<String, OntModel> getContextModelModules(Properties contextModelConfig, 
			Map<String, String> contextModelURIMap) throws ConfigException {
		Map<String, OntModel> contextModelMap = new HashMap<String, OntModel>();
		
		// ======== setup document managers for ontology importing ========
        setupOntologyDocManagers(contextModelConfig);
		
        OntDocumentManager domainDocManager = ontDocumentManagers.get(ConfigKeys.DOMAIN_ONT_DOCMGR_FILE);
        OntModelSpec domainContextModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM);
        domainContextModelSpec.setDocumentManager(domainDocManager);
        
        // ======== now we are ready to load all context ontology modules ========
        // 1) build the core context model
        String consertCoreURI = contextModelConfig.getProperty(ConfigKeys.CONSERT_ONT_CORE_URI);
        String contextModelCoreURI = contextModelURIMap.get(ConfigKeys.DOMAIN_ONT_CORE_URI);
        
        OntModel contextModelCore = ModelFactory.createOntologyModel(domainContextModelSpec);
        contextModelCore.read(consertCoreURI);
        contextModelCore.read(contextModelCoreURI);
        contextModelMap.put(ConfigKeys.DOMAIN_ONT_CORE_URI, contextModelCore);
        
        // 2) build the annotation context model
        String consertAnnotationURI = contextModelConfig.getProperty(ConfigKeys.CONSERT_ONT_ANNOTATION_URI);
        String contextModelAnnotationURI = contextModelURIMap.get(ConfigKeys.DOMAIN_ONT_ANNOTATION_URI);
        OntModel contextModelAnnotations = ModelFactory.createOntologyModel(domainContextModelSpec);
        
        contextModelAnnotations.read(consertAnnotationURI);
        if (contextModelAnnotationURI != null) {
        	contextModelAnnotations.read(contextModelAnnotationURI);
        }
//        else {
//        	contextModelAnnotations.read(consertAnnotationURI);
//        }
        contextModelMap.put(ConfigKeys.DOMAIN_ONT_ANNOTATION_URI, contextModelAnnotations);
        
        // 3) build the constraints context model
        String consertConstraintsURI = contextModelConfig.getProperty(ConfigKeys.CONSERT_ONT_CONSTRAINT_URI);
        String contextModelConstraintsURI = contextModelURIMap.get(ConfigKeys.DOMAIN_ONT_CONSTRAINT_URI);
        OntModel contextModelConstraints = ModelFactory.createOntologyModel(domainContextModelSpec);
        
        contextModelConstraints.read(consertConstraintsURI);
        if (contextModelConstraintsURI != null) {
        	contextModelConstraints.read(contextModelConstraintsURI);
        }
//      else {
//      	contextModelConstraints.read(consertConstraintsURI);
//      }
        contextModelMap.put(ConfigKeys.DOMAIN_ONT_CONSTRAINT_URI, contextModelConstraints);
        
        
        // 4) build the functions context model
        String consertFunctionsURI = contextModelConfig.getProperty(ConfigKeys.CONSERT_ONT_FUNCTIONS_URI);
        String contextModelFunctionsURI = contextModelURIMap.get(ConfigKeys.DOMAIN_ONT_FUNCTIONS_URI);
        OntModel contextModelFunctions = ModelFactory.createOntologyModel(domainContextModelSpec);
        
        contextModelFunctions.read(consertFunctionsURI);
        if (contextModelFunctionsURI != null) {
        	contextModelFunctions.read(contextModelFunctionsURI);
        }
//        else {
//        	contextModelFunctions.read(consertFunctionsURI);
//        }
        contextModelMap.put(ConfigKeys.DOMAIN_ONT_FUNCTIONS_URI, contextModelFunctions);
        
        // 5) build the rules context model
        String consertRulesURI = contextModelConfig.getProperty(ConfigKeys.CONSERT_ONT_RULES_URI);
        String contextModelRulesURI = contextModelURIMap.get(ConfigKeys.DOMAIN_ONT_RULES_URI);
        OntModel contextModelRules = ModelFactory.createOntologyModel(domainContextModelSpec);
        
        contextModelRules.read(consertRulesURI);
        if (contextModelRulesURI != null) {
        	contextModelRules.read(contextModelRulesURI);
        }
//        else {
//        	contextModelRules.read(consertRulesURI);
//        }
        contextModelMap.put(ConfigKeys.DOMAIN_ONT_RULES_URI, contextModelRules);
        
	    return contextModelMap;
    }
	
	
	public static OntModel getTransitiveInferenceModel(OntModel basicContextModel) {
		return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_TRANS_INF, basicContextModel);
	}
	
	public static OntModel getRDFSInferenceModel(OntModel basicContextModel) {
		//Model inferenceHolder = ModelFactory.createDefaultModel();
		//Model inferenceBase = inferenceHolder.union(basicContextModel);
		//return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_RDFS_INF, inferenceBase);
		
		OntModel rdfsModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_RDFS_INF);
		rdfsModel.addSubModel(basicContextModel);
		return rdfsModel;
	}
	
	public static OntModel getOWLInferenceModel(OntModel basicContextModel) {
		return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MINI_RULE_INF, basicContextModel);
	}
	
	/**
	 * Return an extended {@link OntModel} context domain ontology module (with no entailement specification), 
	 * where the SPL, SPIN and SP namespaces have been added to the <code>baseContextModelModule</code>.
	 * @param baseContextModelModule The context domain ontology module to extend with the SPIN imports
	 * @return An {@link OntModel} with no entailement specification, extended with the contents of the 
	 * 		SPL, SPIN and SP ontologies
	 */
	public static OntModel ensureSPINImported(OntModel baseContextModelModule) {
		//return baseContextModelModule;
		
		// First create a new OWL_MEM OntModelSpec; NO inference should be run on the SPIN ontology
		// suits because for some reason it messes up the parsing process.
		// Then add the global instance of the document manager.
		OntModelSpec enrichedModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM);
		OntDocumentManager globalDocMgr = OntDocumentManager.getInstance();
		enrichedModelSpec.setDocumentManager(globalDocMgr);
		
		// Now create a new model with the new specification 
		OntModel enrichedModel = ModelFactory.createOntologyModel(enrichedModelSpec, baseContextModelModule);
		globalDocMgr.loadImport(enrichedModel, SP.BASE_URI);
		globalDocMgr.loadImport(enrichedModel, SPIN.BASE_URI);
		globalDocMgr.loadImport(enrichedModel, SPL.BASE_URI);
		
		//enrichedModel.read(SP.BASE_URI, "TTL");
		//enrichedModel.read(SPIN.BASE_URI, "TTL");
		//enrichedModel.read(SPL.BASE_URI, "TTL");
		
		return enrichedModel;
		
		
		/*
		Graph baseGraph = baseContextModelModule.getGraph();
		MultiUnion spinUnion = JenaUtil.createMultiUnion();
		
		ensureImported(baseGraph, spinUnion, SP.BASE_URI, SP.getModel());
		ensureImported(baseGraph, spinUnion, SPL.BASE_URI, SPL.getModel());
		ensureImported(baseGraph, spinUnion, SPIN.BASE_URI, SPIN.getModel());
		Model unionModel = ModelFactory.createModelForGraph(spinUnion);
		
		OntDocumentManager docManager = ontDocumentManagers.get(ConfigKeys.DOMAIN_ONT_DOCMGR_FILE);
		OntModelSpec modelSpec = new OntModelSpec(OntModelSpec.OWL_DL_MEM_RDFS_INF);
		modelSpec.setDocumentManager(docManager);
		
		return ModelFactory.createOntologyModel(modelSpec, unionModel.union(baseContextModelModule));
		*/
	}
	
	
	/**
	 * Return an extended Jena {@link Model}, where the SPL, SPIN and SP namespaces have been added to 
	 * the <code>baseModel</code>.
	 * @param baseModel The model to extend with the SPIN imports.
	 * @return An {@link Model} extended with the contents of the SPL, SPIN and SP ontologies.
	 */
	public static Model ensureSPINImported(Model baseModel) {
		Graph baseGraph = baseModel.getGraph();
		MultiUnion spinUnion = JenaUtil.createMultiUnion();
		
		ensureImported(baseGraph, spinUnion, SP.BASE_URI, SP.getModel());
		ensureImported(baseGraph, spinUnion, SPL.BASE_URI, SPL.getModel());
		ensureImported(baseGraph, spinUnion, SPIN.BASE_URI, SPIN.getModel());
		Model unionModel = ModelFactory.createModelForGraph(spinUnion);
		
		return unionModel;
	}
	
	private static void ensureImported(Graph baseGraph, MultiUnion union, String baseURI, Model model) {
		if(!baseGraph.contains(Triple.create(Node.createURI(baseURI), RDF.type.asNode(), OWL.Ontology.asNode()))) {
			union.addGraph(model.getGraph());
		}
	}
}
