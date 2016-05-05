/*

Created on Apr 4, 2005

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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;

public class HScrollbar extends VScrollbar { // NO_UCD (unused code)

	public HScrollbar(final double sw, final double sh, final @Nullable Color _BG, final @NonNull Color _FG,
			final @NonNull Runnable _action) {
		super(-sw, sw, sh, _BG, _FG, _action);
		setRotation(-Math.PI / 2.0);
		downButton.mouseDoc = "Scroll right one line";
		upButton.mouseDoc = "Scroll left one line";
	}

	@Override
	public void drag(final @NonNull PInputEvent e) {
		dragPosition += e.getDelta().getWidth();
		setThumbPosition(dragPosition);
	}

	@Override
	void setMouseDoc(final @NonNull PNode node, final @NonNull PInputEvent e, final boolean state) {
		assert node == this;
		String desc = null;
		if (state) {
			final PNode pickedNode = e.getPickedNode();
			if (pickedNode == node) {
				desc = getPageDirection(e) == ScrollDirection.DOWN ? "Page left" : "Page right";
			} else {
				desc = "Start dragging scrollbar";
			}
		}
		setMouseDoc(desc);
	}

}
