package org.aimas.ami.contextrep.test.adhocmeeting;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.aimas.ami.contextrep.datatype.CalendarInterval;
import org.aimas.ami.contextrep.datatype.CalendarIntervalList;
import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.api.InsertionHandler;
import org.aimas.ami.contextrep.engine.api.QueryHandler;
import org.aimas.ami.contextrep.engine.api.QueryResult;
import org.aimas.ami.contextrep.engine.api.QueryResultNotifier;
import org.aimas.ami.contextrep.model.ContextAssertion.ContextAssertionType;
import org.aimas.ami.contextrep.model.ContextModelUtils;
import org.aimas.ami.contextrep.test.ContextEvent;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.osgi.service.component.ComponentContext;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.core.Quad;
import com.hp.hpl.jena.sparql.modify.request.QuadDataAcc;
import com.hp.hpl.jena.sparql.modify.request.UpdateDataInsert;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateRequest;
import com.hp.hpl.jena.vocabulary.RDF;

@Component(
	name = "adhocmeeting-test",
	immediate = true
)
public class ScenarioRunner {
	private static final int ADVANCE_MIN_SKEL = -7;
	private static final int ADVANCE_MIN_MIC = -3;
	private static final int DELAY_MILLIS = 1000;
	
	
	public ScenarioRunner() {}
	
	@Reference(
		cardinality = ReferenceCardinality.MANDATORY_UNARY,
		policy = ReferencePolicy.STATIC
	)
	private volatile InsertionHandler engineInsertionHandler;
	
	@Reference(
		cardinality = ReferenceCardinality.MANDATORY_UNARY,
		policy = ReferencePolicy.STATIC
	)
	private volatile QueryHandler engineQueryHandler;
	
	
	@Activate
	protected void activate(ComponentContext componentContext) {
		// In theory it means our dependencies are satisfied, i.e. we have the references
		// to the CONSERT Engine component for both insertion and query handlers. We can start planning the
		// scenario
		UpdateRequest bootstrapRequest = createScenarioBootstrap();
		List<ContextEvent> scenarioEvents = createScenarioEvents();
		Query scenarioQuery = createScenarioQuery();
		
		// STEP 1: make bootstrap insert
		InsertionHandler currentInsertionHandler = engineInsertionHandler;
		if (currentInsertionHandler != null) {
			Future<?> insertResult = currentInsertionHandler.bulkInsert(bootstrapRequest);
			try {
	            insertResult.get();
            }
            catch (InterruptedException e) {
	            e.printStackTrace();
            }
            catch (ExecutionException e) {
	            e.printStackTrace();
            }
		}
		else {
			System.out.println("INSERTION HANDLER WHY U NO WORK!");
		}
		
		// STEP 2: place the query
		QueryHandler currentQueryHandler = engineQueryHandler;
		if (currentQueryHandler != null) {
			QuerySolutionMap bindings = new QuerySolutionMap();
			QueryResultNotifier subscribeNotifier = new QueryResultNotifier() {
				
				@Override
				public void notifyQueryResult(QueryResult result) {
					if (result.hasError()) {
						System.out.println("[QUERY] Received subscription notification with error.");
						result.getError().printStackTrace();
					}
					else {
						System.out.println("[QUERY] Received subscription notification with results: ");
						
						ResultSet resultSet = result.getResultSet();
						while (resultSet.hasNext()) {
							QuerySolution sol = resultSet.nextSolution();
							System.out.println("AdHoc Meeting Room: " + sol.get("room"));
						}
					}
				}
				
				@Override
				public void notifyAskResult(QueryResult result) {
					System.out.println("WE SHOULD NOT BE GETTING RESULTS HERE!");
				}
			};
			
			currentQueryHandler.subscribe(scenarioQuery, bindings, subscribeNotifier);
		}
		else {
			System.out.println("QUERY HANDLER WHY U NO WORK!");
		}
		
		// STEP 3: run the events
		List<Future<InsertResult>> eventResults = new LinkedList<Future<InsertResult>>();
		while (!scenarioEvents.isEmpty()) {
			Calendar nowSkel = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			Calendar nowMic = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			nowSkel.add(Calendar.MINUTE, ADVANCE_MIN_SKEL);
			nowMic.add(Calendar.MINUTE, ADVANCE_MIN_MIC);
			
			int nrEvents = scenarioEvents.size();
			//System.out.println("Number of events: " + nrEvents);
			
			for (int i = 0; i < nrEvents; i++) {
				ContextEvent event = scenarioEvents.get(i);
				if (event instanceof SenseSkeletonSittingEvent && event.getTimestamp().after(nowSkel)) {
					break;
				}
				else if (event instanceof HasNoiseLevelEvent && event.getTimestamp().after(nowMic)) {
					break;
				}
				else {
					scenarioEvents.remove(i);
					i--;
					nrEvents--;
					
					// wrap event for execution and send it to insert executor
					System.out.println("GENERATING EVENT: " + event);
					InsertionHandler eventInsertionHandler = engineInsertionHandler;
					if (eventInsertionHandler != null) {
						eventResults.add(eventInsertionHandler.insert(event.getUpdateRequest()));
					}
				}
			}
			
			try {
				Thread.sleep(DELAY_MILLIS);
			} 
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Deactivate
	protected void deactivate(ComponentContext componentContext) {
		
	}
	
	
	
	private UpdateRequest createScenarioBootstrap() {
		Update entityStoreUpdate = buildEntityStoreUpdate();
		Update initialAssertionUpdate = buildInitialAssertionsUpdates();
		
		UpdateRequest bootstrapRequest = new UpdateRequest();
		bootstrapRequest.add(entityStoreUpdate);
		bootstrapRequest.add(initialAssertionUpdate);
		
	    return bootstrapRequest;
    }
	
	
	private List<ContextEvent> createScenarioEvents() {
		List<ContextEvent> events = new ArrayList<>();
		
		Resource cameraAlex = ADHOC.CAMERA1;
		Resource mic1 = ADHOC.MIC1;
		Resource mic2 = ADHOC.MIC2;
		
		int gap = 2;
		Calendar timestampInit = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		
		// round 1
		Calendar timestampSkel1 = (Calendar)timestampInit.clone();
		timestampSkel1.add(Calendar.MINUTE, ADVANCE_MIN_SKEL);
		timestampSkel1.add(Calendar.SECOND, gap);
		Resource skeleton1 = ADHOC.SKELETON1;
		
		ContextEvent event1 = new SenseSkeletonSittingEvent(timestampSkel1, 600, cameraAlex, skeleton1);
		events.add(event1);
		
		// round 2
		gap = 5;
		Calendar timestampSkel2 = (Calendar)timestampSkel1.clone();
		timestampSkel2.add(Calendar.SECOND, gap);
		Resource skeleton2 = ADHOC.SKELETON2;
		ContextEvent event2 = new SenseSkeletonSittingEvent(timestampSkel2, 600, cameraAlex, skeleton1);
		ContextEvent event3 = new SenseSkeletonSittingEvent(timestampSkel2, 600, cameraAlex, skeleton2);
		
		Calendar timestampMic2 = (Calendar)timestampInit.clone();
		timestampMic2.add(Calendar.MINUTE, ADVANCE_MIN_MIC);
		timestampMic2.add(Calendar.SECOND, gap);
		ContextEvent event4 = new HasNoiseLevelEvent(timestampMic2, 600, mic1, 80);
		ContextEvent event5 = new HasNoiseLevelEvent(timestampMic2, 600, mic2, 80);
		events.add(event2); events.add(event3); events.add(event4); events.add(event5);
		
		// round 3
		gap = 5;
		Calendar timestampSkel3 = (Calendar)timestampSkel2.clone();
		timestampSkel3.add(Calendar.SECOND, gap);
		Resource skeleton3 = ADHOC.SKELETON3;
		ContextEvent event6 = new SenseSkeletonSittingEvent(timestampSkel3, 600, cameraAlex, skeleton1);
		ContextEvent event7 = new SenseSkeletonSittingEvent(timestampSkel3, 600, cameraAlex, skeleton2);
		ContextEvent event8 = new SenseSkeletonSittingEvent(timestampSkel3, 600, cameraAlex, skeleton3);
		
		Calendar timestampMic3 = (Calendar)timestampMic2.clone();
		timestampMic3.add(Calendar.SECOND, gap);
		ContextEvent event9 = new HasNoiseLevelEvent(timestampMic3, 600, mic1, 80);
		ContextEvent event10 = new HasNoiseLevelEvent(timestampMic3, 600, mic2, 80);
		
		events.add(event6); events.add(event7); events.add(event8); events.add(event9); events.add(event10);
		
		//ContextEvent stopEvent = new StopEvent(contextModel, timestampMic3, 600);
		//events.add(stopEvent);
		
		return events;
    }
	
	
	private Query createScenarioQuery() {
		String queryString = ""
	    		+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" + "\n"
	    		+ "PREFIX ctxmod: <http://pervasive.semanticweb.org/ont/2013/09/adhocmeeting/models#>" + "\n"
	    		+ "PREFIX ctxcore: <http://pervasive.semanticweb.org/ont/2014/05/consert/core#>" + "\n"
	    		+ "SELECT ?room" + "\n"
	    		+ "WHERE {" + "\n"
	    		+ "	GRAPH ?assertionID {" + "\n"
	    		+ "		[] 	rdf:type ctxmod:HostsAdHocMeeting;" + "\n"
	    		+ "			ctxcore:assertionRole ?room." + "\n"
	    		+ "	}" + "\n"
	    		+ "}";
		    
	    Query subscribeQuery = QueryFactory.create(queryString);
	    
		return subscribeQuery;
    }
	
	
	private Update buildEntityStoreUpdate() {
		Node entityStoreNode = Node.createURI(ConsertCore.ENTITY_STORE_URI);
		
		QuadDataAcc data = new QuadDataAcc();
		
		data.addQuad(Quad.create(entityStoreNode, ADHOC.DESK_ALEX.asNode(), RDF.type.asNode(), ADHOC.BUILDING_ROOM_SECTION.asNode()));
		data.addQuad(Quad.create(entityStoreNode, ADHOC.CAMERA1.asNode(), RDF.type.asNode(), ADHOC.KINECT_CAMERA.asNode()));
		data.addQuad(Quad.create(entityStoreNode, ADHOC.MIC1.asNode(), RDF.type.asNode(), ADHOC.MICROPHONE.asNode()));
		data.addQuad(Quad.create(entityStoreNode, ADHOC.MIC2.asNode(), RDF.type.asNode(), ADHOC.MICROPHONE.asNode()));
		
		return new UpdateDataInsert(data);
	}
	
	
	private Update buildInitialAssertionsUpdates() {
		Property deviceRoomSection = ADHOC.deviceRoomSection;
		
		// ======== create identifier named graphs for the deviceRoomSection property of our 3 devices ========
		Node camera1RoomSectionID = Node.createURI( 
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, deviceRoomSection.getLocalName()));
		
		Node mic1RoomSectionID = Node.createURI(
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, deviceRoomSection.getLocalName()));
		
		Node mic2RoomSectionID = Node.createURI(
			ContextModelUtils.createUUID(ScenarioInit.AD_HOC_MEETING_BASE, deviceRoomSection.getLocalName()));
		

		QuadDataAcc roomSectionData = new QuadDataAcc();
		
		// create the device room section content
		roomSectionData.addQuad(
			Quad.create(camera1RoomSectionID, ADHOC.CAMERA1.asNode(), deviceRoomSection.asNode(), ADHOC.DESK_ALEX.asNode()));
		roomSectionData.addQuad(
			Quad.create(mic1RoomSectionID, ADHOC.MIC1.asNode(), deviceRoomSection.asNode(), ADHOC.DESK_ALEX.asNode()));
		roomSectionData.addQuad(
			Quad.create(mic2RoomSectionID, ADHOC.MIC2.asNode(), deviceRoomSection.asNode(), ADHOC.DESK_ALEX.asNode()));
		
		
		
		// create the device room section annotations
		Node deviceRoomSectionStore = Node.createURI(ContextModelUtils.getAssertionStoreURI(deviceRoomSection.getURI()));
		
		Calendar timestamp = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		CalendarInterval interval = new CalendarInterval(null, true, null, true);  // valid forever [-INF,+INF]
		CalendarIntervalList validity = new CalendarIntervalList();
		validity.add(interval);
		
		List<Statement> camera1Annotations = 
			ContextModelUtils.createAnnotationStatements(camera1RoomSectionID.getURI(),
			ContextAssertionType.Profiled, timestamp, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI); 
		for (Statement s : camera1Annotations) {
			roomSectionData.addQuad(Quad.create(deviceRoomSectionStore, s.asTriple()));
		}
		
		List<Statement> mic1Annotations = 
			ContextModelUtils.createAnnotationStatements(mic1RoomSectionID.getURI(), 
			ContextAssertionType.Profiled, timestamp, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		for (Statement s : mic1Annotations) {
			roomSectionData.addQuad(Quad.create(deviceRoomSectionStore, s.asTriple()));
		}
		
		List<Statement> mic2Annotations = 
			ContextModelUtils.createAnnotationStatements(mic2RoomSectionID.getURI(), 
			ContextAssertionType.Profiled, timestamp, validity, 
			ContextEvent.DEFAULT_ACCURACY, ContextEvent.DEFAULT_SOURCE_URI);
		for (Statement s : mic2Annotations) {
			roomSectionData.addQuad(Quad.create(deviceRoomSectionStore, s.asTriple()));
		}
		
		
		return new UpdateDataInsert(roomSectionData);
	}
}
