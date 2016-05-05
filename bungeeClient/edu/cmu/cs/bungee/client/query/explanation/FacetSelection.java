package edu.cmu.cs.bungee.client.query.explanation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.explanation.Explanation.PrintLevel;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * FacetSelection and EdgeSelection do the work of creating an Explanation for a
 * null model.
 */
public class FacetSelection extends GreedySubset<Perspective, Explanation> {
	public static final int MAX_FACET_SELECTION_FACETS = 7;

	/**
	 * Explanations are @Immutable.
	 */
	private final @NonNull Map<SortedSet<Perspective>, Explanation> explanations = new HashMap<>();
	// private final @Immutable @NonNull Explanation nullModel;

	FacetSelection(final @Immutable @NonNull Explanation _nullModel,
			final @NonNull Collection<Perspective> _candidates) {
		super(_nullModel, GraphicalModel.DEFAULT_EDGE_COST * Math.min(2, _nullModel.nFacets()), _candidates,
				GreedySubsetMode.ADD, MAX_FACET_SELECTION_FACETS - _nullModel.nFacets());
		// System.out.println("\nnew FacetSelection for nullModel=" + _nullModel
		// + "\n");
		assert _nullModel.isImmutable;
		// nullModel = _nullModel;
		cacheCandidates();
	}

	/**
	 * @return best Explanation for nullModel with fewer than MAX_FACETS by
	 *         adding a subset of candidates.
	 */
	static @Immutable @NonNull Explanation selectFacets(final @Immutable @NonNull Explanation nullModel,
			final @NonNull Collection<Perspective> candidates) {
		final FacetSelection facetSelection = new FacetSelection(nullModel, candidates);
		facetSelection.selectCandidates();
		final Explanation result = facetSelection.lookupExplanation();
		// result.printGraph(false);

		result.eval = facetSelection.getValue();
		return result;
	}

	@Override
	protected double eval() {
		final Explanation currentGuessExplanation = lookupExplanation();
		assert currentGuessExplanation.nullModel == nullModel : currentGuessExplanation + " " + nullModel;
		assert currentGuess.isEmpty() || currentGuessExplanation != nullModel : "\n " + currentGuessExplanation + "\n "
				+ nullModel + "\n " + currentGuess;
		currentGuessExplanation.maybePrintToFile(nullModel);
		return currentGuessExplanation.improvement(nullModel);
	}

	@Override
	@Immutable
	@NonNull
	protected Explanation lookupExplanation() {
		Explanation result = null;
		final SortedSet<Perspective> nullModelFacetsPlusAddedFacets = nullModelFacetsPlusAddedFacets();
		result = explanations.get(nullModelFacetsPlusAddedFacets);
		if (result == null) {
			result = nullModel.getAlternateExplanation(nullModel, nullModelFacetsPlusAddedFacets);
			assert result.isImmutable;
			explanations.put(nullModelFacetsPlusAddedFacets, result);
			// explanations.put(result.facets(), result);
		}
		return result;
	}

	@Override
	protected void updateGuess(@SuppressWarnings("unused") final Eval<Perspective> eval) {
		cacheCandidates();
	}

	private void cacheCandidates() {
		Distribution.cacheCandidateDistributions(nullModelFacetsPlusAddedFacets(), candidates,
				nullModel.explanationTask);
	}

	/**
	 * @return nullModel.facets() + addedFacets.
	 */
	private @NonNull SortedSet<Perspective> nullModelFacetsPlusAddedFacets() {
		SortedSet<Perspective> result;
		if (currentGuess.isEmpty()) {
			result = nullModel.facets();
		} else {
			result = new TreeSet<>(nullModel.facets());
			result.addAll(currentGuess);
		}
		return result;
	}

	@Override
	protected boolean maybePrintNewBest(final @Nullable Eval<Perspective> eval) {
		final boolean result = super.maybePrintNewBest(eval);
		if (result) {
			cacheCandidates();
			final Explanation best = lookupExplanation();
			assert best.nullModel == nullModel : best + " " + nullModel;
			best.maybePrintToFile(nullModel);
			if (Explanation.isPrintLevelAtLeast(PrintLevel.GRAPH)) {
				best.printGraph();
			}
		}
		return result;
	}

	// super.maybePrintNewBest(eval);
	// final Explanation best = lookupExplanation(currentGuess);
	// best.maybePrintToFile(nullModel);
	// if (Explanation.isPrintLevelAtLeast(PrintLevel.GRAPH)) {
	// best.printGraph();
	// }
}
