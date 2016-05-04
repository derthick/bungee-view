package edu.cmu.cs.bungee.javaExtensions.threads;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

/**
 * A QueueThread where everything that has accumulated on the queue is processed
 * at once. queue is unique (no duplicates).
 */
public abstract class AccumulatingQueueThread<T> extends QueueThread<T> {

	/**
	 * @param name
	 * @param deltaPriority
	 */
	public AccumulatingQueueThread(final @NonNull String name, final int deltaPriority) {
		super(name, deltaPriority);
	}

	private List<T> getAllQueued() {
		// Util.indent("AccumulatingQueueThread.getAllQueued enter");

		List<T> results = null;
		if (!exited) {
			try {
				// blocks until queue is non-empty
				results = new LinkedList<>();
				final T result = queue.takeFirst();
				queue.drainTo(results);
				results.add(0, result);
			} catch (final InterruptedException e) {
				// e.printStackTrace();
			}
		}
		// Util.indent("AccumulatingQueueThread.getAllQueued return " +
		// results);
		return exited ? null : results;
	}

	@Override
	public void run() {
		init();
		List<T> ts;
		while ((ts = getAllQueued()) != null) {
			try {
				processing = true;
				process(ts);

			} catch (final Throwable e) {
				reportError(e, ts);

			} finally {
				postProcess();
			}
		}
	}

	private void reportError(final Throwable e, final List<T> ts) {
		System.err.println("While processing " + ts + " " + myThread + " ignoring exception:\n queue=" + queue);
		e.printStackTrace();
	}

	// no need to synchronize; will always be called by run (in this thread)
	protected abstract void process(@NonNull List<T> ts) throws SQLException;

	@Override
	public void process(@SuppressWarnings("unused") final T ts) {
		assert false : this + ": for AccumulatingQueueThread, process takes a List";
	}

	@Override
	protected T get() {
		assert false : this + ": for AccumulatingQueueThread, use getAllQueued";
		return null;
	}

}
