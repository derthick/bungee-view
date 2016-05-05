package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Paint;
import java.awt.geom.AffineTransform;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public abstract class ButtonWithChild<T extends LazyNode> extends Button {

	/**
	 * An unpickable PNode displayed inside the button; for instance a text
	 * label or icon.
	 */
	public @NonNull T child;

	public ButtonWithChild(final double x, final double y, final double outerW, final double outerH,
			final @Nullable String _disabledMessage, final @Nullable String _mouseDoc, final boolean is3d,
			final @Nullable Paint paintS) {
		super(x, y, outerW, outerH, _disabledMessage, _mouseDoc, is3d, paintS);
		child = dummyT();
	}

	protected abstract @NonNull T dummyT();

	@Override
	void adjustSize(final double outerW, final double outerH) {
		super.adjustSize(outerW, outerH);
		positionChild();
	}

	@Override
	protected @NonNull String adjustSizeErrorMessage(final @Nullable String outerW, final @Nullable String outerH) {
		return super.adjustSizeErrorMessage(outerW, outerH) + PiccoloUtil.ancestorString(child.pNode());
	}

	/**
	 * If isScaleToFit, fit child to our [inner] size with an AffineTransform;
	 * otherwise, center child.
	 */
	public void positionChild() {
		if (isInitted()) {
			if (isScaleToFit) {
				final double xRatio = innerW() / child.getWidth();
				final double yRatio = innerH() / child.getHeight();
				assert xRatio > 0.0 && Double.isFinite(xRatio) && yRatio > 0.0
						&& Double.isFinite(yRatio) : child.getBounds() + " " + innerW() + " " + innerH() + " " + xRatio
								+ " " + yRatio;
				final AffineTransform transform = AffineTransform.getScaleInstance(xRatio, yRatio);
				child.setTransform(transform);
			} else {
				// child.setCenter(getCenter());
				child.setOffset(Math.rint((innerW() - child.getWidth()) / 2.0),
						Math.rint((innerH() - child.getHeight()) / 2.0));
			}
		}
	}

	void fitToChild() {
		final double childW = child.getWidth();
		final double childH = child.getHeight();
		assert childW > 0.0 && childH > 0.0 : childW + " " + childH;
		adjustSize(childW + 2 * borderTwiceStrokeW(), childH + 2 * borderTwiceStrokeW());
	}

	@SuppressWarnings("null")
	private boolean isInitted() {
		return child != null;
	}

}
