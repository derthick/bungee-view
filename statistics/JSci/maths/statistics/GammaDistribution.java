package JSci.maths.statistics;

import JSci.maths.SpecialMath;

/**
 * The GammaDistribution class provides an object for encapsulating gamma
 * distributions.
 *
 * @version 1.0
 * @author Jaco van Kooten
 */
final class GammaDistribution extends ProbabilityDistribution {
	private double shape;

	/**
	 * Constructs a gamma distribution.
	 *
	 * @param s
	 *            the shape parameter.
	 */
	GammaDistribution(final double s) {
		if (s <= 0.0) {
			throw new OutOfRangeException(
					"The shape parameter should be (strictly) positive.");
		}
		shape = s;
	}

	/**
	 * Returns the shape parameter.
	 */
	public double getShapeParameter() {
		return shape;
	}

	/**
	 * Returns the shape parameter.
	 */
	public void setShapeParameter(final double s) {
		shape = s;
	}

	/**
	 * Returns the mean.
	 */
	public double getMean() {
		return shape;
	}

	/**
	 * Returns the variance.
	 */
	public double getVariance() {
		return shape;
	}

	/**
	 * Probability density function of a gamma distribution.
	 *
	 * @return the probability that a stochastic variable x has the value X,
	 *         i.e. P(x=X).
	 */
	@Override
	public double probability(final double X) {
		checkRange(X, 0.0, Double.MAX_VALUE);
		if (X == 0.0) {
			return 0.0;
		} else {
			return Math.exp(-SpecialMath.logGamma(shape) - X + (shape - 1)
					* Math.log(X));
		}
	}

	/**
	 * Cumulative gamma distribution function.
	 *
	 * @return the probability that a stochastic variable x is less then X, i.e.
	 *         P(x&lt;X).
	 */
	@Override
	public double cumulative(final double X) {
		checkRange(X, 0.0, Double.MAX_VALUE);
		return SpecialMath.incompleteGamma(shape, X);
	}

	/**
	 * Inverse of the cumulative gamma distribution function.
	 *
	 * @return the value X for which P(x&lt;X).
	 */
	@Override
	public double inverse(final double probability) {
		checkRange(probability);
		if (probability == 0.0) {
			return 0.0;
		}
		if (probability == 1.0) {
			return Double.MAX_VALUE;
		}
		return findRoot(probability, shape, 0.0, Double.MAX_VALUE);
	}
}