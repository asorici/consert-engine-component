package org.aimas.ami.contextrep.engine.api;

import java.io.Serializable;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.Syntax;

public class QueryResult implements Serializable {
    private static final long serialVersionUID = 6450463508353276290L;
    
	private String queryString;
	private QueryException error;
	
	private transient ContextResultSet queryResult;
	private boolean askResult;
	
	private boolean isAsk = false;
	
	public QueryResult(String queryString, boolean isAsk, QueryException error, ContextResultSet queryResult, boolean askResult) {
		this.queryString = queryString;
		this.isAsk = isAsk;
		this.error = error;
		this.queryResult = queryResult;
		this.askResult = askResult;
	}
	
	public String getQuery() {
		return queryString;
	}
	
	public QueryResult(Query query, QueryException error) {
		this(query.toString(Syntax.syntaxSPARQL_11), query.isAskType(), error, null, false);
	}
	
	public QueryResult(Query query, QueryException error, boolean askResult) {
		this(query.toString(Syntax.syntaxSPARQL_11), query.isAskType(), error, null, askResult);
	}
	
	public QueryResult(Query query, QueryException error, ContextResultSet queryResult) {
		this(query.toString(Syntax.syntaxSPARQL_11), query.isAskType(), error, queryResult, false);
	}
	
	public ContextResultSet getResultSet() {
		return queryResult;
	}
	
	public boolean getAskResult() {
		return askResult;
	}
	
	public boolean isAsk() {
		return isAsk;
	}
	
	public boolean isSelect() {
		return !isAsk;
	}
	
	public QueryException getError() {
		return error;
	}
	
	public boolean hasError() {
		return error != null;
	}
	
	public void cumulateAsk(boolean askResult) {
		if (isAsk && error != null) {
			this.askResult |= askResult;
		}
	}
	
	public void cumulateResultSet(ContextResultSet rs) {
		if (!isAsk && error != null) {
			if (queryResult == null) {
				queryResult = rs;
			}
			else {
				queryResult.accumulate(rs);
			}
		}
	}
	
	// SERIALIZATION / DESERIALIZATION
	////////////////////////////////////////////////////////////////////////////////////////
	
	/*
	private void writeObject(ObjectOutputStream oos) throws IOException {
		// default serialization 
		System.out.println("["+getClass().getName()+"] PERFORMING SERIALIZATION");
		oos.defaultWriteObject();
		
		
		// write the result set as XML
		if (queryResult != null) {
			queryResult.reset();
			String xmlResultSet = ResultSetFormatter.asXMLString(queryResult);
			oos.writeObject(xmlResultSet);
		}
		else {
			oos.writeObject("none");
		}
		
	}
	
	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		System.out.println("["+getClass().getName()+"] PERFORMING DESERIALIZATION OF QUERY RESULTS");
		
		// default deserialization 
		ois.defaultReadObject();
		System.out.println("["+getClass().getName()+"] READ ALL NON-TRANSIENT MEMBERS");
		
		
		// read the xml result set and recreate the ContextResultSet
		String xmlResultSet = (String)ois.readObject();
		System.out.println("["+getClass().getName()+"] Deserialized result set as xml: " + xmlResultSet);
		
		if (!xmlResultSet.equals("none")) {
			ResultSet results = ResultSetFactory.fromXML(xmlResultSet);
			List<String> resultVars = results.getResultVars();
			final List<Binding> bindings = new ArrayList<Binding>();
			
			while (results.hasNext()) {
				Binding binding = results.nextBinding();
				bindings.add(detachBinding(binding));
			}
			
			setResultSet(new ContextResultSet(resultVars, bindings));
		}
		
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
    */
}
