package org.aimas.ami.contextrep.engine.execution;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.query.ContextQueryTask;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;

public class QueryService implements ExecutionService {
	private static QueryService instance;
	
	private static final int DEFAULT_NUM_WORKERS = 1;
	
	private ThreadPoolExecutor queryExecutor;
	private int numWorkers = DEFAULT_NUM_WORKERS;
	
	private QueryService() {}

	@Override
    public void init(Properties configuration) {
		queryExecutor = createQueryExecutor(configuration);
    }

	@Override
    public void start() {
	    // TODO Auto-generated method stub
	    
    }

	@Override
    public void stop() {
	    // TODO Auto-generated method stub
	    
    }

	@Override
    public void close() {
	    queryExecutor.shutdown();
    }
	
	
	public void executeRequest(Query query, QuerySolutionMap initialBindings, QueryResultNotifier notifier) {
		queryExecutor.submit(new ContextQueryTask(query, initialBindings, notifier));
	}
	
	
	private ThreadPoolExecutor createQueryExecutor(Properties execConfiguration) {
		try {
			String sizeStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_QUERY_THREADS, "" + DEFAULT_NUM_WORKERS);
			numWorkers = Integer.parseInt(sizeStr);
        }
		catch(NumberFormatException e) {
		    numWorkers = DEFAULT_NUM_WORKERS;
        }
		
		return (ThreadPoolExecutor)Executors.newFixedThreadPool(numWorkers, new ContextQueryThreadFactory());
	}
	
	
	public static QueryService getInstance() {
		if (instance == null) {
			instance = new QueryService();
		}
		
		return instance;
	}
}
