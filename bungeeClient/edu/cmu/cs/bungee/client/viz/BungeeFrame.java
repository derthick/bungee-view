package edu.cmu.cs.bungee.client.viz;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInteger;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.awt.Color;
import java.awt.Font;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Boundary;
import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryCapable;
import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryWithLabels.LabelNoffset;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyContainer;
import edu.cmu.cs.bungee.piccoloUtils.gui.MouseDoc;
import edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil;

public abstract class BungeeFrame extends LazyContainer implements MouseDoc, BoundaryCapable {

	private static final double LABEL_SCALE = 2.0;

	/**
	 * Allow room for yellow outline.
	 */
	private static final double BOTTOM_MARGIN = BungeeConstants.YELLOW_SI_COLUMN_OUTLINE_THICKNESS;

	public final @NonNull Bungee art;

	public final @NonNull Query query;

	protected final @NonNull APText label;

	private final boolean isBoundaryHorizontal;

	protected @Nullable Boundary boundary = null;

	private boolean isInitted = false;

	protected BungeeFrame(final @NonNull Bungee _art, final @NonNull Color labelColor, final @NonNull String labelText,
			final boolean _isBoundaryHorizontal) {
		art = _art;
		query = art.getQuery();
		isBoundaryHorizontal = _isBoundaryHorizontal;

		label = art.oneLineLabel();
		label.setScale(LABEL_SCALE);
		label.setTextPaint(labelColor);
		label.setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
		label.maybeSetText(labelText);
		addChild(label);
	}

	protected void initted() {
		if (!isInitted()) {
			setFeatures();
			forceValidate();
			isInitted = true;
		}
	}

	public boolean isInitted() {
		return isInitted;
	}

	protected void setInitted(final boolean _isInitted) {
		isInitted = _isInitted;
	}

	/**
	 * @return whether anything changed. Call forceValidate() if so.
	 */
	public boolean validate(final double w1, final double h1) { // NO_UCD (use
		// default)

		assert isInRange(w1, minWidth(), maxWidth()) : this + " width=" + w1 + " min-max: " + minWidth() + " - "
				+ maxWidth();
		assert isInRange(h1, minHeight(), maxHeight()) : this + " height=" + h1 + " min-max: " + minHeight() + "-"
				+ maxHeight();

		final boolean isChanged = setWidthHeight(w1, h1);
		if (isChanged && isInitted()) {
			forceValidate();
		}
		return isChanged;
	}

	private void forceValidate() {
		assert Util.assertMouseProcess();
		label.setCenterX(((int) getWidth()) / 2);
		initBoundary();
		validateInternal();
	}

	/**
	 * Currently called by forceValidate(), *.setFont(),
	 * SelectedItem.boundaryDragged(), and Header.setFeatures().
	 *
	 * Override to do class-specific tasks.
	 */
	public void validateInternal() {
		setFont(art.getCurrentFont());
	}

	/**
	 * Override this to check changes to any feature except font and
	 * getShowBoundaries.
	 */
	public void setFeatures() {
		setFont(art.getCurrentFont());

		// in case getShowBoundaries changed
		initBoundary();
	}

	/**
	 * Override to do class-specific tasks
	 */
	protected boolean setFont(final @NonNull Font font) {
		final boolean result = getFont() != font;
		assert result == (getFontSize() != font.getSize());
		if (result) {
			label.setFont(font);
			label.setCenterX(((int) getWidth()) / 2);
		}
		return result;
	}

	protected int getFontSize() {
		return getFont().getSize();
	}

	public @NonNull Font getFont() {
		final Font result = label.getFont();
		assert result != null;
		return result;
	}

	private void initBoundary() {
		boundary = initBoundary(boundary,
				art.getShowBoundaries() && (isBoundaryHorizontal || followingColumn(1) != null));
	}

	/**
	 * @return @NonNull iff isShow
	 */
	protected @Nullable Boundary initBoundary(@Nullable Boundary b, final boolean isShow) {
		if (isShow) {
			if (b == null) {
				b = getBoundary();
			} else {
				b.validate();
			}
			addChild(b);
		} else if (b != null) {
			b.removeFromParent();
			b = null;
		}
		return b;
	}

	/**
	 * Called only by initBoundary()
	 *
	 * @return a Boundary with its margin set.
	 */
	protected @NonNull Boundary getBoundary() {
		final Boundary result = new Boundary(this, isBoundaryHorizontal);
		result.setVisualOffset(art.interFrameMarginSize() / 2.0);
		return result;
	}

	/**
	 * Visually offset boundary from it's logical offset by
	 * interFrameMarginSize/2.
	 */
	protected void resetBoundaryOffsetIfNotDragging() {
		if (boundary != null && !boundary.isDragging()) {
			assert boundary != null;
			boundary.setVisualOffset(art.interFrameMarginSize() / 2.0);
		}
	}

	@Override
	public boolean setBounds(final double x, final double y, final double width, final double height) {
		assert assertInteger(width);
		assert assertInteger(height);
		assert isInRange(width, minWidth(), maxWidth()) : this + " width=" + width + " min-max: " + minWidth() + " - "
				+ maxWidth();
		assert isInRange(height, minHeight(), maxHeight()) : this + " height=" + height + " min-max: " + minHeight()
				+ "-" + maxHeight();
		final boolean result = super.setBounds(x, y, width, height);
		return result;
	}

	@Override
	public void setMouseDoc(final @Nullable String doc) {
		art.setClickDesc(doc);
	}

	/**
	 * Doesn't subtract for scrollbar
	 *
	 * @return w - 2 * internalMargin()
	 */
	public double usableWifNoScrollbar() {
		assert getWidth() >= minWidth() : this + " w=" + getWidth() + " minWidth=" + minWidth() + " fontSize="
				+ getFontSize();
		return getWidth() - art.twiceInternalColumnMargin;
	}

	/**
	 * @return the margin around the column on EACH SIDE. Always equal to an
	 *         int.
	 */
	public double internalXmargin() {
		return art.internalColumnMargin;
	}

	/**
	 * @return the margin between logical sections. Always equal to an int.
	 */
	protected double internalYmargin() {
		return art.lineH();
	}

	protected double getTopMargin() {
		assert label.getParent() == this;
		final double maxY = label.getMaxY();

		// final double height = getHeight();
		// if (maxY > height) {
		// final double labelYoffset = label.getYOffset();
		// final double labelHeight = label.getHeight();
		// final double labelScale = label.getScale();
		// assert false : " label.getYOffset()=" + labelYoffset + "
		// label.getHeight()=" + labelHeight
		// + " label.getScale()=" + labelScale + " label.getMaxY()=" + maxY + "
		// height=" + height
		// + " isInitted=" + isInitted() + " " +
		// PiccoloUtil.ancestorString(label);
		// }

		final double buttonMargin = art.buttonMargin();
		final double result = maxY + buttonMargin;
		assert isInRange(result, 0.0, getHeight()) : "result=" + result + " label.getMaxY()=" + maxY + " buttonMargin="
				+ buttonMargin;
		return result;

		// return label.getMaxY() + art.buttonMargin();
	}

	@SuppressWarnings("static-method")
	// Can't be static because TagWall overrides it non-staticly
	protected double getBottomMargin() {
		return BOTTOM_MARGIN;
	}

	@Override
	public double minDragLimit(final @NonNull Boundary boundary1) {
		assert !boundary1.isHorizontal();
		final double minW = minWidth() - slop(UtilArray.sublistTo(art.columns, this));
		assert minW == (int) minW : minW + " " + PiccoloUtil.ancestorString(this) + " getScale=" + getScale()
				+ " minWidth()=" + minWidth() + " slop(Util.sublistTo(art.columns, this))="
				+ slop(UtilArray.sublistTo(art.columns, this));
		return minW;
	}

	@Override
	public double maxDragLimit(@SuppressWarnings("unused") final @NonNull Boundary boundary1) {
		final double maxW = getWidth() + slop(UtilArray.sublist(art.columns, followingColumn(1)));
		assert maxW == (int) maxW : maxW + " " + PiccoloUtil.ancestorString(this) + " getScale=" + getScale();
		return maxW;
	}

	@Override
	// This must not depend on frames being validated or having updated fonts,
	// so use getStringWidth
	// instead of getWidth, for instance. It MUST depend on art.getFontSize.
	public double minWidth() {
		final String text = label.getText();
		assert text != null;
		final double result = art.getStringWidth(text) * LABEL_SCALE;
		assert result == (int) result : result + " " + PiccoloUtil.ancestorString(this) + " getScale=" + getScale();
		return result;
	}

	@Override
	public double maxWidth() {
		return Double.POSITIVE_INFINITY;
	}

	@Override
	public double minHeight() {
		final double result = art.lineH() * LABEL_SCALE * 5.0;
		assert result == (int) result : result + " " + PiccoloUtil.ancestorString(this) + " getScale=" + getScale();
		return result;
	}

	@Override
	public double maxHeight() {
		return art.getHeight();
	}

	private static double slop(final @NonNull Collection<BungeeFrame> columns) {
		double slop = 0.0;
		for (final BungeeFrame column : columns) {
			slop += column.slop();
		}
		return slop;
	}

	private double slop() {
		assert getWidth() == (int) getWidth() : getWidth() + PiccoloUtil.ancestorString(this);
		final double result = Math.max(0.0, getWidth() - minWidth());
		assert result == (int) result : result + PiccoloUtil.ancestorString(this);
		return result;
	}

	@Override
	// Default drag action which changes our xOffset and width. Used by TagWall,
	// TopTagsViz, and ResultsGrid.
	public void boundaryDragged(final @NonNull Boundary boundary1) {
		assert !isBoundaryHorizontal : this;
		art.updateBoundary(boundary1);
	}

	@Override
	public void exitBoundary(@SuppressWarnings("unused") final Boundary _boundary) {
		art.getGrid().validateYellowSIcolumnOutline();
	}

	@Override
	public List<LabelNoffset> getLabels() {
		return null;
	}

	public void setXoffset(final int direction, final double amount) {
		setXoffset(getXOffset() + direction * amount);
	}

	public @Nullable BungeeFrame followingColumn(final int direction) {
		return art.followingColumn(this, direction);
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "");
	}

	/**
	 * Called only by Bungee.setFramesVisibility(), when a setTooSmall or Error
	 * message appears/disappears.
	 *
	 * Like setVisible, except if isVisible, make sure it's appropriate (e.g.
	 * that it's initted).
	 */
	public void maybeSetVisible(final boolean isVisible) {
		setVisible(isVisible);
	}

	// TODO Remove unused code found by UCDetector
	// public void mayHideTransients() {
	// art.mayHideTransients();
	// }

	public void doHideTransients() {
		// override this
	}

	public void setExitOnError(@SuppressWarnings("unused") final boolean isExit) {
		// override this
	}

}
