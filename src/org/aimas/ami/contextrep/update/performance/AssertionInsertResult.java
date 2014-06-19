package org.aimas.ami.contextrep.update.performance;

import java.util.List;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.update.ConstraintResult;
import org.aimas.ami.contextrep.update.ContinuityResult;

public class AssertionInsertResult implements Comparable<AssertionInsertResult> {
	private int referenceID;
	private long startTime;
	private int duration;
	
	private List<ContextAssertion> insertedAssertions;
	
	private List<ContinuityResult> continuityResults;
	private List<ConstraintResult> constraintResults;
	
	
	public AssertionInsertResult(int referenceID, long startTime, int duration, List<ContextAssertion> insertedAssertions, 
			List<ContinuityResult> continuityResults, List<ConstraintResult> constraintResults) {
	    this.referenceID = referenceID;
	    this.startTime = startTime;
	    this.duration = duration;
	    
	    this.insertedAssertions = insertedAssertions;
	    this.continuityResults = continuityResults;
	    this.constraintResults = constraintResults;
    }

	public int getReferenceID() {
		return referenceID;
	}

	public long getStartTime() {
		return startTime;
	}

	public int getDuration() {
		return duration;
	}

	public List<ContextAssertion> getInsertedAssertions() {
		return insertedAssertions;
	}
	
	public List<ContinuityResult> continuityResults() {
	    return continuityResults;
    }

	public List<ConstraintResult> constraintResults() {
	    return constraintResults;
    }

	@Override
    public int compareTo(AssertionInsertResult o) {
	    return referenceID - o.getReferenceID();
    }
}
