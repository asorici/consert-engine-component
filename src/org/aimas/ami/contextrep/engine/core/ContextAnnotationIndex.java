package org.aimas.ami.contextrep.engine.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.model.ContextAnnotation;
import org.aimas.ami.contextrep.model.ContextAnnotation.ContextAnnotationType;
import org.aimas.ami.contextrep.model.StructuredAnnotation;
import org.aimas.ami.contextrep.model.impl.ContextAnnotationImpl;
import org.aimas.ami.contextrep.model.impl.StructuredAnnotationImpl;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.Resource;

public class ContextAnnotationIndex {
	private Map<OntProperty, List<StructuredAnnotation>> structuredAnnotationMap;
	private Map<OntProperty, List<ContextAnnotation>> basicAnnotationMap;
	
	ContextAnnotationIndex() {
		structuredAnnotationMap = new HashMap<OntProperty, List<StructuredAnnotation>>();
		basicAnnotationMap = new HashMap<OntProperty, List<ContextAnnotation>>();
	}
	
	void addStructuredAnnotation(OntProperty annotationProperty, StructuredAnnotation structAnn) {
		List<StructuredAnnotation> annotations = structuredAnnotationMap.get(annotationProperty);
		if (annotations == null) {
			annotations = new LinkedList<StructuredAnnotation>();
			annotations.add(structAnn);
			structuredAnnotationMap.put(annotationProperty, annotations);
		}
		else {
			annotations.add(structAnn);
		}
	}
	
	void addUnstructuredAnnotation(OntProperty annotationProperty, ContextAnnotation ann) {
		List<ContextAnnotation> annotations = basicAnnotationMap.get(annotationProperty);
		if (annotations == null) {
			annotations = new LinkedList<ContextAnnotation>();
			annotations.add(ann);
			basicAnnotationMap.put(annotationProperty, annotations);
		}
		else {
			annotations.add(ann);
		}
	}
	
	
	// ================== get annotation by resource ==================
	public ContextAnnotation getByResource(Resource annotationRes) {
		// first search in the structured annotation list
		ContextAnnotation ann = getStructuredByResource(annotationRes);
		if (ann != null) {
			return ann;
		}
		
		// then in the unstructured one
		ann = getBasicByResource(annotationRes);
		return ann;
	}
	
	
	public ContextAnnotation getBasicByResource(Resource annotationRes) {
		for (List<ContextAnnotation> annotations : basicAnnotationMap.values()) {
			for (ContextAnnotation ann : annotations) {
				if (ann.getOntologyResource().equals(annotationRes)) {
					return ann;
				}
			}
		}
		
		return null;
	}
	
	
	public StructuredAnnotation getStructuredByResource(Resource annotationRes) {
		for (List<StructuredAnnotation> annotations : structuredAnnotationMap.values()) {
			for (StructuredAnnotation ann : annotations) {
				if (ann.getOntologyResource().equals(annotationRes)) {
					return ann;
				}
			}
		}
		
		return null;
	}
	
	
	// ================== list annotation properties ==================
	public Set<OntProperty> getBasicAnnotationProperties() {
		return basicAnnotationMap.keySet();
	}
	
	public Set<OntProperty> getStructuredAnnotationProperties() {
		return structuredAnnotationMap.keySet();
	}
	
	public Set<OntProperty> getAnnotationProperties() {
		Set<OntProperty> allAnnProperties = new HashSet<OntProperty>();
		allAnnProperties.addAll(basicAnnotationMap.keySet());
		allAnnProperties.addAll(structuredAnnotationMap.keySet());
		
		return allAnnProperties;
	}
	
	// ========== list ContextAnnotations per type and annotation property category ==========
	public List<ContextAnnotation> getBasicAnnotations(OntProperty annotationProperty) {
		return basicAnnotationMap.get(annotationProperty);
	}
	
	public List<StructuredAnnotation> getStructuredAnnotations(OntProperty annotationProperty) {
		return structuredAnnotationMap.get(annotationProperty);
	}
	
	// ================== list ContextAnnotation per type ==================
	public List<ContextAnnotation> getAllBasicAnnotations() {
		List<ContextAnnotation> allUnstructured = new LinkedList<ContextAnnotation>();
		
		for (List<ContextAnnotation> annotations : basicAnnotationMap.values()) {
			allUnstructured.addAll(annotations);
		}
		
		return allUnstructured;
	}
	
	public List<StructuredAnnotation> getAllStructuredAnnotations() {
		List<StructuredAnnotation> allStructured = new LinkedList<StructuredAnnotation>();
		
		for (List<StructuredAnnotation> annotations : structuredAnnotationMap.values()) {
			allStructured.addAll(annotations);
		}
		
		return allStructured;
	}
	
	
	public static ContextAnnotationIndex create(OntModel contextModelAnnotations) {
	    ContextAnnotationIndex annotationIndex = new ContextAnnotationIndex();
		
	    // Start out by collecting the index of structured ContextAnnotation categories.
	    // These are the direct subclasses of StructuredAnnotation.
	    OntClass structuredAnnRootClass = contextModelAnnotations.getOntClass(ConsertAnnotation.STRUCTURED_ANNOTATION.getURI());
	    Set<OntClass> structuredAnnotationClasses = structuredAnnRootClass.listSubClasses(true).toSet();
	    
	    
	    // Do the same now to collect all subclasses of basic ContextAnnotations 
	    // This time the search is exhaustive, as we do not consider the notion of annotation categories
	    // for the unstructured ContextAnotations.
	    OntClass basicAnnRootClass = contextModelAnnotations.getOntClass(ConsertAnnotation.BASIC_ANNOTATION.getURI());
	    Set<OntClass> basicAnnotationClasses = basicAnnRootClass.listSubClasses(false).toSet();
	    
	    // Now list all the subProperties of :hasAnnotation. These are the properties that bind the URI of 
	    // a ContextAssertion identifier named graph to either a structured or basic ContextAnnotation.
	    // The :range of these properties provides the hint towards the exact type.
	    OntProperty hasAnnotationProperty = contextModelAnnotations.getOntProperty(ConsertAnnotation.HAS_ANNOTATION.getURI());
	    Set<? extends OntProperty> annotationProperties = hasAnnotationProperty.listSubProperties(true).toSet();
	    
	    
	    // Now inspect each discovered annotation property in turn, determine its structured or basic 
	    // ContextAnnotation range and look for all the subclasses of that class. These will be added to the
	    // index grouped under the annotation property currently under inspection.
	    for (OntProperty annProp : annotationProperties) {
	    	OntResource annotationRange = annProp.getRange();
	    	
	    	if (structuredAnnotationClasses.contains(annotationRange)) { 
	    		// if the range is a structured annotation, it actually represents a ContextAnnotation category
	    		OntClass annCategClass = annotationRange.asClass();
	    		
	    		// list all its subclasses
	    		Set<OntClass> annCategClasses = annCategClass.listSubClasses(false).toSet();
	    		for (OntClass annClass : annCategClasses) {
	    			// The class must contain in its axiomatic definition the owl:Restrictions that
	    			// specify the meet and join operators, the permits_continuity function and the 
	    			// structuredValue restriction
	    			String[] structureElementURIs = ContextAnnotationImpl.getStructureElementURIs(annClass, contextModelAnnotations);
	    			if (structureElementURIs != null) {
		    			StructuredAnnotation structAnn = 
		    				new StructuredAnnotationImpl(ContextAnnotationType.Structured, annClass, annProp, 
		    					annCategClass, structureElementURIs[0], structureElementURIs[1], 
		    					structureElementURIs[2], structureElementURIs[3]);
		    			
		    			annotationIndex.addStructuredAnnotation(annProp, structAnn);
	    			}
	    		}
	    	}
	    	else if (basicAnnotationClasses.contains(annotationRange)) {
	    		// if the range is a basic annotation, just add it as is in the annotation index
	    		OntClass basicAnnClass = annotationRange.asClass();
	    		ContextAnnotation basicAnn = 
	    			new ContextAnnotationImpl(ContextAnnotationType.Basic, basicAnnClass, annProp);
	    		
	    		annotationIndex.addUnstructuredAnnotation(annProp, basicAnn);
	    	}
	    }
	    
	    return annotationIndex;
    }
}
