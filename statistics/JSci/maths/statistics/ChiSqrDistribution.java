package JSci.maths.statistics;

import JSci.maths.SpecialMath;

/**
 * The ChiSqrDistribution class provides an object for encapsulating chi-squared
 * distributions.
 *
 * @version 1.0
 * @author Jaco van Kooten
 */
final class ChiSqrDistribution extends ProbabilityDistribution {
	private final double r;
	// The ChiSqr and Gamma distributions are closely related.
	private final GammaDistribution gamma;

	/**
	 * Constructs a chi-squared distribution.
	 *
	 * @param dgr
	 *            degrees of freedom.
	 */
	ChiSqrDistribution(final double dgr) {
		if (dgr <= 0.0) {
			throw new OutOfRangeException(
					"The degrees of freedom must be greater than zero.");
		}
		r = dgr;
		gamma = new GammaDistribution(0.5 * r);
	}

	/**
	 * Returns the degrees of freedom.
	 */
	public double getDegreesOfFreedom() {
		return r;
	}

	/**
	 * Probability density function of a chi-squared distribution.
	 *
	 * @return the probability that a stochastic variable x has the value X,
	 *         i.e. P(x=X).
	 */
	@Override
	public double probability(final double X) {
		return 0.5 * gamma.probability(0.5 * X);
	}

	/**
	 * Cumulative chi-squared distribution function.
	 *
	 * @return the probability that a stochastic variable x is less then X, i.e.
	 *         P(x&lt;X).
	 */
	@Override
	public double cumulative(final double X) {
		checkRange(X, 0.0, Double.MAX_VALUE);
		return SpecialMath.incompleteGamma(0.5 * r, 0.5 * X);
	}

	/**
	 * Inverse of the cumulative chi-squared distribution function.
	 *
	 * @return the value X for which P(x&lt;X).
	 */
	@Override
	public double inverse(final double probability) {
		if (probability == 1.0) {
			return Double.MAX_VALUE;
		} else {
			return 2.0 * gamma.inverse(probability);
		}
	}
}