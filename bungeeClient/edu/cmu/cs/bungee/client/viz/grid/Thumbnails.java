package edu.cmu.cs.bungee.client.viz.grid;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Boundary;
import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryCapable;
import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryWithLabels;
import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryWithLabels.LabelNoffset;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyContainer;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.MouseDoc;
import edu.cmu.cs.bungee.piccoloUtils.gui.SolidBorder;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;

final class Thumbnails extends LazyContainer implements MouseDoc, BoundaryCapable {

	private static final long YELLOW_SI_COLUMN_OUTLINE_ANIMATION_MS = 600L;
	/**
	 * The always-visible yellow outline around the selected item thumbnail.
	 * Parent is that thumbnail.
	 */
	private final @NonNull SolidBorder selectedThumbOutline;
	/**
	 * The always-visible yellow border that animates to outline the SI column.
	 * Parent is permanently this Thumbnails.
	 */
	private final @NonNull YellowSIcolumnOutline yellowSIcolumnOutline = new YellowSIcolumnOutline();
	@Nullable
	PInterpolatingActivity yellowSIcolumnOutlineAnimation;

	protected @Nullable Boundary boundary;

	Thumbnails() {
		selectedThumbOutline = new SolidBorder(BungeeConstants.OUTLINE_COLOR,
				BungeeConstants.YELLOW_SELECTED_THUMB_OUTLINE_THICKNESS);

		yellowSIcolumnOutline.addChild(
				new SolidBorder(BungeeConstants.OUTLINE_COLOR, BungeeConstants.YELLOW_SI_COLUMN_OUTLINE_THICKNESS));
		yellowSIcolumnOutline.setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
		addChild(yellowSIcolumnOutline);
	}

	void outlineSelectedThumbnail(final @Nullable PNode thumbnail) {
		assert Util.assertMouseProcess();
		if (selectedThumbOutline.getParent() != thumbnail) {
			if (thumbnail != null && thumbnail.getParent() != null) {
				assert thumbnail.getParent() == this;
				thumbnail.addChild(selectedThumbOutline);
				yellowSIcolumnOutline.moveToFront();
				thumbnail.moveToFront();
				animateYellowSIcolumnOutline(thumbnail);
			}
		}
	}

	/**
	 * Only called by outline.
	 */
	void animateYellowSIcolumnOutline(final @NonNull PNode thumbnail) {
		yellowSIcolumnOutline.setBounds(globalToLocal(thumbnail.getGlobalBounds()));
		yellowSIcolumnOutlineAnimation = yellowSIcolumnOutline.animateToBounds(yellowSIcolumnOutlineGoalBounds(),
				YELLOW_SI_COLUMN_OUTLINE_ANIMATION_MS);

		art().getQuery().queueOrRedraw(art().getSelectedItemColumn());
	}

	public void validateYellowSIcolumnOutline() {
		if (yellowSIcolumnOutlineAnimation == null) {
			// System.out.println("Thumbnails.validateYellowOutline " +
			// yellowOutlineGoalBounds()
			// + " grid.getGlobalBounds=" + grid().getGlobalBounds() + "
			// grid().getXOffset()="
			// + grid().getXOffset() + " grid().getFont=" + grid().getFont());
			yellowSIcolumnOutline.setBounds(yellowSIcolumnOutlineGoalBounds());
		}
	}

	private @NonNull Rectangle2D yellowSIcolumnOutlineGoalBounds() {
		final Rectangle2D selectedItemColumnBounds = globalToLocal(art().getSelectedItemColumn().getGlobalBounds());
		return new Rectangle2D.Double(selectedItemColumnBounds.getX(), selectedItemColumnBounds.getY() + 1.0,
				selectedItemColumnBounds.getWidth() - BungeeConstants.YELLOW_SI_COLUMN_OUTLINE_THICKNESS,
				selectedItemColumnBounds.getHeight() - BungeeConstants.YELLOW_SI_COLUMN_OUTLINE_THICKNESS - 1.0);
	}

	boolean updateBrushing(final Set<Perspective> changedFacets) {
		boolean result = false;
		for (final Iterator<PNode> it = getChildrenIterator(); it.hasNext();) {
			final PNode child = it.next();
			if (child instanceof GridElement && ((GridElement) child).getWrapper().updateBrushing(changedFacets)) {
				result = true;
			}
		}
		return result;
	}

	private @NonNull Font getFont() {
		return art().getCurrentFont();
	}

	@Override
	public double minDragLimit(@SuppressWarnings("unused") final @NonNull Boundary boundary1) {
		return grid().minGridW();
	}

	@Override
	public double maxDragLimit(@SuppressWarnings("unused") final @NonNull Boundary boundary1) {
		return getNcols() * grid().gridW;
	}

	@Override
	public void boundaryDragged(final @NonNull Boundary boundary1) {
		final int oldNcols = getNcols();
		final int _nCols = dragPercentage2col(boundary1.constrainedDragPercentage());
		if (art().setDesiredNumResultsColumns(_nCols)) {
			final BoundaryWithLabels boundaryWithLabels = (BoundaryWithLabels) boundary1;
			final APText oldLabel = boundaryWithLabels.getLabel(Integer.toString(oldNcols));
			assert oldLabel != null : oldNcols;
			oldLabel.setTextPaint(UtilColor.WHITE);
			final APText newLabel = boundaryWithLabels.getLabel(Integer.toString(_nCols));
			assert newLabel != null : _nCols;
			newLabel.setTextPaint(UtilColor.YELLOW);
		}
		boundary1.setLogicalDragPosition(boundary1.constrainedLogicalDragPosition());
	}

	private int dragPercentage2col(final double dragPercentage) {
		assert assertInRange(dragPercentage, 0.0, 1.0);
		final int nLabels = maxColumnsThatFit();
		final int result = UtilMath.roundToInt(nLabels / (dragPercentage * (nLabels - 1.0) + 1.0));
		assert isInRange(result, 1, nLabels) : dragPercentage + " " + nLabels + " => " + result;
		return result;
	}

	@Override
	public List<LabelNoffset> getLabels() {
		final int maxColumnsThatFit = maxColumnsThatFit();
		final List<LabelNoffset> result = new ArrayList<>(maxColumnsThatFit);
		for (int i = 1; i <= maxColumnsThatFit; i++) {
			result.add(new LabelNoffset(Integer.toString(i), col2dragPercentage(i)));
		}
		// System.out.println("Thumbnails.getLabels " + maxColumnsThatFit + " "
		// + result);
		return result;
	}

	private double col2dragPercentage(final double col) {
		final double nLabels = maxColumnsThatFit();
		assert assertInRange(col, 1.0, nLabels);
		final double result = 1.0 - nLabels * (col - 1.0) / (nLabels - 1.0) / col;
		assert isInRange(result, 0.0, 1.0) : col + " " + result;
		return result;
	}

	private int maxColumnsThatFit() {
		return grid().maxColumnsThatFitWithoutScrollbar();
	}

	@Override
	public void enterBoundary(final @NonNull Boundary boundary1) {
		final APText label2 = ((BoundaryWithLabels) boundary1).getLabel(Integer.toString(getNcols()));
		assert label2 != null;
		label2.setTextPaint(UtilColor.YELLOW);
	}

	@Override
	public void exitBoundary(final Boundary boundary1) {
		// System.out.println("Thumbnails.exitBoundary gridW=" + grid().gridW);
		boundary1.setLogicalDragPosition(grid().gridW);
	}

	private @NonNull Bungee art() {
		return grid().art;
	}

	private @NonNull ResultsGrid grid() {
		final ResultsGrid result = (ResultsGrid) getParent();
		assert result != null;
		return result;
	}

	@Override
	public void setMouseDoc(final @Nullable String doc) {
		grid().setMouseDoc(doc);
	}

	@Override
	public String toString() {
		return UtilString.toString(this, getNvisibleRows() + "×" + getNcols());
	}

	protected int getNcols() {
		return grid().nCols;
	}

	protected int getNvisibleRows() {
		return Math.max(0, grid().nVisibleRows);
	}

	/**
	 * We're about to draw grid. Remove thumbs and set boundary location.
	 */
	void retainOutlineAndBoundaryGrid() {
		for (final PNode child : getChildrenAsPNodeArray()) {
			if (child != selectedThumbOutline && child != yellowSIcolumnOutline && child != boundary) {
				removeChild(child);
			}
		}
		createOrDeleteBoundary();
	}

	private void createOrDeleteBoundary() {
		// setWidthHeight();
		if (art().getShowBoundaries()) {
			if (boundary == null) {
				// setWidthHeight();
				boundary = new BoundaryWithLabels(this, false, getFont());
				boundary.setPercentOfParentWidth(1.03);
				boundary.mouseDoc = "Start dragging to change the number of columns.";
				addChild(boundary);
			}
			assert boundary != null;
			@NonNull
			final Boundary boundary1 = boundary;
			boundary1.validate();
			if (!boundary1.isDragging()) {
				// setWidthHeight();
				boundary1.setLogicalDragPosition(grid().gridW);
			}
		} else if (boundary != null) {
			removeChild(boundary);
			boundary = null;
		}
		// System.out.println("Thumbnails.createOrDeleteBoundary
		// art().getShowBoundaries()=" + art().getShowBoundaries()
		// + " => " + boundary);
	}

	void setWidthHeight() {
		// System.out.println("Thumbnails.setWidthHeight " + (getNcols() *
		// grid().gridW) + " x "
		// + (getNvisibleRows() * grid().gridH));
		setWidthHeight(getNcols() * grid().gridW, getNvisibleRows() * grid().gridH);
	}

	void setFeatures() {
		createOrDeleteBoundary();
	}

	class YellowSIcolumnOutline extends LazyPNode {

		@Override
		public void endResizeBounds() {
			yellowSIcolumnOutlineAnimation = null;
			validateYellowSIcolumnOutline();
		}

	}

}