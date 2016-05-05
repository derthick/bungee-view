/*

 Created on May 25, 2006

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

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.PrefetchStatus;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.client.viz.markup.PerspectiveMarkupAPText;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.comparator.IntValueComparator;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.MouseDoc;
import edu.cmu.cs.bungee.piccoloUtils.gui.SolidBorder;
import edu.cmu.cs.bungee.piccoloUtils.gui.SortButton;
import edu.cmu.cs.bungee.piccoloUtils.gui.SortButtons;
import edu.cmu.cs.bungee.piccoloUtils.gui.SortDirection;
import edu.cmu.cs.bungee.piccoloUtils.gui.VScrollbar;
import edu.umd.cs.piccolo.PNode;

/**
 * The expert-mode list of children adjacent to Rank label.
 *
 * Child of TagWall when not hidden; else parent is null;
 */
public final class PerspectiveList extends LazyPNode implements MouseDoc,
		// NO_UCD (use default)
		RedrawCallback, SortButtons<SortBy> {

	/**
	 * Draw the list above the rank label (as opposed to below)?
	 */
	private static final boolean IS_DRAW_ABOVE_LABEL = false;
	private static final double LABEL_SCALE = 1.0;
	private static final @NonNull SortBy DEFAULT_SORT_FIELD = SortBy.SORT_BY_ID;

	private static final @NonNull IDComparator ID_COMPARATOR = new IDComparator();
	private static final @NonNull SelectedComparator SELECTED_COMPARATOR = new SelectedComparator();

	final @NonNull Bungee art;
	final @NonNull VScrollbar scrollbar;
	final @NonNull SortButton<SortBy> sortByOnCount;

	private final @NonNull OnCountComparator onCountComparator = new OnCountComparator();
	private final @NonNull List<SortButton<SortBy>> sortButtons;
	private final @NonNull SortButton<SortBy> sortByID;
	private final @NonNull SortButton<SortBy> sortBySelection;
	private final @NonNull SolidBorder border = new SolidBorder(BungeeConstants.GOLD_BORDER_COLOR,
			BungeeConstants.PERSPECTIVE_LIST_OUTLINE_THICKNESS);
	private final @NonNull APText label;
	private final Map<Perspective, int[]> selectedOnCountsCache = new IdentityHashMap<>();

	/**
	 * The field currently being sorted by
	 */
	private @NonNull SortBy currentSortField = DEFAULT_SORT_FIELD;

	private @NonNull SortDirection currentSortDirection = currentSortField.defaultSortDirection();

	/**
	 * Just the effective children of currentChildParent, sorted according to
	 * SortButton states.
	 */
	private Perspective[] sortedEffectiveFacets;

	private int countsQueryVersion = -2;

	private Perspective currentChildParent;
	/**
	 * The largest selectedOnCount among children of currentChildParent.
	 */
	private int maxChildSelectedOnCount;
	/**
	 * The longest MarkupElement among children of currentChildParent.
	 */
	private double longestNamedFacetMarkupElementLength = 0.0;

	PerspectiveList(final @NonNull Bungee a) {
		art = a;
		setPaint(BungeeConstants.PERSPECTIVE_LIST_BG_COLOR);

		label = art.oneLineLabel();
		label.scale(LABEL_SCALE);
		label.setTextPaint(BungeeConstants.PERSPECTIVE_LIST_TEXT_COLOR);

		final Runnable scroll = new Runnable() {
			@Override
			public void run() {
				try {
					draw();
				} catch (final Throwable e) {
					art.stopReplayer();
					throw (e);
				}
			}
		};
		scrollbar = new VScrollbar(art.scrollbarWidth(), BungeeConstants.PERSPECTIVE_LIST_SCROLL_BG_COLOR,
				BungeeConstants.PERSPECTIVE_LIST_SCROLL_FG_COLOR, scroll);

		sortBySelection = getSortButton(SortBy.SORT_BY_SELECTION);
		sortByOnCount = getSortButton(SortBy.SORT_BY_ON_COUNT);
		sortByID = getSortButton(SortBy.SORT_BY_ID);
		sortButtons = new ArrayList<>(3);
		sortButtons.add(sortBySelection);
		sortButtons.add(sortByOnCount);
		sortButtons.add(sortByID);
	}

	/**
	 * Only called by Constructor
	 */
	private @NonNull SortButton<SortBy> getSortButton(final @NonNull SortBy field) {
		final SortButton<SortBy> sortButton = new SortButton<SortBy>(field, field.defaultSortDirection(),
				art.getCurrentFont(), this, BungeeConstants.PERSPECTIVE_LIST_TEXT_COLOR,
				BungeeConstants.PERSPECTIVE_LIST_SORT_BUTTON_BG_COLOR, field.mouseDoc()) {

			@Override
			protected @NonNull String getMouseDoc() {
				String result = isEnabled() ? mouseDoc : getDisabledMessage();
				if (isEnabled() && this == sortByOnCount) {
					result = "Sort tags by number of " + query().getGenericObjectLabel(true) + " that ";
					final int nRestrictions = currentChildParent().nRestrictions();
					if (nRestrictions > 0) {
						result += "would satisfy all filters if you clicked on that tag (and thus removed the "
								+ nRestrictions + " " + UtilString.maybePluralize("filter", nRestrictions)
								+ " currently on " + currentChildParent().getName() + ")";
					} else {
						result += "satisfy all filters";
					}
				}
				assert UtilString.isNonEmptyString(result) : this + "\n isEnabled=" + isEnabled() + " getVisible="
						+ getVisible() + " getPickable=" + getPickable() + " getPickableMode=" + getPickableMode()
						+ " result=" + result;
				assert result != null;
				return result;
			}

		};
		sortButton.child.setConstrainWidthToTextWidth(false);
		return sortButton;
	}

	/**
	 * Only called by TagWall.togglePerspectiveList()
	 *
	 * Show PerspectiveList for currentChildParent. If
	 * pvPchild.isRestriction(true), setArrowFocus(pvPchild).
	 */
	void setSelected(final @NonNull Perspective _currentChildParent) {
		assert art.getShowTagLists() || art.isReplaying();
		art.getTagWall().addChild(this);
		if (currentChildParent != _currentChildParent) {
			currentChildParent = _currentChildParent;
			sortedEffectiveFacets = null;
		}
		query().queueOrRedraw(this);
	}

	@Override
	public void redrawCallback() {
		if (!isHidden() && ensureCountsAndNames()) {
			validate();
		}
	}

	/**
	 * Only called by redrawCallback
	 *
	 * @return ensurePrefetched(PREFETCHED_YES) && isQueryValid(). If not, will
	 *         redraw this. If so, update the following:
	 *
	 *         LongestNamedFacet, maxOnCount, onCounts, label,
	 *         countSortedFacets, and effectiveFacets.
	 */
	private boolean ensureCountsAndNames() {
		final boolean result = currentChildParent().ensurePrefetched(PrefetchStatus.PREFETCHED_YES, this)
				&& query().isQueryValid();
		if (result) {
			getSelectedOnCounts(currentChildParent(), this);
			label.maybeSetText(
					UtilString.addCommas(nEffectiveChildren()) + " " + currentChildParent().getName() + " tags");
		}
		return result;
	}

	void validate() {
		if (!isHidden()) {
			final TagWall tagWall = art.getTagWall();
			final Rank connectedRank = tagWall.connectedRank();
			assert connectedRank != null;
			final Rectangle2D rankLabelBounds = tagWall
					.globalToLocal(connectedRank.pVizs.first().rankLabel.getGlobalBounds());
			double y = (int) (rankLabelBounds.getMinY());
			if (IS_DRAW_ABOVE_LABEL) {
				setOffset(art.buttonMargin(), 0.0);
				setHeight(y);
			} else {
				y += art.lineH();
				setOffset(art.buttonMargin(), y);
				setHeight(tagWall.getHeight() - y);
			}
			draw();
		}
	}

	@Override
	public double maxWidth() {
		return Math.rint(0.9 * art.getTagWall().getWidth());
	}

	@Override
	public void setMouseDoc(final String doc) {
		art.setClickDesc(doc);
	}

	/**
	 * Only called by other classes
	 *
	 * Removes from parent and resets.
	 */
	void hide() {
		assert !isHidden() : this;
		removeFromParent();
		// It's confusing if the arrows don't go by natural order if the
		// list is hidden
		currentSortField = DEFAULT_SORT_FIELD;
		currentSortDirection = currentSortField.defaultSortDirection();
	}

	/**
	 * @return currentChildParent, or null if isHidden()
	 */
	@NonNull
	Perspective currentChildParent() {
		assert !isHidden();
		assert currentChildParent != null;
		return currentChildParent;
	}

	public boolean isHidden() {
		return getParent() == null;
	}

	private int nEffectiveChildren() {
		return sortedEffectiveFacets().length;
	}

	/**
	 * Can be called from Rank and UserAction
	 *
	 * @param callback
	 *            if non-null and counts aren't cached, return [total counts]
	 *            and callback when they are.
	 *
	 * @return The onCount that would result from selecting child.
	 */
	public int selectedOnCount(final @NonNull Perspective child, final @Nullable RedrawCallback callback) {
		assert query().isQueryValid();
		final Perspective parent = child.getParent();
		return (parent != null && parent.isRestricted()) ? getSelectedOnCounts(parent, callback)[child.whichChildRaw()]
				: child.getOnCount();
	}

	/**
	 * @param callback
	 *            if non-null and counts aren't cached, return [total counts]
	 *            and callback when they are.
	 *
	 * @return what onCounts would be if there were no filters on
	 *         parentPerspective, or null if query is invalid or if any counts
	 *         aren't up to date. length = nChildrenRaw().
	 */
	@NonNull
	int[] getSelectedOnCounts(final @NonNull Perspective parentPerspective, final @Nullable RedrawCallback callback) {
		final Query query = query();
		assert query.isQueryValid();
		decacheInvalidCounts();
		int[] result = selectedOnCountsCache.get(parentPerspective);
		if (result == null) {
			result = new int[parentPerspective.nChildrenRaw()];
			final int parentNrestrictions = parentPerspective.nDescendentRestrictions();
			final int queryNrestrictions = query.nFilters(true, true, true);
			assert parentNrestrictions <= queryNrestrictions : parentPerspective.path(true, true) + "\n"
					+ query.getName(null) + "\nparentNrestrictions=" + parentNrestrictions + " queryNrestrictions="
					+ queryNrestrictions;
			if (parentNrestrictions == 0) {
				// parent is unrestricted; use onCount as is.
				for (final Perspective child : parentPerspective.getChildrenRaw()) {
					result[child.whichChildRaw()] = child.getOnCount();
				}
				selectedOnCountsCache.put(parentPerspective, result);
			} else if (parentNrestrictions == queryNrestrictions) {
				// ONLY parent is restricted; use child.totalCount
				for (final Perspective child : parentPerspective.getChildrenRaw()) {
					result[child.whichChildRaw()] = child.getTotalCount();
				}
				selectedOnCountsCache.put(parentPerspective, result);
			} else {
				try (final ResultSet rs = query.getOnCountsIgnoringFacet(parentPerspective, this, callback);) {
					// System.out.println("PerspectiveList.getSelectedOnCounts "
					// + parentPerspective + " " + callback + " "
					// + (rs != null));
					if (rs == null) {
						for (final Perspective child : parentPerspective.getChildrenRaw()) {
							result[child.whichChildRaw()] = child.getTotalCount();
						}
					} else {
						getSelectedOnCountsFromRS(result, rs, parentPerspective);
					}
				} catch (final SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	public void getSelectedOnCountsFromRS(@Nullable int[] result, final @NonNull ResultSet rs,
			final @NonNull Perspective parentPerspective) {
		if (result == null) {
			result = new int[parentPerspective.nChildrenRaw()];
		}
		final Query query = query();
		try {
			while (rs.next()) {
				final Perspective child = query.findPerspectiveOrError(rs.getInt(1));
				final int onCount = rs.getInt(2);
				result[child.whichChildRaw()] = onCount;
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		selectedOnCountsCache.put(parentPerspective, result);
	}

	/**
	 * Only called by ensureCountsAndNames() and Rank (via selectedOnCount()).
	 *
	 * Updates countsQueryVersion, and clears selectedOnCountsCache and
	 * sortedEffectiveFacets.
	 */
	private void decacheInvalidCounts() {
		final int queryVersion = query().version();
		assert queryVersion > 0;
		if (countsQueryVersion != queryVersion) {
			sortedEffectiveFacets = null;
			selectedOnCountsCache.clear();
			countsQueryVersion = queryVersion;
		}
	}

	private int lineOffset() {
		return scrollbar.getRowOffset(nInvisibleValues());
	}

	private boolean isScrollbar() {
		return nInvisibleValues() > 0;
	}

	int nInvisibleValues() {
		return Math.max(0, nEffectiveChildren() - nVisibleLines());
	}

	private int nVisibleLines() {
		final double internalH = getHeight() - sortByID.getMaxY();
		return (int) (internalH / art.lineH());
	}

	void draw() {
		final int nInvisibleValues = nInvisibleValues();
		final int lineOffset = UtilMath.constrain(lineOffset(), 0, nInvisibleValues);
		final int nVisibleLines = nVisibleLines();
		final int maxLine = Math.min(nEffectiveChildren(), lineOffset + nVisibleLines);
		if (updateLongestNamedFacet(lineOffset, maxLine)) {
			assert !isHidden();
			moveToFront();
			PerspectiveMarkupAPText.removeAllChildrenAndReclaimCheckboxes(this);
			final double topMargin = validateSortButtons();
			final double bottomMargin = 2.0;
			final double numW = sortByOnCount.getWidth();
			if (nInvisibleValues > 0) {
				if (lineOffset != scrollbar.getRowOffset(nInvisibleValues)) {
					scrollbar.setThumbPercent(lineOffset / (double) nInvisibleValues);
					// setPos calls draw, so our work is done.
					return;
				}
				scrollbar.setOffset(getWidth() - art.scrollbarWidthNmargin(), topMargin);
				scrollbar.setHeight(getHeight() - topMargin - bottomMargin);
				scrollbar.setBufferPercent(nVisibleLines, nEffectiveChildren());
				addChild(scrollbar);
			}
			final double nameW = Math.ceil(sortByID.getWidth() + art.checkBoxWidth());
			addChildren(lineOffset, numW + art.buttonMargin(), topMargin, nameW, maxLine);
		}
	}

	private void addChildren(final int lineOffset, final double numW, double y, final double nameW, final int maxLine) {
		addChild(label);
		addChild(border);
		for (int line = lineOffset; line < maxLine; line++) {
			final Perspective lineChild = getFacet(line);
			final int lineChildOnCount = selectedOnCount(lineChild, this);
			assert lineChildOnCount <= maxChildSelectedOnCount : this + " lineFacet=" + lineChild + " lineFacetOnCount="
					+ lineChildOnCount + " should be <= maxOnCount=" + maxChildSelectedOnCount
					+ " lineChildParent.isRestricted()=" + Util.nonNull(lineChild.getParent()).isRestricted()
					+ " getSelectedOnCounts:\n"
					+ UtilString.valueOfDeep(getSelectedOnCounts(Util.nonNull(lineChild.getParent()), null));

			// redrawer has to be this, in case longestNamedFacet changes
			final PerspectiveMarkupAPText lineFacetLabel = (PerspectiveMarkupAPText) PerspectiveMarkupAPText
					.getFacetText(lineChild.getMarkupElement(), art, numW, nameW,
							UserAction.isDefaultLocationUnderline(art, lineChild, null), lineChildOnCount, this,
							art.buttonMargin(), y, ReplayLocation.DEFAULT_REPLAY);
			// lineFacetLabel.setUnderline(UserAction.isDefaultLocationUnderline(art,
			// lineChild, lineFacetLabel),
			// YesNoMaybe.MAYBE);
			lineFacetLabel.mayHideTransients = false;
			addChild(lineFacetLabel);

			y += art.lineH();
		}
		final double height = !isScrollbar() && y < getHeight() ? y : getHeight();
		setHeight(height);
	}

	/**
	 * Only called by draw()
	 *
	 * layout: <margin> <art.checkBoxWidth> <numW> " "
	 * <name> <margin> <scrollbar> <art.scrollMarginSize>
	 *
	 * Note that each row is a single FacetText, so column placement is dictated
	 * by that, not arbitrary constants we get to set.
	 *
	 * @return the y-coordinate where we can start drawing tags
	 */
	private double validateSortButtons() {
		final double buttonMargin = art.buttonMargin();
		final double y = label.getMaxY() + buttonMargin;

		// art.checkBoxWidth has some extra space on the right built in, so the
		// 0.7 let's us line up more exactly with the boxes.
		addButton(sortBySelection, buttonMargin, y, art.checkBoxWidth() * 0.7);
		addButton(sortByOnCount, sortBySelection.getMaxX() + buttonMargin, y, art.numWidth(maxChildSelectedOnCount));

		final double sortByIDOffset = sortByOnCount.getMaxX() + buttonMargin;
		final double rightMargin = buttonMargin + (isScrollbar() ? art.scrollbarWidthNmargin() : 0.0);
		final double longestNameLength = longestNameLength();
		final double width = Math.max(label.getWidth() + 2.0 * buttonMargin,
				Math.min(maxWidth(), sortByIDOffset + longestNameLength + rightMargin));
		setWidth(width);
		final double sortByIDWidth = Math.min(longestNameLength, width - rightMargin - sortByIDOffset);
		addButton(sortByID, sortByIDOffset, y, sortByIDWidth);
		updateButtonDirections();

		label.setCenterX(width / 2.0);
		return Math.ceil(sortByID.getMaxY() + buttonMargin);
	}

	/**
	 * Layout and addChild button.
	 */
	private void addButton(final @NonNull SortButton<SortBy> button, final double xOffset, final double yOffset,
			double width) {
		width = setButtonWidth(button, width);
		button.setOffset(xOffset, yOffset);
		button.positionChild();
		addChild(button);
	}

	private double setButtonWidth(final @NonNull SortButton<SortBy> button, double width) {
		width = Math.ceil(Math.max(width, art.getStringWidth(SortDirection.A_Z.getLabel())));
		button.setWidth(width);
		button.child.setWidth(width);
		return width;
	}

	/**
	 * Only called by other classes
	 *
	 * @return whether highlighting changed
	 */
	boolean updateHighlighting(final int queryVersion) {
		boolean result = false;
		if (!isHidden()) {
			for (final Iterator<PNode> it = getChildrenIterator(); it.hasNext();) {
				final PNode child = it.next();
				if (child instanceof PerspectiveMarkupAPText
						&& ((PerspectiveMarkupAPText) child).updateHighlighting(queryVersion)) {
					result = true;
				}
			}
		}
		return result;
	}

	/**
	 * @param line
	 * @return facet to display on line according to currentSortField/Direction.
	 *
	 *         Uses effectiveFacets[], countSortedFacets[], or
	 *         selectionSortedFacets[]
	 */
	private @NonNull Perspective getFacet(int line) {
		assert line < nEffectiveChildren() : line + " onCounts.length" + nEffectiveChildren() + " "
				+ currentChildParent() + " " + currentChildParent().nChildrenRaw();
		if (!currentSortDirection.equals(currentSortField.defaultSortDirection())) {
			line = nEffectiveChildren() - line - 1;
		}
		final Perspective result = sortedEffectiveFacets()[line];
		assert result != null : currentChildParent() + " " + line;
		return result;
	}

	private @NonNull Perspective[] sortedEffectiveFacets() {
		decacheInvalidCounts();
		if (sortedEffectiveFacets == null) {
			final List<Perspective> effectiveFacets = new LinkedList<>();
			maxChildSelectedOnCount = 0;
			for (final Perspective child : currentChildParent().getChildrenRaw()) {
				final int childTotalCount = child.getTotalCount();
				if (childTotalCount > 0) {
					effectiveFacets.add(child);
					final int selectedOnCount = selectedOnCount(child, this);
					assert selectedOnCount <= childTotalCount : selectedOnCount + " " + child.path(true, true)
							+ "\n countsQueryVersion=" + countsQueryVersion + " query().version()=" + query().version();
					if (selectedOnCount > maxChildSelectedOnCount) {
						maxChildSelectedOnCount = selectedOnCount;
					}
					if (child.getNameIfCached() != null) {
						updateLongestNamedFacet(child);
					}
				}
			}
			// System.out
			// .println("PerspectiveList.sortedEffectiveFacets " + this + "
			// query().version()=" + query().version()
			// + " maxChildSelectedOnCount=" + maxChildSelectedOnCount + "
			// getSelectedOnCounts:\n"
			// +
			// UtilString.valueOfDeep(getSelectedOnCounts(currentChildParent(),
			// null)));

			sortedEffectiveFacets = effectiveFacets.toArray(new Perspective[effectiveFacets.size()]);
			assert sortedEffectiveFacets.length > 0;
			final SortBy oldField = currentSortField;
			currentSortField = SortBy.SORT_BY_ID;
			setOrderDontRedraw(oldField, currentSortDirection);
		}
		assert sortedEffectiveFacets != null;
		return sortedEffectiveFacets;
	}

	/**
	 * @return whether all facet names on these lines are known.
	 */
	private boolean updateLongestNamedFacet(final int lineOffset, final int maxLine) {
		boolean result = true;
		final Collection<Perspective> uncached = new HashSet<>();
		for (int line = lineOffset; line < maxLine; line++) {
			final Perspective facet = getFacet(line);
			if (facet.getNameIfCached() == null) {
				uncached.add(facet);
				result = false;
			} else if (result) {
				updateLongestNamedFacet(facet);
			}
		}
		query().queueGetNames(uncached, this);
		return result;
	}

	private void updateLongestNamedFacet(final @NonNull Perspective p) {
		final PerspectiveMarkupElement PME = p.getMarkupElement();
		final double facetStringWidth = art.getFacetStringWidth(PME, true, false);
		if (facetStringWidth > longestNameLength()) {
			setLongestNamedFacetMarkupElement(facetStringWidth);
		}
	}

	private void setLongestNamedFacetMarkupElement(final double facetStringWidth) {
		longestNamedFacetMarkupElementLength = facetStringWidth;
	}

	private double longestNameLength() {
		return longestNamedFacetMarkupElementLength;
	}

	@Override
	public void setOrder(final @NonNull SortBy order, final @NonNull SortDirection direction) {
		if (setOrderDontRedraw(order, direction)) {
			draw();
		}
	}

	private boolean setOrderDontRedraw(final @NonNull SortBy order, final @NonNull SortDirection direction) {
		final boolean result = currentSortField != order || currentSortDirection != direction;
		if (result) {
			currentSortDirection = direction;
			if (order != currentSortField) {
				currentSortField = order;
				switch (order) {
				case SORT_BY_ID:
					Arrays.sort(sortedEffectiveFacets(), ID_COMPARATOR);
					break;
				case SORT_BY_SELECTION:
					Arrays.sort(sortedEffectiveFacets(), SELECTED_COMPARATOR);
					break;

				default:
					assert order == SortBy.SORT_BY_ON_COUNT;
					Arrays.sort(sortedEffectiveFacets(), onCountComparator);
					break;
				}
			}
			updateButtonDirections();
		}
		return result;
	}

	void updateButtonDirections() {
		for (final SortButton<SortBy> button : sortButtons) {
			// ensure button is wide enough to display direction label, in case
			// validateSortButtons hasn't been called yet.
			setButtonWidth(button, button.getWidth());
			button.setDirection(currentSortField, currentSortDirection);
		}
	}

	void setFont(final @NonNull Font font) {
		label.setFont(font);
		sortBySelection.setFont(font);
		sortByOnCount.setFont(font);
		sortByID.setFont(font);
		setLongestNamedFacetMarkupElement(0.0);
	}

	@NonNull
	Query query() {
		return art.getQuery();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, currentChildParent());
	}

	final class OnCountComparator extends IntValueComparator<Perspective> {

		@Override
		public int value(final Perspective child) {
			assert getSelectedOnCounts(currentChildParent(), PerspectiveList.this).length > child
					.whichChildRaw() : child.path() + " whichChild=" + child.whichChildRaw() + " listedPerspective="
							+ currentChildParent() + " nChildren=" + Util.nonNull(currentChildParent()).nChildrenRaw();
			return selectedOnCount(child, PerspectiveList.this);
		}
	}

	final static class IDComparator extends IntValueComparator<Perspective> {

		@Override
		public int value(final Perspective child) {
			return -child.getID();
		}
	}

	final static class SelectedComparator extends IntValueComparator<Perspective> {

		@Override
		public int value(final Perspective child) {
			return child.isRestriction() ? 1 : 0;
		}
	}

}
