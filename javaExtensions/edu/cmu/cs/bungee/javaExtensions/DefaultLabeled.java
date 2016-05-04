package edu.cmu.cs.bungee.javaExtensions;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.approxEqualsAbsolute;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString.Justification;

/**
 * Default implementation of interface Labeled
 */
public abstract class DefaultLabeled<V> implements Labeled<V> {

	protected static final double MINIMUM_DRAWABLE_PRIORITY = 0.0;

	/**
	 * The 'parent' of all the labels; e.g. the parent facet for
	 * PerspectiveVScrollLabeled, LabeledBars and LabeledLabels, and the prefix
	 * String for LetterLabeled.
	 */
	public @NonNull V parent;

	/**
	 * -1 means uninitialized; otherwise should always be >= 1
	 */
	public int totalChildCount = -1;

	public double visibleWidth;

	/**
	 * int version of visibleWidth. Always kept equal.
	 */
	protected int iVisibleWidth;

	/**
	 * The childIndex of the label at a given x coordinate, or -1. Only label
	 * centers (iMidXs) are recorded. Length is visibleW. Only changed inside
	 * validate().
	 */
	protected int[] labelXs;

	/**
	 * Maps from Vs to child index. null iff areChildrenIndexed
	 */
	private @Nullable Map<V, Integer> childIndexes;

	/**
	 * The inclusive cumCount for each child index. Entries always equal an int.
	 */
	@NonNull
	public float[] cumCountsInclusive = new float[0];

	/**
	 * Only set by setLogicalWperItem
	 */
	private double logicalWperItem = -1.0;

	/**
	 * Our logical width, of which floor(leftEdge) - floor(leftEdge +
	 * visibleWidth) is visible.
	 *
	 * In order to fill labelXs, logicalWidth must satisfy:
	 *
	 * logicalWidth >= labelXs.size == visibleWidth == iVisibleWidth
	 */
	public double logicalWidth;

	/**
	 * offset into logicalWidth of the leftmost visible pixel. Rightmost visible
	 * pixel is leftEdge + w <= leftEdge < logicalWidth-visibleWidth;
	 */
	public double leftEdge = 0.0;

	/**
	 * The width of a horizontal slice through the rotated text, minus 1. Always
	 * >= 0 (and ==0 for Bars). Only used by drawNextComputedLabel(); there's
	 * enough room to draw label iff:
	 *
	 * label1.midX + labelW < label2.midX
	 */
	private int labelW;

	private List<V> children;

	private final static @NonNull Object countsHashInvalid = new Object();

	/**
	 * ensureChildren() calls getChildren() and updateCounts() iff
	 * getCountsHash() != countsHash.
	 *
	 * Initialize to something nonNull and not likely to equal getCountsHash().
	 */
	protected @NonNull Object countsHash = countsHashInvalid;

	/**
	 * Must call validate after initializing! Can't do it here, because
	 * subclasses may need to init stuff before validate.
	 */
	public DefaultLabeled(final @NonNull V _parent, final int width, final int _labelW) {
		assert _labelW > 0 : _labelW;

		logicalWidth = width;
		setWidth(width);

		// This will initialize childIndexes iff no one has overridden it.
		childIndex(null);
		labelW = _labelW - 1;
		parent = setParent(_parent);
	}

	/**
	 * make sure validate gets called after this
	 *
	 * @return parent
	 */
	public @NonNull V setParent(final @NonNull V _parent) {
		if (!_parent.equals(parent)) {
			parent = _parent;
			resetParent();
		}
		return parent;
	}

	/**
	 * recompute getChildren, updateCounts
	 *
	 * make sure validate gets called after this
	 */
	public void resetParent() {
		ensureChildren();
	}

	/**
	 * updateCounts() if !areCountsConstant, and computeNdrawLabels()
	 */
	protected void validate() {
		try {
			ensureChildren();
			computeNdrawLabels();
		} catch (final Exception e) {
			System.err.println("While validating " + this + ":\n");
			throw (e);
		}
	}

	/**
	 * Only called by ensureChildren()
	 *
	 * Override this if counts depend on anything besides parent
	 */
	protected @NonNull Object getCountsHash() {
		return parent;
	}

	public void setCountsHashInvalid() {
		countsHash = countsHashInvalid;
	}

	/**
	 * If getCountsHash() changes, call getChildren() and updateCounts().
	 */
	protected void ensureChildren() {
		final Object _countsHash = getCountsHash();
		if (!Objects.deepEquals(_countsHash, countsHash)) {
			countsHash = _countsHash;
			children = getChildren();
			assert children.size() > 0 : this;
			assert UtilArray.assertNoNulls(children) : this;
			updateCounts();
		} else {
			assert assertEnsureChildren();
		}
	}

	/**
	 * Only called by ensureChildren()
	 */
	private boolean assertEnsureChildren() {
		final List<V> children2 = getChildren();
		assert children.equals(children2) : this + "\n" + UtilArray.symmetricDifference(children, children2) + "\n"
				+ children;
		final int oldTotalChildCount = totalChildCount;
		updateCounts();
		assert totalChildCount == oldTotalChildCount : totalChildCount + " != " + oldTotalChildCount;
		return true;
	}

	/**
	 * Only called by ensureChildren()
	 */
	public void updateCounts() {
		final int nChildren = children.size();
		if (cumCountsInclusive.length != nChildren) {
			cumCountsInclusive = new float[nChildren];
		}
		if (childIndexes != null) {
			childIndexes.clear();
		}
		int i = 0;
		int cum = 0;
		for (final V child : children) {
			assert child != null : "null children are not allowed";
			final int count = count(child);
			assert count >= 0 : this + " child=" + child + " count=" + count;
			cum += count;
			if (childIndexes != null) {
				// This is slow
				childIndexes.put(child, i);
			}
			cumCountsInclusive[i++] = cum;
		}
		setIntLogicalBounds(leftEdge, logicalWidth, cum);
	}

	public int nChildren() {
		assert children != null && children.size() > 0 && children.size() == cumCountsInclusive.length : this + " "
				+ children;
		return cumCountsInclusive.length;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, parent);
	}

	public @Nullable V firstPotentiallyVisibleChild() {
		return childFromCumCount(iMinVisibleCumCount());
	}

	public @Nullable V lastPotentiallyVisibleChild() {
		return childFromCumCount(iMaxVisibleCumCount());
	}

	private @Nullable V childFromCumCount(final int cumCount) {
		final int index = childIndexFromCumCount(cumCount);
		if (index >= 0) {
			return childFromIndex(index);
		} else {
			return null;
		}
	}

	protected @NonNull V childFromIndex(final int index) {
		assert assertCheckChildIndex(index);
		final V result = children.get(index);
		assert result != null : this + " index=" + index + " nChildren=" + nChildren() + "\n" + formatLabelXs();
		return result;
	}

	public int nChildrenInVisibleRange() {
		final int first = childIndexFromCumCount(iMinVisibleCumCount());
		final int last = childIndexFromCumCount(iMaxVisibleCumCount());
		assert first >= 0 && last >= 0 : first + " " + last + " " + cumCount(leftEdge) + " " + cumCount(getRightEdge());
		return last - first + 1;
	}

	/**
	 * @return intCeil(cumCount(leftEdge))
	 */
	private int iMinVisibleCumCount() {
		return UtilMath.intCeil(cumCount(leftEdge) - UtilMath.ABSOLUTE_SLOP);
	}

	/**
	 * @return floor(cumCount(getRightEdge())
	 */
	private int iMaxVisibleCumCount() {
		return (int) (cumCount(getRightEdge() + UtilMath.ABSOLUTE_SLOP));
	}

	double cumCount(final double x) {
		assert assertInRange(x, 0.0, logicalWidth + 2.0 * UtilMath.ABSOLUTE_SLOP);
		final double result = x / logicalWperItem;

		// if logicalWperItem is very small, this assertion may fail. Rely on
		// x assertion and then constrain result.
		// assert assertInRange(result, 0.0, totalChildCount +
		// UtilMath.ABSOLUTE_SLOP / logicalWperItem);

		return UtilMath.constrain(result, 0.0, totalChildCount);
	}

	/**
	 * All callers are looking for [partially] visible children.
	 *
	 * @return the index of the child where child.exclusiveCumCount <= cumCount
	 *         < child.inclusiveCumCount.
	 *
	 *         If a bunch of children have the specified cumCount (because their
	 *         counts are zero) binarySearch can return any of them. So
	 *         add/subtract 0.5f to get the one with non-ero count.
	 */
	private int childIndexFromCumCount(final int cumCount) {
		assert assertInRange(cumCount, iMinVisibleCumCount(), iMaxVisibleCumCount());
		int index = Arrays.binarySearch(cumCountsInclusive,
				cumCount + (cumCount == iMaxVisibleCumCount() ? -0.5f : 0.5f));
		// Since we're searching for a non-integer, it should never be found
		assert index < 0;
		index = -index - 1;
		assert visibleCount(index) > UtilMath.ABSOLUTE_SLOP : this + "\n index=" + index + " cumCount=" + cumCount
				+ " countFromCumCount=" + countFromCumCount(index) + " childFromIndex=" + childFromIndex(index);
		assert assertInRange(cumCount, cumCountExclusive(index), cumCountInclusive(index));
		return index;
	}

	private int cumCountInclusive(final int childIndex) {
		return (int) cumCountsInclusive[childIndex];
	}

	private int cumCountExclusive(final int childIndex) {
		int cum = 0;
		if (childIndex > 0) {
			assert childIndex < cumCountsInclusive.length : this + " " + childFromIndex(childIndex) + " " + childIndex
					+ " " + cumCountsInclusive.length;
			cum = (int) cumCountsInclusive[childIndex - 1];
		} else {
			assert childIndex == 0;
		}
		return cum;
	}

	/**
	 * assert 0 <= childIndex < nChildren()
	 */
	private boolean assertCheckChildIndex(final int childIndex) {
		assert checkChildIndex(childIndex) : this + " childIndex=" + childIndex + " nChildren()=" + nChildren()
				+ " cumCounts=" + UtilString.valueOfDeep(cumCountsInclusive);
		return true;
	}

	/**
	 * @return 0 <= childIndex < nChildren()
	 */
	private boolean checkChildIndex(final int childIndex) {
		return isInRange(childIndex, 0, nChildren() - 1);
	}

	/**
	 * Only called by other classes
	 */
	public double visibleWidth() {
		return visibleWidth;
	}

	/**
	 * Only called by other classes
	 */
	public boolean isInittedLabelXs() {
		return labelXs != null;
	}

	/**
	 * @return getLogicalWidth() > visibleWidth
	 */
	public boolean isZoomed() {
		return getLogicalWidth() > visibleWidth;
	}

	/**
	 * Only called by other classes
	 */
	public double getLogicalWidth() {
		return logicalWidth;
	}

	/**
	 * Only called by other classes
	 */
	public double getLeftEdge() {
		return leftEdge;
	}

	public double getRightEdge() {
		return leftEdge + visibleWidth;
	}

	public void setWidth(final double width) {
		if (width != visibleWidth) {
			assert width > 0.0 && Double.isFinite(width) : width;
			visibleWidth = width;
			iVisibleWidth = (int) visibleWidth;
			assert visibleWidth == iVisibleWidth : this + " " + width;
			if (logicalWidth < visibleWidth) {
				setLogicalBoundsAndValidateIfChanged(leftEdge, visibleWidth);
			}
		}
	}

	public void setLogicalBoundsAndValidateIfChanged(final double _leftEdge, final double _logicalWidth) {
		if (setIntLogicalBounds(_leftEdge, _logicalWidth, totalChildCount)) {
			validate();
		}
	}

	/**
	 * @return whether logicalWperItem changed
	 */
	public boolean setIntLogicalBounds(final double _leftEdge, final double _logicalWidth, final int _totalChildCount) {
		final double[] intCountLogicalBounds = intCountLogicalBounds(_leftEdge, _logicalWidth, _totalChildCount);
		final double intLeftEdge = intCountLogicalBounds[0];
		final double intLogicalWidth = intCountLogicalBounds[1];
		return setLogicalWperItem(intLeftEdge, intLogicalWidth, _totalChildCount);
	}

	/**
	 * @return whether leftEdge, logicalWidth, or totalChildCount (and hence
	 *         logicalWperItem) changed
	 */
	protected boolean setLogicalWperItem(final double _leftEdge, final double _logicalWidth,
			final int _totalChildCount) {
		final boolean result = _leftEdge != leftEdge || _logicalWidth != logicalWidth
				|| _totalChildCount != totalChildCount
				|| !UtilMath.approxEqualsAbsolute(logicalWperItem, _logicalWidth / _totalChildCount);
		if (result) {
			assert _totalChildCount > 0 : _totalChildCount;
			assert _leftEdge >= 0.0
					&& _leftEdge + visibleWidth <= _logicalWidth + UtilMath.ABSOLUTE_SLOP : "visibleWidth="
							+ visibleWidth + "; old " + leftEdge + "/" + logicalWidth + "; new " + _leftEdge + "/"
							+ _logicalWidth;
			totalChildCount = _totalChildCount;
			leftEdge = _leftEdge;
			logicalWidth = _logicalWidth;
			logicalWperItem = logicalWidth / totalChildCount;
			assert assertEdgesNearInt(leftEdge, logicalWidth, logicalWperItem, totalChildCount);
		}
		return result;
	}

	/**
	 * @param _totalChildCount
	 * @return { intLeftEdge, intLogicalWidth }, such that
	 *         intLeftEdge/intLogicalWidth are near
	 *         doubleLeftEdge/doubleLogicalWidth, cumCount(intLeftEdge) is an
	 *         integer, and cumCount(intLogicalWidth)==totalChildCount. visible
	 *         count must be >=1
	 */
	public @NonNull double[] intCountLogicalBounds(final double doubleLeftEdge, final double doubleLogicalWidth,
			final int _totalChildCount) {
		assert _totalChildCount > 0 : _totalChildCount + " " + this;
		assert visibleWidth > 0.0;
		final double doubleLogicalWperItem = doubleLogicalWidth / _totalChildCount;
		final double doubleVisibleCount = visibleWidth / doubleLogicalWperItem;
		if (doubleVisibleCount < 1.0) {
			return intCountLogicalBounds(0.0, visibleWidth, _totalChildCount);
		} else {
			final double intLogicalWidthPerItem = visibleWidth / Math.rint(doubleVisibleCount);
			final double doubleLeftEdgeCount = doubleLeftEdge / doubleLogicalWperItem;
			final double intLeftEdge = Math.rint(doubleLeftEdgeCount) * intLogicalWidthPerItem;
			final double intLogicalWidth = Math.max(visibleWidth, _totalChildCount * intLogicalWidthPerItem);
			assert assertEdgesNearInt(intLeftEdge, intLogicalWidth, intLogicalWidthPerItem, _totalChildCount);

			final double[] result = { intLeftEdge, intLogicalWidth };
			return result;
		}
	}

	protected boolean assertEdgesNearInt(final double _leftEdge, final double _logicalWidth,
			final double _logicalWperItem, final int _totalChildCount) {
		if (_totalChildCount >= 0) {
			assert approxEqualsAbsolute(_logicalWperItem, _logicalWidth / _totalChildCount);
			final double leftCount = _leftEdge / _logicalWperItem;
			final double rightCount = (_leftEdge + visibleWidth) / _logicalWperItem;
			final double logicalWidthCount = _logicalWidth / _logicalWperItem;
			assert UtilMath.isNearInt(leftCount) : this + " left/WperItem: " + _leftEdge + "/" + _logicalWperItem
					+ " _logicalWidth=" + _logicalWidth + " _totalChildCount=" + _totalChildCount;
			assert UtilMath.isNearInt(rightCount) : leftEdge + "+" + visibleWidth + "/" + _logicalWperItem;
			assert UtilMath.isNearInt(logicalWidthCount) : _logicalWidth + "/" + _logicalWperItem;
			assert UtilMath.approxEqualsAbsolute(_totalChildCount, logicalWidthCount) : "totalChildCount="
					+ _totalChildCount + " _logicalWidth=" + logicalWidthCount + " logicalWidthCount=" + _logicalWidth;
		}
		return true;
	}

	protected void computeNdrawLabels() {
		if (totalChildCount > 0) {
			assert logicalWidth >= visibleWidth : this + " " + logicalWidth + " " + visibleWidth;
			computeLabelXs();
			drawComputedLabels();
		}
	}

	protected void computeLabelXs() {
		assert Util.assertMouseProcess();
		assert totalChildCount > 0;
		if (labelXs == null || labelXs.length != iVisibleWidth) {
			labelXs = new int[iVisibleWidth];
		}
		Arrays.fill(labelXs, -1);
		final int[] visibleChildrenRange = visibleChildrenRange();
		final int minVisibleChildIndexInclusive = visibleChildrenRange[0];
		final int maxVisibleChildIndexExclusive = visibleChildrenRange[1];
		int prevImaxX = -1; // only used in assertions
		final int[] scratchpad = new int[2];
		for (int childIndex = minVisibleChildIndexInclusive; childIndex < maxVisibleChildIndexExclusive; childIndex++) {
			// Cramming in a child with visibleCount==0 messes up zooming.
			if (visibleCount(childIndex) > UtilMath.ABSOLUTE_SLOP) {
				labelPixelRange(childIndex, scratchpad, false);
				final int iMinX = scratchpad[0];
				final int iMaxX = scratchpad[1];
				assert iMinX >= prevImaxX : computeLabelXsErrString(prevImaxX, childIndex, iMinX, iMaxX);
				prevImaxX = iMaxX;
				assert (childIndex != minVisibleChildIndexInclusive || iMinX == 0)
						&& (childIndex != maxVisibleChildIndexExclusive - 1 || iMaxX == labelXs.length - 1)

				: computeLabelXsErrStringInternal(childIndex, iMinX, iMaxX);

				final int floorMidX = (iMaxX + iMinX) / 2;
				final int ceilMidX = (iMaxX + iMinX + 1) / 2;
				final double childPriority = priority(childIndex);
				// check the integers above & below midX for an open spot...
				for (int iMidX = floorMidX; iMidX <= ceilMidX; iMidX++) {
					if (childPriority > xPriority(iMidX)) {
						labelXs[iMidX] = childIndex;
						break;
					}
				}
			}
		}
	}

	private double xPriority(final int x) {
		return priority(labelXs[x]);
	}

	protected double priority(final int childIndex) {
		if (childIndex < 0) {
			return MINIMUM_DRAWABLE_PRIORITY - 1.0;
		}
		return // bonus(childIndex) +
		priorityCount(childIndex);
	}

	// @SuppressWarnings("static-method") // overriders aren't static
	// protected double bonus(@SuppressWarnings("unused") final int childIndex)
	// {
	// return 0.0;
	// }

	protected double priorityCount(final int childIndex) {
		return visibleCount(childIndex);
	}

	private @NonNull String computeLabelXsErrString(final int prevImaxX, final int childIndex, final int iMinX,
			final int iMaxX) {
		return UtilString.valueOfDeep(cumCountsInclusive) + "\nprev=" + previous(childIndex) + " prevImaxX=" + prevImaxX
				+ " child=" + childFromIndex(childIndex) + "\n child double: " + minLabelPixel(childIndex) + " - "
				+ maxLabelPixel(childIndex) + "\n  prev double: " + minLabelPixel(childIndex - 1) + " - "
				+ maxLabelPixel(childIndex - 1) + "\n child: " + iMinX + " - " + iMaxX + "\n  prev: "
				+ labelPixelRangeString(childIndex - 1, false) + "\n "
				+ computeLabelXsErrStringInternal(childIndex, iMinX, iMaxX);
	}

	private @NonNull String computeLabelXsErrStringInternal(final int childIndex, final int iMinX, final int iMaxX) {
		return "DefaultLabeled.computeLabelXs " + this + " checking label for " + childFromIndex(childIndex)
				+ ":\n childIndex=" + childIndex + " count=" + countFromCumCount(childIndex) + " child cumCount range: "
				+ cumCountExclusive(childIndex) + " - " + cumCountInclusive(childIndex) + "\n child iMinX-iMaxX: "
				+ iMinX + " - " + iMaxX + "\n child Pixel Range: " + minLabelPixel(childIndex) + " - "
				+ maxLabelPixel(childIndex)

				+ "\n\n leftEdge/logicalWidth=" + leftEdge + " / " + logicalWidth + " visibleWidth=" + visibleWidth
				+ "\n visibleWhichChildRange()=" + UtilString.valueOfDeep(visibleChildrenRange())
				+ "\n iMin-MaxVisibleCumCount: " + iMinVisibleCumCount() + " - " + iMaxVisibleCumCount() + "\n"
				+ formatLabelXs();
	}

	/**
	 * Only called by computeLabelXsErrString
	 */
	private @Nullable V previous(final int childIndex) {
		V result = null;
		final int index = childIndex - 1;
		if (index >= 0) {
			result = childFromIndex(index);
		}
		return result;
	}

	/**
	 * Only called when generating error messages (computeLabelXsErrString and
	 * formatLabelXs), so ignore further errors.
	 */
	private @Nullable String labelPixelRangeString(final int childIndex, final boolean useDoubles) {
		if (checkChildIndex(childIndex)) {
			String minPixelString = "<error>";
			String maxPixelString = "<error>";
			try {
				double minPixel;
				double maxPixel;
				if (useDoubles) {
					minPixel = minLabelPixel(childIndex);
					maxPixel = maxLabelPixel(childIndex);
				} else {
					final int[] intLabelPixelRange = labelPixelRange(childIndex, new int[2], true);
					minPixel = intLabelPixelRange[0];
					maxPixel = intLabelPixelRange[1];
				}
				final int numChars = UtilString.numChars((int) -logicalWidth);
				minPixelString = UtilString.addCommas(minPixel, numChars, 2);
				maxPixelString = UtilString.addCommas(maxPixel, numChars, 2);
			} catch (final Throwable e) {
				System.err.println("Please constrain 'Throwable' to what you really expect here");
			}
			return "[" + minPixelString + " - " + maxPixelString + "]";
		} else {
			return null;
		}
	}

	/**
	 * @return [minVisibleChildIndexInclusive, maxVisibleChildIndexExclusive]
	 *         over all visible children.
	 *
	 *         i.e. over all children c where: iMinVisibleCumCount() <=
	 *         c.cumCount <= iMaxVisibleCumCount()
	 */
	private @NonNull int[] visibleChildrenRange() {
		final int[] result = { 0, nChildren() };// common case
		final int iMinVisibleCumCount = iMinVisibleCumCount();
		final int iMaxVisibleCumCount = iMaxVisibleCumCount();
		if (iMinVisibleCumCount != 0 || iMaxVisibleCumCount != totalChildCount) {
			// Oh well, not common case

			final int minVisibleWhichChild = childIndexFromCumCount(iMinVisibleCumCount);
			final int maxVisibleWhichChild = childIndexFromCumCount(iMaxVisibleCumCount);
			assert cumCountsInclusive[minVisibleWhichChild] > iMinVisibleCumCount : cumCountRangeChildrenErrMsg(
					iMinVisibleCumCount, minVisibleWhichChild, iMaxVisibleCumCount, maxVisibleWhichChild);
			// Could be == if minChild.count == 0
			assert minVisibleWhichChild == 0 || cumCountsInclusive[minVisibleWhichChild
					- 1] <= iMinVisibleCumCount : "minVisibleWhichChild == 0 || cumCounts[minVisibleWhichChild - 1] <= iMinVisibleCumCount fails:\n"
							+ cumCountRangeChildrenErrMsg(iMinVisibleCumCount, minVisibleWhichChild,
									iMaxVisibleCumCount, maxVisibleWhichChild);

			assert cumCountsInclusive[maxVisibleWhichChild] >= iMaxVisibleCumCount : cumCountRangeChildrenErrMsg(
					iMinVisibleCumCount, minVisibleWhichChild, iMaxVisibleCumCount, maxVisibleWhichChild);
			assert maxVisibleWhichChild == 0 || cumCountsInclusive[maxVisibleWhichChild
					- 1] <= iMaxVisibleCumCount : cumCountRangeChildrenErrMsg(iMinVisibleCumCount, minVisibleWhichChild,
							iMaxVisibleCumCount, maxVisibleWhichChild);
			result[0] = minVisibleWhichChild;
			result[1] = maxVisibleWhichChild + 1;
		}
		return result;
	}

	private @NonNull String cumCountRangeChildrenErrMsg(final int minCumCount, final int minWhichChild,
			final int maxCumCount, final int maxWhichChild) {
		return this + "\n nChildren=" + UtilString.addCommas(nChildren()) + "\n totalChildCount="
				+ UtilString.addCommas(totalChildCount) + "\n minCumCount=" + UtilString.addCommas(minCumCount)
				+ "\n maxCumCount=" + UtilString.addCommas(maxCumCount)

				+ "\n minWhichChild=" + UtilString.addCommas(minWhichChild) + " (" + childFromIndex(minWhichChild)
				+ ") "
				+ (minWhichChild == 0 ? ""
						: " cumCountExclusive=" + UtilString.addCommas(cumCountExclusive(minWhichChild)))
				+ " cumCountInclusive=" + UtilString.addCommas(cumCountInclusive(minWhichChild))

				+ "\n maxWhichChild=" + UtilString.addCommas(maxWhichChild) + " (" + childFromIndex(maxWhichChild)
				+ ") "
				+ (maxWhichChild >= cumCountsInclusive.length ? ""
						: " cumCountInclusive=" + UtilString.addCommas(cumCountInclusive(maxWhichChild)))
				+ (maxWhichChild >= cumCountsInclusive.length - 1 ? ""
						: " cumCounts[maxWhichChild+1]=" + UtilString.addCommas(cumCountInclusive(maxWhichChild + 1)))

				+ "\n" + formatLabelXs();
	}

	protected void drawComputedLabels() {
		drawNextComputedLabel(-1, -1, priority(-1));
	}

	/**
	 * March along pixels, finding the child Objects to draw. At each pixel, you
	 * draw the child with the highest count at that pixel, which was computed
	 * above, unless another child with a higher count has a label that would
	 * occlude it, unless IT would be occluded. So you get a recusive test,
	 * where a conflict on the rightmost label can propagate all the way back to
	 * the left. At each call, you know there are no conflicts with
	 * leftCandidate from the left. You look for a conflict on the right (or
	 * failing that, the next non-conflict on the right) and recurse on that to
	 * get the next labeled Object to the right. You draw leftCandidate iff it
	 * doesn't conflict with that next label.
	 */
	protected int drawNextComputedLabel(final int leftCandidateIndex, final int leftCandidateMidX,
			final double leftCandidatePriority) {
		int drawnMidLabelPixel = -1;
		double threshold = leftCandidatePriority;
		final int[] rightCandidatePixelRange = new int[2];
		for (int x = leftCandidateMidX + 1; x < iVisibleWidth; x++) {
			if (x > leftCandidateMidX + labelW) {
				// caller will draw leftCandidate.
				threshold = MINIMUM_DRAWABLE_PRIORITY - 1;
			}
			final int rightCandidateIndex = labelXs[x];
			double rightCandidatePriority;
			if (rightCandidateIndex >= 0 && rightCandidateIndex != leftCandidateIndex
					&& (rightCandidatePriority = priority(rightCandidateIndex)) > threshold) {
				final int rightCandidateMidX = computeLabelPixelRange(rightCandidateIndex, rightCandidatePixelRange);
				if (rightCandidateMidX >= 0) {
					final int nextDrawnMidLabelPixel = drawNextComputedLabel(rightCandidateIndex, rightCandidateMidX,
							rightCandidatePriority);
					if (nextDrawnMidLabelPixel >= 0 && nextDrawnMidLabelPixel < rightCandidateMidX + labelW) {
						drawnMidLabelPixel = nextDrawnMidLabelPixel;
					} else {
						drawnMidLabelPixel = rightCandidateMidX;
						assert rightCandidateMidX < iVisibleWidth : rightCandidateIndex + " " + rightCandidateMidX + " "
								+ iVisibleWidth;
						assert labelXs[rightCandidateMidX] == rightCandidateIndex : rightCandidateIndex + " "
								+ labelXs[rightCandidateMidX];
						final V rightCandidate = childFromIndex(rightCandidateIndex);
						assert countFromCumCount(rightCandidateIndex) >= 0 : this + " child=" + rightCandidate
								+ " count=" + countFromCumCount(rightCandidateIndex);
						drawLabel(rightCandidate, rightCandidatePixelRange);
					}
					break;
				}
			}
		}
		return drawnMidLabelPixel;
	}

	/**
	 * Only called by drawNextComputedLabel
	 *
	 * @param scratchpad
	 *            used to return pixel range for label. Only valid if return
	 *            value >= 0
	 * @return pixel where label should be centered, or -1 if there is no room
	 *         for a label.
	 */
	private int computeLabelPixelRange(final int childIndex, final @NonNull int[] scratchpad) {
		labelPixelRange(childIndex, scratchpad, false);
		int iMidX = (scratchpad[0] + scratchpad[1]) >>> 1;
		if (labelXs[iMidX] != childIndex) {
			if (scratchpad[1] < iVisibleWidth - 1) {
				iMidX++;
				assert labelXs[iMidX] == childIndex : childIndex + " _x0=" + iMidX + " labelXs.get(_x0-1)="
						+ labelXs[iMidX - 1] + " labelXs.get(_x0)=" + labelXs[iMidX] + formatLabelXs();
				scratchpad[0]++;
				scratchpad[1]++;
			} else {
				// No room to draw any more labels
				iMidX = -1;
			}
		}
		return iMidX;
	}

	/**
	 * Try to make room for everyone. This makes the range for a child depend on
	 * the ranges of adjacent children, and it gets complicated trying to figure
	 * out who has priority.
	 *
	 * @return [iMinLabelPixel, iMaxLabelPixel] relative to leftEdge
	 *
	 *         where 0 <= iMinLabelPixel < iVisibleWidth
	 */
	private @NonNull int[] labelPixelRange(final int childIndex, final @NonNull int[] scratchpad,
			final boolean noError) {
		final double minLabelPixel = minLabelPixel(childIndex);
		final double maxLabelPixel = maxLabelPixel(childIndex);
		assert noError || minLabelPixel < visibleWidth + UtilMath.ABSOLUTE_SLOP : this + "\n child="
				+ childFromIndex(childIndex) + " (" + childIndex + ") minLabelPixel=" + minLabelPixel + " visibleWidth="
				+ visibleWidth;
		assert noError || maxLabelPixel > 0.0 : this + "\n child=" + childFromIndex(childIndex) + " (" + childIndex
				+ ") maxLabelPixel=" + maxLabelPixel + " visibleWidth=" + visibleWidth;

		int iMinLabelPixel = UtilMath.constrain((int) minLabelPixel, 0, iVisibleWidth - 1);
		int iMaxLabelPixel = Math.min(iVisibleWidth - 1, (int) maxLabelPixel);
		// if ((int) minLabelPixel == (int) maxLabelPixel) {
		// // Don't try to be fancy if it only overlaps one pixel
		// iMaxLabelPixel = iMinLabelPixel;
		// }
		if (iMinLabelPixel > iMaxLabelPixel) {
			assert noError : "\ndouble: " + minLabelPixel + " - " + maxLabelPixel + "\nint: " + iMinLabelPixel + " - "
					+ iMaxLabelPixel;
			// max was limited to visibleWidth-1
			iMinLabelPixel = iMaxLabelPixel;
		}
		final double minLabelPixelPercent = 1.0 - (minLabelPixel - iMinLabelPixel);
		final double maxLabelPixelPercent = maxLabelPixel - (iMaxLabelPixel - 1);
		assert noError || iMaxLabelPixel >= iMinLabelPixel : "1. " + childFromIndex(childIndex) + " min-maxDouble="
				+ minLabelPixel + " - " + maxLabelPixel + " iMin-iMax=" + iMinLabelPixel + " - " + iMaxLabelPixel + " "
				+ visibleWidth + " count=" + countFromCumCount(childIndex);
		if (iMinLabelPixel + 1 == iMaxLabelPixel) {
			if (minLabelPixelPercent < 0.5 && minLabelPixelPercent < maxLabelPixelPercent) {
				// Util.indent("DefaultLabeled.LabelPixelRange reducing " +
				// child + " to one pixel (minLabelPixelPercent="
				// + minLabelPixelPercent + ") " + iMinLabelPixel + " - "
				// + iMaxLabelPixel);
				iMinLabelPixel++;
			} else if (maxLabelPixelPercent < 0.5) {
				// Util.indent("DefaultLabeled.LabelPixelRange reducing " +
				// child + " to one pixel (maxLabelPixelPercent="
				// + maxLabelPixelPercent + ") " + iMinLabelPixel + " - "
				// + iMaxLabelPixel);
				iMaxLabelPixel--;
			}
		} else if (iMinLabelPixel < iMaxLabelPixel) {
			final int previousChildIndex = childIndex - 1;
			if (previousChildIndex >= 0 && isPotentiallyVisible(previousChildIndex)
					&& (minLabelPixelPercent < 0.5 || labelPixelWidth(previousChildIndex) <= 1.0)) {
				// Util.indent("DefaultLabeled.LabelPixelRange reducing " +
				// child + " by one pixel (minLabelPixelPercent="
				// + minLabelPixelPercent + " minLabelPixelDouble="
				// + minLabelPixelDouble + ") " + iMinLabelPixel + " - "
				// + iMaxLabelPixel);
				iMinLabelPixel++;
			}
			final int nextChildIndex = childIndex + 1;
			if (nextChildIndex < nChildren() && isPotentiallyVisible(nextChildIndex)
					&& (maxLabelPixelPercent < 0.5 || labelPixelWidth(nextChildIndex) <= 1.0)) {
				// Util.indent("DefaultLabeled.LabelPixelRange reducing " +
				// child + " by one pixel (maxLabelPixelPercent="
				// + maxLabelPixelPercent + ") " + iMinLabelPixel + " - "
				// + iMaxLabelPixel);
				iMaxLabelPixel--;
			}
		}
		assert noError || (iMaxLabelPixel >= 0 && iMinLabelPixel < iVisibleWidth
				&& iMaxLabelPixel >= iMinLabelPixel) : "2. min-maxDouble=" + minLabelPixel + " - " + maxLabelPixel
						+ " iMin-iMax=" + iMinLabelPixel + " - " + iMaxLabelPixel + " iVisibleWidth=" + iVisibleWidth;
		assert noError || (iMaxLabelPixel < iVisibleWidth && iMinLabelPixel >= 0
				&& iMaxLabelPixel <= maxLabelPixel) : "iVisibleWidth=" + iVisibleWidth + " iMin/MaxLabelPixel="
						+ iMinLabelPixel + " - " + iMaxLabelPixel + " min/maxLabelPixelDouble=" + minLabelPixel + " - "
						+ maxLabelPixel;

		scratchpad[0] = iMinLabelPixel;
		scratchpad[1] = iMaxLabelPixel;
		return scratchpad;
	}

	private double labelPixelWidth(final int childIndex) {
		return maxLabelPixel(childIndex) - minLabelPixel(childIndex);
	}

	/**
	 * @return x offset relative to left edge.
	 */
	public double minLabelPixel(final int childIndex) {
		return childIndex > 0 ? maxLabelPixel(childIndex - 1) : -leftEdge;
	}

	/**
	 * @return x offset relative to leftEdge.
	 */
	public double maxLabelPixel(final int childIndex) {
		return visibleXcoord(cumCountsInclusive[childIndex]);
	}

	private double visibleXcoord(final double cumCount) {
		assert assertInRange(cumCount, 0.0, totalChildCount);
		return cumCount * logicalWperItem - leftEdge;
	}

	/**
	 * Only called by PerspectiveViz.anchorForPopup
	 *
	 * @return whether the visible count of v >= 0.5
	 */
	public boolean isPotentiallyVisible(final @NonNull V v) {
		return isPotentiallyVisible(childIndex(v));
	}

	/**
	 * Override this to avoid overhead of maintaining childIndexes.
	 *
	 * @param child
	 * @Null only during initialization.
	 *
	 * @return index into children of child [0, nChildren), or -1 if not found.
	 */
	protected int childIndex(final @Nullable V child) {
		if (child == null) {
			// child==null only during initialization, so childIndexes becomes
			// non-null iff childIndex() is not overridden.
			childIndexes = new HashMap<>();
		}
		assert childIndexes != null;
		final Integer result = childIndexes.get(child);
		final int intResult = result == null ? -1 : result;
		return intResult;
	}

	/**
	 * @return whether the visible count of v >= 0.5
	 */
	private boolean isPotentiallyVisible(final int childIndex) {
		return visibleCount(childIndex) >= 0.5;
	}

	/**
	 * @return child's visible count, considering that its pixel range may
	 *         straddle the left or right visible edge.
	 */
	protected double visibleCount(final int childIndex) {
		return countFromCumCount(childIndex) * visiblePercent(childIndex);
	}

	protected double visiblePercent(final int childIndex) {
		double result = 0.0;
		final double minX = minLabelPixel(childIndex);
		final double maxX = maxLabelPixel(childIndex);
		if (minX < visibleWidth && maxX > 0.0 && maxX > minX) {
			final double nVisiblePixels = Math.min(maxX, visibleWidth) - Math.max(minX, 0.0);
			assert assertInRange(nVisiblePixels, 0.0, visibleWidth);
			result = nVisiblePixels / (maxX - minX);
			assert assertInRange(result, 0.0, 1.0);
		}
		return result;
	}

	/**
	 * Use our cached info instead of a potentially expensive recalculation
	 * (using count())
	 */
	protected int countFromCumCount(final int childIndex) {
		final int result = countFromCumCountNoError(childIndex);
		assert result >= 0 : "childIndex=" + childIndex + " child=" + childFromIndex(childIndex)
				+ " cumCountsInclusive[childIndex]=" + cumCountsInclusive[childIndex] + " result=" + result
				+ (childIndex > 0 ? " prev child=" + childFromIndex(childIndex - 1)
						+ " cumCountsInclusive[childIndex - 1]=" + cumCountsInclusive[childIndex - 1] : "");
		return result;
	}

	/**
	 * Use our cached info instead of a potentially expensive recalculation
	 * (using count())
	 */
	protected int countFromCumCountNoError(final int childIndex) {
		assert assertCheckChildIndex(childIndex);
		int result = (int) cumCountsInclusive[childIndex];
		if (childIndex > 0) {
			result -= cumCountsInclusive[childIndex - 1];
		}
		return result;
	}

	public @NonNull String formatLabelXs() {
		final StringBuilder buf = new StringBuilder();
		buf.append("\nDefaultLabeled.formatLabelXs ").append(this).append("\n");
		buf.append(parent).append(" has ").append(nLabels()).append(" Labels over ").append(iVisibleWidth)
				.append(" pixels. totalChildCount=").append(UtilString.addCommas(totalChildCount)).append(" labelW=")
				.append(labelW).append("\n leftEdge=     ").append(xNcumCount(leftEdge)).append("rightEdge=    ")
				.append(xNcumCount(getRightEdge())).append("logicalWidth= ").append(xNcumCount(logicalWidth))
				.append("\n");
		final FormattedTableBuilder align = new FormattedTableBuilder();
		align.addLine("Child Index", "Child", "Priority", "Count", "Visible Count", "CumCounts", "Double Pixel Range",
				"Pixel Range", "x|labelXs[x]=Child Index");
		align.setColumnJustifications(Justification.RIGHT, Justification.CENTER, Justification.CENTER,
				Justification.CENTER, Justification.CENTER, Justification.CENTER, Justification.CENTER,
				Justification.CENTER, Justification.LEFT);
		align.addLine();
		for (int childIndex = 0; childIndex < nChildren(); childIndex++) {
			final V child = childFromIndex(childIndex);
			String priority = "<error>";
			String visibleCount = "<error>";
			String cumCountExclusive = "<error>";
			String cumCountInclusive = "<error>";
			try {
				priority = Double.toString(priority(childIndex));
				final int numCharsCount = UtilString.numChars(totalChildCount);
				visibleCount = UtilString.addCommas(visibleCount(childIndex), numCharsCount, 2);
				cumCountExclusive = UtilString.addCommas(cumCountExclusive(childIndex), numCharsCount);
				cumCountInclusive = UtilString.addCommas(cumCountInclusive(childIndex), numCharsCount);
			} catch (final Throwable e) {
				System.err.println("Please constrain 'Throwable' to what you really expect here");
			}
			align.addLine(childIndex, "'" + child + "'", priority, countFromCumCountNoError(childIndex), visibleCount,
					cumCountExclusive + " - " + cumCountInclusive, labelPixelRangeString(childIndex, true),
					labelPixelRangeString(childIndex, false), " ");
		}
		for (int x = 0; x < iVisibleWidth; x++) {
			final int childIndex = labelXs[x];
			if (childIndex >= 0) {
				if (childIndex >= nChildren()) {
					System.err.println("nDefaultLabeled.formatLabelXs x=" + x + " childIndex=" + childIndex
							+ " nChildren=" + nChildren());
				} else {
					final Object prev = align.set(8, childIndex + 1, x);
					assert " ".equals(prev) : "'" + prev + "' x=" + x + " childIndex=" + childIndex;
				}
			}
		}
		final String result = buf.append(align.format()).toString();
		assert result != null;
		return result;
	}

	/**
	 * Only called by formatLabelXs
	 */
	private @NonNull String xNcumCount(final double x) {
		return UtilString.addCommas(x) + "      (cumCount of " + UtilString.addCommas(x / logicalWperItem) + ")\n ";
	}

	/**
	 * Only called by formatLabelXs
	 */
	protected int nLabels() {
		final HashSet<Integer> labels = new HashSet<>();
		for (final int childIndex : labelXs) {
			if (childIndex >= 0) {
				labels.add(childIndex);
			}
		}
		return labels.size();
	}

	/**
	 * Only called by DefaultLabeledPNode.setFont()
	 */
	protected void setLabelW(final int _labelW) {
		assert _labelW >= 1 : _labelW;
		final int __labelW = _labelW - 1;
		if (labelW != __labelW) {
			labelW = __labelW;
			validate();
		}
	}

}
