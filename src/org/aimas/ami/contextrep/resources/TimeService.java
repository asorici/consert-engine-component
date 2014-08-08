package org.aimas.ami.contextrep.resources;

import java.util.Calendar;

public interface TimeService {
	/**
	 * @return The current CONSERT Engine internal time in milliseconds since the Epoch.
	 */
	public long getCurrentTimeMillis();
	
	/**
	 * 
	 * @return The current CONSERT Engine internal Calendar date, in GMT time zone.
	 */
	public Calendar getCalendarInstance();
}
