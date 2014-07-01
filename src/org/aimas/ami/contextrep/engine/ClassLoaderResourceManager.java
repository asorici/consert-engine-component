package org.aimas.ami.contextrep.engine;

import java.io.InputStream;

import com.hp.hpl.jena.util.Locator;
import com.hp.hpl.jena.util.LocatorClassLoader;

public class ClassLoaderResourceManager implements EngineResourceManager {
	private static Locator resourceLocator;
	private ClassLoader engineResourceLoader;
	
	/**
	 * Create a new ClassLoaderResourceManager by specifying the classloader that is used to 
	 * find the required resources (coniguration files, ontology files).
	 * 
	 * @param engineResourceLoader
	 */
	public ClassLoaderResourceManager(ClassLoader engineResourceLoader) {
		this.engineResourceLoader = engineResourceLoader;
	}
	
	/**
	 * Create a new EngineResourceManager where the resourceLoader is considered to be the one
	 * that loaded this class.
	 */
	public ClassLoaderResourceManager() {
		this(ClassLoaderResourceManager.class.getClassLoader());
	}
	
	
	public ClassLoader getLoader() {
	    return engineResourceLoader;
    }
	
	@Override
	public InputStream getResourceAsStream(String name) {
		// try the engineResourceLoader first
		return engineResourceLoader.getResourceAsStream(name);
	}

	@Override
    public Locator getResourceLocator() {
	    if (resourceLocator == null) {
	    	resourceLocator = new LocatorClassLoader(engineResourceLoader); 
	    }
		
	    return resourceLocator;
    }
	
}
