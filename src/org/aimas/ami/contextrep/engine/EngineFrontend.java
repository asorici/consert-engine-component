package org.aimas.ami.contextrep.engine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.Properties;
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
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.reasoner.ValidityReport;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateRequest;


@Component(
	name = "consert-engine",
	immediate = true
)
@Service
public class EngineFrontend implements InsertionHandler, QueryHandler, CommandHandler, StatsHandler {
	private Logger log = LoggerFactory.getLogger(getClass());
	
	private Dictionary<String, String> consertEngineConfig;
	private Dictionary<String, String> domainModelConfig;
	
	public EngineFrontend() {}
	
	// ==================== INITIALIZATION AND TAKE-DOWN HANDLING ==================== //
	
	/*
	@ConfigurationDependency(pid="domain-model-config")
	void configure(Dictionary<String, String> domainModelConfig) throws ConfigurationException {
		validateDomainModelConfig(domainModelConfig);
		this.domainModelConfig = domainModelConfig;
	}
	*/
	
	private void validateDomainModelConfig(Dictionary<String, String> domainModelConfig) 
			throws ConfigurationException {
		// Step 1) Check for existence of Context Model Core URI (this MUST exist)
		if (domainModelConfig.get(ConfigKeys.DOMAIN_ONT_CORE_URI) == null) {
				throw new ConfigurationException(ConfigKeys.DOMAIN_ONT_CORE_URI, "No value for required" 
						+ "Context Domain core module key: " + ConfigKeys.DOMAIN_ONT_CORE_URI);
		}
		
		// Step 2) Check for existence of the Context Model document manager file key
		if (domainModelConfig.get(ConfigKeys.DOMAIN_ONT_DOCMGR_FILE) == null) {
			throw new ConfigurationException(ConfigKeys.DOMAIN_ONT_DOCMGR_FILE, "Configuration properties has no value for "
					+ "Context Domain ontology document manager key: " + ConfigKeys.DOMAIN_ONT_DOCMGR_FILE);
		}
    }
	
	// We first wait for the context-domain specific configuration above and now try
	// initialization. We try to read the CONSERT Engine specific configuration file and process it. 
	void initEngine(org.apache.felix.dm.Component component) throws ConfigurationException {
		System.out.println("Initializing CONSERT Engine component");
		
		URL configURL = this.getClass().getResource("/etc/config.properties");
		System.out.println(configURL);
		if (configURL != null) {
			try {
	            InputStream configStream = configURL.openStream();
	            Properties configProperties = Engine.readConfiguration(configStream);
	            
	            // initialize the engine
	            Engine.init(configProperties, true);
            }
            catch (IOException e) {
	            throw new ConfigurationException(null, "CONSERT Engine configuration file unreadable.", e);
            }
			catch (ConfigException e) {
				throw new ConfigurationException(null, "CONSERT Engine configuration invalid.", e);
			}
		}
		else {
			throw new ConfigurationException(null, "CONSERT Engine configuration file not found.");
		}
	}
	
	
	void startEngine(ComponentContext context) {
		
	}
	
	void stopEngine(ComponentContext context) {
		
	}
	
	
	// =============================== INSERT HANDLING =============================== //
	
	@Override
	public InsertResult insert(UpdateRequest insertionRequest) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public InsertResult insert(Update assertionContents, Update assertionAnnotations) {
		// TODO Auto-generated method stub
		return null;
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
