/*
		defaultTextPaint=aPaint;

 Created on Mar 27, 2006

 Bungee View lets you search, browse, and data-mine an image collection.
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

package edu.cmu.cs.bungee.client.viz.markup;

import java.awt.Component;
import java.awt.Font;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupPaintElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupTag;
import edu.cmu.cs.bungee.client.query.markup.MexPerspectives;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Alignment;
import edu.cmu.cs.bungee.piccoloUtils.gui.ExpandableText;
import edu.cmu.cs.bungee.piccoloUtils.gui.ExpandableTextHover;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil;
import edu.umd.cs.piccolo.PNode;

/**
 * Used by HeaderQueryDescription, rankLabels, MouseDocLine, & PopupSummary
 * (name, namePrefix, totalDesc, onDesc). Takes a Markup and renders it.
 */
public class MarkupViz extends LazyPNode implements RedrawCallback, ExpandableText {

	protected final @NonNull Bungee art;

	public @NonNull ReplayLocation replayLocation = ReplayLocation.DEFAULT_REPLAY;
	public boolean constrainWidthToContent = false;
	public boolean constrainHeightToContent = false;

	/**
	 * Ignored unless isWrapText
	 */
	protected boolean isWrapOnWordBoundaries = true; // NO_UCD (unused code)

	private @Nullable RedrawCallback redrawer;

	/**
	 * True if layout was unable to fit everything, given w and h. Set only by
	 * nextLine(); reset only by layout().
	 */
	private boolean incomplete;
	private @NonNull Markup content = DefaultMarkup.emptyMarkup();
	/**
	 * Default text paint for Strings. Ignored for ItemPredicates. Overridden by
	 * MarkupPaintElement tags in content.
	 */
	private @Nullable Paint defaultTextPaint;

	/**
	 * If set to false, will ignore newlines in content and truncate at w.
	 */
	private boolean isWrapText = false;

	/**
	 * Total horizontal margin (half this on each side)
	 */
	private double trimW = 0.0;

	/**
	 * Total vertical margin (half this on each edge)
	 */
	private double trimH = 0.0;

	private float justification = Component.LEFT_ALIGNMENT;

	/**
	 * constrainWidth/HeightToContent & isWrapText default to false.
	 */
	public MarkupViz(final @NonNull Bungee _art, final @Nullable Paint _defaultTextPaint) {
		defaultTextPaint = _defaultTextPaint;
		art = _art;
	}

	/**
	 * markup.substituteForGenericObjectLabel() and expandMexPerspectives().
	 * Destructive.
	 *
	 * @return whether this.content changed
	 */
	public boolean setContent(final @NonNull Markup markup) {
		// System.out.println("TNF.setContent input: " + uncompiledMarkup);
		// Markup compiledMarkup = null;
		// if (uncompiledMarkup != null) {
		// compiledMarkup = uncompiledMarkup.compile(query()
		// .getGenericObjectLabel(false));

		markup.substituteForGenericObjectLabel(query().getGenericObjectLabel(false));
		expandMexPerspectives(markup);

		// System.out.println("TNF.setContent same as old=" +
		// newV.equals(content) +
		// "\n" + newV);
		// compiledMarkup.expandMexPerspectives();
		// }
		final boolean result = !content.equals(markup);
		if (result) {
			content = markup;
		}
		return result;
	}

	/**
	 * Only called by setContent().
	 *
	 * Replace MarkupElements with their description() in compiledMarkup.
	 */
	private static void expandMexPerspectives(final @NonNull Markup compiledMarkup) {
		for (final ListIterator<MarkupElement> it = compiledMarkup.listIterator(); it.hasNext();) {
			final MarkupElement element = it.next();
			assert element != null : compiledMarkup;
			// Could do this for all elements, but it's inefficient.
			if (element instanceof MexPerspectives) {
				it.remove();
				final Markup mexElements = element.description();
				for (final MarkupElement mexElement : mexElements) {
					it.add(mexElement);
				}
			}
		}
	}

	@Override
	public void enableExpandableText(final boolean enable) {
		if (enable) {
			addInputEventListener(ExpandableTextHover.EXPANDABLE_TEXT_HOVER);
		} else {
			removeInputEventListener(ExpandableTextHover.EXPANDABLE_TEXT_HOVER);
		}
	}

	@Override
	public boolean expand() {
		// System.out.println("APText.expandSummary getHeight="
		// + getHeight() + " <= lineH=" + art.lineH()
		// + " isIncomplete=" + isIncomplete());

		final boolean canExpand = isIncomplete();
		if (canExpand) {
			// art.printUserAction(ReplayLocation.BUTTON, Replayer.ELLIPSIS, 0);
			setIsWrapText(true);
			setConstrainHeightToTextHeight(true);
			// setHeight(Double.POSITIVE_INFINITY);
			layout();
			moveAncestorsToFront();
		}
		return canExpand;
	}

	@Override
	public boolean contract() {
		// System.out.println("HeaderQueryDescription.contractSummary
		// getHeight="
		// + getHeight());

		setIsWrapText(false);
		final boolean canContract = getHeight() > lineH();
		if (canContract) {
			// setHeight(lineH);
			layout();
			setConstrainHeightToTextHeight(false);
			setHeight(lineH());
		}
		return canContract;
	}

	private double lineH() {
		return art.lineH();
	}

	public @NonNull Markup getContent() {
		return content;
	}

	/**
	 * True if layout was unable to fit everything, given w and h.
	 */
	protected boolean isIncomplete() {
		return incomplete;
	}

	/**
	 * If set to false, will ignore newlines in content and truncate at w.
	 */
	public void setIsWrapText(final boolean isWrap) {
		isWrapText = isWrap;
	}

	// public void setIsWrapOnWordBoundaries(final boolean
	// _isWrapOnWordBoundaries) {
	// isWrapOnWordBoundaries = _isWrapOnWordBoundaries;
	// }

	/**
	 * @return width - margins
	 */
	private double trimmedW() {
		// assert trimW >= 0.0;
		return getWidth() - trimW;
	}

	@Override
	public void setJustification(final float _justification) {
		assert 0f <= _justification && justification <= 1f;
		justification = _justification;
	}

	public void setRedrawer(final @Nullable RedrawCallback _redrawer) {
		redrawer = _redrawer;
	}

	private @NonNull RedrawCallback getRedrawer() {
		return redrawer != null ? redrawer : this;
	}

	@Override
	public void redrawCallback() {
		if (getParent() != null) {
			// Ignore stale redraw requests
			layout();
		}
	}

	@Override
	protected void layoutChildren() {
		if (trimmedW() > 0.0) {
			layout();
		}
	}

	public void layout() {
		assert trimmedW() > 0.0 : this;
		assert content.size() != 1
				|| !content.get(0).getName(getRedrawer()).equals(art.getQuery().getGenericObjectLabel(true)) : content;
		assert getX() == 0.0 && getY() == 0.0 : "layout doesn't work right unless x,y==0";
		PerspectiveMarkupAPText.removeAllChildrenAndReclaimCheckboxes(this);
		incomplete = false;
		if (content.size() > 0) {
			if (constrainWidthToContent) {
				setWidth(Integer.MAX_VALUE);
			}
			if (constrainHeightToContent) {
				setHeight(Integer.MAX_VALUE);
			}
			if (!isWrapText) {
				setHeight(lineH() + trimH);
			}
			try {
				layoutInternal();
			} catch (final AssertionError e) {
				System.err.println("While MarkupViz.layout " + content);
				throw (e);
			}

			constrainWnHtoContent();

			// if (isWrapText) {
			// System.out.println("MarkupViz.layout " + content + "\n w=" +
			// getWidth() + " h=" + getHeight()
			// + " art.lineH()=" + lineH() + " trimW=" + trimW + " trimH=" +
			// trimH + " incomplete="
			// + incomplete + " isWrapText=" + isWrapText + "
			// isWrapOnWordBoundaries=" + isWrapOnWordBoundaries
			// + "\n" + this);
			// }
		}
	}

	/**
	 * Layout all the content that will fit in trimmed width and height.
	 */
	private void layoutInternal() {
		boolean underline = false;
		Paint paint = defaultTextPaint;
		int fontStyle = Font.BOLD;
		double x = getXmargin();
		double y = getYmargin();
		for (final MarkupElement element : content) {
			// if
			// (content.indexOf(Util.nonNull(query().findPerspectiveNow(59)).getMarkupElement())
			// >= 0) {
			// UtilString.indent("MarkupViz.layoutInternal element=" + element +
			// " paint=" + paint + "x=" + x + " y="
			// + y + " incomplete=" + incomplete);
			// }
			assert element != null;

			if (element == DefaultMarkup.PLURAL_TAG) {
				assert false : this;
			} else if (element == DefaultMarkup.NO_UNDERLINE_TAG) {
				assert underline;
				underline = false;
			} else if (element == DefaultMarkup.UNDERLINE_TAG) {
				assert !underline;
				underline = true;
			} else if (element == DefaultMarkup.NEWLINE_TAG) {
				if (isWrapText) {
					y = nextLine(x, y);
					x = getXmargin();
				} else {
					// This doesn't do anything because of all the else's,
					// so if THERE'S A MISSING SPACE bug, look here.
					// element = DefaultMarkup.FILTER_TYPE_SPACE;
				}
			} else if (element == DefaultMarkup.DEFAULT_STYLE_TAG) {
				fontStyle = Font.BOLD;
			} else if (element == DefaultMarkup.ITALIC_STRING_TAG) {
				fontStyle = Font.ITALIC;
			} else if (element == DefaultMarkup.DEFAULT_COLOR_TAG) {
				paint = defaultTextPaint;
			} else if (element instanceof MarkupPaintElement) {
				paint = ((MarkupPaintElement) element).getPaint();
			} else {
				// Make sure all MarkTags have been accounted for above.
				assert !(element instanceof MarkupTag) : element;
				final APText mostRecentChild = wrapElement(element, x, y, underline, paint, fontStyle);
				if (mostRecentChild != null) {
					// Next APText goes to the right of this one.
					x = mostRecentChild.getIntMaxX();
					y = mostRecentChild.getYOffset();
				}
			}
			if (isIncomplete()) {
				break;
			}
		}
	}

	/**
	 * @return NULL if incomplete or if we'll be redrawCallback()ed.
	 */
	private @Nullable APText wrapElement(final @NonNull MarkupElement element, final double x, final double y,
			final boolean underline, final @Nullable Paint paint, final int fontStyle) {
		final String s = art.computeText(element, getRedrawer());
		return s == null ? null : nextChild(element, x, y, underline, paint, fontStyle, s);
	}

	/**
	 * @return NULL iff there's no room for more text. (Non-null even if we'll
	 *         be redrawCallback()ed.)
	 */
	private @Nullable APText nextChild(final @Nullable MarkupElement element, double x, double y,
			final boolean underline, final @Nullable Paint paint, final int fontStyle, final @NonNull String s) {
		// UtilString.indentMore("MarkupViz.nextChild " + element + " x=" + x
		// + " nameW(x)=" + nameW(x) + " isWrapText=" + isWrapText
		// + " isWrapOnWordBoundaries=" + isWrapOnWordBoundaries + " '"
		// + s + "' sLength=" + s.length());
		final int sLength = s.length();
		assert sLength > 0 : element;
		String truncatedS = truncateText(s, x);
		final int truncatedSlength = truncatedS.length();
		boolean useNewLine = false;
		if (truncatedSlength < sLength) {
			if (isWrapText && isWrapOnWordBoundaries) {
				int breakIndex = truncatedS.lastIndexOf(' ');
				if (breakIndex > 0) {
					truncatedS = truncatedS.substring(0, breakIndex);
				} else if (x > getXmargin()) {
					useNewLine = true;
					final String truncatedS2 = truncateText(s, getXmargin());
					breakIndex = truncatedS2.lastIndexOf(' ');
					if (breakIndex > 0 && truncatedS2.length() < sLength) {
						truncatedS = truncatedS2.substring(0, breakIndex);
					} else {
						truncatedS = truncatedS2;
					}
				}
			} else if (truncatedSlength == 0) {
				useNewLine = true;
				truncatedS = truncateText(s, getXmargin());
			}
		}
		if (useNewLine) {
			y = nextLine(x, y);
			x = getXmargin();
		}
		APText mostRecentChild = null;
		if (!incomplete) {
			assert truncatedS.length() > 0 : "x=" + x + " '" + s + "' useNewLine=" + useNewLine;
			mostRecentChild = PerspectiveMarkupAPText.getFacetText(element, truncatedS, art, underline, getRedrawer(),
					x, y, paint, getReplayLocation(element));
			final Font font = mostRecentChild.getFont();
			if (fontStyle != font.getStyle()) {
				mostRecentChild.setFont(font.deriveFont(fontStyle));
			}

			// this duplicates wrapping work done in computeText
			mostRecentChild = nextChildRemainder(mostRecentChild, s, fontStyle, underline);
		}

		// UtilString.indentLess("MarkupViz.nextChild " + element + " ⇒ "
		// + mostRecentChild);
		return mostRecentChild;
	}

	/**
	 * addChild(mostRecentChild). Recurse on nextChild() iff mostRecentChild
	 * won't all fit on the same line.
	 *
	 * @return mostRecentChild or result of nextChild().
	 */
	private @Nullable APText nextChildRemainder(final @NonNull APText mostRecentChild,
			final @NonNull String recentPlusRest, final int fontStyle, final boolean underline) {
		final int recentPlusRestLength = recentPlusRest.length();
		addChild(mostRecentChild);
		if (mostRecentChild instanceof BungeeAPText) {
			((BungeeAPText) mostRecentChild).setUnderline(getReplayLocation(mostRecentChild));
		}
		APText nextChild = mostRecentChild;

		final int recentLength = mostRecentChild.getText().length();
		if (recentLength < recentPlusRestLength) {
			final String rest = recentPlusRest.substring(recentLength).trim();
			// wrapping will take the place of leading whitespace
			if (rest.length() > 0) {
				final Paint paint = mostRecentChild.getTextPaint();
				final MarkupElement element = (mostRecentChild instanceof PerspectiveMarkupAPText)
						? ((PerspectiveMarkupAPText) mostRecentChild).treeObject() : null;
				final double x = mostRecentChild.getIntMaxX();
				final double y = mostRecentChild.getYOffset();
				// if (nameW(x) < art.lineH()) {
				// // go to next line and start at x=0
				// x = getXmargin();
				// y += art.lineH();
				// }
				nextChild = nextChild(element, x, y, underline, paint, fontStyle, rest);
			}
		}
		return nextChild;
	}

	protected @NonNull ReplayLocation getReplayLocation(final @Nullable Object element) {
		ReplayLocation result = replayLocation;
		if (element instanceof PerspectiveMarkupAPText) {
			result = ((PerspectiveMarkupAPText) element).getReplayLocation();
		}
		// System.out.println("MarkupViz.getReplayLocation " + element + " " +
		// replayLocation);
		return result;
	}

	private @NonNull String truncateText(final @NonNull String s, final double x) {
		String result = "";
		final double nameW = nameW(x);
		if (nameW > 0.0) {
			try {
				result = art.truncateText(s, nameW);
			} catch (final NullPointerException e) {
				System.err.println("While MarkupViz.truncateText " + PiccoloUtil.ancestorString(getParent()) + "\n"
						+ this + " '" + s + "' nameW=" + nameW + ":\n");
				throw (e);
			}
		}
		return result;
	}

	/**
	 * Sets incomplete if next line would exceed height.
	 *
	 * @return y for next line.
	 */
	private double nextLine(final double x, final double y) {
		justifyLine();
		assert x > getXmargin() : "Already at the beginning of line: " + x;
		final double newY = y + lineH();
		if (!isWrapText || newY + lineH() + getYmargin() > getHeight()) {
			incomplete = true;
		}
		return newY;
	}

	/**
	 * @param x
	 * @return amount of drawable [trimmed] width after x. (x is already
	 *         indented by trimW / 2.0).
	 */
	private double nameW(final double x) {
		final double w = getWidth() - getXmargin() - x;
		// assert w > 0.0 : this + " tw=" + trimmedW() + " x="+ x;
		return w;
	}

	public double getXmargin() {
		return trimW / 2.0;
	}

	public double getYmargin() {
		return trimH / 2.0;
	}

	/**
	 * Justify the line containing (same yOffset) our last child (the most
	 * recent line), LEFT, CENTER, or RIGHT by updating their offset.
	 */
	private void justifyLine() {
		if (justification > Component.LEFT_ALIGNMENT) {
			final int nChildren = getChildrenCount();
			if (nChildren > 0) {
				final BungeeAPText lastChild = (BungeeAPText) getChild(nChildren - 1);
				final double y = lastChild.getYOffset();
				final double x = lastChild.getIntMaxX();
				final double offset = Math.rint(nameW(x) * justification);
				for (int i = 0; i < nChildren; i++) {
					final PNode child = getChild(i);
					if (child.getYOffset() == y) {
						child.setOffset(child.getXOffset() + offset, y);
					}
				}
			}
		}
	}

	/**
	 * @param xMargin
	 *            margin on ONE SIDE
	 * @param yMargin
	 *            margin on ONE SIDE
	 */
	public void setMargins(final double xMargin, final double yMargin) {
		assert xMargin >= 0.0 && yMargin >= 0.0 : xMargin + " " + yMargin;
		trimW = 2.0 * xMargin;
		trimH = 2.0 * yMargin;
	}

	/**
	 * If constrain to content, update width and height to contain all children,
	 * with margins of trimW and trimH.
	 */
	private void constrainWnHtoContent() {
		// System.out.println("trim " + content.toString());
		if (constrainWidthToContent || constrainHeightToContent) {
			try {
				final Rectangle2D childrenBounds = Alignment.getChildrenBounds(this, trimW, trimH);
				if (constrainWidthToContent) {
					setWidth(childrenBounds.getWidth());
				}
				if (constrainHeightToContent) {
					setHeight(childrenBounds.getHeight());
				}
			} catch (final AssertionError e) {
				System.err.println("While constrainWnHtoContent with content=" + content + " for " + this + ":\n");
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param changedFacets
	 *            - Facets whose highlighting has changed.
	 *
	 *            Update child FacetTexts representing one of the facets.
	 *
	 *            No one uses FacetPText, so nothing to do
	 * @param queryVersion
	 * @return whether highlighting changed
	 */
	public boolean updateHighlighting(final @NonNull Set<? extends Perspective> changedFacets, final int queryVersion) {
		boolean result = false;
		final int nChildren = getChildrenCount();
		for (int i = 0; i < nChildren; i++) {
			final PNode node = getChild(i);
			if (node instanceof FacetNode) {
				final FacetNode facetNode = (FacetNode) node;
				if (changedFacets.contains(facetNode.getFacet())
						&& facetNode.updateHighlighting(queryVersion, YesNoMaybe.MAYBE)) {
					result = true;
				}
			}
		}
		// System.out.println("MarkupViz.updateHighlighting " + changedFacets
		// + " ⇒ " + result);
		return result;
	}

	public void queryValidRedraw(final int queryVersion, final @Nullable Pattern textSearchPattern) {
		final int nChildren = getChildrenCount();
		for (int i = 0; i < nChildren; i++) {
			final PNode node = getChild(i);
			if (node instanceof PerspectiveMarkupAPText) {
				((PerspectiveMarkupAPText) node).queryValidRedraw(queryVersion, textSearchPattern);
			} else if (node instanceof BungeeAPText) {
				((BungeeAPText) node).queryValidRedraw(textSearchPattern);
			}
		}
	}

	private @NonNull Query query() {
		return art.getQuery();
	}

	@Override
	public String getText() {
		return toString();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, content);
	}

	@Override
	public @NonNull String nodeDesc() {
		return " content='" + content + "'";
	}

	public boolean isEmpty() {
		return content.toText(this).trim().length() == 0;
		// return content.isEmpty();
	}

	private final Collection<MarkupElement> UNDERLINES = Arrays.asList(DefaultMarkup.UNDERLINE_TAG,
			DefaultMarkup.NO_UNDERLINE_TAG);

	@Override
	public void setUnderline(final boolean isUnderline, final @NonNull YesNoMaybe isRerender) {
		final Markup _content = DefaultMarkup.newMarkup(content);
		_content.removeAll(UNDERLINES);
		if (isUnderline) {
			_content.add(0, DefaultMarkup.UNDERLINE_TAG);
		}
		if (isRerender == YesNoMaybe.YES || (isRerender == YesNoMaybe.MAYBE && !_content.equals(content))) {
			setContent(_content);
		}
	}

	@Override
	public void setTextPaint(final @Nullable Paint aPaint) {
		boolean result = defaultTextPaint != aPaint;
		defaultTextPaint = aPaint;
		final Markup _content = DefaultMarkup.newMarkup(content);
		for (final Iterator<MarkupElement> it = _content.iterator(); it.hasNext();) {
			final MarkupElement markupElement = it.next();
			if (markupElement instanceof MarkupPaintElement) {
				it.remove();
				result = true;
			}
		}
		if (result) {
			setContent(_content);
		}
	}

	@Override
	public void setConstrainWidthToTextWidth(final boolean _constrainWidthToTextWidth) {
		constrainWidthToContent = _constrainWidthToTextWidth;
	}

	@Override
	public void setConstrainHeightToTextHeight(final boolean _constrainHeightToTextHeight) {
		constrainHeightToContent = _constrainHeightToTextHeight;
	}

	@Override
	public boolean maybeSetText(final String text) {
		final boolean result = !Objects.deepEquals(text, getText());
		if (result) {
			final Markup _content = text == null ? DefaultMarkup.newMarkup()
					: DefaultMarkup.newMarkup(MarkupStringElement.getElement(text));
			setContent(_content);
		}
		return result;
	}

}
