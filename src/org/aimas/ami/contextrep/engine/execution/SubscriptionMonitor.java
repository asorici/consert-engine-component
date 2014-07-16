package org.aimas.ami.contextrep.engine.execution;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.utils.ContextQueryUtil;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.query.ContextQueryTask;
import org.openjena.atlas.lib.SetUtils;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.util.IteratorCollection;

public class SubscriptionMonitor implements ContextInsertListener {
	private Map<ContextAssertion, Map<SubscriptionWrapper, List<QueryResultNotifier>>> subscriptionIndex;
	
	public SubscriptionMonitor() {
		subscriptionIndex = new HashMap<ContextAssertion, Map<SubscriptionWrapper,List<QueryResultNotifier>>>();
	}
	
	@Override
    public void notifyAssertionInserted(ContextAssertion assertion) {
	    // trigger a ContextQueryTask for each query and each subscription notifier per query
		Map<SubscriptionWrapper, List<QueryResultNotifier>> assertionSubscriptions = subscriptionIndex.get(assertion);
		if (assertionSubscriptions != null) {
			for (SubscriptionWrapper sw : assertionSubscriptions.keySet()) {
				List<QueryResultNotifier> resultNotifiers = assertionSubscriptions.get(sw);
				
				for (QueryResultNotifier notifier : resultNotifiers) {
					// submit the ContextQueryTask
					Engine.getQueryService().executeRequest(sw.getQuery(), sw.getInitialBinding(), notifier);
				}
			}
		}
    }
	
	public void newSubscription(Query query, QuerySolutionMap initialBindings, QueryResultNotifier subscriptionNotifier) {
		Set<ContextAssertion> referencedAssertions = analyzeSubscription(query, initialBindings);
		SubscriptionWrapper wrapper = new SubscriptionWrapper(query, initialBindings);
		
		for (ContextAssertion refAssertion : referencedAssertions) {
			Map<SubscriptionWrapper, List<QueryResultNotifier>> assertionSubscriptions = subscriptionIndex.get(refAssertion);
			if (assertionSubscriptions == null) {
				assertionSubscriptions = new HashMap<SubscriptionWrapper, List<QueryResultNotifier>>();
				
				List<QueryResultNotifier> queryResultNotifiers = new LinkedList<QueryResultNotifier>();
				queryResultNotifiers.add(subscriptionNotifier);
				assertionSubscriptions.put(wrapper, queryResultNotifiers);
				
				subscriptionIndex.put(refAssertion, assertionSubscriptions);
			}
			else {
				List<QueryResultNotifier> queryResultNotifiers = assertionSubscriptions.get(wrapper);
				if (queryResultNotifiers == null) {
					queryResultNotifiers = new LinkedList<QueryResultNotifier>();
					queryResultNotifiers.add(subscriptionNotifier);
					assertionSubscriptions.put(wrapper, queryResultNotifiers);
				}
				else if (!queryResultNotifiers.contains(subscriptionNotifier)) {
					queryResultNotifiers.add(subscriptionNotifier);
				}
			}
		}
	}
	
	
	private Set<ContextAssertion> analyzeSubscription(Query query, QuerySolutionMap initialBindings) {
	    OntModel coreContextModel = Engine.getModelLoader().getCoreContextModel();
	    return ContextQueryUtil.analyzeContextQuery(query, initialBindings, coreContextModel);
    }


	private class SubscriptionWrapper {
		private Query query;
		private QuerySolutionMap initialBinding;
		
		SubscriptionWrapper(Query query, QuerySolutionMap initialBinding) {
			this.query = query;
			this.initialBinding = initialBinding;
		}

		public Query getQuery() {
			return query;
		}

		public QuerySolutionMap getInitialBinding() {
			return initialBinding;
		}
		
		@Override
        public int hashCode() {
	        return query.hashCode();
        }

		@Override
        public boolean equals(Object obj) {
	        if (this == obj) {
		        return true;
	        }
	        
	        if (obj == null) {
		        return false;
	        }
	        
	        if (!(obj instanceof SubscriptionWrapper)) {
		        return false;
	        }
	        
	        SubscriptionWrapper other = (SubscriptionWrapper) obj;
	        if (!query.equals(other.query)) {
		        return false;
	        }
	        
	        // check the equality of the initial bindings
	        // since the queries are equal, the variable names MUST be the same
	        Iterator<String> boundVarIt = initialBinding.varNames();
	        Iterator<String> otherBoundVarIt = other.initialBinding.varNames();
	        
	        Set<String> thisVars = new HashSet<String>(IteratorCollection.iteratorToSet(boundVarIt));
	        Set<String> otherVars = new HashSet<String>(IteratorCollection.iteratorToSet(otherBoundVarIt));
	        
	        if (thisVars.size() != otherVars.size())
	        	return false;
	        
	        return SetUtils.difference(thisVars, otherVars).isEmpty();
        }
	}
}
