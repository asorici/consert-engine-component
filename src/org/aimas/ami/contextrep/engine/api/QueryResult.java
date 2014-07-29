package org.aimas.ami.contextrep.engine.api;

import java.io.Serializable;

import com.hp.hpl.jena.query.Query;

public class QueryResult implements Serializable {
    private static final long serialVersionUID = 6450463508353276290L;
    
	private Query query;
	private QueryException error;
	
	private ContextResultSet queryResult;
	private boolean askResult;
	
	public QueryResult(Query query, QueryException error, ContextResultSet queryResult, boolean askResult) {
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
	
	public QueryResult(Query query, QueryException error, ContextResultSet queryResult) {
		this(query, error, queryResult, false);
	}
	
	public ContextResultSet getResultSet() {
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
