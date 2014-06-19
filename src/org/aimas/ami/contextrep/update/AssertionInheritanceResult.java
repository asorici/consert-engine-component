package org.aimas.ami.contextrep.update;

import java.util.List;

import org.aimas.ami.contextrep.model.ContextAssertion;

public class AssertionInheritanceResult extends HookResult {
	
	private List<ContextAssertion> ancestorAssertionList;
	
	public AssertionInheritanceResult(ContextAssertion assertion, Exception error, 
			List<ContextAssertion> ancestorAssertionList) {
	    
		super(assertion, error);
	    this.ancestorAssertionList = ancestorAssertionList;
    }
	
	
	public List<ContextAssertion> getAncestorAssertionList() {
		return ancestorAssertionList;
	}
	
	
	public boolean inherits() {
		return ancestorAssertionList != null && !ancestorAssertionList.isEmpty();
	}
}
