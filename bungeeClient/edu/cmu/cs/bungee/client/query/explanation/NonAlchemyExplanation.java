package edu.cmu.cs.bungee.client.query.explanation;

import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.math.LBFGSMinimizer;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.explanation.GraphicalModel.SimpleEdge;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * NonAlchemyExplanation is the only concrete extension of the abstract class
 * Explanation
 */
public class NonAlchemyExplanation extends Explanation implements DifferentiableFunction {

	private static final int BURN_IN = 0;
	private static transient SoftReference<Map<NAMparams, NonAlchemyExplanation>> nonAlchemyExplanations;

	private final @NonNull double[] cachedGradient;
	private double cachedEval = Double.NaN;

	/**
	 * A NonAlchemyExplanation with the specified facets and edges, and learned
	 * weights.
	 */
	private @Immutable NonAlchemyExplanation(final @NonNull SortedSet<Perspective> facets,
			final @NonNull Set<SimpleEdge> edges, final @Nullable Explanation _nullModel,
			final @NonNull ExplanationTask _explanationTask) {
		super(facets, edges, _nullModel, _explanationTask, true);
		cachedGradient = getCachedGradient();
		learnWeights();
		cache();
	}

	/**
	 * Called only by EdgeSelection.selectEdges to create a fake nullModel with
	 * weights copied from intermediateExplanation, so don't cache result. No
	 * point in learning weights.
	 */
	NonAlchemyExplanation(final @NonNull Explanation _nullModel, final @NonNull Explanation intermediateExplanation) {
		super(_nullModel.facets(), _nullModel.predicted.getEdges(false), _nullModel, _nullModel.explanationTask, false);
		predicted.setWeights(intermediateExplanation.predicted, nullModel.predicted.getEdges(true));
		cachedGradient = getCachedGradient();
	}

	/**
	 * This is the only outside call into the explanation package (by
	 * InfluenceDiagramCreator.process()).
	 *
	 * @return Explanation or null if no topCandidates.
	 */
	public static @Nullable Explanation getExplanation(final @NonNull ExplanationTask explanationTask) {
		if (Explanation.isPrintLevelAtLeast(PrintLevel.STATISTICS)) {
			System.out.println("enter NonAlchemyExplanation.getExplanation");
		}
		Explanation result = null;
		final SortedSet<Perspective> primaryFacets = explanationTask.primaryFacets();
		if (primaryFacets.size() < FacetSelection.MAX_FACET_SELECTION_FACETS) {
			final long start = UtilString.now();
			final Collection<Perspective> candidates = topCandidates(explanationTask);
			if (candidates.size() > 0) {
				final @Immutable @NonNull Explanation nullModel = nullModel(null, explanationTask);
				assert nullModel.isImmutable;
				result = nullModel.getExplanation(candidates);
				assert primaryFacets.equals(nullModel.facets());
				result.setEval(result.improvement(nullModel));
				result.printInfo(start);
			}
		}
		return result;
	}

	@Override
	@Immutable
	@NonNull
	Explanation getAlternateExplanation(final @Immutable @NonNull Explanation _nullModel,
			final @NonNull SortedSet<Perspective> nullModelFacetsPlusAddedFacets) {
		return nullModel(_nullModel, explanationTask.getModifiedInstance(nullModelFacetsPlusAddedFacets));
	}

	/**
	 * @return getInstance with explanationTask.primaryFacets() and allEdges
	 *         possible.
	 */
	private static @Immutable @NonNull Explanation nullModel(final @Immutable @Nullable Explanation _nullModel,
			final @NonNull ExplanationTask explanationTask) {
		assert _nullModel == null || _nullModel.isImmutable;
		final SortedSet<Perspective> primaryFacets = explanationTask.primaryFacets();
		return getInstance(primaryFacets, GraphicalModel.allEdges(primaryFacets), _nullModel, explanationTask);
	}

	@Override
	@Immutable
	@NonNull
	Explanation getAlternateExplanation(final @NonNull Explanation _nullModel, final @NonNull Set<SimpleEdge> edges) {
		return getInstance(facets(), edges, _nullModel, explanationTask);
	}

	/**
	 * If _nullModel!=null, look up a previous learned Explanation with the
	 * specified facets and edges. If not found (or _nullModel==null), learn a
	 * new one.
	 */
	private static @Immutable @NonNull Explanation getInstance(final @NonNull SortedSet<Perspective> facets,
			final @NonNull Set<SimpleEdge> edges, final @Nullable Explanation _nullModel,
			final @NonNull ExplanationTask explanationTask) {
		Explanation prev = _nullModel == null ? null
				: getExplanationsTable().get(new NAMparams(facets, edges, _nullModel));
		if (prev == null) {
			prev = new NonAlchemyExplanation(facets, edges, _nullModel, explanationTask);
		}
		return prev;
	}

	@Override
	public @NonNull Explanation getExplanation(final @NonNull Collection<Perspective> candidates) {
		final @Immutable Explanation facetsSelectedExplanation = FacetSelection.selectFacets(this, candidates);
		assert facetsSelectedExplanation.isImmutable && facetsSelectedExplanation.nullModel == this : this + " "
				+ facetsSelectedExplanation;
		final Explanation edgesSelectedExplanation = EdgeSelection.selectEdges(facetsSelectedExplanation, this);

		assert edgesSelectedExplanation.nullModel == this : this + " " + edgesSelectedExplanation;
		return edgesSelectedExplanation;
	}

	/*************************
	 * END OF METHODS THAT RETURN EXPLANATION
	 *
	 * @param _explanationTask
	 *************************/

	/**
	 * cacheCandidateDistributions as a side effect.
	 */
	static @NonNull Collection<Perspective> topCandidates(final @NonNull ExplanationTask _explanationTask) {
		final SortedSet<Perspective> primaryFacets = _explanationTask.primaryFacets();
		final Collection<Perspective> candidates = topCandidates(primaryFacets, true);
		if (candidates.size() > 0) {
			Distribution.cacheCandidateDistributions(primaryFacets, candidates, _explanationTask);
		} else if (isPrintLevelAtLeast(PrintLevel.STATISTICS)) {
			System.err.println(
					"Warning: NonAlchemyExplanation.getExplanationForFacets: No candidates for " + primaryFacets);
		}
		return candidates;
	}

	@SuppressWarnings("unused")
	protected void learnWeights() {
		final boolean debug = false;
		final long startTime = debug || isPrintLevelAtLeast(PrintLevel.GRAPH) ? UtilString.now() : -1L;
		final double[] initialWeights = predicted.getWeights();
		final @NonNull Set<SimpleEdge> primaryEdges = predicted.getEdgesAmong(explanationTask.primaryFacets(), true);
		printLearnWeightsInfo(startTime, primaryEdges, "initial", observed);
		if (BURN_IN > 0) {
			final @NonNull int[][] allEdges = predicted.edgesToIndexes(predicted.getEdges(true));
			for (int i = 0; i < BURN_IN; i++) {
				if (!optimizeWeights(Util.nonNull(allEdges))) {
					break;
				}
			}
			printLearnWeightsInfo(startTime, Util.nonNull(primaryEdges), "burned-in", predicted);
		}
		final double[] weights = lbfgsSearch(initialWeights); // cgSearch(initialWeights);
		setWeights(weights);
		printLearnWeightsInfo(startTime, primaryEdges, "  final", predicted);
	}

	private @NonNull double[] lbfgsSearch(final @NonNull double[] initialWeights) {
		return Util.nonNull(new LBFGSMinimizer().minimize(this, initialWeights, false));
	}

	/**
	 * predicted.setWeights(argument)
	 */
	private boolean setWeights(final @NonNull double[] weights) {
		boolean isChange = true;
		try {
			isChange = predicted.setWeights(weights);
		} catch (final Error e) {
			System.err.println("Warning: NonAlchemyExplanation.setWeights While setting weights for\n" + observed + "\n"
					+ predicted);
			throw (e);
		}
		if (isChange) {
			cachedEval = Double.NaN;
			cachedGradient[0] = Double.NaN;
		}
		return isChange;
	}

	/**
	 * Only called during BURN_IN.
	 *
	 *
	 * dKL/dw = 0 implies observedOn/observedOff = expEOn/expEOff.
	 *
	 * Only expEOn depends on w, which can be written wOld+deltaW
	 *
	 * Since this factor occurs in every term,
	 *
	 * observedOn/observedOff * expEOff/expEOn(wOld) = exp(deltaW)
	 */
	boolean optimizeWeights(final @NonNull int[][] edges) {
		double kl = 0.0;
		assert (kl = klDivergence()) == kl;
		boolean isChange = false;
		for (final int[] edge : edges) {
			final int cause = edge[0];
			final int caused = edge[1];
			final double delta = Math.log(observed.odds(cause, caused) / predicted.odds(cause, caused));
			final double w = predicted.getWeight(cause, caused);
			if (predicted.setWeight(cause, caused, w + delta)) {
				isChange = true;
			}
			assert (kl = optimizeWeightInternal(kl, cause, caused, delta, w)) == kl;
		}
		if (isChange) {
			predicted.cacheWeightsInfo();
		}
		return isChange;
	}

	private double optimizeWeightInternal(final double kl, final int cause, final int caused, final double delta,
			final double w) {
		final double newkl = klDivergence();
		assert newkl <= kl * 1.0001 || Math.abs(newkl - kl) < 1e-7 : cause + " " + caused + " " + kl + " " + newkl + " "
				+ w + " " + delta + " " + UtilString.valueOfDeep(predicted.getWeights()) + " "
				+ UtilString.valueOfDeep(predicted.getProbDist());
		return newkl;
	}

	/*
	 * Cases:
	 *
	 * FacetSelection: previous has 1 fewer facet, and its parent is 'this'.
	 *
	 * EdgeSelection: previous has 1 additional edge. This model's parent may be
	 * largerModel, or one of its ancestors.
	 *
	 * @return the weightSpaceChange minus the worsening of the kl divergence
	 * compared to the previous over the previous's facets (scaled by
	 * NULL_MODEL_ACCURACY_IMPORTANCE)
	 */
	@Override
	// This is for comparing Explanations, not for LBFGS
	protected double improvement(final @NonNull Explanation _nullModel) {
		final double result = weightSpaceChange(_nullModel);
		maybePrintImprovement(_nullModel, result);
		assert result >= 0.0;
		return result;
	}

	/**
	 * @return distance in displayed parameter space from smallerModel over
	 *         edges among facetsOfInterest (always >= 0.0).
	 */
	private double weightSpaceChange(final @NonNull Explanation baseExplanation) {
		double sumSquaredChange = 0.0;
		if (baseExplanation != this) {
			// if (isPrintLevelAtLeast(PrintLevel.WEIGHTS)) {
			// System.out.println("NonAlchemyExplanation.weightSpaceChange\n
			// currentGuess=" + this
			// + "\n baseExplanation=" + baseExplanation);
			// }
			final @NonNull SortedSet<Perspective> facetsOfInterest = baseExplanation.facets();
			for (final Perspective causedP : facetsOfInterest) {
				assert causedP != null;
				for (final Perspective causeP : facetsOfInterest) {
					if (causeP.compareTo(causedP) <= 0) {
						final double edgeWeightChange = edgeWeightChange(baseExplanation, causeP, causedP);
						final double smoothChange = edgeWeightChange * edgeWeightChange;
						sumSquaredChange += smoothChange;
						assert !Double.isNaN(smoothChange) && smoothChange >= 0.0 : smoothChange + " "
								+ edgeWeightChange;
					}
				}
			}
		}
		return Math.sqrt(sumSquaredChange);
	}

	private double edgeWeightChange(final @NonNull Explanation baseExplanation, final @NonNull Perspective causeP,
			final @NonNull Perspective causedP) {
		double diffUnnormalized = Math.abs(predicted.weightOrSigmoid(causeP, causedP)
				- baseExplanation.predicted.weightOrSigmoid(causeP, causedP));
		if (causeP == causedP) {
			diffUnnormalized *= BIAS_MULTIPLIER;
		}
		maybePrintWSC(causeP, causedP, baseExplanation, diffUnnormalized);
		assert diffUnnormalized >= 0.0;
		return diffUnnormalized;
	}

	@Override
	public @NonNull double[] derivativeAt(final double[] weights) {
		// evaluate(x) is always called right before derivativeAt(x)
		// evaluate(Util.nonNull(x));

		assert weights != null;
		assert !setWeights(weights);
		return computeGradient();
	}

	private @NonNull double[] computeGradient() {
		if (!Double.isNaN(cachedGradient[0])) {
			final double[] gradient = ArrayUtils.clone(cachedGradient);
			assert gradient != null;
			return gradient;
		}
		final double[] predictedDistribution = predicted.getProbDist();
		final double[] observedDistribution = observed.getProbDist();
		final double[] gradient = predicted.bigWeightGradient();

		final int[][] stateWeights = predicted.stateWeights();
		final int nStates = stateWeights.length;
		for (int state = 0; state < nStates; state++) {
			final double q = predictedDistribution[state];
			final double p = observedDistribution[state];
			final double deltaGradient = q - p;
			assert !Double.isNaN(deltaGradient) : q + " " + p;
			final int[] weights = stateWeights[state];
			final int nWeights = weights.length;
			for (int w = 0; w < nWeights; w++) {
				gradient[weights[w]] += deltaGradient;
			}
		}
		System.arraycopy(gradient, 0, cachedGradient, 0, gradient.length);
		return gradient;
	}

	private @NonNull double[] getCachedGradient() {
		assert cachedGradient == null;
		final double[] result = new double[predicted.getNumEdgesPlusBiases()];
		result[0] = Double.NaN;
		return result;
	}

	@Override
	public int dimension() {
		return predicted.getNumEdgesPlusBiases();
	}

	@Override
	public double valueAt(final double[] weights) {
		return evaluate(Util.nonNull(weights));
	}

	/**
	 * This is for LBFGS, not for comparing Explanations
	 */
	private double evaluate(final @NonNull double[] weights) {
		setWeights(weights);
		if (Double.isNaN(cachedEval)) {
			cachedEval = klDivergence() + predicted.bigWeightPenalty();
		}
		return cachedEval;
	}

	@Override
	public @NonNull Graph<Perspective> buildGraph(final boolean isDebug) {
		return buildGraph(nullModel, isDebug);
	}

	private void printLearnWeightsInfo(final long startTime, final @NonNull Set<SimpleEdge> primaryEdges,
			final @NonNull String phase, final @NonNull Distribution distribution) {
		if (startTime > 0L) {
			final double[] weights = new double[primaryEdges.size()];
			int edgeIndex = 0;
			for (final SimpleEdge edge : primaryEdges) {
				weights[edgeIndex++] = edge.graphicalModel != null
						? edge.graphicalModel.getWeight(edge.causeP, edge.causedP) : -1.0;
			}
			System.out
					.println((phase.equals("initial") ? "NonAlchemyExplanation.learnWeights for " + this + "\n " : " ")
							+ phase + " weights\t" + UtilString.elapsedTimeString(startTime, 5)
							+ (phase.equals("burned-in") ? " " + BURN_IN + " iterations, KL=" : "\tKL=")
							+ formatDouble(klDivergence()) + "\tdistribution: "
							+ UtilString.valueOfDeep(distribution.getMarginalDist(explanationTask.primaryFacets()),
									DOUBLE_FORMAT)
							+ (phase.equals("initial") ? ""
									: "\tweights: " + UtilString.valueOfDeep(weights, DOUBLE_FORMAT)));
		}
	}

	private void maybePrintWSC(final @NonNull Perspective causeP, final @NonNull Perspective causedP,
			final @NonNull Explanation baseExplanation, final double diffU) {
		if (isPrintLevelAtLeast(PrintLevel.WEIGHTS)) {
			final String diffUstring = formatDouble(diffU);
			// if (causeP == causedP) {
			// diffUstring += " [* BIAS_MULTIPLIER]";
			// }
			final StringBuilder buf = new StringBuilder();
			buf.append("weightSpaceChange Δσ=");
			buf.append(diffUstring);
			buf.append(" ").append(causeP);
			if (causeP == causedP) {
				buf.append(" [BIAS]");
			} else {
				buf.append(" ⇒ ").append(causedP);
			}
			buf.append("\n base weight ");
			baseExplanation.printW(causeP, causedP, buf).append("\n      weight ");
			printW(causeP, causedP, buf);
			System.out.println(buf.toString());
		}
	}

	private void maybePrintImprovement(final @NonNull Explanation baseExplanation, final double result) {
		if (isPrintLevelAtLeast(PrintLevel.WEIGHTS) && baseExplanation == this) {
			System.out.println("total weightSpaceChange " + formatDouble(result));
			System.out.println(" because this==nullModel: " + this);
			// else {
			// System.out
			// .println(" base observed distribution="
			// + UtilString.valueOfDeep(baseExplanation.observed.getProbDist(),
			// DOUBLE_FORMAT)
			// + "\n base predicted distribution="
			// + UtilString.valueOfDeep(baseExplanation.predicted.getProbDist(),
			// DOUBLE_FORMAT)
			// + "\n predicted distribution=" + UtilString.valueOfDeep(
			// predicted.getMarginalDist(baseExplanation.facets()),
			// DOUBLE_FORMAT));
			// }
		}
	}

	/**
	 * The cache should be a member of Query, but for now we just keep one
	 * global cache.
	 */
	public static void decacheExplanations() {
		getExplanationsTable().clear();
	}

	private void cache() {
		assert isImmutable;
		final Explanation prev = getExplanationsTable().put(new NAMparams(this), this);
		if (prev != null) {
			printGraph();
			prev.printGraph();
			assert false : this;
		}
	}

	private static synchronized @NonNull Map<NAMparams, NonAlchemyExplanation> getExplanationsTable() {
		Map<NAMparams, NonAlchemyExplanation> map = nonAlchemyExplanations == null ? null
				: nonAlchemyExplanations.get();
		if (map == null) {
			map = new Hashtable<>();
			nonAlchemyExplanations = new SoftReference<>(map);
		}
		return map;
	}

	// private double[] cgSearch(double[] sw) {
	// ConjugateGradientSearch search = new ConjugateGradientSearch();
	// double[] weights = search.findMinimumArgs(this, sw, epsilon, epsilon);
	//
	// // if (debug) {
	// // System.out.println("final weights " + (new Date().getTime() - start)
	// // + "ms, " + (search.numFun + BURN_IN) + " iterations, KL "
	// // + klDivergence() + " " + UtilString.valueOfDeep(weights));
	// // System.out.println("wsi " +
	// WEIGHT_STABILITY_IMPORTANCE
	// // + "\n" + this + " " + base);
	// // }
	//
	// totalNumFuns += search.numFun + BURN_IN;
	// totalNumGrad += search.numGrad;
	// return weights;
	// }

	@Immutable
	static class NAMparams {
		final @NonNull Set<Perspective> facets;
		final @NonNull Set<SimpleEdge> edges;
		final @NonNull Explanation nullModel;
		// final double edgeCost;

		NAMparams(final @NonNull NonAlchemyExplanation nonAlchemyExplanation) {
			this(nonAlchemyExplanation.facets(), nonAlchemyExplanation.predicted.getEdges(false),
					nonAlchemyExplanation.nullModel);
		}

		NAMparams(final @NonNull Collection<Perspective> _facets, final @NonNull Set<SimpleEdge> _edges,
				final @NonNull Explanation _nullModel) {
			super();
			assert UtilMath.assertInRange(_facets.size(), 1,
					FacetSelection.MAX_FACET_SELECTION_FACETS) : "Bad _facets.size():" + _facets;
			facets = new HashSet<>(_facets);
			for (final SimpleEdge edge : _edges) {
				assert facets.contains(edge.causeP) && facets.contains(edge.causedP) : facets + " " + _edges;
			}
			edges = new HashSet<>(_edges);
			nullModel = _nullModel;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + edges.hashCode();
			result = prime * result + facets.hashCode();
			result = prime * result + nullModel.hashCode();
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
			final NAMparams other = (NAMparams) obj;
			if (!edges.equals(other.edges)) {
				return false;
			}
			if (!facets.equals(other.facets)) {
				return false;
			}
			if (!nullModel.equals(other.nullModel)) {
				return false;
			}
			return true;
		}
	}

}