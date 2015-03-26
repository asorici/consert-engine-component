package org.aimas.ami.contextrep.engine.utils.spin;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.engine.utils.ConstraintsWrapper;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.ContextConstraintViolation;
import org.aimas.ami.contextrep.model.ContextIntegrityConstraintViolation;
import org.aimas.ami.contextrep.model.ContextUniquenessConstraintViolation;
import org.aimas.ami.contextrep.model.ContextValueConstraintViolation;
import org.aimas.ami.contextrep.model.ViolationAssertionWrapper;
import org.aimas.ami.contextrep.vocabulary.ConsertConstraint;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.statistics.SPINStatistics;
import org.topbraid.spin.system.SPINLabels;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.JenaUtil;
import org.topbraid.spin.util.QueryWrapper;
import org.topbraid.spin.util.SPINUtil;
import org.topbraid.spin.vocabulary.SPIN;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.update.UpdateRequest;
import com.hp.hpl.jena.vocabulary.RDF;

public class ContextSPINConstraints {
	
	public static List<ContextConstraintViolation> check(Engine consertEngine, Model constraintContextModel, ContextAssertion assertion, 
			Node assertionUUID, UpdateRequest triggeringRequest, ConstraintsWrapper constraints, List<SPINStatistics> stats) {
		
		Resource anchorResource = constraints.getAnchorResource();
		List<CommandWrapper> constraintCommands = constraints.getConstraintCommands();
		Map<CommandWrapper, Map<String, RDFNode>> templateBindings = constraints.getConstraintTemplateBindings();
		
		return run(consertEngine, constraintContextModel, assertion, assertionUUID, triggeringRequest, anchorResource, constraintCommands, templateBindings, stats);
		//return run(constraintContextModel, assertion, anchorResource, constraintCommands, stats);
	}
	
	private static List<ContextConstraintViolation> run(Engine consertEngine, Model constraintContextModel, ContextAssertion assertion, 
			Node assertionUUID, UpdateRequest triggeringRequest, Resource anchorResource, List<CommandWrapper> constraintCommands,
			Map<CommandWrapper, Map<String, RDFNode>> templateBindings,
			List<SPINStatistics> stats) {
		
		List<ContextConstraintViolation> results = new LinkedList<>();
		
		for(CommandWrapper arqConstraint : constraintCommands) {
			QueryWrapper queryConstraintWrapper = (QueryWrapper) arqConstraint;
			Map<String,RDFNode> initialBindings = templateBindings.get(arqConstraint);
			//Map<String,RDFNode> initialBindings = arqConstraint.getTemplateBinding();
			
			Query arq = queryConstraintWrapper.getQuery();
			String label = arqConstraint.getLabel();
			
			runQueryOnClass(consertEngine, results, arq, queryConstraintWrapper.getSPINQuery(), label, constraintContextModel, assertion, assertionUUID, triggeringRequest, anchorResource, initialBindings, arqConstraint.isThisUnbound(), arqConstraint.isThisDeep(), arqConstraint.getSource(), stats);
			
			if(!arqConstraint.isThisUnbound()) {
				Set<Resource> subClasses = JenaUtil.getAllSubClasses(anchorResource);
				for(Resource subClass : subClasses) {
					runQueryOnClass(consertEngine, results, arq, queryConstraintWrapper.getSPINQuery(), label, constraintContextModel, assertion, assertionUUID, triggeringRequest, subClass, initialBindings, arqConstraint.isThisUnbound(), arqConstraint.isThisDeep(), arqConstraint.getSource(), stats);
				}
			}
		}
		
		return results;
	}

	private static void runQueryOnClass(Engine consertEngine,
            List<ContextConstraintViolation> results, Query arq,
            org.topbraid.spin.model.Query spinQuery, String label,
            Model constraintContextModel, ContextAssertion assertion, 
            Node assertionUUID, UpdateRequest triggeringRequest, Resource anchorResource,
            Map<String, RDFNode> initialBindings, boolean thisUnbound,
            boolean thisDeep, Resource source, List<SPINStatistics> stats) {
	    
		if(thisUnbound || SPINUtil.isRootClass(anchorResource) || constraintContextModel.contains(null, RDF.type, anchorResource)) {
			QuerySolutionMap arqBindings = new QuerySolutionMap();
			if(!thisUnbound) {
				arqBindings.add(SPINUtil.TYPE_CLASS_VAR_NAME, anchorResource);
			}
			if(initialBindings != null) {
				for(String varName : initialBindings.keySet()) {
					RDFNode value = initialBindings.get(varName);
					arqBindings.add(varName, value);
				}
			}
			
			long startTime = consertEngine.currentTimeMillis();
			Model cm = JenaUtil.createDefaultModel();
			
			if(thisDeep && !thisUnbound) {
				StmtIterator it = constraintContextModel.listStatements(null, RDF.type, anchorResource);
				while(it.hasNext()) {
					Resource instance = it.next().getSubject();
					arqBindings.add(SPIN.THIS_VAR_NAME, instance);
					QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, constraintContextModel, arqBindings);
					qexec.execConstruct(cm);
					qexec.close();
				}
			}
			else {
				QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, constraintContextModel, arqBindings);
				qexec.execConstruct(cm);
				qexec.close();
			}
			
			long endTime = consertEngine.currentTimeMillis();
			if(stats != null) {
				long duration = endTime - startTime;
				String queryText = SPINLabels.get().getLabel(spinQuery);
				if(label == null) {
					label = queryText;
				}
				stats.add(new SPINStatistics(label, queryText, duration, startTime, anchorResource.asNode()));
			}
			
			addConstructedProblemReports(consertEngine, cm, results, constraintContextModel, assertion, assertionUUID, triggeringRequest, anchorResource, null, label, source);
		}
	    
    }

	private static void addConstructedProblemReports(Engine consertEngine, Model cm,
            List<ContextConstraintViolation> results, Model constraintContextModel,
            ContextAssertion assertion, Node triggeringAssertionUUID, UpdateRequest triggeringRequest, Resource anchorResource, Resource matchRoot, String label,
            Resource source) {
		
		// First we must determine the type of constraint violation that was triggered
		StmtIterator uniquenessViolationIt = cm.listStatements(null, RDF.type, ConsertConstraint.UNIQUENESS_CONSTRAINT_VIOLATION);
		StmtIterator integrityViolationIt = cm.listStatements(null, RDF.type, ConsertConstraint.INTEGRITY_CONSTRAINT_VIOLATION);
		StmtIterator valueViolationIt = cm.listStatements(null, RDF.type, ConsertConstraint.VALUE_CONSTRAINT_VIOLATION);
		
		if (uniquenessViolationIt.hasNext()) {
			while(uniquenessViolationIt.hasNext()) {
				Statement s = uniquenessViolationIt.nextStatement();
				Resource vio = s.getSubject();
				
				// determine the URI of the ContextConstraintTemplate that triggered this conflict
				//Statement constraintTemplateStmt = cm.getProperty(vio, ConsertConstraint.HAS_SOURCE_TEMPLATE);
				//String constraintTemplateURI = constraintTemplateStmt.getResource().getURI();
				
				// get the identifier URIs of the two conflicting ContextAssertions
				List<Statement> conflictingAssertions = cm.listStatements(vio, ConsertConstraint.HAS_CONFLICTING_ASSERTION, (RDFNode)null).toList();
				if (conflictingAssertions.size() == 2) {
					String assertionUUID1 = conflictingAssertions.get(0).getResource().getProperty(ConsertConstraint.HAS_ASSERTION_INSTANCE).getResource().getURI();
					String assertionUUID2 = conflictingAssertions.get(1).getResource().getProperty(ConsertConstraint.HAS_ASSERTION_INSTANCE).getResource().getURI();
					
					results.add(createUniquenessConstraintViolation(assertion, triggeringAssertionUUID, triggeringRequest, assertionUUID1, assertionUUID2, source));
				}
			}
		}
		else if (valueViolationIt.hasNext()) {
			while(valueViolationIt.hasNext()) {
				Statement s = valueViolationIt.nextStatement();
				Resource vio = s.getSubject();
				
				// get the identifier URI of the conflicting ContextAssertion
				Statement conflictingAssertionStmt = cm.getProperty(vio, ConsertConstraint.HAS_CONFLICTING_ASSERTION);
				String conflictingAssertionUUID = conflictingAssertionStmt.getResource().getProperty(ConsertConstraint.HAS_ASSERTION_INSTANCE).getResource().getURI();
				
				// check for assertion or annotation violation value indicators
				Resource assertionConflictingVal = null;
				Resource annotationConflictingVal = null;
				
				Statement assertionConflictValueStmt = cm.getProperty(vio, ConsertConstraint.HAS_CONFLICT_ASSERTION_VALUE);
				if (assertionConflictValueStmt != null) {
					assertionConflictingVal = assertionConflictValueStmt.getResource();
				}
				
				Statement annotationConflictValueStmt = cm.getProperty(vio, ConsertConstraint.HAS_CONFLICT_ANNOTATION_VALUE);
				if (annotationConflictValueStmt != null) {
					annotationConflictingVal = annotationConflictValueStmt.getResource();
				}
				
				results.add(createValueConstraintViolation(assertion, triggeringRequest, source, conflictingAssertionUUID, assertionConflictingVal, annotationConflictingVal));
			}
		}
		else {
			while(integrityViolationIt.hasNext()) {
				Statement s = integrityViolationIt.nextStatement();
				Resource vio = s.getSubject();
				
				// determine the URI of the ContextConstraintTemplate that triggered this conflict
				//Statement constraintTemplateStmt = cm.getProperty(vio, ConsertConstraint.HAS_SOURCE_TEMPLATE);
				//String constraintTemplateURI = constraintTemplateStmt.getResource().getURI();
				
				// get the identifier URIs of the two conflicting ContextAssertions
				List<Statement> conflictingAssertions = cm.listStatements(vio, ConsertConstraint.HAS_CONFLICTING_ASSERTION, (RDFNode)null).toList();
				if (conflictingAssertions.size() == 2) {
					Resource assertionType1 = conflictingAssertions.get(0).getResource().getProperty(ConsertConstraint.HAS_ASSERTION_TYPE).getResource();
					Resource assertionType2 = conflictingAssertions.get(1).getResource().getProperty(ConsertConstraint.HAS_ASSERTION_TYPE).getResource();
					
					String assertionUUID1 = conflictingAssertions.get(0).getResource().getProperty(ConsertConstraint.HAS_ASSERTION_INSTANCE).getResource().getURI();
					String assertionUUID2 = conflictingAssertions.get(1).getResource().getProperty(ConsertConstraint.HAS_ASSERTION_INSTANCE).getResource().getURI();
					
					ViolationAssertionWrapper violatingAssertion1 = new ViolationAssertionWrapper(consertEngine.getContextAssertionIndex().getAssertionFromResource(assertionType1), assertionUUID1);
					ViolationAssertionWrapper violatingAssertion2 = new ViolationAssertionWrapper(consertEngine.getContextAssertionIndex().getAssertionFromResource(assertionType2), assertionUUID2);
					
					results.add(createIntegrityConstraintViolation(new ViolationAssertionWrapper[] {violatingAssertion1, violatingAssertion2}, 
							triggeringAssertionUUID, triggeringRequest, source));
				}
			}
		}
		
    }
	
	
	private static ContextConstraintViolation createIntegrityConstraintViolation(ViolationAssertionWrapper[] violatingAssertions,  
			Node triggeringAssertionUUID, UpdateRequest triggeringRequest, Resource source) {
	    
		return new ContextIntegrityConstraintViolation(violatingAssertions, triggeringAssertionUUID, triggeringRequest, source);
    }
	
	private static ContextConstraintViolation createUniquenessConstraintViolation(
            ContextAssertion assertion, Node triggeringAssertionUUID, UpdateRequest triggeringRequest, String assertionUUID1, String assertionUUID2, Resource source) {
	    
		return new ContextUniquenessConstraintViolation(assertion, triggeringAssertionUUID, triggeringRequest, source, assertionUUID1, assertionUUID2);
    }
	
	private static ContextConstraintViolation createValueConstraintViolation(ContextAssertion assertion, 
			UpdateRequest triggeringRequest, Resource source, String assertionUUID, Resource assertionConflictValue, Resource annotationConflictValue) {
	    
		return new ContextValueConstraintViolation(assertion, triggeringRequest, source, assertionUUID, assertionConflictValue, annotationConflictValue);
    }
}
