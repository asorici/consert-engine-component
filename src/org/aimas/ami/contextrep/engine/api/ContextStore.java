package org.aimas.ami.contextrep.engine.api;

import java.util.List;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;

public interface ContextStore {
	
	/**
	 * Get the content of a ContextAssertion instance expressed as an RDF {@link Model}.
	 * @param assertionResource		The {@link Resource} identifying the type of ContextAssertion as given in the application context model.
	 * @param assertionUUID			The named graph URI identifying the ContextAssertion instance.
	 * @return	The content of a ContextAssertion instance expressed as an RDF {@link Model}, or null if no ContextAssertion exists for the given UUID.
	 */
	public Model getContextAssertionContent(Resource assertionResource, String assertionUUID);
	
	/**
	 * List the instances for a given ContextAssertion type;
	 * @param assertionResource		The {@link Resource} identifying the type of ContextAssertion as given in the application context model.
	 * @return	A list of named graph URIs identifying the ContextAssertion instances. 
	 */
	public List<String> listContextAssertionInstances(Resource assertionResource);
	
	/**
	 * Get the annotation content for a given ContextAssertion instance and a given <code>annotationProperty</code>. 
	 * The response is returned as a wrapper over the RDF statement that binds the assertion instance to its annotation and the actual annotation content expressed as
	 * an RDF model.
	 * @param assertionResource		The {@link Resource} identifying the type of ContextAssertion as given in the application context model.
	 * @param assertionUUID			The named graph URI identifying the ContextAssertion instance.
	 * @param annotationProperty	The annotation property for which content is retrieved.	
	 * @return	A wrapper containing the annotation binding statement and the annotation contents expressed as an RDF {@link Model}, or null if no such annotation
	 * exists for the specified ContextAssertion instance.
	 */
	public AnnotationWrapper getAnnotationContent(Resource assertionResource, String assertionUUID, Property annotationProperty);
	
	 
	/**
	 * Get the {@link RDFNode} representing the structured annotation value attached to the ContextAssertion instance identified by
	 * <code>assertionUUID</code>. 
	 * @param assertionResource		The {@link Resource} identifying the type of ContextAssertion as given in the application context model.
	 * @param assertionUUID			The named graph URI identifying the ContextAssertion instance.
	 * @param structuredAnnotationProperty	The StructuredAnnotation property for which the value is retrieved.	
	 * @return	The value of the StructuredAnnotation as an {@link RDFNode}, or null if no such annotation exists for the specified ContextAssertion instance.
	 */
	 public RDFNode getStructuredAnnotationValue(Resource assertionResource, String assertionUUID, Property structuredAnnotationProperty);
	
	/**
	 * List the content of all annotations for a given ContextAssertion instance.
	 * The response is returned as a list of wrappers over the RDF statement that binds the assertion instance to its annotation and the actual annotation content 
	 * expressed as an RDF model.
	 * @param assertionResource		The {@link Resource} identifying the type of ContextAssertion as given in the application context model.
	 * @param assertionUUID			The named graph URI identifying the ContextAssertion instance.
	 * @return	A list of wrappers containing the annotation binding statement and the annotation contents expressed as an RDF {@link Model}.
	 */
	public List<AnnotationWrapper> listAnnotationContents(Resource assertionResource, String assertionUUID);
	
	/**
	 * Access the raw dataset underlying the ContextStore. 
	 * @return The raw dataset underlying the ContextStore.
	 */
	public Dataset getRawDataset();
	
	public static class AnnotationWrapper {
		private Statement annotationBindingStatement;
		private Model annotationContentModel;
		
		public AnnotationWrapper(Statement annotationBindingStatement, Model annotationContentModel) {
	        this.annotationBindingStatement = annotationBindingStatement;
	        this.annotationContentModel = annotationContentModel;
        }

		public Property getAnnotationProperty() {
			return annotationBindingStatement.getPredicate();
		}
		
		public Statement getAnnotationBindingStatement() {
			return annotationBindingStatement;
		}
		
		public Model getAnnotationContentModel() {
			return annotationContentModel;
		}
	}
}
