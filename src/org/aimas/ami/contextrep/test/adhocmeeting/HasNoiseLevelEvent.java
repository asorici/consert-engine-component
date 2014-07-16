package org.aimas.ami.contextrep.test.adhocmeeting;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.test.ContextEvent;
import org.aimas.ami.contextrep.utils.ContextModelUtils;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.core.Quad;
import com.hp.hpl.jena.sparql.modify.request.QuadDataAcc;
import com.hp.hpl.jena.sparql.modify.request.UpdateCreate;
import com.hp.hpl.jena.sparql.modify.request.UpdateDataInsert;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateRequest;
import com.hp.hpl.jena.vocabulary.RDF;

public class HasNoiseLevelEvent extends ContextEvent {
	
	private Resource microphone;
	private int level;

	public HasNoiseLevelEvent(Calendar timestamp, int validity, Resource microphone, int level) {
		super(timestamp, validity);
		this.microphone = microphone;
		this.level = level;
		
		this.request = createUpdateRequest();
	}

	@Override
	public UpdateRequest createUpdateRequest() {
		UpdateRequest request = UpdateFactory.create() ;
		
		Property assertionProperty = ADHOC.hasNoiseLevel; 
		Literal noiseLevel = ResourceFactory.createTypedLiteral(new Integer(level));
		
		Node graphURINode = Node.createURI(ContextModelUtils.createUUID(assertionProperty));
		
		Quad q = Quad.create(graphURINode, microphone.asNode(), assertionProperty.asNode(), noiseLevel.asNode());
		
		QuadDataAcc data = new QuadDataAcc();
		data.addQuad(q);
		
		Update createUpdate = new UpdateCreate(graphURINode);
		Update assertionUpdate = new UpdateDataInsert(data);
		Update annotationUpdate = createAnnotationUpdate(graphURINode, getTimestamp(), validity);
		
		request.add(createUpdate);
		request.add(assertionUpdate);
		request.add(annotationUpdate);
		
		return request;
	}
	
	
	/**
	 * Create mock annotations for a ContextAssertion identified by <code>graphURINode</code>
	 * @param graphURINode Graph URI identifying the ContextAssertion
	 * @param timestamp Timestamp of the ContextAssertion
	 * @param validity Time validity in seconds
	 * @return an Update request inserting the mock annotations
	 */
	private Update createAnnotationUpdate(Node graphURINode, Calendar timestamp, int validity) {
		Property assertionProperty = ADHOC.hasNoiseLevel;
		
		Calendar validTo = Calendar.getInstance();
		validTo.setTimeInMillis(timestamp.getTimeInMillis() + validity * 1000);
		
		CalendarInterval interval = new CalendarInterval(timestamp, true, validTo, true);
		CalendarIntervalList intervalList = new CalendarIntervalList();
		intervalList.add(interval);
		
		// Create validity Literal
		Literal validityAnn = ResourceFactory.createTypedLiteral(intervalList);
		
		// Create timestamp Literal
		XSDDateTime xsdTimestamp = new XSDDateTime(timestamp);
		Literal timestampAnn = ResourceFactory.createTypedLiteral(xsdTimestamp);
		
		// Create accuracy Literal
		Literal accuracyAnn = ResourceFactory.createTypedLiteral(new Double(DEFAULT_ACCURACY));
		
		// Create source Literal
		Literal sourceAnn = ResourceFactory.createTypedLiteral(DEFAULT_SOURCE_URI, XSDDatatype.XSDanyURI);
		
		// create update quads
		Node storeURINode = Node.createURI(ContextModelUtils.getAssertionStoreURI(assertionProperty.getURI()));
		
		Property hasSource = ConsertAnnotation.HAS_SOURCE;
		Property hasTimestamp = ConsertAnnotation.HAS_TIMESTAMP;
		Property hasValidity = ConsertAnnotation.HAS_VALIDITY;
		Property hasCertainty = ConsertAnnotation.HAS_CERTAINTY;
		
		QuadDataAcc data = new QuadDataAcc();
		
		// source ann
		Resource srcRes = ResourceFactory.createResource();
		Quad qSrc1 = Quad.create(storeURINode, graphURINode, hasSource.asNode(), srcRes.asNode());
		Quad qSrc2 = Quad.create(storeURINode, srcRes.asNode(), RDF.type.asNode(), ConsertAnnotation.SOURCE_ANNOTATION.asNode());
		Quad qSrc3 = Quad.create(storeURINode, srcRes.asNode(), ConsertAnnotation.HAS_UNSTRUCTURED_VALUE.asNode(), sourceAnn.asNode());
		data.addQuad(qSrc1); data.addQuad(qSrc2); data.addQuad(qSrc3); 
		
		// timestamp ann
		Resource timestampRes = ResourceFactory.createResource();
		Quad qTimestamp1 = Quad.create(storeURINode, graphURINode, hasTimestamp.asNode(), timestampRes.asNode());
		Quad qTimestamp2 = Quad.create(storeURINode, timestampRes.asNode(), RDF.type.asNode(), ConsertAnnotation.DATETIME_TIMESTAMP.asNode());
		Quad qTimestamp3 = Quad.create(storeURINode, timestampRes.asNode(), ConsertAnnotation.HAS_STRUCTURED_VALUE.asNode(), timestampAnn.asNode());
		data.addQuad(qTimestamp1); data.addQuad(qTimestamp2); data.addQuad(qTimestamp3); 
		
		// validity ann
		Resource validityRes = ResourceFactory.createResource();
		Quad qValidity1 = Quad.create(storeURINode, graphURINode, hasValidity.asNode(), validityRes.asNode());
		Quad qValidity2 = Quad.create(storeURINode, validityRes.asNode(), RDF.type.asNode(), ConsertAnnotation.TEMPORAL_VALIDITY.asNode());
		Quad qValidity3 = Quad.create(storeURINode, validityRes.asNode(), ConsertAnnotation.HAS_STRUCTURED_VALUE.asNode(), validityAnn.asNode());
		data.addQuad(qValidity1); data.addQuad(qValidity2); data.addQuad(qValidity3); 
		
		// certainty ann
		Resource certaintyRes = ResourceFactory.createResource();
		Quad qCertainty1 = Quad.create(storeURINode, graphURINode, hasCertainty.asNode(), certaintyRes.asNode());
		Quad qCertainty2 = Quad.create(storeURINode, certaintyRes.asNode(), RDF.type.asNode(), ConsertAnnotation.NUMERIC_VALUE_CERTAINTY.asNode());
		Quad qCertainty3 = Quad.create(storeURINode, certaintyRes.asNode(), ConsertAnnotation.HAS_STRUCTURED_VALUE.asNode(), accuracyAnn.asNode());
		data.addQuad(qCertainty1); data.addQuad(qCertainty2); data.addQuad(qCertainty3); 
		
		Quad qAssertionType = Quad.create(storeURINode, graphURINode, ConsertCore.CONTEXT_ASSERTION_TYPE_PROPERTY.asNode(), 
				ConsertCore.TYPE_SENSED.asNode());
		data.addQuad(qAssertionType);
		
		Quad qAssertionRes = Quad.create(storeURINode, graphURINode, ConsertCore.CONTEXT_ASSERTION_RESOURCE.asNode(), 
				assertionProperty.asNode());
		data.addQuad(qAssertionRes);
		
		
		Update annotationUpdate = new UpdateDataInsert(data);
		return annotationUpdate;
	}
	
	@Override
	public String toString() {
		String response = "Event :: " + "hasNoiseLevel " + "mic: " + microphone + "," + "level: " + level; 
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		response += " timestamp: " + formatter.format(getTimestamp().getTime());
		return response;
	}
}
