package org.aimas.ami.contextrep.engine.core;

public class ConfigKeys {
	// ======== Storage configuration ========
	public static final String MEMORY_STORE_NAME = "consert.storage.memory.tdb.name";
	public static final String PERSISTENT_STORE_ASSEMBLER_FILE = "consert.storage.persistent.tdb.assembler";
	public static final String PERSISTENT_STORE_DIRECTORY = "consert.storage.persistent.tdb.directory";
	
	// ======== CONSERT ontology configuration ========
	public static final String CONSERT_ONT_DOCMGR_FILE = "consert.ontology.documentmgr.file";
	public static final String CONSERT_ONT_CORE_URI = "consert.ontology.core.uri";
	public static final String CONSERT_ONT_ANNOTATION_URI = "consert.ontology.annotation.uri";
	public static final String CONSERT_ONT_CONSTRAINT_URI = "consert.ontology.constraints.uri";
	public static final String CONSERT_ONT_FUNCTIONS_URI = "consert.ontology.functions.uri";
	public static final String CONSERT_ONT_RULES_URI = "consert.ontology.rules.uri";
	
	public static final String[] CONSERT_ONT_MODULE_KEYS = new String[] {
		CONSERT_ONT_CORE_URI, CONSERT_ONT_ANNOTATION_URI, CONSERT_ONT_CONSTRAINT_URI,
		CONSERT_ONT_FUNCTIONS_URI, CONSERT_ONT_RULES_URI
	};
	
	// ======== SPIN ontolgy configuration ========
	public static final String SPIN_ONT_DOCMGR_FILE = "spin.ontology.documentmgr.file";
	
	// ======== Context Model ontology configuration ========
	public static final String DOMAIN_ONT_DOCMGR_FILE = "context.model.documentmgr.file";
	public static final String DOMAIN_ONT_CORE_URI = "context.model.ontology.core.uri";
	//public static final String DOMAIN_ONT_CORE_ALT_URL = "context.model.ontology.core.alturl";
	//public static final String DOMAIN_ONT_CORE_PREFIX = "context.model.ontology.core.prefix";
	
	public static final String DOMAIN_ONT_ANNOTATION_URI = "context.model.ontology.annotation.uri";
	//public static final String DOMAIN_ONT_ANNOTATION_ALT_URL = "context.model.ontology.annotation.alturl";
	//public static final String DOMAIN_ONT_ANNOTATION_PREFIX = "context.model.ontology.annotation.prefix";
	
	public static final String DOMAIN_ONT_CONSTRAINT_URI = "context.model.ontology.constraints.uri";
	//public static final String DOMAIN_ONT_CONSTRAINT_ALT_URL = "context.model.ontology.constraint.alturl";
	//public static final String DOMAIN_ONT_CONSTRAINT_PREFIX = "context.model.ontology.constraint.prefix";
	
	public static final String DOMAIN_ONT_FUNCTIONS_URI = "context.model.ontology.functions.uri";
	//public static final String DOMAIN_ONT_FUNCTIONS_ALT_URL = "context.model.ontology.functions.alturl";
	//public static final String DOMAIN_ONT_FUNCTIONS_PREFIX = "context.model.ontology.functions.prefix";
	
	public static final String DOMAIN_ONT_RULES_URI = "context.model.ontology.rules.uri";
	//public static final String DOMAIN_ONT_RULES_ALT_URL = "context.model.ontology.rules.alturl";
	//public static final String DOMAIN_ONT_RULES_PREFIX = "context.model.ontology.rules.prefix";
	
	public static final String[] DOMAIN_ONT_MODULE_KEYS = new String[] {
		DOMAIN_ONT_CORE_URI, DOMAIN_ONT_ANNOTATION_URI, DOMAIN_ONT_CONSTRAINT_URI,
		DOMAIN_ONT_FUNCTIONS_URI, DOMAIN_ONT_RULES_URI
	};
	
	// ======== CONSERT Engine execution configuration ========
	public static final String CONSERT_ENGINE_NUM_INSERTION_THREADS = "consert.runtime.insertion.threadpool.size";
	
	public static final String CONSERT_ENGINE_NUM_INFERENCE_THREADS = "consert.runtime.inference.threadpool.size";
	public static final String CONSERT_ENGINE_INFERENCE_SCHEDULER_SLEEP = "consert.runtime.inference.scheduler.sleep";
	public static final String CONSERT_ENGINE_INFERENCE_SCHEDULER_TYPE = "consert.runtime.inference.scheduler.type";
	public static final String CONSERT_ENGINE_INFERENCE_RUN_WINDOW = "consert.runtime.inference.stats.runwindow";
	
	public static final String CONSERT_ENGINE_NUM_QUERY_THREADS = "consert.runtime.query.threadpool.size";
	public static final String CONSERT_ENGINE_QUERY_RUN_WINDOW = "consert.runtime.query.stats.runwindow";
}
