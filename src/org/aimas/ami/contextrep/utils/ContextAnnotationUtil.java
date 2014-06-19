package org.aimas.ami.contextrep.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.Engine;
import org.aimas.ami.contextrep.model.ContextAnnotation;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.StructuredAnnotation;
import org.aimas.ami.contextrep.update.CheckContinuityHook;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;
import org.openjena.atlas.lib.Pair;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.RDF;

public class ContextAnnotationUtil {
	
	/**
	 * Get all the annotations of a <i>ContextAssertion</i> instance identified by <code>assertionUUID</code>, 
	 * grouped by the Statements which bind the <i>ContextAssertion</i> to its <i>ContextAnnotations</i>.
	 * @param contextAssertion
	 * @param assertionUUID
	 * @param contextModel
	 * @param contextStoreDataset
	 * @return A mapping from the <i>ContextAnnotation</i> {@link Statement} to the list of statements that define the annotations. 
	 */
	public static Map<Statement, Set<Statement>> getAnnotationsFor(ContextAssertion contextAssertion, Resource assertionUUID, 
			OntModel contextModel, Dataset contextStoreDataset) {
		Map<Statement, Set<Statement>> annotationsMap = new HashMap<Statement, Set<Statement>>();
		
		// get the all annotation properties defined in the Context Model
		Set<OntProperty> annotationProperties = Engine.getContextAnnotationIndex().getAnnotationProperties();
		
		// get the assertion store for the ContextAssertion
		String assertionStoreURI = contextAssertion.getAssertionStoreURI();
		Model assertionStoreModel = contextStoreDataset.getNamedModel(assertionStoreURI);
		
		for(OntProperty annProp : annotationProperties) {
			// by definition there can be only one instance of each annotation type attached to an assertion
			Pair<Statement, Set<Statement>> annContents = getAnnotationFor(annProp, assertionUUID, assertionStoreModel);
			annotationsMap.put(annContents.car(), annContents.cdr());
		}
		
		return annotationsMap;
	}
	
	/**
	 * Get all the statements that make up the ContextAnnotation of a <i>ContextAssertion</i> instance identified by <code>assertionUUID</code>,
	 * knowing they are bound by the annotation property <code>annProperty</code> and contained 
	 * in the ContextAssertion store <code>assertionModelStore</code>. 
	 * @param assertionUUID	The ContextAssertion named graph identifier, wrapped as a Resource
	 * @param assertionStoreModel	The model containing the ContextAnnotations.
	 * @param annProperty	The property by which this ContextAnnotation is bound to its ContextAssertion instance.
	 * @return A pair formed from:
	 * 		<ul>
	 * 			<li> The statement that binds the ContextAnnotation to the <code>assertionUUid</code> ContextAssertion instance. </li>
	 * 			<li> The set of statements forming the ContextAnnotation </li>
	 * 		</ul>
	 * 		The method returns null if there is no such annotation.
	 */
	public static Pair<Statement, Set<Statement>> getAnnotationFor(OntProperty annProperty, Resource assertionUUID, Model assertionStoreModel) {
		// by definition there can be only one instance of each annotation type attached to an assertion
		Statement annStatement = assertionStoreModel.getProperty(assertionUUID, annProperty);
		
		if (annStatement.getObject().isResource()) {
			Resource annResource = annStatement.getResource();
			
			// get all statement within the assertion store that start out from the annotation resource
			Set<Statement> collectedAnnotationStatements = new HashSet<Statement>();
			Set<Resource> reached = new HashSet<Resource>();
			collectAnnotationStatements(annResource, assertionStoreModel, collectedAnnotationStatements, reached);
		
			return new Pair<Statement, Set<Statement>>(annStatement, collectedAnnotationStatements);
		}
		
		return null;
	}
	
	
	/**
	 * Get the contents of a ContextAnnotation instance given by the <i>identifier statement</i> <code>annotationIdentifierStmt</code> from a
	 * model that contains them. If the statement is insufficient in order to collect the contents of the ContextAnnotation instance it is supposed 
	 * to identify, a null value will be returned. <p>
	 * This method is reserved for internal usage by the CheckInferenceHook mechanism during CONSERT Engine Derivation Rule reasoning.
	 * @param annotationIdentifierStmt The statement acting as an identifier for the ContextAnnotation instance
	 * @param contentModel The model that contains the ContextAnnotation contents
	 * @return A mapping specifying: 
	 * 		<ul>
	 * 			<li> the type of ContextAnnotation that could be identified </li>
	 * 			<li> a pair consisting of: the annotation root node and the set of statements that compose it entirely </li>
	 * 		</ul>		
	 * 		The method returns null if no ContextAnnotation could be identified.
	 */
	public static Map<ContextAnnotation, Pair<Resource, Set<Statement>>> getAnnotationContents(Statement annotationIdentifierStmt, Model contentModel) {
		Map<ContextAnnotation, Pair<Resource, Set<Statement>>> annotationInstance = new HashMap<ContextAnnotation, Pair<Resource,Set<Statement>>>();
		
		// The identifier statement has as subject a blank node that identifies an instance of a triggered Derivation Rule CONSTRUCT query.
		// The predicate is a subProperty of :hasAnnotation and can lead to either a structured or basic annotation.
		// The object is a blank node that will denote the type of ContextAnnotation we are dealing with
		if (!annotationIdentifierStmt.getObject().isResource()) 
			return null;
		
		
		Resource annotationRoot = annotationIdentifierStmt.getResource();
		NodeIterator annTypeList = contentModel.listObjectsOfProperty(annotationRoot, RDF.type);
		while(annTypeList.hasNext()) {
			RDFNode annTypeNode = annTypeList.nextNode();
			if (annTypeNode.isURIResource()) {
				ContextAnnotation annotation = Engine.getContextAnnotationIndex().getByResource(annTypeNode.asResource());
				if (annotation != null) {
					// we have found a definition of an annotation so we can collect all the statements that start out
					// with the annotation root node
					Set<Statement> collectedAnnotationStatements = new HashSet<Statement>();
					Set<Resource> reached = new HashSet<Resource>();
					collectAnnotationStatements(annotationRoot, contentModel, collectedAnnotationStatements, reached);
					
					annotationInstance.put(annotation, new Pair<Resource, Set<Statement>>(annotationRoot, collectedAnnotationStatements));
					break;
				}
			}
		}
		
		annTypeList.close();
		
		return annotationInstance;
    }
	
	
	private static void collectAnnotationStatements(Resource annRelatedsubject, Model contentModel, 
			Set<Statement> collectedAnnotationStatements, Set<Resource> reached) {
		
		reached.add(annRelatedsubject);
		StmtIterator statementIt = contentModel.listStatements(annRelatedsubject, (Property)null, (RDFNode)null);
		
		Set<Statement> statements = statementIt.toSet();
		collectedAnnotationStatements.addAll(statements);
		
		/* Walk through statements and recurse through those that have a resource object, 
		   if that object has not already been visited */ 
		for (Statement s : statements) {
			RDFNode obj = s.getObject();
			if (obj.isResource()) {
				Resource objRes = obj.asResource();
				if (!reached.contains(objRes)) {
					collectAnnotationStatements(objRes, contentModel, collectedAnnotationStatements, reached);
				}
			}
		}
	}


	/**
	 * Get the {@link RDFNode} representing the structured annotation value attached to the ContextAssertion identified by
	 * <code>assertionUUIDResource</code>. <p> 
	 * This method is used internally by the {@link CheckContinuityHook} mechanism activated on insertion of a 
	 * new ContextAssertion.
	 * @param annotationProp 	The structured annotation property binding a ContextAssertion to its annotation.
	 * @param assertionUUIDResource		The named graph ContextAssertion identifier, wrapped as a URI Resource
	 * @param assertionStoreModel	The ContextAssertion store where the annotations of ContextAssertion instances are kept.
	 * @return	An RDFNode representing the value of the sought structured ContextAnnotation, or null if no such annotation exists
	 * 		for the ContextAssertion identified by <code>assertionUUIDResource</code>.
	 */
	public static RDFNode getStructuredAnnotationValue(Property annotationProp, Resource assertionUUIDResource, Model assertionStoreModel) {
		// We first get the annotation statement (since, per recommendations, there can be only one annotation of each type per 
		// ContextAssertion, we only need to retrieve one such statement)
		Statement annotationStmt = assertionStoreModel.getProperty(assertionUUIDResource, annotationProp);
		
		if (annotationStmt != null && annotationStmt.getObject().isResource()) {
			Resource annotationRes = annotationStmt.getResource();
			
			// all structured annotations must have a :hasStructuredValue property which gives their value
			Statement annotationValueStmt = assertionStoreModel.getProperty(annotationRes, ConsertAnnotation.HAS_STRUCTURED_VALUE); 
			if (annotationValueStmt != null)
				return annotationValueStmt.getObject();
		}
		
		return null;
    }
	
	/**
	 * Get the {@link ContextAnnotation} representing the annotation type attached to the ContextAssertion identified by
	 * <code>assertionUUIDResource</code> by the property <code>annotationProp</code>. <p> 
	 * This method is used internally by the {@link CheckContinuityHook} mechanism activated on insertion of a 
	 * new ContextAssertion.
	 * @param annotationProp 	The annotation property binding the <code>assertionUUIDResource</code> ContextAssertion to its annotation.
	 * @param assertionUUIDRes		The named graph ContextAssertion identifier, wrapped as a URI Resource
	 * @param assertionStoreModel	The ContextAssertion store where the annotations of ContextAssertion instances are kept.
	 * @return	The {@link StructuredAnnotation} giving the details about the attached annotation or null if no such annotation exists.
	 */
	public static ContextAnnotation getAnnotationType(Property annotationProp, Resource assertionUUIDRes, Model assertionStoreModel) {
		// We first get the annotation statement (since, per recommendations, there can be only one annotation of each type per 
		// ContextAssertion, we only need to retrieve one such statement)
		Statement annotationStmt = assertionStoreModel.getProperty(assertionUUIDRes, annotationProp);
		
		if (annotationStmt != null && annotationStmt.getObject().isResource()) {
			Resource annotationRes = annotationStmt.getResource();
			
			// all annotations must have a rdf:type property which gives their type
			Statement annotationTypeStmt = assertionStoreModel.getProperty(annotationRes, RDF.type); 
			if (annotationTypeStmt != null && annotationTypeStmt.getObject().isURIResource()) {
				Resource annotationTypeRes = annotationTypeStmt.getResource();
				return Engine.getContextAnnotationIndex().getByResource(annotationTypeRes);
			}
		}
		
		return null;
    }
}
