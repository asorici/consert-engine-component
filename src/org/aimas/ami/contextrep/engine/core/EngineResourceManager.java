package org.aimas.ami.contextrep.engine.core;

import java.io.InputStream;

import com.hp.hpl.jena.util.Locator;

public interface EngineResourceManager {
	
	public InputStream getResourceAsStream(String name);
	
	public Locator getResourceLocator();
}
