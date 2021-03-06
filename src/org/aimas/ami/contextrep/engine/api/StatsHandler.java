package org.aimas.ami.contextrep.engine.api;

import java.util.List;

import com.hp.hpl.jena.rdf.model.Resource;

public interface StatsHandler {
	
	/* ================ Statistics computation parameters ================ */
	public long getDefaultQueryRunWindow();
	
	public long getQueryRunWindow(Resource assertionResource);
	
	public long getDefaultInferenceRunWindow();
	
	public long getInferenceRunWindow(Resource assertionResource);
	
	
	/* ================ Statistics for ContextAssertion insertions ================ */
	// public EngineInsertionStats getInsertionStatistics();
	public boolean assertionUpdatesEnabled(Resource assertionResource);
	
	public List<Resource> getEnabledAssertions();
	
	/* ================ Statistics for ContextAssertion inferences ================ */
	public EngineInferenceStats getInferenceStatistics();
	
	public ContextDerivationRule getLastDerivation();
	
	public int nrDerivations(Resource derivedAssertionResource);
	
	public int nrSuccessfulDerivations(Resource derivedAssertionResource);
	
	/* ================ Statistics for ContextAssertion queries ================ */
	public EngineQueryStats getQueryStatistics();
	
	public int nrQueries(Resource assertionResource);
	
	public int nrSuccessfulQueries(Resource assertionResource);
	
	public long timeSinceLastQuery(Resource assertionResource);
	
	/* ================ Statistics for CONSERT Engine Performance Testing ================ */
	public PerformanceResult measurePerformance();
}
