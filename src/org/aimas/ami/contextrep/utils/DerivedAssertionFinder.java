package org.aimas.ami.contextrep.utils;

import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.topbraid.spin.model.Aggregation;
import org.topbraid.spin.model.Bind;
import org.topbraid.spin.model.Element;
import org.topbraid.spin.model.ElementList;
import org.topbraid.spin.model.Exists;
import org.topbraid.spin.model.Filter;
import org.topbraid.spin.model.FunctionCall;
import org.topbraid.spin.model.Minus;
import org.topbraid.spin.model.NamedGraph;
import org.topbraid.spin.model.NotExists;
import org.topbraid.spin.model.Optional;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.model.Service;
import org.topbraid.spin.model.SubQuery;
import org.topbraid.spin.model.TriplePath;
import org.topbraid.spin.model.TriplePattern;
import org.topbraid.spin.model.Union;
import org.topbraid.spin.model.Variable;
import org.topbraid.spin.model.visitor.AbstractElementVisitor;
import org.topbraid.spin.model.visitor.AbstractExpressionVisitor;
import org.topbraid.spin.model.visitor.ElementWalker;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

public class DerivedAssertionFinder {
	private Element rootElement;
	private OntModel assertionModel;
	private Property assertionResourceProp;
	
	private OntResource derivedContextAssertion;
	
	private ElementWalker walker;
	
	public DerivedAssertionFinder(Element rootElement, OntModel assertionModel) {
		this.rootElement = rootElement;
		this.assertionModel = assertionModel;
		this.assertionResourceProp = ConsertCore.CONTEXT_ASSERTION_RESOURCE;
	}
	
	
	public void run() {
		walker = new ElementWalker(new MyElementVisitor(), new MyExpressionVisitor());
		rootElement.visit(walker);
	}
	
	
	public OntResource getResult() {
		return derivedContextAssertion;
	}
	
	
	private class MyElementVisitor extends AbstractElementVisitor {

		@Override
		public void visit(Bind bind) {
			//System.out.println("A binding: " + bind.getVariable().getName());
			//System.out.println();
		}

		@Override
		public void visit(ElementList elementList) {
			//System.out.println("Recursing into element list of size: " + elementList.size());
			//System.out.println();
		}

		@Override
		public void visit(Exists exists) {
			//System.out.println("An EXISTS statement: " + exists.getElements().size() + " subelements");
			//System.out.println();
		}

		@Override
		public void visit(Filter filter) {
			//System.out.println("An FILTER statement: " + filter.getExpression().getClass().getName());
			//System.out.println();
		}

		@Override
		public void visit(Minus minus) {
			//System.out.println("An MINUS statement: " + minus.getElements().size() + " subelements");
			//System.out.println();
		}

		@Override
		public void visit(NamedGraph namedGraph) {
			//System.out.println("A NAMED GRAPH statement: " + namedGraph.getElements().size() + " subelements");
			//System.out.println();
		}

		@Override
		public void visit(NotExists notExists) {
			//System.out.println("A NOT EXISTS statement: " + notExists.getElements().size() + " subelements");
			//System.out.println();
		}

		@Override
		public void visit(Optional optional) {
			//System.out.println("An OPTIONAL statement: " + optional.getElements().size() + " subelements");
			//System.out.println();
		}

		@Override
		public void visit(Service service) {
			//System.out.println("A SERVICE statement: " + service.getElements().size() + " subelements");
			//System.out.println();
		}

		@Override
		public void visit(SubQuery subQuery) {
			//System.out.println("A SUBQUERY statement: " + ARQFactory.get().createCommandString(subQuery.getQuery()));
			//System.out.println();
		}

		@Override
		public void visit(TriplePath triplePath) {
			//System.out.println("A TriplePath statement: " + triplePath.getSubject().getURI() + " " + 
			//		triplePath.getObject().asResource().getURI());
			//System.out.println();
		}

		@Override
		public void visit(TriplePattern triplePattern) {
			//System.out.println("A TripplePattern statement: " + triplePattern.getSubject().getURI() + " " + 
			//		triplePattern.getPredicate().getURI() + " " +
			//		triplePattern.getObject().asResource().getURI());
			//System.out.println();
			
			// if we encounter the "contextassertion:assertionResource" property
			if (triplePattern.getPredicate().equals(assertionResourceProp)) {
				Resource assertionRes = triplePattern.getObjectResource();
				derivedContextAssertion = assertionModel.getOntResource(assertionRes);
			}
		}

		@Override
		public void visit(Union union) {}
	}
	
	
	private class MyExpressionVisitor extends AbstractExpressionVisitor {

		@Override
		public void visit(Aggregation aggregation) {
			//System.out.println("An AGGREGATION expression ");
		}

		@Override
		public void visit(FunctionCall functionCall) {
			//System.out.println("An FunctionCall expression ");
		}

		@Override
		public void visit(RDFNode node) {
			//System.out.println("An RDFNode expression ");
			if (node.isResource()) {
				Resource res = node.asResource();
				
				Element elem = SPINFactory.asElement(res);
				if (elem != null) {
					elem.visit(walker);
				}
			}
		}

		@Override
		public void visit(Variable variable) {
			//System.out.println("A VARIABLE expression: " + variable.getName());
		}
	}
}
