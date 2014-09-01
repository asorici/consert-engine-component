package org.aimas.ami.contextrep.engine.core;

public class ConfigKeys {
	// ======== Storage configuration ========
	public static final String MEMORY_STORE_NAME = "consert.storage.memory.tdb.name";
	public static final String PERSISTENT_STORE_ASSEMBLER_FILE = "consert.storage.persistent.tdb.assembler";
	public static final String PERSISTENT_STORE_DIRECTORY = "consert.storage.persistent.tdb.directory";
	
	// ======== CONSERT Engine execution configuration ========
	public static final String CONSERT_ENGINE_NUM_INSERTION_THREADS = "consert.runtime.insertion.threadpool.size";
	
	public static final String CONSERT_ENGINE_NUM_INFERENCE_THREADS = "consert.runtime.inference.threadpool.size";
	public static final String CONSERT_ENGINE_INFERENCE_SCHEDULER_SLEEP = "consert.runtime.inference.scheduler.sleep";
	public static final String CONSERT_ENGINE_INFERENCE_SCHEDULER_TYPE = "consert.runtime.inference.scheduler.type";
	public static final String CONSERT_ENGINE_INFERENCE_RUN_WINDOW = "consert.runtime.inference.stats.runwindow";
	
	public static final String CONSERT_ENGINE_NUM_QUERY_THREADS = "consert.runtime.query.threadpool.size";
	public static final String CONSERT_ENGINE_QUERY_RUN_WINDOW = "consert.runtime.query.stats.runwindow";
	
	public static final String CONSERT_ENGINE_EXECUTION_MONITORING = "consert.runtime.monitoring.enabled";
}
