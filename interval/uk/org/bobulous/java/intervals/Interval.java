/*
 * Copyright © 2014 Bobulous <http://www.bobulous.org.uk/>.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.org.bobulous.java.intervals;

import java.io.Serializable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * An interval within a naturally ordered type. The natural order is defined by
 * the <code>compareTo</code> method of the type. The comparison is first by
 * lowerEndpoint, then by upperEndpoint.
 * <p>
 * An interval has a lower endpoint and an upper endpoint, each having a value
 * of the type which forms the basis of the interval, and each having a mode of
 * either open or closed. An interval includes all values which are permitted by
 * <strong>both</strong> the lower <strong>and</strong> upper endpoints. A
 * closed lower endpoint permits all values which are greater-than-or-equal-to
 * the lower endpoint value; an open lower endpoint permits all values which are
 * greater than the lower endpoint value. A closed upper endpoint permits all
 * values which are less-than-or-equal-to the upper endpoint value; an open
 * upper endpoint permits all values which are less than the upper endpoint
 * value.
 * </p>
 * <p>
 * If an endpoint is <code>null</code> then it means there is no limit to what
 * is included in that direction. So a <code>null</code> lower endpoint means
 * that all values permitted by the upper endpoint are included in the interval;
 * a <code>null</code> upper endpoint means that all values permitted by the
 * lower endpoint are included in the interval; and if both endpoints are
 * <code>null</code> then all values are included in this interval. Note that
 * <code>null</code> itself is never included in an interval, as a null object
 * represents the lack of a value. Be aware that null endpoints will allow any
 * value of the permitted type, including values such as <code>Double.NaN</code>
 * (which is supposed to represent a non-value value).
 * </p>
 * <p>
 * Also note that a type constant which is intended to represent infinity, such
 * as <code>Double.POSITIVE_INFINITY</code>, is just another numeric value so
 * far as the <code>Comparable</code> interface is concerned, and is therefore
 * just another member of the naturally ordered set Double so far as
 * <code>Interval&lt;Double&gt;</code> is concerned. Specifying such a value for
 * an endpoint is not the same as specifying <code>null</code> because
 * <code>null</code> will admit any value belonging to the interval basis type,
 * whereas a pseudo-infinite value such as <code>Double.POSITIVE_INFINITY</code>
 * will exclude any values which are beyond it in the natural order of the basis
 * type. For example, <code>Double.NaN</code> is considered greater than
 * <code>Double.NEGATIVE_INFINITY</code> and also greater than
 * <code>Double.POSITIVE_INFINITY</code> according to the <code>compareTo</code>
 * method of <code>Double</code>.
 * </p>
 * <p>
 * An implementation of <code>Interval</code> may or may not permit
 * <code>null</code> endpoint values, and it must make clear in its
 * documentation what is permitted.
 * </p>
 * <p>
 * An interval can include zero, one or both of its endpoint values. This is
 * specified by the mode of each endpoint. {@link EndpointMode#CLOSED} means
 * that the endpoint value itself is permitted by the endpoint, while
 * {@link EndpointMode#OPEN} means that the endpoint value itself is not
 * permitted by the endpoint. For instance, an
 * <code>Interval&lt;Double&gt;</code> might have a lower endpoint value of zero
 * and a lower endpoint mode of <code>EndpointMode.OPEN</code> which means that
 * zero is the lower limit of the interval but is not included in the interval.
 * The endpoint mode is irrelevant for a null endpoint.
 * </p>
 * <p>
 * An implementation of <code>Interval</code> may permit both open and closed
 * endpoints, or may permit only one mode, and it must make the options clear in
 * its documentation.
 * </p>
 *
 *
 * @author Bobulous <http://www.bobulous.org.uk/>
 * @param <T>
 *            the basis type of this <code>Interval</code>. The basis type must
 *            implement <code>Comparable&lt;T&gt;</code> so that each instance
 *            of the type can be compared with other instances of the same type,
 *            thus being a type which has a natural order.
 * @see GenericInterval // * @see IntervalComparator
 */
@Immutable
public interface Interval<T extends Comparable<? super T>> extends Serializable {

	/**
	 * An enumerated type which contains values to specify endpoint mode.
	 * <p>
	 * An endpoint can be either open (endpoint value is excluded from interval)
	 * or closed (endpoint value is included in interval).
	 * </p>
	 */
	static enum EndpointMode {

		/**
		 * The endpoint value is <strong>excluded</strong>.
		 */
		OPEN,

		/**
		 * The endpoint value is <strong>included</strong>.
		 */
		CLOSED;

		@NonNull
		EndpointMode complement() {
			return this == OPEN ? CLOSED : OPEN;
		}
	}

	/**
	 * Returns the lower endpoint value of this interval.
	 *
	 * @return the value of the lower endpoint, or <code>null</code> if there is
	 *         no lower bound for this interval.
	 */
	public @Nullable T getLowerEndpoint();

	/**
	 * Returns the upper endpoint value of this interval.
	 *
	 * @return the value of the upper endpoint, or <code>null</code> if there is
	 *         no upper bound for this interval.
	 */
	public @Nullable T getUpperEndpoint();

	/**
	 * Returns the endpoint mode of the lower endpoint of this interval.
	 *
	 * @return the mode of the lower endpoint.
	 */
	public @NonNull EndpointMode getLowerEndpointMode();

	/**
	 * Returns the endpoint mode of the upper endpoint of this interval.
	 */
	public @NonNull EndpointMode getUpperEndpointMode();

	/**
	 * Whether this interval includes value.
	 */
	public boolean includes(@NonNull T value); // NO_UCD (unused code)

	/**
	 * Whether there is any point in common with interval.
	 *
	 * @return !isBefore(interval) && !interval.isBefore(this)
	 */
	public boolean overlaps(final @NonNull Interval<T> interval); // NO_UCD
																	// (unused
																	// code)

	/**
	 * @return whether our upper is less than interval's lower (or they're equal
	 *         but at least one's mode is OPEN)
	 */
	public boolean isBefore(final @NonNull Interval<T> interval);

	/**
	 * @return an Interval including exactly the points common to this and
	 *         otherInterval.
	 */
	public @NonNull Interval<T> intersection(final @NonNull Interval<T> otherInterval);

	/**
	 * @return whether our lower is less than interval's lower and our upper is
	 *         greater than or equal to interval's lower
	 */
	public boolean overlapsLowerEndpoint(final @NonNull Interval<T> interval);

	/**
	 * @return whether our upper is greater than interval's upper and our lower
	 *         is less than or equal to interval's upper
	 */
	public boolean overlapsUpperEndpoint(final @NonNull Interval<T> interval); // NO_UCD
	// (unused
	// code)

	/**
	 * @return Our lower endpoint compared to interval's, taking into account
	 *         nulls and endpoint modes.
	 */
	public <S extends Comparable<? super S>> int compareLowerEndpoint(final Interval<S> interval);

	/**
	 * @return Our lower endpoint compared to endpoint2's, taking into account
	 *         nulls and modes.
	 */
	public <S extends Comparable<? super S>> int compareLowerEndpoint(final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2);

	/**
	 * @return endpoint1 compared to endpoint2, treated as lower endpoints,
	 *         taking into account nulls and modes.
	 */
	public <S extends Comparable<? super S>> int compareLowerEndpoint(final @Nullable S endpoint1,
			final @NonNull EndpointMode endpointMode1, final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2);

	/**
	 * @return Our upper endpoint compared to interval's, taking into account
	 *         nulls and endpoint modes.
	 */
	public <S extends Comparable<? super S>> int compareUpperEndpoint(final Interval<S> interval);

	/**
	 * @return Our upper endpoint compared to endpoint2's, taking into account
	 *         nulls and modes.
	 */
	public <S extends Comparable<? super S>> int compareUpperEndpoint(final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2);

	/**
	 * @return endpoint1 compared to endpoint2, treated as upper endpoints,
	 *         taking into account nulls and modes.
	 */
	public <S extends Comparable<? super S>> int compareUpperEndpoint(final @Nullable S endpoint1,
			final @NonNull EndpointMode endpointMode1, final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2);

	/**
	 * Whether this interval includes every point in interval.
	 */
	public boolean includes(@NonNull Interval<T> interval); // NO_UCD (unused
															// code)

	/**
	 * @return whether lower endpoint mode==CLOSED and upper endpoint mode==OPEN
	 */
	public boolean isOpenUpperClosedLower();

	/**
	 * @return whether upper==lower and modes are both OPEN
	 */
	public boolean isEmpty();
}
