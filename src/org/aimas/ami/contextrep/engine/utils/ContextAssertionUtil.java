package org.aimas.ami.contextrep.engine.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.core.ContextAssertionIndex;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.update.CheckInferenceHook;
import org.aimas.ami.contextrep.vocabulary.ConsertCore;
import org.topbraid.spin.model.Element;
import org.topbraid.spin.model.NamedGraph;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.model.TriplePattern;
import org.topbraid.spin.util.JenaUtil;

import com.hp.hpl.jena.enhanced.EnhGraph;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ReifiedStatement;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

public class ContextAssertionUtil {
	
	/**
	 * Construct an appropriate instance of ContextAssertionGraph from the given <code>namedGraph</code> instance and based on
	 * the ContextAssertion definitions available in the ontology model give by <code>assertionModel</code> 
	 * @param namedGraph The named graph instance which is supposed to contain the statements of a ContextAssertion
	 * @param assertionIndex The index of available ContextAssertions
	 * @param templateBindings A map containing mappings of possible variable resources contained in 
	 * the statements of the named graph
	 * @return The appropriate instance of a ContextAssertion or null if the Named Graph does not contain statements
	 * that make up a ContextAssertion
	 */
	public static ContextAssertionGraphImpl getFromNamedGraph(NamedGraph namedGraph, ContextAssertionIndex assertionIndex, Map<String, RDFNode> templateBindings) {
		List<Element> childElements = namedGraph.getElements();
		
		for (Element element : childElements) {
			if (element instanceof TriplePattern) {
				TriplePattern triple = element.as(TriplePattern.class);
				RDFNode property = triple.getPredicate();
				RDFNode object = triple.getObjectResource();
				
				if (SPINFactory.isVariable(property)) {
					String varName = SPINFactory.asVariable(property).getName();
					if (templateBindings != null && templateBindings.get(varName) != null) { 
						property = templateBindings.get(varName);
					}
				}
				
				if (SPINFactory.isVariable(object)) {
					String varName = SPINFactory.asVariable(object).getName();;
					
					if (templateBindings != null && templateBindings.get(varName) != null) {
						object = templateBindings.get(varName);
					}
				}
				
				if (property.isURIResource()) {
					// check if we have an assertion property
					ContextAssertion binaryAssertion = assertionIndex.getAssertionFromResource(property.asResource());
					if (binaryAssertion != null) {
						return new ContextAssertionGraphImpl(namedGraph.asNode(), (EnhGraph)namedGraph.getModel(), binaryAssertion);
					}
					
					// check if we have an assertion class
					if (property.equals(RDF.type) && object != null && object.isURIResource()) {
						ContextAssertion naryAssertion = assertionIndex.getAssertionFromResource(object.asResource());
						
						if (naryAssertion != null) {
							return new ContextAssertionGraphImpl(namedGraph.asNode(), (EnhGraph)namedGraph.getModel(), naryAssertion);
						}
					}
				}
				
			}
		}
		
		return null;
	}
	
	
	/**
	 * Returns the {@link ContextAssertion} that is a direct parent of the <code>contextAssertion</code> 
	 * given as input, within the <code>contextModel</code>. As per the modeling recommendations of a 
	 * CONSERT-based context model, a <i>ContextAssertion</i> can have at most a parent assertion. 
	 * The method returns <code>null</code> if the input <code>contextAssertion</code> has no parent to inherit from.
	 * 
	 * @param contextAssertion
	 * @param contextModel
	 * @return the {@link ContextAssertion} that is a direct parent or null if no parent exists
	 */
	public static ContextAssertion getContextAssertionParent(ContextAssertion contextAssertion, OntModel contextModel) {
		OntResource assertionRes = contextAssertion.getOntologyResource();
		
		// we must make the distinction between assertion arity
		if (contextAssertion.isUnary() || contextAssertion.isNary()) {
			Collection<Resource> directAssertionParents = JenaUtil.getSuperClasses(assertionRes);
			
			for (Resource res : directAssertionParents) {
				ContextAssertion parentAssertion = validParentClass(res, assertionRes, contextModel);
				if (parentAssertion != null) {
					return parentAssertion;
				}
			}
		}
		else if (contextAssertion.isBinary()) {
			NodeIterator directParentIt = contextModel.listObjectsOfProperty(assertionRes, RDFS.subPropertyOf);
			for (;directParentIt.hasNext();) {
				RDFNode parentProp = directParentIt.nextNode();
				if (parentProp.isURIResource()) {
					ContextAssertion parentAssertion = validParentProperty(parentProp.asResource(), assertionRes, contextModel);
					if (parentAssertion != null) {
						return parentAssertion;
					}
				}
			}
		}
		
		return null;
	}
	
	private static ContextAssertion validParentClass(Resource parentRes, OntResource assertionRes, OntModel contextModel) {
		if (parentRes.isURIResource() && !parentRes.equals(assertionRes) 
				&& !parentRes.equals(ConsertCore.UNARY_CONTEXT_ASSERTION)
				&& !parentRes.equals(ConsertCore.NARY_CONTEXT_ASSERTION)
				&& !parentRes.equals(OWL.Thing) && !parentRes.equals(RDFS.Resource)) {
			
			OntResource parentOntRes = contextModel.getOntResource(parentRes);
			return Engine.getContextAssertionIndex().getAssertionFromResource(parentOntRes);
		}
		
		return null;
    }
	
	
	private static ContextAssertion validParentProperty(Resource parentRes, OntResource assertionRes, OntModel contextModel) {
		if (parentRes.isURIResource() && !parentRes.equals(assertionRes)
				&& !ConsertCore.ROOT_BINARY_RELATION_ASSERTION_SET.contains(parentRes) 
				&& !ConsertCore.ROOT_BINARY_DATA_ASSERTION_SET.contains(parentRes)) {
			
			OntResource parentOntRes = contextModel.getOntResource(parentRes);
			return Engine.getContextAssertionIndex().getAssertionFromResource(parentOntRes);
		}
		
		return null;
    }

	
	
	/**
	 * Returns the entire chain of {@link ContextAssertion} ancestor assertions up to the base ontology resources that
	 * define a <i>ContextAssertion</i> in the CONSERT ontology. 
	 * @param contextAssertion
	 * @param contextModel
	 * @return The list of {@link ContextAssertion} ancestor assertions from nearest to farthest.
	 */
	public static List<ContextAssertion> getContextAssertionAncestors(ContextAssertion contextAssertion, OntModel contextModel) {
		List<ContextAssertion> assertionAncestorList = new ArrayList<ContextAssertion>();
		ContextAssertion currentAssertion = contextAssertion;
		
		while( (currentAssertion = getContextAssertionParent(currentAssertion, contextModel)) != null ) {
			assertionAncestorList.add(currentAssertion);
		}
		
		return assertionAncestorList;
	}
	
	
	/**
	 * Get the contents of a ContextAssertion that has been derived following the trigger of a Context Derivation Rule. 
	 * The content of the ContextAssertion instance we are looking for is initially hinted towards by a root <code>contentStatement</code> 
	 * the object of which is a ReificationStatement that describes the <i>seed</i> statement of the ContextAssertion instance. 
	 * This <i>seed</i> statement depends on the arity of the assertion and allows to retrieve its entire content.
	 * <p>
	 * This method is reserved for internal usage by the {@link CheckInferenceHook} mechanism during CONSERT Engine Derivation Rule reasoning.
	 * @param assertionResource The ontology resource that defines the type of this ContextAssertion
	 * @param contentStatement	The statement that describes the ContextAssertion instance whose contents we are trying to retrieve.
	 * @param contentModel	The model containing all the inferred triples as result of applying a Context Derivation Rule.
	 * @return The set of statements that make up the contents of the derived ContextAssertion instance.
	 * 		The method returns null, if the seed statement cannot be found or it does not correspond to the expected format.
	 */
	public static Set<Statement> getDerivedAssertionContents(OntResource assertionResource, Statement contentStatement, Model contentModel) {
		Set<Statement> assertionInstanceContents = null;
		
		// The contentStatement object is a reified statement describing the seed statement
		RDFNode seedStatementNode = contentStatement.getObject();
		if (!seedStatementNode.canAs(ReifiedStatement.class)) 
			return null;
		
		Statement seedEqualStatement = seedStatementNode.as(ReifiedStatement.class).getStatement();
		
		// Since the above transformation produces an .equals() statement from the reification we want to get the exact
		// statement in the contentModel so we can further use it to recover the rest of the ContextAssertion contents
		Statement seedStatement = contentModel.listStatements(new EqualsStatementSelector(seedEqualStatement)).next();
		
		// Now switch according to assertion arity
		ContextAssertion assertion = Engine.getContextAssertionIndex().getAssertionFromResource(assertionResource);
		if (assertion.isUnary()) {
			assertionInstanceContents = collectUnaryAssertionStatements(assertion, seedStatement, contentModel);
		}
		else if (assertion.isBinary()) {
			assertionInstanceContents = collectBinaryAssertionStatements(assertion, seedStatement, contentModel);
		}
		else {
			assertionInstanceContents = collectNaryAssertionStatements(assertion, seedStatement, contentModel);
		}
		
		return assertionInstanceContents;
	}
	
	
	private static Set<Statement> collectUnaryAssertionStatements(ContextAssertion derivedAssertion, Statement seedStatement, Model contentModel) {
	    // The seed statement for a unary assertion is the a triple of the form
		// _:bnode a <derivedAssertion>
		if (!seedStatement.getObject().isURIResource())
			return null;
		
		if (!seedStatement.getPredicate().equals(RDF.type) && !seedStatement.getResource().equals(derivedAssertion.getOntologyResource()))
			return null;
	    
		// In the case of the unary assertion we only need to select all the statements that have the same subject as that
		// of the seed statement
		Set<Statement> unaryAssertionContents = contentModel.listStatements(seedStatement.getSubject(), (Property)null, (RDFNode)null).toSet();
		
		return unaryAssertionContents;
    }
	
	
	private static Set<Statement> collectBinaryAssertionStatements(ContextAssertion derivedAssertion, Statement seedStatement, Model contentModel) {
		// The seed statement for a binary assertion is the a triple of the form
		// <some context entity> <derivedAssertion> <some context entity or literal>
		// It is currently in itself, the whole content of a binary assertion
		if (!seedStatement.getPredicate().equals(derivedAssertion.getOntologyResource()))
			return null;
		
		Set<Statement> binaryAssertionContents = new HashSet<Statement>();
		binaryAssertionContents.add(seedStatement);
		
		return binaryAssertionContents;
    }

	
	private static Set<Statement> collectNaryAssertionStatements(ContextAssertion derivedAssertion, Statement seedStatement, Model contentModel) {
		// The seed statement for an nary assertion is the a triple of the form
		// _:bnode a <derivedAssertion>
		if (!seedStatement.getObject().isURIResource())
			return null;
		
		if (!seedStatement.getPredicate().equals(RDF.type) && !seedStatement.getResource().equals(derivedAssertion.getOntologyResource()))
			return null;
		
		// As in the case of the unary assertion, we only need to select all the statements that have the same subject as that
		// of the seed statement (because unary and nary assertions are basically an extension of the reification mechanism)
		Set<Statement> naryAssertionContents = contentModel.listStatements(seedStatement.getSubject(), (Property)null, (RDFNode)null).toSet();
		return naryAssertionContents;
    }
	
	
	private static class EqualsStatementSelector implements Selector {
		
		private Statement statement;
		
		EqualsStatementSelector(Statement statement) {
	        this.statement = statement;
        }
		
		@Override
        public boolean test(Statement s) {
	        return statement.equals(s);
        }

		@Override
        public boolean isSimple() {
	        return false;
        }

		@Override
        public Resource getSubject() {
	        return null;
        }

		@Override
        public Property getPredicate() {
	        return null;
        }

		@Override
        public RDFNode getObject() {
	        return null;
        }
	}
}
