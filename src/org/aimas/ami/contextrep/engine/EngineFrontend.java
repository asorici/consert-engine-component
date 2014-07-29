package org.aimas.ami.contextrep.engine;

import java.util.Calendar;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;

import org.aimas.ami.contextrep.engine.api.AssertionUpdateListener;
import org.aimas.ami.contextrep.engine.api.CommandException;
import org.aimas.ami.contextrep.engine.api.CommandHandler;
import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;
import org.aimas.ami.contextrep.engine.api.EngineConfigException;
import org.aimas.ami.contextrep.engine.api.EngineInferenceStats;
import org.aimas.ami.contextrep.engine.api.EngineQueryStats;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.InsertionResultNotifier;
import org.aimas.ami.contextrep.engine.api.QueryHandler;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.api.StatsHandler;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.execution.ContextInsertNotifier;
import org.aimas.ami.contextrep.engine.execution.FCFSPriorityProvider;
import org.aimas.ami.contextrep.engine.utils.ContextQueryUtil;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextAssertion.ContextAssertionType;
import org.aimas.ami.contextrep.utils.BundleResourceManager;
import org.aimas.ami.contextrep.utils.ResourceManager;
import org.apache.felix.dm.Dependency;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.Bundle;
import org.osgi.service.cm.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.rdf.model.Resource;
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
	 * Keeps a reference to the Component view of the CONSERT Engine frontend.
	 */
	private org.apache.felix.dm.Component engineComponent;
	
	/**
	 * The Bundle that contains the configuration and context model ontology files 
	 * that this CONSERT Engine component instance uses.
	 */
	private Bundle modelResourceBundle;
	
	/**
	 * The priority provider that is essential for the inference request scheduling
	 * service of the CONSERT Engine.
	 */
	private InferencePriorityProvider inferencePriorityProvider;
	
	/**
	 * A reference to the priority provider service dependency. We keep this in order to be able
	 * to replace it (specifically, the service filter part) at runtime, following a TASKING COMMAND
	 * received from the CtxCoord agent.
	 */
	private Dependency inferencePriorityProviderDependency;
	
	
	public EngineFrontend() {}
	
	// ==================== INITIALIZATION AND TAKE-DOWN HANDLING =================== //
	////////////////////////////////////////////////////////////////////////////////////
	@SuppressWarnings("unused")
    private void setModelResourceBundle(Bundle modelResourceBundle) {
		this.modelResourceBundle = modelResourceBundle;
    }
	
	@SuppressWarnings("unused")
	private void addedInferencePriorityProvider(InferencePriorityProvider provider) {
		inferencePriorityProvider = provider;
		Engine.getInferenceService().setPriorityProvider(provider, false);
	}
	
	@SuppressWarnings("unused")
	private void removedInferencePriorityProvider(InferencePriorityProvider provider) {
		if (provider == inferencePriorityProvider) {
			// if the priority provider we were currently using was removed, revert to the default one
			inferencePriorityProvider = FCFSPriorityProvider.getInstance();
			Engine.getInferenceService().setPriorityProvider(inferencePriorityProvider, false);
		}
		
		/* There will be a case when this method is called when we switch out services 
		 * from one type to another. However, in those cases the addedInferencePriorityProvider
		 * function will have already been called with the new interface, such that when we call this
		 * with the old provider, we don't have to do anything.
		 */
	}
	
	
	// We first wait for the context-domain specific configuration above and now try
	// initialization. We try to read the CONSERT Engine specific configuration file and process it. 
	void initEngine(org.apache.felix.dm.Component component) throws ConfigurationException {
		// keep reference to our component view
		this.engineComponent = component;
		
		if (modelResourceBundle != null) {
			try {
	            // initialize the EngineResourceManager
				ResourceManager resourceManager = new BundleResourceManager(modelResourceBundle);
				Engine.setResourceManager(resourceManager);
	            
	            // initialize the engine
	            Engine.init(true);
            }
            catch (EngineConfigException e) {
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
			
			// save reference to the inference priority provider service
			inferencePriorityProviderDependency = component.getDependencyManager().
					createServiceDependency()
					.setService(InferencePriorityProvider.class, "(type=" + schedulerType + ")")
					.setDefaultImplementation(FCFSPriorityProvider.getInstance())
					.setCallbacks("addedInferencePriorityProvider", "removedInferencePriorityProvider");
			
			if (schedulerType != null) {
				component.add(inferencePriorityProviderDependency);
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
	
	
	// =============================== INSERT HANDLING ============================== //
	////////////////////////////////////////////////////////////////////////////////////
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
	////////////////////////////////////////////////////////////////////////////////////
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
	
	@Override
	public Set<ContextAssertion> analyzeQuery(Query query, QuerySolutionMap initialBindings) {
		OntModel coreContextModel = Engine.getModelLoader().getCoreContextModel();
		
		return ContextQueryUtil.analyzeContextQuery(query, initialBindings, coreContextModel);
	}
	
	@Override
	public void registerAssertionUpdateListener(AssertionUpdateListener updateListener) {
		ContextInsertNotifier.getInstance().addUpdateListener(updateListener);
	}
	
	@Override
	public void unregisterAssertionUpdateListener(AssertionUpdateListener updateListener) {
		ContextInsertNotifier.getInstance().removeUpdateListener(updateListener);
	}
	
	// =============================== COMMAND HANDLING =============================== //
	//////////////////////////////////////////////////////////////////////////////////////
	
	@Override
    public Dataset getRuntimeContextStore() {
	    return Engine.getRuntimeContextStore();
    }
	
	
	@Override
    public void setDefaultQueryRunWindow(long runWindow) {
	    Engine.getQueryService().setDefaultRunWindow(runWindow);
    }

	@Override
    public void setSpecificQueryRunWindow(Resource assertionResource, long runWindow) {
	    Engine.getQueryService().setSpecificRunWindow(assertionResource, runWindow);
    }

	@Override
    public void setDefaultInferenceRunWindow(long runWindow) {
		Engine.getInferenceService().setDefaultRunWindow(runWindow);
    }

	@Override
    public void setSpecificInferenceRunWindow(Resource assertionResource, long runWindow) {
	    Engine.getInferenceService().setSpecificRunWindow(assertionResource, runWindow);
    }
	
	@Override
    public void setInferenceSchedulingType(String priorityProviderType) {
	    Dependency oldDependency = inferencePriorityProviderDependency;
	    inferencePriorityProviderDependency = 
	    	engineComponent.getDependencyManager().createServiceDependency()
				.setService(InferencePriorityProvider.class, "(type=" + priorityProviderType + ")")
				.setDefaultImplementation(FCFSPriorityProvider.getInstance())
				.setCallbacks("addedInferencePriorityProvider", "removedInferencePriorityProvider");
	    
	    // add the new dependency
	    engineComponent.add(inferencePriorityProviderDependency);
	    
	    // remove the old one
	    engineComponent.remove(oldDependency);
	}
	
	@Override
	public void setAssertionInsertActiveByDefault(boolean activeByDefault) {
		Engine.getContextAssertionIndex().setActiveByDefault(activeByDefault);
	}
	
	@Override
	public void setAssertionInferenceActiveByDefault(boolean activeByDefault) {
		Engine.getDerivationRuleDictionary().setActiveByDefault(activeByDefault);
	}
	
	@Override
	public void setAssertionActive(Resource assertionResource, boolean active) {
		Engine.getContextAssertionIndex().setAssertionActive(assertionResource, active);
		
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		if (assertion.getAssertionType() == ContextAssertionType.Derived) {
			Engine.getDerivationRuleDictionary().setDerivedAssertionActive(assertion, active);
		}
	}
	
	@Override
    public void setDerivationRuleActive(Resource derivedAssertionResource, boolean active) {
		Engine.getContextAssertionIndex().setAssertionActive(derivedAssertionResource, active);
		
		ContextAssertion derivedAssertion = Engine.getContextAssertionIndex().getAssertionFromResource(derivedAssertionResource);
		Engine.getDerivationRuleDictionary().setDerivedAssertionActive(derivedAssertion, active);
    }
	
	
	@Override
	public ValidityReport triggerOntReasoning(Resource assertionResource) throws CommandException {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	@Override
	public void cleanRuntimeContextStore(Resource assertionResource, Calendar timeThreshold) throws CommandException {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void cleanRuntimeContextStore(Calendar timeThreshold) throws CommandException {
		// TODO Auto-generated method stub
		
	}

	// =============================== STATS HANDLING =============================== // 
	////////////////////////////////////////////////////////////////////////////////////
	@Override
    public long getDefaultQueryRunWindow() {
		return Engine.getQueryService().getDefaultRunWindow();
    }

	@Override
    public long getQueryRunWindow(Resource assertionResource) {
	    Long runWindow = Engine.getQueryService().getSpecificRunWindow(assertionResource);
	    if (runWindow == null) {
	    	return 0;
	    }
	    
	    return runWindow;
    }
	
	@Override
    public long getDefaultInferenceRunWindow() {
		return Engine.getInferenceService().getDefaultRunWindow();
    }

	@Override
    public long getInferenceRunWindow(Resource assertionResource) {
		Long runWindow = Engine.getInferenceService().getSpecificRunWindow(assertionResource);
	    if (runWindow == null) {
	    	return 0;
	    }
	    
	    return runWindow;
    }
	
	
	@Override
    public EngineInferenceStats getInferenceStatistics() {
	    return Engine.getInferenceService();
    }

	@Override
    public ContextDerivationRule getLastDerivation() {
	    return Engine.getInferenceService().lastDerivation();
    }

	@Override
    public int nrDerivations(Resource derivedAssertionResource) {
	    int ct = 0;
	    
	    Map<ContextDerivationRule, Integer> derivations = Engine.getInferenceService().nrDerivations(); 
	    for (ContextDerivationRule rule : derivations.keySet()) {
	    	if (derivedAssertionResource.equals(rule.getDerivedAssertion().getOntologyResource())) {
	    		ct += derivations.get(rule);
	    	}
	    }
		
	    return ct;
    }

	@Override
    public int nrSuccessfulDerivations(Resource derivedAssertionResource) {
		int ct = 0;
	    
	    Map<ContextDerivationRule, Integer> successDerivations = Engine.getInferenceService().nrSuccessfulDerivations(); 
	    for (ContextDerivationRule rule : successDerivations.keySet()) {
	    	if (derivedAssertionResource.equals(rule.getDerivedAssertion().getOntologyResource())) {
	    		ct += successDerivations.get(rule);
	    	}
	    }
		
	    return ct;
    }

	@Override
    public EngineQueryStats getQueryStatistics() {
		return Engine.getQueryService();
    }

	@Override
    public int nrQueries(Resource assertionResource) {
		Integer ct = Engine.getQueryService().nrQueries().get(assertionResource);
		return ct == null ? 0 : ct;
    }

	@Override
    public int nrSuccessfulQueries(Resource assertionResource) {
		Integer ct = Engine.getQueryService().nrSuccessfulQueries().get(assertionResource);
		return ct == null ? 0 : ct;
    }

	@Override
    public long timeSinceLastQuery(Resource assertionResource) {
	    Long time = Engine.getQueryService().timeSinceLastQuery().get(assertionResource);
	    return time == null ? 0 : time;
	}

	@Override
    public boolean assertionUpdatesEnabled(Resource assertionResource) {
		return Engine.getContextAssertionIndex().isAssertionActive(assertionResource);
    }
}
