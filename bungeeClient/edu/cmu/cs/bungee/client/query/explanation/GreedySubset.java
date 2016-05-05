package edu.cmu.cs.bungee.client.query.explanation;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.explanation.Explanation.PrintLevel;
import edu.cmu.cs.bungee.javaExtensions.FormattedTableBuilder;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Find the best concise subset of candidates, where it's worth adding a
 * candidates if it contributes more than threshold to the evaluation function.
 */
abstract class GreedySubset<C, E extends GreedySubsetResult> {

	enum GreedySubsetMode {
		ADD_AND_REMOVE, ADD, REMOVE;

		boolean isApplicable(final boolean isAdd) {
			return (this == GreedySubsetMode.ADD_AND_REMOVE || (isAdd == (this == GreedySubsetMode.ADD)));
		}
	}

	protected final @NonNull Set<C> currentGuess;
	private final @NonNull GreedySubsetMode mode;
	/**
	 * Cs you can add and remove from model. selectCandidates may be more
	 * efficient if candidates are sorted from bst to worst.
	 */
	protected final @NonNull @Immutable List<C> candidates;
	/**
	 * Use this for eval. For coreEdgeSelection, it is NOT the eventual
	 * nullModel of the resulting Explanation.
	 */
	protected final @NonNull E nullModel;

	protected final int maxCandidates;
	private final double threshold;
	/**
	 * Don't toggle if the resulting state has already been evaluated.
	 */
	private final @NonNull Set<Set<C>> previousGuesses = new HashSet<>();
	private final @NonNull SortedSet<Eval<C>> evals = new TreeSet<>();
	/**
	 * NaN is a placeholder for lazy evaluation, because FacetSelection.eval
	 * fails before FacetSelection is fully initialized.
	 */
	private double previousValue = Double.NaN;

	/**
	 * @param _nullModel
	 * @param _candidates
	 *            selectCandidates may be more efficient if _candidates are
	 *            sorted from bst to worst.
	 */
	protected GreedySubset(final @NonNull E _nullModel, final double _threshold,
			final @NonNull Collection<C> _candidates, final @NonNull GreedySubsetMode _mode, final int _maxCandidates) {
		super();
		assert _candidates.size() > 0;
		currentGuess = new HashSet<>();
		threshold = _threshold;
		candidates = Util.nonNull(Collections.unmodifiableList(new ArrayList<>(_candidates)));
		maxCandidates = _maxCandidates;
		mode = _mode;
		nullModel = _nullModel;
	}

	/**
	 * The raison d'etre of GreedySubset
	 */
	protected @NonNull E selectCandidates() {
		while (maybeUpdate(false) || maybeUpdate(true)) {
			// Keep updating until at local optimum
		}
		if (isPrintImprovements()) {
			System.out.println(this);
		}
		final E result = lookupExplanation();
		result.setEval(getValue());
		// System.out.println("GreedySubset.selectCandidates returning " +
		// result);
		return result;
	}

	/**
	 * toggle the best togglable candidate, if any are above threshold.
	 */
	private boolean maybeUpdate(final boolean isAdd) {
		boolean result = mode.isApplicable(isAdd);
		if (result) {
			final double threshold1 = previousValue() + getThreshold(isAdd);
			final Eval<C> eval = bestCandidate(isAdd, threshold1);
			result = eval != null;
			if (result) {
				assert eval != null;
				toggle(eval.candidate);
				previousGuesses.add(new HashSet<>(currentGuess));
				previousValue = eval.value;
				updateGuess(eval);
			}
			maybePrintBestCandidate(isAdd, threshold1);
			maybePrintNewBest(eval);
		}
		return result;
	}

	/**
	 * eval.candidate has just been toggled
	 */
	protected void updateGuess(@SuppressWarnings("unused") final Eval<C> eval) {
		// override this
	}

	/**
	 * @param threshold1
	 * @return bestCandidate to toggle, or null if none are above threshold.
	 */
	private @Nullable Eval<C> bestCandidate(final boolean isAdd, final double threshold1) {
		double bestValue = threshold1;
		Eval<C> bestEval = null;
		evals.clear();
		for (final C candidate : candidates) {
			assert candidate != null;
			if (maybeIsAdd(candidate, isAdd)) {
				// if (Explanation.isPrintLevelAtLeast(PrintLevel.WEIGHTS)) {
				// System.out.println("\n" + UtilString.shortClassName(this) +
				// ".eval with" + (isAdd ? " " : "out ")
				// + candidate + "; threshold=" +
				// Explanation.formatDouble(threshold1));
				// }
				final double value = eval();
				final Eval<C> eval = new Eval<>(value, candidate);
				evals.add(eval);
				if (Explanation.isPrintLevelAtLeast(PrintLevel.WEIGHTS)) {
					final String formattedValue = Explanation.formatDouble(value);
					System.out.println(UtilString.shortClassName(this) + ".eval "
							+ (value > threshold1 ? formattedValue + " > " : "[" + formattedValue + "] < ")
							+ "threshold=" + Explanation.formatDouble(threshold1) + " for "
							+ (isAdd ? "adding " : " removing ") + candidate + "\n");
				}
				if (value > bestValue) {
					bestValue = value;
					bestEval = eval;
				}
				toggle(candidate);
			}
		}
		return bestEval;
	}

	/**
	 * @param isAdd
	 *            update currentGuess by (isAdd ? "adding" : "removing")
	 *            candidate, unless that guess has already been evaluated or
	 *            would exceed maxCandidates.
	 * @return whether currentGuess changed
	 */
	private boolean maybeIsAdd(final @NonNull C candidate, final boolean isAdd) {
		boolean result = (isAdd != currentGuess.contains(candidate)) && toggle(candidate);
		if (result) {
			result = !previousGuesses.contains(currentGuess);
			if (!result) {
				// Oops, undo
				toggle(candidate);
			}
		}
		return result;
	}

	/**
	 * @return whether toggling is legal (doesn't exceed maxCandidates)
	 */
	private boolean toggle(final @NonNull C candidate) {
		boolean result = true;
		if (currentGuess.contains(candidate)) {
			currentGuess.remove(candidate);
		} else if (currentGuess.size() < maxCandidates) {
			currentGuess.add(candidate);
		} else {
			result = false;
		}
		return result;
	}

	/**
	 * @return previousValue, the evaluation of the E from isAdd'ing the most
	 *         recent successful bestCandidate().
	 */
	public double getValue() {
		return previousValue;
	}

	private double previousValue() {
		if (Double.isNaN(previousValue)) {
			previousValue = eval();
		}
		return previousValue;
	}

	static final NumberFormat DOUBLE_FORMAT = new DecimalFormat("0.00000");

	private void maybePrintBestCandidate(final boolean isAdd, final double threshold1) {
		if (isPrintImprovements()) {
			System.out.println("\n" + UtilString.shortClassName(this) + ".bestCandidate "
					+ (isAdd ? "is adding " : "is removing") + " with threshold=" + DOUBLE_FORMAT.format(threshold1));

			final FormattedTableBuilder align = new FormattedTableBuilder();
			// align.setMinSpacing(2);
			align.setMaxFractionChars(5);
			align.addLine("eval", "candidate", "original rank");

			for (final Eval<C> e : Util.nonNull(evals)) {
				align.addLine((e.value > threshold1 ? " ⇑" : " ↓") + e.value, e.candidate,
						(candidates.indexOf(e.candidate) + 1));
			}
			System.out.println(align.format());
			// if (bestEval != null) {
			// final boolean win = bestEval.value > threshold1;
			System.out.println(
					// "BEST Candidate " + (isAdd ? "+" : "-")
					// + (win ? bestEval.candidate : "[" +
					// bestEval.candidate + "]") + " Rank = "
					// + (candidates.indexOf(bestEval.candidate) + 1) +
					// "\n ⇒ " +
					this + "\n");
			// }
		}
	}

	protected void addAllCandidatesToCurrentGuess() {
		currentGuess.addAll(candidates);
		assert currentGuess.size() <= maxCandidates : addAllCandidatesErrMsg();
	}

	private @NonNull String addAllCandidatesErrMsg() {
		final Collection<C> currentMinusCandidates = new HashSet<>(currentGuess);
		currentMinusCandidates.removeAll(candidates);
		return "More than " + maxCandidates + " candidates: " + currentMinusCandidates.size() + " from currentGuess "
				+ currentMinusCandidates + " and " + candidates.size() + " from candidates " + candidates;
	}

	private static boolean isPrintImprovements() {
		return Explanation.isPrintLevelAtLeast(PrintLevel.IMPROVEMENT);
	}

	protected boolean maybePrintNewBest(@SuppressWarnings("unused") final @Nullable Eval<C> eval) {
		final boolean result = Explanation.PRINT_CANDIDATES_TO_FILE
				|| Explanation.isPrintLevelAtLeast(PrintLevel.GRAPH);
		if (result) {
			final E best = lookupExplanation();
			best.maybePrintToFile(nullModel);
			if (Explanation.isPrintLevelAtLeast(PrintLevel.GRAPH)) {
				best.printGraph();
			}

		}
		return result;
	}

	/**
	 * @return Explanation for currentGuess
	 */
	protected abstract @NonNull E lookupExplanation();

	/**
	 * @return absolute desirability of the state where candidate is toggled
	 */
	abstract protected double eval();

	private double getThreshold(final boolean isAdd) {
		return isAdd ? threshold : -threshold;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "currentGuess.size=" + currentGuess.size() + ": " + currentGuess + " eval="
				+ Explanation.formatDouble(previousValue()));
	}

	protected @Immutable class Eval<T> implements Comparable<Eval<T>> {
		final double value;
		final @NonNull C candidate;

		Eval(final double _eval, final @NonNull C _object) {
			value = _eval;
			candidate = _object;
		}

		@Override
		public int compareTo(final Eval<T> arg0) {
			return UtilMath.sgn(arg0.value - value);
		}

		@Override
		public String toString() {
			return UtilString.toString(this, candidate + " ⇒ " + DOUBLE_FORMAT.format(value));
		}

		// public String toShortString(final double threshold1) {
		// return (value > threshold1 ? " ⇑" : " ↓") +
		//
		// " Rank = "
		// + (candidates.indexOf(candidate) + 1)
		//
		// + DOUBLE_FORMAT.format(value) + "\t" + candidate;
		// }

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + candidate.hashCode();
			long temp;
			temp = Double.doubleToLongBits(value);
			result = prime * result + (int) (temp ^ (temp >>> 32));
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
			@SuppressWarnings("unchecked")
			final Eval<?> other = (Eval<?>) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (!candidate.equals(other.candidate)) {
				return false;
			}
			if (Double.doubleToLongBits(value) != Double.doubleToLongBits(other.value)) {
				return false;
			}
			return true;
		}

		private GreedySubset<C, ?> getOuterType() {
			return GreedySubset.this;
		}
	}

}
