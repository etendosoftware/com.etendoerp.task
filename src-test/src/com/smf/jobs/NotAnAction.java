package com.smf.jobs;

/**
 * Dummy class that is NOT an Action, used to test error handling when a process class
 * does not implement the Action contract.
 */
public class NotAnAction {

	public NotAnAction() {
		// Intentionally empty: this class is a test fixture used to verify
		// TaskUtil.runAction behavior when a process class does not implement
		// the Action contract. Keeping the constructor simple avoids
		// introducing unintended side effects during test class loading.
	}

}
