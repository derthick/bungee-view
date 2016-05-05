package edu.cmu.cs.bungee.client.viz.menusNbuttons;

import java.awt.Color;
import java.awt.Paint;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.Menu;
import edu.cmu.cs.bungee.piccoloUtils.gui.MenuButton;
import edu.cmu.cs.bungee.piccoloUtils.gui.MenuItem;

public class BungeeMenu extends Menu {

	final @NonNull public Bungee art;

	public BungeeMenu(final @Nullable Paint _bg, final @NonNull Color _fg, final @NonNull Bungee _art) {
		super(_bg, _fg, _art.getCurrentFont());
		art = _art;
	}

	@Override
	protected @NonNull MenuButton getMenuButton(final @NonNull MenuItem item) {
		return new BungeeMenuButton(item, null, fg, font, art);
	}

	@Override
	protected void setMouseDoc(final boolean state) {
		final String doc = state
				? (visible ? "Close this menu without doing anything (as will pressing ESCAPE)" : mouseDoc) : null;
		art.setClickDesc(doc);
	}

	@Override
	public void setMouseDoc(final @Nullable String doc) {
		art.setClickDesc(doc);
	}

}
