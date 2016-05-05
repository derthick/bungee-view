package edu.cmu.cs.bungee.client.viz;

import java.awt.Font;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.TopTags;
import edu.cmu.cs.bungee.client.query.TopTags.TagRelevance;
import edu.cmu.cs.bungee.client.query.markup.TopTagsPerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.client.viz.markup.BungeeAPText;
import edu.cmu.cs.bungee.client.viz.markup.PerspectiveMarkupAPText;
import edu.cmu.cs.bungee.client.viz.markup.TopTagsPerspectiveMarkupAPText;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.PNodePool;
import edu.umd.cs.piccolo.PNode;

public class TopTagsViz extends BungeeFrame implements RedrawCallback {

	public final PNodePool<Perspective, TopTagsPerspectiveMarkupAPText> zPool = new PNodePool<>(false);
	private double zPoolNumW = 0.0;

	public TopTagsPerspectiveMarkupAPText getFromZPool(final @NonNull Perspective perspective, final double _numW) {
		maybeClearZPool();
		assert _numW == zPoolNumW : zPoolNumW + " " + _numW;
		return zPool.pop(perspective);
	}

	void putToZPool(final @NonNull Collection<TopTagsPerspectiveMarkupAPText> texts) {
		maybeClearZPool();
		for (final TopTagsPerspectiveMarkupAPText text : texts) {
			if (text.numW == zPoolNumW) {
				zPool.put(text.getFacet(), text);
			}
		}
	}

	void putToZPool(final @NonNull TopTagsPerspectiveMarkupAPText text) {
		maybeClearZPool();
		if (text.numW == zPoolNumW) {
			zPool.put(text.getFacet(), text);
		}
	}

	private void maybeClearZPool() {
		final double numW = numW();
		if (numW != zPoolNumW) {
			zPoolNumW = numW;
			zPool.clear();
		}
	}

	// private double nameNnumW() {
	// return numW();
	// }

	private double nameW() {
		final double result = usableWifNoScrollbar() - numW() - art.lineH();
		assert result == Math.floor(result);
		return result;
	}

	/**
	 * Always equal to an int
	 */
	private double numW() {
		return art.numWidth(-100);
	}

	public TopTagsViz(final @NonNull Bungee bungee) {
		super(bungee, BungeeConstants.TAGWALL_FG_COLOR, "Top Tags", false);
		setVisible(false);
		setInitted(true);
	}

	private boolean setVisibility() {
		final boolean isVis = query.isQueryValid() && query.isPartiallyRestricted();
		setVisible(isVis);
		return isVis;
	}

	@Override
	public void maybeSetVisible(final boolean isVisible) {
		setVisible(isVisible && query.isQueryValid() && query.isPartiallyRestricted());
	}

	@Override
	protected boolean setFont(final Font font) {
		final boolean result = super.setFont(font);
		if (result) {
			validateInternal();
		}
		return result;
	}

	@Override
	public void validateInternal() {
		super.validateInternal();
		redraw();
	}

	@Override
	public void redrawCallback() {
		redraw();
	}

	public void queryValidRedraw(final Pattern textSearchPattern) {
		if (setVisibility()) {
			redraw();
			for (final Iterator<PNode> it = getChildrenIterator(); it.hasNext();) {
				final PNode node = it.next();
				if (node instanceof BungeeAPText) {
					((BungeeAPText) node).queryValidRedraw(textSearchPattern);
				}
			}
		}
	}

	private void redraw() {
		if (setVisibility()) {
			removeMostChildren();
			final Collection<Perspective> unnamed = new LinkedList<>();
			double y = getTopMargin();
			final int nLines = (int) ((getHeight() - y - internalYmargin()) / (art.lineH() * 2.0));
			final TopTags topTags = query.topTags(nLines, this);
			y = redrawTopOrBottom(y, nLines, topTags.top, unnamed);
			redrawTopOrBottom(y + internalYmargin(), nLines, topTags.bottom, unnamed);
			resetBoundaryOffsetIfNotDragging();

			// System.out.println("TopTagsViz.redraw nLines=" + nLines + "
			// getChildrenCount=" + getChildrenCount()
			// + " unnamed.size()=" + unnamed.size() + " topTags.top.size()=" +
			// topTags.top.size());

			query.queueGetNames(unnamed, this);
		}
	}

	/**
	 * Remove all but label and boundary
	 */
	private void removeMostChildren() {
		final LinkedList<TopTagsPerspectiveMarkupAPText> toRemove = new LinkedList<>(getChildrenReference());
		toRemove.remove(label);
		toRemove.remove(boundary);
		putToZPool(toRemove);
		removeChildren(toRemove);
	}

	private double redrawTopOrBottom(double y, final int nLines, final SortedSet<TagRelevance> tagRelevances,
			final Collection<Perspective> unnamed) {
		assert getRoot() != null;
		final double nameW = nameW();
		final double numW = numW();
		final double textW = numW + nameW;
		final Iterator<TagRelevance> it = tagRelevances.iterator();
		int line = 0;
		// check line in case cached TopTags was computed for more lines.
		while (it.hasNext() && line++ < nLines) {
			final TagRelevance tagRelevance = it.next();
			final int score = UtilMath.roundToInt(tagRelevance.relevanceScore());
			final Perspective facet = tagRelevance.getFacet();
			assert Math.abs(score) <= 100 : facet;
			if (facet.getNameIfCached() == null) {
				unnamed.add(facet);
			} else {
				// callback will be text itself
				final TopTagsPerspectiveMarkupAPText topTagsPerspectiveMarkupAPText = (TopTagsPerspectiveMarkupAPText) PerspectiveMarkupAPText
						.getFacetText(new TopTagsPerspectiveMarkupElement(facet, score), null, art, numW, nameW, true,
								false, false, true, /* onCount */score, false, null, internalXmargin(), y,
								ReplayLocation.DEFAULT_REPLAY);
				topTagsPerspectiveMarkupAPText.setUnderline(
						UserAction.isDefaultLocationUnderline(art, facet, topTagsPerspectiveMarkupAPText),
						YesNoMaybe.MAYBE);
				topTagsPerspectiveMarkupAPText.setWidth(textW);
				addChild(topTagsPerspectiveMarkupAPText);
			}
			y += art.lineH();
		}
		return y;
	}

	public boolean updateBrushing(final Set<Perspective> changedFacets, final int queryVersion) {
		boolean result = false;
		for (final Iterator<PNode> it = getChildrenIterator(); it.hasNext();) {
			final PNode node = it.next();
			if (node instanceof PerspectiveMarkupAPText) {
				final PerspectiveMarkupAPText child = (PerspectiveMarkupAPText) node;
				final Perspective childFacet = child.getFacet();
				if (changedFacets.contains(childFacet) && child.updateHighlighting(queryVersion)) {
					result = true;
				}
			}
		}
		return result;
	}

}
