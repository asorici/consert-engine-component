package org.aimas.ami.contextrep.engine.execution;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.aimas.ami.contextrep.engine.api.EngineQueryStats;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.query.ContextQueryTask;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.rdf.model.Resource;

public class QueryService extends ExecutionService implements EngineQueryStats, QueryStatsCollector {
	
	private static final int DEFAULT_NUM_WORKERS = 1;
	private static final long DEFAULT_RUN_WINDOW = 5000;
	
	private ThreadPoolExecutor queryExecutor;
	private int numWorkers = DEFAULT_NUM_WORKERS;
	
	private long defaultRunWindow = DEFAULT_RUN_WINDOW;
	private Map<Resource, Long> assertionSpecificRunWindow = new HashMap<Resource, Long>(); 
	
	public QueryService(Engine engine) {
		super(engine);
	}

	@Override
    public void init(Properties configuration) {
		// configure
		configureExecutionParameters(configuration);
		
		// configure statistics collector
		configureStatisticsCollector();
	}
	
	
	private void configureExecutionParameters(Properties execConfiguration) {
		queryExecutor = createQueryExecutor(execConfiguration);
    	
    	try {
			String runWindowStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_QUERY_RUN_WINDOW, "" + DEFAULT_RUN_WINDOW);
			defaultRunWindow = Long.parseLong(runWindowStr);
        }
		catch(NumberFormatException e) {
			defaultRunWindow = DEFAULT_RUN_WINDOW;
        }
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
	
	
	
	public void setDefaultRunWindow(long runWindow) {
    	this.defaultRunWindow = runWindow * 1000; 	// conversion to milliseconds
    }
    
	/**
	 * @return the default RUN_WINDOW width in milliseconds
	 */
	public long getDefaultRunWindow() {
    	return defaultRunWindow;
    }
    
    public void setSpecificRunWindow(Resource contextAssertionRes, long runWindow) {
    	assertionSpecificRunWindow.put(contextAssertionRes, runWindow * 1000);	// converstion to milliseconds
    }
	
    /**
     * @param contextAssertionRes
     * @return the specific RUN_WINDOW width in milliseconds for the ContextAssertion identified by 
     * <code>contextAssertionRes</code>
     */
    public Long getSpecificRunWindow(Resource contextAssertionRes) {
    	return assertionSpecificRunWindow.get(contextAssertionRes);
    }
	
	
    
    public void executeRequest(Query query, QuerySolutionMap initialBindings, QueryResultNotifier notifier) {
		queryExecutor.submit(new ContextQueryTask(engine, query, initialBindings, notifier));
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
	
	// ================ Inference Statistics Implementation ================
	////////////////////////////////////////////////////////////////////////
	private QueryTracker queryTracker;
	
	
	private void configureStatisticsCollector() {
		queryTracker = new QueryTracker();
	}
	

	@Override
    public Map<Resource, Integer> nrQueries() {
	    return queryTracker.nrQueries();
    }
	
	@Override
    public Map<Resource, Integer> nrSubscriptions() {
	    return queryTracker.nrSubscriptions();
    }
	
	@Override
    public Map<Resource, Long> timeSinceLastQuery() {
	    return queryTracker.timeSinceLastQuery();
    }

	@Override
    public Map<Resource, Integer> nrSuccessfulQueries() {
	    return queryTracker.nrSuccessfulQueries();
    }
	
	@Override
	public void markQueryExecution(Set<ContextAssertion> assertions, boolean successful) {
		queryTracker.markQueryExecution(assertions, engine.currentTimeMillis(), successful);
	}
	
	@Override
	public void markSubscription(ContextAssertion contextAssertion) {
		queryTracker.markSubscription(contextAssertion);
	}
	
	@Override
	public void unmarkSubscription(ContextAssertion contextAssertion) {
		queryTracker.unmarkSubscription(contextAssertion);
	}
	
	private class QueryTracker {
		Map<Resource, Queue<QueryTrackParams>> queryTracker;
		Map<Resource, Long> mostRecentTracker;
		Map<Resource, Integer> subscriptionsTracker;
		
		QueryTracker () {
			this.queryTracker = new HashMap<Resource, Queue<QueryTrackParams>>();
			this.mostRecentTracker = new HashMap<Resource, Long>();
			this.subscriptionsTracker = new HashMap<Resource, Integer>();
		}
		
		
		void markQueryExecution(Set<ContextAssertion> assertions, long timestamp, boolean successful) {
			// add it in the queryTracker
			synchronized (queryTracker) {
				for (ContextAssertion assertion : assertions) {
					Queue<QueryTrackParams> runTimestamps = queryTracker.get(assertion.getOntologyResource());
					if (runTimestamps == null) {
						runTimestamps = new LinkedList<QueryTrackParams>();
						runTimestamps.add(new QueryTrackParams(timestamp, successful));
						
						queryTracker.put(assertion.getOntologyResource(), runTimestamps);
					}
					else {
						runTimestamps.add(new QueryTrackParams(timestamp, successful));
						siftTracker();
					}
				}
            }
			
			// add it in the mostRecentTracker
			synchronized (mostRecentTracker) {
				for (ContextAssertion assertion : assertions) {
					mostRecentTracker.put(assertion.getOntologyResource(), timestamp);
				}
			}
		}
		
		
		void markSubscription(ContextAssertion assertion) {
			synchronized (subscriptionsTracker) {
				Integer nrSubs = subscriptionsTracker.get(assertion.getOntologyResource());
	            int newSubs = nrSubs == null ? 1 : (nrSubs + 1);
				
				subscriptionsTracker.put(assertion.getOntologyResource(), newSubs);
            }
		}
		
		void unmarkSubscription(ContextAssertion assertion) {
			synchronized (subscriptionsTracker) {
				Integer nrSubs = subscriptionsTracker.get(assertion.getOntologyResource());
				int newSubs = nrSubs == null ? 0 : nrSubs > 0 ? (nrSubs - 1) : 0;
				
				subscriptionsTracker.put(assertion.getOntologyResource(), newSubs);
            }
		}
		
		
		void siftTracker() {
			long now = engine.currentTimeMillis();
			
			for (Resource assertionRes : queryTracker.keySet()) {
				long runWindowWidth = defaultRunWindow;
				if (assertionSpecificRunWindow.get(assertionRes) != null) {
					runWindowWidth = assertionSpecificRunWindow.get(assertionRes);
				}
			
				long windowStart = now - runWindowWidth;
				Queue<QueryTrackParams> runTimestamps = queryTracker.get(assertionRes);
			
				while(true) {
					QueryTrackParams p = runTimestamps.peek();
					if (p == null) break;
					
					if (p.getTimestamp() < windowStart) {
						runTimestamps.remove();
					}
					else {
						break;
					}
				}
			}
		}
		
		
		Map<Resource, Integer> nrQueries() {
			synchronized(queryTracker) {
				siftTracker();
				Map<Resource, Integer> counter = new HashMap<Resource, Integer>();
				for (Resource assertionRes : queryTracker.keySet()) {
					counter.put(assertionRes, queryTracker.get(assertionRes).size());
				}
				
				return counter;
			}
		}
		
		public Map<Resource, Integer> nrSubscriptions() {
			// create a snapshot of the current tracker
        	Map<Resource, Integer> subscriptionsSnapshot = new HashMap<Resource, Integer>();
        	
			synchronized(subscriptionsTracker) {
	        	subscriptionsSnapshot.putAll(subscriptionsTracker);
	        }
			
			synchronized(queryTracker) {
				for (Resource assertionRes : queryTracker.keySet()) {
					if (!subscriptionsSnapshot.containsKey(assertionRes)) {
						subscriptionsSnapshot.put(assertionRes, 0);
					}
				}
			}
        
			return subscriptionsSnapshot;
		}
		
		
		synchronized Map<Resource, Integer> nrSuccessfulQueries() {
			synchronized(queryTracker) {
				siftTracker();
				
				Map<Resource, Integer> counter = new HashMap<Resource, Integer>();
				for (Resource assertionRes : queryTracker.keySet()) {
					Queue<QueryTrackParams> runTimestamps = queryTracker.get(assertionRes);
					
					int ct = 0;
					for (QueryTrackParams p : runTimestamps) {
						if (p.successful()) {
							ct++;
						}
					}
					
					counter.put(assertionRes, ct);
				}
				
				return counter;
			}
		}
		
		
		Map<Resource, Long> timeSinceLastQuery() {
			synchronized(queryTracker) {
				long now = engine.currentTimeMillis(); 
				Map<Resource, Long> m = new HashMap<Resource, Long>();
				
				for (Resource assertionRes : mostRecentTracker.keySet()) {
					long recentTimestamp = mostRecentTracker.get(assertionRes);
					m.put(assertionRes, (now - recentTimestamp));
				}
				
				return m;
			}
		}
	}
	
	private static class QueryTrackParams {
		long timestamp;
		boolean successful;
		
		QueryTrackParams(long timestamp, boolean successful) {
	        this.timestamp = timestamp;
	        this.successful = successful;
        }
		
		public long getTimestamp() {
			return timestamp;
		}
		
		public boolean successful() {
			return successful;
		}
	}
	
}
