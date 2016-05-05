package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.Paint;

import org.eclipse.jdt.annotation.NonNull;

class IconButton extends ButtonWithChild<LazyPPath> {

	IconButton(final double x, final double y, final double outerW, final double outerH, final String _disabledMessage,
			final String documentation, final boolean is3D, final Color fg, final Paint bg) {
		super(x, y, outerW >= 0 ? outerW : 10, outerH >= 0 ? outerH : 10, _disabledMessage, documentation, is3D, bg);
		child = new LazyPPath();
		child.setPaint(fg);
		addChild(child);
	}

	private static final @NonNull LazyPPath DUMMYT = new LazyPPath();

	@Override
	protected LazyPPath dummyT() {
		return DUMMYT;
	}

}