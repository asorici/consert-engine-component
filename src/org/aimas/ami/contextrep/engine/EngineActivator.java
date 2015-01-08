package org.aimas.ami.contextrep.engine;

import java.util.Hashtable;

import org.aimas.ami.contextrep.engine.api.ConstraintResolutionService;
import org.aimas.ami.contextrep.engine.api.InferencePriorityProvider;
import org.aimas.ami.contextrep.engine.execution.DropAllConstraintResolution;
import org.aimas.ami.contextrep.engine.execution.FCFSPriorityProvider;
import org.aimas.ami.contextrep.engine.execution.PreferAccurateConstraintResolution;
import org.aimas.ami.contextrep.engine.execution.PreferNewestConstraintResolution;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;

public class EngineActivator extends DependencyActivatorBase {
	
	public EngineActivator() {}
	
	@Override
	public void init(BundleContext context, DependencyManager manager) throws Exception {
		// Register services exposed by default by the CONSERT Engine
		// FCFS InferenceRequestComparatorProvider
		Hashtable<String, String> props = new Hashtable<String, String>();
		props.put(InferencePriorityProvider.PRIORITY_PROVIDER_TYPE, "FCFS");
		context.registerService(InferencePriorityProvider.class, FCFSPriorityProvider.getInstance(), props);
		
		// PreferNewest Constraint Resolution Service
		props = new Hashtable<String, String>();
		props.put(ConstraintResolutionService.RESOLUTION_TYPE, "PreferNewest");
		context.registerService(ConstraintResolutionService.class, PreferNewestConstraintResolution.getInstance(), props);
		
		// PreferAccurate Constraint Resolution Service
		props = new Hashtable<String, String>();
		props.put(ConstraintResolutionService.RESOLUTION_TYPE, "PreferAccurate");
		context.registerService(ConstraintResolutionService.class, PreferAccurateConstraintResolution.getInstance(), props);
		
		// DropAll Constraint Resolution Service
		props = new Hashtable<String, String>();
		props.put(ConstraintResolutionService.RESOLUTION_TYPE, "DropAll");
		context.registerService(ConstraintResolutionService.class, DropAllConstraintResolution.getInstance(), props);
	}
	
	@Override
	public void destroy(BundleContext context, DependencyManager manager) throws Exception {
		
	}
	
}
