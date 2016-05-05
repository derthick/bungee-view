package edu.cmu.cs.bungee.client.query.query;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.query.CallbackQueueThread.CallbackSpec;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.threads.AccumulatingQueueThread;

/**
 * Stores a Query to enable waitForValidQuery and Redraw (which calls
 * isQueryValid)
 */
abstract class CallbackQueueThread<S extends Comparable<?>> extends AccumulatingQueueThread<CallbackSpec<S>> {

	protected final @NonNull Query query;

	/**
	 * Call query.waitForValidQuery() before processClassSpecificInfos? (always
	 * call waitForValidQuery before processCallbacks)
	 */
	private final boolean isWaitForValidQuery;

	CallbackQueueThread(final @NonNull String name, final int deltaPriority, final @NonNull Query _query,
			final boolean _isWaitForValidQuery) {
		super(name, deltaPriority);
		query = _query;
		assert query != null;
		isWaitForValidQuery = _isWaitForValidQuery;
	}

	synchronized boolean isCallbackQueued(final @NonNull RedrawCallback callback) {
		for (final CallbackSpec<S> callbackSpec : queue) {
			if (callbackSpec.callback == callback) {
				return true;
			}
		}
		return false;
	}

	synchronized boolean addAll(final @NonNull Collection<S> classSpecificInfos,
			final @Nullable RedrawCallback callback) {
		boolean result = false;
		for (final S classSpecificInfo : classSpecificInfos) {
			if (add(classSpecificInfo, callback)) {
				result = true;
			}
		}
		return result;
	}

	/**
	 * @return whether CallbackSpec was newly added.
	 */
	synchronized boolean add(final @Nullable S classSpecificInfo, final @Nullable RedrawCallback callback) {
		return add(new CallbackSpec<>(classSpecificInfo, callback));
	}

	final Set<RedrawCallback> callbacks = new HashSet<>();

	@Override
	protected void process(final @NonNull List<CallbackSpec<S>> callbackSpecs) {
		final Set<S> classSpecificInfos = new LinkedHashSet<>();
		for (final CallbackSpec<S> callbackSpec : callbackSpecs) {
			if (callbackSpec.classSpecificInfo != null) {
				classSpecificInfos.add(callbackSpec.classSpecificInfo);
			}
			if (callbackSpec.callback != null) {
				callbacks.add(callbackSpec.callback);
			}
		}
		if (classSpecificInfos.size() > 0) {
			// don't waitForValidQuery if no classSpecificInfos
			if (isWaitForValidQuery && !query.waitForValidQuery()) {
				exit();
				return;
			}
			processClassSpecificInfos(classSpecificInfos);
		}
		// Check isQueueEmptyNnotExited() to minimize duplicate callbacks
		if (isQueueEmptyNnotExited() && !callbacks.isEmpty() && query.waitForValidQuery()) {
			Redraw.redraw(callbacks, query);
			callbacks.clear();
		}
	}

	/**
	 * If isWaitForValidQuery, only called when isQueryValid.
	 */
	abstract protected void processClassSpecificInfos(final @NonNull Collection<S> classSpecificInfos);

	static class CallbackSpec<S extends Comparable<?>> {
		final @Nullable S classSpecificInfo;
		final @Nullable RedrawCallback callback;

		CallbackSpec(final @Nullable S _classSpecificInfo, final @Nullable RedrawCallback redrawCallback) {
			// allow _classSpecificInfo==null meaning callback when isQueryValid
			assert !(_classSpecificInfo instanceof CallbackSpec) : _classSpecificInfo;
			classSpecificInfo = _classSpecificInfo;
			callback = redrawCallback;
		}

		@Override
		public String toString() {
			return UtilString.toString(this,
					"[classSpecificInfo=" + classSpecificInfo + ", callback=" + callback + "]");
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			final int i = (callback != null) ? callback.hashCode() : 0;
			result = prime * result + i;
			result = prime * result + ((classSpecificInfo != null) ? classSpecificInfo.hashCode() : 0);
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
			@SuppressWarnings("unchecked")
			final CallbackSpec<S> other = (CallbackSpec<S>) obj;
			if (callback == null) {
				if (other.callback != null) {
					return false;
				}
			} else if (!Util.nonNull(callback).equals(other.callback)) {
				return false;
			}
			if (classSpecificInfo == null) {
				if (other.classSpecificInfo != null) {
					return false;
				}
			} else if (!Util.nonNull(classSpecificInfo).equals(other.classSpecificInfo)) {
				return false;
			}
			return true;
		}

	}

}