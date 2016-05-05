package edu.cmu.cs.bungee.client.viz.tagWall;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;

class LabeledBars extends DefaultLabeledBarsAndLabels<Bar> {

	LabeledBars(final PerspectiveViz _perspectiveViz) {
		super((int) _perspectiveViz.front.getWidth(), 1, _perspectiveViz.front, _perspectiveViz);
		assert parentPNode.getVisible() : parent.path(true, true) + "\n" + parentPNode + " rank.frontH()="
				+ _perspectiveViz.rank.frontH() + " front.getGlobalBounds()=" + _perspectiveViz.front.getGlobalBounds();
	}

	@Override
	protected void ensureChildren() {
		assert !getChildren().contains(null) : this + " " + parent.prefetchStatus();
		super.ensureChildren();
	}

	@Override
	protected @NonNull Bar getLabel(final Perspective child, final int from, final int to) {
		final PerspectiveViz perspectiveViz = perspectiveViz();
		assert to < perspectiveViz.getWidth() : to + " " + perspectiveViz.getWidth() + " visibleWidth=" + visibleWidth;
		Bar bar = getFromCache(child);
		if (bar == null) {
			bar = new Bar(perspectiveViz, from, to, child);
		} else {
			bar.update(from, to);
		}
		assert bar.getParent() == null : bar;
		return bar;
	}

}
