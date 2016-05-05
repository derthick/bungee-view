/*

 Created on Mar 28, 2006

 Bungee View lets you search, browse, and data-mine an image collection.
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at
 mad@cs.cmu.edu,
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.client.viz.markup;

import static edu.cmu.cs.bungee.javaExtensions.Util.EXCLUDE_ACTION;

import java.awt.Color;
import java.awt.Paint;
import java.awt.event.InputEvent;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupSearchElement;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.query.markup.TopTagsPerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.client.viz.tagWall.PerspectiveViz;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.CheckBox;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * Represents a PerspectiveMarkupElement. Includes checkboxes and child
 * indicators. onCount and color are determined dynamically.
 */
public class PerspectiveMarkupAPText extends BungeeAPText implements FacetNode, RedrawCallback {

	private static final double CHECKBOX_SCALE = 0.5;
	private static final @NonNull FacetTextHandler FACET_TEXT_HANDLER = new FacetTextHandler();
	private static final @NonNull Queue<CheckBox> CHECKBOX_CACHE = new LinkedList<>();
	private static final @NonNull Queue<CheckBox> XBOX_CACHE = new LinkedList<>();

	/**
	 * trucate name to this width.
	 */
	public final double nameW;
	public final boolean showCheckBox;

	final boolean showChildIndicator;

	protected final @NonNull Perspective facet;

	/**
	 * This is only used by printUserAction; it has nothing to do with
	 * PerspectiveMarkupElementLocation and UserAction.
	 */
	private final @NonNull ReplayLocation replayLocation;
	private final boolean numFirst;
	private final int onCount;

	/**
	 * Only used by mayHideTransients. (And only false for PerspectiveList
	 * labels.)
	 */
	public boolean mayHideTransients = true; // NO_UCD (unused code)
	public double numW;

	/**
	 * Add spaces to fill nameW?
	 */
	private boolean padToNameW = false; // NO_UCD (use final)
	private CheckBox checkBox;
	private CheckBox xBox;

	/**
	 * Only called by RotatedFacetText, which keeps its own cache (via
	 * DefaultLabeleds LetterLabeled & LabeledLabels)
	 *
	 * constrainWidth defaults to true; constrainHeight and isWrap default to
	 * false. justification defaults to LEFT_ALIGNMENT.
	 */
	protected PerspectiveMarkupAPText(final @NonNull Perspective _facet,
			final @NonNull PerspectiveViz _perspectiveViz) {
		this(_facet, null, _perspectiveViz.art(), _perspectiveViz.numW(), _perspectiveViz.nameW(),
				/* numFirst */false, /* showChildIndicator */
				true, /* showCheckBox */true, UserAction.isLabeledLabelUnderline(_perspectiveViz, _facet, null),
				_facet.getOnCount(), /* padToNameW */
				true, ReplayLocation.BAR_LABEL);
		UserAction.isLabeledLabelUnderline(_perspectiveViz, _facet, this);
	}

	/**
	 * All constructors go through here.
	 *
	 * constrainWidth defaults to true; constrainHeight and isWrap default to
	 * false. justification defaults to LEFT_ALIGNMENT.
	 */
	protected PerspectiveMarkupAPText(final @NonNull Perspective perspective, @Nullable final String s,
			final @NonNull Bungee _art, final double _numW, final double _nameW, final boolean _numFirst,
			final boolean _showChildIndicator, final boolean _showCheckBox, final boolean _underline,
			final int _onCount, final boolean _padToNameW, final @NonNull ReplayLocation _replayLocation) {
		super(null, _art, null);
		assert !_showCheckBox || perspective.getParent() != null;
		facet = perspective;
		assert _nameW >= _art.lineH() : _nameW;
		nameW = _nameW;
		numW = _numW;
		padToNameW = _padToNameW;
		showChildIndicator = _showChildIndicator;
		showCheckBox = _showCheckBox;
		onCount = _onCount;

		// underline = false for FacetTreeViz's FacetTypes, for instance
		setUnderline(_underline, YesNoMaybe.NO);
		replayLocation = _replayLocation;

		numFirst = _numFirst;
		// dontHideTransients = false;
		setConstrainHeightToTextHeight(false);
		setConstrainWidthToTextWidth(true);
		setWrap(false);
		setHeight(art.lineH());

		if (s != null) {
			// s is not always a default, e.g. MarkupViz wrapping.
			maybeSetText(s);
		} else {
			maybeSetText();
		}
		updateCheckboxes();
		addInputEventListener(FACET_TEXT_HANDLER);
	}

	@Override
	public void redrawCallback() {
		maybeSetText();
	}

	/**
	 * @return whether text changed. queues to nameGetter if uncached.
	 */
	boolean maybeSetText() {
		boolean result = false;
		if (facet.getNameOrDefaultAndCallback(null, this) != null) {
			final String s = art.computeText(facet.getMarkupElement(), numW, nameW, numFirst, showChildIndicator,
					showCheckBox, padToNameW, this, onCount);
			assert s != null;
			result = maybeSetText(s);
		}
		return result;
	}

	@Override
	public boolean maybeSetText(final String _text) {
		final boolean result = super.maybeSetText(_text);
		if (result) {
			assert assertFacetLabelWidthEqualsPredicted(this, facet.getMarkupElement(), nameW, shouldShowCheckBox(),
					showChildIndicator, art);
			updateHighlighting();
		}
		return result;
	}

	@Override
	public void printUserAction(final int modifiers) {
		art.printUserAction(replayLocation, facet, modifiers);
	}

	// Called by FacetTreeViz
	public static @NonNull APText getFacetText(final @NonNull MarkupElement treeObject, final @NonNull Bungee art,
			final double nameW, final boolean _showCheckBox, final boolean _underline, final double x, final double y,
			final @NonNull ReplayLocation replayLocation) {
		assert treeObject instanceof PerspectiveMarkupElement : treeObject;
		final APText result = getFacetText(treeObject, null, art, -1.0, nameW, false, false, _showCheckBox, _underline,
				1, false, null, x, y, replayLocation);
		return result;
	}

	// Called by MarkupViz
	static @NonNull APText getFacetText(final @Nullable MarkupElement treeObject, final String s,
			final @NonNull Bungee art, final boolean isUnderline, final @NonNull RedrawCallback _redraw, final double x,
			final double y, final Paint paint, final @NonNull ReplayLocation replayLocation) {
		final APText result = getFacetText(treeObject, s, art, -1.0, Double.POSITIVE_INFINITY, false, false, false,
				isUnderline, 1, false, _redraw, x, y, replayLocation);
		if (paint != null && !(treeObject instanceof PerspectiveMarkupElement)) {
			result.setTextPaint(paint);
		}
		return result;
	}

	// Called by PerspectiveList
	public static @NonNull APText getFacetText(final @NonNull MarkupElement treeObject, final @NonNull Bungee art,
			final double _numW, final double _nameW, final boolean _underline, final int onCount,
			final @NonNull RedrawCallback _redraw, final double x, final double y,
			final @NonNull ReplayLocation replayLocation) {
		assert treeObject instanceof PerspectiveMarkupElement : treeObject;
		final APText result = getFacetText(treeObject, null, art, _numW, _nameW, true, true, true, _underline, onCount,
				false, _redraw, x, y, replayLocation);
		return result;
	}

	/**
	 * All getFacetText's go through this.
	 *
	 * @param treeObject
	 *            the Perspective, Cluster, or String represented
	 * @param _numW
	 *            the width to make the onCount, or -1 to not show counts.
	 * @param _nameW
	 *            the width to make the name.
	 * @param _showChildIndicator
	 *            append a down arrow?
	 * @param _showCheckBox
	 *            prepend check- and x- boxes?
	 * @param _underline
	 *            underline the text?
	 * @param onCount
	 *            the number to prepend.
	 * @param _redraw
	 *            call this Redrawer when the treeObject's name becomes known.
	 *            Defaults to this.
	 * @param x
	 * @param y
	 * @return a single-line FacetText whose width varies with its content. i.e.
	 *
	 *         setConstrainWidthToTextWidth(true);
	 *         setConstrainHeightToTextHeight(false); setWrap(false);
	 *         justification defaults to LEFT_ALIGNMENT.
	 */
	public static @NonNull APText getFacetText(final @Nullable MarkupElement treeObject, final @Nullable String s,
			final @NonNull Bungee art, final double _numW, final double _nameW, final boolean _numFirst,
			final boolean _showChildIndicator, final boolean _showCheckBox, final boolean _underline, final int onCount,
			final boolean padToNameW, final @Nullable RedrawCallback _redraw, final double x, final double y,
			final @NonNull ReplayLocation replayLocation) {
		assert _nameW > 0.0 && (_nameW == (int) _nameW || (Double.isInfinite(_nameW) && !padToNameW)) : _nameW;
		assert !_showCheckBox || art.getShowCheckboxes() || art.isReplaying() : treeObject;
		APText result;
		if (treeObject instanceof PerspectiveMarkupElement) {
			final PerspectiveMarkupElement pme = (PerspectiveMarkupElement) treeObject;
			final Perspective perspective = pme.perspective;
			assert !_showCheckBox || perspective.getParent() != null;
			if (pme instanceof TopTagsPerspectiveMarkupElement) {
				result = TopTagsPerspectiveMarkupAPText.getInstance(perspective, s, art, _numW, _numFirst,
						_showChildIndicator, _showCheckBox, _underline, onCount, padToNameW, _redraw, replayLocation);
			} else {
				result = new PerspectiveMarkupAPText(perspective, s, art, _numW, _nameW, _numFirst, _showChildIndicator,
						_showCheckBox, _underline, onCount, padToNameW, replayLocation);
			}
			assert assertFacetLabelWidthEqualsPredicted(result, pme, _nameW, _showCheckBox, _showChildIndicator, art);
		} else if (treeObject instanceof MarkupSearchElement) {
			result = new MarkupSearchText(treeObject, art);
		} else {
			result = art.oneLineLabel();
			result.maybeSetText(s);
		}
		result.setOffset(x, y);
		return result;
	}

	/**
	 * This is rarely applicable, as text.equals(treeObject.getName()) would be
	 * unusual (no checkboxes, child indicators, padding, or truncation)
	 */
	private static boolean assertFacetLabelWidthEqualsPredicted(final APText aPText,
			final PerspectiveMarkupElement treeObject, final double nameW, final boolean realShowCheckBox,
			final boolean showChildIndicator, final Bungee art) {
		final String labelText = aPText.getText();
		if (treeObject != null && labelText != null) {
			// art.getFacetStringWidth (via textMeasurer.getAdvanceBetween)
			// ignores trailing whitespace, so make sure there isn't any.
			final String text = UtilString.removeSuffix(labelText, " ");
			if (text.equals(treeObject.getName())) {
				aPText.recomputeLayout();
				final double width = aPText.getWidth();
				final double ceilW = Math.ceil(width);
				final boolean realShowChildIndicator = showChildIndicator && treeObject.isEffectiveChildren();
				final double fsw = art.getStringWidth(text, realShowChildIndicator, realShowCheckBox);
				assert UtilMath.approxEquals(ceilW, fsw) || UtilMath.approxEquals(ceilW + 1.0, fsw) : "\ntreeObject="
						+ treeObject + " '" + text + "'\nhas w=" + width + " (font =" + aPText.getFont()
						+ "; width of string alone is " + art.getStringWidth(text)
						+ "), but the computed width with showCheckBox=" + realShowCheckBox
						+ " and realShowChildIndicator=" + realShowChildIndicator + " is " + fsw + ".\n(checkbox width="
						+ art.checkBoxWidth() + "; child indicator width=" + art.childIndicatorWidth() + "; nameW="
						+ nameW + "; current font=" + art.getCurrentFont() + " isConstrainWidthToTextWidth="
						+ aPText.isConstrainWidthToTextWidth() + "). ";
			}
		}
		return true;
	}

	final boolean shouldShowCheckBox() {
		return showCheckBox && art().getShowCheckboxes() && facet.getParent() != null;
	}

	public final boolean isShowingCheckBox() {
		return checkBox != null;
	}

	@Override
	public Perspective getFacet() {
		return facet;
	}

	@NonNull
	MarkupElement treeObject() {
		assert facet != null;
		final MarkupElement result = facet.getMarkupElement();
		return result;
	}

	@Override
	public boolean brush(final boolean state, @SuppressWarnings("unused") final PInputEvent e) {
		art().brushFacet(state ? getFacet() : null);
		return false;
	}

	public boolean updateHighlighting() {
		return updateHighlighting(art.getQuery().version());
	}

	/**
	 * Update text (foreground) color
	 */
	public boolean updateHighlighting(final int queryVersion) {
		return updateHighlighting(queryVersion, YesNoMaybe.NO);
	}

	@Override
	public boolean updateHighlighting(final int queryVersion, @SuppressWarnings("unused") final YesNoMaybe isRerender) {
		final Color facetColor = facetColor(queryVersion);

		final boolean result = facetColor != getTextPaint();
		if (result) {
			setTextPaint(facetColor);
			// maybeIncfGGG();
		}
		return result;
	}

	/**
	 * Update checkbox state and FG/BG colors.
	 */
	public void queryValidRedraw(final int queryVersion, final @Nullable Pattern textSearchPattern) {
		updateCheckboxes();
		updateSearchHighlighting(textSearchPattern, YesNoMaybe.MAYBE);
		updateHighlighting(queryVersion);
	}

	/**
	 * Update state for both checkboxes
	 */
	protected void updateCheckboxes() {
		if (shouldShowCheckBox()) {
			if (checkBox == null) {
				final double edgeLength = art.lineH();
				final double y = Math.rint(edgeLength * (1.0 - CHECKBOX_SCALE) / 2.0);

				xBox = getCheckBox(false, 0.0, y, edgeLength, BungeeConstants.X_COLOR, BungeeConstants.XBOX_COLOR);

				checkBox = getCheckBox(true, xBox.getMaxX() + art.buttonMargin() / 3.0, y, edgeLength,
						BungeeConstants.CHECKMARK_COLOR, BungeeConstants.CHECKBOX_COLOR);
			}
			checkBox.setState(facet.isRestriction(true));
			xBox.setState(facet.isRestriction(false));
		}
	}

	protected Color facetColor(final int queryVersion) {
		return art().facetColor(facet, queryVersion);
	}

	private @NonNull CheckBox getCheckBox(final boolean polarity, final double x, final double y,
			final double edgeLength, final @NonNull Color fg, final @NonNull Color bg) {
		CheckBox result = polarity ? CHECKBOX_CACHE.poll() : XBOX_CACHE.poll();
		if (result == null) {
			result = new CheckBox(polarity, x, y, edgeLength, null, fg, bg);
			result.removeInputListeners();
			result.addInputEventListener(FACET_TEXT_HANDLER);
			result.setScale(CHECKBOX_SCALE);
		}
		assert result.getParent() == null : getParent();
		addChild(result);
		return result;
	}

	public static void removeAllChildrenAndReclaimCheckboxes(final @NonNull PNode parent) {
		final int childrenCount = parent.getChildrenCount();
		for (int i = 0; i < childrenCount; i++) {
			final PNode child = parent.getChild(i);
			if (child instanceof PerspectiveMarkupAPText) {
				((PerspectiveMarkupAPText) child).cacheCheckBoxes();
			}
		}
		parent.removeAllChildren();
	}

	protected void cacheCheckBoxes() {
		if (checkBox != null) {
			cacheCheckBox(checkBox);
			checkBox = null;
		}
		if (xBox != null) {
			cacheCheckBox(xBox);
			xBox = null;
		}
	}

	static void cacheCheckBox(final @NonNull CheckBox _checkBox) {
		_checkBox.removeFromParent();
		final Queue<CheckBox> cache = _checkBox.isRequire() ? CHECKBOX_CACHE : XBOX_CACHE;
		final boolean isAdded = cache.offer(_checkBox);
		assert isAdded;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, treeObject() + " '" + getText() + "'");
	}

	@Override
	public @NonNull Bungee art() {
		return art;
	}

	@Override
	public int getModifiersEx(final @NonNull PInputEvent e) {
		// If over check boxes, and they're already checked, don't add
		// CTRL_DOWN_MASK/EXCLUDE_ACTION. That way, result will be UNSELECT,
		// rather than a NO-OP select of the current state.

		int result = art.getModifiersEx(e);
		final double x = e.getPositionRelativeTo(this).getX();
		if (isShowingCheckBox() && x < checkBox.getMaxX()) {
			if (x > xBox.getMaxX()) {
				// clicked checkBox
				if (!facet.isRestriction(true)) {
					// not already checked
					result |= InputEvent.CTRL_DOWN_MASK;
				}
			} else if (!facet.isRestriction(false)) {
				// clicked xBox and not already checked
				result |= EXCLUDE_ACTION;
				result &= ~InputEvent.CTRL_DOWN_MASK;
			}
		}
		assert result >= 0;
		return result;
	}

	public @NonNull ReplayLocation getReplayLocation() {
		return replayLocation;
	}

	@Override
	public boolean isGGG() {
		return facet.getNameNow().equals(gggName);
	}

}

/**
 * For nodes with checkboxes
 */
class FacetTextHandler extends BungeeClickHandler<BungeeAPText> {

	public FacetTextHandler() {
		super(BungeeAPText.class);
	}

	@Override
	public boolean moved(final @NonNull BungeeAPText node, final PInputEvent e) {
		return enter(node, e);
	}

}
