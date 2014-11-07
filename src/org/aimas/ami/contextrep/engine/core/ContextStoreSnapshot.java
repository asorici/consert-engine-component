package org.aimas.ami.contextrep.engine.core;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.api.ContextStore;
import org.aimas.ami.contextrep.engine.utils.ContextAnnotationUtil;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.openjena.atlas.lib.Pair;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

public class ContextStoreSnapshot implements ContextStore {
	
	private Dataset contextStore;
	
	public ContextStoreSnapshot(Dataset contextStore) {
	    this.contextStore = contextStore;
    }

	@Override
	public Model getContextAssertionContent(Resource assertionResource, String assertionUUID) {
		return	contextStore.getNamedModel(assertionUUID);
	}
	
	@Override
	public List<String> listContextAssertionInstances(Resource assertionResource) {
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		Model assertionStoreModel = contextStore.getNamedModel(assertion.getAssertionStoreURI());
		
		List<String> assertionInstances = new LinkedList<String>();
		StmtIterator it = assertionStoreModel.listStatements(null, ConsertCore.CONTEXT_ASSERTION_RESOURCE, assertionResource);
		for ( ; it.hasNext(); ) {
			Statement s = it.next();
			assertionInstances.add(s.getSubject().getURI());
		}
		
		return assertionInstances;
	}
	
	@Override
	public AnnotationWrapper getAnnotationContent(Resource assertionResource, String assertionUUID, 
			Property annotationProperty) {
		
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		Model assertionStoreModel = contextStore.getNamedModel(assertion.getAssertionStoreURI());
		
		Pair<Statement, Set<Statement>> annotation = ContextAnnotationUtil.getAnnotationFor(annotationProperty, 
				ResourceFactory.createResource(assertionUUID), assertionStoreModel); 
		
		if (annotation != null) {
			Statement annotationBindingStatement = annotation.car();
			
			Model annotationContentModel = ModelFactory.createDefaultModel();
			annotationContentModel.add((Statement[])annotation.cdr().toArray());
			
			return new AnnotationWrapper(annotationBindingStatement, annotationContentModel);
		}
		
		return null;
	}
	
	@Override
	public RDFNode getStructuredAnnotationValue(Resource assertionResource, String assertionUUID, 
			Property structuredAnnotationProperty) {
		
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		Model assertionStoreModel = contextStore.getNamedModel(assertion.getAssertionStoreURI());
		
		RDFNode annVal = ContextAnnotationUtil.getStructuredAnnotationValue(structuredAnnotationProperty, 
				ResourceFactory.createResource(assertionUUID), assertionStoreModel);
		
		return annVal;
	}
	
	@Override
	public List<AnnotationWrapper> listAnnotationContents(Resource assertionResource, String assertionUUID) {
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		
		Map<Statement, Set<Statement>> annotations = ContextAnnotationUtil.getAnnotationsFor(assertion, 
				ResourceFactory.createResource(assertionUUID), contextStore);
		
		List<AnnotationWrapper> annotationList = new LinkedList<ContextStore.AnnotationWrapper>();
		
		for (Statement bindingStatement : annotations.keySet()) {
			Model annotationContentModel = ModelFactory.createDefaultModel();
			annotationContentModel.add((Statement[])annotations.get(bindingStatement).toArray());
			
			annotationList.add(new AnnotationWrapper(bindingStatement, annotationContentModel));
		}
		
		return annotationList;
	}

	@Override
    public Dataset getRawDataset() {
	    return contextStore;
    }
}
