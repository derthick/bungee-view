/*
 *
 * Created on Mar 4, 2005
 *
 * Bungee View lets you search, browse, and data-mine an image
 * collection. Copyright (C) 2006 Mark Derthick
 *
 * This program is free software; you can redistribute it and/or modify it undercents
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

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInteger;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.PrefetchStatus;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.DefaultLabeledForPV;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.client.viz.markup.MarkupViz;
import edu.cmu.cs.bungee.client.viz.markup.PerspectiveMarkupAPText;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyContainer;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPPath;
import edu.cmu.cs.bungee.piccoloUtils.gui.SqueezablePNode;

public final class PerspectiveViz extends LazyContainer implements RedrawCallback, Comparable<PerspectiveViz> {

	public final @NonNull Perspective p;

	final @NonNull Rank rank;
	final @Nullable PerspectiveViz parentPV;

	// These have xOffset==0.0
	final @NonNull SqueezablePNode labels;
	final @NonNull SqueezablePNode front;

	@Nullable
	MedianArrow medianArrow;
	LabeledLabels labeledLabels;
	LabeledBars labeledBars;
	LetterLabeled letterLabeled;

	/**
	 * Just wide enough for children *100 and /100 labels. null unless
	 * isFirstPV().
	 */
	@Nullable
	PercentLabelHotZone percentLabelHotZone;

	/**
	 * Green trapezoid connecting this PV to its bar in parentPV.
	 *
	 * Always our child. Visibility changes.
	 */
	LazyPPath lightBeam = null;

	/**
	 * This will be null unless isFirstPV().
	 *
	 * This will have xOffset<0.0
	 */
	MarkupViz rankLabel;

	@NonNull
	Font font;

	/**
	 * Our logical width, of which floor(leftEdge) - floor(leftEdge +
	 * visibleWidth) is visible. logicalWidth>=visibleWidth();
	 *
	 * visibleWidth = w - epsilon bars are placed at floor(0 - logicalWidth)
	 */
	double logicalWidth = 0.0;

	private @Nullable Letters letters;

	/**
	 * offset into logicalWidth of the leftmost visible pixel. Rightmost visible
	 * pixel is leftEdge + w - epsilon; 0<=leftEdge<logicalWidth-visibleWidth;
	 */
	double leftEdge = 0.0;

	private double visibleW;

	/**
	 * Always >= 0
	 */
	private int cachedQueryVersion;

	PerspectiveViz(final @NonNull Perspective pvP, final @NonNull Rank _rank) {
		assert pvP.isEffectiveChildren() : pvP + " " + pvP.getTotalChildTotalCount();
		p = pvP;
		rank = _rank;
		font = setFont(art().getCurrentFont());
		final Rank parentRank = rank.parentRank;
		if (parentRank != null) {
			final Perspective pParent = p.getParent();
			assert pParent != null;
			parentPV = parentRank.lookupPV(pParent);
		} else {
			parentPV = null;
		}

		front = new SqueezablePNode("Front");
		front.setPaint(BungeeConstants.PV_BG_COLOR);
		front.setHeight(1.0);
		addChild(front);

		// add labels after front, so labels get picked in favor of rankLabel
		labels = new SqueezablePNode("Labels");
		labels.setVisible(false);
		addChild(labels);
	}

	void setFeatures() {
		if (labeledLabels != null) {
			labeledLabels.setCheckboxes();
		}
		if (createOrDeleteOrRedrawLabeleds()) {
			createOrDeleteMedianArrow();
		}
	}

	/**
	 * Only called by setFeatures().
	 */
	void createOrDeleteMedianArrow() {
		if (p.isOrdered()) {
			if (!art().getShowMedian()) {
				if (medianArrow != null) {
					medianArrow.removeFromParent();
					medianArrow = null;
				}
			} else if (medianArrow == null) {
				medianArrow = new MedianArrow(this, art().getFontSize() / 2, 0);
				front.addChild(medianArrow);
				layoutMedianArrow();
			}
		}
	}

	void queryValidRedraw(final int _queryVersion, final @Nullable Pattern textSearchPattern) {
		assert _queryVersion >= 0;
		cachedQueryVersion = _queryVersion;
		if (createOrDeleteOrRedrawLabeleds()) {
			for (final Bar bar : getAllBars()) {
				bar.queryValidRedraw();
			}
			if (rank.isConnected) {
				for (final PerspectiveMarkupAPText label : labeledLabels.getAllLabels()) {
					label.queryValidRedraw(cachedQueryVersion, textSearchPattern);
				}
			}
			if (isFirstPV()) {
				assert rankLabel != null : rank + " " + p.path();
				rankLabel.queryValidRedraw(cachedQueryVersion, textSearchPattern);
			}
		}
	}

	@Override
	// 'this' is ONLY suitable as a callback from within
	// createOrDeleteOrRedrawLabeleds() !!!!
	// Otherwise use Rank as the callback.
	public void redrawCallback() {
		createOrDeleteOrRedrawLabeleds();
	}

	/**
	 * Called when most anything changes - by Rank.addPV (via setFeatures),
	 * queryValidRedraw, restrictData, setConnected, setFeatures,
	 * setLogicalBounds, and redrawCallback (as a callback from within
	 * createOrDeleteOrRedrawLabeleds)
	 *
	 * @return whether redraw queue is empty of more redraws and labels are
	 *         initted, that is, if prefetched, frontW>0, and query valid.
	 */
	private boolean createOrDeleteOrRedrawLabeleds() {
		assert Util.assertMouseProcess();
		final boolean result = !maybeUndisplay() && p.isPrefetched(PrefetchStatus.PREFETCHED_NO_NAMES)
				&& front.getWidth() > 0.0 && updateQueryVersion() && !query().isCallbackQueued(this);
		if (result) {
			assert getVisible() : this;
			assert p.isEffectiveChildren() : p;
			assert rank.pVizs.contains(this) : p + " has been removed; redraw request is stale";

			if (labeledBars == null) {
				labeledBars = new LabeledBars(this);
			}
			query().queueOrRedraw(Util.nonNull(labeledBars));

			layoutMedianArrow();

			if (rank.isConnected) {
				if (labeledLabels == null) {
					assert labels.getWidth() > 0.0;
					assert labelHprojectionW() > 0;
					labeledLabels = new LabeledLabels(this);
				}
				query().queueOrRedraw(Util.nonNull(labeledLabels));

				createOrDeleteOrRedrawLetters();
			}
		}
		return result;
	}

	void layoutMedianArrow() {
		if (medianArrow != null) {
			boolean isVisible = false;
			if (p.getOnCount() > 0) {
				assert query().isQueryValid();
				assert p.prefetchStatus().compareTo(PrefetchStatus.PREFETCHED_NO) > 0;
				final double unconditionalX = logicalWidth / 2.0 - leftEdge;
				final double conditionalX = conditionalX();
				assert !Double.isNaN(conditionalX) : "p.oncount>0, so why NaN?";
				isVisible = Math.min(unconditionalX, conditionalX) < visibleW
						&& Math.max(unconditionalX, conditionalX) > 0.0;
				if (isVisible) {
					assert medianArrow != null;
					medianArrow.layout(conditionalX, unconditionalX, 1.0 / tagWall().selectedRankFrontH());
					assert medianArrow != null;
					medianArrow.moveToFront();
				}
			}
			assert medianArrow != null;
			medianArrow.setVisible(isVisible);
		}
	}

	/**
	 * Only called by layoutMedianArrow().
	 */
	private double conditionalX() {
		double conditionalX = Double.NaN;
		final double conditionalMedianWhichChildPlusFractionEffective = p.medianWhichChild(true);
		if (conditionalMedianWhichChildPlusFractionEffective >= 0.0) {
			final int conditionalMedianIndexEffective = (int) conditionalMedianWhichChildPlusFractionEffective;
			final double left = labeledBars.minLabelPixel(conditionalMedianIndexEffective);
			final double right = labeledBars.maxLabelPixel(conditionalMedianIndexEffective);
			final double childFraction = conditionalMedianWhichChildPlusFractionEffective
					- conditionalMedianIndexEffective;
			conditionalX = UtilMath.interpolate(left, right, childFraction);
			assert !Double.isNaN(conditionalX);
		}
		// if (Double.isNaN(conditionalX)) {
		// System.out.println("PerspectiveViz.conditionalX " + p + "
		// conditionalMedianWhichChildPlusFractionEffective="
		// + conditionalMedianWhichChildPlusFractionEffective);
		// }
		return conditionalX;
	}

	/**
	 * Invalidate counts
	 */
	void restrictData() {
		if (!maybeUndisplay()) {
			labeledBars.setCountsHashInvalid();
			if (labeledLabels != null) {
				labeledLabels.setCountsHashInvalid();
			}
			if (letterLabeled != null) {
				letterLabeled.setCountsHashInvalid();
			}
			// No need - queryValidRedraw() is always called after this
			// initLabeleds();
		}
	}

	/**
	 * undisplay() if !p.isEffectiveChildren()
	 *
	 * @return whether !p.isDisplayed()
	 */
	private boolean maybeUndisplay() {
		if (p.isDisplayed() && !p.isEffectiveChildren()) {
			p.undisplay();
			tagWall().synchronizeWithQuery();
		}
		return !p.isDisplayed();
	}

	private @NonNull Collection<Bar> getAllBars() {
		return labeledBars == null ? UtilArray.EMPTY_LIST : labeledBars.getAllLabels();
	}

	/**
	 * @return If there is already a bar for facet, return it.
	 */
	private @Nullable Bar lookupBar(final @NonNull Perspective facet) {
		Bar result = null;
		if (labeledBars != null) {
			result = labeledBars.lookupLabel(facet);
		}
		assert result == null || result.getParent() == front;
		return result;
	}

	/**
	 * Called only by Rank.validateInternal.
	 */
	void validate(final int width, final double height) {

		// during Rank.addPV, height may be < 0.0
		assert width > 0 && assertInteger(height) : width + " x " + height;

		if (p.isEffectiveChildren()) {
			// If !p.isEffectiveChildren(), we're in the process of
			// removing this perspective, which will be done by updateData.

			setWidthHeight(width, height);
			createOrDeleteRankLabel();
		}
	}

	@Override
	public boolean setBounds(final double x, final double y, final double width, final double height) {
		assert x == 0.0 && y == 0.0;
		final boolean isWidthChanged = width != getWidth();
		final boolean result = super.setBounds(x, y, width, height);
		if (isWidthChanged) {
			visibleW = width;
			front.setWidth(width);
			labels.setWidth(width);
			layoutRankLabel(false);

			if (labeledBars != null) {
				labeledBars.setWidth(width);
			}
			if (labeledLabels != null) {
				labeledLabels.setWidth(width);
			}
			if (letters != null) {
				letters.setWidth(width);
			}
			resetLogicalBounds();
			layoutLightBeam();
		}
		return result;
	}

	private double percentLabelHotZoneW() {
		return isShowHotZoneWhenConnected() ? ensureHotZone().getGlobalBounds().getWidth() : 0.0;
	}

	private @NonNull PercentLabelHotZone ensureHotZone() {
		assert isShowHotZoneWhenConnected();
		if (percentLabelHotZone == null) {
			percentLabelHotZone = new PercentLabelHotZone(this);
			front.addChild(percentLabelHotZone);
		}
		assert percentLabelHotZone != null;
		return percentLabelHotZone;
	}

	private boolean isShowHotZone() {
		return isConnected() && isShowHotZoneWhenConnected();
	}

	private boolean isShowHotZoneWhenConnected() {
		return isFirstPV() && rank.frontH() > 0.0;
	}

	/**
	 * Remove or add rankLabel, and updateHotZoneTransparency(), depending on
	 * isFirstPV(). Doesn't do layout.
	 */
	void createOrDeleteRankLabel() {
		if (isFirstPV() == (rankLabel == null)) {
			if (rankLabel == null) {
				rankLabel = new MarkupViz(art(), BungeeConstants.TAGWALL_FG_COLOR) {

					@Override
					protected @NonNull ReplayLocation getReplayLocation(final @Nullable Object element) {
						ReplayLocation result = ReplayLocation.DEFAULT_REPLAY;
						Perspective perspective = null;
						if (element instanceof PerspectiveMarkupAPText) {
							perspective = ((PerspectiveMarkupAPText) element).getFacet();
						} else if (element instanceof PerspectiveMarkupElement) {
							perspective = ((PerspectiveMarkupElement) element).fromPerspective();
						}
						if (rank.hasPVp(perspective)) {
							result = ReplayLocation.RANK_LABEL_REPLAY;
						}
						return result;
					}

				};
				front.addChild(rankLabel);
				rankLabel.replayLocation = ReplayLocation.RANK_LABEL_REPLAY;
			} else {
				rankLabel.removeFromParent();
				rankLabel = null;
			}
			updateHotZoneTransparency();
		}
	}

	@NonNull
	Font setFont(final @NonNull Font _font) {
		assert _font != font;
		font = _font;
		if (percentLabelHotZone != null) {
			percentLabelHotZone.setFont(font);
		}
		if (labeledLabels != null) {
			labeledLabels.setFont(font, labelHprojectionW());
		}
		if (letterLabeled != null) {
			letterLabeled.setFont(font, labelHprojectionW());
		}
		return font;
	}

	void setParentPNodesVisible(final boolean isVisible) {
		labels.setParentPNodesVisible(isVisible);
	}

	/**
	 * Only called by Rank.setNameAndNumW(), and only when it's nameW or numW
	 * changes.
	 */
	void setNameAndNumW() {
		if (labeledLabels != null) {
			labeledLabels.removeAllLabels(true);
		}
	}

	/**
	 * Only called by TagWall.barHeightAnimator
	 *
	 * Animate Bars
	 */
	void setRelativeTargetValue(final double zeroToOne) {
		for (final Bar bar : getAllBars()) {
			bar.setRelativeTargetValue(zeroToOne);
		}
	}

	/**
	 * Only called by Rank.animateRank()
	 */
	void animate(final float zeroToOne) {
		try {
			layoutChildren();
		} catch (final AssertionError e) {
			System.err.println("While PerspectiveViz.animate connectedRank=" + tagWall().connectedRank() + " " + this
					+ " " + zeroToOne);
			e.printStackTrace();
		}
		// if (zeroToOne == 1.0f) {
		// if (medianArrow != null) {
		// medianArrow.setVisible(true);
		// }
		// }
	}

	@Override
	public void layoutChildren() {
		// Don't do anything if we're being removed from rank
		if (rank.pVizs.contains(this) && logicalWidth > 0.0) {
			setHeight(rank.getHeight());
			final double foldH = rank.foldH();
			final double frontH = rank.frontH();
			front.layout(foldH, frontH);
			layoutRankLabel(false);
			layoutLightBeam();

			// if (rank.isConnected) {
			labels.layout(foldH + frontH, rank.labelsYScale());
			if (letters != null) {
				letters.layout(foldH, rank.lettersYScale());
			}
			if (isShowHotZone()) {
				ensureHotZone().layout();
			}
			// }
		}
	}

	void setRankLabelContent(final @NonNull Markup content) {
		if (rankLabel.setContent(content)) {
			layoutRankLabel(true);
		}
	}

	/**
	 * |---------tagWall().rankLabelW---------|
	 *
	 * internalMargin rankLabel percentLabels bars
	 *
	 * ...............|......................|
	 *
	 * ..........rankLabelX().............pixel=0
	 *
	 * (rankLabel may be scaled down to fit, so attend to its globalBounds)
	 */
	void layoutRankLabel(final boolean contentChanged) {
		final double frontH = rank.frontH();
		if (isFirstPV() && frontH > 0.0) {
			final double lineH = art().lineH();
			final double rankLabelRawH = frontH + rank.labelsH();
			final double nLines = Math.max(1.0, Math.floor(rankLabelRawH / lineH));
			final double rankLabelH = lineH * nLines;
			final double xScale = Math.min(rankLabelRawH, 1.0);
			final double yScale = xScale / frontH;
			final boolean boundsChanged = rankLabel.setBounds(0.0, 0.0, Math.floor(rankLabelWidth() / xScale),
					rankLabelH);
			rankLabel.setTransform(UtilMath.scaleNtranslate(xScale, yScale, Math.floor(rankLabelX()), 0.0));
			if (contentChanged || boundsChanged) {
				rankLabel.setHeight(rankLabelH);
				final boolean isWrap = rankLabelH >= 2.0 * lineH;
				rankLabel.setIsWrapText(isWrap);
				if (!contentChanged && isWrap) {
					rank.updateRankLabelContent();
				} else if (!isWrap) {
					// Lose indentation before nested PARENT_INDICATOR_PREFIXs.
					for (final ListIterator<MarkupElement> it = rankLabel.getContent().listIterator(1); it.hasNext();) {
						final MarkupElement markupElement = it.next();
						if (markupElement instanceof MarkupStringElement) {
							final String markupString = ((MarkupStringElement) markupElement).getName();
							if (markupString.contains(BungeeConstants.PARENT_INDICATOR_PREFIX)
									&& markupString.length() > 1) {
								it.set(MarkupStringElement.getElement(BungeeConstants.PARENT_INDICATOR_PREFIX));
							}
						}
					}
				}
				rankLabel.layout();
			}
		}
	}

	/**
	 * Only used by layoutLightBeam()
	 *
	 * [top, bottom, topLeft, topRight, bottomLeft, bottomRight]
	 */
	private float[] prevLightBeamCoords = null;

	/**
	 * Draw the light beam shining from parentPV to this pv.
	 *
	 * The shape has to change shape during animation, so is called in
	 * layoutChildren rather than draw.
	 */
	void layoutLightBeam() {
		if (parentPV != null) {
			final Bar parentBar = parentPV.lookupBar(p);
			if (parentBar != null) {
				final float[] newCoords = getLightBeamCoords(parentBar);
				if (!Arrays.equals(newCoords, prevLightBeamCoords)) {
					prevLightBeamCoords = newCoords;
					final float[] Xs = { newCoords[4], newCoords[2], newCoords[3], newCoords[5], newCoords[4] };
					final float[] Ys = { newCoords[1], newCoords[0], newCoords[0], newCoords[1], newCoords[1] };
					ensureLightBeam().setPathToPolyline(Xs, Ys);
				}
			} else {
				removeLightBeam();
			}
		}
	}

	private LazyPPath ensureLightBeam() {
		if (lightBeam != null) {
			lightBeam.reset();
			lightBeam.setVisible(true);
		} else {
			lightBeam = new LazyPPath();
			lightBeam.setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
			lightBeam.setStroke(null);
			updateLightBeamTransparency();
			addChild(lightBeam);
		}
		return lightBeam;
	}

	void removeLightBeam() {
		if (lightBeam != null) {
			lightBeam.setVisible(false);
		}
	}

	private float[] getLightBeamCoords(final Bar parentBar) {
		final float[] newCoords = new float[6];
		final Rectangle2D frontBounds = globalToLocal(front.getGlobalBounds());
		newCoords[1] = (float) (frontBounds.getY()); // bottom
		newCoords[4] = (float) frontBounds.getX(); // bottom left
		// bottom right
		newCoords[5] = newCoords[4] + (float) frontBounds.getWidth();

		final Rectangle2D parentBarBounds = globalToLocal(parentBar.getGlobalBounds());
		newCoords[0] = (float) parentBarBounds.getMaxY(); // top
		newCoords[2] = (float) parentBarBounds.getX(); // top left
		// top right
		newCoords[3] = newCoords[2] + (float) parentBarBounds.getWidth();

		assert newCoords[1] + 5f >= newCoords[0] : this + "\n front (" + front.getGlobalBounds()
				+ ") should be below\n parentBar " + parentBar + " (" + parentBar.getGlobalBounds() + ").\n PV: " + this
				+ " " + getGlobalBounds() + "\n rank: " + rank + " " + rank.getGlobalBounds() + "\nparentRank: "
				+ rank.parentRank + " " + Util.nonNull(rank.parentRank).getGlobalBounds();
		return newCoords;
	}

	/**
	 * @return The desired rankLabel.getWidth()
	 */
	private double rankLabelWidth() {
		final double result = -rankLabelX() - percentLabelHotZoneW();
		assert result > 0.0 : this + " rankLabelX=" + rankLabelX() + " percentLabelHotZoneW=" + percentLabelHotZoneW();
		return result;
	}

	double rankLabelX() {
		return tagWall().rankLabelX();
	}

	boolean updateHighlighting(final @NonNull Set<Perspective> changedFacets, final int queryVersion) {
		boolean result = updateLightBeamTransparency();

		if (isFirstPV() && rankLabel.updateHighlighting(changedFacets, queryVersion)) {
			result = true;
		}

		if (labeledLabels != null && labeledLabels.updateHighlighting(changedFacets, queryVersion)) {
			result = true;
		}

		if (labeledBars != null && labeledBars.updateHighlighting(changedFacets, queryVersion)) {
			result = true;
		}

		return result;
	}

	/**
	 * @return whether color (for p.isRestriction(true/false/neither)) or
	 *         transparency (for highlighting) changed.
	 */
	boolean updateLightBeamTransparency() {
		boolean result = false;
		if (lightBeam != null) {
			final Set<Perspective> brushedFacets = art().getBrushedFacets();
			boolean isBrush = brushedFacets.contains(p);
			for (final Perspective brushedFacet : brushedFacets) {
				if (isBrush) {
					break;
				}
				isBrush = brushedFacet.getParent() == p;
			}
			if (lightBeam.setMyTransparency(BungeeConstants.getLightbeamTransparency(isBrush))) {
				result = true;
			}
			art();
			if (lightBeam.setMyPaint(Bungee.significanceColor(Bungee.significance(p, -1), false))) {
				result = true;
			}
		}
		return result;
	}

	void setConnected(final boolean connected) {
		if (connected) {
			resetLogicalBounds();
		} else if (labeledBars != null) {
			// labeledBars remain visible, so always reset them
			labeledBars.setLogicalBoundsAndValidateIfChanged(0.0, visibleWidth());
		}
		updateHotZoneTransparency();
		createOrDeleteOrRedrawLabeleds();
	}

	private void updateHotZoneTransparency() {
		final boolean connected = rank.isConnected;
		if (isShowHotZone()) {
			ensureHotZone().setConnected(connected);
		} else if (percentLabelHotZone != null && (!connected || !isFirstPV())) {
			// if it is no longer the first PV, make sure it goes transparent.
			assert percentLabelHotZone != null;
			percentLabelHotZone.setConnected(false);
		}
	}

	private void resetLogicalBounds() {
		if (letterLabeled != null) {
			letterLabeled.stopZoomer();
		}
		setLogicalBounds(0.0, visibleWidth());
	}

	public void setLogicalBounds(final double intCountLeftEdge, final double intCountLogicalWidth) {
		if (labeledBars != null) {
			labeledBars.setIntLogicalBounds(intCountLeftEdge, intCountLogicalWidth, labeledBars.totalChildCount);
			if (logicalWidth != labeledBars.logicalWidth) {
				logicalWidth = labeledBars.logicalWidth;
				leftEdge = labeledBars.leftEdge;
				labeledBars.validate();
				setLogicalBoundsInternal(letterLabeled);
				setLogicalBoundsInternal(labeledLabels);
				createOrDeleteOrRedrawLabeleds();
			}
		} else {
			logicalWidth = intCountLogicalWidth;
			leftEdge = intCountLeftEdge;

			// added 1/9/2016 because bars weren't drawn on startup, because
			// front.getWidth was zero.
			createOrDeleteOrRedrawLabeleds();
		}
	}

	void setLogicalBoundsInternal(final DefaultLabeledForPV<?, ?> lettersOrLabels) {
		if (lettersOrLabels != null) {
			// System.out.println("PerspectiveViz.setLogicalBoundsInternal
			// parentPNode.getVisible="
			// + lettersOrLabels.parentPNode.getVisible());
			assert logicalWidth + UtilMath.ABSOLUTE_SLOP >= leftEdge + lettersOrLabels.visibleWidth() : lettersOrLabels
					+ " leftEdge=" + leftEdge + " visibleWidth()=" + lettersOrLabels.visibleWidth() + " logicalWidth="
					+ logicalWidth;
			lettersOrLabels.setLogicalBoundsAndValidateIfChanged(leftEdge, logicalWidth);
			assert UtilMath.assertApproxEquals(lettersOrLabels.leftEdge, leftEdge);
			assert UtilMath.assertApproxEquals(lettersOrLabels.logicalWidth, logicalWidth);
		}
	}

	double visibleWidth() {
		return visibleW;
	}

	// void hidePvTransients() {
	// tagWall().mayHideTransients();
	// }

	/**
	 * @return rankLabel if p==facet; else its bar if facet is a child in the
	 *         visible range; else front. null if these have empty bounds.
	 */
	@Nullable
	LazyPNode anchorForPopup(final @NonNull Perspective facet) {
		LazyPNode result = null;
		if (facet == p) {
			final PerspectiveViz parentPV2 = parentPV;
			if (parentPV2 != null) {
				if (parentPV2.labeledBars != null && parentPV2.labeledBars.isPotentiallyVisible(facet)) {
					result = parentPV2.lookupBar(facet);
				}
			}
			if (result == null && isFirstPV() && !rankLabel.getGlobalBounds().isEmpty()) {
				result = rankLabel;
			}
		} else if (labeledBars != null && labeledBars.isPotentiallyVisible(facet)) {
			result = lookupBar(facet);
		}
		if (result == null && !front.getGlobalBounds().isEmpty()) {
			result = front;
		}
		return result;
	}

	private boolean isFirstPV() {
		return rank.firstPV() == this;
	}

	/**
	 * Called only by Rank.prepareAnimation
	 */
	void prepareAnimation() {
		// work around display bug - line gets drawn too thick initially after
		// changes, so hide it during animation. The problem still shows up when
		// the popup translates across it.
		// if (medianArrow != null) {
		// medianArrow.setVisible(false);
		// }
		if (nameW() > art().w) {
			query().queueOrRedraw(rank);
		}
	}

	void createOrDeleteOrRedrawLetters() {
		if (art().getShowZoomLetters()) {
			if (letters == null) {
				assert labeledBars.isInittedLabelXs();
				letters = new Letters(this);
				letters.layout(rank.foldH(), rank.lettersYScale());
				addChild(letters);
				if (p.isAlphabetic()) {
					assert letters != null;
					letterLabeled = new LetterLabeled(letters);
				}
			} else if (letterLabeled != null) {
				query().queueOrRedraw(Util.nonNull(letterLabeled));
			}
		} else if (letters != null) {
			letters.removeFromParent();
			letters = null;
			letterLabeled = null;
		}
	}

	Perspective firstVisibleBar() {
		return labeledBars.firstPotentiallyVisibleChild();
	}

	Perspective lastVisibleBar() {
		return labeledBars.lastPotentiallyVisibleChild();
	}

	int nBarsInVisibleRange() {
		return labeledBars == null ? 0 : labeledBars.nChildrenInVisibleRange();
	}

	/**
	 * Can be called by mouse click on LetterLabeledAPText
	 *
	 * @return whether zoom succeeded
	 */
	boolean zoomTo(final char lowerCaseSuffix) {
		if (!p.isAlphabetic()) {
			art().setTip("Zooming is disabled because " + p.getName() + " tags are not in alphabetical order");
		} else if (nBarsInVisibleRange() == 1) {
			art().setTip(p.getNameIfCached() + " is already zoomed in to a single Tag ("
					+ firstVisibleBar().getNameIfCached() + ")");
		} else {
			if (letters == null) {
				UserAction.displayAncestors(p, art());
				// createOrDeleteOrRedrawLetters();
			}
			return letterLabeled.zoomTo(lowerCaseSuffix);
		}
		return false;
	}

	@Override
	public int compareTo(final PerspectiveViz pv) {
		return p.compareTo(pv.p);
	}

	/**
	 * @return isQueryValid. Updates cachedQueryVersion if so.
	 */
	boolean updateQueryVersion() {
		final int version = query().version();
		final boolean result = version >= 0;
		if (result) {
			cachedQueryVersion = version;
		}
		return result;
	}

	/**
	 * The width of a horizontal slice through the rotated text.
	 */
	int labelHprojectionW() {
		return tagWall().labelHprojectionW;
	}

	boolean isConnected() {
		return rank.isConnected();
	}

	public double numW() {
		return rank.numW;
	}

	public double nameW() {
		return rank.nameW;
	}

	Query query() {
		return p.query();
	}

	public TagWall tagWall() {
		return rank.tagWall;
	}

	public @NonNull Bungee art() {
		return tagWall().art;
	}

	public Rank getRank() {
		return rank;
	}

	@Override
	public String toString() {
		String prefix = "";
		if (letterLabeled != null) {
			prefix = letterLabeled.prefix();
			if (prefix.length() > 0) {
				prefix = " prefix='" + prefix + "'";
			}
		}
		return UtilString.toString(this, p + prefix);
	}

	public void stop() {
		if (letterLabeled != null) {
			letterLabeled.stopZoomer();
		}
	}

	double getLogicalWidth() {
		return logicalWidth;
	}

	double getLeftEdge() {
		return leftEdge;
	}

	void setLeftEdge(final double _leftEdge) {
		leftEdge = _leftEdge;
	}

	double getVisibleW() {
		return visibleW;
	}

	void setVisibleW(final double _visibleW) {
		visibleW = _visibleW;
	}

	int getCachedQueryVersion() {
		assert cachedQueryVersion == query().version();
		return cachedQueryVersion;
	}

}
