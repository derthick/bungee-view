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

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolox.event.PStyledTextEventHandler;
import edu.umd.cs.piccolox.nodes.PStyledText;


public class TextBox extends PNode implements MouseDoc {

	private static final int scrollW = 12;

	boolean isEditable = false;

	private static final long serialVersionUID = -4542251288320601498L;

//	double w, h;

//	String s;

	int nLines;

	int nVisibleLines;

	VScrollbar sb;

	Color BG;

	Color FG;

	Color color;

	private APText text;

	private int prevLineOffset = -1;

	private PStyledText editBuffer;

	JTextComponent editor;

	private PStyledTextEventHandler textHandler;

	protected Runnable editAction;

	public TextBox(double w, double hMax, String _s, Color Scroll_BG,
			Color Scroll_FG, Color _color, double lineH, Font font) {
		// hMax = lineH * 4; // JUST FOR TESTING!!!!
		FG = Scroll_FG;
		BG = Scroll_BG;
		color = _color;
//		w = _w;
		text = new APText(font);
		text.setTextPaint(color);
		text.setConstrainWidthToTextWidth(false);
		text.setWidth(w - 2);
		text.setPickable(false);
		text.setText(_s);

		// s = gui.Util.wrapText(_s, (float) (w - 2), font);
		nLines = text.getNlines();
		double h = lineH * nLines;
		if (h > hMax) {
			// s = gui.Util.wrapText(_s, (float) (w - 3 - scrollW), font);
			// nLines = Util.nLines(s);
			text.setWidth(w - 3 - scrollW);
			nLines = text.getNlines();
			nVisibleLines = (int) (hMax / lineH);
			h = nVisibleLines * lineH;
			Runnable scroll = new Runnable() {

				public void run() {
					// System.out.println("TextBox.scroll");
					draw();
				}
			};
			sb = new VScrollbar(// (int) (w - scrollW / 2 - 1), 0,
					scrollW, (int) h, BG, FG, scroll);
			sb.setOffset(w - scrollW, 0);
			addChild(sb);
			sb.setBufferPercent(nVisibleLines, nLines);
			text.setConstrainHeightToTextHeight(false);
			text.setHeight(h);
			// System.out.println("Adding scrollbar " + nVisibleLines + " " +
			// nLines);
			// System.out.println(sb.getBounds());
			// System.out.println(sb.getOffset());
		} else {
			nVisibleLines = nLines;
		}
		setPickable(false);
		setBounds(0.0, 0.0, w, h);
		// text.setOffset(5, 5);
		addChild(text);
		// System.out.println(s);
		// System.out.println("TextBox " + nLines + " " + sH + " " + hMax);
	}

	public String getText() {
		return text.getText();
	}

	public boolean isScrollBar() {
		return nVisibleLines < nLines;
	}

	void draw() {
		assert sb != null;
		int lineOffset = (int) ((nLines - nVisibleLines) * sb.getPos() + 0.5);
		// System.out.println(lineOffset);
		if (lineOffset != prevLineOffset) {
//			if (s == null)
				String s = text.getBrokenText();
			String visString = Util.subLines(s, lineOffset, nVisibleLines);
			text.setText(visString);
			prevLineOffset = lineOffset;
		}
		// System.out.println("Text " + text.getHeight());
	}

	public void setEditable(boolean state, PCanvas canvas, Runnable _action) {
		if (state != isEditable) {
			editAction = _action;
			isEditable = state;
			text.setPickable(state);
			// setPickable(state);
			if (state) {
				editor = createEditor();
				editor.setFont(text.getFont());
				// editor.setText(" ");
				textHandler = new PStyledTextEventHandler(canvas, editor);
				Document doc = editor.getUI().getEditorKit(editor)
						.createDefaultDocument();
				try {
					doc.insertString(0, text.getText(), null);
				} catch (BadLocationException e) {
					e.printStackTrace();
				}

				editBuffer = new PStyledText();
				// editBuffer.setPaint(text.getPaint());
				editBuffer.setVisible(false);
				// editBuffer.setConstrainHeightToTextHeight(false);
				editBuffer.setConstrainWidthToTextWidth(false);
				// searchBox = textHandler.createText();
				editBuffer.setDocument(doc);
				editBuffer.setBounds(text.getBounds());
				// editor.setBounds(text.getBounds().getBounds());
				// editor.setDocument(doc);
				editBuffer.setOffset(text.getOffset());
				addChild(editBuffer);
				editBuffer.addInputEventListener(textHandler);

				InputMap inputMap = editor.getInputMap();
				KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
				inputMap.put(key, new EnterAction(this));

			}
		}
	}

	static class EnterAction extends AbstractAction {

		private static final long serialVersionUID = -3636184693260706104L;

		TextBox qv;

		public EnterAction(TextBox _q) {
			qv = _q;
		}

		public void actionPerformed(ActionEvent e) {
			qv.doSearch();
		}
	}

	public void revert() {
		if (textHandler != null)
			textHandler.stopEditing();
	}

	public void doSearch() {
		String s = editor.getText().trim();
		text.setText(s);
		textHandler.stopEditing();
		editAction.run();
		editBuffer.setVisible(false);
	}

	// copied from PStyledTextEventHandler
	private JTextComponent createEditor() {
		JTextPane tComp = new JTextPane() {

			private static final long serialVersionUID = -1081364272578141120L;

			/**
			 * Set some rendering hints - if we don't then the rendering can be
			 * inconsistent. Also, Swing doesn't work correctly with fractional
			 * metrics.
			 */
			public void paint(Graphics g) {
				Graphics2D g2 = (Graphics2D) g;

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_RENDERING,
						RenderingHints.VALUE_RENDER_QUALITY);
				g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
						RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

				super.paint(g);
			}

			/**
			 * If the standard scroll rect to visible is on, then you can get
			 * weird behaviors if the canvas is put in a scrollpane.
			 */
			// public void scrollRectToVisible() {
			// }
		};
		// tComp.setBackground(FG);
		// tComp.setBorder(new CompoundBorder(new LineBorder(Color.black),
		// new EmptyBorder(3, 3, 3, 3)));
		return tComp;
	}

	// private PStyledText searchBox;
	//
	// private JTextComponent editor;
	//
	// private PStyledTextEventHandler textHandler;
	//
	// public void startEditing(PCanvas canvas) {
	// editor = createEditor();
	// editor.setSize((int) text.getWidth(), (int) text.getHeight());
	// editor.setFont(text.getFont());
	// editor.setText(text.getText());
	// editor.setBackground(Color.red);
	// // editor.setBounds()
	// textHandler = new PStyledTextEventHandler(canvas, editor);
	// addInputEventListener(textHandler);
	//
	// searchBox = new PStyledText();
	// // searchBox.setPaint(Art.summaryFG);
	// searchBox.setVisible(false);
	// searchBox.setConstrainHeightToTextHeight(false);
	// searchBox.setConstrainWidthToTextWidth(false);
	// // searchBox = textHandler.createText();
	// Document doc = editor.getUI().getEditorKit(editor)
	// .createDefaultDocument();
	// searchBox.setDocument(doc);
	// searchBox.setHeight((int) text.getHeight());
	// addChild(searchBox);
	// }
	//
	// // copied from PStyledTextEventHandler
	// private JTextComponent createEditor() {
	// JTextPane tComp = new JTextPane() {
	//
	// private static final long serialVersionUID = -1081364272578141120L;
	//
	// /**
	// * Set some rendering hints - if we don't then the rendering can be
	// * inconsistent. Also, Swing doesn't work correctly with fractional
	// * metrics.
	// */
	// public void paint(Graphics g) {
	// Graphics2D g2 = (Graphics2D) g;
	//
	// g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
	// RenderingHints.VALUE_ANTIALIAS_ON);
	// g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
	// RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	// g2.setRenderingHint(RenderingHints.KEY_RENDERING,
	// RenderingHints.VALUE_RENDER_QUALITY);
	// g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
	// RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
	//
	// super.paint(g);
	// }
	//
	// /**
	// * If the standard scroll rect to visible is on, then you can get
	// * weird behaviors if the canvas is put in a scrollpane.
	// */
	// // public void scrollRectToVisible() {
	// // }
	// };
	// // tComp.setBackground(FG);
	// // tComp.setBorder(new CompoundBorder(new LineBorder(Color.black),
	// // new EmptyBorder(3, 3, 3, 3)));
	// return tComp;
	// }

	public void setMouseDoc(String doc, boolean state) {
		if (getParent() instanceof MouseDoc) {
			((MouseDoc) getParent()).setMouseDoc(doc, state);
		}
	}

//	public void setMouseDoc(Vector doc, boolean state) {
//		if (getParent() instanceof MouseDoc) {
//			((MouseDoc) getParent()).setMouseDoc(doc, state);
//		}
//	}

	public void setMouseDoc(PNode source, boolean state) {
		setMouseDoc(((Button) source).mouseDoc, state);
	}
}
