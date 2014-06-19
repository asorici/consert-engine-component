package org.aimas.ami.contextrep.engine.api;


public interface QueryResultNotifier {
	public void notifyQueryResult(QueryResult result);
	
	public void notifyAskResult(QueryResult result);
}
