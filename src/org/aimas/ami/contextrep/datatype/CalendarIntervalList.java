package org.aimas.ami.contextrep.datatype;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.aimas.ami.contextrep.utils.SortedList;

public class CalendarIntervalList extends IntervalList<CalendarInterval> {
	public static final String INTERVAL_SEPARATOR = ";";
	
	public CalendarIntervalList() {}
	
	public CalendarInterval getEarliest() {
		return get(0);
	}
	
	public CalendarInterval getLatest() {
		return get(size() - 1);
	}
    
	/**
	 * Determines if this list of validity intervals "contains" the <code>other</code>.
	 * The relation is defined to be true if at least one interval from this list includes entirely
	 * includes an interval from <code>other</code>.
	 * @param other The CalendarIntervalList to check for inclusion
	 * @return true if the containment relation is satisfied and false otherwise
	 */
	public boolean contains(CalendarIntervalList other) {
		int size = size();
		int otherSize = other.size();
		
		int i = 0, j = 0;
		
		while(i < size || j < otherSize) {
			CalendarInterval interval = intervals.get(i);
			CalendarInterval otherInterval = other.get(j);
			
			if (interval.covers(otherInterval)) {
				//System.out.println(interval + " covers " + otherInterval);
				return true;
			}
			
			if (interval.intersects(otherInterval)) {
				if (interval.compareTo(otherInterval) >= 0) {
					j++;
					if (j == otherSize)
						break;
				}
				else {
					i++;
					j++;
					
					if (i == size || j == otherSize)
						break;
				}
			}
			else {
				if (interval.isBefore(otherInterval)) {
					i++;
					
					if (i < size) {
						while (i < size - 1
								&& (interval = intervals.get(i)).upperLimit() != null 
								&& interval.upperLimit().before(otherInterval.lowerLimit())
								) i++;
					}
					else 
						break;
				}
				else if (interval.isAfter(otherInterval)) {
					j++;
					
					if (j < otherSize) {
						while (j < otherSize - 1
								&& (otherInterval = other.get(j)).upperLimit() != null 
								&& otherInterval.upperLimit().before(interval.lowerLimit())
								) j++;
					}
					else 
						break;
				}
			}
			
		}
		
		return false;
	}
	
	/**
	 * Determines if this list of validity intervals "overlaps" the <code>other</code>.
	 * The relation is defined to be true if at least one interval from this list overlaps
	 * with an interval from <code>other</code>.
	 * @param other The CalendarIntervalList to check for overlap
	 * @return true if the overlap relation is satisfied and false otherwise
	 */
	public boolean hasOverlap(CalendarIntervalList other) {
		int size = size();
		int otherSize = other.size();
		
		int i = 0, j = 0;
		
		while(i < size || j < otherSize) {
			CalendarInterval interval = intervals.get(i);
			CalendarInterval otherInterval = other.get(j);
			
			if (interval.intersects(otherInterval)) {
				return true;
			}
			else {
				if (interval.isBefore(otherInterval)) {
					i++;
					
					if (i < size) {
						while (i < size - 1
								&& (interval = intervals.get(i)).upperLimit() != null 
								&& interval.upperLimit().before(otherInterval.lowerLimit())
								) i++;
					}
					else
						break;
				}
				else if (interval.isAfter(otherInterval)) {
					j++;
					
					if (j < otherSize) {
						while (j < otherSize - 1
								&& (otherInterval = other.get(j)).upperLimit() != null 
								&& otherInterval.upperLimit().before(interval.lowerLimit())
								) j++;
					}
					else
						break;
				}
			}
		}
		
		return false;
	}
	
	
	/**
	 * Determines if this list of validity intervals "is close enough to" the <code>other</code>.
	 * The relation is defined to be true if at least one interval from this list "is close" 
	 * within <code>maxGapMillis</code> milliseconds to an interval from <code>other</code>.
	 * @param other The CalendarIntervalList to check for closeness
	 * @return true if the closeness relation is satisfied and false otherwise
	 */
	public boolean closeEnoughTo(CalendarIntervalList other, long maxGapMillis) {
		int size = size();
		int otherSize = other.size();
		
		int i = 0, j = 0;
		
		while(i < size || j < otherSize) {
			CalendarInterval interval = intervals.get(i);
			CalendarInterval otherInterval = other.get(j);
			
			if (interval.closeEnoughTo(otherInterval, maxGapMillis)) {
				return true;
			}
			else {
				if (interval.isBefore(otherInterval)) {
					i++;
					
					if (i < size) {
						while (i < size - 1 
							&& (interval = intervals.get(i)).upperLimit() != null 
							&& otherInterval.lowerLimit().getTimeInMillis() - interval.upperLimit().getTimeInMillis() > maxGapMillis 
							) i++;
					}
					else 
						break;
				}
				else if (interval.isAfter(otherInterval)) {
					j++;
					
					if (j < otherSize) {
						while (j < otherSize - 1
							&&	(otherInterval = other.get(j)).upperLimit() != null 
							&& interval.lowerLimit().getTimeInMillis() - otherInterval.upperLimit().getTimeInMillis() > maxGapMillis
							) j++;
					}
					else 
						break;
				}
			}
		}
		
		return false;
	}
	
	
	public boolean closeEnoughTo(CalendarIntervalList other) {
		return closeEnoughTo(other, CalendarInterval.MAX_GAP_MILLIS);
	}
	
	
	/**
	 * Implements the join close enough operator for two time validity annotations
	 * @param other The validity annotation with which to <i>join</i> by being <i>close enough</i> (given as a <code>CalendarIntervalList</code> instance)
	 * @return The result of the <i>join close enough</i> of the two validity annotations
	 */
	public CalendarIntervalList joinCloseEnough(CalendarIntervalList other, long maxGapMillis) {
		CalendarIntervalList join = new CalendarIntervalList();
		
		join.addAll(intervals);
		join.addAll(other.asList());
		
		int index = 0;
		
		while(true) {
			if (index == join.size() - 1) 
				break;
			
			CalendarInterval current = join.get(index);
			CalendarInterval next = join.get(index + 1);
			
			if (current.closeEnoughTo(next, maxGapMillis)) {
				join.remove(index);
				join.remove(index);
				
				join.insertAt(index, CalendarInterval.fromInterval(current.extend(next)));
			}
			else {
				index++;
			}
		}
		
		return join;
	}
	
	
	/**
	 * Implements the join operator for two time validity annotations
	 * @param other The validity annotation with which to <i>join</i> (given as a <code>CalendarIntervalList</code> instance)
	 * @return The result of the <i>join</i> of the two validity annotations
	 */
	public CalendarIntervalList join(CalendarIntervalList other) {
		CalendarIntervalList join = new CalendarIntervalList();
		
		join.addAll(intervals);
		join.addAll(other.asList());
		
		int index = 0;
		
		while(true) {
			if (index == join.size() - 1) 
				break;
			
			CalendarInterval current = join.get(index);
			CalendarInterval next = join.get(index + 1);
			
			if (current.intersects(next)) {
				join.remove(index);
				join.remove(index);
				
				join.insertAt(index, CalendarInterval.fromInterval(current.extend(next)));
			}
			else {
				index++;
			}
		}
		
		return join;
	}
	
	/**
	 * Implements the meet operator for two time validity annotations
	 * @param other The validity annotations with which to <i>meet</i> (given as a <code>CalendarIntervalList</code> instance)
	 * @return The result of the <i>meet</i> of the two validity annotations
	 */
	public CalendarIntervalList meet(CalendarIntervalList other) {
		CalendarIntervalList meet = new CalendarIntervalList();
		List<IntervalMark> intervalLimits = new ArrayList<>();
		
		for (int i = 0; i < size(); i++) {
			CalendarInterval interval = get(i);
			intervalLimits.add(new IntervalMark(interval, interval.lowerLimit(), IntervalMark.LOWER));
			intervalLimits.add(new IntervalMark(interval, interval.upperLimit(), IntervalMark.UPPER));
		}
		
		for (int i = 0; i < other.size(); i++) {
			CalendarInterval interval = other.get(i);
			intervalLimits.add(new IntervalMark(interval, interval.lowerLimit(), IntervalMark.LOWER));
			intervalLimits.add(new IntervalMark(interval, interval.upperLimit(), IntervalMark.UPPER));
		}
		
		Collections.sort(intervalLimits);
		
		// create the common queue
		SortedList<CalendarInterval> queue = new SortedList<>();
		
		while(!intervalLimits.isEmpty()) {
			IntervalMark limit = intervalLimits.remove(0);
			if (limit.isLower()) {
				queue.add(limit.getInterval());
			}
			else if (limit.isUpper()) {
				makeCutOrPop(queue, meet);
			}
		}
		
		return meet;
	}
	
	
	

	public String unparse() {
		String unparsed = "{";
		
		int size = intervals.size();
		for (int i = 0; i < size; i++) {
			if (i == size - 1) {
				unparsed += intervals.get(i).unparse();
			}
			else {
				unparsed += intervals.get(i).unparse();
				unparsed += INTERVAL_SEPARATOR;
			}
		}
		
		unparsed += "}";
		
		return unparsed;
    }
	
	
	public static CalendarIntervalList parse(String intervalListStr) throws IntervalListFormatException {
		if (!intervalListStr.startsWith("{") || !intervalListStr.endsWith("}")) 
			throw new IntervalListFormatException(intervalListStr);
		
		intervalListStr = intervalListStr.substring(1, intervalListStr.length() - 1);
		String[] intervalArrayStr = intervalListStr.split(INTERVAL_SEPARATOR);
		
		CalendarIntervalList intervalList = new CalendarIntervalList();
		
		for (int i = 0; i < intervalArrayStr.length; i++) {
			String intervalStr = intervalArrayStr[i];
			try {
				CalendarInterval interval = CalendarInterval.parse(intervalStr);
				intervalList.add(interval);
			} catch (IntervalFormatException e) {
				throw new IntervalListFormatException(intervalListStr, e);
			}
		}
		
		return intervalList;
	}
	
	
	public boolean isEqual(CalendarIntervalList otherList) {
		if (otherList.size() != intervals.size()) {
			return false;
		}
		
		for (int i = 0; i < intervals.size(); i++) {
			CalendarInterval thisInterval = intervals.get(i);
			CalendarInterval otherInterval = otherList.get(i);
			
			if (thisInterval != otherInterval) {
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public String toString() {
		return unparse();
	}
	
	
	private class IntervalMark implements Comparable<IntervalMark> {
		public static final int LOWER = 0;
		public static final int UPPER = 1;
		
		int type;
		Calendar limit;
		CalendarInterval interval;
		
		IntervalMark(CalendarInterval interval, Calendar timestamp, int type) {
			this.interval = interval;
			this.limit = timestamp;
			this.type = type;
		}
		
		public boolean isLower() {
			return type == LOWER;
		}
		
		public boolean isUpper() {
			return type == UPPER;
		}
		
		public CalendarInterval getInterval() {
			return interval;
		}
		
		public Calendar getLimit() {
			return limit;
		}

		@Override
		public int compareTo(IntervalMark other) {
			if (limit == null && other.getLimit() != null) {
				return -1;
			}
			
			if (limit != null && other.getLimit() == null) {
				return 1;
			}
			
			if (limit != null && other.getLimit() != null) {
				return limit.compareTo(other.getLimit());
			}
			
			return 0;
		}
	}
	
	
	private void makeCutOrPop(SortedList<CalendarInterval> queue, CalendarIntervalList meet) {
		if (queue.size() == 1) {
			queue.remove();
		}
		else if (queue.size() == 2) {
			CalendarInterval first = queue.pollFirst();
			CalendarInterval last = queue.pollLast();
			
			CalendarInterval intersection = CalendarInterval.fromInterval(first.intersect(last));
			meet.add(intersection);
			
			Calendar remainderLower = first.upperLimit();
			boolean includesLower = first.includesUpperLimit();
			
			Calendar remainderUpper = last.upperLimit();
			boolean includesUpper = last.includesUpperLimit();
			
			if (remainderLower.compareTo(remainderUpper) > 0) {
				Calendar aux = remainderLower;
				remainderLower = remainderUpper;
				remainderUpper = aux;
			}
			
			last = CalendarInterval.fromInterval(last.newOfSameType(remainderLower, includesLower, remainderUpper, includesUpper));
			
			queue.add(last);
		}
		else {
			System.out.println("WE SHOULD NOT SEE THIS !!!");
		}
	}
	
	
	public static void main(String[] args) {
		// test for [t1, t2] includes [t1, t2-10]
		Calendar t1 = Calendar.getInstance();
		Calendar t2 = (Calendar)t1.clone(); t2.add(Calendar.SECOND, 20);
		Calendar t3 = (Calendar)t1.clone(); t3.add(Calendar.SECOND, 10);
		
		CalendarInterval interval1 = new CalendarInterval(t1, true, t2, true);
		CalendarInterval interval2 = new CalendarInterval(t1, true, t3, true);
		
		CalendarIntervalList list1 = new CalendarIntervalList();
		CalendarIntervalList list2 = new CalendarIntervalList();
		
		list1.add(interval1);
		list2.add(interval2);
		
		System.out.println(list1.contains(list2));
		
		// test for [-inf, t2] includes [-inf, t1]
		interval1 = new CalendarInterval(null, false, null, false);
		interval2 = new CalendarInterval(null, false, t1, false);

		list1 = new CalendarIntervalList();
		list2 = new CalendarIntervalList();

		list1.add(interval1);
		list2.add(interval2);

		System.out.println(list1.contains(list2));
		
		// test for {[t1, t1+10], [t1+20, inf]} includes {[t1-20, t1-10], [t1-5, t1+5], [t1+15, t1+25], [t1+35, inf]}
		t2 = (Calendar)t1.clone(); t2.add(Calendar.SECOND, 10);
		t3 = (Calendar)t1.clone(); t3.add(Calendar.SECOND, 20);
		Calendar t4 = (Calendar)t1.clone(); t4.add(Calendar.SECOND, -20);
		Calendar t5 = (Calendar)t1.clone(); t5.add(Calendar.SECOND, -10);
		Calendar t6 = (Calendar)t1.clone(); t6.add(Calendar.SECOND, -5);
		Calendar t7 = (Calendar)t1.clone(); t7.add(Calendar.SECOND, 5);
		Calendar t8 = (Calendar)t1.clone(); t8.add(Calendar.SECOND, 15);
		Calendar t9 = (Calendar)t1.clone(); t9.add(Calendar.SECOND, 25);
		Calendar t10 = (Calendar)t1.clone(); t10.add(Calendar.SECOND, 35);
		
		interval1 = new CalendarInterval(t1, true, t2, true);
		interval2 = new CalendarInterval(t3, true, null, false);
		list1 = new CalendarIntervalList();
		list1.add(interval1); list1.add(interval2);
		
		
		CalendarInterval interval3 = new CalendarInterval(t4, true, t5, true);
		CalendarInterval interval4 = new CalendarInterval(t6, true, t7, true);
		CalendarInterval interval5 = new CalendarInterval(t8, true, t9, true);
		CalendarInterval interval6 = new CalendarInterval(t10, true, null, false);
		list2 = new CalendarIntervalList();
		list2.add(interval3); list2.add(interval4);
		list2.add(interval5); list2.add(interval6);

		System.out.println(list1.contains(list2));
		
		t2 = (Calendar)t1.clone(); t2.add(Calendar.SECOND, 10);
		t3 = (Calendar)t2.clone(); t3.add(Calendar.SECOND, 20); 
		t4 = (Calendar)t3.clone(); t4.add(Calendar.SECOND, 10);
		
		interval1 = new CalendarInterval(t1, true, t2, true);
		interval2 = new CalendarInterval(t3, true, t4, true);
		list1 = new CalendarIntervalList();
		list2 = new CalendarIntervalList();
		list1.add(interval1); 
		list2.add(interval2);
		
		System.out.println("CLOSE ENOUGH:" + list2.closeEnoughTo(list1, CalendarInterval.MAX_GAP_MILLIS));
	}
}
