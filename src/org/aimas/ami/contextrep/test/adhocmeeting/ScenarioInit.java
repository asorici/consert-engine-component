package org.aimas.ami.contextrep.test.adhocmeeting;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.model.ContextAssertion.ContextAssertionType;
import org.aimas.ami.contextrep.test.ContextEvent;
import org.aimas.ami.contextrep.utils.ContextModelUtils;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.vocabulary.RDF;

public class ScenarioInit {
	public static final String AD_HOC_MEETING_BASE = "http://pervasive.semanticweb.org/ont/2013/09/adhocmeeting/models";
	public static final String AD_HOC_MEETING_NS = AD_HOC_MEETING_BASE + "#";
	
	public static final String SCENARIO_BASE_URL = "http://pervasive.semanticweb.org/ont/2013/09/adhocmeeting/test";
	public static final String SCENARIO_NS = SCENARIO_BASE_URL + "#";
	
	public static final String ROOM_SECTION_ALEX_URI = SCENARIO_NS + "deskAlex";
	public static final String CAMERA1_URI = SCENARIO_NS + "cameraAlex1";
	public static final String MIC1_URI = SCENARIO_NS + "micAlex1";
	public static final String MIC2_URI = SCENARIO_NS + "micAlex2";
	
	public static OntModel initScenario(Dataset dataset, OntModel contextModel) {
		
		long timestamp = System.currentTimeMillis();
		
		// Create general store basic model
		OntModel generalStoreBasicModel = initGeneralStoreModel(contextModel);
		System.out.println("[INFO] Created basic general store model. Duration: " + 
				(System.currentTimeMillis() - timestamp) );
		timestamp = System.currentTimeMillis();
		//printStatements(generalStoreBasicModel);
		
		// Create RDFS closure of basic model
		
		OntModel generalStoreRDFSClosure = performRDFSClosure(generalStoreBasicModel, contextModel);
		generalStoreBasicModel.add(generalStoreRDFSClosure);
		
		System.out.println("[INFO] Performed general Store RDFS closure. Duration: " + 
				(System.currentTimeMillis() - timestamp) );
		timestamp = System.currentTimeMillis();
		//printStatements(generalStoreBasicModel);
		
		// Insert into TDB backed general store
		insertInitialGeneralStore(generalStoreBasicModel, dataset);
		System.out.println("[INFO] Insert into TDB backed general store. Duration: " + 
				(System.currentTimeMillis() - timestamp) );
		timestamp = System.currentTimeMillis();
		
		// insert initial ContextAssertions
		insertInitialContextAssertions(dataset, contextModel, generalStoreBasicModel);
		System.out.println("[INFO] Insert initial ContextAssertions. Duration: " + 
				(System.currentTimeMillis() - timestamp) );
		timestamp = System.currentTimeMillis();
		
		return generalStoreBasicModel;
	}
	
	
	private static OntModel initGeneralStoreModel(OntModel contextSchemaModel) {
		OntModel data = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
		
		OntClass roomSection = contextSchemaModel.getOntClass(AD_HOC_MEETING_NS + "BuildingRoomSection");
		OntClass kinectCamera = contextSchemaModel.getOntClass(AD_HOC_MEETING_NS + "KinectCamera");
		OntClass microphone = contextSchemaModel.getOntClass(AD_HOC_MEETING_NS + "Microphone");
		
		data.createIndividual(ROOM_SECTION_ALEX_URI, roomSection);
		data.createIndividual(CAMERA1_URI, kinectCamera);
		data.createIndividual(MIC1_URI, microphone);
		data.createIndividual(MIC2_URI, microphone);
		
		
		// if indeed the base model gets updated at inference, then we will have to retrieve that one
		// when we insert into the TDB dataset generalStore
		return data;
	}
	
	
	private static OntModel performRDFSClosure(OntModel contentModel, OntModel schemaModel) {
		InfModel infModel = ModelFactory.createRDFSModel(schemaModel, contentModel);
		OntModel filteredRDFSClosure = filterBlankNodes(contentModel, infModel);
		return filteredRDFSClosure;
	}
	
	
	private static OntModel filterBlankNodes(OntModel basicModel, InfModel closureModel) {
		ResIterator subjIt = basicModel.listSubjects();
		NodeIterator objIt = basicModel.listObjects();
		
		OntModel filteredModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
		
		for (;subjIt.hasNext();) {
			Resource subject = subjIt.next();
			if (subject.isURIResource() && basicModel.getIndividual(subject.getURI()) != null) {
				StmtIterator stIt = closureModel.listStatements(subject, null, (RDFNode)null);
				for (;stIt.hasNext();) {
					Statement s = stIt.next();
					
					if (!s.getObject().isAnon()) {
						filteredModel.add(s);
					}
				}
				
				stIt = closureModel.listStatements(null, null, subject);
				for (;stIt.hasNext();) {
					Statement s = stIt.next();
					if (!s.getSubject().isAnon()) {
						filteredModel.add(s);
					}
				}
			}
		}
		
		
		for (;objIt.hasNext();) {
			RDFNode object = objIt.next();
			if (object.isURIResource() && basicModel.getIndividual(object.asResource().getURI()) != null) {
				StmtIterator stIt = closureModel.listStatements(object.asResource(), null, (RDFNode)null);
				for (;stIt.hasNext();) {
					Statement s = stIt.next();
					
					if (!s.getObject().isAnon()) {
						filteredModel.add(s);
					}
				}
				
				stIt = closureModel.listStatements(null, null, object);
				for (;stIt.hasNext();) {
					Statement s = stIt.next();
					if (!s.getSubject().isAnon()) {
						filteredModel.add(s);
					}
				}
			}
		}
		
		return filteredModel;
	}
	
	
	private static void insertInitialGeneralStore(OntModel initialModel, Dataset dataset) {
		Model entityStore = dataset.getNamedModel(ConsertCore.ENTITY_STORE_URI);
		
		entityStore.add(initialModel);
		TDB.sync(entityStore);
	}
	
	
	private static void insertInitialContextAssertions(Dataset dataset, OntModel contextModel, OntModel baseModel) {
		OntProperty deviceRoomSection = contextModel.getOntProperty(AD_HOC_MEETING_NS + "deviceRoomSection");
		
		// ======== create identifier named graphs for the deviceRoomSection property of our 3 devices ========
		String camera1RoomSectionID = 
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, deviceRoomSection.getLocalName());
		
		String mic1RoomSectionID = 
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, deviceRoomSection.getLocalName());
		
		String mic2RoomSectionID = 
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, deviceRoomSection.getLocalName());
		
		// ======== create assertion triples ========
		Statement camera1Statement = ResourceFactory.createStatement(
			baseModel.getIndividual(CAMERA1_URI), deviceRoomSection, baseModel.getIndividual(ROOM_SECTION_ALEX_URI));
		
		Statement mic1Statement = ResourceFactory.createStatement(
			baseModel.getIndividual(MIC1_URI), deviceRoomSection, baseModel.getIndividual(ROOM_SECTION_ALEX_URI));
		
		Statement mic2Statement = ResourceFactory.createStatement(
			baseModel.getIndividual(MIC2_URI), deviceRoomSection, baseModel.getIndividual(ROOM_SECTION_ALEX_URI));
		
		// ======== insert assertions into dataset ========
		Model camera1Graph = dataset.getNamedModel(camera1RoomSectionID);
		camera1Graph.add(camera1Statement);
		
		Model mic1Graph = dataset.getNamedModel(mic1RoomSectionID);
		mic1Graph.add(mic1Statement);
		
		Model mic2Graph = dataset.getNamedModel(mic2RoomSectionID);
		mic2Graph.add(mic2Statement);
		
		TDB.sync(dataset);
		
		// ======== create annotations and insert them in the appropriate named graphs in the dataset ========
		String deviceRoomSectionGraphURI = ContextModelUtils.getAssertionStoreURI(deviceRoomSection.getURI());
		Model deviceRoomSectionGraph = dataset.getNamedModel(deviceRoomSectionGraphURI);
		
		Calendar timestamp = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		
		CalendarInterval interval = new CalendarInterval(null, true, null, true);  // valid forever [-INF,+INF]
		CalendarIntervalList validity = new CalendarIntervalList();
		validity.add(interval);
		
		List<Statement> camera1Annotations = 
			ContextModelUtils.createAnnotationStatements(camera1RoomSectionID, deviceRoomSection.getURI(), 
			ContextAssertionType.Profiled, timestamp, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI); 
		
		List<Statement> mic1Annotations = 
			ContextModelUtils.createAnnotationStatements(mic1RoomSectionID,  deviceRoomSection.getURI(),
			ContextAssertionType.Profiled, timestamp, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		
		List<Statement> mic2Annotations = 
			ContextModelUtils.createAnnotationStatements(mic2RoomSectionID, deviceRoomSection.getURI(), 
			ContextAssertionType.Profiled, timestamp, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		
		deviceRoomSectionGraph.add(camera1Annotations);
		deviceRoomSectionGraph.add(mic1Annotations);
		deviceRoomSectionGraph.add(mic2Annotations);
		
		TDB.sync(deviceRoomSectionGraph);
		
		/*
		// ======== create test annotations to run initial SPIN rule inference ========
		Calendar validFromSkeleton = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		validFromSkeleton.add(Calendar.MINUTE, -7);
		createSenseSkeletonSittingEvent(dataset, contextModel, baseModel, validFromSkeleton);
		
		Calendar validFromMic = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		validFromMic.add(Calendar.MINUTE, -3);
		createHasNoiseLevelEvent(dataset, contextModel, baseModel, validFromMic);
		*/
	}

	
	private static void createSenseSkeletonSittingEvent(Dataset dataset, OntModel contextModel, OntModel baseModel, 
			Calendar validFrom) {
		Resource bnode1 = ResourceFactory.createResource();
		Resource bnode2 = ResourceFactory.createResource();
		Resource bnode3 = ResourceFactory.createResource();
		
		
		OntClass assertionClass = contextModel.getOntClass(ScenarioInit.AD_HOC_MEETING_NS + "SensesSkelInPosition");
		OntClass skeletonClass = contextModel.getOntClass(ScenarioInit.AD_HOC_MEETING_NS + "Skeleton");
		
		Individual kinectCamera = baseModel.getIndividual(CAMERA1_URI);
		Individual skeleton1 = baseModel.createIndividual(SCENARIO_NS + "skeleton1", skeletonClass);
		Individual skeleton2 = baseModel.createIndividual(SCENARIO_NS + "skeleton2", skeletonClass);
		Individual skeleton3 = baseModel.createIndividual(SCENARIO_NS + "skeleton3", skeletonClass);
		
		String skeletonAssertionID1 = 
				ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, assertionClass.getLocalName());
		String skeletonAssertionID2 = 
				ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, assertionClass.getLocalName());
		String skeletonAssertionID3 = 
				ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, assertionClass.getLocalName());
		
		String sensesSkeletonGraphURI = ContextModelUtils.getAssertionStoreURI(assertionClass.getURI());
		Model sensesSkeletonGraph = dataset.getNamedModel(sensesSkeletonGraphURI);
		
		Individual sittingPosition = contextModel.getIndividual(ScenarioInit.AD_HOC_MEETING_NS + "sitting");
		OntProperty cameraRole = contextModel.getOntProperty(ScenarioInit.AD_HOC_MEETING_NS + "kinectCameraRole");
		OntProperty skeletonRole = contextModel.getOntProperty(ScenarioInit.AD_HOC_MEETING_NS + "skeletonRole");
		OntProperty skelPositionRole = contextModel.getOntProperty(ScenarioInit.AD_HOC_MEETING_NS + "skelPositionRole");
		
		Model skeleton1Graph = dataset.getNamedModel(skeletonAssertionID1);
		Model skeleton2Graph = dataset.getNamedModel(skeletonAssertionID2);
		Model skeleton3Graph = dataset.getNamedModel(skeletonAssertionID3);
		
		// create first assertion
		skeleton1Graph.add(bnode1, RDF.type, assertionClass);
		skeleton1Graph.add(bnode1, cameraRole, kinectCamera);
		skeleton1Graph.add(bnode1, skeletonRole, skeleton1);
		skeleton1Graph.add(bnode1, skelPositionRole, sittingPosition);
		
		// create second assertion
		skeleton2Graph.add(bnode2, RDF.type, assertionClass);
		skeleton2Graph.add(bnode2, cameraRole, kinectCamera);
		skeleton2Graph.add(bnode2, skeletonRole, skeleton2);
		skeleton2Graph.add(bnode2, skelPositionRole, sittingPosition);
		
		// create third assertion
		skeleton3Graph.add(bnode3, RDF.type, assertionClass);
		skeleton3Graph.add(bnode3, cameraRole, kinectCamera);
		skeleton3Graph.add(bnode3, skeletonRole, skeleton3);
		skeleton3Graph.add(bnode3, skelPositionRole, sittingPosition);
		
		TDB.sync(dataset);
		
		// create annotations 
		Calendar validTo = (Calendar)validFrom.clone();
		validTo.add(Calendar.SECOND, 600);
		CalendarInterval interval = new CalendarInterval(validFrom, true, validTo, true); 
		CalendarIntervalList validity = new CalendarIntervalList();
		validity.add(interval);
		
		List<Statement> skeleton1Annotations = ContextModelUtils.createAnnotationStatements(skeletonAssertionID1,
			assertionClass.getURI(), ContextAssertionType.Sensed, validFrom, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		
		List<Statement> skeleton2Annotations = ContextModelUtils.createAnnotationStatements(skeletonAssertionID2,
			assertionClass.getURI(), ContextAssertionType.Sensed, validFrom, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		
		List<Statement> skeleton3Annotations = ContextModelUtils.createAnnotationStatements(skeletonAssertionID3, 
			assertionClass.getURI(), ContextAssertionType.Sensed, validFrom, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
	
		sensesSkeletonGraph.add(skeleton1Annotations);
		sensesSkeletonGraph.add(skeleton2Annotations);
		sensesSkeletonGraph.add(skeleton3Annotations);
		
		TDB.sync(sensesSkeletonGraph);
	}
	
	
	private static void createHasNoiseLevelEvent(Dataset dataset, OntModel contextModel, OntModel baseModel, 
			Calendar validFrom) {
		Individual mic1 = baseModel.getIndividual(MIC1_URI);
		Individual mic2 = baseModel.getIndividual(MIC2_URI);
		
		OntProperty assertionProperty = contextModel.getOntProperty(ScenarioInit.AD_HOC_MEETING_NS + "hasNoiseLevel");
		Literal noiseLevel = ResourceFactory.createTypedLiteral(new Integer(80));
		
		String hasNoiseLevelID1 = 
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, assertionProperty.getLocalName());
		String hasNoiseLevelID2 = 
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, assertionProperty.getLocalName());
		
		String hasNoiseLevelGraphURI = ContextModelUtils.getAssertionStoreURI(assertionProperty.getURI());
		Model hasNoiseLevelGraph = dataset.getNamedModel(hasNoiseLevelGraphURI);
		
		Model hasNoiseLevel1Graph = dataset.getNamedModel(hasNoiseLevelID1);
		Model hasNoiseLevel2Graph = dataset.getNamedModel(hasNoiseLevelID2);
		
		// create assertions
		hasNoiseLevel1Graph.add(mic1, assertionProperty, noiseLevel);
		hasNoiseLevel2Graph.add(mic2, assertionProperty, noiseLevel);
		
		TDB.sync(dataset);
		
		// create annotations
		Calendar validTo = (Calendar) validFrom.clone();
		validTo.add(Calendar.SECOND, 600);
		CalendarInterval interval = new CalendarInterval(validFrom, true, validTo, true);
		CalendarIntervalList validity = new CalendarIntervalList();
		validity.add(interval);

		List<Statement> mic1Annotations = ContextModelUtils.createAnnotationStatements(hasNoiseLevelID1, 
			assertionProperty.getURI(), ContextAssertionType.Sensed, validFrom, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		
		List<Statement> mic2Annotations = ContextModelUtils.createAnnotationStatements(hasNoiseLevelID2,
			assertionProperty.getURI(), ContextAssertionType.Sensed, validFrom, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		
		hasNoiseLevelGraph.add(mic1Annotations);
		hasNoiseLevelGraph.add(mic2Annotations);
		
		TDB.sync(hasNoiseLevelGraph);
	}
	
	public static void printStatements(Model model) {
		StmtIterator stIt = model.listStatements();
		for (;stIt.hasNext();) {
			Statement s = stIt.next();
			System.out.println("Statement: " +
				s.getSubject() + " " + s.getPredicate() + " " + s.getObject());
		}
		
		System.out.println();
	}
	
	private static void printInferredStatements(Model base, InfModel inferred) {
		ResIterator subjIt = base.listSubjects();
		
		for (;subjIt.hasNext();) {
			Resource subject = subjIt.next();
			StmtIterator stIt = inferred.listStatements(subject, null, (RDFNode)null);
			for (;stIt.hasNext();) {
				Statement s = stIt.next();
				
				System.out.println("Statement: " + 
					s.getSubject() + " " + s.getPredicate() + " " + s.getObject());
				
				RDFNode obj = s.getObject();
				if (obj.isAnon() && obj.isResource()) {
					Resource res = obj.asResource();
					StmtIterator propIt = res.listProperties();
					for (;propIt.hasNext();) {
						Statement propStatement = propIt.next();
						System.out.println("OBJ prop.: " + propStatement);
					}
				}
			}
			
			stIt = inferred.listStatements(null, null, subject);
			for (;stIt.hasNext();) {
				Statement s = stIt.next();
				System.out.println("Statement: " + 
					s.getSubject() + " " + s.getPredicate() + " " + s.getObject());
			}
		}
		
		System.out.println();
	}

}
