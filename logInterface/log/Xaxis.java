package log;

import java.awt.Color;
import java.text.DateFormat;
import java.util.Date;

import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.util.PDimension;

class Xaxis extends LazyPNode {

	// protected static final long serialVersionUID = 4971597589329044277L;

	final Date min;
	final Date max;
	long zoomMin;
	long zoomMax;
	private final int nTicks = 5;
	private final double tickLength = 6;

	Xaxis(final Date _min, final Date _max) {
		min = _min;
		max = _max;
		zoomMin = min.getTime();
		zoomMax = max.getTime();
		addInputEventListener(new AxisEventHandler());
	}

	void draw(final double length) {
		removeAllChildren();
		setBounds(0, 0, length, 100);
		for (int i = 0; i < nTicks; i++) {
			final LazyPNode tick = new LazyPNode();
			final double x = i * length / (nTicks - 1);
			tick.setBounds(x, 0, 1, tickLength);
			tick.setPaint(Color.black);
			addChild(tick);

			final long mid = zoomMin + ((zoomMax - zoomMin) * i) / (nTicks - 1);
			final APText label = new APText();
			label.maybeSetText(DateFormat.getDateInstance().format(
					new Date(mid)));
			label.setOffset(Math.rint(x - label.getWidth() / 2), tickLength);
			addChild(label);
		}
	}

	double encode(final Date value) {
		final double percent = (value.getTime() - zoomMin)
				/ (double) (zoomMax - zoomMin);
		// System.out.println("x encode " + percent + " " + value.getTime());
		return Math.rint(getWidth() * percent);
	}

	private class AxisEventHandler extends MyInputEventHandler<Xaxis> {

		private double dragStartOffset;

		AxisEventHandler() {
			super(Xaxis.class);
		}

		@Override
		public boolean press(final Xaxis node, final PInputEvent e) {
			assert node == Xaxis.this;
			dragStartOffset = e.getPositionRelativeTo(node).getX();
			return true;
		}

		@Override
		public boolean drag(@SuppressWarnings("unused") final Xaxis node,
				final PInputEvent e) {
			final PDimension delta = e.getDelta();
			double vertical = delta.getHeight();
			double horizontal = delta.getWidth();
			// If you want to just pan, zooming screws you up, and vice-versa,
			// so
			// choose one or the other.
			if (Math.abs(vertical) > Math.abs(horizontal)) {
				horizontal = 0;
			} else {
				vertical = 0;
			}
			double deltaZoom = Math.pow(2, -vertical / 20.0);
			final long minTime = min.getTime();
			final long maxTime = max.getTime();
			final long range = maxTime - minTime;
			final long zoomRange = zoomMax - zoomMin;
			final double logicalWidth = getWidth() * range / zoomRange;
			final double leftEdge = getWidth() * (zoomMin - minTime)
					/ zoomRange;
			final double newLogicalWidth = logicalWidth * deltaZoom;
			double newLeftEdge = 0;
			if (newLogicalWidth < getWidth()) {
				zoomMin = minTime;
				zoomMax = maxTime;
			} else {
				// recalculate zoom after rounding newLogicalWidth
				deltaZoom = newLogicalWidth / logicalWidth;
				final double pan = -horizontal;
				newLeftEdge = UtilMath.constrain(leftEdge + pan
						+ (leftEdge + dragStartOffset) * (deltaZoom - 1), 0,
						newLogicalWidth - getWidth());
				zoomMin = minTime
						+ (long) (newLeftEdge * range / newLogicalWidth);
				zoomMax = minTime
						+ (long) ((newLeftEdge + getWidth()) * range / newLogicalWidth);
			}
			((Chart) getParent()).redraw();
			return true;
		}
	}
}
