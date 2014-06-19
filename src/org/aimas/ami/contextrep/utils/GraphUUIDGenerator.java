package org.aimas.ami.contextrep.utils;

import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.shared.uuid.JenaUUID;
import com.hp.hpl.jena.shared.uuid.UUID_V4_Gen;

public class GraphUUIDGenerator {
	static {
		JenaUUID.setFactory(new UUID_V4_Gen());
	}
	
	public static String createUUID(String baseURI, String assertionName) {
		JenaUUID uuid = JenaUUID.generate();
		return baseURI + "/" + assertionName + "-" + uuid.asString();
	}
	
	public static String createUUID(OntResource res) {
		String resURI = res.getURI();
		resURI = resURI.replaceAll("#", "/");
		
		JenaUUID uuid = JenaUUID.generate();
		return resURI + "-" + uuid.asString();
	}
}
