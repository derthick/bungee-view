package edu.berkeley.nlp.math;

/**
 * Routines for some approximate math functions.
 *
 * @author Dan Klein
 * @author Teg Grenager
 */
class SloppyMath {

	static double min(final int x, final int y) {
		if (x > y) {
			return y;
		}
		return x;
	}

	// TODO Remove unused code found by UCDetector
	// public static double min(double x, double y) {
	// if (x > y)
	// return y;
	// return x;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double max(int x, int y) {
	// if (x > y)
	// return x;
	// return y;
	// }

	// TODO Remove unused code found by UCDetector
	// public static double max(double x, double y) {
	// if (x > y)
	// return x;
	// return y;
	// }

	static double abs(final double x) {
		if (x > 0) {
			return x;
		}
		return -1.0 * x;
	}

	// TODO Remove unused code found by UCDetector
	// public static double logAdd(double logX, double logY) {
	// // make a the max
	// if (logY > logX) {
	// double temp = logX;
	// logX = logY;
	// logY = temp;
	// }
	// // now a is bigger
	// if (logX == Double.NEGATIVE_INFINITY) {
	// return logX;
	// }
	// double negDiff = logY - logX;
	// if (negDiff < -20) {
	// return logX;
	// }
	// return logX + java.lang.Math.log(1.0 + java.lang.Math.exp(negDiff));
	// }

	// TODO Remove unused code found by UCDetector
	// public static double logAdd(double[] logV) {
	// double max = Double.NEGATIVE_INFINITY;
	// double maxIndex = 0;
	// for (int i = 0; i < logV.length; i++) {
	// if (logV[i] > max) {
	// max = logV[i];
	// maxIndex = i;
	// }
	// }
	// if (max == Double.NEGATIVE_INFINITY)
	// return Double.NEGATIVE_INFINITY;
	// // compute the negative difference
	// double threshold = max - 20;
	// double sumNegativeDifferences = 0.0;
	// for (int i = 0; i < logV.length; i++) {
	// if (i != maxIndex && logV[i] > threshold) {
	// sumNegativeDifferences += Math.exp(logV[i] - max);
	// }
	// }
	// if (sumNegativeDifferences > 0.0) {
	// return max + Math.log(1.0 + sumNegativeDifferences);
	// } else {
	// return max;
	// }
	// }

	static double exp(final double logX) {
		// if x is very near one, use the linear approximation
		if (abs(logX) < 0.001) {
			return 1 + logX;
		}
		return Math.exp(logX);
	}

}
