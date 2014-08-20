package org.aimas.ami.contextrep.resources;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class CMMResourceActivator implements BundleActivator {
	
	@Override
	public void start(BundleContext context) throws Exception {
		// Register the SystemTime service
		context.registerService(TimeService.class, new SystemTimeService(), null);
	}
	
	@Override
	public void stop(BundleContext context) throws Exception {
		// Nothing to do
	}
	
}
