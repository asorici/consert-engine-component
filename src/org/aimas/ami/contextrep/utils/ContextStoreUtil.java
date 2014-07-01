package org.aimas.ami.contextrep.utils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.aimas.ami.contextrep.engine.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.graph.compose.MultiUnion;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

public class ContextStoreUtil {
	public static final String UNION_GRAPH_URN = "urn:x-arq:UnionGraph";
	
	/**
	 * Get the union model of all named graphs contained within a TDB-transaction snapshot 
	 * of the ContextStore dataset. This method is used to retrieve a queryable model of the ContextStore
	 * to be used during inference and constraint checks. 
	 * @param contextDatasetSnapshot The dataset representing the current view of the ContextStore.
	 * @return	A Jena {@link Model} representing the union of the named graphs 
	 * 		contained within the <code>contextDatasetSnapshot</code>
	 */
	public static Model getUnionModel(Dataset contextDatasetSnapshot) {
		MultiUnion union = new MultiUnion();
		
		Iterator<String> namedModels = contextDatasetSnapshot.listNames();
		for (; namedModels.hasNext();) {
			union.addGraph(contextDatasetSnapshot.getNamedModel(namedModels.next()).getGraph());
		}
		
		return ModelFactory.createModelForGraph(union);
	}
	
	/**
	 * Get a union model of all the named graphs that relate to a given ContextAssertion:
	 * <ul>
	 * 	<li>The named graph identifiers of instances of <code>contextAssertion</code> </li>
	 *  <li>The ContextAssertion <i>Store</i> named graph for <code>contextAssertion</code> </li>
	 *  <li>The <i>entityStore</i> of the ContextStore</li>
	 * </ul>
	 * @param contextAssertion	The ContextAssertion for which to get the union model.
	 * @param contextDatasetSnapshot The dataset representing the current view of the ContextStore.
	 * @return A Jena {@link Model} representing the union of the named graphs 
	 * 		relating to <code>contextAssertion</code>
	 */
	public static Model unionModelForAssertion(ContextAssertion contextAssertion, Dataset contextDatasetSnapshot) {
		MultiUnion union = new MultiUnion();
		
		// get the URIs of named graphs that identify instances of the ContextAssertion
		Model assertionStoreModel = contextDatasetSnapshot.getNamedModel(contextAssertion.getAssertionStoreURI());
		
		// Since all ContextAssertions MUST have a timestamp annotation it is by this feature that we identify all
		// the named graphs that act as identifiers of ContextAssertion instances. In the ContextAssertion Store, 
		// their URI will be bound by the timestamp property to a resource that represents the timestamp annotation.
		OntProperty timestampAnnProp = Engine.getContextAnnotationIndex()
				.getByResource(ConsertAnnotation.HAS_TIMESTAMP).getBindingProperty();
		
		String queryString = 
			"SELECT DISTINCT ?assertionUUID WHERE {"
			+ 	"GRAPH ?assertionStore {"
			+ 		"?assertionUUID ?timestampProp ?ts ."
			+	"}"
			+ "}"; 
		
		QuerySolutionMap initialBinding = new QuerySolutionMap();
		initialBinding.add("assertionStore", ResourceFactory.createResource(contextAssertion.getAssertionStoreURI()));
		initialBinding.add("timestampProp", timestampAnnProp);
		QueryExecution qexec = QueryExecutionFactory.create(queryString, assertionStoreModel, initialBinding);
		
		List<String> assertionUUIDs = new LinkedList<String>();
		try {
		    ResultSet results = qexec.execSelect() ;
		    for ( ; results.hasNext() ; ) {
		      QuerySolution sol = results.nextSolution() ;
		      Resource assertionUUIDRes = sol.getResource("assertionUUID");
		      assertionUUIDs.add(assertionUUIDRes.getURI());
		    }
		} finally { qexec.close() ; }
		
		// add all the contextAssertion UUID named graphs
		for (String assertionUUID: assertionUUIDs) {
			union.addGraph(contextDatasetSnapshot.getNamedModel(assertionUUID).getGraph());
		}
		
		// add the contextAssertion Store and the EntityStore
		union.addGraph(assertionStoreModel.getGraph());
		union.addGraph(contextDatasetSnapshot.getNamedModel(ConsertCore.ENTITY_STORE_URI).getGraph());
		
		return ModelFactory.createModelForGraph(union);
	}
}
