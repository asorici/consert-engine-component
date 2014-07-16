package org.aimas.ami.contextrep.engine.core;

import java.util.Properties;

import org.aimas.ami.contextrep.engine.api.EngineConfigException;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.base.file.Location;

public class Loader {
	public static final String CONSERT_PERSISTENT_STORAGE_ASSEMBLER_FILE_DEFAULT = "etc/context-tdb-assembler.ttl";
	public static final String CONSERT_PERSISTENT_STORAGE_DIRECTORY_DEFAULT = "store";
	public static final String CONSERT_MEMORY_STORE_NAME_DEFAULT= "consert-store";
	public static final String CONSERT_ONT_DOCMGR_FILE_DEFAULT = "etc/consert-ont-policy.rdf";
	public static final String SPIN_ONT_DOCMGR_FILE_DEFAULT = "etc/spin-ont-policy.rdf";
	
	
	static int getInsertThreadPoolSize(Properties execConfiguration) throws EngineConfigException {
		String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_INSERTION_THREADS, "1");
		
		try {
			return Integer.parseInt(sizeStr);
		}
		catch(NumberFormatException e) {
			throw new EngineConfigException("Illegal specification for integer size of insertion thread pool", e);
		}
	}
	
	
	static int getInferenceThreadPoolSize(Properties execConfiguration) throws EngineConfigException {
		String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_INFERENCE_THREADS, "1");
		
		try {
			return Integer.parseInt(sizeStr);
		}
		catch(NumberFormatException e) {
			throw new EngineConfigException("Illegal specification for integer size of inference thread pool", e);
		}
	}
	
	
	static int getQueryThreadPoolSize(Properties execConfiguration) throws EngineConfigException {
		String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_QUERY_THREADS, "1");
		
		try {
			return Integer.parseInt(sizeStr);
		}
		catch(NumberFormatException e) {
			throw new EngineConfigException("Illegal specification for integer size of query thread pool", e);
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
	 * @throws EngineConfigException if the configuration properties are not initialized (usually because the 
	 * <i>configuration.properties</i> was not found) 
	 */
	static Location createRuntimeStoreLocation(Properties storageConfiguration) throws EngineConfigException {
		if (storageConfiguration != null) {
			String storeMemoryLocation = storageConfiguration.getProperty(ConfigKeys.MEMORY_STORE_NAME, CONSERT_MEMORY_STORE_NAME_DEFAULT);
			
			TDB.init();
			//TDBFactory.createDataset(tdbStorageDirectory);
			
			return Location.mem(storeMemoryLocation);
			//return new Location(tdbStorageDirectory);
		}
		
		throw new EngineConfigException();
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
	 * @throws EngineConfigException if the configuration properties are not initialized (usually because the 
	 * <i>configuration.properties</i> was not found) 
	 */
	static Location createPersistentStoreLocation(Properties storageConfig) throws EngineConfigException {
		if (storageConfig != null) {
			String storePersistentDirectory = storageConfig.getProperty(ConfigKeys.PERSISTENT_STORE_DIRECTORY, 
					CONSERT_PERSISTENT_STORAGE_DIRECTORY_DEFAULT);
			
			TDB.init();
			
			return new Location(storePersistentDirectory);
		}
		
		throw new EngineConfigException();
	}
	
	/**
	 * Create the named graph that acts as the store for ContextEntity and EntityDescription instances
	 * @param dataset The TDB-backed dataset that holds the graphs
	 */
	static void createEntityStoreGraph(Dataset dataset) {
		Model graphStore = dataset.getNamedModel(ConsertCore.ENTITY_STORE_URI);
		TDB.sync(graphStore);
	}
}
