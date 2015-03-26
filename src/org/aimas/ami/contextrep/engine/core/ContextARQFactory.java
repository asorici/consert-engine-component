package org.aimas.ami.contextrep.engine.core;

import org.topbraid.spin.arq.ARQFactory;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;

public class ContextARQFactory extends ARQFactory {
	private Dataset contextDataset;
	private Engine consertEngine;
	
	/**
	 * Instantiate the ContextARQFactory with the CONSERT Engine instance from which it will retrieve the transactionable TDB-backed dataset. 
	 */
	public ContextARQFactory(Engine engine) {
		this.consertEngine = engine;
	}
	
	/**
	 * Instantiate the ContextARQFactory with an instance of the TDB-backed context dataset
	 * which <i>might</i> be in a transaction state.
	 * @param dataset
	 */
	public ContextARQFactory(Dataset dataset) {
		contextDataset = dataset;
	}
	
	/**
	 * Specifies the TDB-backed Dataset that shall be used for query execution.
	 * The default model is the union of all named graphs in the dataset.
	 * @param defaultModel  the default Model of the Dataset
	 * @return the Dataset
	 */
	@Override 
	public Dataset getDataset(Model defaultModel) {
		if (contextDataset != null) {
			return contextDataset;
		}
		
		return consertEngine.getRuntimeContextStore();
	}
}
