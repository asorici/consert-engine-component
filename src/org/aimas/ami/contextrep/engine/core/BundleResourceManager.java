package org.aimas.ami.contextrep.engine.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.util.FileUtils;
import com.hp.hpl.jena.util.Locator;
import com.hp.hpl.jena.util.LocatorFile;
import com.hp.hpl.jena.util.TypedStream;

public class BundleResourceManager implements EngineResourceManager {
	private static Locator resourceLocator;
	private Bundle resourceBundle;
	
	public BundleResourceManager(Bundle resourceBundle) {
		this.resourceBundle = resourceBundle;
	}
	
	@Override
	public InputStream getResourceAsStream(String name) {
		// try getting with the given name
		URL entryURL = resourceBundle.getEntry(name);
		if (entryURL == null) {
			// try adding a slash at the beginning to search the from the root
			entryURL = resourceBundle.getEntry("/" + name);
		}
		
		if (entryURL != null) {
			try {
		        return entryURL.openStream();
	        }
	        catch (IOException e) {
		        e.printStackTrace();
	        	return null;
	        }
		}
		
		return null;
	}
	
	@Override
	public Locator getResourceLocator() {
		if (resourceLocator == null) {
			resourceLocator = new LocatorBundle(resourceBundle);
		}
		
		return resourceLocator;
	}
	
	
	private static class LocatorBundle implements Locator {
		static Logger log = LoggerFactory.getLogger(LocatorBundle.class) ;
		private Bundle bundle;
		
		LocatorBundle(Bundle bundle) {
			this.bundle = bundle;
		}
		
		@Override
        public TypedStream open(String filenameOrURI) {
			String fn = FileUtils.toFilename(filenameOrURI) ;
			 if ( fn == null ){
				 return null ;
		     }
			 
			// try getting with the given name
			URL entryURL = bundle.getEntry(fn);
			if (entryURL == null) {
				// try adding a slash at the beginning to search the from the root
				entryURL = bundle.getEntry("/" + fn);
			}
			
			if (entryURL != null) {
				try {
					InputStream in = entryURL.openStream();
					if (in != null) {
						return new TypedStream(in);
					}
		        }
		        catch (IOException e) {
			        e.printStackTrace();
		        	return null;
		        }
			}
			
			return null;
        }
		
		
		@Override
        public String getName() {
	        return "LocatorBundle(" + bundle.getSymbolicName() + ")";
        }
	}
	
}
