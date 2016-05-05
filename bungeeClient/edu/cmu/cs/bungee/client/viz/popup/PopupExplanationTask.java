package edu.cmu.cs.bungee.client.viz.popup;

import java.util.Collections;
import java.util.SortedSet;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.explanation.ExplanationTask;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;

@Immutable
public class PopupExplanationTask implements ExplanationTask {

	private final @NonNull InfluenceDiagramCreator influenceDiagramCreator;
	private final @NonNull Perspective startPopupFacet;
	private final @Immutable @NonNull SortedSet<Perspective> primaryFacets;
	private final @NonNull PopupSummary popupSummary;

	public PopupExplanationTask(final @NonNull PopupSummary _popupSummary,
			final @NonNull InfluenceDiagramCreator _influenceDiagramCreator, final @NonNull Perspective _popupFacet,
			final @NonNull SortedSet<Perspective> _primaryFacets) {
		popupSummary = _popupSummary;
		influenceDiagramCreator = _influenceDiagramCreator;
		startPopupFacet = _popupFacet;
		primaryFacets = Util.nonNull(Collections.unmodifiableSortedSet(_primaryFacets));
	}

	@Override
	public boolean isTaskCurrent() {
		// assert influenceDiagramCreator.isQueueEmpty() : this;
		return (startPopupFacet == popupSummary.facet) && influenceDiagramCreator.isQueueEmpty()
				&& popupSummary.isQueryVersionCurrent() && !influenceDiagramCreator.getExited();
	}

	@Override
	@Immutable
	public SortedSet<Perspective> primaryFacets() {
		return primaryFacets;
	}

	@Override
	public @Immutable ExplanationTask getModifiedInstance(final @NonNull SortedSet<Perspective> facets) {
		return new PopupExplanationTask(popupSummary, influenceDiagramCreator, startPopupFacet, facets);
	}

	@Override
	public String toString() {
		return UtilString.toString(this, startPopupFacet + " " + primaryFacets);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + influenceDiagramCreator.hashCode();
		result = prime * result + popupSummary.hashCode();
		result = prime * result + primaryFacets.hashCode();
		result = prime * result + startPopupFacet.hashCode();
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final PopupExplanationTask other = (PopupExplanationTask) obj;
		if (!influenceDiagramCreator.equals(other.influenceDiagramCreator)) {
			return false;
		}
		if (!popupSummary.equals(other.popupSummary)) {
			return false;
		}
		if (!primaryFacets.equals(other.primaryFacets)) {
			return false;
		}
		if (!startPopupFacet.equals(other.startPopupFacet)) {
			return false;
		}
		return true;
	}

}
