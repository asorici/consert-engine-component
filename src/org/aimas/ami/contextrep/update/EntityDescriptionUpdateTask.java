package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateRequest;

public class EntityDescriptionUpdateTask implements Runnable {
	
	private Engine consertEngine;
	private UpdateRequest entityDescriptionUpdateRequest;
	
	public EntityDescriptionUpdateTask(Engine consertEngine, UpdateRequest request) {
		this.consertEngine = consertEngine;
		this.entityDescriptionUpdateRequest = request;
	}
	
	@Override
	public void run() {
		// STEP 1: start a new WRITE transaction on the contextStoreDataset
		Dataset contextDataset = consertEngine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.WRITE);
		
		try {
			// STEP 2: execute bulk update
			GraphStore graphStore = GraphStoreFactory.create(contextDataset);
			UpdateAction.execute(entityDescriptionUpdateRequest, graphStore);
			
			// TODO: see if there's a better / more elegant way to do this
			Model entityStore = contextDataset.getNamedModel(ConsertCore.ENTITY_STORE_URI);
			InfModel entityStoreInfModel = ModelFactory.createInfModel(consertEngine.getEntityStoreReasoner(), entityStore);
				
			Model newData = entityStoreInfModel.difference(entityStore);
			entityStore.add(newData);
			
			// STEP 3: commit the update
			contextDataset.commit();
		}
		finally {
			contextDataset.end();
		}
	}
	
}
