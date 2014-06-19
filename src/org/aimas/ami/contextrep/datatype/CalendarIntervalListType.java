package org.aimas.ami.contextrep.datatype;

import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.datatypes.BaseDatatype;
import com.hp.hpl.jena.datatypes.DatatypeFormatException;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.graph.impl.LiteralLabel;

public class CalendarIntervalListType extends BaseDatatype {
	public static final String intervalListTypeURI = ConsertCore.NS + "intervalListType";
    public static final RDFDatatype intervalListType = new CalendarIntervalListType();
	
	
	/** private constructor - single global instance */
    private CalendarIntervalListType() {
        super(intervalListTypeURI);
    }
	
    @Override
    public Class<?> getJavaClass() { 
    	return CalendarIntervalList.class; 
    }
    
	/**
     * Convert a value of a calendar interval list out to lexical form.
     */
    @Override
    public String unparse(Object value) {
    	CalendarIntervalList intervalList = (CalendarIntervalList) value;
        return intervalList.unparse();
    }

    /**
     * Parse a lexical form of a calendar interval list to a value
     * @throws DatatypeFormatException if the lexical form is not legal
     */
    @Override
    public Object parse(String lexicalForm) throws DatatypeFormatException {
        try{
        	return CalendarIntervalList.parse(lexicalForm);
        } catch (IntervalListFormatException e) {
            throw new DatatypeFormatException(lexicalForm, intervalListType, e.getMessage());
        }
    }

    /**
     * Compares two instances of values of the given datatype.
     * This does not allow CalendarIntervalLists to be compared to other number
     * formats, Lang tag is not significant.
     */
    public boolean isEqual(LiteralLabel value1, LiteralLabel value2) {
        if (value1.getDatatype() != value2.getDatatype()) 
        	return false;
        
        CalendarIntervalList list1 = (CalendarIntervalList) value1.getValue();
        CalendarIntervalList list2 = (CalendarIntervalList) value2.getValue();
        
    	return list1.isEqual(list2);
    }

}
