package org.aimas.ami.contextrep.engine.execution;

import java.util.Calendar;

import org.aimas.ami.contextrep.engine.api.ConstraintResolutionService;
import org.aimas.ami.contextrep.engine.api.ContextStore;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.aimas.ami.contextrep.model.ViolationAssertionWrapper;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.rdf.model.RDFNode;

public class PreferNewestConstraintResolution implements ConstraintResolutionService {
	private static PreferNewestConstraintResolution instance;
	
	private PreferNewestConstraintResolution() {}
	
	@Override
	public ViolationAssertionWrapper resolveViolation(ContextConstraintViolation constraintViolation, ContextStore contextStore) {
		// STEP 1: access the type and instance UUID for each conflicting ContextAssertion
		// Since this is used as default for Integrity and Uniqueness constraint violations, we know there are 2 ContextAssertion instances
		ContextAssertion firstAssertion = constraintViolation.getViolatingAssertions()[0].getAssertion();
		String firstAssertionUUID = constraintViolation.getViolatingAssertions()[0].getAssertionInstanceURI();
		
		ContextAssertion secondAssertion = constraintViolation.getViolatingAssertions()[1].getAssertion();
		String secondAssertionUUID = constraintViolation.getViolatingAssertions()[1].getAssertionInstanceURI();
		
		// STEP 2: access the timestamp annotation of each conflicting ContextAssertion
		// If one of the assertions does not have a timestamp annotation, return the other one as the one to keep. If both don't have it, return null
		RDFNode firstTimestampValNode = contextStore.getStructuredAnnotationValue(firstAssertion.getOntologyResource(), firstAssertionUUID, ConsertAnnotation.HAS_TIMESTAMP);
		RDFNode secondTimestampValNode = contextStore.getStructuredAnnotationValue(secondAssertion.getOntologyResource(), secondAssertionUUID, ConsertAnnotation.HAS_TIMESTAMP);
		
		if (firstTimestampValNode != null && secondTimestampValNode == null) {
			return constraintViolation.getViolatingAssertions()[0];
		}
		else if (firstTimestampValNode == null && secondTimestampValNode != null) {
			return constraintViolation.getViolatingAssertions()[1];
		}
		else if (firstTimestampValNode == null && secondTimestampValNode == null) {
			return null;
		}
		
		// STEP 3: compare the timestamps and keep the more recent assertion
		XSDDateTime firstTimestampVal = (XSDDateTime)firstTimestampValNode.asLiteral().getValue();
		Calendar firstTimestamp = firstTimestampVal.asCalendar();
		
		XSDDateTime secondTimestampVal = (XSDDateTime)secondTimestampValNode.asLiteral().getValue();
		Calendar secondTimestamp = secondTimestampVal.asCalendar();
		
		if (firstTimestamp.after(secondTimestamp)) {
			return constraintViolation.getViolatingAssertions()[0];
		}
		else {
			return constraintViolation.getViolatingAssertions()[1];
		}
	}
	
	public static PreferNewestConstraintResolution getInstance() {
		if (instance == null) {
			instance = new PreferNewestConstraintResolution();
		}
		
		return instance;
	}
}
