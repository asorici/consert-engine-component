package org.aimas.ami.contextrep.engine;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;

import org.aimas.ami.contextrep.engine.api.AssertionUpdateListener;
import org.aimas.ami.contextrep.engine.api.CommandException;
import org.aimas.ami.contextrep.engine.api.CommandHandler;
import org.aimas.ami.contextrep.engine.api.ConstraintResolutionService;
import org.aimas.ami.contextrep.engine.api.ContextDerivationRule;
import org.aimas.ami.contextrep.engine.api.EngineConfigException;
import org.aimas.ami.contextrep.engine.api.EngineInferenceStats;
import org.aimas.ami.contextrep.engine.api.EngineQueryStats;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.InsertionResultNotifier;
import org.aimas.ami.contextrep.engine.api.PerformanceResult;
import org.aimas.ami.contextrep.engine.api.QueryHandler;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.engine.api.StatsHandler;
import org.aimas.ami.contextrep.engine.core.ConfigKeys;
import org.aimas.ami.contextrep.engine.core.ContextConstraintIndex.ConstraintType;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.execution.ContextInsertNotifier;
import org.aimas.ami.contextrep.engine.execution.ExecutionMonitor;
import org.aimas.ami.contextrep.engine.execution.FCFSPriorityProvider;
import org.aimas.ami.contextrep.engine.execution.PreferNewestConstraintResolution;
import org.aimas.ami.contextrep.engine.utils.ContextAssertionFinder;
import org.aimas.ami.contextrep.engine.utils.ContextAssertionGraph;
import org.aimas.ami.contextrep.engine.utils.ContextQueryUtil;
import org.aimas.ami.contextrep.engine.utils.DerivationRuleWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextAssertion.ContextAssertionType;
import org.aimas.ami.contextrep.resources.TimeService;
import org.aimas.ami.contextrep.utils.BundleResourceManager;
import org.aimas.ami.contextrep.utils.ResourceManager;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.apache.felix.dm.Dependency;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.Bundle;
import org.osgi.service.cm.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.model.Command;
import org.topbraid.spin.model.Construct;
import org.topbraid.spin.model.ElementList;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.model.Template;
import org.topbraid.spin.model.TemplateCall;
import org.topbraid.spin.util.CommandWrapper;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
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
	 * The service used by the CONSERT Engine to access current time and date settings.
	 * This wrapper allows us to abstract away real or simulated time.
	 */
	private TimeService timeService;
	
	/**
	 * The default OSGi logging service
	 */
	//private LogService logService;
	//private LogReaderService logReaderService;
	
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
	
	private Dependency defaultIntegrityResolutionDependency;
	private Dependency defaultUniquenessResolutionDependency;
	
	private Map<Resource, Dependency> specificIntegrityResolutionDependenies;
	private Map<Resource, Dependency> specificUniquenessResolutionDependenies;
	private Map<Resource, Dependency> specificValueResolutionDependenies;
	
	public EngineFrontend() {
		specificIntegrityResolutionDependenies = new HashMap<Resource, Dependency>();
		specificUniquenessResolutionDependenies = new HashMap<Resource, Dependency>();
		specificValueResolutionDependenies = new HashMap<Resource, Dependency>();
	}
	
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
		
		/* 
		 * There will be a case when this method is called when we switch out services 
		 * from one type to another. However, in those cases the addedInferencePriorityProvider
		 * function will have already been called with the new interface, such that when we call this
		 * with the old provider, we don't have to do anything.
		 */
	}
	
	
	@SuppressWarnings("unused")
	private void addedDefaultUniquenessResolution(ConstraintResolutionService uniquenessResolutionService) {
		Engine.getConstraintIndex().setDefaultUniquenessResolutionService(uniquenessResolutionService);
	}
	
	@SuppressWarnings("unused")
	private void removedDefaultUniquenessResolution(ConstraintResolutionService uniquenessResolutionService) {
		ConstraintResolutionService currentUniquenessResolutionService = Engine.getConstraintIndex().getDefaultUniquenessResolutionService();
		
		if (currentUniquenessResolutionService == uniquenessResolutionService) {
			// if the priority provider we were currently using was removed, revert to the default implementation (PreferNewest)
			Engine.getConstraintIndex().setDefaultUniquenessResolutionService(PreferNewestConstraintResolution.getInstance());
		}
	}
	
	
	@SuppressWarnings("unused")
	private void addedDefaultIntegrityResolution(ConstraintResolutionService integrityResolutionService) {
		Engine.getConstraintIndex().setDefaultIntegrityResolutionService(integrityResolutionService);
	}
	
	@SuppressWarnings("unused")
	private void removedDefaultIntegrityResolution(ConstraintResolutionService integrityResolutionService) {
		ConstraintResolutionService currentIntegrityResolutionService = Engine.getConstraintIndex().getDefaultIntegrityResolutionService();
		
		if (currentIntegrityResolutionService == integrityResolutionService) {
			// if the priority provider we were currently using was removed, revert to the default implementation (PreferNewest)
			Engine.getConstraintIndex().setDefaultIntegrityResolutionService(PreferNewestConstraintResolution.getInstance());
		}
	}
	
	// We first wait for the context-domain specific configuration above and now try
	// initialization. We try to read the CONSERT Engine specific configuration file and process it. 
	void initEngine(org.apache.felix.dm.Component component) throws ConfigurationException {
		// keep reference to our component view
		this.engineComponent = component;
		
		if (modelResourceBundle != null && timeService != null) {
			try {
	            // initialize the EngineResourceManager
				ResourceManager resourceManager = new BundleResourceManager(modelResourceBundle);
				Engine.setResourceManager(resourceManager);
	            
				// set CONSERT Engine time service
				Engine.setTimeService(timeService);
				
				// set CONSERT Engine logging service
				//Engine.setLogService(logService);
				//logReaderService.addLogListener(ExecutionMonitor.getInstance());
				
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
			throw new ConfigurationException(null, "Model Resource Bundle or Time Service is missing!");
		}
	}
	
	
	// Call this when the bundle containing the CONSERT Engine is destroyed
	void closeEngine(org.apache.felix.dm.Component component) {
		try {
			Dataset contextStore = Engine.getRuntimeContextStore();
			contextStore.begin(ReadWrite.READ);
			try {
				// get positions of microphones
				Model profiledLocationStore = 
						contextStore.getNamedModel("http://pervasive.semanticweb.org/ont/2004/06/device/hasProfiledLocationStore");
				StmtIterator locIt = profiledLocationStore.listStatements(null, ConsertCore.CONTEXT_ASSERTION_RESOURCE, 
						ResourceFactory.createResource("http://pervasive.semanticweb.org/ont/2004/06/device#hasProfiledLocation"));
				while (locIt.hasNext()) {
					Statement s = locIt.next();
					Model m = contextStore.getNamedModel(s.getSubject().getURI());
					m.write(System.out, "TTL");
				}
				
				System.out.println("#####################################################");
				
				Model locatedInStore = contextStore.getNamedModel("http://pervasive.semanticweb.org/ont/2004/06/person/locatedInStore"); 
				locatedInStore.write(System.out, "TTL");
				
				System.out.println("=========================================================");
				System.out.println();
				
				Model discussionActivityStore = contextStore.getNamedModel("http://pervasive.semanticweb.org/ont/2014/07/smartclassroom/HostsAdHocDiscussionStore"); 
				discussionActivityStore.write(System.out, "TTL");
			}
			finally {
				contextStore.end();
			}
			
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
	public void insertAssertion(UpdateRequest insertionRequest, InsertionResultNotifier notifier, int updateMode) {
		ExecutionMonitor.getInstance().logInsertEnqueue(insertionRequest.hashCode());
		Engine.getInsertionService().executeRequest(insertionRequest, notifier, updateMode);
	}
	
	@Override
	public Future<InsertResult> insertAssertion(UpdateRequest insertionRequest, int updateMode) {
		return Engine.getInsertionService().executeRequest(insertionRequest, null, updateMode);
	}
	
	@Override
	public Future<?> bulkInsert(UpdateRequest bulkInsertionRequest) {
		//System.out.println("[" + EngineFrontend.class.getName() + "]: Performing BULK INSERT");
		//System.out.println(bulkInsertionRequest.toString());
		
		Future<?> result = Engine.getInsertionService().executeBulkRequest(bulkInsertionRequest);
		return result;
	}
	
	@Override
    public Future<?> updateEntityDescriptions(UpdateRequest entityDescriptionRequest) {
	    return Engine.getInsertionService().executeEntityDescriptionRequest(entityDescriptionRequest);
    }
	
	@Override
	public void updateProfiledAssertion(UpdateRequest profiledAssertionRequest, InsertionResultNotifier notifier) {
		Engine.getInsertionService().executeProfiledAssertionRequest(profiledAssertionRequest, notifier);
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
	public ContextAssertionType getAssertionType(Resource assertionResource) {
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		return assertion.getAssertionType();
	}
	
	public Set<Resource> getReferencedAssertions(Resource derivedAssertionRes) {
		Set<Resource> referencedAssertions = new HashSet<Resource>();
		
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(derivedAssertionRes);
		for (DerivationRuleWrapper wrapper : Engine.getDerivationRuleDictionary().getDerivedAssertionRules(assertion)) {
			for (ContextAssertion bodyAssertion : wrapper.getBodyAssertions()) {
				referencedAssertions.add(bodyAssertion.getOntologyResource());
			}
		}
		
		return referencedAssertions;
	}
	
	public Set<Resource> getControlCommandAssertions(CommandWrapper controlCommand, Map<String, RDFNode> templateBindings) {
		Set<Resource> referencedAssertions = new HashSet<Resource>();
		
		Command spinCommand = extractCommand(controlCommand);
		Construct constructCommand =  spinCommand.as(Construct.class);
		ElementList whereElements = constructCommand.getWhere();
		
		ContextAssertionFinder ruleBodyFinder = new ContextAssertionFinder(whereElements, 
				Engine.getContextAssertionIndex(), templateBindings);
		
		// run context assertion rule body finder and collect results
		ruleBodyFinder.run();
		Set<ContextAssertionGraph> bodyContextAssertions = ruleBodyFinder.getResult();
		
		for (ContextAssertionGraph cag : bodyContextAssertions) {
			referencedAssertions.add(cag.getAssertion().getOntologyResource());
		}
			
		return referencedAssertions;
	}
	
	private Command extractCommand(CommandWrapper controlCommand) {
		Command spinCommand = null;
		
		TemplateCall templateCall = SPINFactory.asTemplateCall(controlCommand.getSource());
		if (templateCall != null) {
			Template template = templateCall.getTemplate();
			if(template != null) {
				spinCommand = template.getBody();
			}
		}
		else {
			spinCommand = SPINFactory.asCommand(controlCommand.getSource());
		}
		
		return spinCommand;
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
	
	// =============================== CONSERT Engine dynamic service configuration =============================== // 
	@Override
    public void setInferenceSchedulingType(String priorityProviderType) {
	    Dependency oldDependency = inferencePriorityProviderDependency;
	    inferencePriorityProviderDependency = 
	    	engineComponent.getDependencyManager().createServiceDependency()
				.setService(InferencePriorityProvider.class, "(" + ConstraintResolutionService.RESOLUTION_TYPE + "=" + priorityProviderType + ")")
				.setDefaultImplementation(FCFSPriorityProvider.getInstance())
				.setCallbacks("addedInferencePriorityProvider", "removedInferencePriorityProvider");
	    
	    // add the new dependency
	    engineComponent.add(inferencePriorityProviderDependency);
	    
	    // remove the old one
	    if (oldDependency != null) {
	    	engineComponent.remove(oldDependency);
	    }
	}
	
	
	@Override
    public void setDefaultUniquenessConstraintResolution(String resolutionServiceName) {
		Dependency oldDependency = defaultUniquenessResolutionDependency;
		defaultUniquenessResolutionDependency = 
	    	engineComponent.getDependencyManager().createServiceDependency()
				.setService(ConstraintResolutionService.class, "(" + ConstraintResolutionService.RESOLUTION_TYPE + "=" + resolutionServiceName + ")")
				.setDefaultImplementation(PreferNewestConstraintResolution.getInstance())
				.setCallbacks("addedDefaultUniquenessResolution", "removedDefaultUniquenessResolution");
	    
	    // add the new dependency
	    engineComponent.add(defaultUniquenessResolutionDependency);
	    
	    // remove the old one
	    if (oldDependency != null) {
	    	engineComponent.remove(oldDependency);
	    }
    }

	@Override
    public void setDefaultIntegrityConstraintResolution(String resolutionServiceName) {
		Dependency oldDependency = defaultIntegrityResolutionDependency;
		defaultIntegrityResolutionDependency = 
	    	engineComponent.getDependencyManager().createServiceDependency()
				.setService(ConstraintResolutionService.class, "(" + ConstraintResolutionService.RESOLUTION_TYPE + "=" + resolutionServiceName + ")")
				.setDefaultImplementation(PreferNewestConstraintResolution.getInstance())
				.setCallbacks("addedDefaultIntegrityResolution", "removedDefaultIntegrityResolution");
	    
	    // add the new dependency
	    engineComponent.add(defaultIntegrityResolutionDependency);
	    
	    // remove the old one
	    if (oldDependency != null) {
	    	engineComponent.remove(oldDependency);
	    }
    }

	@Override
    public void setSpecificUniquenessConstraintResolution(Resource assertionResource, String resolutionServiceName) {
		SpecificConstraintResolutionServiceTracker serviceTracker = new SpecificConstraintResolutionServiceTracker(assertionResource, ConstraintType.Uniqueness);
		Dependency oldDependency = specificUniquenessResolutionDependenies.get(assertionResource);
		Dependency newDependency = 
			engineComponent.getDependencyManager().createServiceDependency()
				.setService(ConstraintResolutionService.class, "(" + ConstraintResolutionService.RESOLUTION_TYPE + "=" + resolutionServiceName + ")")
				.setDefaultImplementation(PreferNewestConstraintResolution.getInstance())
				.setCallbacks(serviceTracker, "resolutionServiceAdded", "resolutionServiceRemoved");
		
		// add the new dependency
		specificUniquenessResolutionDependenies.put(assertionResource, newDependency);
	    engineComponent.add(newDependency);
	    
	    // remove the old one
	    if (oldDependency != null) {
	    	engineComponent.remove(oldDependency);
	    }
    }

	@Override
    public void setSpecificIntegrityConstraintResolution(Resource assertionResource, String resolutionServiceName) {
		SpecificConstraintResolutionServiceTracker serviceTracker = new SpecificConstraintResolutionServiceTracker(assertionResource, ConstraintType.Integrity);
		Dependency oldDependency = specificIntegrityResolutionDependenies.get(assertionResource);
		Dependency newDependency = 
			engineComponent.getDependencyManager().createServiceDependency()
				.setService(ConstraintResolutionService.class, "(type=" + resolutionServiceName + ")")
				.setDefaultImplementation(PreferNewestConstraintResolution.getInstance())
				.setCallbacks(serviceTracker, "resolutionServiceAdded", "resolutionServiceRemoved");
		
		// add the new dependency
		specificIntegrityResolutionDependenies.put(assertionResource, newDependency);
	    engineComponent.add(newDependency);
	    
	    // remove the old one
	    if (oldDependency != null) {
	    	engineComponent.remove(oldDependency);
	    }
    }

	@Override
    public void setSpecificValueConstraintResolution(Resource assertionResource, String resolutionServiceName) {
		SpecificConstraintResolutionServiceTracker serviceTracker = new SpecificConstraintResolutionServiceTracker(assertionResource, ConstraintType.Value);
		Dependency oldDependency = specificValueResolutionDependenies.get(assertionResource);
		Dependency newDependency = 
			engineComponent.getDependencyManager().createServiceDependency()
				.setService(ConstraintResolutionService.class, "(" + ConstraintResolutionService.RESOLUTION_TYPE + "=" + resolutionServiceName + ")")
				.setDefaultImplementation(PreferNewestConstraintResolution.getInstance())
				.setCallbacks(serviceTracker, "resolutionServiceAdded", "resolutionServiceRemoved");
		
		// add the new dependency
		specificValueResolutionDependenies.put(assertionResource, newDependency);
	    engineComponent.add(newDependency);
	    
	    // remove the old one
	    if (oldDependency != null) {
	    	engineComponent.remove(oldDependency);
	    }
    }
	
	private class SpecificConstraintResolutionServiceTracker {
		private Resource assertionResource;
		private ConstraintType constraintType;
		
		public SpecificConstraintResolutionServiceTracker(Resource assertionResource, ConstraintType constraintType) {
	        this.assertionResource = assertionResource;
	        this.constraintType = constraintType;
        }

		@SuppressWarnings("unused")
        public void resolutionServiceAdded(ConstraintResolutionService resolutionService) {
			ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
			
			if (constraintType == ConstraintType.Uniqueness) {
				Engine.getConstraintIndex().setUniquenessResolutionService(assertion, resolutionService);
			}
			else if (constraintType == ConstraintType.Integrity) {
				Engine.getConstraintIndex().setIntegrityResolutionService(assertion, resolutionService);
			}
			else {
				Engine.getConstraintIndex().setValueResolutionService(assertion, resolutionService);
			}
		}
		
		@SuppressWarnings("unused")
        public void resolutionServiceRemoved(ConstraintResolutionService resolutionService) {
			ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
			
			if (constraintType == ConstraintType.Uniqueness && Engine.getConstraintIndex().getUniquenessResolutionService(assertion) == resolutionService) {
				Engine.getConstraintIndex().setUniquenessResolutionService(assertion, PreferNewestConstraintResolution.getInstance());
			}
			else if (constraintType == ConstraintType.Integrity && Engine.getConstraintIndex().getIntegrityResolutionService(assertion) == resolutionService) {
				Engine.getConstraintIndex().setUniquenessResolutionService(assertion, PreferNewestConstraintResolution.getInstance());
			}
			else if (Engine.getConstraintIndex().getValueResolutionService(assertion) == resolutionService) {
				Engine.getConstraintIndex().setUniquenessResolutionService(assertion, PreferNewestConstraintResolution.getInstance());
			}
		}
	}
	
	// =============================== CONSERT Engine assertion update/inference management =============================== //
	@Override
	public void setAssertionInsertActiveByDefault(boolean activeByDefault) {
		Engine.getContextAssertionIndex().setEnabledByDefault(activeByDefault);
	}
	
	@Override
	public void setAssertionInferenceActiveByDefault(boolean activeByDefault) {
		Engine.getDerivationRuleDictionary().setActiveByDefault(activeByDefault);
	}
	
	@Override
	public void setAssertionActive(Resource assertionResource, boolean active) {
		Engine.getContextAssertionIndex().setAssertionUpdateEnabledActive(assertionResource, active);
		
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		if (assertion.getAssertionType() == ContextAssertionType.Derived) {
			Engine.getDerivationRuleDictionary().setDerivedAssertionActive(assertion, active);
		}
	}
	
	@Override
    public void setDerivationRuleActive(Resource derivedAssertionResource, boolean active) {
		Engine.getContextAssertionIndex().setAssertionUpdateEnabledActive(derivedAssertionResource, active);
		
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
    public AssertionEnableStatus getAssertionEnableStatus(Resource assertionResource) {
		boolean containedInModel = Engine.getContextAssertionIndex().containsAssertion(assertionResource);
		boolean updatesEnabled = false;
		
		if (containedInModel) {
			updatesEnabled = Engine.getContextAssertionIndex().isAssertionUpdateEnabled(assertionResource);
		}
		
		return new AssertionEnableStatus(containedInModel, updatesEnabled);
    }
	
	@Override
	public List<Resource> getEnabledAssertions() {
		return Engine.getContextAssertionIndex().listEnabledAssertions();
	}
	
	
	@Override
	public PerformanceResult measurePerformance() {
		return ExecutionMonitor.getInstance().exportPerformanceResult();
	}
}
