package org.aimas.ami.contextrep.engine;

import org.aimas.ami.contextrep.engine.api.CommandHandler;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.QueryHandler;
import org.aimas.ami.contextrep.engine.api.StatsHandler;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;

public class EngineActivator extends DependencyActivatorBase {
	
	public EngineActivator() {
	}
	
	@Override
	public void init(BundleContext context, DependencyManager manager) throws Exception {
		manager.add(createComponent()
			.setInterface(new String[] {InsertionHandler.class.getName(), QueryHandler.class.getName(), 
					StatsHandler.class.getName(), CommandHandler.class.getName()}, null)
			.setImplementation(EngineFrontend.class)
			//.setCallbacks("initEngine", "startEngine", "stopEngine", null)
			.setCallbacks("initEngine", null, null, "closeEngine")
			.add(createBundleDependency()
					.setFilter("(Bundle-Name=consert-model-resources)")
					.setRequired(true)
					.setCallbacks("setModelResourceBundle", null)
					.setPropagate(true)
			)
		);
	}
	
	@Override
	public void destroy(BundleContext context, DependencyManager manager) throws Exception {
		
	}
	
}
