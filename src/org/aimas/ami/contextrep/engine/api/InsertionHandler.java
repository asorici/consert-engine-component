package org.aimas.ami.contextrep.engine.api;

import com.hp.hpl.jena.update.Update;
import com.hp.hpl.jena.update.UpdateRequest;

public interface InsertionHandler {
	/**
	 * Request the insertion of a new ContextAssertion.
	 * The {@link UpdateRequest} must contain three update parts (SPARQL CREATE or INSERT statements)
	 * <ol>
	 * 		<li>CREATE: the Identifier Named Graph for the new ContextAssertion</li>
	 * 		<li>INSERT: the contents of the new ContextAssertion</li>
	 * 		<li>INSERT: the annotations of the new ContextAssertion</li>
	 * </ol>
	 * @param insertionRequest The ContextAssertion update request.
	 * @return The result wrapper for this insert request.
	 */
	public InsertResult insert(UpdateRequest insertionRequest);
	
	/**
	 * Request the insertion of a new ContextAssertion.
	 * 		<li>INSERT: the contents of the new ContextAssertion</li>
	 * 		<li>INSERT: the annotations of the new ContextAssertion</li>
	 * @param assertionContents ContextAssertion contents under the form of a SPARQL INSERT {@link Update}.
	 * @param assertionAnnotations ContextAssertion annotations under the form of a SPARQL INSERT {@link Update}.
	 * 		The INSERT statement <b>must</b> specify the <i>Store</i> named graph that
	 * 		corresponds to the ContextAssertion to be inserted.
	 * @return The result wrapper for this insert request.
	 */
	
	public InsertResult insert(Update assertionContents, Update assertionAnnotations);
}
