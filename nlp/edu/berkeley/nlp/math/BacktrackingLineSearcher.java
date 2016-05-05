package edu.berkeley.nlp.math;

/**
 */
class BacktrackingLineSearcher implements GradientLineSearcher {
	private final double EPS = 1e-15;
	double stepSizeMultiplier = 0.9;
	private final double sufficientDecreaseConstant = 1e-4;// 0.9;

	@Override
	public double[] minimize(final DifferentiableFunction function, final double[] initial, final double[] direction) {

		// double stepSize = 1.0;
		double stepSize = Math.min(1.0, 10.0 / DoubleArrays.magnitude(direction));

		final double initialFunctionValue = function.valueAt(initial);
		final double initialDirectionalDerivative = DoubleArrays.innerProduct(function.derivativeAt(initial),
				direction);
		double[] guess = null;
		double guessValue = 0.0;
		boolean sufficientDecreaseObtained = false;
		/*
		 * if (true) { EPS = 2e-8; guess = DoubleArrays.addMultiples(initial,
		 * 1.0, direction, EPS); guessValue = function.valueAt(guess); double
		 * sufficientDecreaseValue = initialFunctionValue +
		 * sufficientDecreaseConstant * initialDirectionalDerivative * EPS;
		 * System.out.println("NUDGE TEST:"); System.out.println(
		 * "  Trying step size:  "+EPS); System.out.println(
		 * "  Required value is: "+sufficientDecreaseValue); System.out.println(
		 * "  Value is:          "+guessValue); System.out.println(
		 * "  Initial was:       "+initialFunctionValue); if (guessValue >
		 * initialFunctionValue) { System.err.println("NUDGE TEST FAILED");
		 * return initial; } EPS = 1e-10; }
		 */
		// int step = 0;
		while (!sufficientDecreaseObtained) {
			// System.out.println(step++);
			guess = DoubleArrays.addMultiples(initial, 1.0, direction, stepSize);
			final double sufficientDecreaseValue = initialFunctionValue
					+ sufficientDecreaseConstant * initialDirectionalDerivative * stepSize;
			// System.out.println("Trying step size: "+stepSize);
			// System.out.println("Required value is:
			// "+sufficientDecreaseValue);
			// System.out.println("Value is: "+guessValue);
			// System.out.println("Initial was: "+initialFunctionValue);
			try {
				guessValue = function.valueAt(guess);
			} catch (final Error e) {
				System.err.println("While minimizing line search: stepSize=" + stepSize + "; sufficientDecreaseValue="
						+ sufficientDecreaseValue + "; previous guessValue=" + guessValue + "; initialFunctionValue="
						+ initialFunctionValue + ";\ninitial=" + DoubleArrays.toString(initial, 100) + ";\ncurrent="
						+ DoubleArrays.toString(guess, 100) + ";\ndirection=" + DoubleArrays.toString(direction, 100)
						+ ":\n");
				throw (e);
			}
			sufficientDecreaseObtained = (guessValue <= sufficientDecreaseValue);
			if (!sufficientDecreaseObtained) {
				stepSize *= stepSizeMultiplier;
				if (stepSize < EPS) {
					// throw new
					// RuntimeException("BacktrackingSearcher.minimize: stepSize
					// underflow.");
					// System.err
					// .println("BacktrackingSearcher.minimize: stepSize
					// underflow.");
					return initial;
				}
			}
		}
		// double lastGuessValue = guessValue;
		// double[] lastGuess = guess;
		// while (lastGuessValue >= guessValue) {
		// lastGuessValue = guessValue;
		// lastGuess = guess;
		// stepSize *= stepSizeMultiplier;
		// guess = DoubleArrays.addMultiples(initial, 1.0, direction, stepSize);
		// guessValue = function.valueAt(guess);
		// }
		// return lastGuess;
		return guess;
	}

	private static final double[] ZERO = { 0.0 };

	private static double[] zero() {
		assert ZERO[0] == 0.0;
		return ZERO;
	}

	private static final double[] ONE = { 1.0 };

	private static double[] one() {
		assert ONE[0] == 1.0;
		return ONE;
	}

	public static void main(final String[] args) {
		final DifferentiableFunction function = new DifferentiableFunction() {
			@Override
			public int dimension() {
				return 1;
			}

			@Override
			public double valueAt(final double[] x) {
				return x[0] * (x[0] - 0.01);
			}

			@Override
			public double[] derivativeAt(final double[] x) {
				return new double[] { 2 * x[0] - 0.01 };
			}
		};
		final BacktrackingLineSearcher lineSearcher = new BacktrackingLineSearcher();
		lineSearcher.minimize(function, zero(), one());
	}
}
