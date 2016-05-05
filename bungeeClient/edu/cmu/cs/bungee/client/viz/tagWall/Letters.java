package edu.cmu.cs.bungee.client.viz.tagWall;

import java.awt.geom.Point2D;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.piccoloUtils.gui.ClickDragHandler;
import edu.cmu.cs.bungee.piccoloUtils.gui.DraggableNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.SqueezablePNode;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.util.PDimension;

/**
 * This supports dragging to pan/zoom. LetterLabeled supports typing/clicking on
 * prefixes.
 */
public class Letters extends SqueezablePNode implements DraggableNode {

	final @NonNull PerspectiveViz perspectiveViz;

	double dragStartXoffset;

	Letters(final @NonNull PerspectiveViz pv) {
		super("Letters");
		perspectiveViz = pv;
		super.setWidth((int) pv.visibleWidth());
		addInputEventListener(ClickDragHandler.getClickDragHandler());
		setPickableMode(PickableMode.PICKABLE_MODE_AUTOMATIC);
	}

	@Override
	public void layout(final double foldH, final double yScale) {
		setHeight(foldH);
		super.layout(0.0, yScale);
	}

	@Override
	public void setVisible(final boolean isVisible) {
		if (getVisible() != isVisible) {
			super.setVisible(isVisible);
			if (isVisible && letterLabeled() != null) {
				letterLabeled().validate();
			}
		}
	}

	@Override
	public boolean setWidth(final double width) {
		final boolean result = super.setWidth(width);
		if (letterLabeled() != null) {
			letterLabeled().setWidth(width);
		}
		return result;
	}

	private LetterLabeled letterLabeled() {
		return perspectiveViz.letterLabeled;
	}

	@Override
	public void startDrag(final Point2D positionRelativeTo) {
		dragStartXoffset = positionRelativeTo.getX();
	}

	@Override
	public void drag(final PDimension delta) {
		double dy = delta.getHeight();
		double dx = delta.getWidth();

		// If you want to just pan, zooming screws you up, and vice-versa,
		// so choose one or the other.
		if (Math.abs(dy) > Math.abs(dx)) {
			dx = 0.0;
		} else {
			dy = 0.0;
		}

		double deltaZoom = Math.pow(2.0, -dy / 20.0);
		final double visibleWidth = perspectiveViz.getVisibleW();
		final double logicalWidth = perspectiveViz.getLogicalWidth();
		final double rawLogicalWidth = Math.max(visibleWidth, logicalWidth * deltaZoom);
		// recalculate zoom after rounding newLogicalWidth
		deltaZoom = rawLogicalWidth / logicalWidth;

		final double leftEdge = perspectiveViz.getLeftEdge();
		final double rawLeftEdge = UtilMath.constrain(leftEdge - dx + (leftEdge + dragStartXoffset) * (deltaZoom - 1.0),
				0.0, rawLogicalWidth - visibleWidth);
		assert rawLeftEdge >= 0.0 && rawLeftEdge + visibleWidth <= rawLogicalWidth : rawLeftEdge + "/" + rawLogicalWidth
				+ " " + visibleWidth;
		perspectiveViz.setLogicalBounds(rawLeftEdge, rawLogicalWidth);
	}

	Bungee art() {
		return perspectiveViz.art();
	}

	@Override
	public void setMouseDoc(final String doc) {
		art().setClickDesc(doc);
	}

	@Override
	public void enter() {
		setMouseDoc("Drag mouse up/down to zoom; right/left to pan");
	}

	@Override
	public void exit() {
		setMouseDoc(null);
	}

	@Override
	public void endDrag(@SuppressWarnings("unused") final PInputEvent e) {
		// no-op
	}

}
