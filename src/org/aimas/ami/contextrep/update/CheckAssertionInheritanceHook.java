package org.aimas.ami.contextrep.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.aimas.ami.contextrep.engine.api.InsertResult;
import org.aimas.ami.contextrep.engine.core.Engine;
import org.aimas.ami.contextrep.model.ContextAssertion;
import org.aimas.ami.contextrep.model.exceptions.ContextAssertionContentException;
import org.aimas.ami.contextrep.model.exceptions.ContextAssertionModelException;
import org.aimas.ami.contextrep.utils.ContextAnnotationUtil;
import org.aimas.ami.contextrep.utils.ContextAssertionUtil;
import org.aimas.ami.contextrep.utils.GraphUUIDGenerator;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.core.Quad;
import com.hp.hpl.jena.sparql.modify.request.QuadDataAcc;
import com.hp.hpl.jena.sparql.modify.request.UpdateCreate;
import com.hp.hpl.jena.sparql.modify.request.UpdateDataInsert;
import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateRequest;

public class CheckAssertionInheritanceHook extends ContextUpdateHook {
	
	public CheckAssertionInheritanceHook(ContextAssertion contextAssertion, Node contextAssertionUUID) {
		super(contextAssertion, contextAssertionUUID);
	}
	
	@Override
	public AssertionInheritanceResult exec(Dataset contextStoreDataset) {
		// for testing purposes - we need to see how to control this
		long start = System.currentTimeMillis();
		
		// get access to the datastore and the assertionIndex
		OntModel contextModel = Engine.getCoreContextModel();
		
		// determine if this ContextAssertion has ancestors from which it inherits
		List<ContextAssertion> assertionAncestorList = 
			ContextAssertionUtil.getContextAssertionAncestors(contextAssertion, contextModel);
		
		
		if (!assertionAncestorList.isEmpty()) {
			// get the context assertion named graph UUID as a resource
			Resource assertionUUIDRes = ResourceFactory.createResource(contextAssertionUUID.getURI());
			
			// get all annotations of the ContextAssertion
			Map<Statement, Set<Statement>> assertionAnnotations = 
				ContextAnnotationUtil.getAnnotationsFor(contextAssertion, assertionUUIDRes, contextModel, contextStoreDataset);
			
			try {
				// create the appropriate UpdateRequest entries for each ancestor assertion
	            List<UpdateRequest> ancestorAssertionInsertions = 
	            	createAncestorAssertionUpdateRequests(assertionUUIDRes, contextModel, contextStoreDataset, 
	            	assertionAncestorList, assertionAnnotations);
	            
	            // enqueue them as individual insertion requests
	            for (UpdateRequest req : ancestorAssertionInsertions) {
	            	//ContextUpdateTask ancestorInsertWrapper = new ContextUpdateTask(req);
	            	Future<InsertResult> result = Engine.getInsertionService().executeRequest(req, null);
					
	            	// TODO see about performance collect
					//RunTest.insertionTaskEnqueueTime.put(ancestorInsertWrapper.getAssertionInsertID(), System.currentTimeMillis());
					//RunTest.insertionResults.put(ancestorInsertWrapper.getAssertionInsertID(), result);
	            }
	            
	            return new AssertionInheritanceResult(contextAssertion, null, assertionAncestorList);
	            // TODO: performance collect
	            //long end = System.currentTimeMillis();
	    		//return new AssertionInheritanceResult(start, (int)(end - start), false, assertionAncestorList);
            }
            catch (ContextAssertionContentException e) {
	            return new AssertionInheritanceResult(contextAssertion, e, null);
	            // TODO: performance collect
	            //long end = System.currentTimeMillis();
	            //return new AssertionInheritanceResult(start, (int)(end - start), true, null);
            }
            catch (ContextAssertionModelException e) {
            	return new AssertionInheritanceResult(contextAssertion, e, null);
            	// TODO: performance collect
            	//long end = System.currentTimeMillis();
	            //return new AssertionInheritanceResult(start, (int)(end - start), true, null);
            }
		}
		
		return new AssertionInheritanceResult(contextAssertion, null, null);
		// TODO: performance collect
		//long end = System.currentTimeMillis();
		//return new AssertionInheritanceResult(start, (int)(end - start), false, null);
	}
	
	
	private List<UpdateRequest> createAncestorAssertionUpdateRequests(Resource assertionUUIDRes, OntModel contextModel,
        Dataset contextStoreDataset, List<ContextAssertion> assertionAncestorList, Map<Statement, Set<Statement>> assertionAnnotations) 
        throws ContextAssertionContentException, ContextAssertionModelException {
	    
		// create update request list
		List<UpdateRequest> ancestorAssertionInsertions = new ArrayList<UpdateRequest>();
		
		for (ContextAssertion ancestorAssertion : assertionAncestorList) {
			// create the named graph UUID update request for this ancestor ContextAssertion
			Node ancestorUUIDNode = Node.createURI(GraphUUIDGenerator.createUUID(ancestorAssertion.getOntologyResource()));
			Update ancestorCreateUpdate = new UpdateCreate(ancestorUUIDNode);
			
		    Update ancestorContentUpdate = createAssertionContentUpdate(assertionUUIDRes, 
		    	contextStoreDataset, contextModel, ancestorAssertion, ancestorUUIDNode);
		    
		    Update ancestorAnnotationUpdate = 
		    	createAssertionAnnotationUpdate(ancestorAssertion, ancestorUUIDNode, assertionAnnotations);
		
		    // create the update request and add it to the list
			UpdateRequest insertAncestorRequest = UpdateFactory.create();
			insertAncestorRequest.add(ancestorCreateUpdate);
			insertAncestorRequest.add(ancestorContentUpdate);
			insertAncestorRequest.add(ancestorAnnotationUpdate);
			
			ancestorAssertionInsertions.add(insertAncestorRequest);
		}
		
		return ancestorAssertionInsertions;
    }
	
	
	private Update createAssertionContentUpdate(Resource assertionUUIDRes,
            Dataset contextStoreDataset, OntModel contextModel, ContextAssertion ancestorAssertion, Node ancestorUUIDNode) 
            throws ContextAssertionContentException, ContextAssertionModelException {
	    
		List<Statement> ancestorContent = 
			contextAssertion.copyToAncestor(assertionUUIDRes, contextStoreDataset, ancestorAssertion, contextModel);
			
		QuadDataAcc data = new QuadDataAcc();
		for (Statement s : ancestorContent) {
			Quad q = Quad.create(ancestorUUIDNode, s.asTriple());
			data.addQuad(q);
		}
		
		return new UpdateDataInsert(data);
    }
	
	
	private Update createAssertionAnnotationUpdate(ContextAssertion ancestorAssertion, Node ancestorUUIDNode,
            Map<Statement, Set<Statement>> assertionAnnotations) {
		
		Node ancestorAssertionStore = Node.createURI(ancestorAssertion.getAssertionStoreURI());
		
		QuadDataAcc data = new QuadDataAcc();
		for (Statement annStatement : assertionAnnotations.keySet()) {
			Quad annQuad = Quad.create(ancestorAssertionStore, ancestorUUIDNode, annStatement.getPredicate().asNode(), annStatement.getObject().asNode());
			data.addQuad(annQuad);
			
			Set<Statement> annotationContents = assertionAnnotations.get(annStatement);
			for (Statement annContent : annotationContents) {
				Quad annContentQuad = Quad.create(ancestorAssertionStore, annContent.asTriple());
				data.addQuad(annContentQuad);
			}
		}
		
	    return new UpdateDataInsert(data);
    }

}
