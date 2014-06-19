package org.aimas.ami.contextrep.functions;

import org.aimas.ami.contextrep.utils.GraphUUIDGenerator;

import com.hp.hpl.jena.sparql.expr.ExprEvalException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase2;

public class newGraphUUID extends FunctionBase2 {

	@Override
	public NodeValue exec(NodeValue v1, NodeValue v2) {
		if (!v1.isIRI()) {
			throw new ExprEvalException("base URI: argument not a URI: " + v1) ;
		}
		
		if (!v2.isString()) {
			throw new ExprEvalException("local ContextAssertion name: argument not a string: " + v2) ;
		}
		
		String baseURI = v1.getNode().getURI();
		String localAssertionName = v2.getString();
		
		String graphURI = GraphUUIDGenerator.createUUID(baseURI, localAssertionName);
		
		return NodeValue.makeString(graphURI);
	}
}
