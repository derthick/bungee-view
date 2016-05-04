package edu.cmu.cs.bungee.javaExtensions.comparator;

import java.util.Comparator;

import edu.cmu.cs.bungee.javaExtensions.UtilMath;

/**
 * Sorts from highest to lowest
 *
 */
public abstract class DoubleValueComparator<V> implements Comparator<V> {

	@Override
	public int compare(final V data1, final V data2) {
		return UtilMath.sgn(value(data2) - value(data1));
	}

	// TODO Remove unused code found by UCDetector
	// public boolean equals(final V data1, final V data2) {
	// return value(data1) == value(data2);
	// }

	public abstract double value(V data);

}
