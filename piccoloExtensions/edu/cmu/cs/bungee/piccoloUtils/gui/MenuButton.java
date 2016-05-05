/*

Created on Mar 4, 2005

The Bungee View applet lets you search, browse, and data-mine an image collection.
Copyright (C) 2006  Mark Derthick

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.  See gpl.html.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

You may also contact the author at
mad@cs.cmu.edu,
or at
Mark Derthick
Carnegie-Mellon University
Human-Computer Interaction Institute
Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class MenuButton extends TextButton {

	static final @NonNull MenuButton INVALID_MENU_BUTTON = new MenuButton(new AbstractMenuItem("INVALID_MENU_BUTTON"),
			null, UtilColor.WHITE, new Font("Default", 0, 1));

	final @NonNull
	private MenuItem menuItem;

	public MenuButton(final @NonNull MenuItem _item, final @Nullable Paint bg, final @NonNull Color fg,
			final @NonNull Font _font) {
		super(_item.getLabel(), _font, 0, 0, -1, -1, null, null, /* is3D */ false, fg, bg);

		menuItem = _item;
		setPaint(bg);
		setText(_item.getLabel());
		setHeight(Math.ceil(getHeight()));
	}

	@Override
	public boolean isEnabled() {
		return menuItem.isEnabled();
	}

	void draw(final double y, final boolean visible) {
		setVisible(visible);
		if (visible) {
			getParent().moveToFront();
			setOffset(getXOffset(), y);
		}
	}

	@Override
	public void doPick() {
		((Menu) getParent()).choose(menuItem);
	}

	@Override
	protected String getMouseDoc() {
		return menuItem.getMouseDoc();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, getText());
	}

	public MenuItem getMenuItem() {
		return menuItem;
	}

}