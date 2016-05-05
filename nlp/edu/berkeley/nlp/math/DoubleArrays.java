package edu.berkeley.nlp.math;

import java.util.Arrays;

/**
 */
class DoubleArrays {
	static double[] clone(final double[] x) {
		final double[] y = new double[x.length];
		assign(y, x);
		return y;
	}

	static void assign(final double[] y, final double[] x) {
		if (x.length != y.length) {
			throw new RuntimeException("diff lengths: " + x.length + " "
					+ y.length);
		}
		System.arraycopy(x, 0, y, 0, x.length);
	}

	static double innerProduct(final double[] x, final double[] y) {
		if (x.length != y.length) {
			throw new RuntimeException("diff lengths: " + x.length + " "
					+ y.length);
		}
		double result = 0.0;
		for (int i = 0; i < x.length; i++) {
			result += x[i] * y[i];
		}
		return result;
	}

	static double[] addMultiples(final double[] x, final double xMultiplier,
			final double[] y, final double yMuliplier) {
		if (x.length != y.length) {
			throw new RuntimeException("diff lengths: " + x.length + " "
					+ y.length);
		}
		final double[] z = new double[x.length];
		for (int i = 0; i < z.length; i++) {
			z[i] = x[i] * xMultiplier + y[i] * yMuliplier;
		}
		return z;
	}

	static double[] constantArray(final double c, final int length) {
		final double[] x = new double[length];
		Arrays.fill(x, c);
		return x;
	}

	static double[] pointwiseMultiply(final double[] x, final double[] y) {
		if (x.length != y.length) {
			throw new RuntimeException("diff lengths: " + x.length + " "
					+ y.length);
		}
		final double[] z = new double[x.length];
		for (int i = 0; i < z.length; i++) {
			z[i] = x[i] * y[i];
		}
		return z;
	}

	// TODO Remove unused code found by UCDetector
	// public static String toString(final double[] x) {
	// return toString(x, x.length);
	// }

	static String toString(final double[] x, final int length) {
		final StringBuffer sb = new StringBuffer();
		sb.append("[");
		for (int i = 0; i < SloppyMath.min(x.length, length); i++) {
			sb.append(x[i]);
			if (i + 1 < SloppyMath.min(x.length, length)) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

	static void scale(final double[] x, final double s) {
		if (s == 1.0) {
			return;
		}
		for (int i = 0; i < x.length; i++) {
			x[i] *= s;
		}
	}

	// TODO Remove unused code found by UCDetector
	// public static double[] multiply(final double[] x, final double s) {
	// final double[] result = new double[x.length];
	// if (s == 1.0) {
	// System.arraycopy(x, 0, result, 0, x.length);
	// return result;
	// }
	// for (int i = 0; i < x.length; i++) {
	// result[i] = x[i] * s;
	// }
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// public static int argMax(final double[] v) {
	// int maxI = -1;
	// double maxV = Double.NEGATIVE_INFINITY;
	// for (int i = 0; i < v.length; i++) {
	// if (v[i] > maxV) {
	// maxV = v[i];
	// maxI = i;
	// }
	// }
	// return maxI;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double max(final double[] v) {
	// double maxV = Double.NEGATIVE_INFINITY;
	// for (final double element : v) {
	// if (element > maxV) {
	// maxV = element;
	// }
	// }
	// return maxV;
	// }

	// TODO Remove unused code found by UCDetector
	// public static int argMin(final double[] v) {
	// int minI = -1;
	// double minV = Double.POSITIVE_INFINITY;
	// for (int i = 0; i < v.length; i++) {
	// if (v[i] < minV) {
	// minV = v[i];
	// minI = i;
	// }
	// }
	// return minI;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double min(final double[] v) {
	// double minV = Double.POSITIVE_INFINITY;
	// for (final double element : v) {
	// if (element < minV) {
	// minV = element;
	// }
	// }
	// return minV;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double maxAbs(final double[] v) {
	// double maxV = 0;
	// for (final double element : v) {
	// final double abs = (element <= 0.0d) ? 0.0d - element : element;
	// if (abs > maxV) {
	// maxV = abs;
	// }
	// }
	// return maxV;
	// }

	static double magnitude(final double[] v) {
		double result = 0;
		for (final double element : v) {
			result += element * element;
		}
		return Math.sqrt(result);
	}

	// TODO Remove unused code found by UCDetector
	// public static double[] add(final double[] a, final double b) {
	// final double[] result = new double[a.length];
	// for (int i = 0; i < a.length; i++) {
	// final double v = a[i];
	// result[i] = v + b;
	// }
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double add(final double[] a) {
	// double sum = 0.0;
	// for (final double element : a) {
	// sum += element;
	// }
	// return sum;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double add(final double[] a, final int first, final int
	// last) {
	// if (last >= a.length) {
	// throw new RuntimeException("last beyond end of array");
	// }
	// if (first < 0) {
	// throw new RuntimeException("first must be at least 0");
	// }
	// double sum = 0.0;
	// for (int i = first; i <= last; i++) {
	// sum += a[i];
	// }
	// return sum;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double vectorLength(final double[] x) {
	// return Math.sqrt(innerProduct(x, x));
	// }

	// TODO Remove unused code found by UCDetector
	// public static double[] add(final double[] x, final double[] y) {
	// if (x.length != y.length) {
	// throw new RuntimeException("diff lengths: " + x.length + " "
	// + y.length);
	// }
	// final double[] result = new double[x.length];
	// for (int i = 0; i < x.length; i++) {
	// result[i] = x[i] + y[i];
	// }
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double[] subtract(final double[] x, final double[] y) {
	// if (x.length != y.length) {
	// throw new RuntimeException("diff lengths: " + x.length + " "
	// + y.length);
	// }
	// final double[] result = new double[x.length];
	// for (int i = 0; i < x.length; i++) {
	// result[i] = x[i] - y[i];
	// }
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double[] exponentiate(final double[] pUnexponentiated) {
	// final double[] exponentiated = new double[pUnexponentiated.length];
	// for (int index = 0; index < pUnexponentiated.length; index++) {
	// exponentiated[index] = SloppyMath.exp(pUnexponentiated[index]);
	// }
	// return exponentiated;
	// }

	// TODO Remove unused code found by UCDetector
	// public static void truncate(final double[] x, final double maxVal) {
	// for (int index = 0; index < x.length; index++) {
	// if (x[index] > maxVal) {
	// x[index] = maxVal;
	// } else if (x[index] < -maxVal) {
	// x[index] = -maxVal;
	// }
	// }
	// }

	static void initialize(final double[] x, final double d) {
		Arrays.fill(x, d);
	}

	// TODO Remove unused code found by UCDetector
	// public static void initialize(final Object[] x, final double d) {
	// for (final Object o : x) {
	// if (o instanceof double[]) {
	// initialize((double[]) o, d);
	// } else {
	// initialize((Object[]) o, d);
	// }
	// }
	// }

}
