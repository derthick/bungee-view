package edu.cmu.cs.bungee.client.viz.tagWall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.DefaultLabeledForPV;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.markup.BungeeAPText;
import edu.cmu.cs.bungee.client.viz.markup.LetterLabeledAPText;
import edu.cmu.cs.bungee.javaExtensions.FormattedTableBuilder;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.UtilString.Justification;
import edu.umd.cs.piccolo.activities.PActivity;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;
import uk.org.bobulous.java.intervals.Interval;

/**
 * LetterLabeled supports typing/clicking on prefixes. (Letters supports
 * dragging to pan/zoom.)
 */
public class LetterLabeled extends DefaultLabeledForPV<String, LetterLabeledAPText> {

	private static final @NonNull String DUMMY_PREFIX = "☜";

	private static final @NonNull String DUMMY_SUFFIX = "☞";

	@NonNull
	String lastExplicitPrefix = "";

	/**
	 * Maps from a <i>lower case</i> String <i>of length 0 or 1</i> to the
	 * Perspective range of children starting with <b><i> parent+string </i></b>
	 * .
	 *
	 * It should be null only while we're recomputing for a new parent.
	 */
	private Map<String, Interval<Perspective>> letterOffsets;

	LetterLabeled(final @NonNull Letters _letters) {
		super((int) _letters.getWidth(), _letters.perspectiveViz.labelHprojectionW(), "", _letters);
		assert perspective().isAlphabetic() : perspective() + " is not alphabetic";
		validate();
	}

	@Override
	protected void validate() {
		if (parentPNode.getVisible() && perspectiveViz().rank.isConnected) {
			setParent(prefix());
			super.validate();
		}
	}

	@Override
	protected void computeNdrawLabels() {
		if (perspectiveViz().isConnected()) {
			// If we're being animated to invisibility, don't bother redrawing.
			super.computeNdrawLabels();
		}
	}

	/**
	 * @return longest common prefix, <b><i>in lower case</i></b>, of the names
	 *         of p's children in [firstVisibleBar, lastVisibleBar].
	 */
	@NonNull
	String prefix() {
		String result;
		final Perspective firstVisibleBar = perspectiveViz().firstVisibleBar();
		final Perspective lastVisibleBar = perspectiveViz().lastVisibleBar();
		if (firstVisibleBar == lastVisibleBar) {
			result = firstVisibleBar.getNameNow().toLowerCase();
			assert result != null;
		} else {
			// Only need to know first & last to compute commonPrefix
			query().getNamesNow(UtilArray.getArrayList(firstVisibleBar, lastVisibleBar));
			final String name1 = firstVisibleBar.getName().toLowerCase();
			final String name2 = lastVisibleBar.getName().toLowerCase();
			assert name1 != null && name2 != null;
			result = UtilString.commonPrefix(name1, name2);
		}
		return result;
	}

	@Override
	public void redrawCallback() {
		if (isInitted()) {
			super.redrawCallback();
		}
	}

	@Override
	public void resetParent() {
		if (isInitted()) {
			super.resetParent();
		}
	}

	@SuppressWarnings("null")
	private boolean isInitted() {
		return parentPNode != null;
	}

	@Override
	public String setParent(final @NonNull String lowerCasePrefix) {
		assert lowerCasePrefix.equals(lowerCasePrefix.toLowerCase()) : lowerCasePrefix;
		if (lowerCasePrefix.equals(parent)) {
			if (totalChildCount < 0) {
				resetParent();
			}
		} else {
			// parent==prefix and V==suffix, so label text depends on parent.
			removeAllLabels(true);
			super.setParent(lowerCasePrefix);
		}
		return parent;
	}

	public @NonNull String formatLetterOffsets() {
		final StringBuilder buf = new StringBuilder();
		buf.append("\nSuffixes for ").append(this).append(" prefix='").append(prefix()).append("':\n\n");

		final FormattedTableBuilder align = new FormattedTableBuilder();
		align.addLine("Suffix", "Total Count", "Cum Count Exclusive", "First Child", "whichChild", "Last Child",
				"whichChild");
		align.setColumnJustifications(Justification.CENTER, Justification.CENTER, Justification.CENTER,
				Justification.RIGHT, Justification.LEFT, Justification.RIGHT, Justification.LEFT);
		align.addLine();
		int sumCounts = formatLetterOffsetsInternal(align, DUMMY_PREFIX, letterOffsets.get(DUMMY_PREFIX), 0);
		for (final Entry<String, Interval<Perspective>> entry : letterOffsets.entrySet()) {
			final String suffix = entry.getKey();
			assert suffix != null;
			if (!isDummy(suffix)) {
				sumCounts += formatLetterOffsetsInternal(align, suffix, entry.getValue(), sumCounts);
			}
		}
		formatLetterOffsetsInternal(align, DUMMY_SUFFIX, letterOffsets.get(DUMMY_SUFFIX), sumCounts);
		align.addLine();
		buf.append(align.format()).append("\n");
		final String result = buf.toString();
		assert result != null;
		return result;
	}

	private static int formatLetterOffsetsInternal(final @NonNull FormattedTableBuilder align,
			final @NonNull String key, final @Nullable Interval<Perspective> value, final int cumCount) {
		int letterCount = 0;
		if (value != null) {
			final Perspective lowerEndpoint = value.getLowerEndpoint();
			final Perspective upperEndpoint = value.getUpperEndpoint();
			assert lowerEndpoint != null && upperEndpoint != null : value;
			assert lowerEndpoint.getParent() == upperEndpoint.getParent() : value;
			for (Perspective p = lowerEndpoint; p != null
					// if getUpperEndpoint is the last sibling, p becomes null
					&& p.compareTo(upperEndpoint) <= 0; p = p.next()) {
				letterCount += p.getTotalCount();
			}
			align.addLine("'" + key + "'", letterCount, cumCount + letterCount, lowerEndpoint,
					lowerEndpoint.whichChildRaw(), upperEndpoint, upperEndpoint.whichChildRaw());
		}
		return letterCount;
	}

	private @Nullable Interval<Perspective> getLetterOffsets(final @NonNull String length1string) {
		assert length1string.length() <= 1 : length1string;
		return letterOffsets.get(length1string);
	}

	@Override
	// Returns list of initial length=1 extensions to current parent.
	// That is, the keyset of letterOffsets, which it recomputes.
	public @NonNull List<String> getChildren() {
		assert perspectiveViz().rank.isConnected : this;
		query().insideLetterLabeledZoom++;
		letterOffsets = perspective().getLetterOffsets(parent, /* this */ null);
		query().insideLetterLabeledZoom--;
		letterOffsets.remove(DUMMY_PREFIX);
		letterOffsets.remove(DUMMY_SUFFIX);

		final List<String> result = getChildrenInternal();
		assert result.size() > 0 : this + "\n" + formatLetterOffsets();
		return result;
	}

	/**
	 * Adds dummyPrefix/Suffix if there are siblingsNotWithPrefix outside
	 * visible range.
	 */
	@SuppressWarnings("null")
	private @NonNull List<String> getChildrenInternal() {
		assert letterOffsets.size() > 0;
		final List<String> result = new ArrayList<>(letterOffsets.size() + 2);
		result.addAll(letterOffsets.keySet());
		assert result.size() > 0 : result + "\n" + letterOffsets.size();
		assert UtilArray.assertIsSorted(result, UtilString.MY_US_COLLATOR);
		final Perspective firstWithPrefix = letterOffsets.get(result.get(0)).getLowerEndpoint();
		final Perspective lastWithPrefix = letterOffsets.get(result.get(result.size() - 1)).getUpperEndpoint();
		assert result.size() > 1 || result.get(0).length() > 0 || firstWithPrefix == lastWithPrefix : this;
		getChildrenInternal2(result, firstWithPrefix.previous(), DUMMY_PREFIX);
		getChildrenInternal2(result, lastWithPrefix.next(), DUMMY_SUFFIX);
		return result;
	}

	private void getChildrenInternal2(final @NonNull List<String> keys,
			final @Nullable Perspective siblingNotWithPrefix, final @NonNull String dummy) {
		if (siblingNotWithPrefix != null) {
			final boolean isDummyPrefix = dummy == DUMMY_PREFIX;
			final Perspective perspective = perspective();
			final Perspective low = isDummyPrefix ? perspective.getRawNthChild(0) : siblingNotWithPrefix;
			final Perspective high = isDummyPrefix ? siblingNotWithPrefix
					: perspective.getRawNthChild(perspective.nChildrenRaw() - 1);
			if (perspective.putLetterOffsetsValue(prefix(), dummy, low, high, null, true,
					Util.nonNull(letterOffsets))) {
				if (isDummyPrefix) {
					keys.add(0, dummy);
				} else {
					keys.add(dummy);
				}
			}
		}
	}

	@Override
	public void updateCounts() {
		super.updateCounts();
		assert totalChildCount == perspective().getTotalChildTotalCount() : this + " totalChildCount=" + totalChildCount
				+ " perspective.getTotalChildTotalCount()=" + perspective().getTotalChildTotalCount() + "\n"
				+ formatLetterOffsets();
	}

	@Override
	public int count(final @NonNull String length1string) {
		final Interval<Perspective> prefixPerspectiveInterval = getLetterOffsets(length1string);
		assert prefixPerspectiveInterval != null : countErrMsg(length1string);
		final Perspective upperEndpoint = prefixPerspectiveInterval.getUpperEndpoint();
		final Perspective lowerEndpoint = prefixPerspectiveInterval.getLowerEndpoint();
		assert upperEndpoint != null && lowerEndpoint != null : countErrMsg(length1string);
		final int count = upperEndpoint.cumTotalCountInclusive() - lowerEndpoint.cumTotalCountExclusive();
		assert count > 0 : countErrMsg(length1string);
		return count;
	}

	private String countErrMsg(final @NonNull String length1string) {
		return this + " '" + length1string + "' " + getLetterOffsets(length1string) + "\n" + formatLetterOffsets();
	}

	@Override
	public @NonNull LetterLabeledAPText getLabel(final @NonNull String suffix, final int minPixel, final int maxPixel) {
		assert suffix.length() <= 1 : suffix;
		assert suffix.equals(suffix.toLowerCase());
		assert !isDummy(suffix) : perspectiveViz() + " prefix+suffix='" + parent + "' + '" + suffix + "' pixels="
				+ minPixel + "-" + maxPixel + "\n" + formatLetterOffsets() + formatLabelXs();
		final int midX = (minPixel + maxPixel) / 2;
		final String labelText = parent + (suffix.equals(" ") ? "␣" : suffix.toUpperCase());
		LetterLabeledAPText label = getFromCache(suffix);
		assert label == null || label.getFont() == art().getCurrentFont() : "setFont should have cleared zCache.";
		if (label == null) {
			label = new LetterLabeledAPText(labelText, perspectiveViz(), this);
		}
		assert label.getText().equals(labelText) : this + "\n label=" + label + " text=" + labelText;
		label.setPTextOffset(midX);
		assert label.getParent() == null : label;
		label.setVisible(hasMultipleChildren(parent));
		return label;
	}

	public void brush(final @NonNull BungeeAPText node, final boolean state) {
		assert node.getVisible() : this;
		node.setTextPaint(state ? UtilColor.WHITE : BungeeConstants.TAGWALL_FG_COLOR_DARKER);
	}

	public static char convertSuffix(final char c) {
		return c == '␣' ? ' ' : Character.toLowerCase(c);
	}

	@Override
	protected double priorityCount(final int childIndex) {
		double result;
		if (isDummy(childFromIndex(childIndex))) {
			result = MINIMUM_DRAWABLE_PRIORITY - 1.0;
		} else {
			result = super.priorityCount(childIndex);
		}
		return result;
	}

	private static boolean isDummy(final @NonNull String suffix) {
		assert suffix.length() <= 1 : suffix;
		return suffix.equals(DUMMY_PREFIX) || suffix.equals(DUMMY_SUFFIX);
	}

	/**
	 * Called by mouse click on LetterLabeledAPText and KeyEventHandler
	 *
	 * @return whether zoom succeeded
	 */
	boolean zoomTo(final char lowerCaseSuffix) {
		boolean result = false;
		final String prefix = isZooming() ? lastExplicitPrefix : prefix();
		assert Character.isDefined(lowerCaseSuffix);
		if (lowerCaseSuffix == '\b') {
			// Find the longest prefix that is shorter than prefix() and
			// that will include at least one additional child.
			if (prefix.length() > 0) {
				result = zoom(longestSubprefixWithMultipleChildren(prefix));
			} else if (logicalWidth > visibleWidth + UtilMath.ABSOLUTE_SLOP) {
				result = zoom("");
			} else {
				art().setTip(perspective().getNameIfCached() + " is fully zoomed out already");
			}
		} else {
			assert perspectiveViz().nBarsInVisibleRange() > 1;
			assert UtilString.isPrintableChar(lowerCaseSuffix) : "Ignoring non-printable char '" + lowerCaseSuffix
					+ "'. Character.getName=" + Character.getName(lowerCaseSuffix);
			result = zoom(prefix + lowerCaseSuffix);
		}
		return result;
	}

	/**
	 * Only called by zoomTo() when suffx is '\b' (and recursively)
	 */
	private @NonNull String longestSubprefixWithMultipleChildren(final @NonNull String lowerCaseSuffix) {
		// assume subprefixes without a getLetterOffsets don't have any more
		// children than their parent
		assert lowerCaseSuffix.length() > 0;

		final String subPrefix = lowerCaseSuffix.substring(0, lowerCaseSuffix.length() - 1);
		final String result = subPrefix.length() == 0 || hasMultipleChildren(subPrefix) ? subPrefix
				: longestSubprefixWithMultipleChildren(subPrefix);
		return result;
	}

	/**
	 * Only called by getLabel() and longestSubprefixWithMultipleChildren()
	 *
	 * @return does prefix have more than one immediate non-dummy extension.
	 */
	private boolean hasMultipleChildren(final @NonNull String lowerCasePrefix) {
		final Map<String, Interval<Perspective>> _letterOffsets = perspective().lookupLetterOffsets(lowerCasePrefix);
		int n = 0;
		if (_letterOffsets != null) {
			n = _letterOffsets.size();
			if (_letterOffsets.get(DUMMY_PREFIX) != null) {
				n--;
			}
			if (_letterOffsets.get(DUMMY_SUFFIX) != null) {
				n--;
			}
		}
		return _letterOffsets != null && n >= 2;
	}

	/**
	 * Only called by zoomTo()
	 *
	 * @return whether any zooming occurred
	 */
	private boolean zoom(final @NonNull String lowerCasePrefix) {
		assert lowerCasePrefix.equals(Util.nonNull(lowerCasePrefix.toLowerCase())) : lowerCasePrefix;
		boolean result = false;
		if (lowerCasePrefix.length() == 0) {
			result = parent.length() > 0;
			if (result) {
				createZoomer(0.0, visibleWidth, lowerCasePrefix);
			} else {
				System.err.println("LetterLabeled.zoom parent already equals prefix: " + perspective().getNameIfCached()
						+ " prefix='" + lowerCasePrefix + "'");
			}
		} else if (zoomInternal(lowerCasePrefix)) {
			result = true;
		} else {
			art().setTip("No " + perspective().getNameIfCached() + " tags with non-zero count start with '"
					+ lowerCasePrefix.replaceAll("(?:\n|\r|\u0085|\u2028|\u2029)+", "<line terminator>") + "'");
		}
		return result;
	}

	private boolean zoomInternal(final @NonNull String lowerCasePrefix) {
		final Interval<Perspective> rangeForPrefix = getRangeForPrefixNow(lowerCasePrefix);
		assert rangeForPrefix != null;
		if (rangeForPrefix.isEmpty()) {
			return false;
		}
		final Perspective leftFacet = Util.nonNull(rangeForPrefix.getLowerEndpoint());
		final Perspective rightFacet = Util.nonNull(rangeForPrefix.getUpperEndpoint());
		// if (leftFacet == rightFacet) {
		// // It takes a lot of work for p to figure out there
		// // are no children for this prefix, so tell it now
		// final String name = leftFacet.getNameIfPossible();
		// if (name != null) {
		// perspective().putLettersOffsetsMap(name.toLowerCase(), new
		// LinkedHashMap<String, Interval<Perspective>>());
		// }
		// }
		final int cumTotalCountExclusiveLeft = leftFacet.cumTotalCountExclusive();
		final int visibleCount = rightFacet.cumTotalCountInclusive() - cumTotalCountExclusiveLeft;
		assert visibleCount >= 0 : leftFacet + " " + leftFacet.cumTotalCountExclusive() + " " + rightFacet + " "
				+ rightFacet.cumTotalCountExclusive() + " visibleCount=" + visibleCount;
		final boolean result = visibleCount != 0;
		if (result) {
			final double widthToCountRatio = visibleWidth / visibleCount;
			final double newLogicalWidth = widthToCountRatio * totalChildCount;
			final double newLeftEdge = cumTotalCountExclusiveLeft * widthToCountRatio;
			createZoomer(newLeftEdge, newLogicalWidth, lowerCasePrefix);
		} else {
			System.err.println("Warning: LetterLabeled.zoomInternal: " + this + " prefix=" + lowerCasePrefix
					+ " rangeForPrefix=" + rangeForPrefix + " has zero total count.");
		}
		return result;
	}

	/**
	 * @return [first, last] Perspectives whose names begin with prefix. null
	 *         means redraw will be called when it is computed.
	 */
	private @NonNull Interval<Perspective> getRangeForPrefixNow(final @NonNull String lowerCasePrefix) {
		final Query query = art().getQuery();
		query.insideLetterLabeledZoom++;
		// too much work to avoid "calling Servlet in mouse process"
		// error, and we do want immediate response to mouse gestures.
		final Interval<Perspective> rangeForPrefix = perspective().rangeForPrefix(lowerCasePrefix, null);
		assert rangeForPrefix != null;
		query.insideLetterLabeledZoom--;
		return rangeForPrefix;
	}

	/**
	 * There can only be one Zoomer at a time, zoomer.
	 */
	class Zoomer extends PInterpolatingActivity {

		final double goalLeftEdge;
		final double goalLogicalWidth;
		final @NonNull String goalPrefix;
		final double startLeftEdge = getLeftEdge();
		final double startLogicalWidth = getLogicalWidth();
		final int startTotalChildCount = totalChildCount;

		public Zoomer(final double _goalLeftEdge, final double _goalLogicalWidth,
				final @NonNull String lowerCasePrefix) {
			super(BungeeConstants.DATA_ANIMATION_MS, BungeeConstants.DATA_ANIMATION_STEP);
			goalLeftEdge = _goalLeftEdge;
			goalLogicalWidth = _goalLogicalWidth;
			goalPrefix = lowerCasePrefix;
			lastExplicitPrefix = lowerCasePrefix;
		}

		@Override
		public void activityFinished() {
			if (!maybeStopZoomer(startTotalChildCount)) {
				super.activityFinished();
				assert prefix().startsWith(goalPrefix) : this + "\n prefix()='" + prefix() + "', but expected prefix='"
						+ goalPrefix + "' goalLeftEdge=" + goalLeftEdge + " goalLogicalWidth=" + goalLogicalWidth + "\n"
						+ formatLabelXs();
				zoomer = null;
				lastExplicitPrefix = prefix();
			}
		}

		@Override
		public void setRelativeTargetValue(final float zeroToOne) {
			if (!maybeStopZoomer(startTotalChildCount)) {
				final double rawLeftEdge = UtilMath.interpolate(startLeftEdge, goalLeftEdge, zeroToOne);
				final double rawLogicalWidth = UtilMath.interpolate(startLogicalWidth, goalLogicalWidth, zeroToOne);
				try {
					setPVLogicalBounds(rawLeftEdge, rawLogicalWidth);
				} catch (final AssertionError e) {
					printAnimatingPanZoomError(zeroToOne, e);
				}
			}
		}

		private void printAnimatingPanZoomError(final float zeroToOne, final @Nullable AssertionError e) {
			final double rawLeftEdge = UtilMath.interpolate(startLeftEdge, goalLeftEdge, zeroToOne);
			final double rawLogicalWidth = UtilMath.interpolate(startLogicalWidth, goalLogicalWidth, zeroToOne);
			final FormattedTableBuilder align = new FormattedTableBuilder();
			align.addLine("", "Left", "Right", "Logical Width", "Left CumCount", "Right CumCount",
					"Logical Width CumCount");
			align.addLine("Start", startLeftEdge, startLeftEdge + visibleWidth, startLogicalWidth,
					totalChildCount * startLeftEdge / startLogicalWidth,
					totalChildCount * (startLeftEdge + visibleWidth) / startLogicalWidth, totalChildCount);
			align.addLine("Goal", goalLeftEdge, goalLeftEdge + visibleWidth, goalLogicalWidth,
					totalChildCount * goalLeftEdge / goalLogicalWidth,
					totalChildCount * (goalLeftEdge + visibleWidth) / goalLogicalWidth, totalChildCount);
			align.addLine("Current", rawLeftEdge, rawLeftEdge + visibleWidth, rawLogicalWidth,
					totalChildCount * rawLeftEdge / rawLogicalWidth,
					totalChildCount * (rawLeftEdge + visibleWidth) / rawLogicalWidth, totalChildCount);

			System.err.println("Warning: While LetterLabeled.Zoomer.setRelativeTargetValue " + LetterLabeled.this
					+ "\n zeroToOne=" + zeroToOne + " startTotalChildCount=" + startTotalChildCount
					+ " totalChildCount=" + totalChildCount + " caught AssertionError:\n");
			System.err.println(align.format());
			System.err.println(formatLabelXs());
			System.err.println(formatLetterOffsets());
			System.err.println("cumCountsInclusive=" + UtilString.valueOfDeep(cumCountsInclusive));
			if (e != null) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * NonNull iff zooming
	 */
	protected PActivity zoomer;

	private boolean isZooming() {
		return zoomer != null;
	}

	private void createZoomer(final double goalLeftEdge, final double goalLogicalWidth,
			final @NonNull String lowerCasePrefix) {
		stopZoomer();
		if (goalLeftEdge != getLeftEdge() || goalLogicalWidth != getLogicalWidth()) {
			assert goalLeftEdge >= 0.0 && goalLeftEdge + visibleWidth <= goalLogicalWidth + UtilMath.ABSOLUTE_SLOP
					&& Double.isFinite(goalLogicalWidth) : goalLeftEdge + "/" + goalLogicalWidth + " " + visibleWidth;
			assert totalChildCount > 0 : this + " " + totalChildCount;
			assert totalChildCount == perspective().getTotalChildTotalCount() : this + " " + totalChildCount + " "
					+ perspective().getTotalChildTotalCount();
			final double goalLogicalWperItem = goalLogicalWidth / totalChildCount;
			assert assertEdgesNearInt(goalLeftEdge, goalLogicalWidth, goalLogicalWperItem, totalChildCount);

			zoomer = new Zoomer(goalLeftEdge, goalLogicalWidth, lowerCasePrefix);
			final boolean result = parentPNode.addActivity(zoomer);
			assert result : zoomer;
		}
	}

	/**
	 * stopZoomer if !parentPNode.getVisible() or totalChildCount changes.
	 */
	boolean maybeStopZoomer(final int startTotalChildCount) {
		final boolean result = !parentPNode.getVisible() || totalChildCount != startTotalChildCount
				|| totalChildCount != perspective().getTotalChildTotalCount();
		if (result) {
			stopZoomer();
		}
		return result;
	}

	public void stopZoomer() {
		if (zoomer != null) {
			zoomer.terminate(PActivity.TERMINATE_WITHOUT_FINISHING);
			zoomer = null;
		}
	}

	void setPVLogicalBounds(final double rawLeftEdge, final double rawLogicalWidth) {
		final double[] intCountLogicalBounds = intCountLogicalBounds(rawLeftEdge, rawLogicalWidth, totalChildCount);
		final double intCountLeftEdge = intCountLogicalBounds[0];
		final double intCountLogicalWidth = intCountLogicalBounds[1];
		perspectiveViz().setLogicalBounds(intCountLeftEdge, intCountLogicalWidth);
	}

	@Override
	public String toString() {
		return UtilString.toString(this,
				"parent='" + parent + "' visibleW=" + visibleWidth + " " + maybeUninstantiatedPerspectiveViz());
	}

}
