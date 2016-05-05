/*

 Created on Mar 20, 2005

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

package edu.cmu.cs.bungee.client.viz.bungeeCore;

import java.awt.Color;
import java.awt.Component;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.markup.MarkupViz;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;

public final class MouseDocLine extends LazyPNode implements RedrawCallback { // NO_UCD
	// (use
	// default)

	private static final @NonNull Color TIP_BG = BungeeConstants.HELP_COLOR;

	private static final @NonNull MarkupElement FILTER_CONSTANT_HAVING = MarkupStringElement.getElement(" having ");

	private static final @NonNull MarkupElement FILTER_CONSTANT_CLICKING = MarkupStringElement
			.getElement("Clicking will: ");

	private final @NonNull MarkupViz clickDesc;

	/**
	 * If popups are disabled, describe facets here.
	 */
	private final @NonNull MarkupViz facetDesc;

	private final @NonNull MarkupViz tip;

	private final @NonNull Bungee art;

	MouseDocLine(final @NonNull Bungee _art) {
		// Order so clickDesc is behind facetDesc is behind tip
		art = _art;
		clickDesc = new MarkupViz(art, BungeeConstants.TEXT_FG_COLOR);
		clickDesc.setVisible(false);
		clickDesc.constrainWidthToContent = true;
		addChild(clickDesc);

		facetDesc = new MarkupViz(art, BungeeConstants.TEXT_FG_COLOR);
		facetDesc.setVisible(false);
		facetDesc.constrainWidthToContent = true;
		facetDesc.setJustification(Component.RIGHT_ALIGNMENT);
		facetDesc.setRedrawer(this);
		addChild(facetDesc);

		tip = new MarkupViz(art, BungeeConstants.TEXT_FG_COLOR);
		tip.setVisible(false);
		tip.setPaint(TIP_BG);
		addChild(tip);

		setPaint(BungeeConstants.HEADER_BG_COLOR);
	}

	void validate(final double w, final double h) {
		setBounds(0, 0, w, h);
		clickDesc.setBounds(0, 0, w, h);
		facetDesc.setBounds(0, 0, w, h);
		tip.setBounds(0, 0, w, h);
	}

	@Override
	public double minHeight() {
		return art.lineH();
	}

	void setTip(final @Nullable String s) {
		// System.out.println("MouseDocLine.setTip " + s);
		Markup v = null;
		if (s != null) {
			v = DefaultMarkup.newMarkup(MarkupStringElement.getElement(s));
		}
		setTip(v);
	}

	private static final @NonNull MarkupElement ESCAPE_MESSAGE = MarkupStringElement
			.getElement(" (Press ESCAPE to hide this message.)");

	void setTip(final @Nullable Markup v) {
		if (v != null) {
			v.add(ESCAPE_MESSAGE);
			if (tip.setContent(v)) {
				tip.setWidth(getWidth());
				tip.layout();
				tip.setWidth(2.0 * art.lineH());
				tip.animateToBounds(0.0, 0.0, getWidth(), getHeight(), BungeeConstants.DATA_ANIMATION_MS);
				tip.moveToFront();
			}
			System.err.println(" MouseDocLine.setTip " + v.toText(this) + "\n query: " + query().getName(this));
		}
		tip.setVisible(v != null);
	}

	public @Nullable Markup getTipMarkup() {
		return tip.getContent();
	}

	/**
	 * tip.setVisible(false)
	 */
	public void doHideTransients() {
		tip.setVisible(false);
	}

	void setClickDesc(final @Nullable String s, final boolean noClick) {
		// System.out.println("MouseDocLine.setClickDesc " + s);
		Markup v = null;
		if (s != null) {
			v = DefaultMarkup.newMarkup(MarkupStringElement.getElement(s));
		}
		setClickDesc(v, noClick);
	}

	void setClickDesc(@Nullable Markup markup, final boolean noClick) {
		// System.out.println("MouseDocLine.setClickDesc " + s);
		art.getCanvas().paintImmediately();
		if (markup == null) {
			markup = art.defaultClickDesc();
		} else if (!noClick) {
			assert UtilString.isNonEmptyString(markup.toText(null));
			markup.add(0, FILTER_CONSTANT_CLICKING);
		}
		if (markup == null) {
			clickDesc.setVisible(false);
		} else {
			setClickDescInternal(markup);
		}
		repaintNow();
		art.getCanvas().paintImmediately();
	}

	void setClickDescInternal(final @NonNull Markup markup) {
		if (clickDesc.setContent(markup)) {
			// Allow room for facetDoc to at least say
			// "mmmm (100% of nnnn) P=1E-10"
			clickDesc.setBounds(0, 0, getWidth(), getHeight());
			clickDesc.layout();
		}
		clickDesc.setVisible(true);
	}

	void showPopup(final @Nullable Perspective facet) {
		// System.out.println("MouseDocLine.showPopup " + facet);
		if (facet != null) {
			final Markup desc = DefaultMarkup.emptyMarkup();
			if (facet.getParent() == null) {
				desc.add(query().getGenericObjectMarkup(true));
				desc.add(FILTER_CONSTANT_HAVING);
			}
			desc.add(facet);
			desc.add(facetInfo(facet));
			facetDesc.setContent(desc);
			facetDesc.setVisible(true);
			facetDesc.setBounds(0, 0, getWidth(), getHeight());
			query().queueOrRedraw(this);
		}
	}

	private @NonNull Query query() {
		return art.getQuery();
	}

	@Override
	public void redrawCallback() {
		if (facetDesc.getVisible()) {
			facetDesc.layout();

			// Right justify
			facetDesc.setXoffset(getWidth() - facetDesc.getWidth());
		}
		if (tip.getVisible()) {
			tip.layout();
		}
	}

	/**
	 * @param queryVersion
	 * @return e.g. <MarkupStringElement ": 345 matches (12% of 456) p=0.01">
	 */
	private @NonNull MarkupElement facetInfo(final @NonNull Perspective facet) {
		final int onCount = facet.getOnCount();
		final int count = facet.getTotalCount();
		final StringBuffer buf = new StringBuffer(60);
		buf.append(": ").append(UtilString.addCommas(onCount));
		if (onCount == 1) {
			buf.append(" match (");
		} else {
			buf.append(" matches (");
		}
		UtilString.formatPercent(onCount, count, buf, true);
		buf.append(" of ").append(UtilString.addCommas(count)).append(") ");
		art.appendPvalue(facet, buf);
		return MarkupStringElement.getElement(Util.nonNull(buf.toString()));
	}

	<V extends Perspective> boolean updateBrushing(final @NonNull Set<V> changedFacets, final int queryVersion) {
		return clickDesc.updateHighlighting(changedFacets, queryVersion);
	}

}
