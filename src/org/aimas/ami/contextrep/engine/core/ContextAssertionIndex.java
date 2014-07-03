package org.aimas.ami.contextrep.engine.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextAssertion.ContextAssertionType;
import org.aimas.ami.contextrep.model.impl.ContextAssertionImpl;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.topbraid.spin.model.SPINFactory;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

public class ContextAssertionIndex {
	
	private List<ContextAssertion> staticContextAssertions;
	private List<ContextAssertion> profiledContextAssertions;
	private List<ContextAssertion> sensedContextAssertions;
	private List<ContextAssertion> derivedContextAssertions;
	
	private Map<OntResource, ContextAssertion> assertionInfoMap;
	private Map<String, ContextAssertion> graphURIBase2AssertionMap;
	
	ContextAssertionIndex() {
		staticContextAssertions = new ArrayList<>();
		profiledContextAssertions = new ArrayList<>();
		sensedContextAssertions = new ArrayList<>();
		derivedContextAssertions = new ArrayList<>();
		
		assertionInfoMap = new HashMap<>();
		graphURIBase2AssertionMap = new HashMap<>();
	}

	public List<ContextAssertion> getStaticContextAssertions() {
		return staticContextAssertions;
	}

	public List<ContextAssertion> getProfiledContextAssertions() {
		return profiledContextAssertions;
	}

	public List<ContextAssertion> getSensedContextAssertions() {
		return sensedContextAssertions;
	}

	public List<ContextAssertion> getDerivedContextAssertions() {
		return derivedContextAssertions;
	}
	
	public List<ContextAssertion> getContextAssertions() {
		List<ContextAssertion> allAssertions = new ArrayList<>();
		allAssertions.addAll(staticContextAssertions);
		allAssertions.addAll(sensedContextAssertions);
		allAssertions.addAll(profiledContextAssertions);
		allAssertions.addAll(derivedContextAssertions);
		
		return allAssertions;
	}
	
	public Map<OntResource, ContextAssertion> getAssertionInfoMap() {
		return assertionInfoMap;
	}
	
	public Map<String, ContextAssertion> getGraphURIBase2AssertionMap() {
		return graphURIBase2AssertionMap;
	}
	
	/*
	public String getStoreForAssertion(OntResource assertionResource) {
		if (assertionResource != null) {
			return assertionInfoMap.get(assertionResource);
		}
		
		return null;
	}
	*/
	
	public ContextAssertion getAssertionFromResource(OntResource assertionResource) {
		return assertionInfoMap.get(assertionResource);
	}
	
	public ContextAssertion getAssertionFromGraphStore(Node graphNode) {
		// this works because of the way in which we create the 
		// named graph stores for a particular ContextAssertion
		String graphURI = graphNode.getURI();
		int storeSuffixIndex = graphURI.lastIndexOf("Store");
		
		if (storeSuffixIndex > 0) {
			String graphStoreBase = graphURI.substring(0, storeSuffixIndex);
			return graphURIBase2AssertionMap.get(graphStoreBase);
		}
		
		return null;
	}
	
	
	public ContextAssertion getAssertionFromGraphUUID(Node graphUUIDNode) {
		// this works because of the way in which we generate the assertion 
		// named graph UUIDs
		
		String graphUUID = graphUUIDNode.getURI();
		int firstHyphenIndex = graphUUID.indexOf("-");
		
		if (firstHyphenIndex > 0) {
			String graphUUIDBase = graphUUID.substring(0, firstHyphenIndex);
			return graphURIBase2AssertionMap.get(graphUUIDBase);
		}
		
		return null;
	}
	
	
	public boolean isContextAssertionUUID(Node graphNode) {
		return getAssertionFromGraphUUID(graphNode) != null;
	}
	
	
	public boolean isContextStore(Node graphNode) {
		String graphURI = graphNode.getURI();
		
		if (graphURI.equalsIgnoreCase(ConsertCore.ENTITY_STORE_URI)) {
			return true;
		}
		
		return isContextAssertionStore(graphNode);
	}
	
	
	public boolean isContextAssertionStore(Node graphNode) {
		String graphURI = graphNode.getURI();
		int storeSuffixIndex = graphURI.lastIndexOf("Store");
		
		if (storeSuffixIndex > 0) {
			String graphStoreBase = graphURI.substring(0, storeSuffixIndex);
			return graphURIBase2AssertionMap.containsKey(graphStoreBase);
		}
		
		return false;
	}
	
	
	public boolean containsAssertion(OntResource assertion) {
		return assertionInfoMap.containsKey(assertion);
	}
	
	
	public boolean containsAssertion(ContextAssertion assertion) {
		if (staticContextAssertions.contains(assertion)) {
			return true;
		}
		
		if (profiledContextAssertions.contains(assertion)) {
			return true;
		}
		
		if (sensedContextAssertions.contains(assertion)) {
			return true;
		}
		
		if (derivedContextAssertions.contains(assertion)) {
			return true;
		}
		
		return false;
	}
	
	
	public void addStaticContextAssertion(ContextAssertion staticAssertion) {
		staticContextAssertions.add(staticAssertion);
		assertionInfoMap.put(staticAssertion.getOntologyResource(), staticAssertion);
	}
	
	public void addProfiledContextAssertion(ContextAssertion profiledAssertion) {
		profiledContextAssertions.add(profiledAssertion);
		assertionInfoMap.put(profiledAssertion.getOntologyResource(), profiledAssertion);
	}
	
	public void addSensedContextAssertion(ContextAssertion sensedAssertion) {
		sensedContextAssertions.add(sensedAssertion);
		assertionInfoMap.put(sensedAssertion.getOntologyResource(), sensedAssertion);
	}
	
	public void addDerivedContextAssertion(ContextAssertion derivedAssertion) {
		derivedContextAssertions.add(derivedAssertion);
		assertionInfoMap.put(derivedAssertion.getOntologyResource(), derivedAssertion);
	}
	
	
	public void mapAssertionStorage(ContextAssertion assertion) {
		String assertionURI = assertion.getOntologyResource().getURI();
		assertionURI = assertionURI.replaceAll("#", "/");
		
		//String assertionStoreURI = assertionURI + "Store";
		//assertionInfoMap.put(assertion, assertionStoreURI);
		
		graphURIBase2AssertionMap.put(assertionURI, assertion);
	}
	
	
	/**
	 * Create the index structure that retains all types of ContextAssertion and the mapping
	 * between a ContextAssertion and the named graph URI that acts as Store for that type of ContextAssertion
	 * @param contextModelCore The ontology module that defines the core of this Context Model
	 * @param dataset The TDB-backed dataset that holds the graphs
	 * @return An {@link ContextAssertionIndex} instance that holds the index structure.
	 */
	public static ContextAssertionIndex create(OntModel contextModelCore) {
		
		ContextAssertionIndex assertionIndex = new ContextAssertionIndex();
		
		// search all EntityRelationAssertions
		ExtendedIterator<? extends OntProperty> relationPropIt = 
			contextModelCore.getOntProperty(ConsertCore.ENTITY_RELATION_ASSERTION.getURI()).listSubProperties();
		
		for (; relationPropIt.hasNext();) {
			OntProperty prop = relationPropIt.next();
			if (!SPINFactory.isAbstract(prop)) {
				ContextAssertionType assertionType = ContextAssertionImpl.getType(prop, contextModelCore);
				ContextAssertion assertion = ContextAssertionImpl.createBinary(assertionType, 2, prop);
				
				switch(assertionType) {
				case Static:
					assertionIndex.addStaticContextAssertion(assertion);
					break;
				case Profiled:
					assertionIndex.addProfiledContextAssertion(assertion);
					break;
				case Sensed:
					assertionIndex.addSensedContextAssertion(assertion);
					break;
				case Derived:
					assertionIndex.addDerivedContextAssertion(assertion);
					break;
				default:
					break;
				}
				
				assertionIndex.mapAssertionStorage(assertion);
			}
		}
		
		// search all EntityDataAssertions
		ExtendedIterator<? extends OntProperty> dataPropIt = 
			contextModelCore.getOntProperty(ConsertCore.ENTITY_DATA_ASSERTION.getURI()).listSubProperties();
		
		for (; dataPropIt.hasNext();) {
			OntProperty prop = dataPropIt.next();
			if (!SPINFactory.isAbstract(prop)) {
				ContextAssertionType assertionType = ContextAssertionImpl.getType(prop, contextModelCore);
				ContextAssertion assertion = ContextAssertionImpl.createBinary(assertionType, 2, prop);
				
				switch(assertionType) {
				case Static:
					assertionIndex.addStaticContextAssertion(assertion);
					break;
				case Profiled:
					assertionIndex.addProfiledContextAssertion(assertion);
					break;
				case Sensed:
					assertionIndex.addSensedContextAssertion(assertion);
					break;
				case Derived:
					assertionIndex.addDerivedContextAssertion(assertion);
					break;
				default:
					break;
				}
				
				assertionIndex.mapAssertionStorage(assertion);
			}
		}

		// create stores for subclasses of UnaryContextAssertion 
		ExtendedIterator<OntClass> unaryClassIt = contextModelCore
			.getOntClass(ConsertCore.UNARY_CONTEXT_ASSERTION.getURI()).listSubClasses();
		for (; unaryClassIt.hasNext();) {
			OntClass cls = unaryClassIt.next();
			if (!SPINFactory.isAbstract(cls)) {
				ContextAssertionType assertionType = ContextAssertionImpl.getType(cls, contextModelCore);
				ContextAssertion assertion = ContextAssertionImpl.createUnary(assertionType, 1, cls, contextModelCore);
				
				switch(assertionType) {
				case Static:
					assertionIndex.addStaticContextAssertion(assertion);
					break;
				case Profiled:
					assertionIndex.addProfiledContextAssertion(assertion);
					break;
				case Sensed:
					assertionIndex.addSensedContextAssertion(assertion);
					break;
				case Derived:
					assertionIndex.addDerivedContextAssertion(assertion);
					break;
				default:
					break;
				}
				
				assertionIndex.mapAssertionStorage(assertion);
			}
		}
		
		// and subclasses of NaryContextAssertion
		ExtendedIterator<OntClass> naryClassIt = contextModelCore
			.getOntClass(ConsertCore.NARY_CONTEXT_ASSERTION.getURI()).listSubClasses();
		for (; naryClassIt.hasNext();) {
			OntClass cls = naryClassIt.next();
			if (!SPINFactory.isAbstract(cls)) {
				ContextAssertionType assertionType = ContextAssertionImpl.getType(cls, contextModelCore);
				ContextAssertion assertion = ContextAssertionImpl.createNary(assertionType, 3, cls, contextModelCore);
				
				switch(assertionType) {
				case Static:
					assertionIndex.addStaticContextAssertion(assertion);
					break;
				case Profiled:
					assertionIndex.addProfiledContextAssertion(assertion);
					break;
				case Sensed:
					assertionIndex.addSensedContextAssertion(assertion);
					break;
				case Derived:
					assertionIndex.addDerivedContextAssertion(assertion);
					break;
				default:
					break;
				}
				
				assertionIndex.mapAssertionStorage(assertion);
			}
		}
		
		return assertionIndex;
	}
}
