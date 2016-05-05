/*

 Created on Jun 19, 2005

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

import java.awt.Paint;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.umd.cs.piccolo.PNode;

public class Button extends LazyPNode { // NO_UCD (use
										// default)

	protected static final @NonNull ButtonHandler BUTTON_HANDLER = new ButtonHandler();

	public @Nullable String mouseDoc;

	/**
	 * Whether positionChild scales, or just centers.
	 */
	public boolean isScaleToFit = true;

	protected @Nullable String disabledMessage = "<Disabled>";

	private boolean isEnabled = true; // NO_UCD (use default)

	/**
	 * Lazily evaluated. If disabledMessageText!=null && disabledMessage==null,
	 * must recompute.
	 */
	private @Nullable APText disabledMessageAPText;

	private @Nullable BevelBorder border; // NO_UCD (use final)

	/**
	 * @param x
	 *            xOffset
	 * @param y
	 *            yOffset
	 * @param outerW
	 * @param outerH
	 * @param _disabledMessage
	 *            mouse doc
	 * @param _mouseDoc
	 *            mouse doc
	 * @param is3D
	 *            Add BevelBorder
	 * @param paintS
	 *            bgColor
	 */
	public Button(final double x, final double y, final double outerW, final double outerH,
			final @Nullable String _disabledMessage, final @Nullable String _mouseDoc, final boolean is3D,
			final @Nullable Paint paintS) // NO_UCD
	// (use
	// default)
	{
		setPaint(paintS);
		mouseDoc = _mouseDoc;
		if (is3D) {
			assert getPaint() != null;
			border = new BevelBorder(true);
			addChild(border);
		}
		setDisabledMessage(_disabledMessage);
		setOffset(x, y);
		adjustSize(Math.rint(outerW), Math.rint(outerH));
		addInputEventListener(BUTTON_HANDLER);
		setChildrenPickable(false);
	}

	// Assumes border.strokeW==1
	static double inner2outerSize(final double innerSize, final boolean is3D) {
		final double borderSize = is3D ? /* border.strokeW * */2 : 0.0;
		final double outerSize = Math.rint(innerSize + 2.0 * borderSize);
		return outerSize;
	}

	public @Nullable APText getDisabledMessageAPText() {
		final String _disabledMessage = getDisabledMessage();
		if (_disabledMessage != null && disabledMessageAPText == null) {
			final APText _disabledMessageAPText = new APText();
			_disabledMessageAPText.setPaint(UtilColor.YELLOW);
			_disabledMessageAPText.maybeSetText(_disabledMessage);
			_disabledMessageAPText.setOffset(getXOffset(), getYOffset() + outerH() + 1.0);
			disabledMessageAPText = _disabledMessageAPText;
		}
		return disabledMessageAPText;
	}

	public @Nullable String getDisabledMessage() {
		return disabledMessage;
	}

	public void setDisabledMessage(final @Nullable String message) {
		if (!Objects.deepEquals(disabledMessage, message)) {
			disabledMessage = message;
			disabledMessageAPText = null;
		}
	}

	/**
	 * Gray out if !isEnabled()
	 */
	public void setVisibility(final boolean isVisible) {
		setVisible(isVisible);
		if (isVisible) {
			updateTransparency();
		}
	}

	/**
	 * Set partially transparent if !isEnabled()
	 */
	public void updateTransparency() {
		setTransparency(isEnabled() ? 1f : 0.5f);
	}

	/**
	 * Having raised mean true seems backwards...
	 *
	 * @return whether state changed (if is3D; else always returns false).
	 */
	public boolean setState(final boolean state) {
		return border != null && border.setBorderState(state);
	}

	// public void setBorderColor(final Color bgColor) {
	// border.setPaint(bgColor);
	// }

	@Override
	public double getXOffset() {
		return super.getXOffset() - borderTwiceStrokeW();
	}

	@Override
	public double getYOffset() {
		return super.getYOffset() - borderTwiceStrokeW();
	}

	@Override
	public void setOffset(final double outerX, final double outerY) {
		super.setOffset(Math.rint(outerX) + borderTwiceStrokeW(), Math.rint(outerY) + borderTwiceStrokeW());
	}

	@Override
	public double getMaxX() {
		return getX() + getXOffset() + outerW() * getScale();
	}

	@Override
	public double getMaxY() {
		return getY() + getYOffset() + outerH() * getScale();
	}

	int borderTwiceStrokeW() {
		return border != null ? border.getStrokeW() * 2 : 0;
	}

	public double outerW() {
		return super.getWidth() + 2 * borderTwiceStrokeW();
	}

	protected double innerW() {
		return super.getWidth();
	}

	public double outerH() { // NO_UCD (use default)
		return super.getHeight() + 2 * borderTwiceStrokeW();
	}

	protected double innerH() {
		return super.getHeight();
	}

	/**
	 * Compute bounds by subtracting border from outerW/outerH; call
	 * positionChild to transform child to these bounds.
	 */
	void adjustSize(final double outerW, final double outerH) {
		// Always setBounds, because it may not have been set yet.
		final double borderSize = borderTwiceStrokeW();
		super.setOffset(getXOffset() + borderSize, getYOffset() + borderSize);
		final double newWidth = Math.rint(outerW - 2.0 * borderSize);
		final double newHeight = Math.rint(outerH - 2.0 * borderSize);
		assert newWidth > 0.0 && newHeight > 0.0 : adjustSizeErrorMessage(mouseDoc, disabledMessage);
		super.setBounds(0.0, 0.0, newWidth, newHeight);
	}

	protected @NonNull String adjustSizeErrorMessage(final @Nullable String outerW, final @Nullable String outerH) {
		return "borderSize=" + borderTwiceStrokeW() + " outerW=" + outerW + " " + " outerH=" + outerH;
	}

	protected boolean isEnabled() {
		return isEnabled;
	}

	/**
	 * Usually subclasses override isEnabled instead of calling this.
	 */
	public void setIsEnabled(final boolean _isEnabled) {
		isEnabled = _isEnabled;
		updateTransparency();
	}

	void exit() {
		if (disabledMessageAPText != null) {
			disabledMessageAPText.removeFromParent();
		}
		setMouseDoc(false);
	}

	void enter() {
		setMouseDoc(true);
	}

	public void setMouseDoc(final boolean state) {
		if (getParent() instanceof MouseDoc) {
			((MouseDoc) getParent()).setMouseDoc(getMouseDoc(state));
		}
	}

	private @Nullable String getMouseDoc(final boolean state) {
		return state ? getMouseDoc() : null;
	}

	protected @NonNull String getMouseDoc() {
		final String result = isEnabled() ? mouseDoc : getDisabledMessage();
		assert UtilString.isNonEmptyString(result) : this + "\n isEnabled=" + isEnabled() + " getVisible="
				+ getVisible() + " getPickable=" + getPickable() + " getPickableMode=" + getPickableMode() + " result="
				+ result;
		assert result != null;
		return result;
	}

	public void doPick() {
		assert false : "default (no-op) doPick on " + this;
	}

	public void pick() {
		if (isEnabled()) {
			doPick();
		} else {
			showDisabledError();
		}
	}

	protected void showDisabledError() {
		final PNode parent = getParent();
		if (parent instanceof MouseDoc) {
			((MouseDoc) parent).setMouseDoc(getDisabledMessage());
		} else {
			parent.addChild(getDisabledMessageAPText());
		}
	}

	// @SuppressWarnings({ "static-method", "unused" })
	// /**
	// * Nothing calls this!
	// */
	// public void mayHideTransients(final PNode node) {
	// // Override this
	// assert false;
	// }
}

class ButtonHandler extends MyInputEventHandler<Button> {

	public ButtonHandler() {
		super(Button.class);
	}

	// Pretty much anything you do on a button shouldn't go any further, so
	// always return true.

	@Override
	public boolean click(final @NonNull Button node) {
		node.pick();
		return true;
	}

	@Override
	public boolean exit(final @NonNull Button node) {
		node.exit();
		return true;
	}

	@Override
	public boolean enter(final @NonNull Button node) {
		node.enter();
		return true;
	}

	// @Override
	// public void mayHideTransients(final Button<?> node) {
	// node.mayHideTransients(node);
	// }

}
