package edu.cmu.cs.bungee.client.viz.header;

import java.awt.Component;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.menusNbuttons.BungeeMenu;
import edu.cmu.cs.bungee.piccoloUtils.gui.AbstractMenuItem;
import edu.cmu.cs.bungee.piccoloUtils.gui.MenuButton;

class SortMenu extends BungeeMenu {

	public SortMenu(final @NonNull Bungee _art) {
		super(BungeeConstants.HEADER_BG_COLOR, BungeeConstants.HEADER_FG_COLOR, _art);
		setLabelJustification(Component.LEFT_ALIGNMENT);
		mouseDoc = "Choose how Matches are Sorted";
		addButton(new ReorderCommand("Random", "Show thumbnails in random order", SortLabelNMenu.ORDER_BY_RANDOM));
		addButton(new ReorderCommand("ID", "Order thumbnails by their database ID", SortLabelNMenu.ORDER_BY_ID));
		updateButtons();
		// for (int facetTypeID = 1; facetTypeID <= art.getQuery().nAttributes;
		// facetTypeID++) {
		// final Perspective facetType =
		// art.getQuery().findPerspectiveIfCached(facetTypeID);
		// if (facetType != null) {
		// addButton(
		// new ReorderCommand(facetType.getName(), "Order thumbnails by this Tag
		// Category", facetTypeID));
		// }
		// }
	}

	public void updateButtons() {
		final int[] facetTypesWithoutButton = new int[art.getQuery().nAttributes + 1];
		for (int i = 0; i < facetTypesWithoutButton.length; i++) {
			facetTypesWithoutButton[i] = i;
		}
		for (final MenuButton button : buttons) {
			final ReorderCommand menuItem = (ReorderCommand) button.getMenuItem();
			final int facetTypeOrSpecial = menuItem.facetTypeOrSpecial;
			if (facetTypeOrSpecial > 0) {
				final Perspective facetType = art.getQuery().findPerspectiveIfCached(facetTypeOrSpecial);
				if (facetType == null) {
					removeButton(menuItem);
				}
				facetTypesWithoutButton[facetTypeOrSpecial] = 0;
			}
		}
		for (final int facetTypeID : facetTypesWithoutButton) {
			if (facetTypeID > 0) {
				final Perspective facetType = art.getQuery().findPerspectiveIfCached(facetTypeID);
				if (facetType != null) {
					addButton(new ReorderCommand(facetType.getName(), "Order thumbnails by this Tag Category",
							facetTypeID));
				}
			}
		}
	}

	private class ReorderCommand extends AbstractMenuItem {

		final int facetTypeOrSpecial;

		ReorderCommand(final @NonNull String _label, final @NonNull String _mouseDoc, final int _facetTypeOrSpecial) {
			super(_label, _mouseDoc);
			facetTypeOrSpecial = _facetTypeOrSpecial;
		}

		@Override
		public String doCommand() {
			art.reorder(facetTypeOrSpecial);
			return getLabel();
		}

	}
}
