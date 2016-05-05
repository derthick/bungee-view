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
public abstract class DefaultLabeledForPerspective<Z extends PNode & FacetNode>
		extends DefaultLabeledForPV<Perspective, Z> {

	public DefaultLabeledForPerspective(final int width, final int labelW, final @NonNull LazyPNode _parentPNode,
			final @NonNull PerspectiveViz _pv) {
		super(width, labelW, _pv.p, _parentPNode);
	}

	@Override
	protected void validate() {
		final boolean isPrefetched = parent.ensurePrefetched(PrefetchStatus.PREFETCHED_NO_NAMES, this);
		if (isPrefetched && perspectiveViz().updateQueryVersion()) {
			// setForceLabels(art().getHighlightedFacets());
			super.validate();
		} else {
			System.out.println("DefaultLabeledForPerspective.validate not validating: " + this + " isPrefetched="
					+ isPrefetched + " query().version()=" + query().version());
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
				final Perspective perspective = entry.getKey();
				final Perspective perspectiveParent = perspective.getParent();
				assert false : this + " z=" + z + " zParent=" + z.getParent() + ", but " + parentPNode
						+ " was expected. perspective=" + perspective + " perspectiveParent=" + perspectiveParent;
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
	boolean updateHighlighting(final @NonNull Collection<Perspective> changedFacets, final int queryVersion,
			final @NonNull YesNoMaybe isRerender) {
		// If a highlightedFacet isn't displayed, validating may display it.
		boolean result = false;
		for (final Perspective changedFacet : changedFacets) {
			if (changedFacet.getParent() == parent) {
				final FacetNode facetNode = lookupLabel(changedFacet);
				if (facetNode == null) {
					if (art().isHighlighted(changedFacet)) {
						result = true;
						break;
					}
				}
			}
		}
		if (result) {
			// There are changedFacets that have no label.
			validate();
		} else {
			for (final Perspective changedFacet : changedFacets) {
				if (changedFacet.getParent() == parent) {
					final FacetNode facetNode = lookupLabel(changedFacet);

					if (facetNode != null && facetNode.updateHighlighting(queryVersion, isRerender)) {
						// There are changedFacets that changed highlighting.
						result = true;
					}
				}
			}
		}
		return result;
	}

	@Override
	protected @NonNull Query query() {
		return parent.query();
	}

}
