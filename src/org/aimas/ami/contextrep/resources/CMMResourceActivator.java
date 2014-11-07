package org.aimas.ami.contextrep.resources;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class CMMResourceActivator implements BundleActivator {
	public static final String CONSERT_APPLICATION_HEADER 	= "Consert-ApplicationId";
	public static final String CONSERT_APPLICATION_ID_PROP	= "consert.applicationId";
	
	@Override
	public void start(BundleContext context) throws Exception {
		// All we do is register the SystemTimeService with a property stating that it is intended for
		// the CMM deployment of the application with `applicationId'
		
		// All CMM Resource bundles MUST have a CONSERT_APPLICATION_HEADER with the applicationId 
		// for which the CMMdeployment configuration is specified
		Dictionary<String, String> headers = context.getBundle().getHeaders();
		String applicationId = headers.get(CONSERT_APPLICATION_HEADER);
		
		if (applicationId != null) {
			Dictionary<String, String> timeServiceProps = new Hashtable<String, String>();
			timeServiceProps.put(CONSERT_APPLICATION_ID_PROP, applicationId);
			
			// register the SystemTimeService implementation of the TimeService
			context.registerService(TimeService.class, new SystemTimeService(), timeServiceProps);
		}
	}
	
	@Override
	public void stop(BundleContext context) throws Exception {
		// The registered auxiliary services are removed automatically by the OSGi platform
	}
	
}
