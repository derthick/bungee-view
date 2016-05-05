package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.umd.cs.piccolo.PNode;

public class PNodePool<V, Z extends PNode> {

	private final Map<V, Z> zCache;

	/**
	 * Cached Zs aren't associated with a particular V
	 */
	private final Queue<Z> interchangableZCache;

	public PNodePool(final boolean isInterchangableCache) {
		interchangableZCache = isInterchangableCache ? new LinkedList<Z>() : null;
		zCache = (!isInterchangableCache) ? new Hashtable<V, Z>() : null;
	}

	public void clear() {
		if (zCache != null) {
			zCache.clear();
		} else {
			interchangableZCache.clear();
		}
	}

	void putAll(final @NonNull Map<V, Z> map) {
		if (zCache != null) {
			zCache.putAll(map);
		} else {
			interchangableZCache.addAll(map.values());
		}
	}

	public boolean put(final @NonNull V v, final @NonNull Z z) {
		boolean wasAdded;
		if (zCache != null) {
			wasAdded = UtilArray.putNew(zCache, v, z, "");
		} else {
			wasAdded = interchangableZCache.offer(z);
		}
		return wasAdded;
	}

	public @Nullable Z pop(final @NonNull V v) {
		final Z cached = zCache != null ? zCache.remove(v) : interchangableZCache.poll();
		assert cached == null || cached.getParent() == null;
		return cached;
	}

	// TODO Remove unused code found by UCDetector
	// @Nullable
	// Z some() {
	// final Z cached = zCache != null ? UtilArray.some(zCache.values()) :
	// interchangableZCache.peek();
	// assert cached == null || cached.getParent() == null;
	// return cached;
	// }

}
