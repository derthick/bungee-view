package edu.cmu.cs.bungee.javaExtensions.threads;

import static edu.cmu.cs.bungee.javaExtensions.UtilString.now;

import java.sql.SQLException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A thread that maintains a FIFO queue of objects (Ts) on which it calls
 * process.
 *
 * The thread exits when run() does, that is, when get() returns null.
 */
public abstract class QueueThread<T> implements Runnable {

	protected final @NonNull BlockingDeque<T> queue;
	protected boolean processing = false;
	/**
	 * myThread terminates when run() exits, which it does when exited becomes
	 * true.
	 */
	protected volatile boolean exited = false;
	protected Thread myThread = null;

	private final @NonNull String name;
	private final int deltaPriority;

	protected long lastActiveTime = 0L;

	/**
	 * Should reportError() call exit()?
	 */
	private volatile boolean exitOnError = false;

	/**
	 * @param _name
	 *            useful for debugging
	 * @param _deltaPriority
	 *            this thread's priority relative to the caller's priority
	 */
	protected QueueThread(final @NonNull String _name, final int _deltaPriority) {
		name = _name;
		deltaPriority = _deltaPriority;
		queue = new LinkedBlockingDeque<>();
	}

	public void reset() {
		assert exited == false;
		queue.clear();
		exitOnError = false;
		System.out.println("...resetting " + myThread + " priority=" + myThread.getPriority());
		// exited = true;
		// myThread.interrupt();
		// processing = false;
		// lastActiveTime = 0L;
	}

	/**
	 * put t on the queue. If it's already there, remove it and add it at the
	 * end. (queue never has duplicates).
	 *
	 * @param t
	 * @return whether t was newly added.
	 */
	public boolean add(final @NonNull T t) {
		final boolean result = !exited && !queue.remove(t);
		if (!exited) {
			queue.add(t);
			// Often get() will be waiting, and grab t before add even returns.
			// assert queue.contains(t) : result + " " + t + " " + queue;
		}
		return result;
	}

	protected @Nullable T get() {
		@Nullable
		T result = null;
		if (!exited) {
			try {
				result = queue.takeFirst();
			} catch (final InterruptedException e) {
				// e.printStackTrace();
			}
		}
		return exited ? null : result;
	}

	/**
	 * @return true iff our queue is empty. Might or might not be processing the
	 *         last queue entry.
	 */
	public boolean isQueueEmpty() {
		return queue.isEmpty();
	}

	public boolean isQueueEmptyNnotExited() {
		return !exited && queue.isEmpty();
	}

	public long lastActiveTime() {
		return isQueueEmpty() && !processing ? lastActiveTime : Long.MAX_VALUE;
	}

	/**
	 * Clear the queue and stop accepting add's to it. Any current process call
	 * will complete.
	 */
	public void exit() {
		if (myThread != null) {
			System.out.println("...exiting " + myThread + " priority=" + myThread.getPriority());
			exited = true;
			myThread.interrupt();
		}
	}

	/**
	 * Called when this Thread starts.
	 */
	protected void init() {
		myThread = Thread.currentThread();
		myThread.setName(name);
		if (deltaPriority != 0) {
			final int priority = Math.max(Thread.MIN_PRIORITY + 1, myThread.getPriority() + deltaPriority);
			myThread.setPriority(priority);
		}
	}

	@Override
	// myThread terminates when run() exits, which it does when exited becomes
	// true.
	public void run() {
		init();
		@Nullable
		T t;
		while ((t = get()) != null) {
			try {
				processing = true;
				process(t);
			} catch (final Throwable e) {
				reportError(e, t);
			} finally {
				postProcess();
			}
		}
	}

	protected void postProcess() {
		processing = false;
		lastActiveTime = now();
	}

	protected void reportError(final @NonNull Throwable e, final @NonNull T t) {
		final boolean _exitOnError = getExitOnError(t);
		System.err.println("While processing " + t + " " + myThread + (_exitOnError ? " barfing on" : " ignoring")
				+ " exception:\n queue=" + queue);
		e.printStackTrace();
		if (_exitOnError) {
			exit();
		}
	}

	public boolean getExited() {
		return exited;
	}

	public void setExitOnError(final boolean _exitOnError) {
		exitOnError = _exitOnError;
	}

	public boolean getExitOnError(@SuppressWarnings("unused") final @NonNull T t) {
		return exitOnError;
	}

	/**
	 * This is the whole point of QueueThread. Override this to process each
	 * queue addition.
	 *
	 * @param t
	 *            arbitrary argument from the queue.
	 * @throws SQLException
	 */
	protected abstract void process(@NonNull T t) throws SQLException;
}
