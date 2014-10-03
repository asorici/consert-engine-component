package org.aimas.ami.contextrep.engine.api;

import java.util.HashMap;
import java.util.Map;

public class PerformanceResult {
	public static long INF = 10000000;
	
	public long minInsertionDelay = INF;
	public long averageInsertionDelay;
	public long maxInsertionDelay = -INF;
	public long numInsertions;
	
	public long minInferenceDelay = INF;
	public long averageInferenceDelay;
	public long maxInferenceDelay = -INF;
	public long numInferences;
	
	public long minDeductionCycleDuration = INF;
	public long averageDeductionCycleDuration;
	public long maxDeductionCycleDuration = -INF;
	public long numDeductionCycles;
	
	public long minInsertionDuration = INF;
	public long averageInsertionDuration;
	public long maxInsertionDuration = -INF;
	
	public long minInferenceCheckDuration = INF;
	public long averageInferenceCheckDuration;
	public long maxInferenceCheckDuration = -INF;
	
	public long minContinuityCheckDuration = INF;
	public long averageContinuityCheckDuration;
	public long maxContinuityCheckDuration = -INF;
	
	public long minConstraintCheckDuration = INF;
	public long averageConstraintCheckDuration;
	public long maxConstraintCheckDuration = -INF;
	
	public Map<Integer, Long> insertionDelayHistory = new HashMap<Integer, Long>();
	public Map<Integer, Long> inferenceDelayHistory = new HashMap<Integer, Long>();
	
	public Map<Integer, Long> insertionDurationHistory = new HashMap<Integer, Long>();
	public Map<Integer, Long> inferenceDurationHistory = new HashMap<Integer, Long>();
	
	public Map<Integer, Long> deductionCycleHistory = new HashMap<Integer, Long>();
	
	public void accumulateInsert(int insertID, long insertDelay, long insertDuration) {
		averageInsertionDelay += insertDelay;
		averageInsertionDuration += insertDuration;
		numInsertions++;
		
		if (insertDelay > maxInsertionDelay)
			maxInsertionDelay = insertDelay;
		if (insertDelay < minInsertionDelay)
			minInsertionDelay = insertDelay;
		
		if (insertDuration > maxInsertionDuration)
			maxInsertionDuration = insertDuration;
		if (insertDuration < minInsertionDuration)
			minInsertionDuration = insertDuration;
		
		insertionDelayHistory.put(insertID, insertDelay);
		insertionDurationHistory.put(insertID, insertDuration);
	}
	
	public void accumulateContinuityCheck(int insertID, long duration) {
		averageContinuityCheckDuration += duration;
		
		if (duration > maxContinuityCheckDuration)
			maxContinuityCheckDuration = duration;
		if (duration < minContinuityCheckDuration)
			minContinuityCheckDuration = duration;
	}
	
	
	public void accumulateConstraintCheck(int insertID, long duration) {
		averageConstraintCheckDuration += duration;
		
		if (duration > maxConstraintCheckDuration)
			maxConstraintCheckDuration = duration;
		if (duration < minConstraintCheckDuration)
			minConstraintCheckDuration = duration;
	}
	
	public void accumulateInference(int triggerID, long inferenceDelay, long inferenceDuration) {
		averageInferenceDelay += inferenceDelay;
		averageInferenceCheckDuration += inferenceDuration;
		numInferences++;
		
		inferenceDelayHistory.put(triggerID, inferenceDelay);
		inferenceDurationHistory.put(triggerID, inferenceDuration);
		
		if (inferenceDelay > maxInferenceDelay)
			maxInferenceDelay = inferenceDelay;
		if (inferenceDelay < minInferenceDelay)
			minInferenceDelay = inferenceDelay;
		
		if (inferenceDuration > maxInferenceCheckDuration)
			maxInferenceCheckDuration = inferenceDuration;
		if (inferenceDuration < minInferenceCheckDuration)
			minInferenceCheckDuration = inferenceDuration;
	}
	
	public void accumulateDeductionCycle(int triggerID, long deductionCycleDuration) {
		averageDeductionCycleDuration += deductionCycleDuration;
		numDeductionCycles++;
		
		if (deductionCycleDuration > maxDeductionCycleDuration) 
			maxDeductionCycleDuration = deductionCycleDuration;
		if (deductionCycleDuration < minDeductionCycleDuration) 
			minDeductionCycleDuration = deductionCycleDuration;
		
		deductionCycleHistory.put(triggerID, deductionCycleDuration);
	}
	
	public void doAverages() {
		if (numInsertions != 0) {
			averageInsertionDelay /= numInsertions;
			averageInsertionDuration /= numInsertions;
			
			averageContinuityCheckDuration /= numInsertions;
			averageConstraintCheckDuration /= numInsertions;
		}
		
		if (numInferences != 0) {
			averageInferenceDelay /= numInferences;
			averageInferenceCheckDuration /= numInferences;
		}
		
		if (numDeductionCycles != 0) {
			averageDeductionCycleDuration /= numDeductionCycles;
		}
	}
}
