/**
 * Copyright (c) 2004 Domain Language, Inc. (http://domainlanguage.com) This
 * free software is distributed under the "MIT" licence. See file licence.txt.
 * For more information, see http://timeandmoney.sourceforge.net.
 */

package org.aimas.ami.contextrep.datatype;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class IntervalList<T extends Interval> {
    protected List<T> intervals;

    public IntervalList() {
        intervals = new ArrayList<T>();
    }
    
    public Iterator<T> iterator() {
        return intervals.iterator();
    }
    
    public List<T> asList() {
    	return intervals;
    }
    
    public T get(int index) {
    	return intervals.get(index);
    }
    
    public void insertAt(int index, T interval) {
    	intervals.add(index, interval);
    }
    
    public T remove(int index) {
    	return intervals.remove(index);
    }
    
    public boolean remove(T interval) {
    	return intervals.remove(interval);
    }
    
    public int size() {
    	return intervals.size();
    }
    
    public void add(T interval) {
        intervals.add(interval);
        Collections.sort(intervals);
    }
    
    public void addAll(List<T> addedIntervals) {
    	intervals.addAll(addedIntervals);
    	Collections.sort(intervals);
    }
    
    
    public boolean isEmpty() {
        return intervals.isEmpty();
    }
    
    
    public IntervalList<T> gaps() {
        IntervalList<T> gaps = new IntervalList<T>();
        if (intervals.size() < 2)
            return new IntervalList<T>();
        
        for (int i = 1; i < intervals.size(); i++) {
            T left = intervals.get(i - 1);
            T right = intervals.get(i);
            T gap = (T)left.gap(right);
            
            if (!gap.isEmpty())
                gaps.add(gap);
        }
        
        return gaps;
    }

    public T extent() {
        if (intervals.isEmpty())
            return null;
        
        if (intervals.size() == 1)
            return intervals.get(0);
        
        T left = intervals.get(0);
        T right =  intervals.get(intervals.size() - 1);
        return (T)left.newOfSameType(left.lowerLimit(), left.includesLowerLimit(), right.upperLimit(), right.includesUpperLimit());
    }
    
}
