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

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.approxEquals;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Paint;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;

public class Menu extends LazyPNode implements MouseDoc {

	protected static final @NonNull MenuClickHandler MENU_CLICK_HANDLER = new MenuClickHandler();

	public final @NonNull ExpandableText label;

	public @NonNull String mouseDoc = "Open this menu";

	/**
	 * Whether buttons are visible
	 */
	public boolean visible = false;

	protected final @Nullable Paint bg;

	protected final @NonNull Color fg;

	protected final @NonNull Font font;

	protected @NonNull MenuButton[] buttons = new MenuButton[0];

	protected double w = 0.0;

	public Menu(final Paint _bg, final @NonNull Color _fg, final @NonNull Font _font) {
		fg = _fg;
		bg = _bg;
		setPaint(bg);
		font = _font;
		label = getLabel();
		setLabelJustification(Component.CENTER_ALIGNMENT);
		addChild(label.pNode());
		addInputEventListener(MENU_CLICK_HANDLER);
	}

	/**
	 * Only called by Constructor
	 */
	protected @NonNull ExpandableText getLabel() {
		final ExpandableText _label = new APText(font);
		_label.setPaint(bg);
		_label.setTextPaint(fg);
		_label.setUnderline(true, YesNoMaybe.NO);
		_label.enableExpandableText(true);
		return _label;
	}

	/**
	 * Default is CENTER_ALIGNMENT
	 */
	public void setLabelJustification(final float just) {
		label.setJustification(just);
	}

	public void addButton(final @NonNull MenuItem item) {
		final MenuButton button = getMenuButton(item);
		button.updateTransparency();
		addChild(button);
		buttons = Util.nonNull(ArrayUtils.add(buttons, button));

		if (nButtons() == 1) {
			label.setHeight(button.getHeight());
			label.setConstrainWidthToTextWidth(true);
			label.maybeSetText(item.getLabel());
			label.setConstrainWidthToTextWidth(false);
		}
		final double newW = button.getWidth();
		if (newW > w) {
			setWidth(newW);
		}
		draw();
	}

	public void removeButton(final @NonNull MenuItem item) {
		final String _label = item.getLabel();
		final MenuButton button = getButton(_label);
		removeChild(button);
		buttons = Util.nonNull(ArrayUtils.removeElement(buttons, button));
		if (_label.equals(label) && nButtons() > 0) {
			if (!visible) {
				pick();
			}
			choose(0);
		} else if (visible) {
			draw();
		}
	}

	protected @NonNull MenuButton getMenuButton(final @NonNull MenuItem item) {
		return new MenuButton(item, null, fg, font);
	}

	public void setText(final @NonNull String desc) {
		label.maybeSetText(desc);
	}

	@Override
	public boolean setWidth(final double width) {
		w = Math.ceil(width);
		label.setWidth(w);
		final int oldNbuttons = nButtons();
		for (int i = 0; i < oldNbuttons; i++) {
			buttons[i].setWidth(w);
		}
		return super.setWidth(w);
	}

	protected void draw() {
		final double direction = getDrawDirection();
		final double y0 = direction < 0.0 ? 0.0 : label.getHeight();
		double y = y0;

		for (final MenuButton button : buttons) {
			if (direction < 0.0) {
				y -= button.getHeight();
			}
			button.draw(y, visible);
			if (direction > 0.0) {
				y += button.getHeight();
			}
		}
		setHeight(label.getHeight() + (visible ? Math.abs(y - y0) : 0.0));
	}

	/**
	 * @return ±1.0
	 */
	private double getDrawDirection() {
		double direction = 1.0;
		if (visible) {
			double menuH = label.getHeight();
			for (final MenuButton button : buttons) {
				menuH += button.getHeight();
			}
			if (menuH * getGlobalScale() + getGlobalBounds().y > getCamera().getHeight()) {
				direction = -1.0;
			}
		}
		return direction;
	}

	public int nButtons() {
		return buttons.length;
	}

	public void choose(final int buttonIndex) {
		buttons[buttonIndex].pick();
	}

	void choose(final @NonNull MenuItem item) {
		assert visible;
		// visible = true;
		pick(); // hide the menu buttons
		final String newValue = item.doCommand();
		if (newValue != null) {
			label.maybeSetText(newValue);
		}
	}

	public @NonNull String getChoice() {
		final String result = label.getText();
		assert result != null;
		return result;
	}

	public void doHideTransients() {
		if (visible) {
			pick();
		}
	}

	/**
	 * Pick alternately draws and hides the menu buttons.
	 */
	public void pick() {
		visible = !visible;
		if (visible) {
			moveAncestorsToFront();
		}
		setMouseDoc(true);
		draw();
	}

	/**
	 * @param state
	 *            Whether mouse is over this Menu
	 */
	protected void setMouseDoc(final boolean state) {
		if (getParent() instanceof MouseDoc) {
			final String doc = state ? (visible ? "Close this menu without doing anything" : mouseDoc) : null;
			((MouseDoc) getParent()).setMouseDoc(doc);
		}
	}

	public void setFont(final @NonNull Font font2) {
		final double scale = font2.getSize2D() / font.getSize2D();
		if (!approxEquals(scale, getScale())) {
			assert font.getFamily().equals(font2.getFamily()) && font.getStyle() == font2.getStyle() : font + " "
					+ font2;
			setScale(scale);
		}
	}

	public @NonNull MenuButton getButton(final @NonNull String buttonLabel) { // NO_UCD
		// (unused
		// code)
		for (final MenuButton button : buttons) {
			if (buttonLabel.equals(button.getText())) {
				return button;
			}
		}
		assert false;
		return MenuButton.INVALID_MENU_BUTTON;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, label.getText());
	}

}
