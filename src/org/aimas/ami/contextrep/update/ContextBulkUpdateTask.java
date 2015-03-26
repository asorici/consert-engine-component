package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.utils.ContextStoreUtil;
import org.aimas.ami.contextrep.engine.utils.ContextUpdateUtil;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateRequest;

public class ContextBulkUpdateTask implements Runnable {
	
	private Engine consertEngine;
	private UpdateRequest bulkInsertRequest;
	
	public ContextBulkUpdateTask(Engine consertEngine, UpdateRequest request) {
		this.consertEngine = consertEngine;
		this.bulkInsertRequest = request;
	}
	
	@Override
	public void run() {
		// STEP 1: start a new WRITE transaction on the contextStoreDataset
		Dataset contextDataset = consertEngine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.WRITE);
		
		try {
			// STEP 2: execute bulk update
			GraphStore graphStore = GraphStoreFactory.create(contextDataset);
			UpdateAction.execute(bulkInsertRequest, graphStore);
			
			// STEP 2bis: check if an update was made to the EntityStore and if so, apply OWL-Micro reasoning
			List<Node> updatedContextStores = new ArrayList<Node>(analyzeRequest(contextDataset, null));
			
			boolean entityStoreUpdate = false;
			for (Node graphNode : updatedContextStores) {
				if (ContextStoreUtil.isEntityStore(graphNode)) {
					entityStoreUpdate = true;
					break;
				}
			}
			
			if (entityStoreUpdate) {
				// TODO: see if there's a better / more elegant way to do this
				Model entityStore = contextDataset.getNamedModel(ConsertCore.ENTITY_STORE_URI);
				InfModel entityStoreInfModel = ModelFactory.createInfModel(consertEngine.getEntityStoreReasoner(), entityStore);
				
				Model newData = entityStoreInfModel.difference(entityStore);
				entityStore.add(newData);
			}
			
			// STEP 3: commit the update
			contextDataset.commit();
		}
		finally {
			contextDataset.end();
		}
	}
	
	private Collection<Node> analyzeRequest(Dataset dataset, Map<String, RDFNode> templateBindings) {
		Collection<Node> updatedContextStores = null;
		
		for (Update up : bulkInsertRequest.getOperations()) {
			if (updatedContextStores == null) {
				updatedContextStores = ContextUpdateUtil.getUpdatedGraphs(consertEngine, up, dataset, templateBindings, false);
			}
			else {
				updatedContextStores.addAll(ContextUpdateUtil.getUpdatedGraphs(consertEngine, up, dataset, templateBindings, false));
			}
		}
		
		return updatedContextStores;
	}
}
