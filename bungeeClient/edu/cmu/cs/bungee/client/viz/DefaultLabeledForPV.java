package edu.cmu.cs.bungee.client.viz;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.tagWall.PerspectiveViz;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.umd.cs.piccolo.PNode;

/**
 * Known subclasses: DefaultLabeledBarsAndLabels, DefaultLabeledForPerspective,
 * and LetterLabeled
 */
public abstract class DefaultLabeledForPV<V, Z extends PNode> extends DefaultLabeledBungee<V, Z> {

	public DefaultLabeledForPV(final int width, final int labelW, final @NonNull V _parentV,
			final @NonNull LazyPNode _parentPNode) {
		super(width, labelW, _parentV, _parentPNode);
	}

	@Override
	// counts depend on parent and query().getTotalCount()
	protected @NonNull Object getCountsHash() {
		final Integer totalCount = query().getTotalCount();
		if (!(countsHash instanceof Object[]) || ((Object[]) countsHash)[0] != parent
				|| ((Object[]) countsHash)[1] != totalCount) {
			return new Object[] { parent, totalCount };
		} else {
			return countsHash;
		}
	}

	@Override
	protected void validate() {
		super.validate();
		assert !parentPNode.getVisible() || totalChildCount == perspective().getTotalChildTotalCount() : this
				+ " totalChildCount=" + UtilString.addCommas(totalChildCount) + ", but p.getTotalChildTotalCount()="
				+ UtilString.addCommas(perspective().getTotalChildTotalCount());
	}

	protected double bonus(final Perspective child) {
		final int highlightPoints = art().highlightPoints(child);
		if (highlightPoints > 0) {
			return highlightPoints * totalChildCount;
		} else {
			return 0.0;
		}
	}

	@Override
	protected @NonNull Bungee art() {
		return perspectiveViz().art();
	}

	@Override
	protected @NonNull Perspective perspective() {
		return perspectiveViz().p;
	}

	protected @NonNull PerspectiveViz perspectiveViz() {
		assert parentPNode != null : this;
		final PerspectiveViz pv = (PerspectiveViz) parentPNode.getParent();
		assert pv != null;
		return pv;
	}

	@SuppressWarnings("null")
	@Nullable
	protected PerspectiveViz maybeUninstantiatedPerspectiveViz() {
		return parentPNode != null ? (PerspectiveViz) parentPNode.getParent() : null;
	}

}
