package uk.org.bobulous.java.intervals;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Maintains a sorted set (by lower endpoint, then upper endpoint) of
 * non-overlapping Interval<T>s.
 *
 * Supports iterating, inclusion testing, adding and subtracting.
 */
@Immutable
public class Intervals<T extends Comparable<? super T>> implements Iterable<Interval<T>>, Serializable {

	protected static final long serialVersionUID = 1L;

	protected final @NonNull SortedSet<Interval<T>> myIntervals = new TreeSet<>();

	Intervals(final Intervals<T> intervals) {
		this();
		myIntervals.addAll(intervals.myIntervals);
	}

	public Intervals(final @NonNull Interval<T> interval) {
		this();
		myIntervals.add(interval);
	}

	public Intervals() {
		super();
	}

	public boolean includes(final @NonNull T included) {
		boolean result = false;
		for (final Interval<T> interval : myIntervals) {
			if (interval.includes(included)) {
				result = true;
				break;
			}
		}
		return result;
	}

	public boolean includes(final @NonNull Interval<T> included) {
		boolean result = false;
		for (final Interval<T> interval : myIntervals) {
			if (interval.includes(included)) {
				result = true;
				break;
			}
		}
		return result;
	}

	public boolean includes(final @NonNull Intervals<T> included) {
		boolean result = true;
		for (final Interval<T> includedInterval : included.myIntervals) {
			if (!includes(Util.nonNull(includedInterval))) {
				result = false;
				break;
			}
		}
		return result;
	}

	public boolean overlaps(final @NonNull Interval<T> overlappingInterval) {
		boolean result = false;
		for (final Interval<T> interval : myIntervals) {
			if (interval.overlaps(overlappingInterval)) {
				result = true;
				break;
			}
		}
		return result;
	}

	public boolean overlaps(final @NonNull Intervals<T> intervals) {
		boolean result = false;
		for (final Interval<T> overlappingInterval : intervals.myIntervals) {
			if (overlaps(Util.nonNull(overlappingInterval))) {
				result = true;
				break;
			}
		}
		return result;
	}

	public @NonNull Intervals<T> add(final @NonNull Interval<T> interval) {
		assert interval != null;
		Intervals<T> result = this;
		if (!includes(interval)) {
			result = new Intervals<>(this);
			result.myIntervals.add(interval);
			result.coalesce();
		}
		return result;
	}

	public @NonNull Intervals<T> subtract(final @NonNull Intervals<T> _intervals) {
		final Intervals<T> result = new Intervals<>(this);
		boolean isChanged = false;
		for (final Interval<T> interval : _intervals.myIntervals) {
			isChanged = result.subtractDestructive(Util.nonNull(interval)) || isChanged;
		}
		return isChanged ? result : this;
	}

	public @NonNull Intervals<T> subtract(final @NonNull Interval<T> toSubtract) {
		final Intervals<T> result = new Intervals<>(this);
		final boolean isChanged = result.subtractDestructive(toSubtract);
		return isChanged ? result : this;
	}

	public boolean subtractDestructive(final @NonNull Interval<T> toSubtract) {
		boolean result = false;
		final Collection<Interval<T>> toAdd = new LinkedList<>();
		for (final Iterator<Interval<T>> it = myIntervals.iterator(); it.hasNext();) {
			@SuppressWarnings("null")
			final @NonNull Interval<T> interval = it.next();
			if (toSubtract.overlaps(interval)) {
				result = true;
				it.remove();
				if (!toSubtract.includes(interval)) {
					final boolean includesToSubtract = interval.includes(toSubtract);
					final int lowerCompareTo = toSubtract.compareLowerEndpoint(interval);
					final int upperCompareTo = toSubtract.compareUpperEndpoint(interval);
					if ((includesToSubtract && upperCompareTo < 0) || lowerCompareTo <= 0) {
						// There's room at the top
						final @Nullable T toSubtractUpperEndpoint = toSubtract.getUpperEndpoint();
						final GenericInterval<T> newInterval = new GenericInterval<>(
								toSubtract.getUpperEndpointMode().complement(), toSubtractUpperEndpoint,
								interval.getUpperEndpoint(), interval.getUpperEndpointMode());
						toAdd.add(newInterval);
					}
					if ((includesToSubtract && lowerCompareTo > 0) || upperCompareTo >= 0) {
						// There's room at the bottom
						final GenericInterval<T> newInterval = new GenericInterval<>(interval.getLowerEndpointMode(),
								interval.getLowerEndpoint(), toSubtract.getLowerEndpoint(),
								toSubtract.getLowerEndpointMode().complement());
						toAdd.add(newInterval);
					}
				}
			}
		}
		myIntervals.addAll(toAdd);
		return result;
	}

	/**
	 * merge adjacent/overlapping/containing intervals
	 */
	private boolean coalesce() {
		// // Removing inside the loops gives ConcurrentModificationException
		final List<Interval<T>> toRemove = new LinkedList<>();
		final List<Interval<T>> toAdd = new LinkedList<>();

		Interval<T> prev = null;
		for (final Iterator<Interval<T>> it = myIntervals.iterator(); it.hasNext();) {
			Interval<T> interval = it.next();
			assert interval != null;
			if (prev != null) {
				if (prev.overlapsLowerEndpoint(interval)) {
					// includes the case where prev.includes(interval)
					final Interval<T> upperGreaterInterval = prev.compareUpperEndpoint(interval) > 0 ? prev : interval;
					interval = new GenericInterval<>(prev, upperGreaterInterval);
					toAdd.add(interval);
					it.remove();
					toRemove.add(prev);
				} else if (interval.includes(prev)) {
					toRemove.add(prev);
				}
			}
			prev = interval;
		}
		final boolean result = toAdd.size() > 0 || toRemove.size() > 0;
		if (result) {
			myIntervals.removeAll(toRemove);
			myIntervals.addAll(toAdd);
		}
		return result;
	}

	@Override
	public Iterator<Interval<T>> iterator() {
		return myIntervals.iterator();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, myIntervals);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + myIntervals.hashCode();
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
		final Intervals<?> other = (Intervals<?>) obj;
		if (!myIntervals.equals(other.myIntervals)) {
			return false;
		}
		return true;
	}
}
