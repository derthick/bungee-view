package edu.cmu.cs.bungee.client.query.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;

/**
 * Intended to be an argument to javax.swing.SwingUtilities.invokeLater. Just
 * calls all the callbacks.
 *
 * @see RedrawCallback
 */
public class Redraw implements Runnable {

	private static final Map<Query, Redraw> REDRAWS = new IdentityHashMap<>(1);

	private final @NonNull Query query;

	private final @NonNull Set<RedrawCallback> callbacks = new HashSet<>();

	/**
	 * Called only by CallbackQueueThread.process(), after waitForValidQuery.
	 *
	 * Never call in mouse process. Use Query.queueRedraw() instead.
	 */
	public static void redraw(final Set<RedrawCallback> _callbacks, final @NonNull Query _query) {
		final Redraw redraw = getRedraw(_query);
		boolean isAdded;
		synchronized (redraw.callbacks) {
			isAdded = redraw.callbacks.addAll(_callbacks);
		}
		if (isAdded) {
			_query.queryInvokeLater(redraw);
		}
	}

	public static boolean isCallbackQueued(final @NonNull RedrawCallback callback, final @NonNull Query _query) {
		boolean result;
		// synchronized (getRedraw(_query).callbacks) {
		result = getRedraw(_query).callbacks.contains(callback);
		// }
		return result;
	}

	private static @NonNull Redraw getRedraw(final @NonNull Query _query) {
		Redraw result = REDRAWS.get(_query);
		if (result == null) {
			result = new Redraw(_query);
			REDRAWS.put(_query, result);
		}
		return result;
	}

	private Redraw(final @NonNull Query _query) {
		super();
		query = _query;
	}

	@Override
	public void run() {
		assert Util.assertMouseProcess();
		while (query.isAlive() && callbacks.size() > 0) {
			// copy to avoid ConcurrentModificationException
			List<RedrawCallback> callbacksCopy;
			synchronized (callbacks) {
				callbacksCopy = new ArrayList<>(callbacks);
			}
			final List<RedrawCallback> toRemove = new LinkedList<>();
			for (final RedrawCallback callback : callbacksCopy) {
				assert callback != null;
				toRemove.add(callback);
				query.queueOrRedraw(callback);
			}
			synchronized (callbacks) {
				callbacks.removeAll(toRemove);
			}
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + callbacks.hashCode();
		result = prime * result + query.hashCode();
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Redraw other = (Redraw) obj;
		if (!callbacks.equals(other.callbacks)) {
			return false;
		}
		if (!query.equals(other.query)) {
			return false;
		}
		return true;
	}
}
