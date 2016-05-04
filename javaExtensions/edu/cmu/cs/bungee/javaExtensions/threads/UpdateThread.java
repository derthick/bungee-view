package edu.cmu.cs.bungee.javaExtensions.threads;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A QueueThread where only the most recently added queue entry is relevant. The
 * queue is cleared each time that entry is processed.
 *
 * @author mad
 *
 */
public abstract class UpdateThread<T> extends QueueThread<T> {

	/**
	 * @param name
	 *            useful for debugging
	 * @param deltaPriority
	 *            this thread's priority relative to the caller's priority
	 */
	public UpdateThread(final @NonNull String name, final int deltaPriority) {
		super(name, deltaPriority);
	}

	@Override
	protected T get() {
		@Nullable
		T result = null;
		if (!exited) {
			try {
				result = queue.takeLast();
				queue.clear();
			} catch (final InterruptedException e) {
				// e.printStackTrace();
			}
		}
		return exited ? null : result;
	}
}
