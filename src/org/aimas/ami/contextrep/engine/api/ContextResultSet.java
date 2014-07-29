package org.aimas.ami.contextrep.engine.api;

import java.util.Iterator;
import java.util.List;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSetRewindable;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sparql.core.ResultBinding;
import com.hp.hpl.jena.sparql.engine.binding.Binding;

public class ContextResultSet implements ResultSetRewindable {
	
	private List<String> resultVars;
	private int rowNumber;
	private Model model;
	private final List<Binding> bindings;
	private Iterator<Binding> iter;
	
	
	public ContextResultSet(List<String> resultVars, List<Binding> bindings) {
	    this.resultVars = resultVars;
	    this.model = null;
	    this.bindings = bindings;
	    
	    reset();
    }

	/**
	 * Returns the Model corresponding to a combination of the <i>core</i> and <i>annotation</i>
	 * Context Model modules.
	 * 
	 * <b>Note</b> that a ContextResultSet ships intentionally with a <code>null</code> default since 
	 * the results might have to be transported over a network as part of the response of a 
	 * CtxQueryHandler to a CtxUser agent. The latter agent can later attach a model by calling the 
	 * {@link attachContextModel} function with a which it will have initialized at startup. 
	 */
	@Override
	public Model getResourceModel() {
		return model;
	}
	
	/**
	 * Function to be called locally by a CtxUser on a received ContextResultSet to attach
	 * a combination of the <i>core</i> and <i>annotation</i> modules of the Context Model.
	 * This will allow easier manipulation of the context result set.
	 * @param model
	 */
	public void attachContextModel(Model model) {
		this.model = model;
	}
	
	/**
	* Get the variable names for the projection
	*/
	@Override
	public List<String> getResultVars() {
		return resultVars;
	}
	
	/**
	 * Return the "row number" - a count of the number of possibilities 
	 * returned so far. Remains valid (as the total number of possibilities) after the
	 * iterator ends.
	 */
	@Override
	public int getRowNumber() {
		return rowNumber;
	}
	
	@Override
	public boolean hasNext() {
		return iter.hasNext();
	}
	
	/** Moves onto the next result possibility. */
	@Override
	public QuerySolution next() {
		return nextSolution();
	}
	
	@Override
	public Binding nextBinding() {
		Binding binding = iter.next();
		
		if (binding != null) {
			rowNumber++;
		}

		return binding;
	}
	
	/**
	 * Moves onto the next result possibility. The returned object is actual the
	 * binding for this result.
	 */
	@Override
	public QuerySolution nextSolution() {
		return new ResultBinding(model, nextBinding());
	}
	
	/**
	 * @throws UnsupportedOperationException Always thrown
	 */
	@Override
	public void remove() throws UnsupportedOperationException {
		throw new UnsupportedOperationException(this.getClass().getName() + ".remove");
	}
	
	@Override
	public void reset() {
		iter = bindings.iterator();
		rowNumber = 0;
	}
	
	@Override
	public int size() {
		return bindings.size();
	}
}
