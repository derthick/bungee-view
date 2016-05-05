/*

 Created on Mar 4, 2005

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

package edu.cmu.cs.bungee.client.viz.tagWall;

import static edu.cmu.cs.bungee.javaExtensions.Util.assertMouseProcess;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.Perspective.ToggleFacetResult;
import edu.cmu.cs.bungee.client.viz.BungeeFrame;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.KeyEventHandler;
import edu.cmu.cs.bungee.client.viz.markup.RotatedFacetText;
import edu.cmu.cs.bungee.client.viz.popup.PopupSummary;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;

/**
 * @author mad
 *
 */
public final class TagWall extends BungeeFrame implements RedrawCallback {

	private static final @NonNull DesiredSize DESIRED_MARGIN_H = new DesiredSize(1.0, Double.POSITIVE_INFINITY, 0.2);

	private static final @NonNull DesiredSize DESIRED_FRONT_H = new DesiredSize(0.0, Double.POSITIVE_INFINITY, 3.0);

	private static final @NonNull DesiredSize DESIRED_FOLD_H = new DesiredSize(0.0, Double.POSITIVE_INFINITY, 1.5);

	public final @NonNull PopupSummary popup;

	/**
	 * The drop-down menu on rank labels.
	 */
	public final @NonNull PerspectiveList perspectiveList;

	/**
	 * Currently displayed Ranks, in order.
	 *
	 * rankForPerspective adds to ranks; synchronizePerspectives removes ranks
	 */
	final @NonNull List<Rank> ranks;

	/**
	 * The width of the rankLabel plus its left and right margins and HotZone.
	 *
	 * UPDATE: now it is the whole rankLabel and extras. Rank must subtract them
	 * back out, since it depends on isConnected.
	 */
	private double rankLabelW;

	/**
	 * The width of a horizontal slice through the rotated text.
	 */
	int labelHprojectionW;

	private @NonNull RankComponentHeights selectedRankComponentHeights = new RankComponentHeights(0.0, 0.0, 0.0, 0.0);

	public TagWall(final @NonNull Bungee _art) {
		super(_art, BungeeConstants.TAGWALL_FG_COLOR, "Tag Wall", false);
		labelHprojectionW = (int) labelHprojectionW();
		ranks = new ArrayList<>(query.nAttributes);
		popup = new PopupSummary(art);
		addChild(popup);
		perspectiveList = new PerspectiveList(art);
	}

	@Override
	public void setFeatures() {
		super.setFeatures();
		for (final Rank rank : ranks) {
			rank.setFeatures();
		}
		if (!art.getShowTagLists()) {
			hidePerspectiveList();
		}
		popup.setFeatures();
	}

	@Override
	protected boolean setFont(final @NonNull Font font) {
		final boolean result = super.setFont(font);
		if (result) {
			labelHprojectionW = (int) labelHprojectionW();
			perspectiveList.setFont(font);
			for (final Rank rank : ranks) {
				rank.setFont(font);
			}
			validateInternal();
		}
		return result;
	}

	/**
	 * @return the horizontal projection of the rectangle
	 */
	public double labelHprojectionW() {
		return lineH() / RotatedFacetText.TEXT_ANGLE_SINE;
	}

	@Override
	public void validateInternal() {
		// Try to work around inconsistent fontSize/getBounds problems by not
		// redrawing during validate by temporarily setting invisible.
		final boolean oldIsVisible = getVisible();
		setParentPNodesVisible(false);
		super.validateInternal();
		computeAndAnimateRankComponentHeights();
		if (oldIsVisible) {
			setParentPNodesVisible(oldIsVisible);
		}

		synchronizeWithQuery();
		validateRanks();
		perspectiveList.validate();
		resetBoundaryOffsetIfNotDragging();
	}

	private void validateRanks() {
		final double selectedRankTotalH = selectedRankTotalH();
		final double deselectedRankTotalH = deselectedRankTotalH();
		final double width = getWidth();
		assert width >= minWidth() : this + " width=" + width + " minWidth=" + minWidth() + " super.minWidth()="
				+ super.minWidth() + " fontSize=" + getFontSize() + " rankLabelW=" + rankLabelW;
		for (final Rank rank : ranks) {
			rank.setNameAndNumW();
			rank.validate(width, rank.isConnected() ? selectedRankTotalH : deselectedRankTotalH);
		}
	}

	private void setParentPNodesVisible(final boolean isVisible) {
		for (final Rank rank : ranks) {
			rank.setParentPNodesVisible(isVisible);
		}
	}

	public void init() {
		if (!isInitted()) {
			initted();
			synchronizeWithQuery();
		}
	}

	/**
	 * Invalidate counts
	 */
	public void restrictData() {
		synchronizeWithQuery();
		// rank.restrictData() can remove a rank from ranks (via
		// pv.restrictData() via pv.maybeUndisplay()), so copy it.
		for (final Rank rank : new ArrayList<>(ranks)) {
			rank.restrictData();
		}
	}

	/**
	 * @return whether ranks changed.
	 */
	public boolean synchronizeWithQuery() {
		return synchronizePerspectives(query.displayedPerspectives());
	}

	/**
	 * @return whether any ranks were removed or PVs added.
	 */
	public boolean synchronizePerspectives(final @NonNull SortedSet<Perspective> queryPerspectives) {
		boolean result = false;

		// out with the old.
		for (final Iterator<Rank> rit = ranks.iterator(); rit.hasNext();) {
			final Rank rank = rit.next();
			if (!rank.removeUnsynchronizePerspectives(queryPerspectives)) {
				result = true;
				if (rank.isConnected) {
					art.setArrowFocus(null);
				}
				removeChild(rank);
				rit.remove();
			}
		}

		// in with the new.
		// Must create parent rank before child rank so queryPerspectives must
		// be sorted.
		for (final Perspective queryPerspective : queryPerspectives) {
			assert queryPerspective != null;
			if (lookupPV(queryPerspective) == null) {
				result = true;
				final Rank rank = rankForPerspective(queryPerspective);
				rank.addPV(queryPerspective);
			}
		}

		if (result) {
			computeAndAnimateRankComponentHeights();
		}
		final Set<Perspective> previousNcurrentRestrictions = new HashSet<>(previousRestrictions);
		final Set<Perspective> currentRestrictions = query.allNonImpliedRestrictions();
		previousNcurrentRestrictions.addAll(currentRestrictions);
		previousRestrictions = currentRestrictions;
		updateHighlighting(previousNcurrentRestrictions, query.version());

		return result;
	}

	private @NonNull Set<Perspective> previousRestrictions = new HashSet<>();

	/**
	 * @param changedFacets
	 *            facets for which highlighting has changed
	 * @return whether highlighting changed
	 */
	public boolean updateHighlighting(final @NonNull Set<Perspective> changedFacets, final int queryVersion) {
		boolean result = perspectiveList.updateHighlighting(queryVersion);
		for (final Rank rank : ranks) {
			if (rank.updateHighlighting(changedFacets, queryVersion)) {
				result = true;
			}
		}
		return result;
	}

	/**
	 * Compute a stable rankLabelW. If rankLabelW isn't consistent with
	 * selectedRankLabelsH, labels won't vertically align with bars.
	 */
	private void computeAndAnimateRankComponentHeights() {
		// Set a maximum here.
		rankLabelW = rankLabelMinWidth();
		if (getHeight() > 0.0) {
			// Find a stable value here.
			do {
				final double minSelectedRankLabelsH = rankLabelsMinHeight();

				// Any taller than this, and the base of the diagonal facet
				// labels would run off the screen to the left.
				final double maxSelectedRankLabelsH = Math.max(minSelectedRankLabelsH, rankLabelsMaxHeight());
				// System.out.println("TagWall.computeAndAnimateRankComponentHeights
				// min/maxSelectedRankLabelsH="
				// + minSelectedRankLabelsH + " - " + maxSelectedRankLabelsH + "
				// rankLabelW=" + rankLabelW
				// + " selectedRankLabelsH()=" + selectedRankLabelsH() + "
				// availableNameNnumStringW()="
				// + availableNameNnumStringW());
				final DesiredSize desiredSelectedRankLabelsH = new DesiredSize(minSelectedRankLabelsH,
						maxSelectedRankLabelsH, 7.0);
				selectedRankComponentHeights = RankComponentHeights.computeRankComponentHeights(DESIRED_FOLD_H,
						DESIRED_FRONT_H, desiredSelectedRankLabelsH, DESIRED_MARGIN_H, nRanks(), internalH());
			} while (rankLabelW != (rankLabelW = rankLabelMinWidth()));
			animateRankComponentHeights();
			validateRanks();
		}
	}

	/**
	 * call Rank.prepareAnimation and then animate to new heights.
	 */
	public void animateRankComponentHeights() {
		final double marginH = marginH();
		final @NonNull RankComponentHeights selectedHeights = computeSelectedHeights();
		final @NonNull RankComponentHeights unselectedHeights = computeUnselectedHeights(deselectedRankTotalH());
		boolean mustAnimate = false;
		final @NonNull List<Rank> _ranks = new ArrayList<>(ranks);
		double goalYoffset = getTopMargin();
		for (final Rank rank : _ranks) {
			assert selectedHeights != null && unselectedHeights != null;
			final @NonNull RankComponentHeights goalHeights = rank.isConnected ? selectedHeights : unselectedHeights;
			if (rank.prepareAnimation(goalYoffset, goalHeights)) {
				// Give ranks non-zero height imediately, so layout works.
				rank.animateRank(RankComponentHeights.MIN_ZERO_TO_ONE);
				mustAnimate = true;
			}
			goalYoffset += goalHeights.totalH() + marginH;
		}
		if (mustAnimate) {
			Util.ignore(new PiccoloUtil.MyPInterpolatingActivity(this, BungeeConstants.RANK_ANIMATION_MS, 0L,
					BungeeConstants.RANK_ANIMATION_STEP) {

				@Override
				public void setRelativeTargetValue(final float zeroToOne) {
					assert assertMouseProcess();
					final float myZeroToOne = Math.max(RankComponentHeights.MIN_ZERO_TO_ONE, zeroToOne);
					for (final Rank rank : _ranks) {
						rank.animateRank(myZeroToOne);
					}
				}

			});
		}
	}

	/**
	 * @return always > 0.0
	 */
	private double deselectedRankTotalH() {
		final double selectedRankTotalH = selectedRankTotalH();
		double result = selectedRankTotalH;
		final int nDeselectedRanks = nRanks() - 1;
		if (nDeselectedRanks > 0) {
			final double deselectedRanksTotalH = internalH() - selectedRankTotalH - nDeselectedRanks * marginH();
			result = Math.floor(deselectedRanksTotalH / nDeselectedRanks);

			assert result > 0.0 : result + " nDeselectedRanks=" + nDeselectedRanks + " selectedRankTotalH="
					+ selectedRankTotalH + " internalH()=" + internalH() + " marginH()=" + marginH();
		}
		return result;
	}

	/**
	 * @return When animating, give front priority. Only when there's more space
	 *         than the SELECTED frontH do we allocate space proportionally to
	 *         the fold and labels (and none to margin).
	 */
	private @NonNull RankComponentHeights computeSelectedHeights() {
		final double selectedRankTotalH = selectedRankTotalH();
		final double selectedFrontH = selectedRankFrontH();
		final double selectedFoldPlusLabelH = selectedRankTotalH - selectedFrontH;
		double selectedLabelH;
		if (selectedRankLabelsH() == 0.0) {
			selectedLabelH = 0.0;
		} else {
			final double foldLabelRatio = selectedRankFoldH() / selectedRankLabelsH();
			selectedLabelH = Math.rint(selectedFoldPlusLabelH / (1.0 + foldLabelRatio));
		}
		final double selectedFoldH = Math.rint(selectedFoldPlusLabelH - selectedLabelH);
		return new RankComponentHeights(selectedFoldH, selectedFrontH, selectedLabelH, 0.0);
	}

	/**
	 * @param deselectedRankTotalH
	 * @return allocate deselectedRankTotalH all to frontH
	 */
	private @NonNull static RankComponentHeights computeUnselectedHeights(final double deselectedRankTotalH) {
		return new RankComponentHeights(0.0, Math.rint(deselectedRankTotalH), 0.0, 0.0);
	}

	/**
	 * The maximum stringWidth for bar labels, computed from
	 * selectedRankLabelsH(), lineH(), and RotatedFacetText.TEXT_ANGLE. <br>
	 * <br>
	 *
	 * nam+num y range = selectedRankLabelsH() <br>
	 * = availableNameNnumStringW()*sin(θ) + lineH*cos(θ) <br>
	 * rearranging gives the actual code: <br>
	 * availableNNSW() <= (selectedRankLabelsH() - lineH*cos(θ)) /sin(θ) <br>
	 * <br>
	 *
	 * labelHprojectionW() = lineH/sin(θ) <br>
	 * <br>
	 *
	 * Make the corner of label bounding box line up with the bar center: <br>
	 * x_offset_from_bar_center = <br>
	 * letters: - lineH*sin(θ)/2 <br>
	 * labels: - availableNameNnumStringW()*cos(θ) - lineH*sin(θ)/2 <br>
	 * <br>
	 *
	 * rankLabelsYoffset() = selectedRankLabelsH() - lineH*cos(θ) <br>
	 * = selectedRankLabelsH() + rotatedLabelYoffset() <br>
	 *
	 * <img src="RankLabels.png">
	 */
	// .............|------bars------|
	// |.........../m/...⊤
	// |........./u/.....|
	// |......./n/.......|
	// |...../+/........selectedRankLabelsH()
	// |.../m/...........|
	// |./a/.............|
	// |⊢⊣...............┴
	// (⊢⊣ = labelHprojectionW() )
	double availableNameNnumStringW() {
		return Math.floor(rankLabelsYoffset() / RotatedFacetText.TEXT_ANGLE_SINE);
	}

	/**
	 * @return y offset from bar
	 */
	public double rankLabelsYoffset() {
		return selectedRankLabelsH() + rotatedLabelYoffset();
	}

	public double rankLettersYoffset() {
		return selectedRankFoldH() + rotatedLabelYoffset();
	}

	private double rankLabelsMaxHeight() {
		return rankLabelW * RotatedFacetText.TEXT_ANGLE_TANGENT + rotatedLabelYoffset();
	}

	/**
	 * @return yOffset from bottom of labels, so that text stays in bounds even
	 *         with rotation.
	 */
	private double rotatedLabelYoffset() {
		return -lineH() * RotatedFacetText.TEXT_ANGLE_COSINE;
	}

	public double rankLabelsXoffset() {
		return -availableNameNnumStringW() * RotatedFacetText.TEXT_ANGLE_COSINE;
	}

	public double rankLettersXoffset() {
		return -lineH() * RotatedFacetText.TEXT_ANGLE_SINE;
	}

	/**
	 * Always equal to an int
	 */
	private double rankLabelMinWidth() {
		return Math.ceil(Math.max(art.minNameWidth(), minNameNnumStringW() * RotatedFacetText.TEXT_ANGLE_COSINE));
	}

	private double rankLabelsMinHeight() {
		final double minNameNnumStringW = minNameNnumStringW();
		return minNameNnumStringW * RotatedFacetText.TEXT_ANGLE_SINE - rotatedLabelYoffset();
	}

	private double minNameNnumStringW() {
		double minNameNnumStringW = art.numWidth(maxBarTotalCount()) + art.minNameWidth();
		if (art.getShowCheckboxes()) {
			minNameNnumStringW += art.checkBoxWidth();
		}
		return minNameNnumStringW;
	}

	double rankLabelX() {
		return -rankLabelW + internalXmargin();
	}

	double availablePVw() {
		return usableWifNoScrollbar() - rankLabelW;
	}

	private double lineH() {
		return art.lineH();
	}

	double internalH() {
		return getHeight() - getTopMargin() - getBottomMargin();
	}

	@Override
	public double minWidth() {
		final double minBarsWidth = super.minWidth();
		final double result = minBarsWidth + rankLabelMinWidth();
		// System.out.println("TagWall.minWidth art.getFontSize=" +
		// art.getFontSize() + " minBarsWidth=" + minBarsWidth
		// + " rankLabelMinWidth=" + rankLabelMinWidth() + "
		// minNameNnumStringW=" + minNameNnumStringW() + " => "
		// + result);
		return result;
	}

	/**
	 * @return the totalCount of the bar with the highest totalCount, in any of
	 *         our PVs. Called only by minWidth().
	 */
	private int maxBarTotalCount() {
		return query.maxBarTotalCount();
	}

	@Override
	public double minHeight() {
		return lineH() * (10 + query.nAttributes);
	}

	int nRanks() {
		return ranks.size();
	}

	public double selectedRankFoldH() {
		return selectedRankComponentHeights.foldH();
	}

	double selectedRankFrontH() {
		return selectedRankComponentHeights.frontH();
	}

	public double selectedRankLabelsH() {
		return selectedRankComponentHeights.labelsH();
	}

	private double selectedRankTotalH() {
		return selectedRankComponentHeights.totalH();
	}

	double marginH() {
		return selectedRankComponentHeights.marginH();
	}

	/**
	 * @return rank whose pVizs include pvP, if any
	 */
	public @Nullable Rank lookupRank(final @NonNull Perspective pvP) {
		Rank result = null;
		for (final Rank rank : ranks) {
			if (rank.lookupPV(pvP) != null) {
				result = rank;
				break;
			}
		}
		return result;
	}

	@Override
	public void redrawCallback() {
		queryValidRedraw(query.version(), null);
	}

	public void queryValidRedraw(final int queryVersion, final @Nullable Pattern textSearchPattern) {
		// Use a copy, because rank.queryValidRedraw() can remove rank from
		// ranks
		for (final Rank rank : new TreeSet<>(ranks).descendingSet()) {
			assert rank.getParent() == this : rank;
			rank.queryValidRedraw(queryVersion, textSearchPattern);
		}

		final boolean isAdded = addActivity(barHeightAnimator);
		assert isAdded;
		query.queueOrRedraw(perspectiveList);
		popup.queryValidRedraw(textSearchPattern);
	}

	transient private final @NonNull PInterpolatingActivity barHeightAnimator = new PInterpolatingActivity(
			BungeeConstants.DATA_ANIMATION_MS, BungeeConstants.DATA_ANIMATION_STEP) {
		@Override
		public void setRelativeTargetValue(final float zeroToOne) {
			for (final Rank rank : ranks) {
				rank.setRelativeTargetValue(zeroToOne);
			}
		}
	};

	/**
	 * @param pvP
	 * @return The [possibly new] Rank where a PV for pvP belongs.
	 */
	private @NonNull Rank rankForPerspective(final @NonNull Perspective pvP) {
		Rank parentRank = findRank(pvP.getParent());
		Rank result = getChildRank(parentRank);
		if (result == null) {
			result = new Rank(this, parentRank);
			if (parentRank == null) {
				// It's a top-level rank

				// When editing, there may be gaps and unordered IDs, but the
				// "parent" is still the greatest top-level rank less than p
				for (final Rank rank : ranks) {
					if (rank.parentRank == null && rank.firstPerspective().getID() < pvP.getID()) {
						parentRank = rank;
					}
				}
			}
			ranks.add(ranks.indexOf(parentRank) + 1, result);
			addChild(result);
		}
		return result;
	}

	private @Nullable Rank findRank(final @Nullable Perspective p) {
		if (p != null) {
			for (final Rank rank : ranks) {
				if (rank.hasPVp(p)) {
					return rank;
				}
			}
		}
		return null;
	}

	@Nullable
	Rank getChildRank(final @Nullable Rank parent) {
		if (parent != null) {
			for (final Rank rank : ranks) {
				if (rank.parentRank == parent) {
					return rank;
				}
			}
		}
		return null;
	}

	public @Nullable Perspective connectedPerspective() {
		final Rank rank = connectedRank();
		return rank != null ? rank.firstPerspective() : null;
	}

	/**
	 * Only called by Rank.handleArrowForAllPVsInternal()
	 */
	public void toggleFacet(final @NonNull Perspective fromFacet, final @NonNull Perspective toFacet,
			final int modifiers) {
		final Rank oldConnectedRank = connectedRank();
		assert oldConnectedRank != null;
		final Perspective parent = toFacet.getParent();
		assert parent != null;
		assert oldConnectedRank.hasPVp(parent) : toFacet;
		assert oldConnectedRank.hasPVp(fromFacet.getParent()) : fromFacet;
		final ToggleFacetResult toggleFacetResult = parent.toggleFacet(fromFacet, toFacet, modifiers);
		if (toggleFacetResult.result) {
			art.updateQuery();
			connectToRank(oldConnectedRank);
			// Do this after updateQuery, because that will setQueryInvalid
			// art.highlightFacet(toFacet);
		} else if (toggleFacetResult.errorMsg != null) {
			art.setTip(toggleFacetResult.errorMsg);
		} else if (!synchronizeWithQuery()) {
			System.out.println("TagWall.toggleFacet NO-OP: " + toFacet + Util.printModifiersEx(modifiers));
		}
		art.setArrowFocus(toFacet);
	}

	public @Nullable Rank connectedRank() {
		Rank result = null;
		for (final Rank rank : ranks) {
			if (rank.isConnected) {
				result = rank;
				break;
			}
		}
		return result;
	}

	/**
	 * connectToRank null if pvP is null, or rank whose pVizs include pvP, if
	 * any, or error.
	 */
	public void connectToPerspective(final @Nullable Perspective pvP) {
		Rank rank = null;
		if (pvP != null) {
			rank = lookupRank(pvP);
			assert rank != null : pvP.path(true, true);
		}
		connectToRank(rank);
	}

	public void connectToRank(final @Nullable Rank rank) {
		final Rank connectedRank = connectedRank();
		if (rank != connectedRank) {
			hidePerspectiveList();
			if (connectedRank != null) {
				connectedRank.setConnected(false);
				art.setArrowFocus(null);
			}
			if (rank != null) {
				art.removeInitialHelp();
				rank.setConnected(true);
			}
			art.getHeader().setSearchVisibility(rank != null);

			animateRankComponentHeights();
		}
	}

	@Override
	public void doHideTransients() {
		popup.hide();
		hidePerspectiveList();
	}

	@Override
	public void setExitOnError(final boolean isExit) {
		popup.setExitOnError(isExit);
	}

	public void computeInfluenceDiagramNow(final @NonNull Perspective _facet) {
		showPopup(_facet);
		popup.computeInfluenceDiagramNow(_facet);
	}

	public void showPopup(final @Nullable Perspective facet) {
		if (facet == null) {
			popup.hide();
		} else if (popup.facet != facet && prepareToPopup()) {
			// prepareToPopup will hide popup

			popup.setFacet(facet, false, anchorForPopup(facet));
		}
	}

	/**
	 * Work your way up from facet until you find an ancestor with a pv. barp
	 * will be a bar on it (or the same pv if facet is a facet_type).
	 */
	@SuppressWarnings("null")
	public @NonNull LazyPNode anchorForPopup(final @NonNull Perspective facet) {
		LazyPNode anchor = null;
		for (Perspective ancestor = facet, barp = facet; anchor == null; barp = ancestor, ancestor = ancestor
				.getParent()) {
			final PerspectiveViz pv = lookupPV(ancestor);
			if (pv != null) {
				anchor = pv.anchorForPopup(barp);
			} else {
				assert ancestor.getParent() != null : "showPopup " + facet + ": " + ancestor
						+ " should have a parent or a PV";
			}
		}
		assert anchor != null;
		return anchor;
	}

	public void showMedianArrowPopup(final @Nullable Perspective facet) {
		if (prepareToPopup() && facet != null) {
			final PerspectiveViz pv = lookupPV(facet);
			assert pv != null : facet;
			assert pv.medianArrow != null : pv;
			popup.setFacet(facet, true, pv.medianArrow);
		}
	}

	/**
	 * @return pv in any of our ranks pVizs whose p = pvP, if any
	 */
	public @Nullable PerspectiveViz lookupPV(final @NonNull Perspective pvP) {
		PerspectiveViz result = null;
		for (final Rank rank : ranks) {
			result = rank.lookupPV(pvP);
			if (result != null) {
				break;
			}
		}
		return result;
	}

	/**
	 * Get rid of old popup.
	 *
	 * @return it's OK to show popups, unless isShowingInitialHelp
	 */
	private boolean prepareToPopup() {
		if (!popup.isHidden()) {
			// Theoretically should be hidden, but maybe we missed a handler
			// exit().
			popup.hide();
		}

		final boolean result = !art.isShowingInitialHelp();
		if (result) {
			moveInFrontOf(art.getGrid());
		}
		return result;
	}

	public void togglePerspectiveList(final @NonNull Perspective pvP) {
		if (perspectiveList.isHidden()) {
			perspectiveList.setSelected(pvP);
		} else {
			hidePerspectiveList();
		}
	}

	void hidePerspectiveList() {
		if (!perspectiveList.isHidden()) {
			perspectiveList.hide();
		}
	}

	/**
	 * @return the Perspective showing the PerspectiveList, if any.
	 */
	public @Nullable Perspective listedPerspective() {
		return perspectiveList.currentChildParent();
	}

	/**
	 * Can be called by mouse click on LetterLabeledAPText
	 *
	 * @param pvP
	 *            zoom within pvP's Rank, or if null in connectedRank.
	 *
	 * @return whether any zooming occurred
	 */
	public boolean zoomTo(final char lowerCaseSuffix, @Nullable final Perspective pvP) {
		assert !Character.isUpperCase(lowerCaseSuffix) : "'" + lowerCaseSuffix + "'";
		boolean result = false;
		if (!Character.isDefined(lowerCaseSuffix)) {
			System.err.println("Can't zoom to undefined char '" + lowerCaseSuffix + "'");
		} else if (!UtilString.isPrintableChar(lowerCaseSuffix) && !KeyEventHandler.isZoomChar(lowerCaseSuffix)) {
			System.err.println("Can't zoom to unprintable char '" + lowerCaseSuffix + "'");
		} else if (!art.getShowZoomLetters()) {
			art.setTip("Zooming is disabled in beginner mode");
		} else {
			// For replaying, don't assume findRank(pvP) == connectedRank()
			final Rank rank = pvP == null ? connectedRank() : findRank(pvP);
			if (rank == null) {
				System.err.println(
						"Can't zoom because " + (pvP == null ? "no connectedRank." : "can't find rank for " + pvP));
			} else {
				result = rank.zoomTo(lowerCaseSuffix, pvP);
			}
		}
		return result;
	}

	public void stop() {
		// This hangs
		// facetDesc.exit();

		for (final Rank rank : ranks) {
			rank.stop();
		}
		popup.stop();

		// This hangs in removeFromParent
		// hidePerspectiveList();
	}

}