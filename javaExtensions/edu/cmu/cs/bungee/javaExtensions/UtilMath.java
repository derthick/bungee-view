package edu.cmu.cs.bungee.javaExtensions;

import static edu.cmu.cs.bungee.javaExtensions.UtilString.addCommas;

import java.awt.geom.AffineTransform;

import org.eclipse.jdt.annotation.NonNull;

public class UtilMath {
	/**
	 * some assert statements allow this much error when comparing doubles.
	 */
	public static final double ABSOLUTE_SLOP = 5.0e-10;
	protected static final double RELATIVE_SLOP = 1.0e-3;

	/**
	 * version of Math.signum that returns an int
	 *
	 * @param n
	 * @return -1, 0, or 1 depending on signum of n
	 */
	public static int sgn(final int n) {
		if (n < 0) {
			return -1;
		} else if (n > 0) {
			return 1;
		} else {
			return 0;
		}
	}

	/**
	 * version of Math.signum that returns an int
	 *
	 * @param n
	 * @return -1, 0, or 1 depending on signum of n
	 */
	public static int sgn(final double n) {
		if (n < 0.0) {
			return -1;
		} else if (n > 0.0) {
			return 1;
		} else {
			return 0;
		}
	}

	/**
	 * @return x + zeroToOne * (x2 - x)
	 */
	public static double interpolate(final double x1, final double x2, final double zeroToOne) {
		if (zeroToOne == 1.0) {
			// avoid roundoff errors
			return x2;
		}
		// print(" "+x1+" "+x2+" "+zeroToOne+" "+x1 + zeroToOne * (x2 - x1));
		return x1 + zeroToOne * (x2 - x1);
	}

	/**
	 * @return x + zeroToOne * (x2 - x)
	 */
	public static double interpolate(final double x1, final double x2, final float zeroToOne) {
		if (zeroToOne == 1.0f) {
			// avoid roundoff errors
			return x2;
		}
		// print(" "+x1+" "+x2+" "+zeroToOne+" "+x1 + zeroToOne * (x2 - x1));
		return x1 + zeroToOne * (x2 - x1);
	}

	/**
	 * @return x + zeroToOne * (x2 - x)
	 */
	public static float interpolate(final float x1, final float x2, final float zeroToOne) {
		if (zeroToOne == 1.0f) {
			// avoid roundoff errors
			return x2;
		}
		return x1 + zeroToOne * (x2 - x1);
	}

	public static long max(long long1, final long... longs) {
		for (final long aLong : longs) {
			if (aLong > long1) {
				long1 = aLong;
			}
		}
		return long1;
	}

	public static double max(double double1, final double... doubles) {
		for (final double adouble : doubles) {
			if (adouble > double1) {
				double1 = adouble;
			}
		}
		return double1;
	}

	public static @NonNull <T extends Comparable<? super T>> T max(final @NonNull T t1, final @NonNull T t2) {
		final int comparison = t1.compareTo(t2);
		return comparison > 0 ? t1 : t2;
	}

	public static boolean assertInteger(final double i) {
		assert i == (int) i : addCommas(i);
		return true;
	}

	public static boolean assertNearInt(final double d) {
		assert isNearInt(d) : d;
		return true;
	}

	public static boolean isNearInt(final double d) {
		return Math.abs(d - Math.rint(d)) < ABSOLUTE_SLOP;
	}

	/**
	 * @return whether val is in the range [minv, maxv]
	 */
	public static boolean assertInRange(final double val, final double minv, final double maxv) {
		assert isInRange(val, minv, maxv) : addCommas(val) + " is not in [" + addCommas(minv) + " - " + addCommas(maxv)
				+ "]";
		return true;
	}

	/**
	 * @return whether val is in the range [minv, maxv]
	 */
	public static boolean isInRange(final double val, final double minv, final double maxv) {
		assert minv <= maxv : addCommas(minv) + " > " + addCommas(maxv);
		return val >= minv && val <= maxv;
	}

	/**
	 * @return whether val is in the range [minv, maxv]
	 */
	public static boolean assertInRange(final int val, final int minv, final int maxv) {
		assert isInRange(val, minv, maxv) : addCommas(val) + " is not in [" + addCommas(minv) + " - " + addCommas(maxv)
				+ "]";
		return true;
	}

	/**
	 * @return whether val is in the range [minv, maxv]
	 */
	public static boolean isInRange(final int val, final int minv, final int maxv) {
		assert minv <= maxv : addCommas(minv) + " > " + addCommas(maxv);
		return val >= minv && val <= maxv;
	}

	/**
	 * @return the int in the range [minv, maxv] closest to val
	 */
	public static int constrain(final int val, final int minv, final int maxv) {
		assert minv <= maxv : addCommas(minv) + " > " + addCommas(maxv);
		return Math.min(Math.max(val, minv), maxv);
	}

	/**
	 * @return the float in the range [minv, maxv] closest to val
	 */
	public static float constrain(final float val, final float minv, final float maxv) {
		assert minv <= maxv : addCommas(minv) + " > " + addCommas(maxv);
		return Math.min(Math.max(val, minv), maxv);
	}

	/**
	 * @return the double in the range [minv, maxv] closest to val
	 */
	public static double constrain(final double val, final double minv, final double maxv) {
		assert minv <= maxv : addCommas(minv) + " > " + addCommas(maxv);
		return Math.min(Math.max(val, minv), maxv);
	}

	// TODO Remove unused code found by UCDetector
	// public static int min(final int n1, final int... ns) {
	// int result = n1;
	// for (final int n : ns) {
	// if (n < result) {
	// result = n;
	// }
	// }
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double min(final double n1, final double... ns) {
	// double result = n1;
	// for (final double n : ns) {
	// if (n < result) {
	// result = n;
	// }
	// }
	// return result;
	// }

	// public static boolean isClose(final double[] a, final double[] b) {
	// for (int i = 0; i < b.length; i++) {
	// if (!isClose(a[i], b[i])) {
	// return false;
	// }
	// }
	// return true;
	// }
	//
	// public static boolean isClose(final double a, final double b) {
	// return approxEquals(a, b, 1e-7, 2e-1);
	// }

	public static boolean assertApproxEqualsAbsolute(final double a, final double b) {
		assert approxEquals(a, b, ABSOLUTE_SLOP, 0.0) : a + " " + b;
		return true;
	}

	public static boolean approxEqualsAbsolute(final double a, final double b) {
		return approxEquals(a, b, ABSOLUTE_SLOP, 0.0);
	}

	public static boolean assertApproxEquals(final double a, final double b) {
		assert approxEquals(a, b) : a + " " + b;
		return true;
	}

	public static boolean approxEquals(final double a, final double b) {
		return approxEquals(a, b, ABSOLUTE_SLOP, RELATIVE_SLOP);
	}

	private static boolean approxEquals(final double a, final double b, final double absoluteThreshold,
			final double relativeThreshold) {
		if (a == b) {
			return true;// for INFINITY
		}
		final double diff = Math.abs(a - b);
		// never division by zero if absoluteThreshold > 0
		return diff < absoluteThreshold || diff / Math.max(Math.abs(a), Math.abs(b)) < relativeThreshold;
	}

	public static boolean assertIsFinite(final double x) {
		assert !Double.isNaN(x) && !Double.isInfinite(x) : x;
		return true;
	}

	/**
	 * @param i
	 * @param bit
	 *            in [0, 31]
	 * @return 1 if bit is set in i; else 0. E.g. getBit(4, 2) = 1
	 */
	public static int getBit(final int i, final int bit) {
		assert bit < Integer.BYTES * 8 : bit;
		return (i >> bit) & 1;
	}

	public static boolean isBit(final int i, final int bit) {
		return getBit(i, bit) == 1;
	}

	/**
	 * @param i
	 * @param bit
	 * @param state
	 * @return e.g. setBit(8, 0, true) == 9
	 */
	public static int setBit(int i, final int bit, final boolean state) {
		final int mask = 1 << bit;
		if (state) {
			i |= mask;
		} else {
			i &= ~mask;
		}
		return i;
	}

	public static boolean isPowerOfTwo(final int i) {
		return weight(i) == 1;
	}

	/**
	 * @return number of "1" bits in substate
	 */
	public static int weight(int i) {
		int result = 0;
		while (i > 0) {
			result += UtilMath.getBit(i, 0);
			i = i >> 1;
		}
		return result;
	}

	public static int roundToInt(final double _double) {
		return (int) Math.round(_double);
	}

	public static int intCeil(final double _double) {
		return (int) Math.ceil(_double);
	}

	/**
	 * The code computes the average of two integers using either division or
	 * signed right shift, and then uses the result as the index of an array. If
	 * the values being averaged are very large, this can overflow (resulting in
	 * the computation of a negative average). Assuming that the result is
	 * intended to be nonnegative, you can use an unsigned right shift instead.
	 * In other words, rather that using (low+high)/2, use (low+high) >>> 1
	 */
	public static int safeIntAverage(final int i, final int j) {
		return (i + j) >>> 1;
	}

	public static @NonNull AffineTransform scaleNtranslate(final double scaleX, final double scaleY, final double x,
			final double y) {
		final AffineTransform result = AffineTransform.getTranslateInstance(x, y);
		result.concatenate(AffineTransform.getScaleInstance(scaleX, scaleY));
		return result;
	}

	public static double square(final double x) {
		return x * x;
	}

	public static double[] solveQuadratic(final double a, final double b, final double c) {
		final double root = Math.sqrt(b * b - 4.0 * a * c);
		final double denom = 2.0 * a;
		final double leftTerm = -b;
		return new double[] { (leftTerm + root) / denom, (leftTerm - root) / denom };
	}

}
