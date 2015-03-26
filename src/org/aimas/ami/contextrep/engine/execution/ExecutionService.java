package org.aimas.ami.contextrep.engine.execution;

import java.util.Properties;

import org.aimas.ami.contextrep.engine.core.Engine;

public abstract class ExecutionService {
	
	protected Engine engine;
	
	protected ExecutionService(Engine engine) {
		this.engine = engine;
	}
	
	public abstract void init(Properties configuration);
	
	public abstract void start();
	
	public abstract void stop();
	
	public abstract void close();
}
