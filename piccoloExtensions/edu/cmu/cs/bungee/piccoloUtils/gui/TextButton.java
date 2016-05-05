/*

 Created on Jun 20, 2005

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
import java.awt.Component;
import java.awt.Font;
import java.awt.Paint;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public class TextButton extends ButtonWithChild<APText> {

	public TextButton(final @NonNull String label, final @NonNull Font font, final double x, final double y,
			final double outerW, final double outerH, final @Nullable String _disabledMessage,
			final @Nullable String _mouseDoc, final boolean is3D, final @Nullable Color textColor,
			final @Nullable Paint bgPaint) {
		// Must specify final w and h, rather than have it computed from text.
		// Otherwise, Button would try to shrink it, which wouldn't have any
		// effect, and child would occlude the whole Button.
		super(x, y, outerW >= 0.0 ? outerW : defaultOuterSize(font, is3D),
				outerH >= 0.0 ? outerH : defaultOuterSize(font, is3D), _disabledMessage, _mouseDoc, is3D, bgPaint);
		assert font.getSize() > 0 : font;
		isScaleToFit = false;
		child = new APText(font);
		child.setWrap(false);
		child.setTextPaint(textColor);
		child.setJustification(Component.CENTER_ALIGNMENT);
		final boolean isSetText = child.maybeSetText(label);
		assert isSetText;
		addChild(child);
		if (outerW < 0.0 || outerH < 0.0) {
			// setWidth/Height are NO-OPs, because child constrainWidth/Height
			// must be true at this point
			//
			// if (outerW > 0.0) {
			// child.setWidth(outerW - 2 * borderTwiceStrokeW());
			// }
			// if (outerH > 0.0) {
			// child.setHeight(outerH - 2 * borderTwiceStrokeW());
			// }
			fitToChild();
		} else {
			positionChild();
		}
		// System.out.println("TextButton " + label + " getBounds()="
		// + getBounds() + " getFullBounds()=" + getFullBounds()
		// + " child.getBounds()=" + child.getBounds());
	}

	private static double defaultOuterSize(final @NonNull Font font, final boolean is3D) {
		// System.out.println("TextButton.defaultOuterSize " + font);
		assert font.getSize() > 0 : font;
		return Button.inner2outerSize(APText.fontLineH(font), is3D);
	}

	private @NonNull Font getFont() {
		final Font result = child.getFont();
		assert result != null;
		return result;
	}

	public void setFont(final @NonNull Font f) {
		if (!Objects.deepEquals(getFont(), f)) {
			child.setFont(f);
			// if (child.isConstrainWidthToTextWidth()) {
			fitToChild();
			// } else {
			// positionChild();
			// }
		}
	}

	public boolean setText(final @Nullable String text) {
		final boolean result = !Objects.deepEquals(getText(), text) && child.maybeSetText(text);
		if (result) {
			if (child.isConstrainWidthToTextWidth()) {
				fitToChild();
			} else {
				positionChild();
			}
			// System.out.println(Util.ancestorString(child));
		}
		return result;
	}

	// public void setTextPaint(final Paint paint) {
	// child.setTextPaint(paint);
	// }

	// e.g. javax.swing.JLabel.LEFT_ALIGNMENT;
	public void setJustification(final float just) {
		child.setJustification(just);
	}

	public @Nullable String getText() {
		return child.getText();
	}

	private static final @NonNull APText DUMMYT = new APText();

	@Override
	protected @NonNull APText dummyT() {
		return DUMMYT;
	}

	// @Override
	// public void setVisible(final boolean state) {
	// setPickable(state);
	// setChildrenPickable(state);
	// super.setVisible(state);
	// }

}