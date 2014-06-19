package org.aimas.ami.contextrep.datatype;

public class IntervalListFormatException extends Exception {

	private static final long serialVersionUID = 1L;

	public IntervalListFormatException(String intervalListStr) {
		super("String " + intervalListStr + " does not respect {[Calendar,Calendar],...} format");
	}

	public IntervalListFormatException(String intervalListStr, Throwable cause) {
		super("String " + intervalListStr + " does not respect {[Calendar,Calendar],...} format", cause);
	}

}
