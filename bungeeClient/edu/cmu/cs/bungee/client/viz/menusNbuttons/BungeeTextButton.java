package edu.cmu.cs.bungee.client.viz.menusNbuttons;

import java.awt.Color;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.TextButton;

/**
 * Uses _art.getDefaultFont(), art.mayHideTransients()
 */
public class BungeeTextButton extends TextButton {

	protected final Bungee art;

	protected BungeeTextButton(final @NonNull String text, final Color textColor, final Color bgColor,
			final Bungee _art, final String documentation) {
		super(text, _art.getCurrentFont(), /* x */0.0, /* y */0.0, /* outerW */-1.0, /* outerH */-1.0,
				/* disabledMessage */null, documentation, /* fadeFactor1 */
				true, textColor, bgColor);
		art = _art;
	}

	@Override
	public double minWidth() {
		// return Math.ceil(publicScale * (art.getStringWidth(getText()) + 2));
		return outerW();
	}

	// @Override
	// public void mayHideTransients(@SuppressWarnings("unused") final PNode
	// node) {
	// art.mayHideTransients();
	// }

}
