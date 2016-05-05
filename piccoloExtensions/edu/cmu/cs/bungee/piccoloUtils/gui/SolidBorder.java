package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.Graphics2D;

import org.eclipse.jdt.annotation.NonNull;

import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PPaintContext;

public class SolidBorder extends BevelBorder {

	// TODO Remove unused code found by UCDetector
	// public SolidBorder(final @NonNull Color color) {
	// this(color, DEFAULT_STROKE_W);
	// }

	public SolidBorder(final @NonNull Color color, final int _strokeW) {
		super(false, /* highlightOuter */color, /* highlightInner */color, _strokeW);
		setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
	}

	@Override
	protected void paint(final PPaintContext paintContext) {
		final int strokeW = getStrokeW();
		if (strokeW > 0 && getTransparency() > 0f) {
			final Graphics2D g = paintContext.getGraphics();
			g.setStroke(LazyPPath.getStrokeInstance(strokeW));
			g.setPaint(highlightOuter);

			final PBounds bounds = getBorderBounds();
			// each stroke is centered on an edge, so divide by 2
			final double halfStrokeWidth = strokeW / 2.0;
			final double x = bounds.getX() - halfStrokeWidth;
			final double y = bounds.getY() - halfStrokeWidth;
			final double right = bounds.getMaxX() + halfStrokeWidth;
			final double bottom = bounds.getMaxY() + halfStrokeWidth;
			g.drawRect((int) x, (int) y, (int) (right - x), (int) (bottom - y));

			setBounds(x - halfStrokeWidth, y - halfStrokeWidth, right - x + strokeW, bottom - y + strokeW);
		}
	}
}
