package org.aimas.ami.contextrep.engine.execution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;
import org.aimas.ami.contextrep.engine.api.EngineInferenceStats;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.api.InferenceRequest;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.update.CheckInferenceHook;
import org.aimas.ami.contextrep.update.ContextInferenceTask;

import com.hp.hpl.jena.rdf.model.Resource;

public class InferenceService implements ExecutionService, EngineInferenceStats, InferenceStatsCollector {
	private static InferenceService instance;
	
	private static final int DEFAULT_NUM_WORKERS = 1;
	private static final int DEFAULT_PRIORITY_QUEUE_CAPACITY = 10;
	
	private static final int DEFAULT_SCHEDULER_SLEEP = 50;
	private static final long DEFAULT_RUN_WINDOW = 60000;
	
	
	private ThreadPoolExecutor inferenceExecutor;
	private int numWorkers = DEFAULT_NUM_WORKERS;
	
	private PriorityBlockingQueue<InferenceRequestWrapper> requestQueue;
	private InferencePriorityProvider rulePriorityProvider;
	private AtomicBoolean newRequests = new AtomicBoolean(false);
	
	private ScheduledExecutorService schedulingTimer;
	private int schedulingSleep = DEFAULT_SCHEDULER_SLEEP;
	
	private ScheduledExecutorService priorityEvaluationTimer;
	private ScheduledFuture<?> priorityEvaluationTask;
	
	private long defaultRunWindow = DEFAULT_RUN_WINDOW;
	private Map<Resource, Long> assertionSpecificRunWindow = new HashMap<Resource, Long>();	
	
	// ================= Initialization and Configurations =================
	////////////////////////////////////////////////////////////////////////
	
	@Override
    public void init(Properties execConfiguration) {
    	// configure execution parameters
		configureExecutionParameters(execConfiguration);
    	
    	
    	// setup stats collection
    	configureStatisticsCollector();
	}
    

	private void configureRequestQueue(Comparator<InferenceRequest> priorityComparator) {
    	if (requestQueue == null) {
	    	// first time initialization, we just have to create the queue
	    	requestQueue = new PriorityBlockingQueue<InferenceRequestWrapper>(DEFAULT_PRIORITY_QUEUE_CAPACITY, priorityComparator);
    	}
    	else {
    		// queue already exists, we have to recompute the priorities of the inference requests
    		synchronized(requestQueue) {
    			// make a temporary list of all the inference requests
    			List<InferenceRequestWrapper> temp = new ArrayList<InferenceRequestWrapper>();
    			requestQueue.drainTo(temp);
    			
    			// set the new priority comparator for the inference request queue
    			requestQueue = new PriorityBlockingQueue<InferenceRequestWrapper>(temp.size(), priorityComparator);
    			
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
    }
    
    private void setupPriorityComputation() {
    	// setup the default inference request comparator provider
    	if (rulePriorityProvider == null) {
    		rulePriorityProvider = FCFSPriorityProvider.getInstance();
    	}
    	
    	// setup the internal scheduler for the priority evaluation
    	priorityEvaluationTimer = Executors.newScheduledThreadPool(1);
    }

    
    
    // ============================== RUNTIME ==============================
 	////////////////////////////////////////////////////////////////////////
    public void start() {
    	// 1) configure priority evaluation
    	if (priorityEvaluationTimer == null || priorityEvaluationTimer.isShutdown()) {
    		setupPriorityComputation();
    	}
    	
    	// 2) configure the request queue
    	List<ContextDerivationRule> activeDerivationRules = getActiveDerivationRules();
		Map<ContextDerivationRule, Integer> priorityMap = rulePriorityProvider.computePriorities(activeDerivationRules, InferenceService.this);
		Comparator<InferenceRequest> priorityComparator = new InferencePriorityComparator(priorityMap);
    	configureRequestQueue(priorityComparator);
    	
    	// 3) configure the inference scheduler
    	if (schedulingTimer == null || schedulingTimer.isShutdown()) {
    		setupScheduler();
    	}
    	
    	// 4) start the above schedulers
    	schedulingTimer.schedule(new InferenceSchedulingTask(), 0, TimeUnit.MILLISECONDS);
    	priorityEvaluationTask = priorityEvaluationTimer.scheduleAtFixedRate(new PriorityEvaluationTask(), defaultRunWindow / 2, defaultRunWindow, TimeUnit.MILLISECONDS);
	}
	
    @Override
    public void stop() {
    	// stop the internal scheduler, allowing the last schedule task to finish execution
	    if (schedulingTimer != null) {
	    	schedulingTimer.shutdown();
	    	schedulingTimer = null;
	    }
	    
	    // stop the internal priority evaluation scheduler, canceling the ongoing task
	    if (priorityEvaluationTimer != null) {
	    	priorityEvaluationTimer.shutdown();
	    	priorityEvaluationTimer = null;
	    	priorityEvaluationTask = null;
	    }
    }
    
    @Override
    public void close() {
    	// stop the internal scheduler now
    	if (schedulingTimer != null) {
    		schedulingTimer.shutdownNow();
    	}
    	
    	// stop the priority evaluation scheduler
    	if (priorityEvaluationTimer != null) {
    		priorityEvaluationTimer.shutdown();
    		priorityEvaluationTimer = null;
    		priorityEvaluationTask = null;
    	}
    	
    	// close executors
		inferenceExecutor.shutdown();
    }
	
	
    public void setPriorityProvider(InferencePriorityProvider requestPriorityProvider, boolean reviseQueue) {
    	// set the new rule priority evaluation provider
    	this.rulePriorityProvider = requestPriorityProvider;
    	
    	if (reviseQueue) {
    		// compute the new priority comparator now and revise the queue ordering
    		List<ContextDerivationRule> activeDerivationRules = getActiveDerivationRules();
    		Map<ContextDerivationRule, Integer> priorityMap = requestPriorityProvider.computePriorities(activeDerivationRules, InferenceService.this);
    		Comparator<InferenceRequest> priorityComparator = new InferencePriorityComparator(priorityMap);
        	
    		configureRequestQueue(priorityComparator);
    	}
    }
    
    
    public void setDefaultRunWindow(long runWindow) {
    	this.defaultRunWindow = runWindow * 1000;	// conversion to milliseconds
    	
    	// If we change the default run window and there is an active priorityEvaluation task, cancel it and reschedule
    	if (priorityEvaluationTask != null) {
    		priorityEvaluationTask.cancel(false);
    		priorityEvaluationTask = priorityEvaluationTimer.scheduleAtFixedRate(new PriorityEvaluationTask(), defaultRunWindow / 2, defaultRunWindow, TimeUnit.MILLISECONDS);
    	}
    }
    
    /**
     * @return the default RUN_WINDOW width in milliseconds
     */
    public long getDefaultRunWindow() {
    	return defaultRunWindow;
    }
    
    public void setSpecificRunWindow(Resource contextAssertionRes, long runWindow) {
    	assertionSpecificRunWindow.put(contextAssertionRes, runWindow * 1000);	// conversion to milliseconds
    }
    
    /**
     * @param contextAssertionRes
     * @return the specific RUN_WINDOW width in milliseconds for the ContextAssertion identified by 
     * <code>contextAssertionRes</code>
     */
    public Long getSpecificRunWindow(Resource contextAssertionRes) {
    	return assertionSpecificRunWindow.get(contextAssertionRes);
    }
    
    
    private List<ContextDerivationRule> getActiveDerivationRules() {
    	List<ContextAssertion> derivedAssertions = Engine.getContextAssertionIndex().getDerivedContextAssertions();
    	
    	List<ContextDerivationRule> activeDerivationRules = new LinkedList<ContextDerivationRule>();
		for (ContextAssertion assertion: derivedAssertions) {
			if (Engine.getDerivationRuleDictionary().isDerivedAssertionActive(assertion)) {
				List<DerivationRuleWrapper> assertionDerivationRules = Engine.getDerivationRuleDictionary().getDerivedAssertionRules(assertion);
				activeDerivationRules.addAll(assertionDerivationRules);
			}
		}
		
		return activeDerivationRules;
    }
    
    
	public void executeRequest(CheckInferenceHook inferenceRequest) {
		/*
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
			//System.out.println("We already have a inferenceRequest for: " + wrappedRequest.getDerivationRule().getDerivedAssertion());
		}
		*/
		
		InferenceRequestWrapper wrappedRequest = new InferenceRequestWrapper(inferenceRequest);
		requestQueue.add(wrappedRequest);
		newRequests.set(true);
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
			
			// STEP 3: if we are not stopped, schedule a new InferenceScheduling task within `schedulingSleep' ms
			if (!schedulingTimer.isShutdown()) {
				schedulingTimer.schedule(new InferenceSchedulingTask(), schedulingSleep, TimeUnit.MILLISECONDS);
			}
        }
	}
	
	
	private class PriorityEvaluationTask implements Runnable {

		@Override
        public void run() {
			// If from last time there were new entries in the request queue, re-evaluate based on updated statistics
			if (newRequests.get()) {
				// STEP 1: get all active ContextDerivationRules
				List<ContextDerivationRule> activeDerivationRules = getActiveDerivationRules();
				
				// STEP 2: invoke the inference priority provider service to get the priority map
				Map<ContextDerivationRule, Integer> priorityMap = rulePriorityProvider.computePriorities(activeDerivationRules, InferenceService.this);
				Comparator<InferenceRequest> priorityComparator = new InferencePriorityComparator(priorityMap);
				
				// STEP 3: recompute the inference priority queue
				configureRequestQueue(priorityComparator);
				
				// Lastly, reset newRequest marker
				newRequests.set(false);		
			}
        }
	}
	
	
	private class InferencePriorityComparator implements Comparator<InferenceRequest> {
		
		private Map<ContextDerivationRule, Integer> priorityMap;
		
		public InferencePriorityComparator(Map<ContextDerivationRule, Integer> priorityMap) {
	        this.priorityMap = priorityMap;
        }
		
		@Override
        public int compare(InferenceRequest req1, InferenceRequest req2) {
			int p1 = 0;
	        int p2 = 0;
        
			if (priorityMap != null) {
				p1 = priorityMap.get(req1.getDerivationRule()) == null ? 0 : priorityMap.get(req1.getDerivationRule());
		        p2 = priorityMap.get(req2.getDerivationRule()) == null ? 0 : priorityMap.get(req2.getDerivationRule());
	        }
			
	        if (p1 == p2) {
	        	if (req1.getEnqueueTimestamp() > req2.getEnqueueTimestamp()) 
	        		return -1;
	        	else if (req1.getEnqueueTimestamp() < req2.getEnqueueTimestamp()) 
	        		return 1;
	        	else 
	        		return 0;
	        }
	        else {
	        	return p2 - p1;
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
	    
	    derivationRuleTracker.markInferenceExecution(rule, Engine.currentTimeMillis(), successful);
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
			long now = Engine.currentTimeMillis();
			
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
