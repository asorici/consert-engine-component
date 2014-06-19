package org.aimas.ami.contextrep.query;

import org.aimas.ami.contextrep.engine.Engine;
import org.aimas.ami.contextrep.engine.api.QueryException;
import org.aimas.ami.contextrep.engine.api.QueryResult;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;

public class ContextQueryTask implements Runnable {
	private Query query;
	private QuerySolutionMap initialBindings;
	private QueryResultNotifier resultNotifier;
	
	
	public ContextQueryTask(Query query, QuerySolutionMap initialBindings, QueryResultNotifier resultNotifier) {
		this.query = query;
		this.resultNotifier = resultNotifier;
		this.initialBindings = initialBindings;
	}

	@Override
    public void run() {
		// STEP 1: start a new READ transaction on the contextStoreDataset
		Dataset contextDataset = Engine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.READ);
		
		try {
			QueryExecution qexec = QueryExecutionFactory.create(query, contextDataset, initialBindings);
			
			// STEP 2: check the type of the query and execute it
			if (query.isSelectType()) {
				try {
				    ResultSet results = qexec.execSelect();
				    resultNotifier.notifyQueryResult(new QueryResult(query, null, results));
				}
				catch (Exception ex) {
					resultNotifier.notifyQueryResult(new QueryResult(query, new QueryException("Query execution error.", ex)));
				}
				finally { qexec.close() ; }
			}
			else if (query.isAskType()) {
				try {
				    boolean askResult = qexec.execAsk();
				    resultNotifier.notifyQueryResult(new QueryResult(query, null, askResult));
				}
				catch (Exception ex) {
					resultNotifier.notifyQueryResult(new QueryResult(query, new QueryException("Query execution error.", ex)));
				}
				finally { qexec.close() ; }
			}
			else {
				resultNotifier.notifyQueryResult(new QueryResult(query, new QueryException("The submitted query is neither a SELECT, nor an ASK.")));
			}
		}
		finally {
			// STEP 3: end the READ transaction
			contextDataset.end();
		}
    }
}
