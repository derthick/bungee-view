package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Font;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.graph.Edge.LabelLocation;
import edu.umd.cs.piccolo.event.PInputEventListener;

/**
 * There are two ways to use Arrow. Preferred is setEndpoints, where "left" and
 * "right" are just names for the two ends, with leftTail and rightHead being
 * visible by default. Arrow sets a transform to place the ends.
 *
 * It's convenient to let the "left" end remain fixed at x==0, and to point left
 * or right by changing this Arrow's bounds, instead of the application having
 * to mess with the offset. Setting setLength<0 accomplishes this, and
 * isReversed tests whether it is.
 *
 * PerspectiveViz uses the transform to scale the bars, so handles Arrow
 * placement on its own. It sets head visibility to point left or right as
 * appropriate. xOffset is always for the tail.
 */
public class Arrow extends LazyPNode {

	public enum ArrowPart {
		LEFT_TAIL, LEFT_HEAD, RIGHT_TAIL, RIGHT_HEAD, LINE
	}

	private final @NonNull LazyPPath line;
	private final @NonNull LazyPPath leftTail;
	private final @NonNull LazyPPath rightTail;
	/**
	 * always points to the left
	 */
	private final @NonNull LazyPPath leftHead;
	private final @NonNull LazyPPath rightHead;

	private final @NonNull List<APText> labels = new ArrayList<>(3);
	private final @NonNull List<Double> labelOffsetPercents = new ArrayList<>(3);

	float arcH = 0f;

	/**
	 * tails are invisible initially. RIGHT_HEAD is visible iff length>0; else
	 * LEFT_HEAD.
	 */
	public Arrow(final @Nullable Paint strokePaint, final int headAndTailSize, final int length, final int strokeW) {
		line = new LazyPPath();
		leftHead = new LazyPPath();
		rightHead = new LazyPPath();
		setStroke(LazyPPath.getStrokeInstance(strokeW));
		leftTail = new LazyPPath();
		rightTail = new LazyPPath();
		setStrokePaint(strokePaint);

		setHeadAndTailSizes(headAndTailSize);
		setVisible(ArrowPart.RIGHT_TAIL, false);
		setVisible(ArrowPart.LEFT_TAIL, false);

		addChild(line);
		addChild(leftHead);
		addChild(rightHead);
		addChild(leftTail);
		addChild(rightTail);
		setLengthAndDirection(length);
	}

	public void setVisible(final @NonNull ArrowPart part, final boolean state) {
		getPart(part).setVisible(state);
	}

	public void setStroke(final @NonNull Stroke stroke) {
		line.setStroke(stroke);
	}

	private LazyPPath getPart(final ArrowPart part) {
		LazyPPath result = null;
		switch (part) {
		case LEFT_HEAD:
			result = leftHead;
			break;
		case RIGHT_HEAD:
			result = rightHead;
			break;
		case LEFT_TAIL:
			result = leftTail;
			break;
		case RIGHT_TAIL:
			result = rightTail;
			break;
		case LINE:
			result = line;
			break;
		default:
			assert false;
			break;
		}
		return result;
	}

	public void setStrokePaint(final Paint color) {
		leftHead.setPaint(color);
		rightHead.setPaint(color);
		leftTail.setPaint(color);
		rightTail.setPaint(color);
		line.setStrokePaint(color);
		colorLabels(color);
	}

	private void colorLabels(final Paint color) {
		for (final APText label : labels) {
			if (label != null) {
				label.setTextPaint(color);
			}
		}
	}

	public void setHeadAndTailSizes(final int headAndTailSize) {
		setSizes(headAndTailSize, ArrowPart.LEFT_HEAD, ArrowPart.RIGHT_HEAD, ArrowPart.LEFT_TAIL, ArrowPart.RIGHT_TAIL);
	}

	private void setSizes(final int size, final ArrowPart... parts) {
		for (final ArrowPart part : parts) {
			setSize(part, size);
		}
	}

	private void setSize(final ArrowPart part, final int size) {
		switch (part) {
		case LEFT_HEAD:
		case RIGHT_HEAD:
			setHeadSize(part, size);
			break;
		case LEFT_TAIL:
		case RIGHT_TAIL:
			setTailSize(part, size);
			break;
		case LINE:
			line.setStroke(LazyPPath.getStrokeInstance(size));
			break;
		default:
			assert false;
			break;
		}
	}

	private double maxPartSize() {
		double result = 0.0;
		for (final ArrowPart part : ArrowPart.values()) {
			result = Math.max(result, getPart(part).getBoundsReference().height);
		}
		return result;
	}

	/**
	 * Percent of arrowhead that extends beyond line length. 0.5f makes head
	 * behave like tail.
	 */
	private static final float HEAD_OFFSET_PERCENT = 0.5f;

	/**
	 * x: [-size, 0] or [0, size]
	 *
	 * y: [-size/2, size/2]
	 */
	private void setHeadSize(final ArrowPart part, final float size) {
		final float halfSize = (size / 2f) + 1f;
		final float headOffset = size * HEAD_OFFSET_PERCENT;

		// these coords point to the right.
		final float[] xp = { -size + headOffset, -size + headOffset, headOffset };
		final float[] yp = { -halfSize, halfSize, 0f };

		if ((part == ArrowPart.RIGHT_HEAD) == isReversed()) {
			// oops, make it point left.
			for (int j = 0; j < xp.length; j++) {
				xp[j] = -xp[j];
				yp[j] = -yp[j];
			}
		}
		getPart(part).setPathToPolyline(xp, yp);
	}

	/**
	 * It's convenient to let the "left" end remain fixed at x==0, and to point
	 * left or right by changing this Arrow's bounds, instead of the application
	 * having to mess with the offset. Setting setLength<0 accomplishes this,
	 * and isReversed tests whether it is.
	 *
	 * @return whether rightHead is to the left of leftHead.
	 */
	private boolean isReversed() {
		return rightHead.getX() + rightHead.getWidth() / 2.0 < 0.0;
	}

	private void setTailSize(final ArrowPart part, final double size) {
		final LazyPPath tail = getPart(part);
		final float halfSize = (float) (size / 2);
		final float fSize = (float) size;
		tail.setPathToEllipse(-halfSize, -halfSize, fSize, fSize);
	}

	/**
	 * setLength, and set exactly on head visible (right head visible iff length
	 * > 0)
	 */
	public void setLengthAndDirection(final int length) {
		setLength(length);
		setVisible(ArrowPart.LEFT_HEAD, length < 0);
		setVisible(ArrowPart.RIGHT_HEAD, length > 0);
		// leftHead.setXoffset(rightHead.getXOffset());
	}

	/**
	 * set transform. Does not change visibility of anything.
	 */
	void setEndpoints(final double leftX, final double leftY, final double rightX, final double rightY) {
		setLength((int) Point2D.distance(leftX, leftY, rightX, rightY));
		final AffineTransform affineTransform = getTransformReference(true);
		affineTransform.setToTranslation(leftX, leftY);
		affineTransform.rotate(Math.atan2(rightY - leftY, rightX - leftX));
		placeLabels();
	}

	/**
	 * Make line length, and rightHead/Tail.x = length.
	 */
	public void setLength(final int length) {
		line.setPathTo(new Arc2D.Float(0f, -arcH * 0.75f, length, arcH * 1.5f, 0f, 180f, Arc2D.OPEN));
		rightHead.setXoffset(length);
		rightTail.setX(length - rightTail.getWidth() / 2.0);
		placeLabels();
		setBoundsFromChildrenBounds();
	}

	/**
	 * @param names
	 *            labels for LEFT_LABEL, CENTER_LABEL, RIGHT_LABEL
	 */
	void addLabels(final @NonNull List<String> names, final @NonNull Font font) {
		assert names.size() == 3;
		for (int index = 0; index < names.size(); index++) {
			addLabel(names.get(index), font, index);
			setLabelOffset(LabelLocation.values()[index]);
		}
		placeLabels();
	}

	protected void setLabelOffset(final LabelLocation labelLocation) {
		double offsetPercent;
		switch (labelLocation) {
		case LEFT_LABEL:
			offsetPercent = 0.0;
			break;
		case CENTER_LABEL:
			offsetPercent = 0.5;
			break;

		default:
			assert labelLocation == LabelLocation.RIGHT_LABEL;
			offsetPercent = 1.0;
			break;
		}
		setOffset(labelLocation.ordinal(), offsetPercent);
	}

	// TODO Remove unused code found by UCDetector
	// void addEquidistantLabels(final @NonNull List<String> names, final
	// @NonNull Font font) {
	// final double percentage = 1.0 / (names.size() - 1);
	// for (int i = 0; i < names.size(); i++) {
	// addLabel(names.get(i), font, i);
	// setOffset(i, i * percentage);
	// }
	// placeLabels();
	// }

	void addLabels(final @NonNull List<String> names, final @NonNull List<Double> offsetPercents,
			final @NonNull Font font) {
		for (int index = 0; index < names.size(); index++) {
			addLabel(names.get(index), font, index);
			setOffset(index, offsetPercents.get(index));
		}
		placeLabels();
	}

	private void setOffset(final int index, final double offsetPercent) {
		if (labelOffsetPercents.size() == index) {
			labelOffsetPercents.add(offsetPercent);
		} else {
			assert index < labelOffsetPercents.size() : index + " " + labelOffsetPercents.size();
			labelOffsetPercents.set(index, offsetPercent);
		}
	}

	private @Nullable APText addLabel(final @Nullable String string, final @NonNull Font font, final int index) {
		APText label = null;
		if (string == null) {
			if (index < labels.size()) {
				setLabel(null, index);
			}
		} else {
			for (int i = labels.size(); i < index; i++) {
				labels.add(null);
			}
			if (index >= labels.size() || (label = getLabel(index)) == null) {
				setLabel(label = APText.oneLineLabel(font), index);
				label.setTextPaint(line.getStrokePaint());
				addChild(label);
			}
			label.maybeSetText(string);
		}
		return label;
	}

	private void setLabel(final APText apText, final int index) {
		if (labels.size() == index) {
			labels.add(apText);
		} else {
			assert index < labels.size() : index + " " + labels.size();
			labels.set(index, apText);
		}
	}

	private void placeLabels() {
		final boolean upsideDown = Math.cos(getGlobalRotation()) < 0.0;
		final double lineLength = line.getWidth();
		final float y = Math.max(-arcH * 0.75f, (float) maxPartSize() / 2f);
		for (int i = 0; i < labels.size(); i++) {
			final APText label = labels.get(i);
			if (label != null) {
				label.getTransformReference(true).setToIdentity();
				final double x = lineLength * labelOffsetPercents.get(i) - label.getCenterX();
				label.translate(x + (upsideDown ? label.getWidth() : 0.0), y);
				if (upsideDown) {
					label.setRotation(Math.PI);
				}
			}
		}
	}

	// private APText getLabel(final @NonNull LabelLocation labelLocation) {
	// return labels.get(labelLocation.ordinal());
	// }

	private APText getLabel(final int index) {
		return labels.get(index);
	}

	@Nullable
	APText getLabel(final String s) {
		APText result = null;
		for (final APText label : labels) {
			if (label.getText().equals(s)) {
				result = label;
				break;
			}
		}
		return result;
	}

	@Override
	// Only called by other classes
	public void addInputEventListener(final PInputEventListener listener) {
		leftHead.addInputEventListener(listener);
		rightHead.addInputEventListener(listener);
		leftTail.addInputEventListener(listener);
		rightTail.addInputEventListener(listener);
		line.addInputEventListener(listener);
	}

}
