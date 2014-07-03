package org.aimas.ami.contextrep.engine.execution;

import java.util.Properties;

public interface ExecutionService {
	
	public void init(Properties configuration);
	
	public void start();
	
	public void stop();
	
	public void close();
}
