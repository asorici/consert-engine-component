package org.aimas.ami.contextrep.resources;

import java.util.Calendar;
import java.util.TimeZone;

public class SystemTimeService implements TimeService {
	
	@Override
	public long getCurrentTimeMillis() {
		return System.currentTimeMillis();
	}
	
	@Override
	public Calendar getCalendarInstance() {
		return Calendar.getInstance(TimeZone.getTimeZone("GMT"));
	}
	
}
