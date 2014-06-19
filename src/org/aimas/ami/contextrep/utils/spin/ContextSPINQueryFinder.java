package org.aimas.ami.contextrep.utils.spin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.JenaUtil;
import org.topbraid.spin.util.SPINQueryFinder;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;

public class ContextSPINQueryFinder extends SPINQueryFinder {
	/**
	 * Gets a Map of QueryWrappers with their associated classes. 
	 * @param model  the Model to operate on
	 * @param queryModel  the Model to query on (might be different)
	 * @param subject the resource for which to check for attached queries/constraints
	 * @param predicate  the predicate such as <code>spin:rule</code>
	 * @param withClass  true to also include a SPARQL clause to bind ?this
	 *                   (something along the lines of ?this a ?THIS_CLASS) 
	 * @param allowAsk  also return ASK queries
	 * @return the result Map, possibly empty but not null
	 */
	public static Map<Resource, List<CommandWrapper>> getClass2QueryMap(Model model, Model queryModel, 
			Resource subject, Property predicate, boolean withClass, 
			Map<CommandWrapper,Map<String,RDFNode>> initialTemplateBindings, 
			boolean allowAsk) {
		
		predicate = model.getProperty(predicate.getURI());
		Map<Resource,List<CommandWrapper>> class2Query = new HashMap<Resource,List<CommandWrapper>>();
		
		for(Statement s : JenaUtil.listAllProperties(subject, predicate).toList()) {
			add(class2Query, s, model, withClass, initialTemplateBindings, allowAsk);
			//add(class2Query, s, model, withClass, allowAsk);
		}
		return class2Query;
	}
}
