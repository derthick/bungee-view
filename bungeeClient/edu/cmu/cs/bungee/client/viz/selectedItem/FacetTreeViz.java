package edu.cmu.cs.bungee.client.viz.selectedItem;

import java.awt.Color;
import java.awt.Font;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.FacetTree;
import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.client.viz.markup.FacetNode;
import edu.cmu.cs.bungee.client.viz.markup.PerspectiveMarkupAPText;
import edu.cmu.cs.bungee.javaExtensions.GenericTree;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.MouseDoc;
import edu.cmu.cs.bungee.piccoloUtils.gui.TextButton;
import edu.cmu.cs.bungee.piccoloUtils.gui.VScrollbar;
import edu.umd.cs.piccolo.PNode;

/**
 * The Tree part of Selected Item column. A FacetTreeViz is not itself a tree,
 * in that it doesn't have children that are FacetTreeViz's. Thus there are a
 * lot of methods that have a GenericTree subtree as an argument, rather than
 * the more natural scheme where there would be a method for the subtrees.
 */
class FacetTreeViz extends LazyPNode implements MouseDoc, RedrawCallback {

	static final Color OPEN_BUTTON_BG = null;
	static final Color OPEN_BUTTON_FG = BungeeConstants.UNASSOCIATED_COLORS.get(0);

	private static final boolean MAY_HIDE_TRANSIENTS = true;
	private static final double OPEN_BUTTON_SCALE = 0.7;
	private static final double OPEN_BUTTON_MARGIN_SCALE = 0.15;

	final @NonNull Bungee art;

	final @NonNull VScrollbar scrollbar;

	// FacetTreeViz is used by SelectedItem (which puts Items in
	// the tree), so it can't be GenericTree<Perspective>
	private FacetTree tree;

	private final @NonNull APText[] separators = new APText[100];
	private final @NonNull OpenButton[] openButtons = new OpenButton[100];
	/**
	 * TreeObject's that are currently contracted
	 */
	private final @NonNull List<Object> contractedTreeObjects = new LinkedList<>();

	/**
	 * Only Selected Item use FacetTreeViz. This is false for the first, and
	 * true for the second. Use isShowCheckBox(Perspective p) instead of using
	 * treeCanShowCheckBoxes directly.
	 */
	private boolean treeCanShowCheckBoxes = true;

	private double openButtonYMargin;
	private double openButtonWplusXMargin;

	private int separatorIndex = -1;

	private int openButtonIndex = -1;

	/**
	 * Number of lines (for the current width and adjusting for any scrollbar
	 * width) it takes to display the whole tree (where contracted subtrees take
	 * 1 line). -1 means not known.
	 */
	int totalLines = -1;
	/**
	 * The number of lines that are hidden due to scrolling, including those
	 * "above" AND "below" the visible lines. nInvisibleLines = totalLines -
	 * visibleLines. Scrollbar is visible iff nInvisibleLines>0. Never negative.
	 */
	int nInvisibleLines;
	/**
	 * Can be -1 if totalLines==-1.
	 */
	int visibleLines;
	int prevScrollOffsetLines;
	/**
	 * Amount to indent children (art.lineH()).
	 */
	private double indent;

	FacetTreeViz(final @NonNull Bungee _art, final double _w) {
		art = _art;
		setWidth(_w);

		final Runnable scrollTree = new Runnable() {

			@Override
			public void run() {
				try {
					final int offset = scrollbar.getRowOffset(nInvisibleLines);
					if (offset != prevScrollOffsetLines) {
						drawTree(getWidth() - art.scrollbarWidthNmargin(), offset);
						prevScrollOffsetLines = offset;
					}
				} catch (final Throwable e) {
					art.stopReplayer();
					throw (e);
				}
			}
		};

		scrollbar = new VScrollbar(art.scrollbarWidth(), BungeeConstants.FACET_TREE_SCROLL_BG_COLOR,
				BungeeConstants.FACET_TREE_SCROLL_FG_COLOR, scrollTree);
		scrollbar.setVisible(false);
		addChild(scrollbar);
		setHeight(scrollbar.getHeight());

		setFont(art.getCurrentFont());
	}

	private boolean isShowCheckBox(final PerspectiveMarkupElement pme) {
		return treeCanShowCheckBoxes && art.getShowCheckboxes() && pme.perspective.getParent() != null;
	}

	public void setShowCheckBoxes(final boolean isShow) {
		treeCanShowCheckBoxes = isShow;
	}

	void setFont(@SuppressWarnings("unused") final Font font) {
		final double _indent = art.lineH();
		final double openButtonW = Math.ceil(_indent * OPEN_BUTTON_SCALE);
		openButtonYMargin = Math.ceil((art.lineH() - openButtonW) / 2.0);
		openButtonWplusXMargin = openButtonW + Math.ceil(_indent * OPEN_BUTTON_MARGIN_SCALE);
		totalLines = -1;
		setIndent();

		scrollbar.setWidth(art.scrollbarWidth());
		validate();
	}

	private void setIndent() {
		if (tree != null) {
			indent = Math.min(art.lineH(), Math.rint(getWidth() / 2.0 / tree.maxDepth()));
		}
	}

	void validate() {
		validate(getWidth(), getHeight());
	}

	void validate(final double _w, final double _h) {
		final double prevUsableW = usableWidth();
		setWidth(_w);
		setHeight(_h);
		scrollbar.setHeight(_h);
		scrollbar.setOffset(_w - art.scrollbarWidthNmargin(), 0.0);
		if (prevUsableW != usableWidth()) {
			totalLines = -1;
			setIndent();
		}
		if (tree != null) {
			query().queueOrRedraw(this);
		}
	}

	// y margins:
	// <top> 0 <label> 10 <image> 10 <desc> 10 <tree> 2 <bottom>
	//
	// x margins:
	// <left> leftMargin <textBox> leftMargin <right>
	// or
	// <left> leftMargin <facetTree> leftMargin <scrollbar> leftMargin <right>
	@Override
	public void redrawCallback() {
		ensureTotalLines();
		prevScrollOffsetLines = Math.min(prevScrollOffsetLines, nInvisibleLines);
		drawTree(usableWidth(), prevScrollOffsetLines);
		scrollbar.setVisible(isScrollbarVisible());
		if (isScrollbarVisible()) {
			scrollbar.reset();
			assert totalLines > 0;
			scrollbar.setBufferPercent(visibleLines, totalLines);
		}
	}

	Query query() {
		return art.getQuery();
	}

	FacetTree getTree() {
		return tree;
	}

	void setTree(final FacetTree _tree) {
		assert _tree == null || _tree.getTreeObject() instanceof Item : _tree;
		if (_tree == null || tree == null || _tree.getTreeObject() != tree.getTreeObject()) {
			contractedTreeObjects.clear();
			totalLines = -1;
			tree = _tree;
			setIndent();
		}
	}

	@Nullable
	Item getItem() {
		return (Item) (tree == null ? null : tree.getTreeObject());
	}

	void retainScrollbar() {
		PerspectiveMarkupAPText.removeAllChildrenAndReclaimCheckboxes(this);
		addChild(scrollbar);
		separatorIndex = -1;
		openButtonIndex = -1;
	}

	private @NonNull APText getSeparatorArrow() {
		APText result = separators[++separatorIndex];
		if (result == null) {
			result = art.oneLineLabel();
			result.setConstrainWidthToTextWidth(false);
			result.setWidth(art.parentIndicatorWidth());
			result.maybeSetText(BungeeConstants.PARENT_INDICATOR_PREFIX);
			result.setTextPaint(OPEN_BUTTON_FG);
			separators[separatorIndex] = result;
		}
		return result;
	}

	private @NonNull OpenButton getOpenCloseButton(final boolean isContracted,
			final @NonNull MarkupElement treeObject) {
		OpenButton result = openButtons[++openButtonIndex];
		if (result == null) {
			result = new OpenButton();
			openButtons[openButtonIndex] = result;
		}
		result.setLabel(isContracted);
		result.treeObject = treeObject;
		return result;
	}

	/**
	 * @return (int) (getHeight() / art.lineH())
	 */
	private int availableLines() {
		return (int) (getHeight() / art.lineH());
	}

	private double usableWidth() {
		final double w = getWidth() - (isScrollbarVisible() ? art.scrollbarWidthNmargin() : 0.0);
		return w;
	}

	boolean isScrollbarVisible() {
		return nInvisibleLines > 0;
	}

	/**
	 * If necessary, cache totalLines, visibleLines, and nInvisibleLines by
	 * drawing tree (with infinite offset, to reduce work).
	 *
	 * @return totalLines (always > 0)
	 */
	int ensureTotalLines() {
		if (totalLines < 0 && getWidth() > 0.0) {
			drawTree(usableWidth(), Integer.MAX_VALUE);
		}
		assert totalLines > 0 : getWidth() + " " + usableWidth() + " " + this;
		return totalLines;
	}

	/**
	 * @param treeW
	 * @param offsetLines
	 *            How many to skip (due to scroll bar). For instance, if
	 *            offsetLines=4, compute the first 4 lines, but don't draw them.
	 *            Recursive calls decrement offsetLines. When no more visible
	 *            lines will fit, recursive calls set offsetLines to
	 *            Integer.MAX_VALUE. offsetLines may be Integer.MAX_VALUE
	 *            initially, which means dont draw, just compute number of
	 *            lines.
	 * @return offset plus visible lines (totalLines). Also sets visibleLines,
	 *         nInvisibleLines
	 */
	int drawTree(final double treeW, final int offsetLines) {
		final double margin = art.isOpenClose() ? openButtonWplusXMargin : 0.0;
		retainScrollbar();
		assert tree != null;
		totalLines = drawChildren(tree, margin, treeW, offsetLines);
		assert totalLines > 0 : tree;
		allocateTotalLines();
		return totalLines;
	}

	@Override
	public boolean setBounds(final double x, final double y, final double w, final double h) {
		final boolean result = super.setBounds(x, y, w, h);
		allocateTotalLines();
		return result;
	}

	private void allocateTotalLines() {
		nInvisibleLines = Math.max(0, totalLines - availableLines());
		visibleLines = totalLines - nInvisibleLines;
	}

	private int drawChildren(final @NonNull GenericTree<MarkupElement> subtree, final double x, final double treeW,
			final int offsetLines) {
		int nLinesDrawn = 0;
		for (final Iterator<GenericTree<MarkupElement>> it = subtree.childIterator(); it.hasNext();) {
			final GenericTree<MarkupElement> child = it.next();
			assert child != null;
			nLinesDrawn += drawTreeInternal(child, x, treeW, offsetLines - nLinesDrawn);
		}
		return nLinesDrawn;
	}

	private int drawTreeInternal(final @NonNull GenericTree<MarkupElement> subtree, double x, final double treeW,
			int offsetLines) {
		offsetLines = adjustOffsetLinesIfOffScreen(offsetLines);
		int nLinesDrawn = 0;
		final PerspectiveMarkupElement treeObject = (PerspectiveMarkupElement) subtree.getTreeObject();
		final int nUncontractedChildren = isContracted(treeObject) ? 0 : subtree.nChildren();
		final double y = -offsetLines * art.lineH();
		final boolean isOverflow = x + drawWidth(subtree) > treeW;
		maybeDrawOpenButton(treeObject, x, y, nUncontractedChildren > 0 && (isOverflow || nUncontractedChildren > 1));
		maybeAddFacetLabel(treeObject, x, y, treeW, subtree);
		if (isOverflow || nUncontractedChildren == 0) {
			// This line is done. Increase indentation for any children.
			nLinesDrawn++;
			x += indent;
		} else {
			x += art.getFacetStringWidth(treeObject, false, isShowCheckBox(treeObject));
			x += addSeparator(x, y);
		}
		if (nUncontractedChildren > 0) {
			nLinesDrawn += drawChildren(subtree, x, treeW, offsetLines - nLinesDrawn);
		}
		return nLinesDrawn; // This includes offsetLines
	}

	private int adjustOffsetLinesIfOffScreen(int offsetLines) {
		if (-offsetLines > availableLines() - 1) {
			offsetLines = Integer.MAX_VALUE;
		}
		return offsetLines;
	}

	private double addSeparator(final double x, final double y) {
		if (y >= 0.0) {
			final APText separator = getSeparatorArrow();
			separator.setOffset(x, y);
			addChild(separator);
		}
		return art.parentIndicatorWidth();
	}

	private void maybeAddFacetLabel(final @NonNull PerspectiveMarkupElement treeObject, final double x, final double y,
			final double treeW, final @NonNull GenericTree<MarkupElement> subtree) {
		if (y >= 0.0) {
			final boolean showCheckBox = isShowCheckBox(treeObject);
			try {
				final double nameW = treeW - x;
				final PerspectiveMarkupAPText label = (PerspectiveMarkupAPText) PerspectiveMarkupAPText.getFacetText(
						treeObject, art, nameW, showCheckBox,
						UserAction.isDefaultLocationUnderline(art, treeObject.perspective, null), x, y,
						ReplayLocation.DEFAULT_REPLAY);
				final String text = label.getText();
				UserAction.isDefaultLocationUnderline(art, treeObject.perspective, label);
				assert text != null;
				assert label.getWidth() <= nameW : " result.getWidth()=" + label.getWidth() + " nameW=" + nameW
						+ " result.getText='" + text + "' result.getText.getStringWidth=" + art.getStringWidth(text)
						+ "\n treeObject=" + treeObject + "\n result=" + label;
				label.mayHideTransients = MAY_HIDE_TRANSIENTS;
				addChild(label);
			} catch (final AssertionError e) {
				System.err.println(" While maybeAddFacetLabel x=" + x + " + drawWidth=" + drawWidth(subtree)
						+ " <=? treeW=" + treeW + " indent=" + indent + " CheckBox w="
						+ (showCheckBox ? art.checkBoxWidth() : 0) + "\n subtree=" + subtree);
				throw e;
			}
		}
	}

	private void maybeDrawOpenButton(final @NonNull MarkupElement treeObject, final double x, final double y,
			final boolean isMultiLine) {
		if (y >= 0.0 && art.isOpenClose()) {
			final boolean isContracted = isContracted(treeObject);
			if (isMultiLine || isContracted) {
				final OpenButton open = getOpenCloseButton(isContracted, treeObject);
				addChild(open);
				open.setOffset(x - openButtonWplusXMargin, y + openButtonYMargin);
			}
		}
	}

	void toggleIsContracted(final @NonNull Object treeObject) {
		if (isContracted(treeObject)) {
			contractedTreeObjects.remove(treeObject);
		} else {
			contractedTreeObjects.add(treeObject);
		}
		totalLines = -1;
		query().queueOrRedraw(this);
	}

	private boolean isContracted(final @NonNull Object treeObject) {
		return contractedTreeObjects.contains(treeObject);
	}

	/**
	 * @return the width required to draw subtree while only breaking lines to
	 *         draw siblings.
	 */
	private double drawWidth(final @NonNull GenericTree<MarkupElement> subtree) {
		double result = 0.0;
		for (final Iterator<GenericTree<MarkupElement>> it = subtree.childIterator(); it.hasNext();) {
			final GenericTree<MarkupElement> child = it.next();
			assert child != null;
			result = Math.max(result, drawWidth(child));
		}
		if (result > 0.0) {
			result += art.parentIndicatorWidth();
		}
		final PerspectiveMarkupElement treeObject = (PerspectiveMarkupElement) subtree.getTreeObject();
		result += art.getFacetStringWidth(treeObject, false, isShowCheckBox(treeObject));
		return result;
	}

	boolean updateHighlighting(final @NonNull Set<? extends Perspective> changedFacets, final int queryVersion,
			final @NonNull YesNoMaybe isRerender) {
		boolean result = false;
		final int nChildren = getChildrenCount();
		for (int i = 0; i < nChildren; i++) {
			final PNode node = getChild(i);
			if (node instanceof FacetNode) {
				final FacetNode facetNode = (FacetNode) node;
				if (changedFacets.contains(facetNode.getFacet())
						&& facetNode.updateHighlighting(queryVersion, isRerender)) {
					result = true;
				}
			}
		}
		return result;
	}

	private final class OpenButton extends TextButton {

		/**
		 * plus sign
		 */
		private static final String EXPAND_LABEL = "✚";

		/**
		 * minus sign
		 */
		private static final String CONTRACT_LABEL = "▬";

		Object treeObject;

		OpenButton() {
			super(CONTRACT_LABEL + " ", art.getCurrentFont(), 0.0, 0.0, -1.0, -1.0, null, null, false, OPEN_BUTTON_FG,
					OPEN_BUTTON_BG);
			setScale(OPEN_BUTTON_SCALE);
		}

		/**
		 * Don't call this setState, because Button.setPaths would call it and
		 * screw up the state we want.
		 *
		 * @param isContracted
		 */
		void setLabel(final boolean isContracted) {

			// If isContracted, button should show the expand sign
			setText(isContracted ? EXPAND_LABEL : CONTRACT_LABEL);
			mouseDoc = isContracted ? "Show indented lines" : "Hide indented lines";
		}

		@Override
		public void doPick() {
			assert treeObject != null;
			toggleIsContracted(treeObject);
		}

	}

	@Override
	public void setMouseDoc(final String doc) {
		art.setClickDesc(doc);
	}

}
