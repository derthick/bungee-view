package edu.cmu.cs.bungee.client.viz.bungeeCore;

import edu.cmu.cs.bungee.client.viz.menusNbuttons.BungeeTextButton;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;

final class SmallWindowButton extends BungeeTextButton {

	SmallWindowButton(final Bungee bungee) {
		super("  3. Use this window size anyway", BungeeConstants.BVBG, UtilColor.LIGHT_GRAY, bungee,
				"Reduce font size and truncate text as necessary");
	}

	@Override
	public void doPick() {
		int maxFontSizeThatFitsInWindow;
		while (art.getFontSize() > (maxFontSizeThatFitsInWindow = art.maxFontSizeThatFitsInWindow())) {
			// loop in case maxFontSizeThatFitsInWindow changes after
			// setFontSize
			art.setMinLegibleFontSize(maxFontSizeThatFitsInWindow);
			art.setFontSize(maxFontSizeThatFitsInWindow);
		}
		art.validateIfReady();
	}

}