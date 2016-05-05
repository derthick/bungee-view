package edu.berkeley.nlp.math;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Iterator;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * @author Dan Klein
 */
public class LBFGSMinimizer implements GradientMinimizer, Serializable {
	protected static final long serialVersionUID = 36473897808840226L;

	public static interface IterationCallbackFunction {
		public void iterationDone(double[] curGuess, int iter);
	}

	private static final double LBFGS_ACCURACY = 1e-2; // 1e-9;
	private static final double EPS = 1e-10;
	private static final int maxIterations = Integer.MAX_VALUE;
	private static final int maxHistorySize = 15;
	private static final int minIterations = -1;
	private static final double initialStepSizeMultiplier = 0.01;
	private static final double stepSizeMultiplier = 0.5;

	private final ArrayDeque<double[]> inputDifferenceVectorList = new ArrayDeque<>(maxHistorySize);
	private final ArrayDeque<double[]> derivativeDifferenceVectorList = new ArrayDeque<>(maxHistorySize);

	private transient IterationCallbackFunction iterCallbackFunction = null;

	// public void setMinIteratons(final int _minIterations) {
	// this.minIterations = _minIterations;
	// }

	// public void setMaxIterations(final int _maxIterations) {
	// this.maxIterations = _maxIterations;
	// }

	// public void setInitialStepSizeMultiplier(final double
	// _initialStepSizeMultiplier) {
	// this.initialStepSizeMultiplier = _initialStepSizeMultiplier;
	// }

	// public void setStepSizeMultiplier(final double _stepSizeMultiplier) {
	// this.stepSizeMultiplier = _stepSizeMultiplier;
	// }

	public double[] minimize(final DifferentiableFunction function, final double[] initial,
			final boolean printProgress) {
		final BacktrackingLineSearcher lineSearcher = new BacktrackingLineSearcher();
		double[] guess = DoubleArrays.clone(initial);
		double[] previousGuess = guess;
		for (int iteration = 0; iteration < maxIterations; iteration++) {
			double value = function.valueAt(guess);
			double[] derivative = function.derivativeAt(guess);
			final double[] initialInverseHessianDiagonal = getInitialInverseHessianDiagonal(function);
			double[] direction;
			try {
				direction = implicitMultiply(initialInverseHessianDiagonal, derivative);
			} catch (final RuntimeException e) {
				// This seems to be the normal termination process. mad 4/2015
				// System.err.println("LBFGSMinimizer.minimize on iteration "
				// + iteration + ", ignoring " + e);
				// e.printStackTrace();
				System.err.println(
						"LBFGSMinimizer.minimize erred previousGuess=" + UtilString.valueOfDeep(previousGuess));
				return previousGuess;
			}
			// System.out.println(" Derivative is:
			// "+DoubleArrays.toString(derivative,
			// 100));
			// DoubleArrays.assign(direction, derivative);

			DoubleArrays.scale(direction, -1.0);
			try {
				// System.out.println(" Looking in direction:
				// "+DoubleArrays.toString(direction,
				// 100));
				lineSearcher.stepSizeMultiplier = iteration == 0 ? initialStepSizeMultiplier : stepSizeMultiplier;
				final double[] nextGuess = lineSearcher.minimize(function, guess, direction);
				final double nextValue = function.valueAt(nextGuess);
				final double[] nextDerivative = function.derivativeAt(nextGuess);
				if (printProgress) {
					System.out.printf("[LBFGSMinimizer.minimize] Iteration %d ended with value %.6f\n", iteration,
							nextValue);
					System.out.println("guess=" + UtilString.valueOfDeep(nextGuess));
				}

				if (iteration >= minIterations && converged(value, nextValue)) {
					// System.out.println("LBFGSMinimizer.minimize converged");
					return nextValue < value ? nextGuess : guess;
				}
				updateHistories(guess, nextGuess, derivative, nextDerivative);
				previousGuess = guess;
				guess = nextGuess;
				value = nextValue;
				derivative = nextDerivative;

			} catch (final Error e) {
				System.err.println("While minimizing: iter=" + iteration + "; mutiplier="
						+ lineSearcher.stepSizeMultiplier + "; derivative=" + DoubleArrays.toString(derivative, 100)
						+ "; initialInverseHessianDiagonal=" + DoubleArrays.toString(initialInverseHessianDiagonal, 100)
						+ ":\n");
				throw (e);
			}
			if (iterCallbackFunction != null) {
				iterCallbackFunction.iterationDone(guess, iteration);
			}
		}
		System.err.println("LBFGSMinimizer.minimize: Exceeded maxIterations without converging.");
		return guess;
	}

	private static boolean converged(final double value, final double nextValue) {
		if (value == nextValue) {
			return true;
		}
		final double valueChange = SloppyMath.abs(nextValue - value);
		final double valueAverage = SloppyMath.abs(nextValue + value + EPS) / 2.0;
		if (valueChange / valueAverage < LBFGS_ACCURACY) {
			return true;
		}
		return false;
	}

	private void updateHistories(final double[] guess, final double[] nextGuess, final double[] derivative,
			final double[] nextDerivative) {
		final double[] guessChange = DoubleArrays.addMultiples(nextGuess, 1.0, guess, -1.0);
		final double[] derivativeChange = DoubleArrays.addMultiples(nextDerivative, 1.0, derivative, -1.0);
		pushOntoList(guessChange, inputDifferenceVectorList);
		pushOntoList(derivativeChange, derivativeDifferenceVectorList);
	}

	private static void pushOntoList(final double[] vector, final ArrayDeque<double[]> queue) {
		if (queue.size() >= maxHistorySize) {
			queue.remove();
		}
		queue.add(vector);
	}

	private int historySize() {
		return inputDifferenceVectorList.size();
	}

	// public void setMaxHistorySize(final int _maxHistorySize) {
	// this.maxHistorySize = _maxHistorySize;
	// inputDifferenceVectorList = resizeQueue(inputDifferenceVectorList);
	// derivativeDifferenceVectorList =
	// resizeQueue(derivativeDifferenceVectorList);
	// }

	// private ArrayDeque<double[]> resizeQueue(final ArrayDeque<double[]>
	// queue) {
	// final int excess = queue.size() - maxHistorySize;
	// for (int i = 0; i < excess; i++) {
	// queue.remove();
	// }
	// return queue;
	// }

	// private double[] getInputDifference(final int num) {
	// // 0 is previous, 1 is the one before that
	// return inputDifferenceVectorList.get(num);
	// }
	//
	// private double[] getDerivativeDifference(final int num) {
	// return derivativeDifferenceVectorList.get(num);
	// }

	private double[] getLastDerivativeDifference() {
		return derivativeDifferenceVectorList.getLast();
	}

	private double[] getLastInputDifference() {
		return inputDifferenceVectorList.getLast();
	}

	private double[] implicitMultiply(final double[] initialInverseHessianDiagonal, final double[] derivative) {
		final int historySize = historySize();
		final double[] rho = new double[historySize];
		final double[] alpha = new double[historySize];
		double[] right = DoubleArrays.clone(derivative);
		// loop last backward
		final Iterator<double[]> derivativeDiffItAsc = derivativeDifferenceVectorList.iterator();
		final Iterator<double[]> inputDiffItAsc = inputDifferenceVectorList.iterator();
		for (int i = historySize - 1; i >= 0; i--) {
			final double[] inputDifference = inputDiffItAsc.next();
			final double[] derivativeDifference = derivativeDiffItAsc.next();
			rho[i] = DoubleArrays.innerProduct(inputDifference, derivativeDifference);
			if (rho[i] == 0.0) {
				// This seems to be the normal termination process. mad 4/2015
				throw new RuntimeException("LBFGSMinimizer.implicitMultiply: Curvature problem. i="
				// + i + " historySize=" + historySize + "\n"
				// + UtilString.valueOfDeep(inputDifference)
				// + "\n"
				// + UtilString.valueOfDeep(derivativeDifference)
				);
			}
			alpha[i] = DoubleArrays.innerProduct(inputDifference, right) / rho[i];
			right = DoubleArrays.addMultiples(right, 1.0, derivativeDifference, -1.0 * alpha[i]);
		}
		double[] left = DoubleArrays.pointwiseMultiply(initialInverseHessianDiagonal, right);
		final Iterator<double[]> derivativeDiffItDesc = derivativeDifferenceVectorList.descendingIterator();
		final Iterator<double[]> inputDiffItDesc = inputDifferenceVectorList.descendingIterator();
		for (int i = 0; i < historySize; i++) {
			assert historySize == historySize() : "bad assumption mad 4/15";
			final double[] inputDifference = inputDiffItDesc.next();
			final double[] derivativeDifference = derivativeDiffItDesc.next();
			final double beta = DoubleArrays.innerProduct(derivativeDifference, left) / rho[i];
			left = DoubleArrays.addMultiples(left, 1.0, inputDifference, alpha[i] - beta);
		}
		return left;
	}

	private double[] getInitialInverseHessianDiagonal(final DifferentiableFunction function) {
		double scale = 1.0;
		if (derivativeDifferenceVectorList.size() >= 1) {
			final double[] lastDerivativeDifference = getLastDerivativeDifference();
			final double[] lastInputDifference = getLastInputDifference();
			final double num = DoubleArrays.innerProduct(lastDerivativeDifference, lastInputDifference);
			final double den = DoubleArrays.innerProduct(lastDerivativeDifference, lastDerivativeDifference);
			scale = num / den;
			// assert scale < 10000 : num + " " + den + " "
			// + DoubleArrays.toString(lastDerivativeDifference) + " "
			// + DoubleArrays.toString(lastInputDifference);
		}
		return DoubleArrays.constantArray(scale, function.dimension());
	}

	public void setIterationCallbackFunction(final IterationCallbackFunction callbackFunction) {
		this.iterCallbackFunction = callbackFunction;
	}

	public LBFGSMinimizer() {
		//
	}

	// TODO Remove unused code found by UCDetector
	// public LBFGSMinimizer(final int _maxIterations) {
	// this.maxIterations = _maxIterations;
	// }

}
