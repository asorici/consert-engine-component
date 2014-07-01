package org.aimas.ami.contextrep.test.adhocmeeting;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

public class ADHOC {
	/** <p>The RDF model that holds the vocabulary terms</p> */
    private static Model m_model = ModelFactory.createDefaultModel();
	
	public static final String BASE_URI = "http://pervasive.semanticweb.org/ont/2013/09/adhocmeeting/models";
	public static final String NS = BASE_URI + "#";
	
	public static final String TEST_BASE_URI = "http://pervasive.semanticweb.org/ont/2013/09/adhocmeeting/test";
	public static final String TEST_NS = TEST_BASE_URI + "#";
	
	// =============== Classes ===============
	public final static Resource BUILDING_ROOM_SECTION = m_model.createResource( NS + "BuildingRoomSection" );
    public final static Resource KINECT_CAMERA = m_model.createResource( NS + "KinectCamera" );
	public final static Resource MICROPHONE = m_model.createResource( NS + "Microphone" );
	
	public final static Resource SENSES_SKELETON = m_model.createResource( NS + "SensesSkelInPosition" );
	public final static Resource SKELETON = m_model.createResource( NS + "Skeleton" );
	public final static Resource HOSTS_ADHOC_MEETING = m_model.createResource( NS + "HostsAdHocMeeting" );
	
	// =============== Properties ===============
	public final static Property hasNoiseLevel = m_model.createProperty( NS + "hasNoiseLevel" );
	public final static Property deviceRoomSection = m_model.createProperty( NS + "deviceRoomSection" );
	public final static Property kinectCameraRole = m_model.createProperty( NS + "kinectCameraRole" );
	public final static Property skeletonRole = m_model.createProperty( NS + "skeletonRole" );
	public final static Property skelPositionRole = m_model.createProperty( NS + "skelPositionRole" );
	
	// =============== Individuals ===============
	public final static Resource SITTING_POSITION = m_model.createResource( NS + "sitting" );
	
	public final static Resource DESK_ALEX = m_model.createResource( TEST_NS + "deskAlex" );
	public final static Resource CAMERA1 = m_model.createResource( TEST_NS + "cameraAlex1" );
	public final static Resource MIC1 = m_model.createResource( TEST_NS + "micAlex1" );
	public final static Resource MIC2 = m_model.createResource( TEST_NS + "micAlex2" );
	
	public final static Resource SKELETON1 = m_model.createResource( TEST_NS + "skeleton1" );
	public final static Resource SKELETON2 = m_model.createResource( TEST_NS + "skeleton2" );
	public final static Resource SKELETON3 = m_model.createResource( TEST_NS + "skeleton3" );
}
