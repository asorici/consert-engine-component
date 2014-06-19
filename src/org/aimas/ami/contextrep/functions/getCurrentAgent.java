package org.aimas.ami.contextrep.functions;

import org.aimas.ami.contextrep.vocabulary.ConsertCore;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase0;

public class getCurrentAgent extends FunctionBase0 {

	@Override
	public NodeValue exec() {
		return NodeValue.makeNode(ConsertCore.DEFAULT_AGENT_SOURCE_URI, XSDDatatype.XSDanyURI);
	}

}
