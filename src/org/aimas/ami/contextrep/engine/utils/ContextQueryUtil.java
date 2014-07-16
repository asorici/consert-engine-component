package org.aimas.ami.contextrep.engine.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.model.ElementList;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.vocabulary.RDF;

public class ContextQueryUtil {
	
	/**
	 * Retrieve the set of all ContextAssertions that are relevant to the execution of the <code>query</code>.
	 * @param query	The ContextQuery to be analyzed.
	 * @param initialBindings A {variable:value} map of initial bindings for the query.
	 * @param coreContextModel The core module of the Context Model which contains the ontology definitions for 
	 * 		ContextAssertions. 
	 * @return The set of all ContextAssertions referenced in the <code>query</code> or null if the query does
	 * 	not conform to the ContextQuery SELECT or ASK format (MUST have a relevant where section).
	 */
	public static Set<ContextAssertion> analyzeContextQuery(Query query, QuerySolutionMap initialBindings, 
			OntModel coreContextModel) {
		Set<ContextAssertion> referencedAssertions = new HashSet<ContextAssertion>();
		
		// We begin by analyzing FROM and FROM NAMED statements to see if any identifier or Store
		// named graphs are mentioned
		List<String> fromURIs = query.getGraphURIs();
		for (String uri : fromURIs) {
			Node uriNode = Node.createURI(uri);
			
			ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromGraphUUID(uriNode);
			if (assertion != null) {
				referencedAssertions.add(assertion);
			}
			else {
				assertion = Engine.getContextAssertionIndex().getAssertionFromGraphStore(uriNode);
				if (assertion != null) {
					referencedAssertions.add(assertion);
				}
			}
		}
		
		// TODO: improve this approach - use a Jena Element Visitor
		// Next, create a spinQuery out of the initial query and pass it to a ContextAssertionFinder
		Model holderModel = ModelFactory.createDefaultModel();
		ARQ2SPIN arq2SPIN = new ARQ2SPIN(holderModel, false);
		org.topbraid.spin.model.Query spinQuery = arq2SPIN.createQuery(query, null);
		
		// Convert the initialBindings to the map form used by ContextAssertionFinder
		Map<String, RDFNode> bindingsMap = new HashMap<String, RDFNode>();
		Iterator<String> varNames = initialBindings.varNames();
		for (;varNames.hasNext();) {
			String varName = varNames.next();
			bindingsMap.put(varName, initialBindings.get(varName));
		}
		
		// get the where elements of the query
		ElementList whereElements = spinQuery.getWhere();
		if (whereElements == null || whereElements.equals(RDF.nil)) {
			return null;
		}
		
		ContextAssertionFinder assertionFinder = new ContextAssertionFinder(whereElements, 
				Engine.getContextAssertionIndex(), coreContextModel, bindingsMap);
		assertionFinder.run();
		Set<ContextAssertionGraph> visitedAssertionGraphs = assertionFinder.getResult();
		
		for (ContextAssertionGraph assertionGraph : visitedAssertionGraphs) {
			referencedAssertions.add(assertionGraph.getAssertion());
		}
		
		return referencedAssertions;
    }
	
}
