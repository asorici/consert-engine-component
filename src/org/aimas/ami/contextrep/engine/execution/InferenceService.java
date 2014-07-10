package org.aimas.ami.contextrep.engine.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;
import org.aimas.ami.contextrep.engine.api.EngineInferenceStats;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.update.CheckInferenceHook;
import org.aimas.ami.contextrep.update.ContextInferenceTask;

import com.hp.hpl.jena.rdf.model.Resource;

public class InferenceService implements ExecutionService, EngineInferenceStats, InferenceStatsCollector {
	private static InferenceService instance;
	
	private static final String INFERENCE_SCHEDULER_THREAD = "inference-scheduler-thread";
	
	private static final int DEFAULT_NUM_WORKERS = 1;
	private static final int DEFAULT_SCHEDULER_SLEEP = 100;
	private static final long DEFAULT_RUN_WINDOW = 10;
	
	
	private ThreadPoolExecutor inferenceExecutor;
	private int numWorkers = DEFAULT_NUM_WORKERS;
	
	private PriorityBlockingQueue<InferenceRequestWrapper> requestQueue;
	private InferencePriorityProvider requestPriorityProvider;
	private AtomicBoolean newRequests = new AtomicBoolean(false);
	
	private ScheduledExecutorService schedulingTimer;
	private int schedulingSleep = DEFAULT_SCHEDULER_SLEEP;
	
	private long defaultRunWindow = DEFAULT_RUN_WINDOW;
	private Map<Resource, Long> assertionSpecificRunWindow = new HashMap<Resource, Long>();	
	
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
    	
    	// setup stats collection
    	configureStatisticsCollector();
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
			defaultRunWindow = Long.parseLong(runWindowStr);
        }
		catch(NumberFormatException e) {
			defaultRunWindow = DEFAULT_RUN_WINDOW;
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
	
	
    public void setPriorityProvider(InferencePriorityProvider requestPriorityProvider, boolean reviseQueue) {
    	this.requestPriorityProvider = requestPriorityProvider;
    	
    	if (reviseQueue) {
    		configureRequestQueue();
    	}
    }
    
    
    
    public void setDefaultRunWindow(long runWindow) {
    	this.defaultRunWindow = runWindow;
    }
    
    public long getDefaultRunWindow() {
    	return defaultRunWindow;
    }
    
    public void setSpecificRunWindow(Resource contextAssertionRes, long runWindow) {
    	assertionSpecificRunWindow.put(contextAssertionRes, runWindow);
    }
    
    public Long getSpecificRunWindow(Resource contextAssertionRes) {
    	return assertionSpecificRunWindow.get(contextAssertionRes);
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
	
	private ContextDerivationRule lastDerivation;
	private DerivationRuleTracker derivationRuleTracker;
	
	
	private void configureStatisticsCollector() {
		derivationRuleTracker = new DerivationRuleTracker();
	}
	
	
	
	@Override
    public ContextDerivationRule lastDerivation() {
	    return lastDerivation;
    }
	
	
	@Override
    public Map<ContextDerivationRule, Integer> nrDerivations() {
	    return derivationRuleTracker.nrDerivations();
    }


	@Override
    public Map<ContextDerivationRule, Integer> nrSuccessfulDerivations() {
	    return derivationRuleTracker.nrSuccessfulDerivations();
    }
	
	
	@Override
    public void markInferenceExecution(ContextDerivationRule rule, boolean successful) {
	    if (successful) {
	    	lastDerivation = rule;
	    }
	    
	    derivationRuleTracker.markInferenceExecution(rule, System.currentTimeMillis(), successful);
    }
	
	
	private class DerivationRuleTracker {
		Map<ContextDerivationRule, Queue<RuleTrackParams>> ruleTracker;
		
		DerivationRuleTracker () {
			this.ruleTracker = new HashMap<ContextDerivationRule, Queue<RuleTrackParams>>();
		}
		
		
		void markInferenceExecution(ContextDerivationRule rule, long timestamp, boolean successful) {
			synchronized(ruleTracker) {
				Queue<RuleTrackParams> runTimestamps = ruleTracker.get(rule);
				if (runTimestamps == null) {
					runTimestamps = new LinkedList<RuleTrackParams>();
					runTimestamps.add(new RuleTrackParams(timestamp, successful));
					
					ruleTracker.put(rule, runTimestamps);
				}
				else {
					runTimestamps.add(new RuleTrackParams(timestamp, successful));
					siftTracker();
				}
			}
		}
		
		
		void siftTracker() {
			long now = System.currentTimeMillis();
			
			for (ContextDerivationRule rule : ruleTracker.keySet()) {
				ContextAssertion derivedAssertion = rule.getDerivedAssertion();
				long runWindowWidth = defaultRunWindow;
				if (assertionSpecificRunWindow.get(derivedAssertion.getOntologyResource()) != null) {
					runWindowWidth = assertionSpecificRunWindow.get(derivedAssertion.getOntologyResource());
				}
				
				long windowStart = now - runWindowWidth;
				
				Queue<RuleTrackParams> runTimestamps = ruleTracker.get(rule);
				while(true) {
					RuleTrackParams p = runTimestamps.peek();
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
		
		
		Map<ContextDerivationRule, Integer> nrDerivations() {
			synchronized(ruleTracker) {
				siftTracker();
				
				Map<ContextDerivationRule, Integer> counter = new HashMap<ContextDerivationRule, Integer>();
				for (ContextDerivationRule rule : ruleTracker.keySet()) {
					counter.put(rule, ruleTracker.get(rule).size());
				}
				
				return counter;
			}
		}
		
		
		Map<ContextDerivationRule, Integer> nrSuccessfulDerivations() {
			synchronized(ruleTracker) {
				siftTracker();
				
				Map<ContextDerivationRule, Integer> counter = new HashMap<ContextDerivationRule, Integer>();
				for (ContextDerivationRule rule : ruleTracker.keySet()) {
					Queue<RuleTrackParams> runTimestamps = ruleTracker.get(rule);
					
					int ct = 0;
					for (RuleTrackParams p : runTimestamps) {
						if (p.successful()) {
							ct++;
						}
					}
					
					counter.put(rule, ct);
				}
				
				return counter;
			}
		}
	}
	
	private static class RuleTrackParams {
		long timestamp;
		boolean successful;
		
		RuleTrackParams(long timestamp, boolean successful) {
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
	
	// ============================== Factory ==============================
	////////////////////////////////////////////////////////////////////////
	
	public static InferenceService getInstance() {
		if (instance == null) {
			instance = new InferenceService();
		}
		
		return instance;
	}
}
