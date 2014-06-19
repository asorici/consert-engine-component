package org.aimas.ami.contextrep.utils.spin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.inference.SPINConstructors;
import org.topbraid.spin.inference.SPINInferencesOptimizer;
import org.topbraid.spin.inference.SPINRuleComparator;
import org.topbraid.spin.statistics.SPINStatistics;
import org.topbraid.spin.system.SPINLabels;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.JenaUtil;
import org.topbraid.spin.util.QueryWrapper;
import org.topbraid.spin.util.SPINUtil;
import org.topbraid.spin.util.UpdateUtil;
import org.topbraid.spin.util.UpdateWrapper;
import org.topbraid.spin.vocabulary.SPIN;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.vocabulary.RDF;


/**
 * A service to execute inference rules based on the spin:rule property.
 * 
 * @author Holger Knublauch
 */
public class ContextSPINInferences { 
	
	/**
	 * The globally registered optimizers
	 */
	private static List<SPINInferencesOptimizer> optimizers = new LinkedList<SPINInferencesOptimizer>();
	
	public static void addOptimizer(SPINInferencesOptimizer optimizer) {
		optimizers.add(optimizer);
	}
	
	public static void removeOptimizer(SPINInferencesOptimizer optimizer) {
		optimizers.remove(optimizer);
	}
	
	
	/**
	 * Checks if a given property is a SPIN rule property.
	 * This is (currently) defined as a property that has type spin:RuleProperty
	 * or is a sub-property of spin:rule.  The latter condition may be removed
	 * at some later stage after people have upgraded to SPIN 1.1 conventions.
	 * @param property  the property to check
	 * @return true if property is a rule property
	 */
	public static boolean isRuleProperty(Property property) {
		if(SPIN.rule.equals(property)) {
			return true;
		}
		else if(JenaUtil.hasSuperProperty(property, property.getModel().getProperty(SPIN.rule.getURI()))) {
			return true;
		}
		else {
			return JenaUtil.hasIndirectType(property, SPIN.RuleProperty.inModel(property.getModel())); 
		}
	}
	
	
	/**
	 * Iterates over a provided collection of SPIN rules and adds all constructed
	 * triples to a given Model (newTriples) until no further changes have been
	 * made within one iteration.
	 * Note that in order to iterate more than single pass, the newTriples Model
	 * must be a sub-model of the queryModel (which likely has to be an OntModel).
	 * @param queryModel  the Model to query
	 * @param newTriples  the Model to add the new triples to 
	 * @param class2Query  the map of queries to run (see SPINQueryFinder)
	 * @param class2Constructor  the map of constructors to run
	 * @param templateBindings  initial template bindings (see SPINQueryFinder)
	 * @param explanations  an optional object to write explanations to
	 * @param statistics  optional list to add statistics about which queries were slow
	 * @param singlePass  true to just do a single pass (don't iterate)
	 * @param rulePredicate  the predicate used (e.g. spin:rule)
	 * @param comparator  optional comparator to determine the order of rule execution
	 * @param monitor  an optional ProgressMonitor
	 * @return a {@link ContextInferenceResult} wrapper with the result of the inference
	 */
	public static ContextInferenceResult runContextInference(
			Model queryModel,
			Model newTriples,
			Map<Resource, List<CommandWrapper>> class2Query,
			Map<Resource, List<CommandWrapper>> class2Constructor,
			Map<CommandWrapper, Map<String, RDFNode>> templateBindings,
			List<SPINStatistics> statistics,
			Property rulePredicate,
			SPINRuleComparator comparator) {
		
		// Run optimizers (if available)
		for(SPINInferencesOptimizer optimizer : optimizers) {
			class2Query = optimizer.optimize(class2Query, templateBindings);
			//class2Query = optimizer.optimize(class2Query);
		}
		
		// Get sorted list of Rules and remember where they came from
		List<CommandWrapper> rulesList = new ArrayList<CommandWrapper>();
		Map<CommandWrapper,Resource> rule2Class = new HashMap<CommandWrapper,Resource>();
		for(Resource cls : class2Query.keySet()) {
			List<CommandWrapper> queryWrappers = class2Query.get(cls);
			for(CommandWrapper queryWrapper : queryWrappers) {
				rulesList.add(queryWrapper);
				rule2Class.put(queryWrapper, cls);
			}
		}
		if(comparator != null) {
			Collections.sort(rulesList, comparator);
		}
		
		// Make sure the rulePredicate has a Model attached to it
		if(rulePredicate.getModel() == null) {
			rulePredicate = queryModel.getProperty(rulePredicate.getURI());
		}
		
		// Single iteration needed for Context Inference - single pass rule execution
		
		boolean inferred = false;
		for (CommandWrapper arqWrapper : rulesList) {
			
			Resource cls = rule2Class.get(arqWrapper);
			boolean thisUnbound = arqWrapper.isThisUnbound();
			
			Map<String, RDFNode> initialBindings = templateBindings.get(arqWrapper);
			inferred |= runContextInferenceCommandOnClass(arqWrapper, arqWrapper.getLabel(),
			        queryModel, newTriples, cls, class2Constructor, templateBindings, initialBindings, statistics, thisUnbound);
			
			//inferred |= runContextInferenceCommandOnClass(arqWrapper, arqWrapper.getLabel(),
			//       queryModel, newTriples, cls, class2Constructor, statistics, thisUnbound);
			
			if (!SPINUtil.isRootClass(cls) && !thisUnbound) {
				Set<Resource> subClasses = JenaUtil.getAllSubClasses(cls);
				for (Resource subClass : subClasses) {
					inferred |= runContextInferenceCommandOnClass(arqWrapper,
					        arqWrapper.getLabel(), queryModel, newTriples, subClass, class2Constructor,
					        templateBindings, initialBindings, statistics, thisUnbound);
					
					//inferred |= runContextInferenceCommandOnClass(arqWrapper,
					//        arqWrapper.getLabel(), queryModel, newTriples, subClass, class2Constructor, statistics, thisUnbound);
				}
			}
		}
		
		return new ContextInferenceResult(inferred);
	}

	
	private static boolean runContextInferenceCommandOnClass(
			CommandWrapper commandWrapper, 
			String queryLabel, 
			final Model queryModel, 
			Model newTriples, 
			Resource cls,
			Map<Resource, List<CommandWrapper>> class2Constructor,
			Map<CommandWrapper,Map<String,RDFNode>> initialTemplateBindings,
			Map<String,RDFNode> initialBindings, 
			List<SPINStatistics> statistics, 
			boolean thisUnbound) {
		
		// Check if query is needed at all
		if(thisUnbound || SPINUtil.isRootClass(cls) || queryModel.contains(null, RDF.type, cls)) {
			boolean inferred = false;
			
			QuerySolutionMap bindings = new QuerySolutionMap();
			boolean needsClass = !SPINUtil.isRootClass(cls) && !thisUnbound;
			//Map<String,RDFNode> initialBindings = commandWrapper.getTemplateBinding();
			
			if(initialBindings != null) {
				for(String varName : initialBindings.keySet()) {
					RDFNode value = initialBindings.get(varName);
					bindings.add(varName, value);
				}
			}
			
			long startTime = System.currentTimeMillis();
			final Map<Resource,Resource> newInstances = new HashMap<Resource,Resource>();
			if(commandWrapper instanceof QueryWrapper) {
				Query arq = ((QueryWrapper)commandWrapper).getQuery();
				Model cm;
				if(commandWrapper.isThisDeep() && needsClass) {
					
					// If there is no simple way to bind ?this inside of the query then
					// do the iteration over all instances in an "outer" loop
					cm = JenaUtil.createDefaultModel();
					StmtIterator it = queryModel.listStatements(null, RDF.type, cls);
					while(it.hasNext()) {
						Resource instance = it.next().getSubject();
						QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, queryModel);
						bindings.add(SPIN.THIS_VAR_NAME, instance);
						qexec.setInitialBinding(bindings);
						qexec.execConstruct(cm);
						qexec.close();
					}
				}
				else {
					if(needsClass) {
						bindings.add(SPINUtil.TYPE_CLASS_VAR_NAME, cls);
					}
					QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, queryModel, bindings);
					
					cm = qexec.execConstruct();
					qexec.close();
				}
				
				// if the inferred model is not empty, it means the rule fired and produced a result
				if (!cm.isEmpty()) {
					inferred = true;
					newTriples.add(cm);
				}
			}
			else {
				UpdateWrapper updateWrapper = (UpdateWrapper) commandWrapper;
				Map<String,RDFNode> templateBindings = initialTemplateBindings.get(commandWrapper);
				//Map<String,RDFNode> templateBindings = commandWrapper.getTemplateBinding();
				
				Dataset dataset = ARQFactory.get().getDataset(queryModel);
				Iterable<Graph> updateGraphs = UpdateUtil.getUpdatedGraphs(updateWrapper.getUpdate(), dataset, templateBindings);
				ControlledCtxUpdateGraphStore cugs = new ControlledCtxUpdateGraphStore(dataset, updateGraphs);
				
				if(commandWrapper.isThisDeep() && needsClass) {
					for(Statement s : queryModel.listStatements(null, RDF.type, cls).toList()) {
						Resource instance = s.getSubject();
						bindings.add(SPIN.THIS_VAR_NAME, instance);
						UpdateProcessor up = UpdateExecutionFactory.create(updateWrapper.getUpdate(), cugs, bindings);
						up.execute();
					}
				}
				else {
					if(needsClass) {
						bindings.add(SPINUtil.TYPE_CLASS_VAR_NAME, cls);
					}
					UpdateProcessor up = UpdateExecutionFactory.create(updateWrapper.getUpdate(), cugs, bindings);
					up.execute();
				}
				
				for(ControlledCtxUpdateGraph cug : cugs.getControlledUpdateGraphs()) {
					inferred |= cug.isChanged();
					for(Triple triple : cug.getAddedTriples()) {
						if(RDF.type.asNode().equals(triple.getPredicate()) && !triple.getObject().isLiteral()) {
							Resource subject = (Resource) queryModel.asRDFNode(triple.getSubject());
							newInstances.put(subject, (Resource)queryModel.asRDFNode(triple.getObject()));
						}
					}
				}
			}
			
			if(statistics != null) {
				long endTime = System.currentTimeMillis();
				long duration = (endTime - startTime);
				String queryText = SPINLabels.get().getLabel(commandWrapper.getSPINCommand());
				if(queryLabel == null) {
					queryLabel = queryText;
				}
				statistics.add(new SPINStatistics(queryLabel, queryText, duration, startTime, cls.asNode()));
			}
			
			if(!newInstances.isEmpty()) {
				List<Resource> newRs = new ArrayList<Resource>(newInstances.keySet());
				SPINConstructors.construct(
						queryModel, 
						newRs, 
						newTriples, 
						new HashSet<Resource>(), 
						class2Constructor,
						initialTemplateBindings,
						statistics,
						null, 
						null);
			}
			
			return inferred;
		}
		else {
			return false;
		}
	}

	
	/**
	 * Runs a given Jena CONSTRUCT Inference query on a given instance and adds the inferred triples
	 * to a given Model.
	 * @param arq  the CONSTRUCT query to execute
	 * @param queryModel  the query Model
	 * @param newTriples  the Model to write the triples to
	 * @param instance  the instance to run the inferences on
	 * @param checkContains  true to only call add if a Triple wasn't there yet
	 * @param initialBindings  the initial bindings for arq or null
	 * @return true if changes were done (only meaningful if checkContains == true)
	 */
	public static boolean runInferenceQueryOnInstance(Query arq, Model queryModel, Model newTriples, Resource instance, boolean checkContains, Map<String,RDFNode> initialBindings) {
		boolean inferred = false;
		QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, queryModel);
		QuerySolutionMap bindings = new QuerySolutionMap();
		bindings.add(SPIN.THIS_VAR_NAME, instance);
		
		if(initialBindings != null) {
			for(String varName : initialBindings.keySet()) {
				RDFNode value = initialBindings.get(varName);
				bindings.add(varName, value);
			}
		}
		qexec.setInitialBinding(bindings);
		
		Model cm = qexec.execConstruct();
		if (!cm.isEmpty()) {
			inferred = true;
			newTriples.add(cm);
		}
		
		return inferred;
	}
	
	
	public static class ContextInferenceResult {
		boolean inferred;
		
		public ContextInferenceResult(boolean inferred) {
	        this.inferred = inferred;
        }
		
		public boolean isInferred() {
			return inferred;
		}
	}
}

