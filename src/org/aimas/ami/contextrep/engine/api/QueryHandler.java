package org.aimas.ami.contextrep.engine.api;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;

public interface QueryHandler {
	
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
