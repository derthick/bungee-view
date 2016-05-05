package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;

public class LabeledVScrollbar extends VScrollbar {

	public final @NonNull LazyPNode labels;

	public LabeledVScrollbar(final double width, final @Nullable Color _bg, final @NonNull Color _fg,
			final @NonNull Color labelPaint, final @NonNull Runnable _action) {
		super(width, _bg, _fg, _action);
		labels = new LazyPNode();
		// labels.setVisible(false);
		labels.setPickable(false);
		labels.setChildrenPickable(false);

		// width will be determined by BoxedText widths.
		// labels.setBounds((int) (1.2 * width), sposMin, 1.0,
		// // sheightFromHeight(height)
		// sheight);
		labels.setBounds((int) (1.2 * width), minThumbY, 1.0, barHeight);
		// labels.setScale(1.0, (sheight + 2.0 * sposMin) / sheight);
		// labels.setOffset(0.0, -sposMin);
		labels.setPaint(labelPaint);
		// labels.setTransparency(0.5f);
		addChild(labels);
		labels.moveToBack();
	}

	@SuppressWarnings("null")
	@Override
	public boolean setHeight(final double h) {
		final boolean result = super.setHeight(h);
		if (labels != null) {
			labels.setHeight(barHeight);
		}
		return result;
	}

	@Override
	void setMouseDoc(final @NonNull PNode node, final @NonNull PInputEvent e, final boolean state) {
		super.setMouseDoc(node, e, state);
		// System.out.println("LabeledVScrollbar.mouseDoc " + node + " state="
		// + state);
		if (state) {
			// Make sure labels are on top of SelectedItem image
			getParent().moveToFront();
		}
		labels.setVisible(state || isDragging);
	}

	@Override
	public void endDrag(final @NonNull PInputEvent e) {
		super.endDrag(e);
		if (!getGlobalBounds().contains(e.getCanvasPosition())) {
			labels.setVisible(false);
		}
	}

	@Override
	public void setVisible(final boolean state) {
		if (getVisible() != state) {
			super.setVisible(state);
			labels.setVisible(false);
		}
		if (state) {
			labels.moveAncestorsToFront();
		}
	}

}
