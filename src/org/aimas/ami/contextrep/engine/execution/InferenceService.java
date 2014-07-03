package org.aimas.ami.contextrep.engine.execution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aimas.ami.contextrep.engine.api.EngineInferenceStats;
import org.aimas.ami.contextrep.engine.api.InferenceRequest;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.update.CheckInferenceHook;
import org.aimas.ami.contextrep.update.ContextInferenceTask;

public class InferenceService implements ExecutionService, EngineInferenceStats {
	private static InferenceService instance;
	
	private static final String INFERENCE_SCHEDULER_THREAD = "inference-scheduler-thread";
	
	private static final int DEFAULT_NUM_WORKERS = 1;
	private static final int DEFAULT_SCHEDULER_SLEEP = 100;
	private static final long DEFAULT_RUN_WINDOW = 5000;
	
	
	private ThreadPoolExecutor inferenceExecutor;
	private int numWorkers = DEFAULT_NUM_WORKERS;
	
	private PriorityBlockingQueue<InferenceRequestWrapper> requestQueue;
	private InferencePriorityProvider requestPriorityProvider;
	private AtomicBoolean newRequests = new AtomicBoolean(false);
	
	private ScheduledExecutorService schedulingTimer;
	private int schedulingSleep = DEFAULT_SCHEDULER_SLEEP;
	private long runWindowWidth = DEFAULT_RUN_WINDOW; 
	
	// ================= Initialization and Configurations =================
	////////////////////////////////////////////////////////////////////////
	
	@Override
    public void init(Properties execConfiguration) {
    	// configure
		configureExecutionParameters(execConfiguration);
    	
		// setup scheduler (including comparator provider)
    	setupScheduler();
    	
    	// setup the request queue
    	configureRequestQueue();
	}
    
    private void configureRequestQueue() {
    	if (requestQueue == null) {
	    	// first time initialization, we just have to create the queue
	    	requestQueue = new PriorityBlockingQueue<InferenceRequestWrapper>();
    	}
    	else {
    		// queue already exists, we have to recompute the priorities of the inference requests
    		synchronized(requestQueue) {
    			// make a temporary list of all the inference requests
    			List<InferenceRequestWrapper> temp = new ArrayList<InferenceRequestWrapper>();
    			requestQueue.drainTo(temp);
    			
    			// ask the requestPriorityProvider to recompute the priorities
    			requestPriorityProvider.computePriorities(temp, this);
    			
    			// re-add the re-prioritized requests to the queue 
    			requestQueue.addAll(temp);
    		}
    	}
    }
    
    
    private void configureExecutionParameters(Properties execConfiguration) {
    	inferenceExecutor = createInferenceExecutor(execConfiguration);
    	
    	try {
			String schedulerSleepStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_INFERENCE_SCHEDULER_SLEEP, "" + DEFAULT_SCHEDULER_SLEEP);
			schedulingSleep = Integer.parseInt(schedulerSleepStr);
        }
		catch(NumberFormatException e) {
		    schedulingSleep = DEFAULT_SCHEDULER_SLEEP;
        }
    	
    	try {
			String runWindowStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_INFERENCE_RUN_WINDOW, "" + DEFAULT_RUN_WINDOW);
			runWindowWidth = Long.parseLong(runWindowStr);
        }
		catch(NumberFormatException e) {
			runWindowWidth = DEFAULT_RUN_WINDOW;
        }
    }
    
    private ThreadPoolExecutor createInferenceExecutor(Properties execConfiguration) {
		try {
			String numWorkersStr = execConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_NUM_INFERENCE_THREADS, "" + DEFAULT_NUM_WORKERS);
			numWorkers = Integer.parseInt(numWorkersStr);
        }
		catch(NumberFormatException e) {
		    numWorkers = DEFAULT_NUM_WORKERS;
        }
		
		return (ThreadPoolExecutor)Executors.newFixedThreadPool(numWorkers, new ContextInferenceThreadFactory());
	}
    
    
    private void setupScheduler() {
    	// setup the internal scheduler (a ScheduledExecutorService)
    	schedulingTimer = Executors.newScheduledThreadPool(1);
    	
    	// setup the default inference request comparator provider
    	requestPriorityProvider = FCFSPriorityProvider.getInstance();
    }
    
    // ============================== RUNTIME ==============================
 	////////////////////////////////////////////////////////////////////////
    public void start() {
		// start the first scheduling task
    	schedulingTimer.schedule(new InferenceSchedulingTask(), 0, TimeUnit.MILLISECONDS);
	}
	
    @Override
    public void stop() {
    	// stop the internal scheduler, allowing the last schedule task to finish execution
	    schedulingTimer.shutdown();
    }
    
    @Override
    public void close() {
    	// stop the internal scheduler now
    	schedulingTimer.shutdownNow();
    	
    	// close executors
		inferenceExecutor.shutdown();
    }
	
	
    public void setComparatorProvider(InferencePriorityProvider requestComparatorProvider, boolean reviseQueue) {
    	this.requestPriorityProvider = requestComparatorProvider;
    	
    	if (reviseQueue) {
    		configureRequestQueue();
    	}
    }
    
    
	public void executeRequest(CheckInferenceHook inferenceRequest) {
		// Only insert the incoming request if it is not already 
		// in the requestQueue (i.e. we already have a pending request for this type of derived assertion)
		InferenceRequestWrapper wrappedRequest = new InferenceRequestWrapper(inferenceRequest);
		if (!requestQueue.contains(wrappedRequest)) {
			requestQueue.add(wrappedRequest);
			
			// If so, mark the request queue as having been changed
			// This will effectively schedule a re-computation of the order of the pending inference requests.
			newRequests.set(true);
		}
		else {
			System.out.println("We already have a inferenceRequest for: " + wrappedRequest.getDerivationRule().getDerivedAssertion());
		}
	}
	
	
	private class InferenceSchedulingTask implements Runnable {
		
		@Override
        public void run() {
			InferenceRequestWrapper request = null;
			try {
            	// STEP 1a: wait for a maximum of `schedulingSleep ' for an available request
            	request = requestQueue.poll(schedulingSleep, TimeUnit.MILLISECONDS);
            	
            	// STEP 1b: if we have a request submit it to the inference executors.
            	if (request != null) {
            		inferenceExecutor.submit(new ContextInferenceTask(request.getInferenceRequest()));
            	}
            	else {
            		// Otherwise check if our timer is still accepting tasks (we are not stopped),
                	// and reschedule. Then end current task.
            		if (!schedulingTimer.isShutdown()) {
            			schedulingTimer.schedule(new InferenceSchedulingTask(), 0, TimeUnit.MILLISECONDS);
            		}
            		
            		return;
            	}
            }
            catch (InterruptedException e) {
            	// if we get interrupted (for some reason) we try to reschedule if not stopped
            	if (!schedulingTimer.isShutdown()) {
            		schedulingTimer.schedule(new InferenceSchedulingTask(), schedulingSleep, TimeUnit.MILLISECONDS);
            	}
            	
            	return;
            }
			
			// STEP 2: If we had a response in STEP 1 try and take (numWorkers - 1) more new requests and submit them
			for (int i = 0; i < numWorkers - 1; i++) {
	        	InferenceRequestWrapper req = requestQueue.poll();
	        	if (req != null) {
	        		inferenceExecutor.submit(new ContextInferenceTask(req.getInferenceRequest()));
	        	}
	        }
			
			// STEP 3: if from last time there were new entries in the request queue,
			// reconfigure it based on updated statistics
			if (newRequests.get()) {
				configureRequestQueue();	// if the queue is now empty, we basically just update the comparator
				newRequests.set(false);		// reset newRequest marker
			}
			
			// STEP 4: if we are not stopped, schedule a new InferenceScheduling task within `schedulingSleep' ms
			if (!schedulingTimer.isShutdown()) {
				schedulingTimer.schedule(new InferenceSchedulingTask(), schedulingSleep, TimeUnit.MILLISECONDS);
			}
        }
	}
	
	
	// ================ Inference Statistics Implementation ================
	////////////////////////////////////////////////////////////////////////
	
	private ContextAssertion lastDerivedAssertion;
	private ConcurrentMap<ContextAssertion, Integer> derivedAssertionPopularity = new ConcurrentHashMap<ContextAssertion, Integer>();
	private ConcurrentMap<ContextAssertion, Double> derivedAssertionExecRatio = new ConcurrentHashMap<ContextAssertion, Double>();
	
	@Override
    public long runWindowWidth() {
	    return runWindowWidth;
    }
	
	
	@Override
    public ContextAssertion lastDerivation() {
	    return lastDerivedAssertion;
    }


	@Override
    public Map<ContextAssertion, Integer> currentPopularity() {
		return derivedAssertionPopularity;
    }


	@Override
    public Map<ContextAssertion, Double> ratioSuccessfulExec() {
	    return derivedAssertionExecRatio;
    }
	
	
	// ============================== Factory ==============================
	////////////////////////////////////////////////////////////////////////
	
	public static InferenceService getInstance() {
		if (instance == null) {
			instance = new InferenceService();
		}
		
		return instance;
	}

}
