package org.aimas.ami.contextrep.engine.execution;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionResultNotifier;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.update.ContextBulkUpdateTask;
import org.aimas.ami.contextrep.update.ContextUpdateTask;
import org.aimas.ami.contextrep.update.EntityDescriptionUpdateTask;
import org.aimas.ami.contextrep.update.ProfiledOneShotUpdateTask;

import com.hp.hpl.jena.update.UpdateRequest;

public class InsertionService extends ExecutionService {
	private static final int DEFAULT_NUM_WORKERS = 1;
	
	private ThreadPoolExecutor insertionExecutor;
	private int numWorkers = DEFAULT_NUM_WORKERS;
	
	public InsertionService(Engine engine) {
		super(engine);
	}

	@Override
    public void init(Properties execConfiguration) {
		insertionExecutor = createInsertionExecutor(execConfiguration);
    }
	
	
    @Override
    public void start() {
	    // TODO Auto-generated method stub
	    
    }

    
	@Override
    public void stop() {
		
    }
    
    
	@Override
    public void close() {
		insertionExecutor.shutdown();
    }
	
	
	public Future<InsertResult> executeRequest(UpdateRequest insertionRequest, InsertionResultNotifier notifier, int updateMode) {
		return insertionExecutor.submit(new ContextUpdateTask(engine, insertionRequest, notifier, updateMode));
	}
	
	public Future<?> executeBulkRequest(UpdateRequest bulkRequest) {
		return insertionExecutor.submit(new ContextBulkUpdateTask(engine, bulkRequest));
	}
	
	public Future<?> executeEntityDescriptionRequest(UpdateRequest entityDescriptionRequest) {
	    return insertionExecutor.submit(new EntityDescriptionUpdateTask(engine, entityDescriptionRequest));
    }
	
	public Future<InsertResult> executeProfiledAssertionRequest(UpdateRequest profiledAssertionRequest, InsertionResultNotifier notifier) {
	    return insertionExecutor.submit(new ProfiledOneShotUpdateTask(engine, profiledAssertionRequest, notifier));
    }
	
	private ThreadPoolExecutor createInsertionExecutor(Properties execConfiguration) {
		try {
			String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_INSERTION_THREADS, "" + DEFAULT_NUM_WORKERS);
			numWorkers = Integer.parseInt(sizeStr);
        }
		catch(NumberFormatException e) {
		    numWorkers = DEFAULT_NUM_WORKERS;
        }
		
		return (ThreadPoolExecutor)Executors.newFixedThreadPool(numWorkers, new ContextInsertThreadFactory());
	}
}
