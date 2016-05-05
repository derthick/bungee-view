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

package edu.cmu.cs.bungee.client.viz.grid;

import static edu.cmu.cs.bungee.javaExtensions.Util.assertMouseProcess;
import static edu.cmu.cs.bungee.javaExtensions.Util.nButtons;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.viz.BungeeFrame;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.LabeledVScrollbar;
import edu.cmu.cs.bungee.piccoloUtils.gui.VScrollbar;
import edu.umd.cs.piccolo.PNode;

/**
 * The panel of thumbnails labeled "Matching Items"
 */
public final class ResultsGrid extends BungeeFrame implements RedrawCallback {

	/**
	 * Set to non-null in init(), so essentially @NonNull
	 */
	transient public RangeEnsurer rangeEnsurer;

	/**
	 * Set to non-null in init(), so essentially @NonNull
	 */
	VScrollbar gridScrollbar;

	private @Nullable PerspectiveVScrollLabeled perspectiveVScrollLabeled;

	/**
	 * Set only in Bungee.setSelectedItem()=>setSelectedItem(), and in
	 * setSelectedItemOffset(), when: 1. User clicks, scrolls, or presses arrow
	 * keys, or 2. Item is filtered out.
	 *
	 * <br>
	 * <br>
	 * 0 means it is the first item: row 0, col 0. -1 means
	 *
	 * @see #setSelectedItemOffset
	 */
	int selectedItemOffset = -1;

	/**
	 * Cached copy of query.getOnCount(). Updated by updateOnCount(), which is
	 * called by maybeRedrawFacetDesc, queryValidRedraw, and setSelectedItem.
	 */
	int onCount;
	/**
	 * row of the last item:
	 *
	 * UtilMath.intCeil(onCount / (double) nCols) - 1.
	 *
	 * -1 means "no such row"; 0 means the first row.
	 *
	 * Updated whenever onCount or nCols changes.
	 */
	int onCountRow;
	/**
	 * (int) (usableH / gridW). Set by computeMaxThumbParameters.
	 */
	int nVisibleRows = -1;
	/**
	 * The number of rows 'above' the visible rows. Set only by
	 * setVisRowOffset(int).
	 *
	 * Always in [0, onCountRow + 1 - nVisibleRows]
	 */
	int visRowOffset;
	/**
	 * Set only in computeMaxThumbParameters (via updateNcols). Always >= 0.
	 */
	int nCols;

	/**
	 * usableW / nCols. Only set by computeMaxThumbParameters().
	 */
	int gridW = 1;

	/**
	 * usableH / nVisibleRows. Only updated by computeMaxThumbParameters().
	 *
	 * @see #computeMaxThumbParameters
	 */
	int gridH = 1;

	/**
	 * Set to non-null in init(), so essentially @NonNull
	 */
	public Thumbnails thumbnails;

	public ResultsGrid(final @NonNull Bungee _art) {
		super(_art, BungeeConstants.GRID_FG_COLOR, "Matching " + _art.getQuery().getGenericObjectLabel(true), false);
		setVisible(false);
	}

	@Override
	public void setVisible(final boolean state) {
		super.setVisible(state);
		art.getHeader().setSortLabelNMenuVisible(state);
	}

	public void init() {
		assert rangeEnsurer == null : "init called twice!!";
		rangeEnsurer = new RangeEnsurer(this);
		new Thread(rangeEnsurer).start();

		thumbnails = new Thumbnails();
		addChild(thumbnails);

		initScrollbar();
		initted();
	}

	/**
	 * Only called by other classes.
	 */
	public void reorder() {
		initScrollbar();
	}

	/**
	 * Only called by init() and reorder()
	 *
	 * <br>
	 * <br>
	 * Doesn't set height or offset.
	 *
	 * @see #init()
	 * @see #reorder()
	 */
	private void initScrollbar() {
		final Perspective sortedBy = query.sortedBy();
		if (gridScrollbar != null && (sortedBy == null) == (perspectiveVScrollLabeled == null)) {
			// We can continue to use the existing gridScrollbar.
			if (sortedBy != null) {
				assert perspectiveVScrollLabeled != null;
				perspectiveVScrollLabeled.setParent(sortedBy);
				moveInFrontOf(art.getSelectedItemColumn());
			}
		} else {
			if (gridScrollbar != null) {
				removeChild(gridScrollbar);
			}

			if (sortedBy != null) {
				gridScrollbar = new LabeledVScrollbar(art.scrollbarWidth(), BungeeConstants.GRID_SCROLL_BG_COLOR,
						BungeeConstants.GRID_SCROLL_FG_COLOR, BungeeConstants.BVBG, doScroll);
				perspectiveVScrollLabeled = new PerspectiveVScrollLabeled(art, sortedBy,
						((LabeledVScrollbar) gridScrollbar).labels);
				moveInFrontOf(art.getSelectedItemColumn());
			} else {
				gridScrollbar = new VScrollbar(art.scrollbarWidth(), BungeeConstants.GRID_SCROLL_BG_COLOR,
						BungeeConstants.GRID_SCROLL_FG_COLOR, doScroll);
				perspectiveVScrollLabeled = null;
			}
			addChild(gridScrollbar);
			validateInternal();
			resizeScrollbar();
		}
		gridScrollbar.setVisible(false);
	}

	/**
	 * Only used by initScrollbar()
	 *
	 * @see #initScrollbar()
	 */
	private transient final @NonNull Runnable doScroll = new Runnable() {
		@Override
		public void run() {
			try {
				assert assertMouseProcess();
				final int newVisRowOffset = gridScrollbar.getRowOffset(onCountRow + 1 - nVisibleRows);
				if (newVisRowOffset != visRowOffset) {
					final int selectedRow = selectedItemOffset / nCols;
					final int selectedCol = selectedItemOffset % nCols;
					final int newSelectedRow = UtilMath.constrain(selectedRow, newVisRowOffset,
							newVisRowOffset + nVisibleRows - 1);
					final int newSelectedItemOffset = newSelectedRow * nCols + selectedCol;
					if (computeSelectedItemFromOffset(newSelectedItemOffset, newVisRowOffset)) {
						art.printUserAction(ReplayLocation.GRID_SCROLL, newSelectedItemOffset, newVisRowOffset);
					}
				}
			} catch (final Throwable e) {
				art.stopReplayer();
				throw (e);
			}
		}
	};

	/**
	 * Call after changes to gridH, nVisibleRows, or onCountRow.
	 *
	 * @see #updateOnCountRow
	 * @see #initScrollbar
	 * @see #computeMaxThumbParameters
	 */
	private void resizeScrollbar() {
		if (gridScrollbar != null && gridH > 0 && nVisibleRows > 0 && onCountRow >= 0) {
			gridScrollbar.setBufferPercent(nVisibleRows, onCountRow + 1, gridH);
			if (perspectiveVScrollLabeled != null) {
				perspectiveVScrollLabeled.setWidth(gridScrollbar.getHeight());
			}
		}
	}

	// Only called by other classes
	@Override
	public void setFeatures() {
		setDesiredNumCols(getDesiredNumColumns());
		thumbnails.setFeatures();
		super.setFeatures();
	}

	public void setDesiredNumCols(final int newNcols) {
		computeEdge(newNcols);
	}

	/**
	 * Only called by setFeatures(), maxPossibleThumbs(), and
	 * validateInternal().
	 *
	 * @return User preference (art.getDesiredNumResultsColumns()), or if <= 0
	 *         compute "Bungee's Choice"
	 */
	private int getDesiredNumColumns() {
		int result = art.getDesiredNumResultsColumns();
		if (result <= 0) {
			// thumbnails grow from minGridW as the square root of extra space
			result = Math.max(1, UtilMath.roundToInt(Math.pow(usableWifNoScrollbar() / minGridW(), 0.5)));
		}
		assert result > 0 : result + " " + usableWifNoScrollbar();
		return result;
	}

	// Only called by other classes
	@Override
	protected boolean setFont(final @NonNull Font font) {
		final boolean result = super.setFont(font);
		if (result) {
			if (perspectiveVScrollLabeled != null) {
				perspectiveVScrollLabeled.setFont(font, PerspectiveVScrollLabeled.labelW(art));
			}
			validateInternal();
		}
		return result;
	}

	@Override
	public void validateInternal() {
		super.validateInternal();
		final double topMargin = getTopMargin();
		final double xMargin = art.internalColumnMargin;
		final double scrollbarWidth = art.scrollbarWidth();
		thumbnails.setOffset(xMargin, topMargin);
		gridScrollbar.setWidth(scrollbarWidth);
		gridScrollbar.setOffset(getWidth() - xMargin - scrollbarWidth, topMargin);
		// System.out.println("Grid.validateInternal getXOffset=" + getXOffset()
		// + " thumbnails.getGlobalBounds()="
		// + thumbnails.getGlobalBounds() + " grid().getFont=" + getFont());
		computeEdge(getDesiredNumColumns());
		validateYellowSIcolumnOutline();
		resetBoundaryOffsetIfNotDragging();
	}

	/**
	 * Only called by validateInternal() and setDesiredNumCols().
	 *
	 * <br>
	 * <br>
	 * Set edgeW, edgeH, gridScrollbarH, gridScrollbarBufferPercent, as well as
	 * nVisibleRows, nCols, gridW, gridH and onCountRow (via
	 * computeMaxThumbParameters).
	 *
	 * <br>
	 * <br>
	 * drawGrid() if any of these change.
	 *
	 * @see #validateInternal
	 * @see #setDesiredNumCols
	 */
	private void computeEdge(final int newNcols) {
		final int[] oldInfo = { nCols, nVisibleRows, gridW, gridH };

		computeMaxThumbParameters(newNcols);

		final int[] newInfo = { nCols, nVisibleRows, gridW, gridH };
		if (!Arrays.equals(oldInfo, newInfo)) {
			lastDrawnState[0] = 0; // force redraw
			drawGrid();
		}
	}

	/**
	 * Called only by computeEdge()
	 *
	 * <br>
	 * <br>
	 * Set nVisibleRows, nCols, gridW, gridH and onCountRow.
	 *
	 * <br>
	 * <br>
	 * Normally use nCols=maxColumns, unless maxColumnsThatFit() is smaller, or
	 * unless onCount can be displayed with fewer.
	 *
	 * @see #computeEdge
	 */
	private void computeMaxThumbParameters(final int maxColumns) {
		assert maxColumns > 0;
		final double usableH = usableH();
		if (usableH > 0.0 && onCountRow >= 0 && onCount > 0) {
			final double usableWifNoScrollbar = usableWifNoScrollbar();
			double usableW = usableWifNoScrollbar;
			int _nCols = computeMaxThumbParametersInternal(maxColumns, usableW, usableH);

			// assume square thumbs
			final int _nVisibleRows = (int) (_nCols * usableH / usableW);
			if (onCount > _nVisibleRows * _nCols) {
				usableW -= art.scrollbarWidthNmargin();
				_nCols = computeMaxThumbParametersInternal(maxColumns, usableW, usableH);
			}

			assert minGridW() * _nCols <= usableWifNoScrollbar : _nCols + " getWidth=" + getWidth() + " usableW="
					+ usableWifNoScrollbar + " minGridW=" + minGridW();
			// if (nCols != _nCols) {
			nCols = _nCols;
			gridW = (int) (usableW / nCols);
			// System.out.println("Grid.updateNcols " + nCols + "
			// _nVisibleRows=" + _nVisibleRows + " getWidth="
			// + getWidth() + " usableW()=" + usableWifNoScrollbar + " usableW="
			// + usableW + " onCount=" + onCount
			// // + " isScrollbarVisible=" + isScrollbarVisible()
			// + " gridW=" + gridW);
			gridH = _nVisibleRows > 0 ? ((int) usableH) / _nVisibleRows : gridW;
			updateOnCountRow();
			// if (nVisibleRows > 0) {
			// gridH = ((int) usableH) / nVisibleRows;
			// }
			assert gridH > 0;
			thumbnails.setWidthHeight();
			gridScrollbar.setXoffset(getWidth() - art.internalColumnMargin - art.scrollbarWidth());
			// System.out
			// .println("Grid.ggg global: " + thumbnails.getGlobalBounds() + " "
			// + thumbnails.getGlobalFullBounds()
			// + "\n local: " + thumbnails.getBounds() + " " +
			// thumbnails.getFullBounds() + "\n x: "
			// + (thumbnails.getGlobalFullBounds().getX() +
			// thumbnails.getGlobalFullBounds().getWidth())
			// + " " + gridScrollbar.getGlobalBounds().getX() + "\n
			// nCols*gridW=" + (nCols * gridW));
			if (onCount > 0) {
				setVisRowOffset();
			}
			// }
		}
	}

	private int computeMaxThumbParametersInternal(final int maxColumns, final double usableW, final double usableH) {
		return UtilMath.constrain(UtilMath.intCeil(Math.sqrt(usableW * onCount / usableH)), 1,
				Math.min(maxColumns, (int) (usableW / minGridW())));
	}

	/**
	 * Called only by computeMaxThumbParameters() and
	 * Thumbnails.maxColumnsThatFit().
	 *
	 * @see #computeMaxThumbParameters
	 */
	int maxColumnsThatFitWithoutScrollbar() {
		final int result = (int) (usableWifNoScrollbar() / minGridW());
		assert result > 0;
		return result;
	}

	public void stop() {
		if (rangeEnsurer != null) {
			rangeEnsurer.exit();
			rangeEnsurer = null;
		}
	}

	@Override
	public void maybeSetVisible(final boolean isVisible) {
		if (isVisible) {
			setIsVisible();
		} else {
			setVisible(false);
		}
	}

	/**
	 * setVisible(onCount > 0 && (isInitted ||
	 * query.isIntensionallyRestricted()))
	 *
	 * @return isVisible()
	 */
	private boolean setIsVisible() {
		final boolean isVisible = onCount > 0 && (isInitted() || query.isIntensionallyRestricted());
		setVisible(isVisible);
		return isVisible;
	}

	/**
	 * Called only in QueryUpdater process, so it must not change interface.
	 *
	 * @return the most thumbs we can possibly show, assuming infinite onCount.
	 *         This occurs when nCols=desiredColumns and onCountRow>nVisibleRows
	 *         (because scrollbar makes images smaller).
	 */
	public int maxPossibleThumbs() {
		return Math.max(0, nVisibleRows * nCols);
		// assert isInitted();
		// final int desiredColumns = getDesiredNumColumns();
		// final double usableWifScrollbar = usableWifNoScrollbar() -
		// art.scrollbarWidthNmargin();
		// final int _gridW = (int) (usableWifScrollbar / desiredColumns);
		// final double usableH = usableH();
		// final int _nVisibleRows = (int) (usableH / _gridW);
		// final int result = _nVisibleRows * desiredColumns;
		// assert result >= 0 : " rows x cols: " + nVisibleRows + " x " +
		// desiredColumns + " usable H x W: " + usableH
		// + " x " + usableWifScrollbar;
		// return result;
	}

	/**
	 * Only called by RangeEnsurer.process()
	 *
	 * @return Number of visible thumbs, considering that the last row might not
	 *         be full, or Integer.MIN_VALUE if !isVisRowOffsetValid().
	 */
	int nThumbsForVisRowOffset() {
		// setVisRowOffset();
		int result = Integer.MIN_VALUE;
		if (isVisRowOffsetValid()) {
			result = Math.min(nVisibleRows * nCols, onCount - (visRowOffset * nCols));
			assert result >= 0 : " visRowOffset=" + visRowOffset + " nVisibleRows=" + nVisibleRows + " nCols=" + nCols
					+ " onCount=" + onCount;
		}
		return result;
	}

	public boolean updateBrushing(final Set<Perspective> changedFacets) {
		boolean result = false;
		if (thumbnails != null) {
			reprocessPendingItemFacets();
			result = thumbnails.updateBrushing(changedFacets);
		}
		return result;
	}

	/**
	 * Only called by Bungee.setSelectedItem.
	 *
	 * <br>
	 * <br>
	 * Can be queued from process QueryUpdater, so call updateOnCount().
	 */
	public void setSelectedItem(final @Nullable Item selectedItem) {
		if (selectedItem == null) {
			thumbnails.outlineSelectedThumbnail(null);
		} else {
			// selectedItem.getOffset(query) is surely valid, but
			// setSelectedItemOffset() may barf since we haven't called
			// updateOnCount() yet. Therefore just set selectedItemOffset
			// directly.
			selectedItemOffset = art.getItemOffset(selectedItem);
			updateOnCount();

			// Must outline here in case we needn't drawGrid, but also
			// there, in case gridElement hasn't been displayed yet.
			final GridElement gridElement = GridElementWrapper.lookupGridElement(selectedItem, true);
			final PNode gridElementAsPNode = gridElement == null ? null : gridElement.pNode();
			thumbnails.outlineSelectedThumbnail(gridElementAsPNode);
		}
	}

	/**
	 * Only called by other classes.
	 */
	public void queryValidRedraw() {
		if (!updateOnCount() && getVisible()) {
			drawGrid();
		}
	}

	/**
	 * Only called by queryValidRedraw() and setSelectedItem().
	 *
	 * <br>
	 * <br>
	 * Update onCount, onCountRow and nVisibleRows, and resizeScrollbar(). May
	 * redrawCallback() via setVisRowOffset().
	 *
	 * @return whether redrawCallback() was called.
	 */
	private boolean updateOnCount() {
		boolean result = false;
		final int oldOnCount = onCount;
		onCount = query.isQueryValid() ? query.getOnCount() : -1;
		if (setIsVisible()) {
			if (oldOnCount != onCount) {
				updateOnCountRow();
				// initted();
				computeEdge(getDesiredNumColumns());
				constrainAndSetSelectedItemOffset(selectedItemOffset);
			}
			result = setVisRowOffset();
		}
		return result;
	}

	/**
	 * Update onCountRow and nVisibleRows, and resizeScrollbar() if either
	 * changes.
	 *
	 * @see #updateOnCount
	 */
	protected void updateOnCountRow() {
		if (nCols > 0) {
			final int _onCountRow = UtilMath.intCeil(onCount / (double) nCols) - 1;
			assert _onCountRow < onCount : onCount + " " + _onCountRow;
			final int _nVisibleRows = Math.min(_onCountRow + 1, ((int) usableH()) / gridH);
			assert assertInRange(_nVisibleRows, 0, _onCountRow + 1);

			final boolean isResize = _onCountRow != onCountRow || _nVisibleRows != nVisibleRows
					|| _nVisibleRows * gridH != gridScrollbar.getHeight();
			if (isResize) {
				onCountRow = _onCountRow;
				nVisibleRows = _nVisibleRows;
				resizeScrollbar();
			}
		}
	}

	/**
	 * @return getHeight() - getTopMargin() - getBottomMargin()
	 */
	private double usableH() {
		final double result = getHeight() - getTopMargin() - getBottomMargin();
		assert result > 0.0 : "result=" + result + " getHeight()=" + getHeight() + " getTopMargin=" + getTopMargin()
				+ " getBottomMargin=" + getBottomMargin() + " label.getScale=" + label.getScale() + " label.getYOffset="
				+ label.getYOffset();
		return result;
	}

	@Override
	public void redrawCallback() {
		if (isInitted() && onCount > 0 && !setVisRowOffset()) {
			// setVisRowOffset can change both selectedItemOffset and
			// visRowOffset.
			// If setVisRowOffset(), it has already called us recursively.
			drawGrid();
		}
	}

	/**
	 * [queryVersion, minOffset, maxOffset]
	 */
	private final int[] lastDrawnState = new int[3];

	// Don't call this unless rangeEnsurer.isUpToDate(), or else it will falsely
	// update lastDrawnState[], even though drawGrid() didn't draw grid.
	private boolean isLastDrawnStateCurrent() {
		final int version = query.version();
		assert version > 0;
		final int minOffset = visRowOffset * nCols;
		assert isVisRowOffsetValid() : "visRowOffset=" + visRowOffset + " nVisibleRows=" + nVisibleRows
				+ " selectedItemOffset=" + selectedItemOffset + " minOffset=" + minOffset + " onCount=" + onCount;
		final int maxOffsetExclusive = Math.min(onCount, minOffset + nVisibleRows * nCols);
		assert isInRange(minOffset, 0, maxOffsetExclusive - 1) : "visRowOffset=" + visRowOffset + " nCols=" + nCols
				+ " onCount=" + onCount + " nVisibleRows=" + nVisibleRows;
		assert assertInRange(selectedItemOffset, minOffset, maxOffsetExclusive - 1);

		final boolean result = version == lastDrawnState[0] && minOffset == lastDrawnState[1]
				&& maxOffsetExclusive == lastDrawnState[2];
		if (!result) {
			lastDrawnState[0] = version;
			lastDrawnState[1] = minOffset;
			lastDrawnState[2] = maxOffsetExclusive;
		}
		return result;
	}

	/**
	 * Do this if the query changes (<b>queryValidRedraw()</b>), you
	 * scroll/pick/arrow (<b>setVisRowOffset()</b> ⇒ <b>redrawCallback()</b>),
	 * or nCols/width/height changes (<b>computeEdge()</b>).
	 *
	 * <br>
	 * <br>
	 * Adds thumbnails
	 */
	void drawGrid() {
		assert Util.assertMouseProcess();
		// assert setIsVisible(); // false during initted()
		if (rangeEnsurer.isUpToDate() && !isLastDrawnStateCurrent()) {
			final int minOffset = lastDrawnState[1];
			final int maxOffsetExclusive = lastDrawnState[2];
			assert assertInRange(selectedItemOffset, minOffset, maxOffsetExclusive - 1);

			// In case selectedItem uncached at maybeSetSelectedItem() call.
			final Item selectedItem = rangeEnsurer.getItem(selectedItemOffset);
			assert selectedItem != null;
			art.setSelectedItem(selectedItem, false, selectedItemOffset, null);
			assert selectedItem == art.getSelectedItem();
			thumbnails.retainOutlineAndBoundaryGrid();
			updateScrollbarVisibility();
			int offset = minOffset;
			for (int row = 0; row < nVisibleRows; row++) {
				for (int col = 0; col < nCols; col++) {
					if (offset < maxOffsetExclusive) {
						final Item item = rangeEnsurer.getItem(offset);
						assert item != null : offset;
						assert (offset == selectedItemOffset) == (item == selectedItem) : "offset=" + offset
								+ " selectedItemOffset=" + selectedItemOffset + " item=" + item + " selectedItem="
								+ selectedItem + " art.getSelectedItem()=" + art.getSelectedItem();
						final GridElement gridElement = GridElementWrapper.lookupGridElement(item, true);
						if (gridElement == null) {
							if (lastDrawnState[0] == query.version()) {
								// probably GridElement cache was GC'ed, so just
								// re-load thumbs
								rangeEnsurer.update();
							}
							return;
						} else {
							drawGridElement(row, col, gridElement);
						}
						offset++;
					}
				}
			}
			assert offset == maxOffsetExclusive : offset + " " + UtilString.valueOfDeep(lastDrawnState);
		}
	}

	/**
	 * Only called by drawGrid().
	 */
	private void updateScrollbarVisibility() {
		final boolean isScroll = isScrollbarVisible();
		assert visRowOffset == 0 || isScroll : "visRowOffset=" + visRowOffset + " nVisibleRows=" + nVisibleRows
				+ " onCount=" + onCount + " query onCount=" + query.getOnCount() + " nDisplayableThumbs="
				+ (nVisibleRows * nCols);
		gridScrollbar.setVisible(isScroll);
		if (isScroll && perspectiveVScrollLabeled != null) {
			perspectiveVScrollLabeled.validate();
		}
	}

	/**
	 * Only called by drawGrid().
	 */
	private void drawGridElement(final int row, final int col, final @NonNull GridElement gridElement) {
		gridElement.setDisplaySize(edgeW(), edgeH());
		final double imageW = gridElement.getWidth();
		final double imageH = gridElement.getHeight();
		assert imageW <= edgeW() : imageW + " " + edgeW();
		assert imageH <= edgeH() : imageH + " " + edgeH();
		final double x = col * gridW + (int) ((gridW - imageW) / 2.0);
		final double y = row * gridH + (int) ((gridH - imageH) / 2.0);
		assert (col + 1) * gridW <= thumbnails.getWidth() : ((col + 1) * gridW) + " " + thumbnails.getWidth();
		gridElement.setOffset(x, y);
		final PNode gridElementAsPNode = gridElement.pNode();
		thumbnails.addChild(gridElementAsPNode);
		if (gridElement.getItem() == art.getSelectedItem()) {
			thumbnails.outlineSelectedThumbnail(gridElementAsPNode);
		}
	}

	public boolean isScrollbarVisible() {
		final boolean result = onCount > nVisibleRows * nCols;
		assert !result || thumbnails.getMaxX() + art.internalColumnMargin <= gridScrollbar
				.getXOffset() : (thumbnails.getMaxX() + art.internalColumnMargin) + " " + (gridScrollbar.getXOffset())
						+ " getWidth()=" + getWidth() + " thumbnails.getWidth()=" + thumbnails.getWidth() + " gridW="
						+ gridW + " edgeW()=" + edgeW() + " art.internalColumnMargin=" + art.internalColumnMargin
						+ " isScrollbarVisible=" + result;

		return result;
	}

	/**
	 * @return gridW - art.intTwiceInternalColumnMargin.
	 */
	int edgeW() {
		assert art.intTwiceInternalColumnMargin > 0;
		final int result = gridW - art.intTwiceInternalColumnMargin;
		assert result > 0;
		return result;
	}

	/**
	 * @return gridH - art.intTwiceInternalColumnMargin.
	 */
	int edgeH() {
		final int result = gridH - art.intTwiceInternalColumnMargin;
		assert result > 0;
		return result;
	}

	/**
	 * Only called by Informedia
	 *
	 * Differs from pick() because item may not be visible, due to difering
	 * random order.
	 */
	public void clickThumb(final @NonNull Item item) {
		assert query.isQueryValid();
		assert isInitted(); // does this still happen?
		assert onCount > 0;
		art.setSelectedItemExplicitly(item);
	}

	/**
	 * Only called by Bungee.handleArrow()
	 *
	 * Update selectedItemOffset and visRowOffset. If changed, also update
	 * scrollbar and selectedItem, and redrawCallback().
	 */
	public boolean handleArrow(final int keyCode, final int modifiers) {
		assert nButtons(modifiers) == 0 : KeyEvent.getKeyText(keyCode) + " " + Util.printModifiersEx(modifiers);
		int itemOffset = selectedItemOffset;
		switch (keyCode) {
		case java.awt.event.KeyEvent.VK_KP_DOWN:
		case java.awt.event.KeyEvent.VK_DOWN:
			itemOffset += nCols;
			break;
		case java.awt.event.KeyEvent.VK_KP_UP:
		case java.awt.event.KeyEvent.VK_UP:
			itemOffset -= nCols;
			break;
		case java.awt.event.KeyEvent.VK_KP_LEFT:
		case java.awt.event.KeyEvent.VK_LEFT:
			itemOffset--;
			break;
		case java.awt.event.KeyEvent.VK_KP_RIGHT:
		case java.awt.event.KeyEvent.VK_RIGHT:
			itemOffset++;
			break;
		case java.awt.event.KeyEvent.VK_HOME:
			itemOffset = 0;
			break;
		case java.awt.event.KeyEvent.VK_END:
			itemOffset = onCount;
			break;
		case java.awt.event.KeyEvent.VK_A:
			assert false : "Control+A is not valid for the Matches column.";
			return false;
		default:
			assert false : KeyEvent.getKeyText(keyCode);
			return false;
		}

		final boolean result = constrainAndSetSelectedItemOffset(itemOffset);
		if (result) {
			// Must call maybeSetSelectedItem() before setVisRowOffset() can
			// call drawGrid().
			maybeSetSelectedItem(true);
			art.printUserAction(ReplayLocation.ARROW, keyCode, modifiers);
			setVisRowOffset();
		}
		return result;
	}

	/**
	 * Called only by RangeEnsurer.process
	 *
	 * @return whether selectedItemOffset and visRowOffset are valid
	 */
	boolean isVisRowOffsetValid() {
		return nVisibleRows >= 0 && isOffsetValid(selectedItemOffset) && computeVisRowOffset() == visRowOffset;
	}

	/**
	 * Barf unless 0 <= itemOffset < onCount
	 */
	boolean assertIsOffsetValid(final int itemOffset) {
		assert isOffsetValid(itemOffset) : "onCount=" + onCount + " query.onCount=" + query.getOnCount()
				+ " itemOffset=" + itemOffset;
		return true;
	}

	/**
	 * @return whether 0 <= itemOffset < onCount
	 */
	boolean isOffsetValid(final int itemOffset) {
		return onCount > 0 && UtilMath.isInRange(itemOffset, 0, onCount - 1);
	}

	/**
	 * Only called by handleArror() and updateOnCount().
	 *
	 * selectedItemOffset always becomes UtilMath.constrain(itemOffset, 0,
	 * onCount - 1). No other side effects.
	 *
	 * @return whether selectedItemOffset changed.
	 */
	boolean constrainAndSetSelectedItemOffset(final int itemOffset) {
		return setSelectedItemOffset(UtilMath.constrain(itemOffset, 0, onCount - 1));
	}

	/**
	 * Only called by constrainAndSetSelectedItemOffset() and
	 * computeSelectedItemFromOffset().
	 *
	 * selectedItemOffset always becomes newSelectedItemOffset. No other side
	 * effects.
	 *
	 * @return whether selectedItemOffset changed.
	 */
	boolean setSelectedItemOffset(final int newSelectedItemOffset) {
		assert assertIsOffsetValid(newSelectedItemOffset);
		final boolean result = newSelectedItemOffset != selectedItemOffset;
		selectedItemOffset = newSelectedItemOffset;
		return result;
	}

	/**
	 * Called only by replayOp on GRID_SCROLL
	 */
	public boolean computeSelectedItemFromOffset(final int newSelectedItemOffset) {
		return computeSelectedItemFromOffset(newSelectedItemOffset, computeVisRowOffset());
	}

	/**
	 * Called by replayOp() (via the computeSelectedItemFromOffset method
	 * above), setVisRowOffset(), and scroll.run()
	 *
	 * @return whether selectedItem, selectedItemOffset, or visRowOffset
	 *         changed.
	 */
	public boolean computeSelectedItemFromOffset(final int newSelectedItemOffset, final int newVisRowOffset) {
		assert query.isQueryValid() && setIsVisible() : query.isQueryValid() + " " + setIsVisible();
		boolean result = false;
		if (setSelectedItemOffset(newSelectedItemOffset)) {
			result = true;
		}
		if (setVisRowOffset(newVisRowOffset)) {
			result = true;
		}
		if (maybeSetSelectedItem(false)) {
			result = true;
		}
		return result;
	}

	/**
	 * @return whether Bungee.selectedItem changed as a result of
	 *         selectedItemOffset changing. false if selectedItemOffset isn't
	 *         cached.
	 */
	private boolean maybeSetSelectedItem(final boolean isExplicitly) {
		final Item newSelectedItem = rangeEnsurer.getItem(selectedItemOffset);

		// use replayLocation==null to avoid calling printUserAction twice.
		return newSelectedItem != null && art.setSelectedItem(newSelectedItem, isExplicitly, selectedItemOffset, null);
	}

	/**
	 * Update visRowOffset based on selectedItemOffset. visRowOffset will always
	 * be >= 0.
	 *
	 * @return whether visOffset changed.
	 *
	 *         If so, also update scrollbar and selectedItem, and
	 *         redrawCallback().
	 */
	boolean setVisRowOffset() {
		return setVisRowOffset(computeVisRowOffset());
	}

	/**
	 * Only called by setVisRowOffset() above and
	 * computeSelectedItemFromOffset().
	 *
	 * @return whether visRowOffset changed.
	 *
	 *         If so, also update scrollbar and selectedItem, and
	 *         redrawCallback().
	 */
	boolean setVisRowOffset(final int newVisRowOffset) {
		final boolean result = newVisRowOffset != visRowOffset;
		if (result) {
			assert assertInRange(newVisRowOffset, 0, onCountRow + 1 - nVisibleRows);
			visRowOffset = newVisRowOffset;
			gridScrollbar.setVisRowOffset(visRowOffset);
			query.queueOrRedraw(this);
		}
		return result;
	}

	/**
	 * No side effects.
	 *
	 * @return visRowOffset constrained so that selectedItemOffset is visible.
	 *
	 *         0 <= result <= onCountRow + 1 - nVisibleRows
	 *
	 *         is always true.
	 */
	private int computeVisRowOffset() {
		assert assertIsOffsetValid(selectedItemOffset);
		int result = 0;
		if (nVisibleRows > 0) {
			final int itemRow = selectedItemOffset / nCols;
			final int minVisRowOffset = Math.max(0, itemRow - nVisibleRows + 1);
			final int maxVisRowOffset = Math.min(itemRow, onCountRow - nVisibleRows + 1);

			assert minVisRowOffset <= maxVisRowOffset : minVisRowOffset + "-" + maxVisRowOffset + " onCount=" + onCount
					+ " nCols=" + nCols + " onCountRow=" + onCountRow + " itemRow=" + itemRow + " nVisibleRows="
					+ nVisibleRows + " selectedItemOffset=" + selectedItemOffset;

			result = UtilMath.constrain(visRowOffset, minVisRowOffset, maxVisRowOffset);

			assert result >= 0 && result + nVisibleRows > itemRow && result - nVisibleRows < itemRow : " result="
					+ result + " onCountRow=" + onCountRow + " nVisibleRows=" + nVisibleRows + " itemRow=" + itemRow
					+ " minRow=" + minVisRowOffset + " - maxRow=" + maxVisRowOffset + " onCount=" + onCount;

			assert isInRange(result, 0, onCountRow + 1 - nVisibleRows) : " result=" + result + " onCountRow="
					+ onCountRow + " nVisibleRows=" + nVisibleRows + " itemRow=" + itemRow + " minVisRowOffset="
					+ minVisRowOffset + " - maxVisRowOffset=" + maxVisRowOffset + " onCount=" + onCount + " nCols="
					+ nCols + " selectedItemOffset=" + selectedItemOffset;
		}
		return result;
	}

	/**
	 * Assumes there is a scrollbar.
	 */
	public int maxColumnsIfScrollbar() {
		final double usableW = usableWifNoScrollbar() - art.scrollbarWidthNmargin();
		return (int) (usableW / minGridW());
	}

	double minGridW() {
		return BungeeConstants.MIN_THUMB_SIZE + art.twiceInternalColumnMargin;
	}

	private static final @NonNull Markup DEFAULT_NONEMPTY_CLICKDESC = DefaultMarkup.newMarkup(
			DefaultMarkup.FILTER_CONSTANT_ARROW_KEYS, DefaultMarkup.FILTER_CONSTANT_GRID_FG,
			DefaultMarkup.FILTER_CONSTANT_MATCHES);

	public @Nullable Markup defaultClickDesc() {
		final Markup result = onCount > 1 ? DEFAULT_NONEMPTY_CLICKDESC : null;
		return result;
	}

	public void validateYellowSIcolumnOutline() {
		thumbnails.validateYellowSIcolumnOutline();
	}

	@Override
	public void setExitOnError(final boolean isExit) {
		if (rangeEnsurer != null) {
			rangeEnsurer.setExitOnError(isExit);
		}
	}

	private final @NonNull List<PendingItemFacet> pendingItemFacets = new LinkedList<>();

	void addPendingItemFacet(final @NonNull Item item, final int facetID) {
		synchronized (pendingItemFacets) {
			pendingItemFacets.add(new PendingItemFacet(item, facetID));
		}
	}

	/**
	 * Called only by highlightFacet
	 */
	private void reprocessPendingItemFacets() {
		synchronized (pendingItemFacets) {
			for (final Iterator<PendingItemFacet> it = pendingItemFacets.listIterator(); it.hasNext();) {
				final PendingItemFacet pair = it.next();
				final Perspective facet = query.findPerspectiveIfCached(pair.facet);
				if (facet != null) {
					pair.item.addFacet(facet);
					it.remove();
				}
			}
		}
	}

	private static class PendingItemFacet implements Serializable {

		final @NonNull Item item;
		final int facet;

		PendingItemFacet(final @NonNull Item _item, final int _facet) {
			super();
			assert _item != null;
			item = _item;
			facet = _facet;
		}

		@Override
		public String toString() {
			return UtilString.toString(this, item + ", " + facet);
		}
	}

}
