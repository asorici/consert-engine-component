package org.aimas.ami.contextrep.engine.execution;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.test.performance.PerformanceResult;
import org.aimas.ami.contextrep.update.InferenceResult;


public class ExecutionMonitor {
	public static final int CLEAR_RECORD_THRESHOLD = 1000000;
	
	private static ExecutionMonitor instance;
	
	/* Enable switch */
	private boolean enabled = false;
	
	/* Internal datastructures */
	private List<Integer> insertionList = new LinkedList<Integer>();
	private Map<Integer, AssertionInsertInfo> insertionMonitor = new HashMap<Integer, AssertionInsertInfo>();
	
	private List<Integer> inferenceList = new LinkedList<Integer>();
	private Map<Integer, AssertionInferenceInfo> inferenceMonitor = new HashMap<Integer, AssertionInferenceInfo>();
	
	private ExecutionMonitor() {}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public synchronized void clearHistory() {
		insertionList.clear();
		insertionMonitor.clear();
		
		inferenceList.clear();
		inferenceMonitor.clear();
	}
	
	// Singleton
	/////////////////////////////////////////////////////////////////////////////////////////
	public static ExecutionMonitor getInstance() {
		if (instance == null) {
			instance = new ExecutionMonitor();
		}
		
		return instance;
	}
	
	
	// EXECUTION MONITORING
	////////////////////////////////////////////////////////////////////////////////////////
	// ========================== INSERT MONITOR ==========================
	public synchronized void logInsertEnqueue(int insertID) {
		if (enabled) {
			if (insertionList.size() == CLEAR_RECORD_THRESHOLD) {
				clearHistory();
			}
			
			insertionList.add(insertID);
			insertionMonitor.put(insertID, new AssertionInsertInfo(insertID, System.currentTimeMillis()));
		}
	}
	
	public synchronized void logDerivedInsertEnqueue(int triggerInsertID, int insertID) {
		if (enabled) {
			if (insertionList.size() == CLEAR_RECORD_THRESHOLD) {
				clearHistory();
			}
			
			AssertionInsertInfo insertInfo = new AssertionInsertInfo(insertID, System.currentTimeMillis());
			insertionList.add(insertID);
			insertionMonitor.put(insertID, insertInfo);
			
			AssertionInferenceInfo generatorInferenceInfo = inferenceMonitor.get(triggerInsertID);
			if (generatorInferenceInfo != null) {
				generatorInferenceInfo.addTriggeredInsertion(insertInfo);
			}
		}
	}
	
	public void logInsertExecStart(int insertID) {
		if (enabled) {
			AssertionInsertInfo insertInfo = insertionMonitor.get(insertID);
			if (insertInfo != null) {
				insertInfo.setStartTime(System.currentTimeMillis());
			}
		}
	}
	
	public void logInsertExecEnd(int insertID, InsertResult result) {
		if (enabled) {
			AssertionInsertInfo insertInfo = insertionMonitor.get(insertID);
			if (insertInfo != null) {
				insertInfo.setEndTime(System.currentTimeMillis());
			}
		}
	}
	
	public void logContinuityCheckDuration(int insertID, long duration) {
		if (enabled) {
			AssertionInsertInfo insertInfo = insertionMonitor.get(insertID);
			if (insertInfo != null) {
				insertInfo.setContinuityCheckDuration(duration);
			}
		}
	}
	
	public void logConstraintCheckDuration(int insertID, long duration) {
		if (enabled) {
			AssertionInsertInfo insertInfo = insertionMonitor.get(insertID);
			if (insertInfo != null) {
				insertInfo.setConstraintCheckDuration(duration);
			}
		}
	}
	
	public void logInheritanceCheckDuration(int insertID, long duration) {
		if (enabled) {
			AssertionInsertInfo insertInfo = insertionMonitor.get(insertID);
			if (insertInfo != null) {
				insertInfo.setInheritanceCheckDuration(duration);
			}
		}
	}
	
	// ======================== INFERENCE MONITOR ========================
	public synchronized void logInferenceEnqueue(int triggeringInsertID) {
		if (enabled) {
			if (inferenceList.size() == CLEAR_RECORD_THRESHOLD) {
				clearHistory();
			}
			
			AssertionInferenceInfo inferenceInfo = new AssertionInferenceInfo(triggeringInsertID, System.currentTimeMillis());
			inferenceList.add(triggeringInsertID);
			inferenceMonitor.put(triggeringInsertID, inferenceInfo);
			
			AssertionInsertInfo insertInfo = insertionMonitor.get(triggeringInsertID);
			//if (insertInfo != null) {
				insertInfo.addTriggeredInference(inferenceInfo);
			//}
		}
	}
	
	public void logInferenceExecStart(int triggeringInsertID) {
		if (enabled) {
			AssertionInferenceInfo inferenceInfo = inferenceMonitor.get(triggeringInsertID);
			if (inferenceInfo != null) {
				inferenceInfo.setStartTime(System.currentTimeMillis());
			}
		}
	}
	
	public void logInferenceExecEnd(int triggeringInsertID, InferenceResult result) {
		if (enabled) {
			AssertionInferenceInfo inferenceInfo = inferenceMonitor.get(triggeringInsertID);
			if (inferenceInfo != null) {
				inferenceInfo.setEndTime(System.currentTimeMillis());
			}
		}
	}
	
	
	// Performance export
	public PerformanceResult exportPerformanceResult() {
		PerformanceResult performanceResult = new PerformanceResult();
		
		List<AssertionInsertInfo> insertInfoList = new LinkedList<AssertionInsertInfo>(insertionMonitor.values());
		Collections.sort(insertInfoList);
		
		List<AssertionInferenceInfo> inferenceInfoList = new LinkedList<AssertionInferenceInfo>(inferenceMonitor.values());
		Collections.sort(inferenceInfoList);
		
		for (int i = 0; i < insertInfoList.size(); i++) {
			AssertionInsertInfo insertInfo = insertInfoList.get(i);
			if (insertInfo.isFinished()) {
				performanceResult.accumulateInsert(i, insertInfo.delay(), insertInfo.execDuration());
				performanceResult.accumulateContinuityCheck(i, insertInfo.getContinuityCheckDuration());
				performanceResult.accumulateConstraintCheck(i, insertInfo.getConstraintCheckDuration());
				
				// count deduction cycle if existent
				for (AssertionInferenceInfo inferenceInfo : insertInfo.getTriggeredInferences()) {
					if (inferenceInfo.isFinished()) {
						for (AssertionInsertInfo triggeredInsertInfo : inferenceInfo.getTriggeredInsertions()) {
							if (triggeredInsertInfo.isFinished()) {
								long deductionCycleDuration = triggeredInsertInfo.getEndTime() - insertInfo.getEnqueueTime();
								performanceResult.accumulateDeductionCycle(i, deductionCycleDuration);
							}
						}
					}
				}
			}
		}
		
		for (int i = 0; i < inferenceInfoList.size(); i++) {
			AssertionInferenceInfo inferenceInfo = inferenceInfoList.get(i);
			if (inferenceInfo.isFinished()) {
				
				// find the index in the sorted insertInfoList that matches the triggerID
				for (int j = 0; j < insertInfoList.size(); j++) {
					AssertionInsertInfo insertInfo = insertInfoList.get(j);
					if (insertInfo.getInsertID() == inferenceInfo.getTriggerID()) {
						performanceResult.accumulateInference(j, inferenceInfo.delay(), inferenceInfo.execDuration());
						break;
					}
				}
			}
		}
		
		performanceResult.doAverages();
		
		return performanceResult;
	}
	
	
	// Monitorization info classes
	/////////////////////////////////////////////////////////////////////////////////////////
	private static class MonitorizationBase implements Comparable<MonitorizationBase> {
		protected int id;
		
		protected long enqueueTime;
		protected long startTime;
		protected long endTime;
		protected boolean finished = false;
		
		public MonitorizationBase(int id, long enqueueTime) {
	        this.id = id;
	        this.enqueueTime = enqueueTime;
        }
		
		public long getEnqueueTime() {
			return enqueueTime;
		}
		
		public long getStartTime() {
			return startTime;
		}

		public void setStartTime(long startTime) {
			this.startTime = startTime;
		}

		public long getEndTime() {
			return endTime;
		}

		public void setEndTime(long endTime) {
			this.endTime = endTime;
			this.finished = true;
		}
		
		public boolean isFinished() {
			return finished;
		}
		
		public long delay() {
			return startTime - enqueueTime >= 0 ? startTime - enqueueTime : 0; 
		}
		
		public long execDuration() {
			return endTime - startTime;
		}
		
		public long totalDuration() {
			return endTime - enqueueTime;
		}

		@Override
        public int compareTo(MonitorizationBase o) {
	        if (enqueueTime < o.getEnqueueTime()) 
	        	return -1;
	        else if (enqueueTime > o.getEnqueueTime())
	        	return 1;
	        
	        return 0;
        }
	}
	
	private static class AssertionInsertInfo extends MonitorizationBase {
		List<AssertionInferenceInfo> triggeredInferences = new LinkedList<AssertionInferenceInfo>();
		long continuityCheckDuration;
		long constraintCheckDuration;
		long inheritanceCheckDuration;
		
		AssertionInsertInfo(int id, long enqueueTime) {
	        super(id, enqueueTime);
        }
		
		public int getInsertID() {
			return id;
		}

		public long getContinuityCheckDuration() {
			return continuityCheckDuration;
		}

		public void setContinuityCheckDuration(long continuityCheckDuration) {
			this.continuityCheckDuration = continuityCheckDuration;
		}

		public long getConstraintCheckDuration() {
			return constraintCheckDuration;
		}

		public void setConstraintCheckDuration(long constraintCheckDuration) {
			this.constraintCheckDuration = constraintCheckDuration;
		}

		public long getInheritanceCheckDuration() {
			return inheritanceCheckDuration;
		}

		public void setInheritanceCheckDuration(long inheritanceCheckDuration) {
			this.inheritanceCheckDuration = inheritanceCheckDuration;
		}

		public List<AssertionInferenceInfo> getTriggeredInferences() {
			return triggeredInferences;
		}
		
		public void addTriggeredInference(AssertionInferenceInfo inferenceInfo) {
			triggeredInferences.add(inferenceInfo);
		}
	}
	
	private static class AssertionInferenceInfo extends MonitorizationBase {
		List<AssertionInsertInfo> triggeredInsertions = new LinkedList<AssertionInsertInfo>();
		
		AssertionInferenceInfo(int id, long enqueueTime) {
	        super(id, enqueueTime);
        }
		
		public int getTriggerID() {
			return id;
		}

		public List<AssertionInsertInfo> getTriggeredInsertions() {
			return triggeredInsertions;
		}
		
		public void addTriggeredInsertion(AssertionInsertInfo insertionInfo) {
			triggeredInsertions.add(insertionInfo);
		}
	}
}
