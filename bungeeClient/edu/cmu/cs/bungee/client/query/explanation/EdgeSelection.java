package edu.cmu.cs.bungee.client.query.explanation;

import static edu.cmu.cs.bungee.javaExtensions.UtilString.formatCollection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.explanation.Explanation.PrintLevel;
import edu.cmu.cs.bungee.client.query.explanation.GraphicalModel.SimpleEdge;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * FacetSelection and EdgeSelection do the work of creating an Explanation for a
 * null model.
 */
class EdgeSelection extends GreedySubset<SimpleEdge, Explanation> {

	private final @NonNull Map<Set<SimpleEdge>, Explanation> explanations = new HashMap<>();
	private final @NonNull @Immutable Set<SimpleEdge> committedEdges;
	private final @NonNull Explanation maxModel;
	/**
	 * Only used by lookupExplanation()
	 */
	private final @Immutable @NonNull Explanation nullModelForExplanation;

	/**
	 * @return a new Explanation for nullModel with the same facets as
	 *         explanation, but possibly fewer edges.
	 */
	static @NonNull Explanation selectEdges(final @Immutable @NonNull Explanation explanation,
			final @Immutable @NonNull Explanation nullModel) {
		assert explanation.isImmutable;
		assert nullModel.isImmutable;
		Explanation result = explanation;
		if (explanation.predicted.nNonBiasEdges() > 0) {
			/**
			 * Once core edges are removed, removing others will have no effect.
			 * So first remove any non-core edges you can, and then remove only
			 * core edges.
			 */
			final Set<SimpleEdge> coreEdges = candidateEdges(explanation, nullModel, true);
			final Set<SimpleEdge> nonCoreEdges = candidateEdges(explanation, nullModel, false);

			final Explanation intermediateExplanation = nonCoreEdges.size() == 0 ? explanation
					: selectEdgesInternal(nonCoreEdges, coreEdges, explanation, nullModel, nullModel);
			result = selectEdgesInternal(coreEdges, nonCoreEdges, intermediateExplanation,
					new NonAlchemyExplanation(nullModel, intermediateExplanation), nullModel);
		}
		return result;
	}

	private static @NonNull Explanation selectEdgesInternal(final @NonNull Set<SimpleEdge> candidateEdges,
			final @NonNull Set<SimpleEdge> committedEdges, final @NonNull Explanation maxModel,
			final @NonNull Explanation nullModel, final @Immutable @NonNull Explanation _nullModelForExplanation) {
		assert _nullModelForExplanation.isImmutable;
		final EdgeSelection edgeSelection = new EdgeSelection(candidateEdges, committedEdges, maxModel, nullModel,
				_nullModelForExplanation, GraphicalModel.DEFAULT_EDGE_COST);
		if (Explanation.isPrintLevelAtLeast(PrintLevel.STATISTICS)) {
			System.out.println("\nnew EdgeSelection for \n maxModel=" + maxModel + "\n   edges="
					+ formatCollection(maxModel.predicted.getEdges(false))

			+ "\nnullModel=" + nullModel + "\n   edges=" + formatCollection(nullModel.predicted.getEdges(false))

			+ "\n");
		}
		// final Set<SimpleEdge> selectedEdges =

		final Explanation result = edgeSelection.selectCandidates();

		// if (Explanation.isPrintLevelAtLeast(PrintLevel.STATISTICS)) {
		// System.out.println(
		// (edgeSelection.isCoreEdgeSelection() ? "" : "non-") + "core
		// EdgeSelection.selectEdgesInternal ⇒ "
		// + selectedEdges.size() + " " + formatCollection(selectedEdges));
		// }
		return result;
	}

	/**
	 * @param candidateEdges
	 *            not modified
	 * @param _committedEdges
	 *            not modified
	 * @param _maxModel
	 *            not modified; must not be modified later
	 * @param _nullModel
	 *            not modified; must not be modified later
	 * @param _nullModelForExplanation
	 * @param _threshold
	 */
	private EdgeSelection(final @NonNull Set<SimpleEdge> candidateEdges, final @NonNull Set<SimpleEdge> _committedEdges,
			final @NonNull Explanation _maxModel, final @NonNull Explanation _nullModel,
			final @Immutable @NonNull Explanation _nullModelForExplanation, final double _threshold) {
		super(_nullModel, _threshold, candidateEdges, GreedySubsetMode.REMOVE, candidateEdges.size());
		assert _nullModelForExplanation.isImmutable;
		committedEdges = Util.nonNull(Collections.unmodifiableSet(new HashSet<>(_committedEdges)));
		maxModel = _maxModel;
		nullModelForExplanation = _nullModelForExplanation;
		// cacheExplanation(committedPlusAddedEdges(candidateEdges), maxModel);
		addAllCandidatesToCurrentGuess();
	}

	/**
	 * @return can be modified
	 */
	private static @NonNull Set<SimpleEdge> candidateEdges(final @NonNull Explanation explanation,
			final @NonNull Explanation nullModel, final boolean isCoreEdges) {
		final SortedSet<Perspective> primaryFacets = nullModel.facets();
		final Set<SimpleEdge> result = explanation.predicted.getEdges(false);
		for (final Iterator<SimpleEdge> it = result.iterator(); it.hasNext();) {
			final SimpleEdge candidateEdge = it.next();
			if (isCoreEdges != (primaryFacets.contains(candidateEdge.causeP)
					&& primaryFacets.contains(candidateEdge.causedP))) {
				it.remove();
			}
		}
		return result;
	}

	@Override
	protected double eval() {
		final Explanation currentGuessExplanation = lookupExplanation();
		// currentGuessExplanation.nullModel != nullModel for
		// isCoreEdgeSelection()
		currentGuessExplanation.maybePrintToFile(nullModel);
		double eval = currentGuessExplanation.improvement(nullModel);
		if (isCoreEdgeSelection()) {
			// when removing core edges, change is bad
			eval = -eval;
		}
		return eval;
	}

	private boolean isCoreEdgeSelection() {
		final SimpleEdge someEdge = candidates.get(0);
		final SortedSet<Perspective> primaryFacets = nullModel.facets();
		return primaryFacets.contains(someEdge.causeP) && primaryFacets.contains(someEdge.causedP);
	}

	@Override
	@NonNull
	protected Explanation lookupExplanation() {
		final Set<SimpleEdge> allEdges = committedPlusAddedEdges(currentGuess);
		Explanation result = explanations.get(allEdges);
		if (result == null) {
			result = maxModel.getAlternateExplanation(nullModelForExplanation, allEdges);
			cacheExplanation(allEdges, result);
		}
		return result;
	}

	/**
	 * @return a fresh set containing committedEdges + addedEdges
	 */
	private @NonNull Set<SimpleEdge> committedPlusAddedEdges(final @NonNull Collection<SimpleEdge> addedEdges) {
		final Set<SimpleEdge> result = new HashSet<>(committedEdges);
		result.addAll(addedEdges);
		return result;
	}

	private void cacheExplanation(final @NonNull Set<SimpleEdge> edges, final @NonNull Explanation explanation) {
		assert edges.size() == explanation.predicted.nNonBiasEdges();
		final Explanation prev = explanations.put(new HashSet<>(edges), explanation);
		assert prev == null : edges + " " + explanation + " " + prev;

	}

	@Override
	protected boolean maybePrintNewBest(final @Nullable Eval<SimpleEdge> eval) {
		final boolean result = super.maybePrintNewBest(eval);
		if (result) {
			final Explanation best = lookupExplanation();
			best.maybePrintToFile(nullModel);
			if (Explanation.isPrintLevelAtLeast(PrintLevel.GRAPH)) {
				best.printGraph();
			}
		}
		return result;
	}

	@Override
	public String toString() {
		String result;
		if (nullModel == maxModel) {
			result = UtilString.toString(this, "\n nullModel==maxModel=" + nullModel

			+ "\n   weights: " + UtilString.valueOfDeep(nullModel.predicted.getWeights()

			+ "\n " + currentGuess.size() + " edges: " + UtilString.formatCollection(currentGuess)));
		} else {
			result = UtilString.toString(this, "\n nullModel=" + nullModel

			+ "\n   weights: " + UtilString.valueOfDeep(nullModel.predicted.getWeights())

			+ "\n  maxModel=" + maxModel

			+ "\n   weights: "
					+ UtilString.valueOfDeep(maxModel.predicted.getWeights(nullModel.predicted.getEdges(true)))

			+ "\n " + currentGuess.size() + " edges: " + UtilString.formatCollection(currentGuess));
		}
		return result;
	}

}
