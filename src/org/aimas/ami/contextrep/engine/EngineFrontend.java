package org.aimas.ami.contextrep.engine;

import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.aimas.ami.contextrep.engine.api.CommandException;
import org.aimas.ami.contextrep.engine.api.CommandHandler;
import org.aimas.ami.contextrep.engine.api.ConfigException;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.InsertionResultNotifier;
import org.aimas.ami.contextrep.engine.api.QueryHandler;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.api.StatsHandler;
import org.aimas.ami.contextrep.engine.core.BundleResourceManager;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.core.EngineResourceManager;
import org.aimas.ami.contextrep.engine.execution.FCFSPriorityProvider;
import org.aimas.ami.contextrep.model.ContextAssertion;
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
import com.hp.hpl.jena.update.UpdateRequest;


@Component(
	name = "consert-engine",
	immediate = true
)
@Service
public class EngineFrontend implements InsertionHandler, QueryHandler, CommandHandler, StatsHandler {
	private Logger log = LoggerFactory.getLogger(getClass());
	
	/**
	 * The Bundle that contains the configuration and context model ontology files 
	 * that this CONSERT Engine component instance uses.
	 */
	private Bundle modelResourceBundle;
	
	/**
	 * The comparator provider that is essential for the inference request scheduling
	 * service of the CONSERT Engine.
	 */
	private InferencePriorityProvider inferenceComparatorProvider;
	
	public EngineFrontend() {}
	
	// ==================== INITIALIZATION AND TAKE-DOWN HANDLING ==================== //
	@SuppressWarnings("unused")
    private void setModelResourceBundle(Bundle modelResourceBundle) {
		this.modelResourceBundle = modelResourceBundle;
    }
	
	@SuppressWarnings("unused")
	private void addedInferenceRequestComparator(InferencePriorityProvider provider) {
		inferenceComparatorProvider = provider;
		// TODO: forward to Engine Inference Service
	}
	
	@SuppressWarnings("unused")
	private void removedInferenceRequestComparator(InferencePriorityProvider provider) {
		if (provider == inferenceComparatorProvider) {
			// if the comparator we were currently using was removed, revert to the default one
			inferenceComparatorProvider = FCFSPriorityProvider.getInstance();
			
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
			
			// The Engine is initialized but its execution services are not yet started.
			// More specifically, we first need to see if any configuration was placed on the type
			// of service to use for the Inference Scheduling. If there is no specification, or none can 
			// be found when invoking the search, the default FCFS service is used
			Properties engineConfiguration = Engine.getConfiguration();
			String schedulerType = engineConfiguration.getProperty(ConfigKeys.CONSERT_ENGINE_INFERENCE_SCHEDULER_TYPE);
			if (schedulerType != null) {
				component.add(
					component.getDependencyManager().
					createServiceDependency()
						.setService(InferencePriorityProvider.class, "(type=" + schedulerType + ")")
						.setDefaultImplementation(FCFSPriorityProvider.getInstance())
						.setCallbacks("addedInferenceRequestComparator", "removedInferenceRequestComparator")
				);
			}
		}
		else {
			throw new ConfigurationException(null, "Model Resource Bundle is missing!");
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
	
	// Call this when the CONSERT Engine component is started
	void startEngine(org.apache.felix.dm.Component component) {
		// start all the execution services
		Engine.getInsertionService().start();
		Engine.getInferenceService().start();
		Engine.getQueryService().start();
	}
	
	// Call this when the CONSERT Engine component is stopped
	void stopEngine(org.apache.felix.dm.Component component) {
		// stop all the execution services
		Engine.getInsertionService().stop();
		Engine.getInferenceService().stop();
		Engine.getQueryService().stop();
	}
	
	
	// =============================== INSERT HANDLING =============================== //
	
	@Override
	public void insert(UpdateRequest insertionRequest, InsertionResultNotifier notifier) {
		Engine.getInsertionService().executeRequest(insertionRequest, notifier);
	}
	
	@Override
	public Future<InsertResult> insert(UpdateRequest insertionRequest) {
		return Engine.getInsertionService().executeRequest(insertionRequest, null);
	}
	
	@Override
	public Future<?> bulkInsert(UpdateRequest bulkInsertionRequest) {
		return Engine.getInsertionService().executeBulkRequest(bulkInsertionRequest);
	}
	
	
	// =============================== QUERY HANDLING =============================== //
	
	@Override
	public void execQuery(Query query, QuerySolutionMap initialBindings, QueryResultNotifier notifier) {
		Engine.getQueryService().executeRequest(query, initialBindings, notifier);
	}
	
	@Override
	public void execAsk(Query askQuery, QuerySolutionMap initialBindings, QueryResultNotifier notifier) {
		Engine.getQueryService().executeRequest(askQuery, initialBindings, notifier);
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
