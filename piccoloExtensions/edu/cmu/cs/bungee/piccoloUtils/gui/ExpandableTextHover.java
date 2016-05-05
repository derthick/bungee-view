package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.geom.Point2D;

import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;

public class ExpandableTextHover extends MyInputEventHandler<ExpandableText> {

	public static final ExpandableTextHover EXPANDABLE_TEXT_HOVER = new ExpandableTextHover();

	ExpandableTextHover() {
		super(ExpandableText.class);
	}

	@Override
	public boolean enter(final ExpandableText node) {
		node.expand();

		// return false so MenuClickHandler will also be called.
		return false;
	}

	// This will make the summary text disappear, so it won't be
	// immediately entered again as it should be.
	@Override
	public boolean exit(final ExpandableText node, final PInputEvent e) {
		final Point2D point = e.getPositionRelativeTo((PNode) node);
		if (!node.getBounds().contains(point)) {
			node.contract();
		}
		// maybeHideTransients(e);

		// return false so MenuClickHandler will also be called.
		return false;
	}
}