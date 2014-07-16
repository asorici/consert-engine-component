package org.aimas.ami.contextrep.engine.api;

public class EngineConfigException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public EngineConfigException() {
		this("Configuration properties not initialized.");
	}

	public EngineConfigException(String message) {
		super(message);
	}

	public EngineConfigException(Throwable cause) {
		super(cause);
	}

	public EngineConfigException(String message, Throwable cause) {
		super(message, cause);
	}

	public EngineConfigException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
	
}
