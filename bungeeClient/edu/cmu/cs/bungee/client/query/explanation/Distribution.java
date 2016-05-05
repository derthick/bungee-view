package edu.cmu.cs.bungee.client.query.explanation;

import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * A Distribution over facets' 2^nFacets states. counts[state] is the raw
 * counts; probDist[state] is the percentages. facets.first() corresponds to
 * index==0, the least significant bit of state.
 */
public class Distribution implements Serializable {

	protected static final long serialVersionUID = 1L;

	/**
	 * Maps from facets to immutable Distributions over facets
	 */
	private static transient SoftReference<Map<SortedSet<Perspective>, Distribution>> cacheDAG;

	/**
	 * Enables finding Distributions of supersets of facets, from which
	 * marginals can be computed without asking the Database.
	 */
	private static synchronized @NonNull Map<SortedSet<Perspective>, Distribution> getCacheDAG() {
		Map<SortedSet<Perspective>, Distribution> map = cacheDAG == null ? null : cacheDAG.get();
		if (map == null) {
			// System.out.println("new thumb table");
			map = new Hashtable<>();
			cacheDAG = new SoftReference<>(map);
		}
		return map;
	}

	public final int totalCount;
	final @Immutable @NonNull SortedSet<Perspective> facets;
	protected final int nFacets;
	protected final int nStates;
	/**
	 * Immutable if isCacheable
	 */
	protected final @NonNull double[] probDist;
	private final @NonNull int[] counts;

	/**
	 * Sum(observed) p*log(p) for normalization of KL divergence, so it only has
	 * to compute p*log(q)
	 *
	 * If Double.NaN, must be recomputed from probDist (which should happen for
	 * GraphicalModels only).
	 */
	private double pLogP = Double.NaN;

	/**
	 * If false, counts must be recomputed from probDist (which should happen
	 * for GraphicalModels only).
	 */
	protected boolean isCountsValid = false; // NO_UCD (use final)

	/**
	 * Is this an Immutable Distribution that is up to date with the current
	 * query? (Conditional Distributions won't be.)
	 */
	private final boolean isCacheable;

	/**
	 * This is for GraphicalModels
	 */
	protected Distribution(final @NonNull SortedSet<Perspective> _facets, final int _totalCount) {
		this(_facets, _totalCount, new int[nStates(_facets)], false);
		verifyTotalCount();
	}

	/**
	 * This is only for immutable Distributions
	 */
	@Immutable
	Distribution(final @NonNull SortedSet<Perspective> _facets, final @NonNull int[] _counts) {
		this(_facets, _counts, true);
		assert lookUpDist(_facets) != null;
	}

	Distribution(final @NonNull SortedSet<Perspective> _facets, final @NonNull int[] _counts,
			final boolean _isCacheable) {
		this(_facets, UtilArray.sum(_counts), _counts, _isCacheable);
		assert !isCacheable || !isCached(facets) : facets + " " + _facets;
		final double total = totalCount;
		for (int state = 0; state < nStates; state++) {
			probDist[state] = counts[state] / total;
		}
		assert checkDist(probDist);
		isCountsValid = true;

		// System.out.println("**new dist " + this);
		if (isCacheable) {
			verifyTotalCount();
			cacheSubsets();
		}
	}

	/**
	 * This is called by both of the above constructors.
	 */
	private Distribution(final @NonNull SortedSet<Perspective> _facets, final int _totalCount,
			final @NonNull int[] _counts, final boolean _isCacheable) {
		// assert _facets.equals(new TreeSet<>(_facets)) : _facets;
		isCacheable = _isCacheable;
		facets = Util.nonNull(Collections.unmodifiableSortedSet(new TreeSet<>(_facets)));
		nFacets = facets.size();
		nStates = nStates(nFacets);
		probDist = new double[nStates];
		counts = _counts;
		assert counts.length == nStates;
		assert verifyCountsAreNonNegative(counts);
		totalCount = _totalCount;
	}

	void verifyTotalCount() {
		assert totalCount == Query.query(facets).getTotalCount() : "totalCount=" + totalCount
				+ " query(_facets).getTotalCount()=" + Query.query(facets).getTotalCount();
	}

	boolean verifyCounts(final @NonNull int[] _counts, final @Immutable @NonNull ExplanationTask explanationTask) {
		final boolean result = Arrays.equals(counts, _counts);
		if (!result) {
			decacheDistributions();
			final Distribution trueDistribution = ensureDist(facets, explanationTask);
			System.out.println("Distribution.verifyCounts " + this + "\n " + UtilString.valueOfDeep(_counts)
					+ "\n true Distribution=" + trueDistribution);
		}
		return result;
	}

	/**
	 * @return 2^_nFacets
	 */
	private static int nStates(final @NonNull Collection<Perspective> facets) {
		return nStates(facets.size());
	}

	/**
	 * @return 2^_nFacets
	 */
	private static int nStates(final int _nFacets) {
		return 1 << _nFacets;
	}

	/**
	 * @return defensive copy
	 */
	public @NonNull double[] getProbDist() {
		assert checkDist(probDist);
		// defensive copy
		return Util.nonNull(Arrays.copyOf(probDist, probDist.length));
	}

	protected boolean checkDist() {
		return checkDist(probDist);
	}

	/**
	 * @param _probDist
	 * @return ensure 0.0 <= p(state) <= 1.0 and that they sum to 1.0
	 */
	private static boolean checkDist(final @NonNull double[] _probDist) {
		final double slop = 1e-10;
		for (final double p : _probDist) {
			// all states should have 0 <= p <= 1
			assert p >= 0.0 && (p - 1.0) < slop : p + " " + UtilString.valueOfDeep(_probDist);
		}
		final double sum = UtilArray.kahanSum(_probDist);
		assert Math.abs(sum - 1.0) < slop : sum + " " + UtilString.valueOfDeep(_probDist);

		// int nFacets = (int) Util.log2(dist.length);
		// int[] indexes = new int[1];
		// for (int i = 0; i < nFacets; i++) {
		// indexes[0] = i;
		// double p = getMarginal(indexes, dist)[1];
		// assert p > 0 : i + " " + UtilString.valueOfDeep(dist);
		// }

		return true;
	}

	private static boolean verifyCountsAreNonNegative(final @NonNull int[] _counts) {
		for (final int count : _counts) {
			assert count >= 0 : count + " " + UtilString.valueOfDeep(_counts);
		}
		return true;
	}

	/**
	 * As a side effect, ensures base Distribution over facets.
	 *
	 * CALLER MUST CLOSE result IF NOT NULL (i.e. IF !candidates.isEmpty()).
	 *
	 * @return ResultSet for computing Distributions including each candidate.
	 */
	private static @Nullable ResultSet candidatesRS(final @NonNull SortedSet<Perspective> facets,
			final @NonNull Collection<Perspective> candidates,
			final @Immutable @NonNull ExplanationTask explanationTask) {
		assert candidates.size() <= Explanation.MAX_CANDIDATES;
		ResultSet result = null;
		final boolean needBaseCounts = lookUpDist(facets) == null;
		if (explanationTask.isTaskCurrent() && (needBaseCounts || !candidates.isEmpty())) {
			assert UtilMath.assertInRange(facets.size(), 1,
					FacetSelection.MAX_FACET_SELECTION_FACETS - (candidates.isEmpty() ? 0 : 1));
			final MyResultSet[] rss = Query.query(facets).onCountMatrix(facets, candidates, needBaseCounts);
			assert rss.length == 2 : "candidates=" + candidates + " facets=" + facets + " baseDistribution="
					+ lookUpDist(facets) + " rss.length=" + rss.length;

			if (needBaseCounts) {
				try (final MyResultSet rs = rss[0];) {
					assert rs != null;
					Util.ignore(new Distribution(facets, getCountsFromRS(facets, rs)));
				}
			}
			result = rss[1];
		}
		return result;
	}

	// // Only called by candidatesRS
	// private static boolean isThreadAlive() {
	// // QueueThreads set priority to Thread.MIN_PRIORITY on exit().
	// return Thread.currentThread().getPriority() > Thread.MIN_PRIORITY;
	// }

	/**
	 * Only called by candidatesRS
	 *
	 * @param rs
	 *            [state, count] where state > 0 and count > 0
	 *
	 * @return counts[state]
	 */
	private static @NonNull int[] getCountsFromRS(final @NonNull SortedSet<Perspective> _facets,
			final @NonNull ResultSet rs) {
		final int[] counts1 = new int[nStates(_facets)];
		try {
			while (rs.next()) {
				final int state = rs.getInt(1);
				assert state > 0 : "state=" + state + ". Too many facets (" + _facets + ")?";
				final int count = rs.getInt(2);
				counts1[state] = count;
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		counts1[0] = Query.query(_facets).getTotalCount() - UtilArray.sum(counts1);

		// System.out.println("Distribution.getCountsFromRS " + _facets + " ⇒ "
		// + UtilString.valueOfDeep(counts1) + "\n"
		// + MyResultSet.valueOfDeep(rs, 10));
		return counts1;
	}

	/**
	 * Only called by Distribution(facets, counts)
	 *
	 * Cache this isCacheable Distribution and all Distributions over subsets of
	 * its facets.
	 */
	private void cacheSubsets() {
		assert nStates == counts.length && nStates == nStates(facets) : nStates + " " + counts.length + " " + this;
		getCacheDAG().put(new TreeSet<>(facets), this);
		final SortedSet<Perspective> facetsSubset = new TreeSet<>(facets);
		if (nFacets > 1) {
			for (int i = 0; i < nFacets; i++) {
				// Can't iterate over facetsSubset directly or you get a
				// concurrent modification error
				final Perspective p = UtilArray.get(facetsSubset, i);
				facetsSubset.remove(p);
				if (!isCached(facetsSubset)) {
					getMarginalDistribution(facetsSubset).cacheSubsets();
				}
				facetsSubset.add(p);
			}
		}
	}

	/**
	 * The cache should be a member of Query, but for now we just keep one
	 * global cache.
	 *
	 * Only called from Query
	 */
	public static void decacheDistributions() {
		getCacheDAG().clear();
	}

	/**
	 * Only called from other classes.
	 *
	 * For each likelyCandidate, make sure there is a Distribution for facets
	 * plus that likelyCandidate. In case candidates is empty, make sure there
	 * is a Distribution for facets alone.
	 *
	 * @return Distribution over facets
	 */
	@Immutable
	static @NonNull Distribution cacheCandidateDistributions(final @NonNull SortedSet<Perspective> _facets,
			final @NonNull Collection<Perspective> likelyCandidates,
			final @Immutable @NonNull ExplanationTask explanationTask) {
		// UtilString.indentMore("cacheCandidateDistributions " + _facets
		// + " " + likelyCandidates);
		final Collection<Perspective> candidates = pruneCandidates(_facets, likelyCandidates);
		Distribution baseDistribution = null;
		if (candidates.isEmpty()) {
			baseDistribution = ensureDist(_facets, explanationTask);
		} else {
			try (final ResultSet candidatesRS = candidatesRS(_facets, candidates, explanationTask);) {
				baseDistribution = lookUpDist(_facets);
				assert baseDistribution != null;
				if (candidatesRS != null) {
					cacheCandidateDistributionsInternal(baseDistribution, candidatesRS, explanationTask);
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
		assert baseDistribution != null;
		return baseDistribution;
	}

	/**
	 * Only called by cacheCandidateDistributions
	 *
	 * For each candidate in rs, compute the Distribution for
	 * baseDistribution+candidate using the substate info in rs.
	 *
	 * @param rs
	 *            [facet_id, state, count]
	 */
	private static void cacheCandidateDistributionsInternal(final @NonNull Distribution baseDistribution,
			final @NonNull ResultSet rs, final @Immutable @NonNull ExplanationTask explanationTask)
			throws SQLException {
		final SortedSet<Perspective> facets = baseDistribution.facets;
		final Query query = Query.query(facets);
		// assert baseDistribution.totalCount == query.getTotalCount();
		final int[] baseCounts = baseDistribution.updateCountsFromProbDist();
		int[] facetsIndexes = null;
		int zeroStateCount = 0;
		Perspective candidate = null;
		SortedSet<Perspective> allFacets = null;
		int candidateIndex = -1;
		@NonNull
		int[] counts = new int[0];
		while (rs.next()) {
			final Perspective nextCandidate = query.findPerspectiveOrError(rs.getInt(1));
			if (nextCandidate != candidate) {
				if (allFacets != null) {
					computeNcache(baseDistribution, zeroStateCount, counts, allFacets, candidateIndex, explanationTask);
				}
				candidate = nextCandidate;
				zeroStateCount = candidate.getTotalCount();
				allFacets = allFacetsForCandidate(facets, candidate);
				facetsIndexes = getMarginIndexes(allFacets, facets);
				candidateIndex = UtilArray.indexOf(allFacets, candidate);
				counts = countsForCandidate(baseCounts, facetsIndexes);
			}
			assert candidate != null; // appease compiler
			assert facetsIndexes != null; // appease compiler
			final int count = rs.getInt(3);
			zeroStateCount -= count;
			final int substate = rs.getInt(2);
			final int state = stateFromSubstate(substate, Util.nonNull(facetsIndexes));
			counts[state] = baseCounts[substate] - count;
			counts[UtilMath.setBit(state, candidateIndex, true)] = count;
			assert counts[state] >= 0 && count >= 0 && zeroStateCount >= 0 : "\n facets=" + Perspective.path(facets)
					+ "\n baseCounts=" + UtilString.valueOfDeep(baseCounts) + "\n substate=" + substate
					+ "\n candidate=" + candidate.path() + "\n candidateIndex=" + candidateIndex + "\n state=" + state
					+ "\n count=" + count + "\n nonZeroStateCount=" + zeroStateCount + "\n facetsIndexes="
					+ UtilString.valueOfDeep(facetsIndexes) + "\n allFacets=" + allFacets + "\n counts="
					+ UtilString.valueOfDeep(counts) + "\n" + MyResultSet.valueOfDeep(rs, 300);
		}
		computeNcache(baseDistribution, zeroStateCount, counts, Util.nonNull(allFacets), candidateIndex,
				explanationTask);

		// System.out.println(facets + " " + allFacets + " "
		// + UtilString.valueOfDeep(baseCounts) + " "
		// + UtilString.valueOfDeep(counts));
		// createAndCache(allFacets, counts);
	}

	/**
	 * @param facets
	 * @param candidate
	 * @return SortedSet containing facets + candidate
	 */
	private static @NonNull SortedSet<Perspective> allFacetsForCandidate(final @NonNull SortedSet<Perspective> facets,
			final @NonNull Perspective candidate) {
		assert !facets.contains(candidate) : facets + " " + candidate;
		final SortedSet<Perspective> allFacets = new TreeSet<>(facets);
		allFacets.add(candidate);
		// assert lookUpDist(allFacets) == null;
		return allFacets;
	}

	/**
	 * Only called by cacheCandidateDistributions
	 *
	 * @param facetsIndexes
	 *            indexes for base facets
	 * @return counts under the assumption that candidate is always off
	 */
	private static @NonNull int[] countsForCandidate(final @NonNull int[] baseCounts,
			final @NonNull int[] facetsIndexes) {
		final int[] counts = new int[baseCounts.length * 2];
		for (int substate = 0; substate < baseCounts.length; substate++) {
			final int state = stateFromSubstate(substate, facetsIndexes);
			counts[state] = baseCounts[substate];
		}
		// System.out.println("Distribution.countsForCandidate return "
		// + UtilString.valueOfDeep(counts));
		return counts;
	}

	/**
	 * @param state
	 * @param indexes
	 *            indexes[index] is the bit in state corresponding to
	 *            marginals[index].
	 *
	 *            bits of state to extract
	 * @return the substate (from 0 to 1 << indexes.length)
	 */
	private static int getSubstate(final int state, final @NonNull int[] indexes) {
		int result = 0;
		for (int index = 0; index < indexes.length; index++) {
			result = UtilMath.setBit(result, index, UtilMath.isBit(state, indexes[index]));
		}
		return result;
	}

	private static int stateFromSubstate(final int substate, final @NonNull int[] indexes) {
		final int result = setSubstate(0, substate, indexes);
		// System.out.println("Distribution.stateFromSubstate " + substate
		// + " ⇒ " + result);
		return result;
	}

	/**
	 * Only called by cacheCandidateDistributions.
	 *
	 * @param state
	 * @param substate
	 * @param indexes
	 *            indexes[index] is the bit in state corresponding to
	 *            marginals[index].
	 *
	 *            bits of state to set to substate value.
	 * @return the modified state
	 */
	private static int setSubstate(int state, final int substate, final @NonNull int[] indexes) {
		for (int index = 0; index < indexes.length; index++) {
			state = UtilMath.setBit(state, indexes[index], UtilMath.isBit(substate, index));
		}
		// System.out.println("setState " + substate + " " +
		// UtilString.valueOfDeep(indexes)
		// + " " + state);
		return state;
	}

	/**
	 * Only called by cacheCandidateDistributions
	 *
	 * @return likelyCandidates where there is no cached Distribution for
	 *         facets+likelyCandidate
	 */
	private static @NonNull Collection<Perspective> pruneCandidates(final @NonNull Collection<Perspective> facets,
			final @NonNull Collection<Perspective> likelyCandidates) {
		assert UtilMath.assertInRange(facets.size(), 1, FacetSelection.MAX_FACET_SELECTION_FACETS);
		if (facets.size() == FacetSelection.MAX_FACET_SELECTION_FACETS) {
			return UtilArray.EMPTY_SET;
		} else {
			final Collection<Perspective> uncachedCandidates = new HashSet<>(likelyCandidates);
			uncachedCandidates.removeAll(facets);
			final SortedSet<Perspective> scratchpadForFacets = new TreeSet<>(facets);
			for (final Iterator<Perspective> it = uncachedCandidates.iterator(); it.hasNext();) {
				final Perspective candidate = it.next();
				scratchpadForFacets.add(candidate);
				if (lookUpDist(scratchpadForFacets) != null) {
					it.remove();
				}
				scratchpadForFacets.remove(candidate);
			}
			return uncachedCandidates;
		}
	}

	/**
	 * Only called by cacheCandidateDistributions
	 *
	 * modifies counts
	 *
	 * Create Distribution for baseDistribution+candidate.
	 */
	private static void computeNcache(final @NonNull Distribution baseDistribution, final int zeroStateCount,
			final @NonNull int[] counts, final @NonNull SortedSet<Perspective> allFacets, final int candidateIndex,
			final @Immutable @NonNull ExplanationTask explanationTask) {
		assert verifyCountsAreNonNegative(counts);
		// System.out
		// .println("Distribution.computeNcache
		// baseDistribution.isCountsValid="
		// + baseDistribution.isCountsValid
		// + " baseDistribution.counts="
		// + UtilString.valueOfDeep(baseDistribution.counts)
		// + " counts=" + UtilString.valueOfDeep(counts));
		counts[0] = baseDistribution.updateCountsFromProbDist()[0] - zeroStateCount;
		counts[1 << candidateIndex] = zeroStateCount;
		assert verifyCountsAreNonNegative(counts) : " baseDistribution.counts="
				+ UtilString.valueOfDeep(baseDistribution.counts) + " counts=" + UtilString.valueOfDeep(counts);
		// counts = getCountsForComputeNcache(baseDistribution,
		// zeroStateCount, counts, candidate, candidateIndex);
		final Distribution cachedDist = lookUpDist(allFacets);
		if (cachedDist == null) {
			final Distribution dist = new Distribution(allFacets, counts);
			assert dist.totalCount == baseDistribution.totalCount : dist;
		} else {
			assert cachedDist.verifyCounts(counts, explanationTask);
		}
	}

	// /**
	// * modifies counts
	// */
	// private static int[] getCountsForComputeNcache(
	// final Distribution baseDistribution, final int nonZeroStateCount,
	// final int[] counts, final Perspective candidate,
	// final int candidateIndex) {
	// // System.out
	// // .println("Distribution.getCountsForComputeNcache nonZeroStateCount="
	// // + nonZeroStateCount
	// // + " candidate="
	// // + candidate
	// // + " candidateIndex="
	// // + candidateIndex
	// // + "\n"
	// // + baseDistribution
	// // + "\n"
	// // + UtilString.valueOfDeep(counts));
	// final int[] baseCounts = baseDistribution.counts;
	// final int pTotalCount = candidate.getTotalCountNow();
	// assert pTotalCount >= 0;
	// final int zeroCount = pTotalCount - nonZeroStateCount;
	// assert pTotalCount >= nonZeroStateCount
	// && pTotalCount <= nonZeroStateCount + baseCounts[0] : candidate
	// .path(true, true)
	// + " "
	// + pTotalCount
	// + "-"
	// + nonZeroStateCount
	// + "="
	// + zeroCount
	// + "\ncounts="
	// + UtilString.valueOfDeep(counts)
	// + "\nfCounts="
	// + UtilString.valueOfDeep(baseCounts);
	// // assert zeroCount >= 0&&zeroCount<=baseCounts[0];
	//
	// // System.out.println(" pTotalCount=" + pTotalCount + " zeroCount="
	// // + zeroCount + " bit=" + (1 << candidateIndex));
	//
	// counts[0] = baseCounts[0] - zeroCount;
	//
	// // assert counts[0] >= 0 : p.fullName() + " " + pTotalCount +
	// // "-"
	// // + nonZeroStateCount + "=" + zeroCount + "\ncounts="
	// // + UtilString.valueOfDeep(counts) + "\nfCounts="
	// // + UtilString.valueOfDeep(baseCounts);
	//
	// counts[1 << candidateIndex] = zeroCount;
	//
	// // System.out.println("cd " + baseCounts[0] + " "
	// // + nonZeroStateCount + " "
	// // + p.getTotalCount() + " " + p);
	// // System.out.println(facets + " " + allFacets + " "
	// // + UtilString.valueOfDeep(baseCounts) + " "
	// // + UtilString.valueOfDeep(counts));
	// return counts;
	// }

	@Immutable
	static @NonNull Distribution ensureDist(final @NonNull SortedSet<Perspective> _facets,
			final @Immutable @NonNull ExplanationTask explanationTask) {
		candidatesRS(_facets, UtilArray.EMPTY_LIST, explanationTask);
		return Util.nonNull(lookUpDist(_facets));
	}

	// private static boolean isCached(final SortedSet<Perspective> _facets) {
	// final Distribution largerDist = getCacheDAG().get(_facets);
	// return largerDist != null && largerDist.nFacets == _facets.size();
	// }
	//
	// /**
	// * @return Distribution over _facets, if it or one for a superset of
	// _facets
	// * is cached; else null
	// */
	// static @Nullable Distribution lookUpDist(final SortedSet<Perspective>
	// _facets) {
	// Distribution result = null;
	// final Distribution largerDist = getCacheDAG().get(_facets);
	// if (largerDist != null) {
	// result = largerDist.getMarginalDistribution(_facets);
	// assert result.equalsFacets(_facets) : _facets + "\n" + result
	// + "\n" + largerDist;
	// }
	// return result;
	// }

	private static boolean isCached(final @NonNull SortedSet<Perspective> _facets) {
		return lookUpDist(_facets) != null;
	}

	/**
	 * @return Distribution over _facets, if one for _facets or a superset is
	 *         cached (and therefore Immutable); else null.
	 */
	@Immutable
	static @Nullable Distribution lookUpDist(final @NonNull SortedSet<Perspective> _facets) {
		final Distribution result = getCacheDAG().get(_facets);
		assert result == null || (result.facets.equals(_facets) && result.isCacheable);
		return result;
	}

	@NonNull
	Distribution getMarginalDistribution(final @NonNull SortedSet<Perspective> subFacets) {
		assert facets.containsAll(subFacets) : this + " " + subFacets;
		verifyTotalCount();
		if (equalsFacets(subFacets)) {
			return this;
		} else {
			return new Distribution(subFacets, getMarginalCounts(subFacets));
		}
	}

	public @NonNull int[] getMarginalCounts(final @NonNull SortedSet<Perspective> subFacets) {
		assert facets.containsAll(subFacets) : this + " " + subFacets;
		final int[] counts1 = updateCountsFromProbDist();
		if (equalsFacets(subFacets)) {
			return counts1;
		}
		final int[] indexes = getMarginIndexes(subFacets);
		final int[] result = new int[nStates(indexes.length)];
		for (int state = 0; state < nStates; state++) {
			result[getSubstate(state, indexes)] += counts1[state];
		}
		// System.out.println("Distribution.getMarginalCounts\n " + facets +
		// ": "
		// + UtilString.valueOfDeep(counts1) + "\n " + subFacets + ": "
		// + UtilString.valueOfDeep(result) + "\n "
		// + UtilString.valueOfDeep(indexes));
		assert UtilArray.sum(result) == totalCount : totalCount + " " + UtilString.valueOfDeep(result);
		return result;
	}

	/**
	 * Make sure _facets are in the same order as facets, as well as having the
	 * same elements.
	 */
	boolean equalsFacets(final @NonNull SortedSet<Perspective> _facets) {
		return facets.equals(_facets) && new ArrayList<>(facets).equals(new ArrayList<>(_facets));
	}

	// // Only called from Explanation
	// boolean approxEquals(final @NonNull Distribution distribution2) {
	// if (this == distribution2) {
	// return true;
	// }
	// // if (distribution2 == null) {
	// // return false;
	// // }
	// if (getClass() != distribution2.getClass()) {
	// return false;
	// }
	// if (!facets.equals(distribution2.facets)) {
	// return false;
	// }
	// if (totalCount != distribution2.totalCount) {
	// return false;
	// }
	// for (int i = 0; i < getProbDist().length; i++) {
	// final double a = getProbDist()[i];
	// final double b = distribution2.getProbDist()[i];
	// if (Math.abs(a - b) > APPROX_EQUALS_TOLERANCE) {
	// return false;
	// }
	// }
	// return true;
	// }

	protected @NonNull Perspective getFacet(final int i) {
		return UtilArray.get(facets, i);
	}

	// Only called by other classes
	protected int facetIndex(final @NonNull Perspective facet) {
		final int result = UtilArray.indexOf(facets, facet);
		assert result >= 0 : facet + " " + facets;
		return result;
	}

	// Only called by other classes
	protected @NonNull double[] getMarginalDist(final @NonNull SortedSet<Perspective> marginals) {
		// System.out.println("Distribution.getMarginal " + marginals + " " +
		// facets() + " "
		// + UtilString.valueOfDeep(probDist));
		assert facets.containsAll(marginals) : this + " should contain all " + marginals;
		return getMarginalDist(getMarginIndexes(marginals));
	}

	private @NonNull double[] getMarginalDist(final @NonNull int[] indexes) {
		// System.out.println("Distribution.getMarginal "
		// + UtilString.valueOfDeep(indexes) + " " + facets);

		final int nMargFacets = indexes.length;
		if (nMargFacets == nFacets) {
			assert isSequence(indexes);
			return probDist;
		}
		assert nMargFacets < nFacets : "Bad indexes: " + facets + " " + UtilString.valueOfDeep(indexes);
		final double[] result = new double[nStates(nMargFacets)];
		for (int state = 0; state < nStates; state++) {
			result[getSubstate(state, indexes)] += probDist[state];
		}
		assert checkDist(result);
		return result;
	}

	// Only called by getMarginal
	/**
	 * @param array
	 * @return whether array is like [0, 1, 2, ..., array.length-1]
	 */
	private static boolean isSequence(final @NonNull int[] array) {
		for (int i = 0; i < array.length; i++) {
			if (array[i] != i) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Only called by EulerBlock
	 *
	 * @return Distribution over remaining facets, given the true and false
	 *         facets, or null if totalCount would be 0.
	 */
	public @Nullable Distribution getConditionalDistribution(final @NonNull SortedSet<Perspective> falseFacets,
			final @NonNull SortedSet<Perspective> trueFacets) {
		verifyTotalCount();
		final SortedSet<Perspective> givens = new TreeSet<>(falseFacets);
		givens.addAll(trueFacets);
		assert givens.size() == falseFacets.size() + trueFacets.size() : falseFacets + " " + trueFacets;
		assert facets.containsAll(givens) : this + " " + falseFacets + " " + trueFacets;
		final SortedSet<Perspective> condFacets = new TreeSet<>(facets);
		condFacets.removeAll(givens);
		assert condFacets.size() > 0;

		if (isCacheable && givens.isEmpty()) {
			return lookUpDist(condFacets);
		}
		final int[] condCounts = new int[nStates(condFacets)];
		final int givensMask = getMask(givens, true);
		final int trueMask = getMask(trueFacets, true);
		final int[] condIndexes = getMarginIndexes(condFacets);
		int total = 0;
		final int[] validatedCounts = updateCountsFromProbDist();
		for (int state = 0; state < validatedCounts.length; state++) {
			if ((state & givensMask) == trueMask) {
				final int count = validatedCounts[state];
				condCounts[getSubstate(state, condIndexes)] += count;
				total += count;
			}
		}
		return total > 0 ? new Distribution(condFacets, condCounts, false) : null;
	}

	private int getMask(final @NonNull SortedSet<Perspective> _facets, final boolean polarity) {
		int mask = 0;
		final int[] indexes = getMarginIndexes(_facets);
		for (final int index : indexes) {
			mask = UtilMath.setBit(mask, index, polarity);
		}
		return mask;
	}

	private @NonNull int[] getMarginIndexes(final @NonNull SortedSet<Perspective> subFacets) {
		return getMarginIndexes(facets, subFacets);
	}

	/**
	 * @param allFacets
	 * @param marginals
	 * @return Mapping from each marginal to its index in allFacets. 0 is for
	 *         the least significant bit; e.g. if result[2]==4, the third
	 *         marginal corresponds to the 4's bit of the substate and the 16's
	 *         bit of the full (over allFacets) state. result[n] >= n.
	 */
	private static @NonNull int[] getMarginIndexes(final @NonNull SortedSet<Perspective> allFacets,
			final @NonNull SortedSet<Perspective> marginals) {
		assert allFacets.containsAll(marginals) : marginals + " " + allFacets;
		final int[] result = new int[marginals.size()];
		int index = 0;
		for (final Perspective marginal : marginals) {
			result[index] = UtilArray.indexOf(allFacets, marginal);

			// This is only true if marginals is sorted the same way as
			// allFacets. EulerDiagrams sort marginals by totalCount rather than
			// facetID.
			// assert result[index] >= index : " allFacets=" + allFacets
			// + "\n marginals=" + marginals + "\n marginal=" + marginal
			// + "\n result=" + UtilString.valueOfDeep(result)
			// + "\n index=" + index;

			index++;
		}
		return result;
	}

	double klDivergence(final @NonNull double[] predicted) {
		final double[] obs = getProbDist();
		final double[] addends = new double[nStates + 1];
		for (int state = 0; state < nStates; state++) {
			// assert predicted[i] > 0 : UtilString.valueOfDeep(predicted);
			final double p = obs[state];
			if (p > 0) {
				addends[state] = -p * Math.log(predicted[state]);
				// assert !(Double.isNaN(sum) || Double.isInfinite(sum)) : sum
				// + " " + p + " " + predicted[state] + "\n"
				// + UtilString.valueOfDeep(predicted) + "\n"
				// + UtilString.valueOfDeep(obs);
			}
		}
		addends[nStates] = pLogP();
		final double sum = UtilArray.kahanSum(addends);
		return verifyKL(sum, predicted);
	}

	// Only called from Explanation
	double klDivergenceFromLog(final @NonNull double[] logPredicted) {
		final double[] obs = getProbDist();
		final double[] addends = new double[nStates + 1];
		for (int state = 0; state < nStates; state++) {
			// assert predicted[i] > 0 : UtilString.valueOfDeep(predicted);
			addends[state] = obs[state] * logPredicted[state];
		}
		addends[nStates] = -pLogP();// Double.NEGATIVE_INFINITY;
		// Arrays.sort(addends);
		// addends[0]=-pLogP();
		// System.out.println(UtilString.valueOfDeep(addends));
		final double sum = -UtilArray.kahanSum(addends);
		// if (NonAlchemyModel.printInterval[0] > 0) {
		// Util.testSum(addends, sum);
		// }
		return verifyKL(sum, logPredicted);
	}

	private double verifyKL(final double sum, final @NonNull double[] logPredicted) {
		if (Double.isNaN(sum) || Double.isInfinite(sum) || sum < -1e15) {
			System.err.println(" Warning: Distribution.verifyKL: Bad KL divergence (NaN, Infinite, or negative): " + sum
					+ "\n" + UtilString.valueOfDeep(exp(logPredicted)) + "\n" + UtilString.valueOfDeep(probDist));
		}
		return Math.max(0.0, sum);
	}

	private static @NonNull double[] exp(final @NonNull double[] a) {
		final double[] result = new double[a.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = Math.exp(a[i]);
		}
		return result;
	}

	/**
	 * cache P*log(P) because that is independent of the weights
	 */
	private double pLogP() {
		if (Double.isNaN(pLogP)) {
			// must set pLogP to non-NaN or klDivergence goes into infinite
			// loop.
			pLogP = 0.0;
			assert checkDist(probDist);
			pLogP = -klDivergence(probDist);
		}
		// assert pLogP >= 0 : pLogP + " " + this;
		return pLogP;
	}

	private double[][] odds;

	/**
	 * @param causeNode
	 * @param causedNode
	 * @return the odds of both nodes being on
	 */
	double odds(final int causeNode, final int causedNode) {
		if (odds == null) {
			odds = new double[nFacets][];
			for (int i = 0; i < odds.length; i++) {
				odds[i] = new double[nFacets];
				Arrays.fill(odds[i], -1.0);
			}
		}
		if (odds[causeNode][causedNode] < 0.0) {
			final double[] dist = getProbDist();
			double pOn = 0;
			for (int state = 0; state < dist.length; state++) {
				if (UtilMath.isBit(state, causeNode) && UtilMath.isBit(state, causedNode)) {
					pOn += dist[state];
				}
			}
			odds[causeNode][causedNode] = pOn == 1.0 ? Double.POSITIVE_INFINITY : pOn / (1.0 - pOn);
			odds[causedNode][causeNode] = odds[causeNode][causedNode];
		}
		return odds[causeNode][causedNode];
	}

	protected void decacheOdds() {
		if (odds != null) {
			for (final double[] odd : odds) {
				Arrays.fill(odd, -1.0);
			}
		}
	}

	// TODO Remove unused code found by UCDetector
	// double correlationHacked(final @NonNull Explanation nullModel) {
	// final SortedSet<Perspective> primary = nullModel.facets();
	// final List<Perspective> nonPrimary = new LinkedList<>(facets);
	// nonPrimary.removeAll(primary);
	// if (nonPrimary.size() != 1) {
	// return Double.NaN;
	// }
	// final Perspective candidate = nonPrimary.get(0);
	// assert candidate != null;
	//
	// double sum = 0;
	// double sumSq = 0;
	// for (final Perspective p : primary) {
	// assert p != null;
	// final double corr = Math.abs(getChiSq(p, candidate).correlation());
	// sum += corr;
	// sumSq += corr * corr;
	// }
	// final double result = sum * sum - sumSq;
	// return result;
	// }

	// Only called by correlationHacked
	// private @NonNull ChiSq2x2 getChiSq(final @NonNull Perspective marginal1,
	// final @NonNull Perspective marginal2) {
	// final SortedSet<Perspective> marginals = new TreeSet<>();
	// marginals.add(marginal1);
	// marginals.add(marginal2);
	// final int[] marginal = getMarginalCounts(marginals);
	// final ChiSq2x2 result = ChiSq2x2.getInstance(totalCount, marginal[0] +
	// marginal[1], marginal[0] + marginal[2],
	// marginal[0], this);
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// double entropy() {
	// double result = 0;
	// for (int state = 0; state < nStates; state++) {
	// result -= ChiSq2x2.nLogn(probDist[state]);
	// }
	// return result;
	// }

	// /**
	// * Can't use Perspective.stdDev, because deeply nested facets have
	// * totalCount = -1
	// *
	// * @return standard deviation of binary variable p, over the whole
	// database
	// */
	// protected static double stdDev(final Perspective p) {
	// final SortedSet<Perspective> primaryFacets = new
	// TreeSet<>(Collections.singleton(p));
	// // primaryFacets.add(p);
	// final Distribution dist = Distribution.ensureDist(primaryFacets, null);
	// final double n = dist.totalCount;
	// final double onCount = dist.getDistribution()[1];
	// final double stdDev = Math.sqrt(onCount * (n - onCount) / (n * (n - 1)));
	// assert stdDev >= 0 : onCount + " " + n + " " + p;
	// return stdDev;
	// }
	//
	// // Only called by stdDev
	// static private Distribution ensureDist(
	// final SortedSet<Perspective> _facets, final int[] counts) {
	//
	// Distribution result = lookUpDist(_facets);
	// if (result == null) {
	// result = new Distribution(_facets, counts);
	// }
	// return result;
	// }

	@NonNull
	Distribution independenceDistribution() {
		final double[] marginals = new double[nFacets];
		final int[] index = new int[1];
		for (int i = 0; i < marginals.length; i++) {
			index[0] = i;
			marginals[i] = getMarginalDist(index)[0];
		}
		final int[] independenceCounts = new int[nStates];
		for (int state = 0; state < independenceCounts.length; state++) {
			double p = 1.0;
			for (int i = 0; i < marginals.length; i++) {
				p *= UtilMath.isBit(state, i) ? marginals[i] : 1.0 - marginals[i];
			}
			independenceCounts[state] = UtilMath.roundToInt(p * totalCount);
		}
		return new Distribution(facets, independenceCounts, false);
	}

	@NonNull
	Collection<Perspective> getCauses(final @NonNull Perspective causedP) {
		final List<Perspective> result = new LinkedList<>(facets);
		result.remove(causedP);
		return result;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, Integer.toHexString(hashCode()) + " " + facets + " "
				+ UtilString.valueOfDeep(updateCountsFromProbDist(), Explanation.COUNT_FORMAT));
	}

	@NonNull
	int[] updateCountsFromProbDist() {
		if (!isCountsValid) {
			int sum = 0;
			int maxState = 0;
			int maxValue = 0;
			final double totalCountDouble = totalCount;
			for (int state = 0; state < counts.length; state++) {
				final int count = UtilMath.roundToInt(probDist[state] * totalCountDouble);
				assert count >= 0 : count + " " + UtilString.valueOfDeep(probDist);
				counts[state] = count;
				sum += count;
				if (count > maxValue) {
					maxValue = count;
					maxState = state;
				}
			}
			// correct for any rounding errors
			counts[maxState] += totalCount - sum;
			assert counts[maxState] >= 0 : totalCount + " " + sum + " " + UtilString.valueOfDeep(probDist);
		}
		verifyCountsAreNonNegative(counts);
		// defensive copy
		return Util.nonNull(Arrays.copyOf(counts, counts.length));
	}

	public @NonNull int[] getCounts() {
		assert isCountsValid : this;
		// defensive copy
		return Util.nonNull(Arrays.copyOf(counts, counts.length));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(counts);
		result = prime * result + facets.hashCode();
		result = prime * result + (isCacheable ? 1231 : 1237);
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
		final Distribution other = (Distribution) obj;
		if (!Arrays.equals(counts, other.counts)) {
			return false;
		}
		if (!facets.equals(other.facets)) {
			return false;
		}
		if (isCacheable != other.isCacheable) {
			return false;
		}
		return true;
	}

	// protected @NonNull SortedSet<Perspective> facets() {
	// return facets;
	// }

	// protected int nFacets() {
	// return nFacets;
	// }

}
