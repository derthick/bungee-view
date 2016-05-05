/*
 *
 * Created on Mar 11, 2005
 *
 * Bungee View lets you search, browse, and data-mine an image
 * collection. Copyright (C) 2006 Mark Derthick
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. See gpl.html.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * You may also contact the author at mad@cs.cmu.edu, or at Mark Derthick
 * Carnegie-Mellon University Human-Computer Interaction Institute Pittsburgh,
 * PA 15213
 *
 */

package edu.cmu.cs.bungee.client.viz.tagWall;

import static edu.cmu.cs.bungee.javaExtensions.Util.assertMouseProcess;
import static edu.cmu.cs.bungee.javaExtensions.Util.isShiftDown;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;

import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyContainer;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;

final public class Rank extends LazyContainer implements RedrawCallback, Comparable<Rank> {

	/**
	 * Margin between multiple PVs in a rank
	 */
	private static final double MAX_PV_MARGIN_W = 5.0;

	/**
	 * The Rank immediately above this one, whether a logical parent or not.
	 */
	final @Nullable Rank parentRank;

	final @NonNull TagWall tagWall;

	/**
	 * Only modified by addPV() and removeUnsynchronizePerspectives()
	 */
	final @NonNull SortedSet<PerspectiveViz> pVizs = new TreeSet<>();

	boolean isConnected = false;

	/**
	 * y coordinate for numeric labels; depends on maxCount. ????????
	 *
	 * if < 0, don't show count; if > 0 pad onCount to exactly this width. If
	 * numFirst, add two space characters between onCount and name; if !numFirst
	 * assume numW takes care of separating them.
	 */
	double numW;

	/**
	 * if > 0, trucate name to this width.
	 */
	double nameW = Double.POSITIVE_INFINITY;

	/**
	 * -1 means not cached. Always use getter, totalChildTotalCount(), which
	 * recomputes when needed.
	 */
	private int totalPVpTotalCount = -1;

	// Used for animation
	private double startYOffset;
	private double goalYOffset;
	private @NonNull RankComponentHeights startHeights = RankComponentHeights.ZERO_RANK_COMPONENT_HEIGHTS;
	private @NonNull RankComponentHeights goalHeights = RankComponentHeights.ZERO_RANK_COMPONENT_HEIGHTS;
	protected @NonNull RankComponentHeights componentHeights = RankComponentHeights.ZERO_RANK_COMPONENT_HEIGHTS;

	/**
	 * Only called by TagWall.synchronizePerspectives(), via
	 * TagWall.rankForPerspective()
	 */
	Rank(final @NonNull TagWall _tagWall, final @Nullable Rank _parent) {
		parentRank = _parent;
		tagWall = _tagWall;
	}

	/**
	 * Only called by TagWall.synchronizePerspectives()
	 */
	void addPV(final @NonNull Perspective pvP) {
		assert assertMouseProcess();
		final PerspectiveViz pv = new PerspectiveViz(pvP, this);
		final PerspectiveViz parentPV = pv.parentPV;
		if (parentPV != null && nPVs() == 0) {
			assert parentRank != null;
			setYoffset(parentRank.getMaxY());
		}
		// System.out
		// .println("Rank.addPV " + this + " " + getBounds() + " " + pvP + "\n
		// parentRank=" + parentRank + " "
		// + (parentRank != null
		// ? parentRank.getBounds() + " parentRank.getMaxY()=" +
		// Util.nonNull(parentRank).getMaxY()
		// : ""));

		synchronized (pVizs) {
			pVizs.add(pv);
			addChild(pv);
			for (final PerspectiveViz _pv : pVizs) {
				_pv.createOrDeleteRankLabel();
			}
		}
		// must setFeatures after pVizs updated (so maxCount() is right)
		pv.setFeatures();
		decacheName();
	}

	@Override
	public void redrawCallback() {
		validateInternal();
	}

	void validate(final double _w, final double _h) {
		setWidthHeight(_w, _h);
		validateInternal();
	}

	private void validateInternal() {
		final int nPVs = nPVs();
		if (getWidth() > 0.0 && nPVs > 0) {
			final double availablePVw = tagWall.availablePVw();
			final int nPVmargins = nPVs - 1;
			// Don't let margins take up more than one fifth the width
			final double pvMarginW = nPVmargins == 0 ? 0.0 : Math.min(availablePVw / (5 * nPVmargins), MAX_PV_MARGIN_W);
			final double barsW = availablePVw - pvMarginW * nPVmargins;
			assert barsW > 0.0 : " w=" + getWidth() + " tagWall.availablePVw()=" + tagWall.availablePVw()
					+ " nPerspectives=" + nPVs;
			final double barWidthPerItem = barsW / totalPVpTotalCount();
			assert barWidthPerItem > 0.0 : "I think this should be an if, because totalChildTotalCount() may be -1";
			final double height = getHeight();
			double xOffset = tagWall.internalXmargin() - tagWall.rankLabelX();
			for (final PerspectiveViz pv : pVizs) {
				final Perspective p = pv.p;
				assert p.isDisplayed() : p;
				// System.out.println("Rank.validateInternal " + pv + "
				// totalPVpTotalCount=" + totalPVpTotalCount()
				// + " barsW=" + barsW + " barWidthPerItem=" + barWidthPerItem +
				// " p.getTotalCount()="
				// + p.getTotalCount() + " barWidthPerItem*getTotalCount="
				// + (barWidthPerItem * p.getTotalCount()));
				final int childW = Math.max(1, UtilMath.roundToInt(barWidthPerItem * p.getTotalCount()));
				pv.validate(childW, height);
				pv.setXoffset(xOffset);
				xOffset += childW + pvMarginW;
			}
		}
	}

	/**
	 * @return always >= 0
	 */
	private int totalPVpTotalCount() {
		if (totalPVpTotalCount < 0) {
			int _totalPVpTotalCount = 0;
			final int queryTotalCount = query().getTotalCount();
			for (final PerspectiveViz pv : pVizs) {
				final int pvTotalCount = pv.p.getTotalCount();
				assert assertInRange(pvTotalCount, 0, queryTotalCount);
				_totalPVpTotalCount += pvTotalCount;
			}
			totalPVpTotalCount = _totalPVpTotalCount;
			setNameAndNumW();
		}
		return totalPVpTotalCount;
	}

	PerspectiveViz firstPV() {
		return pVizs.isEmpty() ? null : pVizs.first();
	}

	private int nPVs() {
		return pVizs.size();
	}

	void setFeatures() {
		for (final PerspectiveViz pv : pVizs) {
			pv.setFeatures();
		}
	}

	void setFont(final @NonNull Font font) {
		setNameAndNumW();
		for (final PerspectiveViz pv : pVizs) {
			pv.setFont(font);
		}
	}

	void setParentPNodesVisible(final boolean isVisible) {
		for (final PerspectiveViz pv : pVizs) {
			pv.setParentPNodesVisible(isVisible);
		}
	}

	/**
	 * Should call this on change in: pVizs, restrictData, fontSize, and
	 * rankComponentHeights.
	 *
	 * @return whether we need animated (nameW or numW changed, or
	 *         !componentHeights.equals(goalHeights)).
	 */
	boolean setNameAndNumW() {
		final double newNumW = art().numWidth(maxPVmaxChildTotalCount());
		final double newNameW = tagWall.availableNameNnumStringW() - newNumW;
		// assert newNameW >= art().minNameWidth() : this + " newNameW=" +
		// newNameW + " tagWall.selectedLabelH="
		// + tagWall.selectedRankLabelsH() + " textAngleSine=" +
		// RotatedFacetText.TEXT_ANGLE_SINE + " newNumW="
		// + newNumW + " getWidth=" + getWidth() + " getFontSize=" +
		// art().getFontSize() + " availableW="
		// + availableW;
		final boolean isChanged = newNumW != numW || newNameW != nameW;
		if (isChanged) {
			numW = newNumW;
			nameW = newNameW;
			for (final PerspectiveViz pv : pVizs) {
				pv.setNameAndNumW();
			}
		}
		return isChanged || !componentHeights.equals(goalHeights);
	}

	/**
	 * @return the totalCount of the perspective in any of our PVs with the
	 *         highest totalCount.
	 */
	int maxPVmaxChildTotalCount() {
		int result = 0;
		for (final PerspectiveViz pv : pVizs) {
			result = Math.max(result, pv.p.getMaxChildTotalCount());
		}
		return result;
	}

	/**
	 * Invalidate counts
	 */
	void restrictData() {
		for (final PerspectiveViz pv : pVizs) {
			pv.restrictData();
		}
		decacheName();
	}

	void queryValidRedraw(final int queryVersion, final @Nullable Pattern textSearchPattern) {
		// Use a copy, because pv.queryValidRedraw() (via
		// createOrDeleteOrRedrawLabeleds() via
		// maybeUndisplay()) can remove pv from pVizs (and rank from
		// TagWall.ranks)

		// If a leaf child of a pvP is toggled, updateRankLabelContent() may not
		// be called elsewhere.
		updateRankLabelContent();
		final List<PerspectiveViz> pVizsCopy = new ArrayList<>(pVizs);
		for (final PerspectiveViz pv : pVizsCopy) {
			pv.queryValidRedraw(queryVersion, textSearchPattern);
		}
	}

	/**
	 * Only called by TagWall.barHeightAnimator
	 *
	 * Animate Bars
	 */
	void setRelativeTargetValue(final double zeroToOne) {
		for (final PerspectiveViz pv : pVizs) {
			pv.setRelativeTargetValue(zeroToOne);
		}
	}

	/**
	 * @return [global] height of area containing rankLabel, hotZone, and bars.
	 */
	double frontH() {
		return componentHeights.frontH();
	}

	double foldH() {
		return componentHeights.foldH();
	}

	/**
	 * @return height of RotatedFacetText labels area.
	 */
	double labelsH() {
		return componentHeights.labelsH();
	}

	double totalH() {
		return componentHeights.totalH();
	}

	double labelsYScale() {
		return labelsH() / tagWall.selectedRankLabelsH();
	}

	double lettersYScale() {
		return foldH() / tagWall.selectedRankFoldH();
	}

	/**
	 * Update name and restrictionName labels
	 *
	 * @param changedFacets
	 *            facets for which highlighting has changed
	 * @param queryVersion
	 * @return whether highlighting changed
	 */
	boolean updateHighlighting(final @NonNull Set<Perspective> changedFacets, final int queryVersion) {
		boolean result = false;
		for (final PerspectiveViz pv : pVizs) {
			if (pv.updateHighlighting(changedFacets, queryVersion)) {
				result = true;
			}
		}
		return result;
	}

	void setConnected(final boolean connected) {
		assert connected != isConnected;
		isConnected = connected;
		updateRankLabelContent();
		for (final PerspectiveViz pv : pVizs) {
			pv.setConnected(connected);
		}
	}

	/**
	 * Update the content, set underlines, and
	 * pVizs.first().setRankLabelContent(content). content includes restrictions
	 * that are negative or on leaf perspectives, indented with an arrow, after
	 * a newline.
	 */
	void updateRankLabelContent() {
		if (!pVizs.isEmpty()) {
			final List<Perspective> pvPs = getPVps();
			Markup content = DefaultMarkup.newMarkup(pvPs);
			content.addConnectors(DefaultMarkup.CONNECTOR_OR);
			final Perspective firstPerspective = pvPs.get(0);
			final String indentPrefix = firstPerspective.namePrefix();
			if (indentPrefix.length() > 0) {
				content.add(0, MarkupStringElement.getElement(indentPrefix));
			}
			content = content.compile(query().getGenericObjectLabel(false));

			// determine whether initial label should be clickable
			final int start = (art().getShowTagLists() || !isConnected()) ? 0
					: content.indexOf(DefaultMarkup.NEWLINE_TAG);
			content.underline(start);
			pVizs.first().setRankLabelContent(content);
		}
	}

	public boolean isConnected() {
		return isConnected;
	}

	/**
	 * @return pvPs for all pVizs.
	 */
	private @NonNull List<Perspective> getPVps() {
		final List<Perspective> result = new ArrayList<>(nPVs());
		for (final PerspectiveViz pv : pVizs) {
			result.add(pv.p);
		}
		return result;
	}

	private Bungee art() {
		return tagWall.art;
	}

	private Query query() {
		return tagWall.query;
	}

	/**
	 * @return whether this Rank needs animating. False if it is already at
	 *         _goalYOffset/_goalHeights.
	 */
	boolean prepareAnimation(final double _goalYOffset, final @NonNull RankComponentHeights _goalHeights) {
		goalHeights = _goalHeights;
		goalYOffset = _goalYOffset;
		final boolean isInitialDraw = getYOffset() == 0.0;
		if (isInitialDraw // && firstPerspective().isTopLevel()
		) {
			// It looks funky for us to animate initially.
			componentHeights = goalHeights;
			setHeight(goalHeights.frontH());
			setYoffset(parentRank != null ? parentRank.getMaxY() + tagWall.marginH() : goalYOffset);
			// setYoffset(goalYOffset);
		}
		startHeights = componentHeights;
		startYOffset = getYOffset();

		for (final PerspectiveViz pv : pVizs) {
			pv.prepareAnimation();
		}
		final boolean result = _goalYOffset != startYOffset || !_goalHeights.equals(startHeights);
		// System.out.println(
		// "Rank.prepareAnimation " + this + "\n start/goalYOffset: " +
		// startYOffset + " => " + goalYOffset
		// + " getHeight=" + getHeight() + "\n start/goalHeights: " +
		// startHeights + " => " + goalHeights);
		return result;
	}

	/**
	 * Only called by TagWall.animateRankComponentHeights() [first step] or its
	 * setRelativeTargetValue() method [remaining steps].
	 */
	void animateRank(final float zeroToOne) {
		setYoffset(Math.rint(lerp(zeroToOne, startYOffset, goalYOffset)));

		final RankComponentHeights prevComponentHeights = componentHeights;
		componentHeights = RankComponentHeights.lerp(zeroToOne, startHeights, goalHeights);
		if (!componentHeights.equals(prevComponentHeights)) {
			setHeight(Math.rint(componentHeights.totalH()));
			for (final PerspectiveViz pv : pVizs) {
				pv.animate(zeroToOne);
			}
		} else {
			for (final PerspectiveViz pv : pVizs) {
				pv.layoutLightBeam();
			}
		}
	}

	@Override
	public String toString() {
		return UtilString.toString(this, pVizs.isEmpty() ? "[] (parentRank=" + parentRank + ")" : pVizs.toString());
	}

	/**
	 * Can be called by mouse click on LetterLabeledAPText
	 *
	 * @param pvP
	 *            zoom within this pvP, or any pvP if null.
	 *
	 * @return whether any zooming occurred
	 */
	boolean zoomTo(final char lowerCaseSuffix, @Nullable final Perspective pvP) {
		for (final PerspectiveViz pv : pVizs) {
			if ((pvP == null || pvP == pv.p) && pv.zoomTo(lowerCaseSuffix)) {
				return true;
			}
		}
		System.err.println("Rank.zoomTo failed: rank=" + this + " pvP=" + pvP + " suffix='" + lowerCaseSuffix
				+ "' isMouseEvent=" + false + "\n pVizs=" + pVizs);
		return false;
	}

	@Nullable
	PerspectiveViz findPerspectiveViz(final @NonNull String facetName) { // NO_UCD
		// (unused code)
		for (final PerspectiveViz pv : pVizs) {
			if (pv.p.getName().equals(facetName)) {
				return pv;
			}
		}
		return null;
	}

	/**
	 * @return Whether this Rank has a PV where PV.p==pvP
	 */
	public boolean hasPVp(final @Nullable Perspective pvP) {
		for (final PerspectiveViz pv : pVizs) {
			if (pvP == pv.p) {
				return true;
			}
		}
		return false;
	}

	@NonNull
	public Perspective firstPerspective() {
		return pVizs.first().p;
	}

	/**
	 * @param queryPerspectives
	 *            - Perspectives whose PVs will remain displayed.
	 * @return whether nPVs() > 0
	 */
	boolean removeUnsynchronizePerspectives(final @NonNull Collection<Perspective> queryPerspectives) {
		boolean mustDecache = false;
		synchronized (pVizs) {
			for (final Iterator<PerspectiveViz> it = pVizs.iterator(); it.hasNext();) {
				final PerspectiveViz pv = it.next();
				final Perspective pvP = pv.p;
				if (!queryPerspectives.contains(pvP)) {
					if (!tagWall.perspectiveList.isHidden() && tagWall.listedPerspective() == pvP) {
						tagWall.hidePerspectiveList();
					}
					mustDecache = true;
					it.remove();
					removeChild(pv);
				}
			}
		}
		final boolean result = nPVs() > 0;
		if (mustDecache && result) {
			for (final PerspectiveViz _pv : pVizs) {
				_pv.createOrDeleteRankLabel();
			}
			decacheName();
		}
		return result;
	}

	private void decacheName() {
		totalPVpTotalCount = -1;
		updateRankLabelContent();
		validateInternal();
	}

	// Cases:
	//
	// If already at end, return null and art will warn about no-op.
	//
	// ...Required:
	//
	// ......No control or shift: go to next facet which will have non-zero
	// count when selected. If found, select it and deselect other
	// requireds. (For Home/End, go to that end and search backwards.)
	//
	// ......Control: Same, but don't deselect.
	//
	// ......Shift or control+shift: Don't deselect. Range select from
	// arrowFocus. (For Home/End, range select to end, even if no non-zero
	// counts.)
	/**
	 * fromFacet is the first child to examine. It will normally be adjacent to
	 * arrowFocus; however for isControlA and non-Shift Home/End it will be the
	 * first/last child of the first/last PV, and direction will point the
	 * opposite way.
	 *
	 * Proceed in direction until you find an unselected or excluded* facet
	 * whose onCount would increase from zero. Stop there, unless isControlA or
	 * Shift+Home/End. The last onCount-increasin facet becomes the
	 * newArrowFocus. If isShiftDown or isControlA, TagWall.toggleFacet will
	 * select all intermediate facets as well.
	 *
	 * *currently TagWall.toggleFacet doesn't toggle excluded facets all the way
	 * up to required. 2016/02
	 *
	 * For non-sequential sibling PVs, it skips over the missing ones (as well
	 * as childless sibling p's without PVs). It would be easy to modify
	 * nextChild to include them. With shift- art.toggleFacet will select them.
	 *
	 * @param modifiers
	 *
	 *            control Only relevant for control-A;
	 *
	 *            shift Select intermediate facets;
	 *
	 *            exclude Not currently allowed.
	 *
	 * @return newArrowFocus, or null if nothing is selectable.
	 */
	@Nullable
	public Perspective handleArrowForAllPVs(final int keyCode, int modifiers) {
		final Perspective arrowFocus = art().getArrowFocus();
		assert arrowFocus != null;
		assert arrowFocus.isRestriction(true) && !Util.isExcludeAction(modifiers) : arrowFocus.path()
				+ Util.printModifiersEx(modifiers);
		final boolean isControlA = MyInputEventHandler.isControlA(keyCode, modifiers);
		final boolean isContinueToEnd = isControlA || (isHomeOrEnd(keyCode) && isShiftDown(modifiers));

		// Treat isControlA separately, as direction() barfs on keyCode=A
		int direction = isControlA ? +1 : direction(keyCode);
		Perspective fromFacet = nextChild(arrowFocus, direction);
		if (isControlA) {
			fromFacet = pVizs.first().p.getRawNthChild(0);
			modifiers = InputEvent.SHIFT_DOWN_MASK;
		} else if (isHomeOrEnd(keyCode) && !isShiftDown(modifiers)) {
			// Want the last selectable facet in direction. Start at desired
			// end, and look backward;
			direction = -direction;
			final Perspective fromFacetParent = (direction > 0 ? pVizs.first() : pVizs.last()).p;
			fromFacet = startChild(fromFacetParent, direction);
		}
		return fromFacet == null ? null
				: handleArrowForAllPVsInternal(fromFacet, direction, isContinueToEnd, modifiers);
	}

	/**
	 * Only called by handleArrowForAllPVs()
	 *
	 * @return newArrowFocus and select facets in range [fromFacet,
	 *         newArrowFocus], or null if nothing is selectable.
	 */
	private @Nullable Perspective handleArrowForAllPVsInternal(final @NonNull Perspective fromFacet,
			final int direction, final boolean isContinueToEnd, final int modifiers) {
		assert hasPVp(fromFacet.getParent()) : this + " " + fromFacet.path();
		Perspective newArrowFocus = null;
		for (Perspective child = fromFacet; child != null; child = nextChild(child, direction)) {
			if (selectedOnCount(child) > child.getOnCount()) {
				assert !child.isRestriction(true) : child.path(true, true);
				newArrowFocus = child;
				if (!isContinueToEnd) {
					break;
				}
			}
		}
		if (newArrowFocus != null) {
			tagWall.toggleFacet(fromFacet, newArrowFocus, modifiers);
		}
		return newArrowFocus;
	}

	/**
	 * @return The onCount that would result from selecting child, if known, or
	 *         1 if query is not valid.
	 */
	private int selectedOnCount(final @NonNull Perspective child) {
		if (query().isQueryValid()) {
			return tagWall.perspectiveList.selectedOnCount(child, null);
		} else {
			return 1;
		}
	}

	/**
	 * @return next child in direction, going to the next PV if necessary
	 */
	private @Nullable Perspective nextChild(final @NonNull Perspective child, final int direction) {
		Perspective result = direction > 0 ? child.next() : child.previous();
		if (result == null) {
			final Perspective parent = child.getParent();
			assert parent != null : child;
			final PerspectiveViz pv = lookupPV(parent);
			final PerspectiveViz nextPV = nextPV(pv, direction);
			if (nextPV != null) {
				final Perspective pvP = nextPV.p;
				result = startChild(pvP, direction);
			}
		}
		return result;
	}

	/**
	 * @return pv in pVizs whose p = pvP, if any
	 */
	@Nullable
	PerspectiveViz lookupPV(final @NonNull Perspective pvP) {
		for (final PerspectiveViz pv : pVizs) {
			if (pv.p == pvP) {
				return pv;
			}
		}
		return null;
	}

	private @Nullable PerspectiveViz nextPV(final PerspectiveViz pv, final int direction) {
		PerspectiveViz prev = null;
		for (final PerspectiveViz _pv : pVizs) {
			if (_pv == pv && direction < 0) {
				return prev;
			} else if (prev == pv && direction > 0) {
				return _pv;
			} else {
				prev = _pv;
			}
		}
		return null;
	}

	/**
	 * @return the index of the child from which you can iterate over all
	 *         children in direction . (0 or nChildren - 1).
	 */
	private static @NonNull Perspective startChild(final Perspective parent, final int direction) {
		final int whichChild = direction > 0 ? 0 : parent.nChildrenRaw() - 1;
		return parent.getRawNthChild(whichChild);
	}

	/**
	 * @return -1 or +1
	 */
	private static int direction(final int keyCode) {
		int delta = 0;
		switch (keyCode) {
		case KeyEvent.VK_KP_LEFT:
		case KeyEvent.VK_LEFT:
		case KeyEvent.VK_KP_UP:
		case KeyEvent.VK_UP:
		case KeyEvent.VK_HOME:
			delta = -1;
			break;

		case KeyEvent.VK_KP_RIGHT:
		case KeyEvent.VK_RIGHT:
		case KeyEvent.VK_KP_DOWN:
		case KeyEvent.VK_DOWN:
		case KeyEvent.VK_END:
			delta = 1;
			break;
		default:
			assert false : KeyEvent.getKeyText(keyCode) + " is not an arrow key";
			break;
		}
		return delta;
	}

	// On XPS12 keyboard, Home=Fn+LeftArrow and End=Fn+RightArrow
	private static boolean isHomeOrEnd(final int keyCode) {
		return keyCode == java.awt.event.KeyEvent.VK_HOME || keyCode == java.awt.event.KeyEvent.VK_END;
	}

	@Override
	public int compareTo(final Rank rank2) {
		if (pVizs.isEmpty()) {
			return 1;
		} else if (rank2.pVizs.isEmpty()) {
			return -1;
		} else {
			return pVizs.first().p.compareTo(rank2.pVizs.first().p);
		}
	}

	/**
	 * @return log(constrainOddsRatio()), or 0.0 if the resulting ChiSq2x2 is
	 *         known to be uninformative..
	 */
	double constrainedLogOddsRatio(final Perspective facet) {
		return facet.constrainedLogOddsRatio(getTotalCount(), getOnCount());
	}

	/**
	 * @return always >= 0
	 */
	private int getOnCount() {
		int result = 0;
		for (final PerspectiveViz pv : pVizs) {
			result += pv.p.getOnCount();
		}
		return result;
	}

	/**
	 * @return always >= 0
	 */
	private int getTotalCount() {
		int result = 0;
		for (final PerspectiveViz pv : pVizs) {
			final int pvTotalCount = pv.p.getTotalCount();
			assert pvTotalCount >= 0;
			result += pvTotalCount;
		}
		return result;
	}

	public void stop() {
		for (final PerspectiveViz pv : pVizs) {
			pv.stop();
		}
	}

}
