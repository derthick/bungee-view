/*
 * Copyright © 2014 Bobulous <http://www.bobulous.org.uk/>.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package uk.org.bobulous.java.intervals;

import java.util.Objects;

import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * A concrete implementation of an <code>Interval</code> over any naturally
 * ordered type.
 * <p>
 * Each <code>GenericInterval</code> contains a lower endpoint value and an
 * upper endpoint value such that the result of
 * <code>upper.compareTo(lower)</code> must be zero or greater. In other words,
 * the upper endpoint must be equal to or greater than the lower endpoint
 * according to the natural ordering of the interval basis type.
 * </p>
 * <p>
 * For example, if an <code>GenericInterval&lt;Integer&gt;</code> object (an
 * interval of integers) has a lower endpoint value of zero then its upper
 * endpoint value must be zero or greater. If an
 * <code>GenericInterval&lt;Character&gt;</code> (an interval of character
 * values) had a lower endpoint value of <code>'a'</code> then its upper
 * endpoint value would have to be <code>'a'</code> or <code>'b'</code> or any
 * other character which causes <code>upper.compareTo(lower)</code> to return
 * zero or greater.
 * </p>
 * <p>
 * If an endpoint is <code>null</code> then it means that the endpoint is
 * unbounded and there is no limit to what is included in that direction. So a
 * <code>null</code> lower endpoint means that the lower endpoint is unbounded
 * and the interval includes every value which is less than the upper endpoint
 * value; a <code>null</code> upper endpoint means that the upper endpoint is
 * unbounded and the interval includes every value greater than the lower
 * endpoint value; and if both endpoints are <code>null</code> then all values
 * are included in this interval. In effect, a lower endpoint of
 * <code>null</code> equates to a lower endpoint having the value negative
 * infinity, and an upper endpoint of <code>null</code> equates to an upper
 * endpoint having the value positive infinity. Note that <code>null</code>
 * itself is <strong>never</strong> included in an interval, as
 * <code>null</code> represents the lack of a value. Be aware that
 * <code>null</code> endpoints will allow any value of the permitted type,
 * including values such as <code>Double.NaN</code> (which is supposed to
 * represent a non-value value).
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
 * An interval can include zero, one or both of its endpoint values. This is
 * specified by the mode of each endpoint. {@link Interval.EndpointMode#CLOSED
 * EndpointMode.CLOSED} means that the endpoint value itself is included in the
 * interval, while {@link Interval.EndpointMode#OPEN EndpointMode.OPEN} means
 * that the endpoint value itself is not included in the interval. For instance,
 * an <code>Interval&lt;Double&gt;</code> might have a lower endpoint value of
 * zero and a lower endpoint mode of <code>EndpointMode.OPEN</code> which means
 * that zero is the lower limit of the interval but is not included in the
 * interval. The endpoint mode is irrelevant for a null endpoint.
 * </p>
 * <p>
 * <strong>Warning:</strong> Because an <code>Interval</code> relies on the
 * <code>Comparable</code> interface, everything depends on the result returned
 * by the <code>compareTo</code> method. So be aware that a <code>Double</code>
 * object with value <code>-0.0</code> (negative zero) is considered by
 * compareTo to be less in value than a <code>Double</code> object with value
 * <code>0.0</code> (positive zero) even though almost all applications would
 * consider these two values to be identical. Be careful not to allow a negative
 * zero to be used as an endpoint value without understanding that it will be
 * treated differently.
 * </p>
 * <p>
 * Objects of type <code>GenericInterval</code> cannot be mutated, but it is
 * only safe to consider them truly immutable if they are based on an immutable
 * type. So a <code>GenericInterval&lt;String&gt;</code> is truly immutable
 * because neither the <code>GenericInterval</code> nor the <code>String</code>
 * objects acting as endpoints can be mutated. But a
 * <code>GenericInterval&lt;Date&gt;</code> cannot be considered immutable
 * because even though the <code>GenericInterval</code> cannot be mutated, the
 * <code>Date</code> objects acting as endpoints can be mutated. This also means
 * that a <code>GenericInterval</code> of a mutable type cannot be guaranteed to
 * maintain the order of its endpoints, so the lower endpoint may suddenly be
 * mutated to have a greater value than the upper endpoint. For this reason it
 * is strongly recommended that <code>GenericInterval</code> is only used with
 * immutable types. If a <code>GenericInterval</code> is created over a mutable
 * type, then both the <code>GenericInterval</code> and its endpoint objects
 * must be kept safe, and must not be shared.
 * </p>
 *
 * @author Bobulous <http://www.bobulous.org.uk/>
 * @param <T>
 *            the basis type of this <code>GenericInterval</code>. The basis
 *            type must implement <code>Comparable&lt;T&gt;</code> so that each
 *            instance of the type can be compared with other instances of the
 *            same type, thus being a type which has a natural order.
 * @see Interval // * @see IntervalComparator
 */
public class GenericInterval<T extends Comparable<? super T>> implements Interval<T>, Comparable<Interval<T>> {

	protected static final long serialVersionUID = 1L;

	protected final @Nullable T lowerEndpoint;
	protected final @Nullable T upperEndpoint;
	private final @NonNull EndpointMode lowerMode, upperMode;

	public static @NonNull <T extends Comparable<? super T>> GenericInterval<T> getEmptyInterval() {
		return new GenericInterval<>(EndpointMode.OPEN, null, null, EndpointMode.OPEN);
	}

	public static @NonNull <T extends Comparable<? super T>> GenericInterval<T> getOpenUpperGenericInterval(
			final T _lowerEndpoint, final T _upperEndpoint) {
		return new GenericInterval<>(EndpointMode.CLOSED, _lowerEndpoint, _upperEndpoint, EndpointMode.OPEN);
	}

	/**
	 * Constructs a closed <code>GenericInterval</code> with the specified
	 * endpoint values.
	 * <p>
	 * Because this is a closed interval, both endpoint values will be included
	 * within the interval.
	 * </p>
	 *
	 * @param _lowerEndpoint
	 *            the value of the lower endpoint of this interval, or
	 *            <code>null</code> if the lower endpoint of this interval is
	 *            unbounded.
	 * @param _upperEndpoint
	 *            the value of the upper endpoint of this interval, or
	 *            <code>null</code> if the upper endpoint of this interval is
	 *            unbounded. The effective value of this upper endpoint must not
	 *            be lower than the effective value of the lower endpoint.
	 * @throws IllegalArgumentException
	 *             if the effective value of the upper endpoint is less than the
	 *             effective value of the lower endpoint.
	 */
	public static @NonNull <T extends Comparable<? super T>> GenericInterval<T> getClosedGenericInterval(
			final T _lowerEndpoint, final T _upperEndpoint) {
		return new GenericInterval<>(EndpointMode.CLOSED, _lowerEndpoint, _upperEndpoint, EndpointMode.CLOSED);
	}

	GenericInterval(final Interval<T> lowerInterval, final Interval<T> upperInterval) {
		this(lowerInterval.getLowerEndpointMode(), lowerInterval.getLowerEndpoint(), upperInterval.getUpperEndpoint(),
				upperInterval.getUpperEndpointMode());
	}

	/**
	 * Constructs a <code>GenericInterval</code> with the specified endpoint
	 * values and endpoint modes.
	 *
	 * @param _lowerMode
	 *            the endpoint mode of the lower endpoint.
	 * @param _lowerEndpoint
	 *            the value of the lower endpoint of this interval, or
	 *            <code>null</code> if the lower endpoint of this interval is
	 *            unbounded.
	 * @param _upperEndpoint
	 *            the value of the upper endpoint of this interval, or
	 *            <code>null</code> if the upper endpoint of this interval is
	 *            unbounded. The effective value of this upper endpoint must not
	 *            be lower than the effective value of the lower endpoint.
	 * @param _upperMode
	 *            the endpoint mode of the upper endpoint.
	 * @throws IllegalArgumentException
	 *             if the effective value of the upper endpoint is less than the
	 *             effective value of the lower endpoint.
	 */
	GenericInterval(final @NonNull EndpointMode _lowerMode, final @Nullable T _lowerEndpoint,
			final @Nullable T _upperEndpoint, final @NonNull EndpointMode _upperMode) {
		if (_lowerEndpoint != null) {
			// Bounded below
			if (_upperEndpoint != null) {
				// Bounded below and above
				final int compareTo = _upperEndpoint.compareTo(_lowerEndpoint);
				if (compareTo < 0) {
					throw new IllegalArgumentException(
							"Cannot create an " + "Interval whose upper endpoint has a value less "
									+ "than its lower endpoint value. Lower " + "endpoint is: " + _lowerEndpoint
									+ ", and upper " + "endpoint: " + _upperEndpoint);
				} else if (compareTo == 0 && _lowerMode != _upperMode) {
					throw new IllegalArgumentException(
							"An Interval whose upper endpoint equals its lower endpoint must have equal EndpointMode's."
									+ "lowerMode is: " + _lowerMode + ", and upperMode is: " + _upperMode);
				}
			}
		}
		lowerEndpoint = _lowerEndpoint;
		upperEndpoint = _upperEndpoint;
		lowerMode = _lowerMode;
		upperMode = _upperMode;
	}

	@Override
	public @NonNull Interval<T> intersection(final @NonNull Interval<T> otherInterval) {
		Interval<T> result;
		if (includes(otherInterval)) {
			result = otherInterval;
		} else if (otherInterval.includes(this)) {
			result = this;
		} else if (overlapsLowerEndpoint(otherInterval)) {
			result = new GenericInterval<>(otherInterval, this);
		} else if (otherInterval.overlapsLowerEndpoint(this)) {
			result = new GenericInterval<>(this, otherInterval);
		} else {
			result = new GenericInterval<>(EndpointMode.OPEN, lowerEndpoint, lowerEndpoint, EndpointMode.OPEN);
		}
		return result;
	}

	@Override
	public boolean isEmpty() {
		return Objects.deepEquals(getLowerEndpoint(), getUpperEndpoint())
				&& getLowerEndpointMode() == EndpointMode.OPEN;
	}

	@Override
	public boolean isOpenUpperClosedLower() {
		return getUpperEndpointMode() == EndpointMode.OPEN && getLowerEndpointMode() == EndpointMode.CLOSED;
	}

	@Override
	public @Nullable T getLowerEndpoint() {
		return lowerEndpoint;
	}

	@Override
	public @Nullable T getUpperEndpoint() {
		return upperEndpoint;
	}

	@Override
	public @NonNull EndpointMode getLowerEndpointMode() {
		return lowerMode;
	}

	@Override
	public @NonNull EndpointMode getUpperEndpointMode() {
		return upperMode;
	}

	private boolean lowerEndpointAdmits(final @Nullable T value) {
		return lowerEndpointAdmits(value, EndpointMode.CLOSED);
	}

	private boolean lowerEndpointAdmits(final @NonNull Interval<T> interval) {
		return lowerEndpointAdmits(interval.getLowerEndpoint(), interval.getLowerEndpointMode());
	}

	/**
	 * Reports on whether the specified value is permitted by the lower endpoint
	 * of this interval (without consideration for the upper endpoint).
	 *
	 * That is, whether the specified value is >= our lower endpoint.
	 *
	 * <p>
	 * <strong>Note:</strong> this method does not consider the upper endpoint
	 * of this interval, so a result of true from this method does not
	 * necessarily mean that this interval contains the specified value.
	 * </p>
	 *
	 * @param value
	 *            the value to test, which can be null if an endpoint value is
	 *            being tested.
	 * @return true if the lower endpoint of this interval does not preclude the
	 *         specified value from being a member of this interval; false
	 *         otherwise.
	 * @see upperEndpointAdmits
	 */
	private boolean lowerEndpointAdmits(final @Nullable T value, final @NonNull EndpointMode mode) {
		return compareLowerEndpoint(lowerEndpoint, lowerMode, value, mode) <= 0;
	}

	private boolean upperEndpointAdmits(final @NonNull T value) {
		return upperEndpointAdmits(value, EndpointMode.CLOSED);
	}

	private boolean upperEndpointAdmits(final @NonNull Interval<T> interval) {
		return upperEndpointAdmits(interval.getUpperEndpoint(), interval.getUpperEndpointMode());
	}

	/**
	 * Reports on whether the specified value is permitted by the upper endpoint
	 * of this interval (without consideration for the lower endpoint).
	 * <p>
	 * <strong>Note:</strong> this method does not consider the lower endpoint
	 * of this interval, so a result of true from this method does not
	 * necessarily mean that this interval contains the specified value.
	 * </p>
	 *
	 * @param value
	 *            the value to test, which can be null if an endpoint value is
	 *            being tested.
	 * @return true if the upper endpoint of this interval does not preclude the
	 *         specified value from being a member of this interval; false
	 *         otherwise.
	 * @see upperEndpointAdmits
	 */
	private boolean upperEndpointAdmits(final @Nullable T value, final @NonNull EndpointMode mode) {
		return compareUpperEndpoint(upperEndpoint, upperMode, value, mode) >= 0;
	}

	@Override
	public boolean includes(final T value) {
		return (lowerEndpointAdmits(value) && upperEndpointAdmits(value));
	}

	@Override
	public boolean includes(final @NonNull Interval<T> interval) {
		return (lowerEndpointAdmits(interval) && upperEndpointAdmits(interval));
	}

	@Override
	public boolean overlaps(final Interval<T> interval) {
		return !isBefore(interval) && !interval.isBefore(this);
	}

	@Override
	public boolean isBefore(final @NonNull Interval<T> interval) {
		final @Nullable T intervalLower = interval.getLowerEndpoint();
		final boolean result = upperEndpoint != null && intervalLower != null
				&& (upperEndpoint.compareTo(intervalLower) < 0
						|| (ObjectUtils.compare(upperEndpoint, intervalLower) == 0
								&& (upperMode.equals(EndpointMode.OPEN)
										|| interval.getLowerEndpointMode().equals(EndpointMode.OPEN))));
		return result;
	}

	@Override
	public boolean overlapsUpperEndpoint(final @NonNull Interval<T> interval) {
		final @Nullable T intervalUpper = interval.getUpperEndpoint();
		final boolean result = intervalUpper != null
				&& (upperEndpoint == null || ObjectUtils.compare(upperEndpoint, intervalUpper) > 0)
				&& (lowerEndpoint == null || ObjectUtils.compare(lowerEndpoint, intervalUpper) < 0
						|| (ObjectUtils.compare(lowerEndpoint, intervalUpper) == 0 && (lowerMode == EndpointMode.CLOSED
								|| interval.getUpperEndpointMode() == EndpointMode.OPEN)));
		return result;
	}

	@Override
	public boolean overlapsLowerEndpoint(final @NonNull Interval<T> interval) {
		final @Nullable T intervalLower = interval.getLowerEndpoint();
		final boolean result = intervalLower != null
				&& (lowerEndpoint == null || ObjectUtils.compare(lowerEndpoint, intervalLower) < 0)
				&& (upperEndpoint == null || ObjectUtils.compare(upperEndpoint, intervalLower) > 0
						|| (ObjectUtils.compare(upperEndpoint, intervalLower) == 0 && (upperMode == EndpointMode.CLOSED
								|| interval.getLowerEndpointMode() == EndpointMode.OPEN)));
		return result;
	}

	@Override
	public int compareTo(final Interval<T> o) {
		int result = compareLowerEndpoint(lowerEndpoint, lowerMode, o.getLowerEndpoint(), o.getLowerEndpointMode());
		if (result == 0) {
			result = compareUpperEndpoint(upperEndpoint, upperMode, o.getUpperEndpoint(), o.getUpperEndpointMode());
		}
		return result;
	}

	@Override
	public <S extends Comparable<? super S>> int compareLowerEndpoint(final Interval<S> interval) {
		return compareLowerEndpoint(interval.getLowerEndpoint(), interval.getLowerEndpointMode());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <S extends Comparable<? super S>> int compareLowerEndpoint(final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2) {
		return compareLowerEndpoint((S) lowerEndpoint, lowerMode, endpoint2, endpointMode2);
	}

	@Override
	public <S extends Comparable<? super S>> int compareLowerEndpoint(final @Nullable S endpoint1,
			final @NonNull EndpointMode endpointMode1, final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2) {
		return compareEndpoint(endpoint1, endpointMode1, endpoint2, endpointMode2, false);
	}

	@Override
	public <S extends Comparable<? super S>> int compareUpperEndpoint(final Interval<S> interval) {
		return compareUpperEndpoint(interval.getUpperEndpoint(), interval.getUpperEndpointMode());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <S extends Comparable<? super S>> int compareUpperEndpoint(final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2) {
		return compareUpperEndpoint((S) upperEndpoint, upperMode, endpoint2, endpointMode2);
	}

	@Override
	public <S extends Comparable<? super S>> int compareUpperEndpoint(final @Nullable S endpoint1,
			final @NonNull EndpointMode endpointMode1, final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2) {
		return compareEndpoint(endpoint1, endpointMode1, endpoint2, endpointMode2, true);
	}

	static <S extends Comparable<? super S>> int compareEndpoint(final @Nullable S endpoint1,
			final @NonNull EndpointMode endpointMode1, final @Nullable S endpoint2,
			final @NonNull EndpointMode endpointMode2, final boolean isUpperEndpoint) {
		int result = ObjectUtils.compare(endpoint1, endpoint2, isUpperEndpoint);
		if (result == 0) {
			if (endpoint1 != null) {
				result = (endpointMode2 == EndpointMode.CLOSED ? 1 : 0)
						- (endpointMode1 == EndpointMode.CLOSED ? 1 : 0);
				if (isUpperEndpoint) {
					result = -result;
				}
			}
		}
		return result;
	}

	/**
	 * Returns a <code>String</code> which represents the <code>Interval</code>
	 * using mathematical notation.
	 * <p>
	 * For example, a closed <code>Integer</code> interval might produce <samp>
	 * "[0, 1]"</samp> while an open <code>Double</code> interval might produce
	 * <samp>"(0.0, 1.0)"</samp>, and a left-closed, right-open
	 * <code>String</code> interval might produce <samp>"[a, b)"</samp>. A
	 * square bracket represents a closed endpoint; a parenthesis represents an
	 * open endpoint. A <code>null</code> lower endpoint value is replaced with
	 * negative infinity; a <code>null</code> upper endpoint value is replaced
	 * with positive infinity. This method calls the <code>toString</code>
	 * method on each endpoint of this <code>Interval</code>, so the output of
	 * this method may be meaningless if this <code>Interval</code> is based on
	 * a type whose <code>toString</code> method does not output the actual
	 * value of the endpoint objects.
	 * </p>
	 * <p>
	 * Be warned that if the endpoint values contain commas the output of this
	 * method may be confusing. For example, some numeric types might use the
	 * comma as a decimal point or a thousands-separator, and this could lead to
	 * confusing notation such as <samp>"[3,001, 3,002]"</samp>. And String
	 * objects which permit commas could lead to very confusing results such as
	 * <samp>"[There is, so it is said, a comma in here somewhere, Victor said
	 * so]"</samp>
	 * </p>
	 *
	 * @return a <code>String</code> which represents this
	 *         <code>GenericInterval</code> in mathematical notation.
	 */
	@NonNull
	String inMathematicalNotation() {
		final StringBuilder sb = new StringBuilder();
		if (lowerMode.equals(EndpointMode.CLOSED)) {
			sb.append("[");
		} else {
			sb.append("(");
		}
		if (lowerEndpoint == null) {
			sb.append("−∞");
		} else {
			sb.append(lowerEndpoint);
		}

		sb.append(", ");

		if (upperEndpoint == null) {
			sb.append("+∞");
		} else {
			sb.append(upperEndpoint);
		}
		if (upperMode.equals(EndpointMode.CLOSED)) {
			sb.append("]");
		} else {
			sb.append(")");
		}

		final String result = sb.toString();
		assert result != null;
		return result;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, inMathematicalNotation());
	}

	/**
	 * Returns true if both objects are <code>GenericInterval</code> instances
	 * with identical basis types, endpoint values and (for non-null values)
	 * endpoint modes.
	 * <p>
	 * Note that the mode of a null endpoint is irrelevant. Because
	 * <code>null</code> represents infinity in an endpoint value, the mode is
	 * meaningless. So two lower endpoints with a value of <code>null</code> are
	 * considered equal regardless of mode, and two upper endpoints with a value
	 * of <code>null</code> are considered equal regardless of mode.
	 * </p>
	 * <p>
	 * <strong>Warning:</strong> two <code>GenericInterval</code> objects with
	 * different basis types will be declared equal if all endpoints are
	 * <code>null</code>, because it is not possible to determine the basis type
	 * from a <code>null</code> object. Be aware of this if your code will
	 * compare <code>GenericInterval</code> objects of different basis types.
	 * </p>
	 *
	 * @param obj
	 *            the object to be compared for equality with this
	 *            <code>GenericInterval</code>.
	 * @return <code>true</code> if both objects are
	 *         <code>GenericInterval</code> objects with identical types, values
	 *         and endpoint modes; <code>false</code> otherwise.
	 */

	@Override
	public boolean equals(final Object obj) {
		if (obj == null || !(obj instanceof Interval)) {
			System.out.println("Wrong object type!");
			return false;
		}
		final Interval<?> that = (Interval<?>) obj;
		if (lowerEndpoint == null) {
			if (that.getLowerEndpoint() != null) {
				// This lower is null but other lower is not null
				return false;
			}
			// Both lower endpoints are null, so they are equal, regardless of
			// endpoint modes
		} else {
			if (that.getLowerEndpoint() == null) {
				// This lower is not null, but other lower is null
				return false;
			}
			// Neither lower endpoint is null, so compare values and modes
			if (!Objects.equals(lowerEndpoint, that.getLowerEndpoint())) {
				// Lower values are not equal
				return false;
			}
			if (!lowerMode.equals(that.getLowerEndpointMode())) {
				// Lower modes are not equal (and values are not null)
				return false;
			}
		}

		if (upperEndpoint == null) {
			if (that.getUpperEndpoint() != null) {
				// This upper is null but other upper is not null
				return false;
			}
			// Both upper endpoints are null, so they are equal, regardless of
			// endpoint modes
		} else {
			if (that.getUpperEndpoint() == null) {
				// This upper is not null, but other upper is null
				return false;
			}
			// Neither upper endpoint is null, so compare values and modes
			if (!Objects.equals(upperEndpoint, that.getUpperEndpoint())) {
				// Upper values are not equal
				return false;
			}
			if (!upperMode.equals(that.getUpperEndpointMode())) {
				// Upper modes are not equal (and values are not null)
				return false;
			}
		}

		return true;
	}

	/**
	 * Calculates a hash based on the values of this
	 * <code>GenericInterval</code>. Note that this method relies on there being
	 * a valid implementation of <code>hashCode</code> in the basis type (such
	 * as <code>Integer</code> or <code>String</code>). If the basis type does
	 * not correctly implement <code>hashCode</code> then the result returned by
	 * this method cannot be considered reliable.
	 *
	 * @return an <code>int</code> value based on the values of this
	 *         <code>GenericInterval</code>.
	 */
	@Override
	public int hashCode() {
		int hash = 7;
		hash = 79 * hash + (lowerEndpoint != null ? lowerEndpoint.hashCode() : 0);
		hash = 79 * hash + (upperEndpoint != null ? upperEndpoint.hashCode() : 0);
		hash = 79 * hash + lowerMode.hashCode();
		hash = 79 * hash + upperMode.hashCode();
		return hash;
	}
}
