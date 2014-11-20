package org.aimas.ami.contextrep.engine.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.sparql.core.Quad;
import com.hp.hpl.jena.sparql.modify.request.UpdateData;
import com.hp.hpl.jena.sparql.modify.request.UpdateDataInsert;
import com.hp.hpl.jena.sparql.modify.request.UpdateDeleteWhere;
import com.hp.hpl.jena.sparql.modify.request.UpdateModify;
import com.hp.hpl.jena.update.Update;

public class ContextUpdateUtil {
	/**
	 * Gets all named graph nodes that are potentially updated in the given Update request.
	 * @param update  the Update (UpdateData, UpdateModify and UpdateDeleteWhere are supported)
	 * @param dataset  the Dataset to get the Graphs from
	 * @return the graphs nodes
	 */
	public static Collection<Node> getUpdatedGraphs(Update update, Dataset dataset, 
			Map<String,RDFNode> templateBindings, boolean storesOnly) {
		Set<Node> results = new HashSet<Node>();
		
		if (update instanceof UpdateData) {
			addUpdatedGraphs(results, (UpdateData)update, dataset, templateBindings, storesOnly);
		}
		else if(update instanceof UpdateModify) {
			addUpdatedGraphs(results, (UpdateModify)update, dataset, templateBindings, storesOnly);
		}
		else if(update instanceof UpdateDeleteWhere) {
			addUpdatedGraphs(results, (UpdateDeleteWhere)update, dataset, templateBindings, storesOnly);
		}
		return results;
	}
	
	
	/**
	 * Gets all named graph nodes to which data is inserted in the given Update request.
	 * @param update  the Update (UpdateData, UpdateModify and UpdateDeleteWhere are supported)
	 * @param dataset  the Dataset from which to get the Graphs
	 * @return the graphs nodes
	 */
	public static Collection<Node> getInsertionGraphs(Update update, Dataset dataset, 
			Map<String,RDFNode> templateBindings, boolean storesOnly) {
		Set<Node> results = new HashSet<Node>();
		
		if (update instanceof UpdateDataInsert) {
			addUpdatedGraphs(results, (UpdateData)update, dataset, templateBindings, storesOnly);
		}
		else if(update instanceof UpdateModify) {
			UpdateModify updateModify = (UpdateModify)update;
			if (updateModify.hasInsertClause()) {
				addUpdatedGraphs(results, updateModify.getInsertQuads(), dataset, templateBindings, storesOnly);
			}
		}
		
		return results;
	}
	
	
	private static void addUpdatedGraphs(Set<Node> results, UpdateData update, Dataset dataset, 
			Map<String, RDFNode> templateBindings, boolean storesOnly) {
		addUpdatedGraphs(results, update.getQuads(), dataset, templateBindings, storesOnly);
    }


	private static void addUpdatedGraphs(Set<Node> results, UpdateDeleteWhere update, 
			Dataset dataset, Map<String,RDFNode> templateBindings, boolean storesOnly) {
		addUpdatedGraphs(results, update.getQuads(), dataset, templateBindings, storesOnly);
	}
	
	
	private static void addUpdatedGraphs(Set<Node> results, UpdateModify update, Dataset dataset, 
			Map<String,RDFNode> templateBindings, boolean storesOnly) {
		Node withIRI = update.getWithIRI();
		if(withIRI != null) {
			results.add(withIRI);
		}
		addUpdatedGraphs(results, update.getDeleteQuads(), dataset, templateBindings, storesOnly);
		addUpdatedGraphs(results, update.getInsertQuads(), dataset, templateBindings, storesOnly);
	}

	
	private static void addUpdatedGraphs(Set<Node> results, Iterable<Quad> quads, Dataset graphStore, 
			Map<String,RDFNode> templateBindings, boolean storesOnly) {
		for(Quad quad : quads) {
			if(quad.isDefaultGraph()) {
				results.add(quad.getGraph());
			}
			else if(quad.getGraph().isVariable()) {
				if(templateBindings != null) {
					String varName = quad.getGraph().getName();
					RDFNode binding = templateBindings.get(varName);
					if(binding != null && binding.isURIResource()) {
						if (storesOnly && Engine.getContextAssertionIndex().isContextStore(binding.asNode())) {
							results.add(binding.asNode());
						}
						else if (!storesOnly) {
							results.add(binding.asNode());
						}
					}
				}
			}
			else {
				if (storesOnly && Engine.getContextAssertionIndex().isContextStore(quad.getGraph())) {
					results.add(quad.getGraph());
				}
				else if (!storesOnly) {
					results.add(quad.getGraph());
				}
			}
		}
	}
	
	
	public static Set<Statement> getAllMetaPropertiesFor(Resource assertionUUID, Model assertionStoreModel) {
		Set<Statement> collectedStatements = new HashSet<Statement>();
		Set<Resource> reached = new HashSet<Resource>();
		
		collectStatements(assertionUUID, assertionStoreModel, collectedStatements, reached);
		
		return collectedStatements;
	}
	
	
	private static void collectStatements(Resource subject, Model contentModel, Set<Statement> collectedStatements, Set<Resource> reached) {
		
		reached.add(subject);
		StmtIterator statementIt = contentModel.listStatements(subject, (Property)null, (RDFNode)null);
		
		Set<Statement> statements = statementIt.toSet();
		collectedStatements.addAll(statements);
		
		/* Walk through statements and recurse through those that have a resource object, 
		   if that object has not already been visited */ 
		for (Statement s : statements) {
			RDFNode obj = s.getObject();
			if (obj.isResource()) {
				Resource objRes = obj.asResource();
				if (!reached.contains(objRes)) {
					collectStatements(objRes, contentModel, collectedStatements, reached);
				}
			}
		}
	}
	
	/**
	 * Delete a ContextAssertion instance (content and annotations).
	 * @param assertion The type of the ContextAssertion.
	 * @param assertionUUID The assertion instance identifier encoded as a {@link Resource}.
	 * @param contextStore A snapshot of the runtime ContextStore of the CONSERT Engine.
	 */
	public static void deleteContextAssertionInstance(ContextAssertion assertion, Resource assertionUUID, Dataset contextStore) {
		// STEP 1: delete instance content
		contextStore.removeNamedModel(assertionUUID.getURI());
		
		// STEP 2: access assertionStore and remove all annotation for the assertion instance
		Model assertionStoreModel = contextStore.getNamedModel(assertion.getAssertionStoreURI());
		Set<Statement> assertionAnnotationStatements = getAllMetaPropertiesFor(assertionUUID, assertionStoreModel);
		assertionStoreModel.remove(new LinkedList<Statement>(assertionAnnotationStatements));
	}
}
