package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Font;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Compute layout as if height is infinite. Paint will take into account
 * scrollOffset and height.
 */
class ScrollingAPText extends APText {

	/**
	 * Number of lines "above" displayed text.
	 */
	private int scrollOffsetLines = 0;

	/**
	 * constrainWidth/Height default to false; isWrap defaults to true.
	 * justification defaults to LEFT_ALIGNMENT.
	 */
	ScrollingAPText(final @NonNull Font _font) {
		super(_font);
		setConstrainWidthToTextWidth(false);
		setConstrainHeightToTextHeight(false);
	}

	public void setOffsetLines(final int _scrollOffsetLines) {
		if (_scrollOffsetLines != scrollOffsetLines) {
			scrollOffsetLines = _scrollOffsetLines;
			invalidatePaint();
		}
	}

	@Override
	public void setConstrainHeightToTextHeight(final boolean _constrainHeightToTextHeight) {
		assert !_constrainHeightToTextHeight;
		super.setConstrainHeightToTextHeight(_constrainHeightToTextHeight);
	}

	@Override
	protected float getTextTop() {
		return super.getTextTop() - scrollOffsetLines * getLineH();
	}

	@Override
	protected boolean shouldPaintLine(final float bottomY, final float y) {
		return super.shouldPaintLine(bottomY, y) && y >= super.getTextTop();
	}

	@Override
	public boolean setBounds(final double x, final double y, final double w, final double h) {
		assert h == 0.0 || w > getFont().getSize() : w;
		return super.setBounds(x, y, w, h);
	}

}
