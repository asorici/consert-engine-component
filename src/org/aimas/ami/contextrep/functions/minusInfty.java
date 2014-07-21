package org.aimas.ami.contextrep.functions;

import org.aimas.ami.contextrep.datatype.InfinityMarkerType;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionBase0;

public class minusInfty extends FunctionBase0 {
	@Override
	public NodeValue exec() {
		Node markerNode = Node.createLiteral(InfinityMarkerType.NEGATIVE_INFTY, InfinityMarkerType.infinityMarkerType);
		return NodeValue.makeNode(markerNode);
	}
}