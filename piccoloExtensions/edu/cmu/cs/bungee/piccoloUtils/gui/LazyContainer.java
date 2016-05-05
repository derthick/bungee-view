package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Paint;

/**
 * Nodes that don't need paint, which will be more efficient if the bounds are
 * empty, but for which having an abstract width and height is convenient for
 * laying out children.
 *
 * NO, just set paint=null;
 */
public class LazyContainer extends LazyPNode {

	@Override
	public void setPaint(@SuppressWarnings("unused") final Paint paint) {
		assert false;
	}

}
