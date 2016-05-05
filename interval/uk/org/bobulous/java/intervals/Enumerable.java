package uk.org.bobulous.java.intervals;

import java.io.Serializable;

public interface Enumerable<T extends Comparable<? super T>> extends Serializable {
	public T next();

	public T previous();

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return for any sensible Interval i, (i.upperEndpoint -
	// i.lowerEndpoint)
	// * must be the size of the collection (adjusting for endpoint mode).
	// */
	// public int ordinal();
}
