/*

 Created on Mar 4, 2005

 The Bungee View applet lets you search, browse, and data-mine an image collection.
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

package edu.cmu.cs.bungee.piccoloUtils.gui;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.constrain;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.awt.Color;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.piccoloUtils.gui.VScrollbar.ScrollDirection;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * Only traffics in percentages; doesn't know about text lines (except for
 * setBufferPercent and setVisRowOffset, which use lines to compute percentages
 * and then forgets).
 */
public class VScrollbar extends LazyPNode implements MouseDoc {

	enum ScrollDirection {
		@NonNull UP(-1.0, "˄", "Scroll up one line"),

		@NonNull DOWN(+1.0, "˅", "Scroll down one line");

		private final double delta;
		private final @NonNull String symbol;
		private final @NonNull String mouseDoc;

		ScrollDirection(final double _delta, final @NonNull String _symbol, final @NonNull String _mouseDoc) {
			delta = _delta;
			symbol = _symbol;
			mouseDoc = _mouseDoc;
		}

		/**
		 * @return -1.0 for UP; +1.0 for DOWN.
		 */
		double delta() {
			return delta;
		}

		@NonNull
		String symbol() {
			return symbol;
		}

		@NonNull
		String mouseDoc() {
			return mouseDoc;
		}
	}

	static final int INITIAL_DELAY_MS = 1000;
	static final int REPEAT_MS = 200;

	private static final @NonNull VScrollHandler VSCROLL_HANDLER = new VScrollHandler();

	static final double BUTTON_MARGIN = 1.0;

	/**
	 * Initialize to 100,000, because constructor sets width first, and width is
	 * constrained by height. Using INFINITY or MAX_VALUE leads to NaN when
	 * multiplied by 0.0.
	 */
	private static final double INITIAL_HEIGHT = 100_000.0;
	private static final double MIN_WIDTH = 4.0;

	/**
	 * percentage to scroll to move one line (when up or down button is
	 * pressed): 1.0 / (nTotal - nVisible);
	 */
	double lineScrollPercent = 0.1;

	protected final @NonNull ScrollButton upButton;

	protected final @NonNull ScrollButton downButton;

	protected final @NonNull PNode thumb;

	protected final @NonNull PNode bar;

	/**
	 * height of bar, not including up/down buttons or gaps between bar and
	 * buttons.
	 */
	protected double barHeight = INITIAL_HEIGHT;

	/**
	 * min y position of thumb. Equals width+BUTTON_MARGIN
	 */
	protected double minThumbY;

	/**
	 * 1.0 / (maxThumbY - minThumbY)
	 */
	protected double ratio;

	/**
	 * constrained to [minThumbY, maxThumbY]
	 */
	protected double dragPosition;

	protected boolean isDragging;

	/**
	 * Run this when scroll position changes
	 */
	private final @NonNull Runnable action;

	/**
	 * y position of thumb, in the range [minThumbY, minThumbY + barHeight -
	 * visibleThumbSize();]
	 */
	private double thumbY;

	/**
	 * percentage to scroll to move one page (when bar is clicked): nVisible /
	 * (nTotal - nVisible);
	 */
	private double pageScrollPercent = 0.1;

	/**
	 * nVisible / nTotal
	 *
	 * Changed (and setVisible() changed) only by setBufferPercent().
	 */
	private double percentVisible;
	private boolean isInitted = false;

	/**
	 * VScrollbar with very large height and xPosition = 0.
	 */
	public VScrollbar(final double width, final @Nullable Color bgColor, final @NonNull Color fgColor,
			final @NonNull Runnable _action) {
		this(0.0, width, /* 3.0 * (width + BUTTON_MARGIN) */ INITIAL_HEIGHT, bgColor, fgColor, _action);
	}

	protected VScrollbar(final double xPosition, final double width, final double totalHeight,
			final @Nullable Color bgColor, final @NonNull Color fgColor, final @NonNull Runnable _action) {
		assert width >= MIN_WIDTH : width;
		action = _action;
		setWidthHeight(width, totalHeight);
		assert width == getWidth();
		thumbY = minThumbY;

		bar = new PNode();
		bar.setPaint(bgColor);
		bar.setBounds(xPosition, minThumbY, width, barHeight);
		bar.setPickable(false);
		addChild(bar);

		thumb = new PNode();
		thumb.setPaint(fgColor);
		thumb.setWidth(width);
		addChild(thumb);

		@Nullable
		final Color buttonBgColor = bgColor == null ? null : bgColor.brighter();
		final Color buttonFgColor = fgColor.brighter();
		assert buttonFgColor != null;
		upButton = new ScrollButton(xPosition, 0.0, width, ScrollDirection.UP, buttonFgColor, buttonBgColor);
		addChild(upButton);
		upButton.updateTransparency();
		downButton = new ScrollButton(xPosition, barHeight + width + 2.0 * BUTTON_MARGIN, width, ScrollDirection.DOWN,
				buttonFgColor, buttonBgColor);
		addChild(downButton);
		downButton.updateTransparency();

		isInitted = true;
		resize();
		addInputEventListener(VSCROLL_HANDLER);
	}

	/**
	 * Call this when width, height, or bufferPercent changes.
	 */
	private void resize() {
		if (isInitted) {
			final double width = getWidth();
			assert barHeight >= width : "barHeight < width!   getHeight=" + getHeight() + " barHeight=" + barHeight
					+ " width=" + width;

			upButton.adjustSize(width, width);
			downButton.adjustSize(width, width);
			downButton.setYoffset(barHeight + width + 2.0 * BUTTON_MARGIN);

			final double maxThumbY = getMaxThumbY();
			ratio = 1.0 / (maxThumbY - minThumbY);
			thumbY = UtilMath.constrain(thumbY, minThumbY, maxThumbY);
			setThumbBounds();
			bar.setBounds(0.0, minThumbY, width, barHeight);
		}
	}

	private double getMaxThumbY() {
		final double maxThumbY = minThumbY + (percentVisible < 1.0 ? barHeight - visibleThumbSize() : 0.0);
		assert maxThumbY >= minThumbY : " minThumbY=" + minThumbY + " maxThumbY=" + maxThumbY + " barHeight="
				+ barHeight + " percentVisible=" + percentVisible;
		return maxThumbY;
	}

	// // @SuppressWarnings("null")
	// private boolean isInitted() {
	// // ProGuard was optimizing this away.
	// final ScrollButton up2 = up;
	// return up2 != null;
	// }

	/**
	 * Set thumb to top
	 */
	public void reset() {
		setThumbPosition(minThumbY);
	}

	@Override
	public boolean setBounds(final double x, final double y, final double w, double h) {
		final boolean isChangeWH = w != getWidth() || h != getHeight();
		if (isChangeWH) {
			assert w == (int) w : w;
			h = Math.max(h, 3.0 * w + 2.0 * BUTTON_MARGIN);
		}
		final boolean result = super.setBounds(x, y, w, h);
		assert result == isChangeWH : isChangeWH + " " + x + " " + y;
		if (isChangeWH) {
			assert w > 0.0 : "width=" + w + " barHeight=" + barHeight;
			minThumbY = w + BUTTON_MARGIN;
			barHeight = h - 2.0 * minThumbY;
			resize();
		}
		return result;
	}

	// @Override
	// public boolean setWidth(double w) {
	// final boolean result = super.setWidth(Math.min(barHeight, w));
	// if (result) {
	// final double width = getWidth();
	// assert width > 0.0 : "width=" + width + " barHeight=" + barHeight;
	// minThumbY = width + BUTTON_MARGIN;
	// resize();
	// }
	// return result;
	// }
	//
	// @Override
	// public boolean setHeight(final double h) {
	// assert h >= 0.0 : h;
	// final boolean result = super.setHeight(h);
	// if (result) {
	// barHeight = getHeight() - 2.0 * (getWidth() + BUTTON_MARGIN);
	// resize();
	// }
	// return result;
	// }

	/**
	 * Called only by TextBox.redisplay() and ResultsGrid.resizeScrollbar()
	 *
	 * setVisible and position thumb. Use lineH to compute height.
	 */
	public void setBufferPercent(final int nVisibleLines, final int nTotalLines, final double lineH) {
		// System.out.println("VScrollbar.setBufferPercent " + nVisibleLines + "
		// / " + nTotalLines);
		assert lineH > 0.0;
		final double newH = nVisibleLines * lineH;
		assert newH >= minH() || nVisibleLines == nTotalLines : "nVisibleLines == " + nVisibleLines
				+ ", but must be at least " + UtilMath.intCeil(minH() / lineH) + " (getWidth()=" + getWidth()
				+ " lineH=" + lineH + " nTotalLines=" + nTotalLines + ")";
		setBufferPercentInternal(nVisibleLines, nTotalLines, newH);

		// // Avoid calling resize() twice;
		// boolean mustResize = setHeight(newH);
		// if (mustResize) {
		// setBarHeight();
		// }
		// if (setBufferPercent(nVisibleLines, nTotalLines)) {
		// mustResize = false;
		// }
		// if (mustResize) {
		// resize();
		// }
		// // UtilString.indentLess("VScrollbar.setBufferPercent exit");
	}

	/**
	 * @return whether percentVisible changes. If so, setVisible(), delta,
	 *         pageDelta and position thumb.
	 */
	public boolean setBufferPercent(final int nVisibleLines, final int nTotalLines) {
		return setBufferPercentInternal(nVisibleLines, nTotalLines, getHeight());
	}

	/**
	 * @return whether percentVisible changes. If so, setVisible(), delta,
	 *         pageDelta and position thumb.
	 */
	private boolean setBufferPercentInternal(final int nVisibleLines, final int nTotalLines, final double newH) {
		assert assertInRange(nVisibleLines, 0, nTotalLines);
		final double nVisibleLinesDouble = nVisibleLines;
		final double newPercentVisible = nVisibleLinesDouble / nTotalLines;
		final boolean result = newPercentVisible != percentVisible;
		if (result) {
			percentVisible = newPercentVisible;
			final boolean isVisible = nVisibleLines < nTotalLines;
			setVisible(isVisible);
			if (isVisible) {
				final double nInvisibleLines = nTotalLines - nVisibleLines;
				lineScrollPercent = 1.0 / nInvisibleLines;
				pageScrollPercent = nVisibleLinesDouble / nInvisibleLines;
				if (!setHeight(newH)) {
					resize();
				}
			}
		}
		return result;
	}

	public double minH() {
		return 3.0 * getWidth() + 2.0 * BUTTON_MARGIN;
	}

	void buttonPressed(final @NonNull ScrollDirection scrollDirection) {
		incfThumbPercent(lineScrollPercent * scrollDirection.delta());
	}

	void page(final @NonNull ScrollDirection scrollDirection) {
		incfThumbPercent(pageScrollPercent * scrollDirection.delta());
	}

	void incfThumbPercent(final double percent) {
		final double thumbPercent = constrain(getThumbPercent() + percent, 0.0, 1.0);
		setThumbPercent(thumbPercent);
	}

	/**
	 * @return in [0, nInvisibleRows]
	 */
	public int getRowOffset(final int nInvisibleRows) {
		return nInvisibleRows <= 0 ? 0 : (int) (getThumbPercent() * nInvisibleRows + 0.5);
	}

	/**
	 * @return value between 0.0 and 1.0 representing thumbY as a percentage of
	 *         [minThumbY, maxThumbY]
	 */
	public double getThumbPercent() {
		assert assertInRange(minThumbY, 0.0, thumbY);
		final double result = (thumbY - minThumbY) * ratio;
		assert isInRange(result, 0.0, 1.0 + UtilMath.ABSOLUTE_SLOP) : thumbY + " " + minThumbY + " " + ratio + " "
				+ result;
		return result;
	}

	public void setThumbPercent(final double thumbPercent) {
		assert isInRange(thumbPercent, 0.0, 1.0) : thumbPercent + " (corresponding to visRowOffset of "
				+ (thumbPercent / lineScrollPercent) + " and nInvisibleRows=" + (1.0 / lineScrollPercent) + ") thumbY="
				+ (thumbPercent / ratio + minThumbY);
		if (Double.isInfinite(ratio)) {
			assert thumbPercent == 0.0 && thumbY == minThumbY : "thumbPercent=" + thumbPercent + " thumbY=" + thumbY
					+ " minThumbY=" + minThumbY + " barHeight=" + barHeight + " width=" + getWidth()
					+ " visibleThumbSize=" + visibleThumbSize();
		} else {
			setThumbPosition(thumbPercent / ratio + minThumbY);
		}
	}

	public void setThumbPosition(double newthumbY) {
		// System.out.println("VScrollbar.setThumbPosition " + newthumbY);
		newthumbY = constrain(newthumbY, minThumbY, getMaxThumbY());
		if (newthumbY != thumbY) {
			thumbY = newthumbY;
			setThumbBounds();
			action.run();
			upButton.updateTransparency();
			downButton.updateTransparency();
		}
	}

	public void setThumbBounds() {
		assert thumb.getX() == 0.0;
		thumb.setBounds(0.0, thumbY, getWidth(), visibleThumbSize());
	}

	public void drag(final @NonNull PInputEvent e) {
		dragPosition += e.getDelta().getHeight();
		setThumbPosition(dragPosition);
	}

	void startDrag() {
		isDragging = true;
		dragPosition = thumbY - minThumbY;
	}

	public void endDrag(@SuppressWarnings("unused") final @NonNull PInputEvent e) {
		isDragging = false;
	}

	/**
	 * @return Math.max(width, percentVisible * barHeight).
	 */
	private double visibleThumbSize() {
		return Math.max(getWidth(), percentVisible * barHeight);
	}

	// @Override
	// public void setMouseDoc(final @Nullable String doc) {
	// if (getParent() instanceof MouseDoc) {
	// ((MouseDoc) getParent()).setMouseDoc(doc);
	// }
	// }

	void setMouseDoc(final @NonNull PNode node, final @NonNull PInputEvent e, final boolean state) {
		assert node == this;
		String desc = null;
		if (state) {
			final PNode pickedNode = e.getPickedNode();
			if (pickedNode == node) {
				desc = "Page " + getPageDirection(e);
			} else {
				desc = "Start dragging scrollbar";
			}
		}
		setMouseDoc(desc);
	}

	protected @NonNull ScrollDirection getPageDirection(final @NonNull PInputEvent e) {
		final double y = e.getPositionRelativeTo(this).getY();
		ScrollDirection scrollDirection = null;
		if (y <= thumbY) {
			scrollDirection = ScrollDirection.UP;
		} else if (y >= thumbY + visibleThumbSize()) {
			scrollDirection = ScrollDirection.DOWN;
		}
		// If mouse enters from outside of the window, y will be the value where
		// it last exited!
		// assert scrollDirection != 0 : y + " " + thumbY + " " + thumbSize;
		assert scrollDirection != null : y + " " + thumbY + " " + (thumbY + visibleThumbSize());
		return scrollDirection;
	}

	public void setVisRowOffset(final int visRowOffset) {
		// Treat 0*Infinity as 0;
		final double thumbPercent = visRowOffset == 0 ? 0.0 : visRowOffset * lineScrollPercent;
		assert isInRange(thumbPercent, 0.0, 1.0) : thumbPercent + " " + lineScrollPercent + " " + visRowOffset;
		setThumbPercent(thumbPercent);
	}

}

class VScrollHandler extends MyInputEventHandler<VScrollbar> {

	/**
	 * Non-null iff timer!=null && mouse pressed.
	 */
	@Nullable
	ScrollDirection lastPageDirection = null;

	VScrollHandler() {
		super(VScrollbar.class);
		initTimer(VScrollbar.INITIAL_DELAY_MS, VScrollbar.REPEAT_MS);
	}

	@Override
	public boolean press(final @NonNull VScrollbar node, final @NonNull PInputEvent e) {
		final PNode pickedNode = e.getPickedNode();
		if (pickedNode == node && timer != null) {
			super.press(node);
			lastPageDirection = node.getPageDirection(e);
		}
		return false;
	}

	@Override
	public boolean click(final @NonNull VScrollbar node) {
		boolean result = false;
		if (lastPageDirection != null) {
			node.page(lastPageDirection);
			result = true;
		}
		return result;
	}

	@Override
	public boolean click(final @NonNull VScrollbar node, final @NonNull PInputEvent e) {
		boolean result = true;
		final PNode pickedNode = e.getPickedNode();
		if (pickedNode == node) {
			final ScrollDirection pageDirection = node.getPageDirection(e);
			node.page(pageDirection);
		} else if (pickedNode == node.thumb) {
			node.startDrag();
		} else {
			result = false;
		}
		return result;
	}

	@Override
	public boolean drag(final @NonNull VScrollbar node, final @NonNull PInputEvent e) {
		final PNode pickedNode = e.getPickedNode();
		if (pickedNode == node.thumb) {
			node.drag(e);
			return true;
		}
		return false;
	}

	@Override
	public boolean release(final @NonNull VScrollbar node, final @NonNull PInputEvent e) {
		boolean result = super.release(node);
		lastPageDirection = null;
		if (e.getPickedNode() == node.thumb) {
			node.endDrag(e);
			result = true;
		}
		return result;
	}

	@Override
	public boolean enter(final @NonNull VScrollbar node, final @NonNull PInputEvent e) {
		node.setMouseDoc(node, e, true);
		return true;
	}

	@Override
	public boolean exit(final @NonNull VScrollbar node, final @NonNull PInputEvent e) {
		node.setMouseDoc(node, e, false);
		return true;
	}

}