package edu.cmu.cs.bungee.javaExtensions.comparator;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Sorts from highest to lowest.
 */
public abstract class IntValueComparator<V> implements Comparator<V>, Serializable {

	@Override
	public int compare(final V v1, final V v2) {
		return value(v2) - value(v1);
	}

	// TODO Remove unused code found by UCDetector
	// public boolean equals(V data1, V data2) {
	// return value(data1) == value(data2);
	// }

	public abstract int value(V v);

}
