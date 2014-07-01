package org.aimas.ami.contextrep.engine;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.aimas.ami.contextrep.engine.api.CommandException;
import org.aimas.ami.contextrep.engine.api.CommandHandler;
import org.aimas.ami.contextrep.engine.api.ConfigException;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.QueryHandler;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.api.StatsHandler;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.query.ContextQueryTask;
import org.aimas.ami.contextrep.update.ContextBulkUpdateTask;
import org.aimas.ami.contextrep.update.ContextUpdateTask;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.Bundle;
import org.osgi.service.cm.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.reasoner.ValidityReport;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateRequest;


@Component(
	name = "consert-engine",
	immediate = true
)
@Service
public class EngineFrontend implements InsertionHandler, QueryHandler, CommandHandler, StatsHandler {
	private Logger log = LoggerFactory.getLogger(getClass());
	private Bundle modelResourceBundle;
	
	public EngineFrontend() {}
	
	// ==================== INITIALIZATION AND TAKE-DOWN HANDLING ==================== //
	@SuppressWarnings("unused")
    private void setModelResourceBundle(Bundle modelResourceBundle) {
		this.modelResourceBundle = modelResourceBundle;
		Enumeration<String> resources = modelResourceBundle.getEntryPaths("");
		
		System.out.println("Bundle resources");
		for (;resources.hasMoreElements();) {
			System.out.println(resources.nextElement());
		}
		
		System.out.println("Bundle classpath");
		URL[] urls = ((URLClassLoader)modelResourceBundle.getClass().getClassLoader()).getURLs();
		 
        for(URL url: urls){
        	System.out.println(url.getFile());
        }
    }
	
	
	// We first wait for the context-domain specific configuration above and now try
	// initialization. We try to read the CONSERT Engine specific configuration file and process it. 
	void initEngine(org.apache.felix.dm.Component component) throws ConfigurationException {
		if (modelResourceBundle != null) {
			try {
	            // initialize the EngineResourceManager
				EngineResourceManager resourceManager = new BundleResourceManager(modelResourceBundle);
				Engine.setResourceManager(resourceManager);
	            
	            // initialize the engine
	            Engine.init(true);
            }
            catch (ConfigException e) {
				e.printStackTrace();
				e.getCause().printStackTrace();
				throw new ConfigurationException(null, "CONSERT Engine configuration invalid.", e);
			}
			catch (Exception e) {
				e.printStackTrace();
				throw new ConfigurationException(null, "Unknown engine initialization error.", e);
			}
		}
		
	}
	
	
	// Call this when the bundle containing the CONSERT Engine is destroyed
	void closeEngine(org.apache.felix.dm.Component component) {
		try {
			Engine.close(false);
		}
		catch (Exception e) {
			log.error("This should not have happened to a dog!", e);
		}
	} 
	
	
	void startEngine(org.apache.felix.dm.Component component) {
		
	}
	
	void stopEngine(org.apache.felix.dm.Component component) {
		
	}
	
	
	// =============================== INSERT HANDLING =============================== //
	
	@Override
	public Future<InsertResult> insert(UpdateRequest insertionRequest) {
		return Engine.assertionInsertExecutor().submit(new ContextUpdateTask(insertionRequest));
	}
	
	
	@Override
	public Future<InsertResult> insert(Update assertionIdentifier, Update assertionContents, Update assertionAnnotations) {
		UpdateRequest insertionRequest = UpdateFactory.create() ;
		insertionRequest.add(assertionIdentifier);
		insertionRequest.add(assertionContents);
		insertionRequest.add(assertionAnnotations);
		
		return Engine.assertionInsertExecutor().submit(new ContextUpdateTask(insertionRequest));
	}
	
	@Override
	public Future<?> bulkInsert(UpdateRequest bulkInsertionRequest) {
		return Engine.assertionInsertExecutor().submit(new ContextBulkUpdateTask(bulkInsertionRequest));
	}
	
	
	// =============================== QUERY HANDLING =============================== //
	
	@Override
	public void execQuery(Query query, QuerySolutionMap initialBindings, QueryResultNotifier notifier) {
		Engine.assertionQueryExecutor().submit(new ContextQueryTask(query, initialBindings, notifier));
	}
	
	@Override
	public void execAsk(Query askQuery, QuerySolutionMap initialBindings, QueryResultNotifier notifier) {
		Engine.assertionQueryExecutor().submit(new ContextQueryTask(askQuery, initialBindings, notifier));
	}
	
	@Override
	public void subscribe(Query subscribeQuery, QuerySolutionMap initialBindings, QueryResultNotifier notifier) {
		Engine.subscriptionMonitor().newSubscription(subscribeQuery, initialBindings, notifier);
	}
	
	
	// =============================== COMMAND HANDLING =============================== //

	@Override
	public ValidityReport triggerOntReasoning(ContextAssertion assertion) throws CommandException {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public ValidityReport triggerOntReasoning(OntResource assertionResource) throws CommandException {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void cleanRuntimeContextStore(ContextAssertion assertion, Calendar timeThreshold) throws CommandException {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void cleanRuntimeContextStore(OntResource assertionResource,
	        Calendar timeThreshold) throws CommandException {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void cleanRuntimeContextStore(Calendar timeThreshold) throws CommandException {
		// TODO Auto-generated method stub
		
	}
	
	
	// =============================== STATS HANDLING =============================== // 
	
	@Override
	public int updatesPerTimeUnit(ContextAssertion assertion, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int updatesPerTimeUnit(OntResource assertionResource, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int timeSinceLastUpdate(ContextAssertion assertion, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int timeSinceLastUpdate(OntResource assertionResource, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int requestsPerTimeUnit(ContextAssertion assertion, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int requestsPerTimeUnit(OntResource assertionResource, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int subscriptionCount(ContextAssertion assertion) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int subscriptionCount(OntResource assertionResource) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int timeSinceLastQuery(ContextAssertion assertion, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int timeSinceLastQuery(OntResource assertionResource, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int timeSinceReasoning(ContextAssertion assertion, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int timeSinceReasoning(OntResource assertionResource, TimeUnit measureUnit) {
		// TODO Auto-generated method stub
		return 0;
	}
}
