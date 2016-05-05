/**
 *
 */
package edu.cmu.cs.bungee.client.viz.header;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;

final class ColorKey extends LazyPNode {

	final @NonNull Bungee art;
	final @NonNull ColorKeyHover colorKeyHover = new ColorKeyHover();
	// final int nColors;
	private final @NonNull APText label;
	private final @NonNull ColorKeyKey[] buttons;
	static final @NonNull List<String> MSGS = UtilArray.getUnmodifiableList("Tag required by filters.",
			"Tag associated with filters; Items with this tag are more likely to satisfy the filters.",
			"Unassociated tag; There is no statistically significant association between this tag and the filters.",
			"Tag negatively associated with filters; Items with this tag are less likely to satisfy the filters.",
			"Tag prohibited by filters.");

	ColorKey(final @NonNull Bungee _art) {
		art = _art;
		label = art.oneLineLabel();
		label.setTextPaint(BungeeConstants.HEADER_FG_COLOR);
		label.maybeSetText("Color Key");
		addChild(label);
		final int nColors = art.nColors();
		buttons = new ColorKeyKey[nColors];
		for (int i = 0; i < nColors; i++) {
			final String _msg = MSGS.get(i);
			assert _msg != null;
			final List<Color> colors = BungeeConstants.COLORS_BY_SIGNIFICANCE.get(nColors - i - 1);
			final Color _color = colors.get(0);
			final Color _highlight = colors.get(1);
			assert _color != null && _highlight != null;
			buttons[i] = new ColorKeyKey(_color, _highlight, _msg, 1.0, 1.0);
			addChild(buttons[i]);
		}
		setFont(art.getCurrentFont());
	}

	public int nButtons() {
		return getChildrenCount() - 1;
	}

	void setFont(final @NonNull Font font) {
		label.setFont(font);
		final int nColors = art.nColors();
		final double buttonW = Math.rint(label.getWidth() / nColors);
		final double x = label.getWidth() + art.internalColumnMargin;
		final double y = label.getHeight();
		for (int i = 0; i < nColors; i++) {
			buttons[i].setBounds(0.0, 0.0, buttonW, y);
			buttons[i].setOffset(x + i * buttonW, 0.0);
		}
		setBounds(0.0, 0.0, buttons[nColors - 1].getMaxX(), y);
	}

	private class ColorKeyKey extends LazyPNode {

		@NonNull
		Color color;
		@NonNull
		Color highlight;
		@NonNull
		String msg;

		ColorKeyKey(final @NonNull Color _color, final @NonNull Color _highlight, final @NonNull String _msg,
				final double w, final double h) {
			color = _color;
			highlight = _highlight;
			msg = _msg;
			setBounds(0, 0, w, h);
			setPaint(color);
			addInputEventListener(colorKeyHover);
		}

		void enter() {
			setPaint(highlight);
			art.setNonClickMouseDoc(msg, color);
		}

		void exit() {
			setPaint(color);
			art.resetClickDesc();
			removeAllChildren();
		}
	}

	private static final class ColorKeyHover extends MyInputEventHandler<ColorKeyKey> {

		ColorKeyHover() {
			super(ColorKeyKey.class);
		}

		@Override
		public boolean enter(final ColorKeyKey node) {
			// System.out.println("SummaryTextHover.enter " + node);
			node.enter();
			return true;
		}

		@Override
		public boolean exit(final ColorKeyKey node, @SuppressWarnings("unused") final PInputEvent e) {
			node.exit();
			return true;
		}
	}

}