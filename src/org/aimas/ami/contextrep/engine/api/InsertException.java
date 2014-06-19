package org.aimas.ami.contextrep.engine.api;

public class InsertException extends Exception {
	
    private static final long serialVersionUID = 1L;

	public InsertException() {
		this("Insert error ocurred.");
	}
	
	public InsertException(String message) {
		super(message);
	}
	
	public InsertException(Throwable cause) {
		super(cause);
	}
	
	public InsertException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public InsertException(String message, Throwable cause,
	        boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
}
