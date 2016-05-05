package edu.cmu.cs.bungee.client.viz.bungeeCore;

import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode.PickableMode;

class InitialHelp extends APText {

	/**
	 * constrainWidth/Height and isWrap default to true. justification defaults
	 * to LEFT_ALIGNMENT.
	 */
	InitialHelp(final Bungee bungee) {
		super(bungee.getCurrentFont());
		scale(2.0);
		setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
		setWrap(true);
		setTextPaint(BungeeConstants.TEXT_FG_COLOR);
		maybeSetText("Click on a category,\nand then click on its tags\nto dive into the collection.");
	}

	@Override
	public boolean setWidth(final double w) {
		return super.setWidth(Math.rint(w / getScale()));
	}
}