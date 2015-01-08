package org.aimas.ami.contextrep.functions;

import java.util.Calendar;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.vocabulary.ConsertAnnotation;

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase1;

public class mostRecentAssertionInstance extends FunctionBase1 {
	
	/*
	 * Get the most recently inserted instance of a ContextAssertion.
	 * To do this we access the ContextStore and enter a READ transaction (it turns out we can
	 * do this several times within the same thread if we get separate instances of the dataset, as is our case)
	 * A ToDo is to change this via a custom QueryExecutionEngine and redefine this function 
	 * as a SPARQL operator where the QueryExecution context can be more easily manipulated, giving access to the
	 * DatasetGraph on which we are currently executing (i.e. we get access to the ContextStore graph which 
	 * already lies in a transaction). 
	 */
	@Override
	public NodeValue exec(NodeValue paramNodeValue) {
		// The paramNodeValue is actually a rdfs:Resource denoting the ontology resource identifying
		// the ContextAssertion type.
		String assertionResURI = paramNodeValue.asNode().getURI();
		Resource assertionRes = ResourceFactory.createResource(assertionResURI);
		
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionRes);
		String assertionStoreURI = assertion.getAssertionStoreURI();
		
		Resource newestAssertionUUID = null;
		Calendar newestAssertionTimestamp = null;
		
		Dataset contextStore = Engine.getRuntimeContextStore();
		contextStore.begin(ReadWrite.READ);
		try {
			Model assertionStore = contextStore.getNamedModel(assertionStoreURI);
			
			// list all hasTimestamp annotations
			StmtIterator stIt = assertionStore.listStatements(null, ConsertAnnotation.HAS_TIMESTAMP, (RDFNode)null);
			for (; stIt.hasNext(); ) {
				Statement st = stIt.next();
				Resource timestampAnn = st.getResource();
				XSDDateTime timestampVal = (XSDDateTime)timestampAnn
						.getProperty(ConsertAnnotation.HAS_STRUCTURED_VALUE).getLiteral().getValue();
				Calendar timestampCal = timestampVal.asCalendar();
				
				// get the newest one
				if (newestAssertionTimestamp == null || timestampCal.after(newestAssertionTimestamp)) {
					newestAssertionTimestamp = timestampCal;
					newestAssertionUUID = st.getSubject();
				}
			}
		}
		finally {
			contextStore.end();
		}
		
		// return the assertionUUID as a node, if there was an inserted instance
		if (newestAssertionUUID != null) {
			return NodeValue.makeNode(newestAssertionUUID.asNode());
		}
		
		// otherwise, for lack of a better alternative, just return an anonymous "nothing node"
		return NodeValue.nvNothing;
	}
}
