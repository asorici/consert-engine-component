package org.aimas.ami.contextrep.functions;

import org.aimas.ami.contextrep.datatype.InfinityMarkerType;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase0;

public class plusInfty extends FunctionBase0 {
	@Override
	public NodeValue exec() {
		Node markerNode = Node.createLiteral(InfinityMarkerType.POSITIVE_INFTY, InfinityMarkerType.infinityMarkerType);
		return NodeValue.makeNode(markerNode);
	}
}
