package org.aimas.ami.contextrep.test;

import java.util.Calendar;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.test.adhocmeeting.ScenarioInit;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.core.Quad;
import com.hp.hpl.jena.sparql.modify.request.QuadDataAcc;
import com.hp.hpl.jena.sparql.modify.request.UpdateDataInsert;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateRequest;

public abstract class ContextEvent implements Comparable<ContextEvent> {
	public static double DEFAULT_ACCURACY = 1.0;
	public static final String DEFAULT_SOURCE_URI = ScenarioInit.AD_HOC_MEETING_NS + "room-coordinator";
	
	/**
	 * timestamp of event creation
	 */
	private Calendar timestamp;

	/**
	 * validity of the event in seconds
	 */
	protected int validity;
	
	protected double accuracy;
	
	protected String assertedByURI;
	
	/**
	 * update request to be executed
	 */
	protected UpdateRequest request;
	
	
	public ContextEvent(Calendar timestamp, int validity, double accuracy, String assertedByURI) {
		this.timestamp = timestamp;
		this.validity = validity;
		this.accuracy = accuracy;
		this.assertedByURI = assertedByURI;
	}
	
	public ContextEvent(Calendar timestamp, int validity) {
		// this.dataset = dataset;
		this(timestamp, validity, DEFAULT_ACCURACY, DEFAULT_SOURCE_URI);
	}
	
	
	@Override
	public int compareTo(ContextEvent other) {
		return getTimestamp().compareTo(other.getTimestamp());
	}
	
	protected abstract UpdateRequest createUpdateRequest();
	
	
	public UpdateRequest getUpdateRequest() {
		return request;
	}

	/**
	 * @return the timestamp
	 */
    public Calendar getTimestamp() {
	    return timestamp;
    }
    
    
    public void setTimestamp(Calendar timestamp) {
		this.timestamp = timestamp;
		this.request = createUpdateRequest();
	}
}
