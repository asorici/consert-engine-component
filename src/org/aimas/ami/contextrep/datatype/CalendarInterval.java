package org.aimas.ami.contextrep.datatype;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class CalendarInterval extends Interval<Calendar> {
	public static final String NEGATIVE_INFTY = "-inf";
	public static final String POSITIVE_INFTY = "+inf";
	public static final String LIMITS_SEPARATOR = ",";
	
	public static final int MAX_GAP_MILLIS = 10000;
	
	private static final String FORMAT_TEMPLATE = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	
	
	public CalendarInterval(IntervalLimit<Calendar> lower, IntervalLimit<Calendar> upper) {
		super(lower, upper);
	}

	public CalendarInterval(Calendar lower, boolean isLowerClosed, Calendar upper, boolean isUpperClosed) {
		super(lower, isLowerClosed, upper, isUpperClosed);
	}
	
	public String unparse() {
		SimpleDateFormat formatter = new SimpleDateFormat(FORMAT_TEMPLATE);
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		Calendar lowerLimit = lowerLimit();
		Calendar upperLimit = upperLimit();
		
		String lower = NEGATIVE_INFTY;
		String upper = POSITIVE_INFTY;
		
		if (lowerLimit != null) {
			lower = formatter.format(lowerLimit.getTime());
		}
		
		if (upperLimit != null) {
			upper = formatter.format(upperLimit.getTime());
		}
		
		return "[" + lower + LIMITS_SEPARATOR + upper + "]";
    }
	
	
	public static CalendarInterval parse(String intervalStr) throws IntervalFormatException {
		if (!intervalStr.startsWith("[") || !intervalStr.endsWith("]")) 
			throw new IntervalFormatException(intervalStr);
		
		intervalStr = intervalStr.substring(1, intervalStr.length() - 1);
		
		String[] intervalPoints = intervalStr.split(LIMITS_SEPARATOR);
		if (intervalPoints.length != 2) 
			throw new IntervalFormatException(intervalStr);
		
		SimpleDateFormat formatter = new SimpleDateFormat(FORMAT_TEMPLATE);
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		Calendar lower = Calendar.getInstance();
		Calendar upper = Calendar.getInstance();
		
		try {
			if (intervalPoints[0].equals(NEGATIVE_INFTY)) lower = null;
			else lower.setTime(formatter.parse(intervalPoints[0]));
			
			if (intervalPoints[1].equals(POSITIVE_INFTY)) upper = null;
			else upper.setTime(formatter.parse(intervalPoints[1]));
			
			return new CalendarInterval(lower, true, upper, true);
		} catch (ParseException e) {
			throw new IntervalFormatException(intervalStr, e);
		}
	}
	
	
	public boolean closeEnoughTo(CalendarInterval interval, long maxGapMillis) {
		if (lowerLimit() == null && interval.lowerLimit() == null) {
			if (upperLimit() != null && interval.upperLimit() != null) {
				return Math.abs(upperLimit().getTimeInMillis() - interval.upperLimit().getTimeInMillis()) <= maxGapMillis;
			}
			
			return false;
		}
		
		if (upperLimit() == null && interval.upperLimit() == null) {
			if (lowerLimit() != null && interval.lowerLimit() != null) {
				return Math.abs(lowerLimit().getTimeInMillis() - interval.lowerLimit().getTimeInMillis()) <= maxGapMillis;
			}
			
			return false;
		}
		
		Interval<Calendar> gap = gap(interval);
		
		if (gap.isEmpty()) 
			return true;
		
		Calendar lower = gap.lowerLimit();
		Calendar upper = gap.upperLimit();
		
		if (lower == null || upper == null) return false;
		
		return upper.getTimeInMillis() - lower.getTimeInMillis() <= maxGapMillis;
	}
	
	
	public boolean closeEnoughTo(CalendarInterval interval) {
		return closeEnoughTo(interval, MAX_GAP_MILLIS);
	}
	
	
	@Override
	public String toString() {
		SimpleDateFormat formatter = new SimpleDateFormat(FORMAT_TEMPLATE);
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

		if (isEmpty())
			return "{}";
		if (isSingleElement())
			return "{" + formatter.format(lowerLimit().getTime()) + "}";
		StringBuffer buffer = new StringBuffer();
		buffer.append(includesLowerLimit() ? "[" : "(");
		buffer.append(hasLowerLimit() ? formatter.format(lowerLimit().getTime()) : "-Infinity");
		buffer.append(", ");
		buffer.append(hasUpperLimit() ? formatter.format(upperLimit().getTime()) : "Infinity");
		buffer.append(includesUpperLimit() ? "]" : ")");
		return buffer.toString();
	}
	
	public static CalendarInterval fromInterval(Interval<Calendar> interval) {
		return new CalendarInterval(interval.lowerLimit(), interval.includesLowerLimit(), 
				interval.upperLimit(), interval.includesUpperLimit());
	}
	
	
	public static void main(String args[]) {
		Calendar t1 = Calendar.getInstance();
		Calendar t2 = (Calendar)t1.clone(); t2.add(Calendar.SECOND, 10);
		Calendar t3 = (Calendar)t2.clone(); t3.add(Calendar.SECOND, 20); 
		Calendar t4 = (Calendar)t3.clone(); t4.add(Calendar.SECOND, 10);
		
		CalendarInterval interval1 = new CalendarInterval(t1, true, t2, true);
		CalendarInterval interval2 = new CalendarInterval(t3, true, t4, true);
		
		System.out.println(interval2.closeEnoughTo(interval1));
	}
}
