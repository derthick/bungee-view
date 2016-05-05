package edu.cmu.cs.bungee.client.query.explanation;

import static edu.cmu.cs.bungee.javaExtensions.UtilString.formatCollection;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.explanation.GraphicalModel.SimpleEdge;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.query.query.QuerySQL;
import edu.cmu.cs.bungee.javaExtensions.FormattedTableBuilder;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.StringAlign;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.UtilString.Justification;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph;
import edu.cmu.cs.bungee.javaExtensions.permutations.MyCombinationIterator;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyGraph;
import edu.umd.cs.piccolo.nodes.PText;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Encapsulates an observed Distribution and a GraphicalModel (a kind of
 * Distribution) that explains it.
 */
public abstract class Explanation implements GreedySubsetResult, RedrawCallback, Serializable {

	protected static final long serialVersionUID = 1L;

	enum PrintLevel {
		NOTHING,

		/**
		 * Print getExplanation nullModel and final model, with weights, and
		 * elapsed time. Also print weights for each call to
		 * selectEdgesInternal.
		 */
		STATISTICS,

		/**
		 * Print evaluations for each candidate for FacetSelection and
		 * EdgeSelection, and their final selections.
		 */
		IMPROVEMENT,

		/**
		 * Print GraphicalModel.graph2string for each new bestCandidate.
		 */
		GRAPH,

		/**
		 * For each candidate, print distribution and changes for every weight.
		 */
		WEIGHTS
	}

	static final @NonNull PrintLevel PRINT_LEVEL = PrintLevel.NOTHING;

	public enum WeightNormalizationMethod {
		RAW_WEIGHT, MEAN_DELTA_R, MARGINAL_DELTA_R
	}

	static final @NonNull WeightNormalizationMethod WEIGHT_NORMALIZATION_METHOD = WeightNormalizationMethod.RAW_WEIGHT;

	static final boolean USE_SIGMOID = true;

	/**
	 * If this is too large (~1000), onCountMatrix command barfs.
	 */
	public static final int MAX_CANDIDATES = 10;

	static final boolean PRINT_CANDIDATES_TO_FILE = false;
	static final boolean PRINT_RSQUARED = false;

	static final @NonNull StringAlign STRING_ALIGN_7_RIGHT = new StringAlign(7, Justification.RIGHT);
	static final @NonNull DecimalFormat COUNT_FORMAT = new DecimalFormat("###,###");
	static final @NonNull DecimalFormat PERCENT_FORMAT = new DecimalFormat("###%");
	static final @NonNull DecimalFormat DOUBLE_FORMAT = new DecimalFormat(" #0.000;-#0.000");

	protected final @NonNull GraphicalModel predicted;
	protected final @Immutable @NonNull Distribution observed;
	public final @Immutable @NonNull ExplanationTask explanationTask;
	protected final @NonNull Explanation nullModel;
	protected final boolean isImmutable;
	public double eval = Double.NaN;

	/**
	 * An Explanation with the specified facets and edges. observed is
	 * initialized based on current Query. predicted is an uninitialized
	 * GraphicalModel.
	 *
	 * @param _isImmutable
	 */
	protected Explanation(final @NonNull SortedSet<Perspective> facets, final @NonNull Set<SimpleEdge> edges,
			@Nullable final Explanation _nullModel, final @Immutable @NonNull ExplanationTask _explanationTask,
			final boolean _isImmutable) {
		assert facets.size() <= FacetSelection.MAX_FACET_SELECTION_FACETS;
		explanationTask = _explanationTask;
		final Collection<Perspective> candidates = Collections.emptyList();
		observed = Distribution.cacheCandidateDistributions(facets, Util.nonNull(candidates), explanationTask);
		assert observed.facets.equals(facets) : observed.facets + " " + facets;
		predicted = new GraphicalModel(facets, edges, /* isSymmetric */
				true, observed.totalCount);
		assert predicted.facets.equals(observed.facets) : predicted.facets + " " + observed.facets;
		nullModel = _nullModel == null ? this : _nullModel;
		isImmutable = _isImmutable;
	}

	/**
	 * this Explanation is @Immutable.
	 *
	 * @return an Explanation for this considered as a null model, by adding a
	 *         subset of candidates.
	 */
	public abstract @NonNull Explanation getExplanation(@NonNull Collection<Perspective> candidates);

	/**
	 * Called only by FacetSelection.lookupExplanation()
	 *
	 * @return an explanation with nullModelFacetsPlusAddedFacets
	 */
	abstract @Immutable @NonNull Explanation getAlternateExplanation(final @Immutable @NonNull Explanation _nullModel,
			@NonNull SortedSet<Perspective> nullModelFacetsPlusAddedFacets);

	/**
	 * @return an explanation with the same facets but with the specified edges.
	 */
	abstract @NonNull Explanation getAlternateExplanation(final @NonNull Explanation _nullModel,
			@NonNull Set<SimpleEdge> edges);

	/*************************
	 * END OF METHODS THAT RETURN EXPLANATIONS
	 *************************/

	@Override
	public void setEval(final double _eval) {
		eval = _eval;
	}

	boolean isNullModel() {
		return this == nullModel;
	}

	static boolean isPrintLevelAtLeast(final @NonNull PrintLevel printLevel) {
		return PRINT_LEVEL.compareTo(printLevel) >= 0;
	}

	public @NonNull SortedSet<Perspective> facets() {
		return observed.facets;
	}

	protected int nFacets() {
		return facets().size();
	}

	private int facetIndex(final @NonNull Perspective p2) {
		return predicted.facetIndex(p2);
	}

	double klDivergence() {
		return observed.klDivergenceFromLog(predicted.getLogDistribution());
	}

	/**
	 * @return distance in weight space between this Explanation and
	 *         baseExplanation. If fast approximation > threshold, recalculate
	 *         more carefully. Always positive.
	 */
	abstract double improvement(@NonNull Explanation baseExplanation);

	/******************** GraphicalModel stuff *****************/

	static final double BIAS_MULTIPLIER = 0.1;

	double getRNormalizedWeight(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		return ensureRNormalizedWeights()[facetIndex(causeP)][facetIndex(causedP)];
	}

	private double[][] rNormalizedWeights;

	/**
	 * @return weights normalized so Math.abs(weight) of all edges causing a
	 *         node sum to 1.0 (edges have distinct forward and backward
	 *         weights).
	 */
	@NonNull
	double[][] ensureRNormalizedWeights() {
		if (rNormalizedWeights == null) {
			final SortedSet<Perspective> facets = facets();
			final int nFacets = nFacets();

			rNormalizedWeights = new double[nFacets][];
			for (int i = 0; i < rNormalizedWeights.length; i++) {
				rNormalizedWeights[i] = new double[nFacets];
			}
			int caused = 0;
			for (final Perspective causedP : facets) {
				assert causedP != null;
				double sum = 0.0;
				int cause = 0;
				for (final Perspective causeP : facets) {
					assert causeP != null;
					if (caused != cause && predicted.hasEdge(cause, caused)) {
						final double w = computeRNormalizedWeight(causeP, causedP);
						rNormalizedWeights[cause][caused] = w;
						sum += Math.abs(w);
					}
					cause++;
				}
				if (sum > 0.0) {
					final double correction = ensureRs()[caused] / sum;
					for (int cause1 = 0; cause1 < nFacets; cause1++) {
						rNormalizedWeights[cause1][caused] *= correction;
					}
				}
				caused++;
			}
		}
		assert rNormalizedWeights != null;
		return rNormalizedWeights;
	}

	private double computeRNormalizedWeight(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		double result = Double.NaN;
		switch (WEIGHT_NORMALIZATION_METHOD) {
		case RAW_WEIGHT:
			result = predicted.getWeight(facetIndex(causeP), facetIndex(causedP));
			break;
		case MEAN_DELTA_R:
			result = meanDeltaR(causeP, causedP);
			break;
		case MARGINAL_DELTA_R:
			result = marginalDeltaR(causeP, causedP);
			break;

		default:
			assert false : WEIGHT_NORMALIZATION_METHOD;
			break;
		}
		return result;
	}

	double averageRNormalizedWeight(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		return (getRNormalizedWeight(causeP, causedP) + getRNormalizedWeight(causedP, causeP)) / 2.0;
	}

	/**
	 * Let's try adding this link last.
	 */
	private double marginalDeltaR(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		final int cause = facetIndex(causeP);
		final int caused = facetIndex(causedP);
		final double with = R(caused);
		final Set<SimpleEdge> edges = predicted.getEdges(false);
		edges.remove(SimpleEdge.getInstance(causeP, causedP, null));
		final Explanation withoutModel = Util.nonNull(getAlternateExplanation(nullModel, edges));
		final double without = withoutModel.R(caused);
		final double result = Math.signum(predicted.getWeight(cause, caused)) * Math.max(0.0, with - without);
		assert -1.0 <= result && result <= 1.0 : with + " " + without;
		return result;
	}

	/**
	 * I read in some article that averaging over all the orders for adding
	 * edges is the best way to determine which causes are most important.
	 * However, it doesn't correspond to how important causes are in the current
	 * model, so it's misleading to display.
	 */
	private double meanDeltaR(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		final int cause = facetIndex(causeP);
		final int caused = facetIndex(causedP);
		int nPerm = 0;
		double sumR2 = 0;
		final Collection<Perspective> otherCauses = predicted.getCauses(causedP);
		otherCauses.remove(causeP);
		final SimpleEdge edge = SimpleEdge.getInstance(causeP, causedP, predicted);
		for (final Iterator<List<Perspective>> combIt = new MyCombinationIterator<>(otherCauses); combIt.hasNext();) {
			final @NonNull Collection<Perspective> x = Util.nonNull(combIt.next());

			final Set<SimpleEdge> edges = predicted.getEdges(false);
			edges.removeAll(GraphicalModel.getEdgesTo(x, causedP));
			final Explanation withModel = x.isEmpty() ? this : Util.nonNull(getAlternateExplanation(nullModel, edges));
			final double with = withModel.R(caused);
			edges.remove(edge);
			final Explanation withoutModel = Util.nonNull(getAlternateExplanation(nullModel, edges));
			final double without = withoutModel.R(caused);
			if (with > without) {
				sumR2 += with - without;
			}
			nPerm++;
		}
		final double result = Math.signum(predicted.getWeight(cause, caused)) * sumR2 / nPerm;
		assert -1 <= result && result <= 1 : sumR2 + " " + nPerm;
		return result;
	}

	/**
	 * R squared — the Coefficient of Determination — is the proportion of
	 * variability in a data set that can be explained by a statistical model.
	 * It is the square of the correlation coefficient, r (hence the term r
	 * squared).
	 *
	 * rRoots are the signed square roots of the individual coefficients for
	 * pseudoRsquared.
	 */
	private double[] rRoots;

	private @NonNull double[] ensureRs() {
		if (rRoots == null) {
			rRoots = new double[nFacets()];
			int cause = 0;
			for (final Perspective causeP : facets()) {
				assert causeP != null;
				final double r2 = pseudoRsquared(causeP);
				rRoots[cause] = Math.sqrt(Math.abs(r2)) * Math.signum(r2);
				++cause;
			}
		}
		assert rRoots != null;
		return rRoots;
	}

	/**
	 * Efron's Pseudo R-Squared see
	 *
	 * http://www.ats.ucla.edu/stat/mult_pkg/faq/general/Psuedo_RSquareds.htm
	 *
	 * Compares predicted, rather than do a logistic regression
	 */
	private double pseudoRsquared(final @NonNull Perspective perspective) {
		double residuals = 0.0;
		double baseResiduals = 0.0;
		final int causedIndex = facetIndex(perspective);
		final double unconditionalProb = getUnconditionalProb(perspective);
		if (unconditionalProb > 0.0) {
			final FormattedTableBuilder align = getFormattedTableBuilder(perspective);
			final double[] predDist = predicted.getProbDist();
			final double[] obsDist = observed.getProbDist();
			for (int offState = 0; offState < observed.nStates; offState++) {
				// We're calculating this state-by-state in order to print
				if (UtilMath.getBit(offState, causedIndex) == 0) {
					final int onState = UtilMath.setBit(offState, causedIndex, true);
					final double obsOn = obsDist[onState];
					final double obsOff = obsDist[offState];
					final double predictedTotal = predDist[onState] + predDist[offState];
					final double yHat = predictedTotal > 0.0 ? predDist[onState] / predictedTotal : 0.5;
					final double residual = UtilMath.square(yHat) * obsOff + UtilMath.square(1.0 - yHat) * obsOn;
					residuals += residual;
					assert !Double.isNaN(residuals) : residual + " " + yHat + " " + UtilString.valueOfDeep(predicted);
					final double baseResidual = UtilMath.square(unconditionalProb) * obsOff
							+ UtilMath.square(1.0 - unconditionalProb) * obsOn;
					baseResiduals += baseResidual;
					if (PRINT_RSQUARED) {
						for (int i = 0; i < facets().size(); i++) {
							if (i != causedIndex) {
								align.addLine(UtilMath.getBit(offState, i), obsOn, "/", obsOn + obsOff, "=",
										formatPercent(obsOn, obsOff), formatPercent(yHat), formatResidual(residual),
										formatResidual(baseResidual), Math.log(predDist[offState] / predDist[0]),
										Math.log(predDist[onState] / predDist[0]),
										Math.log(predDist[onState] / predDist[offState]), predDist[offState],
										predDist[onState]);
								break;
							}
						}
					}
				}
			}
			pseudoRsquaredPrintNassert(residuals, baseResiduals, unconditionalProb, align, perspective);
		}
		final double Rsquare = baseResiduals == 0.0 ? 0.0 : 1.0 - residuals / baseResiduals;
		return Math.max(Rsquare, 0.0);
	}

	private void pseudoRsquaredPrintNassert(final double residuals, final double baseResiduals,
			final double unconditionalProb, final FormattedTableBuilder align, final @NonNull Perspective perspective) {
		final int causedIndex = facetIndex(perspective);
		final double Rsquare = 1.0 - residuals / baseResiduals;
		if (Double.isNaN(Rsquare) || PRINT_RSQUARED) {
			System.out.println("pseudoR=" + formatDouble(Math.sqrt(Math.abs(Rsquare)) * Math.signum(Rsquare))
					+ "\n unconditionalProb(" + perspective + ")=" + formatPercent(unconditionalProb)
					+ " residuals/baseResiduals=" + formatResidual(residuals) + "/" + formatResidual(baseResiduals)
					+ "\n klDivergence=" + formatDouble(klDivergence()) + " klDivergence(independenceDistribution)="
					+ formatDouble(observed.klDivergence(observed.independenceDistribution().probDist))
					+ "\n predicted=" + predicted + "\n    non-bias edges="
					+ formatCollection(predicted.getEdges(false)));
		}
		if (align != null) {
			System.out.println(align.format());
		}

		// When trying to model a larger Distribution, it can end up predicting
		// the primary facets worse than the unconditional average
		assert Rsquare <= 1.0001 && (predicted.nNonBiasEdges() > 0 || Rsquare + 0.1 > 0.0) : this + " Rsquare="
				+ Rsquare + " unconditional=" + unconditionalProb + " " + predicted + " residuals=" + residuals
				+ " baseResiduals=" + baseResiduals;
		assert !Double.isNaN(Rsquare) && !Double.isInfinite(Rsquare) : causedIndex + " " + predicted + " " + residuals
				+ " / " + baseResiduals + " " + UtilString.valueOfDeep(observed.getProbDist());
	}

	private FormattedTableBuilder getFormattedTableBuilder(final Perspective perspective) {
		FormattedTableBuilder align = null;
		if (PRINT_RSQUARED) {
			align = new FormattedTableBuilder();
			align.setMinSpacing(2);
			align.setMaxFractionChars(5);
			for (final Perspective facet : facets()) {
				if (facet != perspective) {
					align.addLine(facet.getNameIfCached(), "obsOn", "", "obsOn+obsOff", "", "observed", "pred",
							"residua", "uncondi", "off_nrg", "on_nrg", "predOff", "predOn", "logOdds");
					break;
				}
			}
		}
		return align;
	}

	private double getUnconditionalProb(final Perspective perspective) {
		final SortedSet<Perspective> marginals = new TreeSet<>();
		marginals.add(perspective);
		final double[] marginal = observed.getMarginalDist(marginals);
		if (marginal[0] == 0.0 || marginal[1] == 0.0) {
			return 0.0;
		}
		final double unconditional = marginal[1] / (marginal[0] + marginal[1]);
		return unconditional;
	}

	/******************** End GraphicalModel stuff *****************/

	@Override
	public void maybePrintToFile(final @NonNull GreedySubsetResult _nullModel) {
		if (PRINT_CANDIDATES_TO_FILE) {
			final String explDesc = toString();
			final Graph<Perspective> graph = buildGraph((Explanation) Util.nonNull(_nullModel), true);
			@SuppressWarnings("null")
			final LazyGraph<Perspective> lazyGraph = new LazyGraph<>(graph, new PText(explDesc));
			lazyGraph.printToFile(explDesc);
		}
	}

	@NonNull
	Graph<Perspective> buildGraph(final @NonNull Explanation _nullModel, final boolean debug) {
		return predicted.buildGraph(ensureRs(), ensureRNormalizedWeights(), klDivergence(), _nullModel, debug);
	}

	/**
	 * Only called by NonAlchemyExplanation.topCandidates, with
	 * excludePrimary==true
	 *
	 * I think variable selection algorithm is broken. Do not use, for now;
	 *
	 * @return best candidates, sorted from best to worst.
	 *
	 *         WILL BE EMPTY if no candidates found or if
	 *         query.isRestrictedData().
	 */
	static @NonNull Collection<Perspective> topCandidates(final @NonNull SortedSet<Perspective> primaryFacets,
			final boolean excludePrimary) {
		Collection<Perspective> result;
		final Query query = Query.query(primaryFacets);
		if (!query.isRestrictedData()) {
			result = query.topCandidates(Util.nonNull(QuerySQL.itemPredsSQLexpr(null, primaryFacets, "?").toString()));
			if (excludePrimary) {
				result.removeAll(primaryFacets);
			} else {
				result.addAll(primaryFacets);
			}

			final int queryCount = query.getTotalCount();
			for (final Iterator<Perspective> it = result.iterator(); it.hasNext();) {
				final Perspective candidate = it.next();
				final int totalCount = candidate.getTotalCount();
				if (totalCount == queryCount) {
					it.remove();
				}
			}

			// if (isPrintLevelAtLeast(PrintLevel.WEIGHTS)) {
			// System.out.println("\nExplanation.topCandidates " + primaryFacets
			// + " ⇒ " + result.size()
			// + " candidates:\n" + result + "\n");
			// }
		} else {
			result = new ArrayList<>();
		}
		return result;
	}

	static @NonNull String formatPercent(final double pOn, final double pOff) {
		return formatPercent(pOn / (pOn + pOff));
	}

	static @NonNull String formatPercent(final double x) {
		return (new StringAlign(4, Justification.RIGHT)).format(x, PERCENT_FORMAT);
	}

	static @NonNull String formatDouble(final double x) {
		return STRING_ALIGN_7_RIGHT.format(x, DOUBLE_FORMAT);
	}

	@NonNull
	String formatResidual(final double x) {
		if (Double.isNaN(x)) {
			return "NaN";
		}
		return formatInt(10_000 * x / observed.totalCount);
	}

	@NonNull
	String formatInt(final double dx) {
		final int x = UtilMath.roundToInt(dx * observed.totalCount);
		return STRING_ALIGN_7_RIGHT.format(x, COUNT_FORMAT);
	}

	void printTable(final @NonNull Perspective p) {
		pseudoRsquared(p);
	}

	protected void printInfo(final long start) {
		nullModel.maybePrintToFile(nullModel);
		final SortedSet<Perspective> primaryFacets = nullModel.facets();
		assert facets().containsAll(primaryFacets) : this;
		if (Explanation.isPrintLevelAtLeast(PrintLevel.STATISTICS)) {
			final String methodName = UtilString.shortClassName(this) + ".getExplanation";
			if (nFacets() > nullModel.nFacets()) {
				System.out.println("\n" + methodName + "\n nullModel=" + nullModel + "\n non-bias edges: "
						+ UtilString.formatCollection(nullModel.predicted.getEdges(false))

						+ "\n\n result=" + this + "\n  non-bias edges: "
						+ UtilString.formatCollection(predicted.getEdges(false))
				// + "\n distance from nullModel=" +
				// formatDouble(improvement(nullModel))
				);
			}
			System.out.println(methodName + " took " + UtilString.elapsedTimeString(start, 6) + " nNonBiasEdges="
					+ predicted.nNonBiasEdges() + "\n");
			if (Explanation.isPrintLevelAtLeast(PrintLevel.GRAPH)) {
				nullModel.printGraph();
				printGraph();
				if (Explanation.isPrintLevelAtLeast(PrintLevel.WEIGHTS)) {
					for (final Perspective facet : primaryFacets) {
						assert facet != null;
						nullModel.printTable(facet);
						printTable(facet);
					}
				}
			}
		}
	}

	public SortedSet<Perspective> nonPrimaryFacets() {
		final SortedSet<Perspective> nonPrimaryFacets = new TreeSet<>(facets());
		nonPrimaryFacets.removeAll(unusedFacets());
		nonPrimaryFacets.removeAll(primaryFacets());
		return nonPrimaryFacets;
	}

	// /**
	// * @return nFacets() > nullModel.nFacets()
	// */
	// boolean printStats(final @NonNull Explanation _nullModel) {
	// final boolean result = nFacets() > _nullModel.nFacets();
	// if (result) {
	// final SortedSet<Perspective> primaryFacets = _nullModel.facets();
	// final List<Perspective> nonPrimaryFacets = new LinkedList<>(facets());
	// nonPrimaryFacets.removeAll(primaryFacets);
	// System.out.println("\nExplanation.getExplanation " + "\n nullModel=" +
	// _nullModel
	// + UtilString.formatCollection(_nullModel.predicted.getEdges(true)) +
	// "\n\n result=" + this
	// + UtilString.formatCollection(predicted.getEdgesAmong(primaryFacets,
	// true))
	//
	// // + "\n\n observed.correlationHacked(nullModel)=" +
	// // observed.correlationHacked(nullModel)
	// // + "\n -interactionInformationHacked(nullModel)=" +
	// // (-interactionInformationHacked(nullModel))
	//
	// + "\n distance from nullModel="
	//
	// // + formatDouble(eval)
	// );
	// }
	// return result;
	// }

	protected @NonNull StringBuilder printW(final @NonNull Perspective causeP, final @NonNull Perspective causedP,
			final @NonNull StringBuilder buf) {
		if (USE_SIGMOID) {
			buf.append("σ(").append(formatDouble(predicted.getWeightOrZero(causeP, causedP))).append(") = ");
		}
		buf.append(formatDouble(predicted.weightOrSigmoid(causeP, causedP)));
		return buf;
	}

	// /**
	// * @return If nullModel has 2 [primary] facets, sum entropies of
	// * +allButPrimary0 +allButPrimary1 -observed -nonPrimary
	// *
	// * Else Double.NaN
	// *
	// * @see "http://en.wikipedia.org/wiki/Interaction_information"
	// */
	// private double interactionInformationHacked(final Explanation nullModel)
	// {
	// final SortedSet<Perspective> primary = nullModel.facets();
	// if (primary.size() != 2) {
	// return Double.NaN;
	// }
	// final SortedSet<Perspective> nonPrimary = new TreeSet<>(facets());
	// nonPrimary.removeAll(primary);
	// double result = -observed.entropy() - Distribution.ensureDist(nonPrimary,
	// explanationTask).entropy();
	//
	// nonPrimary.add(UtilArray.get(primary, 0));
	// result += Distribution.ensureDist(nonPrimary, explanationTask).entropy();
	//
	// nonPrimary.remove(UtilArray.get(primary, 0));
	// nonPrimary.add(UtilArray.get(primary, 1));
	// result += Distribution.ensureDist(nonPrimary, explanationTask).entropy();
	//
	// return result;
	// }

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		if (!Double.isNaN(eval)) {
			buf.append(" eval=").append(formatDouble(eval));
		}
		buf.append(" nNonBiasEdges=").append(predicted.nNonBiasEdges());
		buf.append(" nFacets=").append(nFacets()).append(" ").append(UtilString.formatCollection(facets()));

		// if (false) {
		// for (final Iterator<int[]> it = predicted.getEdgeIterator();
		// it.hasNext();) {
		// final int[] edge = it.next();
		// final int causeNode = edge[0];
		// final int causedNode = edge[1];
		// assert predicted.hasEdge(causeNode, causedNode);
		// final double weight = predicted.getWeight(causeNode, causedNode);
		// final Perspective causeP = UtilArray.get(facets(), causeNode);
		// final Perspective causedP = UtilArray.get(facets(), causedNode);
		// buf.append("\n").append(weight).append(" (")
		// .append(predicted.getStdDevNormalizedWeight(Util.nonNull(causeP),
		// Util.nonNull(causedP)))
		// .append(") ").append(causeP).append(" ---- ").append(causedP);
		// }
		// } else {
		// }

		return UtilString.toString(this, buf);
	}

	public int nEdges() { // NO_UCD (unused code)
		return predicted.nNonBiasEdges();
	}

	@Override
	public void printGraph() {
		predicted.graph2string(ensureRs(), ensureRNormalizedWeights(), klDivergence());
	}

	double R(final int facetIndex) {
		return ensureRs()[facetIndex];
	}

	/*
	 * This is just for the sake of files. PopupSummary is the redrawer for
	 * client graphs.
	 */
	@Override
	public void redrawCallback() {
		// System.out.println("Explanation.redraw " + this);
		// printToFile();
	}

	public abstract @NonNull Graph<Perspective> buildGraph(boolean debug);

	public @NonNull Distribution getObservedDistribution() {
		return observed;
	}

	public @NonNull GraphicalModel getPredictedDistribution() {
		return predicted;
	}

	int nUsedFacets() { // NO_UCD (unused code)
		return predicted.nUsedFacets();
	}

	/**
	 * @return Perspectives for unconnected nodes.
	 */
	public @NonNull Collection<Perspective> unusedFacets() { // NO_UCD (unused
																// code)
		return predicted.unusedFacets();
	}

	protected @NonNull Query query() {
		return Query.query(explanationTask.primaryFacets());
	}

	public @NonNull SortedSet<Perspective> primaryFacets() {
		return nullModel.facets();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (nullModel == this ? 0 : nullModel.hashCode());
		result = prime * result + observed.hashCode();
		result = prime * result + predicted.hashCode();
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
		final Explanation other = (Explanation) obj;
		if (nullModel != other.nullModel) {
			return false;
		}
		if (!observed.equals(other.observed)) {
			return false;
		}
		if (!predicted.equals(other.predicted)) {
			return false;
		}
		return true;
	}

}
