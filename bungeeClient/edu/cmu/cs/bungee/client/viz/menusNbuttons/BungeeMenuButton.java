package edu.cmu.cs.bungee.client.viz.menusNbuttons;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.MenuButton;
import edu.cmu.cs.bungee.piccoloUtils.gui.MenuItem;

/**
 * Wants to extend BungeeTextButton, too.
 */
class BungeeMenuButton extends MenuButton {

	private final @NonNull Bungee art;

	public BungeeMenuButton(final @NonNull MenuItem item, final @Nullable Paint bg, final @NonNull Color fg,
			final @NonNull Font _font, final @NonNull Bungee _art) {
		super(item, bg, fg, _font);
		art = _art;
	}

	// @Override
	// public void mayHideTransients(@SuppressWarnings("unused") final PNode
	// node) {
	// art.mayHideTransients();
	// }

	@Override
	public void setMouseDoc(final boolean state) {
		if (isEnabled()) {
			art.setClickDesc(state ? getMouseDoc() : null);
		} else {
			art.setNoClickDesc(state ? "Command Unavailable: " + getMouseDoc() : null);
		}
	}

	@Override
	// Keep equal to BungeeTextButton.showDisabledMessage
	protected void showDisabledError() {
		art.setTip("Command Unavailable: " + getMouseDoc());
	}

}
