package edu.cmu.cs.bungee.client.viz.tagWall;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.markup.RotatedFacetText;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;

/**
 * priority and label is p.onCount, even though count is p.totalCount
 */
class LabeledLabels extends DefaultLabeledBarsAndLabels<RotatedFacetText> {

	LabeledLabels(final @NonNull PerspectiveViz _perspectiveViz) {
		super((int) _perspectiveViz.labels.getWidth(), _perspectiveViz.labelHprojectionW(), _perspectiveViz.labels,
				_perspectiveViz);
	}

	@Override
	protected void validate() {
		if (perspectiveViz().isConnected()) {
			// If we're being animated to invisibility, don't bother validating.

			// System.out.println("\nLabeledLabels.validate " + this + "
			// parentPNode.getVisible()="
			// + parentPNode.getVisible() + "\n parentPNode:" +
			// PiccoloUtil.ancestorString(parentPNode));
			super.validate();
		}
	}

	@Override
	protected @NonNull RotatedFacetText getLabel(final @NonNull Perspective child, final int from, final int to) {
		RotatedFacetText label1 = getFromCache(child);

		// only used in asserts
		final boolean isFromCache = label1 != null;

		if (label1 == null) {
			label1 = new RotatedFacetText(perspectiveViz(), child);
		}
		// label1.getText() may be null pending nameGetter.
		assert label1.getFont() == art().getCurrentFont() : perspectiveViz() + " isFromCache=" + isFromCache
				+ ": setFont should have cleared zCache.\n cached font=" + label1.getFont() + " art font="
				+ art().getCurrentFont();
		assert label1.isShowingCheckBox() == art().getShowCheckboxes() : perspectiveViz() + "." + child
				+ " label1.showCheckBox=" + label1.showCheckBox + " isFromCache=" + isFromCache
				+ ": setCheckboxes should have cleared zCache.";

		label1.updateOnCount();
		label1.setColorAndPTextOffset((from + to) / 2);

		assert label1.getParent() == null : label1;
		return label1;
	}

	public void setCheckboxes() {
		final RotatedFacetText child = UtilArray.some(getAllLabels());
		if (child != null && (child.isShowingCheckBox() != art().getShowCheckboxes())) {
			removeAllLabels(true);
		}
	}

}
