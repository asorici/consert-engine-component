package org.aimas.ami.contextrep.engine.core;

import org.topbraid.spin.arq.ARQFactory;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;

public class ContextARQFactory extends ARQFactory {
	private Dataset contextDataset;
	
	/**
	 * Instantiate the ContextARQFactory. 
	 * A new transactionable instance of the TDB-backed context dataset will be retrieved
	 * from the core.Config class.
	 */
	public ContextARQFactory() {}
	
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
		
		return Engine.getRuntimeContextStore();
	}
}
