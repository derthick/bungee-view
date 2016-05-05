package edu.cmu.cs.bungee.client.viz.tagWall;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.PrefetchStatus;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.DefaultLabeledForPV;
import edu.cmu.cs.bungee.client.viz.markup.FacetNode;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.umd.cs.piccolo.PNode;

/**
 * LabeledBars and LabeledLabels
 */
public abstract class DefaultLabeledBarsAndLabels<Z extends PNode & FacetNode>
		extends DefaultLabeledForPV<Perspective, Z> {

	public DefaultLabeledBarsAndLabels(final int width, final int labelW, final @NonNull LazyPNode _parentPNode,
			final @NonNull PerspectiveViz _pv) {
		super(width, labelW, _pv.p, _parentPNode);
	}

	@Override
	protected void validate() {
		if (parent.isPrefetched(PrefetchStatus.PREFETCHED_NO_NAMES) && perspectiveViz().updateQueryVersion()) {
			// setForceLabels(art().getHighlightedFacets());
			super.validate();
		}
	}

	@Override
	public void updateCounts() {
		super.updateCounts();
		assert parentPNode == null || totalChildCount == perspective().getTotalChildTotalCount() : this
				+ " totalChildCount=" + totalChildCount + " perspective.getTotalChildTotalCount()="
				+ perspective().getTotalChildTotalCount() + " nChildren=" + nChildren();
	}

	@Override
	protected boolean assertAllLabelsAreChildren() {
		for (final Entry<Perspective, Z> entry : facetToLabelTable.entrySet()) {
			final Z z = entry.getValue();
			if (z.getParent() != parentPNode) {
				assert false : this + " z=" + z + " zParent=" + z.getParent() + ", but " + parentPNode
						+ " was expected. perspective path=" + entry.getKey().path(true, true);
			}
		}
		return true;
	}

	@Override
	public List<Perspective> getChildren() {
		return parent.getChildrenRaw();
	}

	@Override
	protected int childIndex(final @Nullable Perspective child) {
		return child != null ? child.whichChildRaw() : -1;
	}

	@Override
	public int count(final Perspective child) {
		return child.getTotalCount();
	}

	@Override
	// Use onCount for priority, but totalCount for count.
	protected double priorityCount(final int childIndex) {
		final Perspective child = childFromIndex(childIndex);
		return child.getOnCount() * visiblePercent(childIndex) + bonus(child);
	}

	/**
	 * @return whether any changedFacets changed highlighting.
	 */
	boolean updateHighlighting(final @NonNull Collection<Perspective> changedFacets, final int queryVersion) {
		// If a highlightedFacet isn't displayed, validating may display it.
		boolean result = false;
		boolean mustValidate = false;
		for (final Perspective changedFacet : changedFacets) {
			if (changedFacet.getParent() == parent) {
				final FacetNode facetNode = lookupLabel(changedFacet);
				if ((facetNode == null) == (art().isHighlighted(changedFacet))) {
					mustValidate = true;
					break;
				} else if (facetNode != null && facetNode.updateHighlighting(queryVersion, YesNoMaybe.MAYBE)) {
					// There are facets that changed highlighting.
					result = true;
				}
			}
		}
		if (mustValidate) {
			// There are facets that have no label, or there are newly
			// unhighlighted facets that might be occluding higher-count facets.
			validate();
			result = true;
		}
		return result;
	}

	@Override
	protected @NonNull Query query() {
		return parent.query();
	}

}
