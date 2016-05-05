/*

 Created on Jul 7, 2006

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

import static edu.cmu.cs.bungee.javaExtensions.UtilArray.assertNoNaNs;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.constrain;

import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.Point2D;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.Arrow.ArrowPart;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PDimension;

/**
 * Allows dragging to resize parent
 */
public class Boundary extends LazyPNode implements DraggableNode {

	/**
	 * The width of the line drawn. It should probably be even, for the sake of
	 * visualOffset
	 */
	protected static final int DEFAULT_LINE_W = 4;

	private static final float AFFORDANCE_TRANSPARENCY = 0.2f;

	/**
	 * Fixed text to display on rollover
	 */
	public @NonNull String mouseDoc = "Start dragging boundary";

	protected final @NonNull BoundaryCapable parent;

	/**
	 * true iff the border line is to be horizontal
	 */
	protected final boolean isHorizontal;

	protected final @NonNull Arrow arrow = new Arrow(null, 14, 1, 2);

	protected final @NonNull LazyPNode affordance = new LazyPNode();

	protected int lineW = DEFAULT_LINE_W;

	protected double percentOfParentWidth = 0.5;

	/**
	 * visualOffset is added to baseForOffset. It includes an offset
	 * (-lineW()/2) to center the line.
	 */
	protected double visualOffset = 0.0;

	/**
	 * Normally (if Double.isNaN), use parent's width/height as logical
	 * x/yoffset. This sets an explicit offset.
	 *
	 * Only non-Nan for internal boundaries in SelectedItem & Thumbnails.
	 */
	protected double logicalDragPosition = Double.NaN;

	/**
	 * drags are limited to this minimum/maximum (effectively minY iff
	 * isHorizontal). Double.NaN iff not dragging.
	 */
	protected double minDragLimit = Double.NaN;
	protected double maxDragLimit = Double.NaN;

	/**
	 * Current drag coordinate: (in the y dimension iff isHorizontal). Initially
	 * <code>getXOffset() - visualOffset()</code>, then changes follow mouse
	 * moves unconstrained. (For constrained value, use dragW().)
	 *
	 * Generally, Double.NaN if not currently dragging. However inside
	 * dragMinMaxX(), dragX is temporarily set to !isNan while calling
	 * parent.minDragLimit().
	 */
	protected double unconstrainedLogicalDragPosition = Double.NaN;

	public Boundary(final @NonNull BoundaryCapable _parent, final boolean _isHorizontal) {
		setPickableMode(PickableMode.PICKABLE_MODE_ALWAYS);
		parent = _parent;
		isHorizontal = _isHorizontal;
		if (isHorizontal) {
			setHeight(lineW());
			affordance.setHeight(1.0);
		} else {
			setWidth(lineW());
			affordance.setWidth(1.0);
		}
		affordance.setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
		affordance.setTransparency(AFFORDANCE_TRANSPARENCY);
		addChild(affordance);

		arrow.setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
		addChild(arrow);

		setBaseColor(null);
		addInputEventListener(ClickDragHandler.getClickDragHandler());
		maybeSetState(false);
		validate();
	}

	/**
	 * Call after changes to: parent width/height, setLineW(),
	 * setAffordancePercentWidth(), setLogicalDragPosition(), setVisualOffset().
	 */
	public void validate() {
		if (isHorizontal) {
			final double parentWidth = parent.getWidth();
			final double width = parentWidth * percentOfParentWidth;
			setWidth(width);
			affordance.setWidth(width);
			affordance.setYoffset(lineW / 2.0);
			setXoffset((parentWidth - width) / 2.0);

			// This is the only thing that changes during dragging
			setYoffset(drawnPosition());
		} else {
			final double parentHeight = parent.getHeight();
			final double height = parentHeight * percentOfParentWidth;
			setHeight(height);
			affordance.setHeight(height);
			affordance.setXoffset(lineW / 2.0);
			setYoffset((parentHeight - height) / 2.0);

			// This is the only thing that changes during dragging
			setXoffset(drawnPosition());
		}
		setArrowVisibility();
	}

	/**
	 * Only called by validate(), which sets X/Yoffset to drawnPosition()
	 *
	 * @return logicalDragPosition() + visualOffset
	 */
	private double drawnPosition() {
		return logicalDragPosition() + visualOffset;
	}

	/**
	 * @return parent.getWidth/Height() if isNaN(logicalDragPosition), else
	 *         logicalDragPosition.
	 */
	private double logicalDragPosition() {
		final double result = !Double.isNaN(logicalDragPosition) ? logicalDragPosition : parentWidthHeight();
		assert result == (int) result : result;
		return result;
	}

	private double parentWidthHeight() {
		return isHorizontal ? parent.getHeight() : parent.getWidth();
	}

	/**
	 * Normally (if Double.isNaN), use parent's width/height as logical
	 * x/yoffset. This sets an explicit offset.
	 *
	 * Only non-Nan for internal boundaries in SelectedItem & Thumbnails.
	 */
	public void setLogicalDragPosition(final double _logicalDragPosition) {
		if (_logicalDragPosition != logicalDragPosition) {
			assert _logicalDragPosition == (int) _logicalDragPosition : _logicalDragPosition;
			logicalDragPosition = _logicalDragPosition;
			if (isDragging()) {
				unconstrainedLogicalDragPosition = logicalDragPosition;
			}
			validate();
		}
	}

	public void setVisualOffset(final double _visualOffset) {
		final double lineWoffset = halfLineW();
		final double baseVisualOffset = visualOffset + lineWoffset;
		if (_visualOffset != baseVisualOffset) {
			visualOffset = _visualOffset - lineWoffset;
			validate();
		}
	}

	@Override
	public void startDrag(@SuppressWarnings("unused") final Point2D positionRelativeTo) {
		final double[] dragMinMaxX = dragMinMaxX();
		unconstrainedLogicalDragPosition = dragMinMaxX[0];
		minDragLimit = dragMinMaxX[1];
		maxDragLimit = dragMinMaxX[2];
	}

	/**
	 * If !isDragging(), changes dragX <i>temporarily</i> to
	 * <code>X/Yoffset - visualOffset</code> while calling
	 * parent.min/maxDragLimit, then changes it back.
	 *
	 * @return { dragX, minDragLimit, maxDragLimit }
	 *
	 *         If maxDragLimit < minDragLimit, don't allow dragging.
	 */
	protected @NonNull double[] dragMinMaxX() {
		final double[] result = { unconstrainedLogicalDragPosition, minDragLimit, maxDragLimit };
		if (Double.isNaN(unconstrainedLogicalDragPosition)) {
			unconstrainedLogicalDragPosition = (isHorizontal ? getYOffset() : getXOffset()) - visualOffset;
			final double _dragX = unconstrainedLogicalDragPosition;
			final double _minX = parent.minDragLimit(this);
			final double _maxX = parent.maxDragLimit(this);
			unconstrainedLogicalDragPosition = Double.NaN;

			assert _minX == Math.rint(_minX) : getParent() + " " + _minX;
			assert _maxX == Math.rint(_maxX) : getParent() + " " + _maxX;

			result[0] = _dragX;
			result[1] = _minX;
			result[2] = _maxX;
		}
		assert assertNoNaNs(result);
		return result;
	}

	/**
	 * Only called by other classes
	 */
	public double constrainedDragPercentage() {
		final double result = (constrainedLogicalDragPosition() - getMinDragLimit())
				/ (getMaxDragLimit() - getMinDragLimit());
		assert assertInRange(result, 0.0, 1.0);
		return result;
	}

	/**
	 * Only called by other classes.
	 *
	 * @return isDragging(): constrain(dragX, minX, maxX)
	 *
	 *         ........else: getX/YOffset() - visualOffset
	 *
	 *         For default BungeeFrames, this is the width/height to set parent.
	 */
	public double constrainedLogicalDragPosition() {
		// assert getState();
		assert !Double.isNaN(unconstrainedLogicalDragPosition);
		return isDragging() ? UtilMath.constrain(unconstrainedLogicalDragPosition, getMinDragLimit(), getMaxDragLimit())
				: unconstrainedLogicalDragPosition;
	}

	public double getMinDragLimit() {
		assert isDragging();
		return minDragLimit;
	}

	public double getMaxDragLimit() {
		assert isDragging();
		return maxDragLimit;
	}

	@Override
	public void drag(final PDimension delta) {
		assert isDragging();
		unconstrainedLogicalDragPosition += isHorizontal ? delta.getHeight() : delta.getWidth();

		final double oldParentOffset = parentOffset();
		try {
			parent.boundaryDragged(this);
		} catch (final AssertionError e) {
			System.err.println("Warning: While dragging " + this + " to " + "constrain("
					+ unconstrainedLogicalDragPosition + ", " + getMinDragLimit() + ", " + getMaxDragLimit() + ")");
			e.printStackTrace();
		}

		// Our X/Yoffset is relative to parent, so if parent moves, we have to
		// adjust in order to remain stationary.
		final double deltaOffset = parentOffset() - oldParentOffset;
		assert !Double.isNaN(deltaOffset);
		if (deltaOffset != 0.0) {
			unconstrainedLogicalDragPosition -= deltaOffset;
			minDragLimit -= deltaOffset;
			maxDragLimit -= deltaOffset;
			validate();
		} else {
			setArrowVisibility();
		}
	}

	private double parentOffset() {
		final PBounds globalBounds = parent.getGlobalBounds();
		return isHorizontal ? globalBounds.y : globalBounds.x;
	}

	@Override
	public void endDrag(final PInputEvent e) {
		assert getState();
		unconstrainedLogicalDragPosition = Double.NaN;
		minDragLimit = Double.NaN;
		maxDragLimit = Double.NaN;
		final boolean isInsideNode = getGlobalBounds().contains(e.getCanvasPosition());
		if (!isInsideNode) {
			exit();
		}
	}

	@Override
	public void exit() {
		if (!isDragging() && maybeSetState(false)) {
			parent.exitBoundary(this);
			setMouseDoc(null);
			moveToBack();
		}
	}

	public boolean isDragging() {
		return !Double.isNaN(maxDragLimit);
	}

	@Override
	public void enter() {
		// System.out.println("Boundary.enter " + this + " getState()=" +
		// getState());
		if (maybeSetState(true)) {
			setMouseDoc(mouseDoc);
			parent.enterBoundary(this);
		}
	}

	/**
	 * @return whether mouse is over this Boundary or isDragging()
	 */
	boolean getState() {
		final boolean result = getPaint() != null;
		assert !isDragging() || result : isDragging() + " " + result;
		return result;
	}

	/**
	 * @return whether state changed. Do not change to true if dragging is not
	 *         enabled (e.g. for fontSize if there are no ther legal sizes).
	 */
	boolean maybeSetState(final boolean state) {
		boolean result = state != getState();
		if (result) {
			setStateInternal(state);
			final boolean adjustedState = setArrowVisibility();
			if (adjustedState != state) {
				// state==true was rejected because drag range is empty.
				assert state;
				result = false;
			}
		}
		return result;
	}

	private void setStateInternal(final boolean state) {
		assert affordance.getPaint() != null;
		final Paint newPaint = state ? affordance.getPaint() : null;
		assert !isDragging() || newPaint != null : isDragging() + " " + state;
		setPaint(newPaint);
		arrow.setStrokePaint(newPaint);
	}

	/**
	 * @return state. Will be false if drag range is empty.
	 */
	protected boolean setArrowVisibility() {
		final double[] dragMinMaxX = dragMinMaxX();
		final double minDragX = dragMinMaxX[1];
		final double maxDragX = dragMinMaxX[2];
		final boolean isVisible = maxDragX > minDragX;
		setVisible(isVisible);
		final boolean result = isVisible && getState();
		if (result) {
			final double _dragX = constrain(dragMinMaxX[0], minDragX, maxDragX);
			arrow.setVisible(ArrowPart.LEFT_HEAD, _dragX > minDragX);
			arrow.setVisible(ArrowPart.RIGHT_HEAD, _dragX < maxDragX);

			final double arrowOffset = (isHorizontal ? getWidth() : getHeight()) / 2.0;
			final double dragOffset = -_dragX + halfLineW();
			if (isHorizontal) {
				arrow.setEndpoints(arrowOffset, minDragX + dragOffset, arrowOffset, maxDragX + dragOffset);
			} else {
				arrow.setEndpoints(minDragX + dragOffset, arrowOffset, maxDragX + dragOffset, arrowOffset);
			}
			moveAncestorsToFront();
		} else {
			setStateInternal(false);
		}
		return result;
	}

	public boolean isHorizontal() {
		return isHorizontal;
	}

	protected int lineW() {
		return lineW;
	}

	protected double halfLineW() {
		return lineW / 2.0;
	}

	public void setPercentOfParentWidth(final double _percentOfParentWidth) {
		if (_percentOfParentWidth != percentOfParentWidth) {
			percentOfParentWidth = _percentOfParentWidth;
			validate();
		}
	}

	/**
	 * @param baseColor
	 *            defaults to WHITE
	 */
	public void setBaseColor(@Nullable Color baseColor) {
		if (baseColor == null) {
			baseColor = UtilColor.WHITE;
		}
		setPaint(baseColor);
		affordance.setPaint(baseColor);
		arrow.setStrokePaint(baseColor);
	}

	public void setLineW(final int _lineW) {
		lineW = _lineW;
		arrow.setStroke(LazyPPath.getStrokeInstance(lineW));
		arrow.setHeadAndTailSizes(lineW * 3);
		validate();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, parent);
	}

}
