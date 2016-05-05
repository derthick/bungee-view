package edu.cmu.cs.bungee.piccoloUtils.gui;

import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * PV Letters, front, or labels
 */
public class SqueezablePNode extends LazyPNode {

	private double prevYscale = Double.NaN;

	private double prevY = Double.NaN;

	/**
	 * Only used by toString()
	 */
	private final String name;

	private boolean parentPNodeInvisible = false;

	public SqueezablePNode(final String _name) {
		name = _name;
		setPickable(false);
	}

	public void setParentPNodesVisible(final boolean isVisible) {
		parentPNodeInvisible = !isVisible;
		revertVisibilty();
	}

	public void layout(final double y, final double yScale) {
		if (yScale != prevYscale || y != prevY) {
			prevYscale = yScale;
			prevY = y;
			revertVisibilty();
		}
	}

	void revertVisibilty() {
		final boolean isVisible = (prevYscale > 0.0 && !parentPNodeInvisible);
		if (isVisible) {
			setTransform(UtilMath.scaleNtranslate(1.0, prevYscale, 0.0, prevY));
		}
		setVisible(isVisible);
	}

	@Override
	public String toString() {
		return UtilString.toString(this, getParent() + " " + name + " prevYscale=" + prevYscale);
	}

}
