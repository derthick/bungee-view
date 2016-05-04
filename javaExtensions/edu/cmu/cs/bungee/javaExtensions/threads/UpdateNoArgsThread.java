package edu.cmu.cs.bungee.javaExtensions.threads;

import org.eclipse.jdt.annotation.NonNull;

/**
 * An UpdateThread where process takes no arguments. update just means
 * "make sure to call process when you get a chance".
 *
 * @author mad
 *
 */
public abstract class UpdateNoArgsThread extends UpdateThread<Object> {

	/**
	 * @param name
	 *            useful for debugging
	 * @param deltaPriority
	 *            this thread's priority relative to the caller's priority
	 */
	public UpdateNoArgsThread(final @NonNull String name, final int deltaPriority) {
		super(name, deltaPriority);
	}

	/**
	 * Request that process be called.
	 *
	 * @return whether the queue was updated (won't be if there's already a
	 *         request queued).
	 */
	final public boolean update() {
		return add(this);
	}

	@Override
	final public void process(@SuppressWarnings("unused") final Object ignore) {
		process();
	}

	/**
	 * Override this to carry out the queued request.
	 */
	public abstract void process();
}
