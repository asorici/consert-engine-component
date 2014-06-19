package org.aimas.ami.contextrep.engine.api;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.ResultSet;

public class QueryResult {
	private Query query;
	private QueryException error;
	
	private ResultSet queryResult;
	private boolean askResult;
	
	public QueryResult(Query query, QueryException error, ResultSet queryResult, boolean askResult) {
		this.query = query;
		this.error = error;
		this.queryResult = queryResult;
		this.askResult = askResult;
	}
	
	public Query getQuery() {
		return query;
	}
	
	public QueryResult(Query query, QueryException error) {
		this(query, error, null, false);
	}
	
	public QueryResult(Query query, QueryException error, boolean askResult) {
		this(query, error, null, askResult);
	}
	
	public QueryResult(Query query, QueryException error, ResultSet queryResult) {
		this(query, error, queryResult, false);
	}
	
	public ResultSet getResultSet() {
		return queryResult;
	}
	
	public boolean getAskResult() {
		return askResult;
	}
	
	public boolean isAsk() {
		return query.isAskType();
	}
	
	public boolean isSelect() {
		return query.isSelectType();
	}
	
	public QueryException getError() {
		return error;
	}
	
	public boolean hasError() {
		return error != null;
	}
}
