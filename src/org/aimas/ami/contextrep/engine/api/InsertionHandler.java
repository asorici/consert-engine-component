package org.aimas.ami.contextrep.engine.api;

import java.util.concurrent.Future;

import com.hp.hpl.jena.update.UpdateRequest;

public interface InsertionHandler {
	public static final int TIME_BASED_UPDATE_MODE 		= 0;
	public static final int CHANGE_BASED_UPDATE_MODE 	= 1;
	
	/**
	 * Request the insertion of a new ContextAssertion and get the results on callback.
	 * The {@link UpdateRequest} must contain at these three update parts (SPARQL CREATE or INSERT statements)
	 * <ol>
	 * 		<li>CREATE: the Identifier Named Graph for the new ContextAssertion</li>
	 * 		<li>INSERT: the contents of the new ContextAssertion</li>
	 * 		<li>INSERT: the annotations of the new ContextAssertion</li>
	 * </ol>
	 * 
	 * Optionally, the update request may insert new values in the EntityStore named graph.
	 * @param insertionRequest The ContextAssertion update request.
	 * @param notifier The InsertionResultNotifier callback to call when insertion has been performed
	 * @param updateMode The way in which updates for this ContextAssertion are carried out: time-based 
	 * 		(at a given period of time) or change-based (when there is a change from a previous value)
	 */
	public void insertAssertion(UpdateRequest insertionRequest, InsertionResultNotifier notifier, int updateMode);
	
	
	/**
	 * Request the insertion of a new ContextAssertion.
	 * The {@link UpdateRequest} must contain three update parts (SPARQL CREATE or INSERT statements)
	 * <ol>
	 * 		<li>CREATE: the Identifier Named Graph for the new ContextAssertion</li>
	 * 		<li>INSERT: the contents of the new ContextAssertion</li>
	 * 		<li>INSERT: the annotations of the new ContextAssertion</li>
	 * </ol>
	 * @param insertionRequest The ContextAssertion update request.
	 * @param updateMode The way in which updates for this ContextAssertion are carried out: time-based 
	 * 		(at a given period of time) or change-based (when there is a change from a previous value)
	 * @return A {@link Future} object that can be used to check on the insert operation status.
	 */
	public Future<InsertResult> insertAssertion(UpdateRequest insertionRequest, int updateMode);
	
	
	
	/**
	 * Request performing a bulk insertion into the Context Knowledge Base.
	 * This method bypasses any continuity, constraint and inference checks and is used to perform 
	 * Knowledge Base bootstrapping. 
	 * @param bulkRequest The update request containing the statements to be inserted.
	 * @return A {@link Future} object that can be used to check on the update operation status.
	 */
	public Future<?> bulkInsert(UpdateRequest bulkRequest);
	
	
	public Future<?> updateEntityDescriptions(UpdateRequest entityDescriptionRequest);
	
	
	public void updateProfiledAssertion(UpdateRequest profiledAssertionRequest, InsertionResultNotifier notifier);
}
