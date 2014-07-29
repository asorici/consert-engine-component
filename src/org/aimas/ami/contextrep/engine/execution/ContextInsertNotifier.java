package org.aimas.ami.contextrep.engine.execution;

import java.util.LinkedList;
import java.util.List;

import org.aimas.ami.contextrep.engine.api.AssertionUpdateListener;
import org.aimas.ami.contextrep.model.ContextAssertion;

public class ContextInsertNotifier {
	private static ContextInsertNotifier instance = null;
	
	private List<AssertionUpdateListener> registeredListeners;
	
	private ContextInsertNotifier() {
		registeredListeners = new LinkedList<AssertionUpdateListener>();
	}
	
	public void addUpdateListener(AssertionUpdateListener updateListener) {
		synchronized(registeredListeners) {
			registeredListeners.add(updateListener);
		}
	}
	
	public void removeUpdateListener(AssertionUpdateListener updateListener) {
		synchronized(registeredListeners) {
			registeredListeners.remove(updateListener);
		}
	}
	
	public void notifyAssertionUpdated(ContextAssertion contextAssertion) {
		synchronized(registeredListeners) {
			for (AssertionUpdateListener updateListener : registeredListeners) {
				updateListener.notifyAssertionUpdated(contextAssertion);
			}
		}
	}
	
	public static ContextInsertNotifier getInstance() {
		if (instance == null) {
			instance = new ContextInsertNotifier();
		}
		
		return instance;
	}
}
