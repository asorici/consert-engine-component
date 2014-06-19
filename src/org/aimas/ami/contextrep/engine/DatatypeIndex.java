package org.aimas.ami.contextrep.engine;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.datatype.CalendarIntervalListType;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.TypeMapper;

public class DatatypeIndex {
	private static Map<String, RDFDatatype> customDatatypes = new HashMap<String, RDFDatatype>();
	static {
		customDatatypes.put(CalendarIntervalListType.intervalListTypeURI, CalendarIntervalListType.intervalListType);
	}
	
	public static RDFDatatype getDatatype(String datatypeURI) {
		return customDatatypes.get(datatypeURI);
	}
	
	
	public static List<RDFDatatype> getDatatypes() {
		return new LinkedList<RDFDatatype>(customDatatypes.values());
	}
	
	
	public static void addDatatype(String datatypeURI, RDFDatatype datatype) {
		addDatatype(datatypeURI, datatype, false);
	}
	
	
	public static void addDatatype(String datatypeURI, RDFDatatype datatype, boolean register) {
		customDatatypes.put(datatypeURI, datatype);
		
		if (register) {
			TypeMapper.getInstance().registerDatatype(datatype);
		}
	}
	
	/**
	 * Register the contents of the data type index with the {@link TypeMapper} of the Jena API. 
	 */
	static void registerCustomDatatypes() {
		for (RDFDatatype rtype : getDatatypes()) {
			TypeMapper.getInstance().registerDatatype(rtype);
		}
	}
}
