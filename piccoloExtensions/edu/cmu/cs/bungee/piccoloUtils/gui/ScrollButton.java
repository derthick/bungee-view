package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.Font;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.piccoloUtils.gui.VScrollbar.ScrollDirection;

class ScrollButton extends TextButton {

	private final @NonNull ScrollDirection scrollDirection;

	protected static final @NonNull ButtonHandler SCROLL_BUTTON_HANDLER = new ButtonHandler();

	static {
		SCROLL_BUTTON_HANDLER.initTimer(VScrollbar.INITIAL_DELAY_MS, VScrollbar.REPEAT_MS);
	}

	ScrollButton(final double x, final double y, final double size, final @NonNull ScrollDirection _scrollDirection,
			final @NonNull Color fgColor, final @Nullable Color bgColor) {
		super(_scrollDirection.symbol(), APText.fontForLineH("SansSerif", Font.BOLD, size), x, y, -1.0, -1.0,
				"Can't scroll any further in this direction", _scrollDirection.mouseDoc(), true, fgColor, bgColor);
		assert getWidth() <= size && getHeight() <= size : size + " " + getBounds() + " "
				+ APText.fontForLineH("SansSerif", Font.BOLD, size);
		scrollDirection = _scrollDirection;
		removeInputEventListener(BUTTON_HANDLER);
		addInputEventListener(SCROLL_BUTTON_HANDLER);
	}

	@Override
	public boolean isEnabled() {
		final VScrollbar vScrollbar = (VScrollbar) getParent();
		assert vScrollbar != null : PiccoloUtil.ancestorString(this);
		double thumbOffsetPercent = vScrollbar.getThumbPercent();
		if (scrollDirection == ScrollDirection.DOWN) {
			thumbOffsetPercent = 1.0 - thumbOffsetPercent;
		}
		return thumbOffsetPercent >= vScrollbar.lineScrollPercent;
	}

	@Override
	public void doPick() {
		((VScrollbar) getParent()).buttonPressed(scrollDirection);
	}
}