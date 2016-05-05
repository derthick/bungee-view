package edu.cmu.cs.bungee.client.viz.tagWall;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;

import java.io.Serializable;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.umd.cs.piccolo.PNode;
import jdk.nashorn.internal.ir.annotations.Immutable;

final @Immutable class RankComponentHeights implements Serializable {

	static final float MIN_ZERO_TO_ONE = 0.001f;

	/**
	 * Height of Letters.
	 */
	private final double foldH;

	/**
	 * Height of bars;
	 */
	private final double frontH;

	/**
	 * Height of bar labels.
	 */
	private final double labelsH;

	/**
	 * Margin between Ranks.
	 */
	private final double marginH;

	static final @NonNull RankComponentHeights ZERO_RANK_COMPONENT_HEIGHTS = new RankComponentHeights(0.0,
			MIN_ZERO_TO_ONE, 0.0, 0.0);

	@Immutable
	RankComponentHeights(final double _foldH, final double _frontH, final double _labelsH, final double _marginH) {
		assert _foldH >= 0.0;
		assert _frontH >= 0.0;
		assert _labelsH >= 0.0;
		assert _marginH >= 0.0;
		foldH = _foldH;
		frontH = _frontH;
		labelsH = _labelsH;
		marginH = _marginH;
	}

	/**
	 * Called only by TagWall.computeAndAnimateRankComponentHeights, when
	 * height, number of ranks, or queryW change.
	 *
	 * Sets rankComponentHeights (fold, front, and labels selected/deselected
	 * heights and margin).
	 *
	 * All ratios are to the combined deselected+margin height.
	 */
	static @NonNull @Immutable RankComponentHeights computeRankComponentHeights(
			// NO_UCD (unused code)
			final DesiredSize desiredFoldH, final DesiredSize desiredFrontH, final DesiredSize desiredLabelsH,
			final DesiredSize desiredMarginH, final int nRanks, final double internalH) {
		// NO_UCD (unused code)

		// System.out.println("setDeselectedHeights " + h + " " + ranks.size());
		final int nDeselectedRanks = Math.max(2, nRanks) - 1;
		final DesiredSize[] desiredSizes = { desiredFoldH, desiredFrontH, desiredLabelsH,
				desiredMarginH.scale(nDeselectedRanks) };

		double unallocated = internalH;
		double selectedToDeselectedRatio = 0.0;
		for (final DesiredSize desiredSize : desiredSizes) {
			selectedToDeselectedRatio += desiredSize.ratioToDeselectedH;
		}

		for (final DesiredSize desiredSize : desiredSizes) {
			final double componentH = desiredSize.ratioToDeselectedH
					* deselectedH(nDeselectedRanks, selectedToDeselectedRatio, unallocated);
			if (componentH < desiredSize.min) {
				// recompute with this as a constant
				unallocated -= desiredSize.min;
				selectedToDeselectedRatio -= desiredSize.ratioToDeselectedH;
			} else if (componentH > desiredSize.max) {
				// recompute with this as a constant
				unallocated -= desiredSize.max;
				selectedToDeselectedRatio -= desiredSize.ratioToDeselectedH;
			}
		}
		final double deselectedH = deselectedH(nDeselectedRanks, selectedToDeselectedRatio, unallocated);
		final double[] sizes = new double[desiredSizes.length];
		for (int i = 0; i < desiredSizes.length; i++) {
			final DesiredSize size = desiredSizes[i];
			sizes[i] = Math.rint(UtilMath.constrain(size.ratioToDeselectedH * deselectedH, size.min, size.max));
		}
		final RankComponentHeights result = new RankComponentHeights(sizes[0], sizes[1], sizes[2],
				Math.floor(sizes[3] / nDeselectedRanks));

		// System.out.println("computeRankComponentHeights internalH=" +
		// internalH
		// + " return " + result);
		return result;
	}

	// Convert the selected rank components into an equal
	// number of deselectedRankH's
	// and combine with them, so you can divide into internalH and
	// get the deselected rank height
	private static double deselectedH(final int nDeselectedRanks, final double selectedToDeselectedRatio,
			final double unallocated) {
		final double equivNranks = nDeselectedRanks + selectedToDeselectedRatio;
		return unallocated / equivNranks;
	}

	/**
	 * Height of Letters.
	 */
	double foldH() {
		return foldH;
	}

	/**
	 * Height of bars.
	 */
	double frontH() {
		return frontH;
	}

	/**
	 * @return height of RotatedFacetText labels area.
	 */
	double labelsH() {
		return labelsH;
	}

	/**
	 * @return foldH + frontH + labelsH.
	 */
	double totalH() {
		return foldH + frontH + labelsH;
	}

	/**
	 * Margin between Ranks.
	 */
	double marginH() {
		return marginH;
	}

	static @Immutable @NonNull RankComponentHeights lerp(final double zeroToOne, final RankComponentHeights start,
			final RankComponentHeights end) {
		return new RankComponentHeights(PNode.lerp(zeroToOne, start.foldH, end.foldH),
				PNode.lerp(zeroToOne, start.frontH, end.frontH), PNode.lerp(zeroToOne, start.labelsH, end.labelsH),
				PNode.lerp(zeroToOne, start.marginH, end.marginH));
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "foldH=" + foldH + "; frontH=" + frontH + "; labelsH=" + labelsH + "; marginH="
				+ marginH + "; totalH=" + totalH());
	}

	@Override
	public boolean equals(final Object o) {
		if (!(o instanceof RankComponentHeights)) {
			return false;
		}
		final RankComponentHeights h = (RankComponentHeights) o;
		return h.foldH == foldH && h.frontH == frontH && h.labelsH == labelsH && h.marginH == marginH;
	}

	@Override
	public int hashCode() {
		int result = 17;
		// we know that components are really integers
		result = 37 * result + (int) foldH;
		result = 37 * result + (int) frontH;
		result = 37 * result + (int) labelsH;
		result = 37 * result + (int) marginH;
		return result;
	}
}

final @Immutable class DesiredSize {

	final double min;
	final double max;
	final double ratioToDeselectedH;

	@Immutable
	DesiredSize(final double _min, final double _max, final double _ratioToDeselectedH) {
		min = _min;
		max = _max;
		ratioToDeselectedH = _ratioToDeselectedH;
		assert assertInRange(min, 0.0, max);
		assert ratioToDeselectedH >= 0.0;
	}

	@Immutable
	@NonNull
	DesiredSize scale(final double scale) {
		return new DesiredSize(min * scale, max * scale, ratioToDeselectedH * scale);
	}
}
