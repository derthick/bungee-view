package edu.cmu.cs.bungee.client.viz.header;

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
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Alignment;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyContainer;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolox.event.PStyledTextEventHandler;
import edu.umd.cs.piccolox.nodes.PStyledText;

public class TextSearch extends LazyContainer { // NO_UCD (use default)

	private final Bungee art;
	private final @NonNull APText searchLabel;
	private final @NonNull PStyledText searchBox;
	private final JTextComponent editor;
	private transient final MyPStyledTextEventHandler textHandler;

	TextSearch(final Bungee _art) {
		art = _art;
		// setPickable(false);

		searchLabel = oneLineLabel();
		searchLabel.setTextPaint(BungeeConstants.HEADER_FG_COLOR);
		searchLabel.setVisible(false);
		searchLabel.maybeSetText("Text Search ");
		addChild(searchLabel);

		editor = createEditor();
		editor.setFont(font());
		// editor.setText(" ");
		textHandler = new MyPStyledTextEventHandler(art().getCanvas(), editor);
		addInputEventListener(textHandler);

		searchBox = new PStyledText();
		searchBox.setPaint(BungeeConstants.HEADER_FG_COLOR);
		searchBox.setVisible(false);
		searchBox.setConstrainHeightToTextHeight(false);
		searchBox.setConstrainWidthToTextWidth(false);
		searchBox.setHeight(lineH() - 2.0);
		// searchBox = textHandler.createText();
		setDocument();

		// final int margin = (int) art.buttonMargin;
		// searchBox.searchBox.setInsets(new Insets(1, margin, 1, margin));
		addChild(searchBox);

		final InputMap inputMap = editor.getInputMap();
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new KeypressEnterAction());
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new KeypressEscapeAction());

	}

	private void setDocument() {
		final Document doc = editor.getUI().getEditorKit(editor).createDefaultDocument();
		searchBox.setDocument(doc);
	}

	/**
	 * Place searchLabel, searchBox, and set our bounds
	 */
	void positionLabels() {
		searchLabel.setFont(font());
		searchLabel.setOffset(0.0, 0.0);

		if (searchBox.getParent() == null) {
			addChild(searchBox);
			// If edited string is empty and you click
			// outside, it is removed.
			// searchBox gets taller when you click on it.
			// Can't figure out how to make it get taller initially,
			// so just add a fudge factor.
		}
		final double searchBoxH = searchLabel.getHeight();
		searchBox.setBounds(0.0, 0.0, 1.0, searchBoxH);
		Alignment.align(searchBox, Alignment.CENTER_LEFT, searchLabel, Alignment.CENTER_RIGHT, art.internalColumnMargin,
				0.0);
		final double searchBoxX = searchBox.getXOffset();
		final double searchBoxW = Math.max(1.0, getWidth() - searchBoxX);
		searchBox.setWidth(searchBoxW);
		setBounds(0.0, 0.0, searchBoxX + searchBoxW, searchBoxH);

		// System.out.println("TextSearch.positionLabels w=" + w + " x=" + x
		// + " searchBox.getBounds()=" + searchBox.getBounds()
		// + " searchW=" + searchW + " searchLabel.getHeight()="
		// + searchLabel.getHeight() + "\n"
		// + Util.ancestorString(searchBox));
	}

	class KeypressEnterAction extends AbstractAction {
		@Override
		public void actionPerformed(@SuppressWarnings("unused") final ActionEvent e) {
			doSearch();
		}
	}

	// Only called by typing "Enter" key
	void doSearch() {
		final String s = getTrimmedEditorText();
		// System.out.println("doSearch '" + s + "'");

		final String errorMsg = Query.isIllegalSearch(s);
		if (errorMsg != null) {
			art.mayHideTransients();
			art().setTip(errorMsg);
		} else {
			editor.setText(null);
			setDocument();
			art.mayHideTransients();
			query().addTextSearch(s);
			art().updateQuery();
			art().printUserAction(ReplayLocation.SEARCH, s, 0);
		}
	}

	@NonNull
	String getTrimmedEditorText() {
		final String result = editor.getText().trim();
		assert result != null;
		return result;
	}

	class KeypressEscapeAction extends AbstractAction {
		@Override
		public void actionPerformed(@SuppressWarnings("unused") final ActionEvent e) {
			art().mayHideTransients();
		}
	}

	void doHideTransients() {
		// System.out.println("TextSearch.doHideTransients");

		if (textHandler.isEditing) {
			textHandler.stopEditing();
		}
		if (searchBox.getParent() == null) {
			addChild(searchBox);
		}
		// This doesn't work - it is already grabbed from PInputManager's
		// point of view.
		art().grabFocus();
	}

	// copied from PStyledTextEventHandler
	private static JTextComponent createEditor() {
		final JTextComponent jTextComponent = new JTextPane() {

			/**
			 * Set some rendering hints - if we don't then the rendering can be
			 * inconsistent. Also, Swing doesn't work correctly with fractional
			 * metrics.
			 */
			@Override
			public void paint(final Graphics g) {
				final Graphics2D g2 = (Graphics2D) g;

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

				super.paint(g);
			}
		};
		return jTextComponent;
	}

	void setSearchVisibility(boolean isVisible) {
		// System.out.println("TextSearch.setSearchVisibility " + isVisible);
		isVisible = isVisible || query().nSearches() > 0;
		searchLabel.setVisible(isVisible);
		searchBox.setVisible(isVisible);
		if (searchBox.getParent() == null) {
			addChild(searchBox);
		}
	}

	// public void setSelectedForEdit(final Perspective facet) {
	// // System.out.println("QueryViz.setSelectedForEdit");
	// if (facet == null) {
	// searchLabel.setText("Text Search");
	// } else {
	// searchLabel.setText("Edit Tag Name");
	// try {
	// final Document doc = searchBox.getDocument();
	// doc.remove(0, doc.getLength());
	// doc.insertString(0, facet.getNameNow(), null);
	// } catch (final BadLocationException e) {
	// e.printStackTrace();
	// }
	// // searchBox.setVisible(true);
	// searchBox.syncWithDocument();
	// searchBox.validateFullPaint();
	// searchBox.repaint();
	// }
	// }

	private @NonNull APText oneLineLabel() {
		return art().oneLineLabel();
	}

	Bungee art() {
		return art;
	}

	private Query query() {
		return art.getQuery();
	}

	/**
	 * Always equal to an int
	 */
	private double lineH() {
		return art().lineH() + 1.0;
	}

	private Font font() {
		return art().getCurrentFont();
	}

	public void setFont(final Font font) {
		editor.setFont(font);
		positionLabels();
	}

	class MyPStyledTextEventHandler extends PStyledTextEventHandler {
		boolean isEditing = false;

		MyPStyledTextEventHandler(final PCanvas arg0, final JTextComponent arg1) {
			super(arg0, arg1);
		}

		@Override
		public void mouseEntered(@SuppressWarnings("unused") final PInputEvent e) {
			art().setClickDesc("Start entering search terms");
		}

		@Override
		public void mouseExited(@SuppressWarnings("unused") final PInputEvent e) {
			art().resetClickDesc();
		}

		@Override
		public void startEditing(final PInputEvent e, final PStyledText t) {
			isEditing = true;
			super.startEditing(e, t);
		}

		@Override
		public void stopEditing() {
			isEditing = false;
			super.stopEditing();
		}

	}

}
