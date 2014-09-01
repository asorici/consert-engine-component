package org.aimas.ami.contextrep.engine;

import java.util.Hashtable;

import org.aimas.ami.contextrep.engine.api.CommandHandler;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.QueryHandler;
import org.aimas.ami.contextrep.engine.api.StatsHandler;
import org.aimas.ami.contextrep.engine.execution.FCFSPriorityProvider;
import org.aimas.ami.contextrep.resources.SystemTimeService;
import org.aimas.ami.contextrep.resources.TimeService;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;

public class EngineActivator extends DependencyActivatorBase {
	
	public EngineActivator() {
	}
	
	@Override
	public void init(BundleContext context, DependencyManager manager) throws Exception {
		// create the CONSERT Engine component and configure its dependencies and lifecycle management
		manager.add(createComponent()
			.setInterface(new String[] {InsertionHandler.class.getName(), QueryHandler.class.getName(), 
					StatsHandler.class.getName(), CommandHandler.class.getName()}, null)
			.setImplementation(EngineFrontend.class)
			.setCallbacks("initEngine", "startEngine", "stopEngine", "closeEngine")
			.add(createBundleDependency()
					.setFilter("(Bundle-Name=cmm-resources)")
					.setRequired(true)
					.setCallbacks("setModelResourceBundle", null)
					.setPropagate(true)
			).add(createServiceDependency()
					.setService(TimeService.class)
					.setDefaultImplementation(SystemTimeService.class)
					.setRequired(true)
					.setAutoConfig("timeService")
			)
			/*
			.add(createServiceDependency()
					.setService(LogService.class)
					.setRequired(true)
					.setAutoConfig("logService")
			).add(createServiceDependency()
					.setService(LogReaderService.class)
					.setRequired(true)
					.setAutoConfig("logReaderService")
			)
			*/
		);
		
		// register the Default FCFS InferenceRequestComparatorProvider that the CONSERT Engine
		// supplies by default
		Hashtable<String, String> props = new Hashtable<String, String>();
		props.put("type", "FCFS");
		context.registerService(InferencePriorityProvider.class, 
				FCFSPriorityProvider.getInstance(), props);
	}
	
	@Override
	public void destroy(BundleContext context, DependencyManager manager) throws Exception {
		
	}
	
}
