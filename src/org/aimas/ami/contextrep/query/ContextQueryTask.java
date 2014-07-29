package org.aimas.ami.contextrep.query;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.aimas.ami.contextrep.engine.api.ContextResultSet;
import org.aimas.ami.contextrep.engine.api.QueryException;
import org.aimas.ami.contextrep.engine.api.QueryResult;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.utils.ContextQueryUtil;
import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import com.hp.hpl.jena.sparql.engine.binding.BindingFactory;

public class ContextQueryTask implements Runnable {
	private Set<ContextAssertion> queriedAssertions;
	
	private Query query;
	private QuerySolutionMap initialBindings;
	private QueryResultNotifier resultNotifier;
	
	
	public ContextQueryTask(Query query, QuerySolutionMap initialBindings, QueryResultNotifier resultNotifier) {
		this.query = query;
		this.resultNotifier = resultNotifier;
		this.initialBindings = initialBindings;
		
		queriedAssertions = ContextQueryUtil.analyzeContextQuery(query, initialBindings, Engine.getModelLoader().getCoreContextModel());
	}
	
	
	public Set<ContextAssertion> getQueriedAssertions() {
		return queriedAssertions;
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
				    
				    // notify query statistics collector
				    Engine.getQueryService().markQueryExecution(queriedAssertions, results.hasNext());
				    
				    // consume the ResultSet and build a ContextResultSet to detach all bindings from
				    // the TDB binding, such that the read transaction can close properly
				    ContextResultSet contextResults = createContextResultSet(results);
				    
				    // notify result handler
				    resultNotifier.notifyQueryResult(new QueryResult(query, null, contextResults));
				}
				catch (Exception ex) {
					Engine.getQueryService().markQueryExecution(queriedAssertions, false);
					resultNotifier.notifyQueryResult(new QueryResult(query, new QueryException("Query execution error.", ex)));
				}
				finally { qexec.close() ; }
			}
			else if (query.isAskType()) {
				try {
				    boolean askResult = qexec.execAsk();
				    
				    // notify query statistics collector
				    Engine.getQueryService().markQueryExecution(queriedAssertions, askResult);
				    
				    // notify result handler
				    resultNotifier.notifyQueryResult(new QueryResult(query, null, askResult));
				}
				catch (Exception ex) {
					Engine.getQueryService().markQueryExecution(queriedAssertions, false);
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


	private ContextResultSet createContextResultSet(ResultSet results) {
		List<String> resultVars = results.getResultVars();
		final List<Binding> bindings = new ArrayList<Binding>();
		
		while (results.hasNext()) {
			Binding binding = results.nextBinding();
			bindings.add(detachBinding(binding));
		}
		
		return new ContextResultSet(resultVars, bindings);
    }


	private Binding detachBinding(Binding binding) {
		Iterator<Var> varsIt = binding.vars();
		Binding initial = BindingFactory.binding();
		
		while (varsIt.hasNext()) {
			Var var = varsIt.next();
			initial = BindingFactory.binding(initial, var, binding.get(var));
		}
		
		return initial;
    }
}
