package org.aimas.ami.contextrep.engine.api;

import java.util.Set;

import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;

public interface QueryHandler {
	/**
	 * Analyze the given <code>query</code> to retrieve the set of ContextAssertions from the ContextModel 
	 * managed by this CONSERT Engine instance which compose the query body.
	 * @param query
	 * @param initialBindings
	 * @return The set of ContextAssertions composing the query body.
	 */
	public Set<ContextAssertion> analyzeQuery(Query query, QuerySolutionMap initialBindings);
	
	/**
	 * Register a listener (implemented by the QueryManager of a CtxQueryHandler agent) for the 
	 * updates made to active ContextAssertions upon successful insertion.
	 * @param updateListener
	 */
	public void registerAssertionUpdateListener(AssertionUpdateListener updateListener);
	
	/**
	 * Unregister a listener (implemented by the QueryManager of a CtxQueryHandler agent) for the 
	 * updates made to active ContextAssertions upon successful insertion.
	 * @param updateListener
	 */
	public void unregisterAssertionUpdateListener(AssertionUpdateListener updateListener);
	
	/**
	 * Execute a one time query with initial bindings.
	 * @param query The Jena query object.
	 * @param initialBindings A map of {variable: value} pairs for initial binding. May be null.
	 * @param notifier The notifier callback used to provide the query results.
	 * @throws QueryException
	 */
	public void execQuery(Query query, QuerySolutionMap initialBindings, QueryResultNotifier notifier);
	
	
	/**
	 * Execute a one time ask query. 
	 * @param askString The Jena ask query object.
	 * @param initialBindings A map of {variable: value} pairs for initial binding. May be null. 
	 * @param notifier The notifier callback used to provide the ask result.
	 * @throws QueryException
	 */
	public void execAsk(Query askQuery, QuerySolutionMap initialBindings, QueryResultNotifier notifier);
	
	
	/**
	 * Subscribe for results to the given query object. 
	 * @param subscribeQuery The Jena subscription query object.
	 * @param initialBindings A map of {variable: value} pairs for initial binding. May be null.
	 * @param notifier A notifier callback used to announce subscription results.
	 * @throws QueryException
	 */
	public void subscribe(Query subscribeQuery, QuerySolutionMap initialBindings, QueryResultNotifier notifier);
}
