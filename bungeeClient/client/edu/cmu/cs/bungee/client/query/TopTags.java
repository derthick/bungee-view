package edu.cmu.cs.bungee.client.query;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;

import JSci.maths.statistics.ChiSq2x2;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.comparator.DoubleValueComparator;

/**
 * Structure used within TopTagsViz.queryValidRedraw
 */
public class TopTags {
	public final SortedSet<TopTags.TagRelevance> top = new TreeSet<>(ENTRY_COMPARATOR);
	public final SortedSet<TopTags.TagRelevance> bottom = new TreeSet<>(ENTRY_COMPARATOR);
	// /**
	// * // * Whether to include Perspectives that are explicitly included in
	// * Query. // * False for TopTagsViz; true for finding Explanations. //
	// */
	// final boolean allowRestrictions;

	/**
	 * Both top and bottom can have up to nLines.
	 */
	private final int nLines;
	/**
	 * The score of the worst TagRelevance in top. Replace with anything with a
	 * higher score.
	 */
	private double topThreshold = Double.POSITIVE_INFINITY;
	/**
	 * The score of the best TagRelevance in bottom. Replace with anything with
	 * a lower score.
	 */
	private double bottomThreshold = Double.NEGATIVE_INFINITY;

	private static final Comparator<TopTags.TagRelevance> ENTRY_COMPARATOR = new EntryComparator();

	public TopTags(final int _nLines) {
		// assert _nLines > 0;
		nLines = _nLines;
		// allowRestrictions = _allowRestrictions;
	}

	public void clear() {
		top.clear();
		bottom.clear();
	}

	public int getnLines() {
		return nLines;
	}

	/**
	 * If it is a new high/low, add to top/bottom. args are {table, score}
	 * rather than {TagRelevance} to slightly optimize the case where nothing is
	 * added.
	 *
	 * @param table
	 *            the object having the score
	 * @param score
	 *            any monotonically increasing relevance function, where
	 *            positive influences are greater than zero, and vice versa.
	 * @param facet
	 */
	void maybeAdd(final ChiSq2x2 table, final double score, final @NonNull Perspective facet) {
		final SortedSet<TagRelevance> topOrBottom = score > 0 ? top : bottom;
		if (topOrBottom.size() < nLines || (topOrBottom == top ? score > topThreshold : score < bottomThreshold)) {
			if (topOrBottom.size() == nLines) {
				topOrBottom.remove(topOrBottom == top ? top.last() : bottom.first());
				assert topOrBottom.size() == nLines - 1 : "facet=" + facet + " score=" + score + "\n" + topOrBottom;
			}
			topOrBottom.add(new TagRelevance(table, facet, score));
			if (topOrBottom == top) {
				topThreshold = top.last().relevance;
			} else {
				bottomThreshold = bottom.first().relevance;
			}
			assert topOrBottom.size() <= nLines : " score=" + score + "\n" + topOrBottom;
		}
	}

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder(ChiSq2x2.statisticsHeading());
		toStringInternal("\nTop ", top, buf);
		buf.append("\n");
		toStringInternal("Bottom ", bottom, buf);
		return UtilString.toString(this, buf);
	}

	private static void toStringInternal(final String which, final Set<TopTags.TagRelevance> whichSet,
			final StringBuilder buf) {
		// if (whichSet.size() > 0) {
		buf.append(which).append(whichSet.size()).append(" tags:\n");
		for (final TagRelevance tagRelevance : whichSet) {
			final ChiSq2x2 pvalue = tagRelevance.tag;
			pvalue.statisticsLine(buf).append("\n");
		}
		// }
	}

	public static class TagRelevance {

		public @NonNull Perspective getFacet() {
			return facet;
		}

		final ChiSq2x2 tag;
		final double relevance;
		final @NonNull Perspective facet;

		TagRelevance(final ChiSq2x2 table, final @NonNull Perspective _facet, final double _relevance) {
			assert Math.abs(_relevance) <= 100.0 : table + " " + _relevance;
			tag = table;
			facet = _facet;
			relevance = _relevance;
			// assert tag instanceof ChiSq2x2;
		}

		/**
		 * @return in [-100.0, 100.0]
		 */
		public double relevanceScore() {
			double result = 100.0 * Math.pow(Math.abs(relevance), 0.25);
			if (relevance < 0.0) {
				result = -result;
			}
			return result;
		}

		@Override
		public String toString() {
			return UtilString.toString(this, relevance + " " + facet);
		}
	}

	static final class EntryComparator extends DoubleValueComparator<TopTags.TagRelevance> implements Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public double value(final TopTags.TagRelevance data) {
			return data.relevance;
		}
	}

}