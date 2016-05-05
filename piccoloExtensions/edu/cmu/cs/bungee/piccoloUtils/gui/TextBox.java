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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolox.event.PStyledTextEventHandler;
import edu.umd.cs.piccolox.nodes.PStyledText;

public class TextBox extends LazyPNode implements MouseDoc {

	/**
	 * margin to the left and right of text, and to the right of the scroll bar.
	 */
	private final double margin;
	/**
	 * The number of lines required to fully display this APText.
	 */
	int totalLines;
	final @NonNull VScrollbar scrollbar;
	final @NonNull ScrollingAPText scrollingAPText;

	private boolean isEditable = false;
	/**
	 * @NonNull iff isEditable
	 */
	private PStyledText editBuffer;
	/**
	 * @NonNull iff isEditable
	 */
	private JTextComponent editor;
	/**
	 * @NonNull iff isEditable
	 */
	private PStyledTextEventHandler textHandler;
	/**
	 * @NonNull iff isEditable
	 */
	private Runnable editAction;

	public TextBox(final double w, final double h, final @Nullable String s, final @NonNull Color scroll_BG,
			final @NonNull Color scroll_FG, final @NonNull Color textColor, final @Nullable Color bgColor,
			final @NonNull Font font, final double _scrollW, final double _margin) {
		margin = _margin;
		setPickable(false);
		scrollbar = initScrollbar(scroll_FG, scroll_BG, _scrollW);

		scrollingAPText = new ScrollingAPText(font);
		scrollingAPText.setTextPaint(textColor);
		scrollingAPText.setPaint(bgColor);
		scrollingAPText.setPickable(false);
		scrollingAPText.setWrap(true);

		setWidthHeight(w, h);
		// setScrollbarW(_scrollW);
		setText(s, YesNoMaybe.MAYBE);
		addChild(scrollingAPText);
	}

	private @NonNull VScrollbar initScrollbar(final @NonNull Color scrollFG, final @NonNull Color scrollBG,
			final double _scrollW) {

		final @NonNull Runnable scroll = new Runnable() {
			@Override
			public void run() {
				final int lineOffset = scrollbar.getRowOffset(totalLines - maxPossibleVisibleLines());
				scrollingAPText.setOffsetLines(lineOffset);
			}
		};

		final VScrollbar _sb = new VScrollbar(_scrollW, scrollBG, scrollFG, scroll);
		_sb.setVisible(false);
		addChild(_sb);
		return _sb;
	}

	private void setScrollbarXOffset() {
		scrollbar.setXoffset(getWidth() - scrollbar.getWidth() - margin);
	}

	public void validate(final double w, final double h) {
		assert h >= minHeight() : h + " " + minHeight();
		if (setWidthHeight(w, h)) {
			redisplay();
		}
	}

	@Override
	public boolean setBounds(final double x, final double y, final double w, final double h) {
		final boolean result = super.setBounds(x, y, w, h);
		if (result) {
			// System.out.println("TextBox.setBounds totalLines=" + totalLines +
			// " width: " + w + " => " + getWidth()
			// + " scrollingAPText.getNlines()=" + scrollingAPText.getNlines() +
			// " scrollingAPText.getWidth()="
			// + scrollingAPText.getWidth() + " => " + getTextWidth() + "
			// isScrollbar()=" + isScrollbar());
			scrollingAPText.setWidthHeight(getTextWidth(), h);
		}
		return result;
	}

	private double getTextWidth() {
		return getWidth() - 2.0 * margin - (isScrollbar() ? scrollbar.getWidth() + margin : 0.0);
	}

	/**
	 * Only called by SelectedItem.setFont().
	 */
	public void setFontNScrollbarW(final @NonNull Font font, final double _scrollbarW) {
		boolean mustRedisplay = font != scrollingAPText.getFont();
		if (scrollbar.setWidth(_scrollbarW)) {
			setScrollbarXOffset();
			mustRedisplay = true;
		}
		if (mustRedisplay) {
			scrollingAPText.setFont(font);
			redisplay();
		}
	}

	private double lineH() {
		return scrollingAPText.getLineH();
	}

	// Make height large enough for scrollbar. Even if scrollbar is hidden, this
	// seems like a good heuristic minimum.
	@Override
	public double minHeight() {
		final double lineH = lineH();
		final double minLines = Math.ceil(scrollbar.minH() / lineH);
		return minLines * lineH;
	}

	@Override
	public double maxHeight() {
		// System.out.println("TextBox.maxHeight totalLines=" + totalLines + "
		// scrollingAPText.getNlines()="
		// + scrollingAPText.getNlines() + " scrollingAPText.getWidth()=" +
		// scrollingAPText.getWidth()
		// + " isScrollbar()=" + isScrollbar());
		assert totalLines == scrollingAPText.getNlines();
		return totalLines * lineH();
	}

	/**
	 * @return the displayed String
	 */
	public @Nullable String getText() {
		return scrollingAPText.getText();
	}

	public void setText(final @Nullable String description, final @Nullable Pattern textSearchPattern) {
		final boolean isTextChanged = setText(description, YesNoMaybe.NO);
		scrollingAPText.updateSearchHighlighting(textSearchPattern, isTextChanged ? YesNoMaybe.NO : YesNoMaybe.MAYBE);
		if (isTextChanged) {
			redisplay();
		}
	}

	/**
	 * @param s
	 *            display this String
	 */
	boolean setText(final @Nullable String s, final @NonNull YesNoMaybe isRerender) {
		final boolean result = scrollingAPText.getAvailableWidth() > 0f && scrollingAPText.maybeSetHTML(s, isRerender);
		if (result) {
			scrollbar.reset();
			scrollingAPText.setOffsetLines(0);
			if (isRerender != YesNoMaybe.NO) {
				redisplay();
			}
		}
		return result;
	}

	/**
	 * scrollingAPText.setWidth(), scrollbar.setBufferPercent(), and
	 * setHeight().
	 *
	 * Sometimes called from validate(), in which case we mustn't increase
	 * height.
	 */
	private void redisplay() {
		totalLines = scrollingAPText.getNlines();
		if (totalLines > 0) {
			scrollingAPText.setWidth(getTextWidth());

			final double lineH = lineH();
			final double newHeight = Math.max(Math.min(totalLines * lineH, getHeight()), minHeight());
			final int maxPossibleVisibleLines = (int) (newHeight / lineH);
			scrollbar.setBufferPercent(Math.min(totalLines, maxPossibleVisibleLines), totalLines, lineH);
			if (isScrollbar()) {
				setScrollbarXOffset();
				assert scrollbar.getHeight() <= newHeight : redisplayErrMsg();
				// setHeight(scrollbar.getHeight());
			} else {
				assert totalLines * lineH <= newHeight : redisplayErrMsg();
				// setHeight(Math.max(minHeight, totalLines * lineH));
			}
			setHeight(newHeight);
			totalLines = scrollingAPText.getNlines();
		}
	}

	private String redisplayErrMsg() {
		return "getHeight=" + getHeight() + " minHeight=" + minHeight() + " lineH=" + lineH() + " totalLines="
				+ totalLines + " maxPossibleVisibleLines=" + maxPossibleVisibleLines() + " isScrollbar="
				+ isScrollbar();
	}

	/**
	 * @return sb.getVisible()
	 */
	public boolean isScrollbar() {
		return scrollbar.getVisible();
	}

	/**
	 * @return (int) (getHeight() / lineH())
	 */
	int maxPossibleVisibleLines() {
		return (int) (getHeight() / lineH());
	}

	public boolean isEditing() {
		return isEditable;
	}

	public void setEditable(final boolean state, final PCanvas canvas, final @NonNull Runnable _action) {
		if (state != isEditable) {
			editAction = _action;
			isEditable = state;
			scrollingAPText.setPickable(state);
			if (state) {
				editor = createEditor();
				editor.setFont(scrollingAPText.getFont());
				textHandler = new PStyledTextEventHandler(canvas, editor);
				final Document doc = editor.getUI().getEditorKit(editor).createDefaultDocument();
				try {
					doc.insertString(0, getText(), null);
				} catch (final BadLocationException e) {
					e.printStackTrace();
				}

				editBuffer = new PStyledText();
				editBuffer.setVisible(false);
				editBuffer.setConstrainWidthToTextWidth(false);
				editBuffer.setDocument(doc);
				editBuffer.setBounds(scrollingAPText.getBounds());
				editBuffer.setOffset(scrollingAPText.getOffset());
				addChild(editBuffer);
				editBuffer.addInputEventListener(textHandler);

				final InputMap inputMap = editor.getInputMap();
				final KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
				inputMap.put(key, new EnterAction(this));
			}
		}
	}

	private static class EnterAction extends AbstractAction {

		private final @NonNull TextBox textBox;

		EnterAction(final @NonNull TextBox _textBox) {
			textBox = _textBox;
		}

		@Override
		public void actionPerformed(@SuppressWarnings("unused") final ActionEvent e) {
			textBox.doEdit();
		}
	}

	public void revert() {
		if (textHandler != null) {
			textHandler.stopEditing();
		}
	}

	void doEdit() {
		final String s = editor.getText().trim();
		scrollingAPText.maybeSetText(s);
		textHandler.stopEditing();
		editAction.run();
		assert editBuffer != null;
		editBuffer.setVisible(false);
	}

	// copied from PStyledTextEventHandler
	private static @NonNull JTextComponent createEditor() {
		return new JTextPane() {

			/**
			 * Set some rendering hints - if we don't then the rendering can be
			 * inconsistent. Also, Swing doesn't work correctly with fractional
			 * metrics.
			 */
			@Override
			public void paint(final Graphics g) {
				assert g instanceof Graphics2D;
				final Graphics2D g2 = (Graphics2D) g;

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

				super.paint(g);
			}
		};
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "'" + getText() + "'");
	}

}
