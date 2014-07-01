package org.aimas.ami.contextrep.update;

import org.aimas.ami.contextrep.engine.Engine;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateRequest;

public class ContextBulkUpdateTask implements Runnable {
	private UpdateRequest bulkInsertRequest;
	
	public ContextBulkUpdateTask(UpdateRequest request) {
		this.bulkInsertRequest = request;
	}
	
	@Override
	public void run() {
		// STEP 1: start a new WRITE transaction on the contextStoreDataset
		Dataset contextDataset = Engine.getRuntimeContextStore();
		contextDataset.begin(ReadWrite.WRITE);
		
		try {
			// STEP 2: execute bulk update
			GraphStore graphStore = GraphStoreFactory.create(contextDataset);
			UpdateAction.execute(bulkInsertRequest, graphStore);
			
			// STEP 3: commit the update
			contextDataset.commit();
		}
		finally {
			contextDataset.end();
		}
	}
	
}
