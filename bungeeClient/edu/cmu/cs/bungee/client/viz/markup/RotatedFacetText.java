package edu.cmu.cs.bungee.client.viz.markup;

import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.tagWall.PerspectiveViz;
import edu.cmu.cs.bungee.client.viz.tagWall.TagWall;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * A FacetText used by a PV, either as a Letter or as a Label. Works around
 * Piccolo rotated-text selection bugs.
 */
public final class RotatedFacetText extends PerspectiveMarkupAPText {
	public static final double TEXT_ANGLE = Math.PI / 4.0;
	public static final double TEXT_ANGLE_SINE = Math.sin(TEXT_ANGLE);
	public static final double TEXT_ANGLE_COSINE = Math.cos(TEXT_ANGLE);
	public static final double TEXT_ANGLE_TANGENT = TEXT_ANGLE_SINE / TEXT_ANGLE_COSINE;

	private final PerspectiveViz perspectiveViz;
	int displayedOnCount = -1;

	/**
	 * Only called by LabeledLabels.getLabel
	 *
	 * constrainWidth defaults to true; constrainHeight and isWrap default to
	 * false. justification defaults to LEFT_ALIGNMENT.
	 */
	public RotatedFacetText(final @NonNull PerspectiveViz _perspectiveViz, final @NonNull Perspective _facet) {
		super(_facet, _perspectiveViz);
		perspectiveViz = _perspectiveViz;
		setRotation(-TEXT_ANGLE);

		// maybeIncfGGG();
	}

	@Override
	public void queryValidRedraw(@SuppressWarnings("unused") final int queryVersion,
			final @Nullable Pattern textSearchPattern) {
		updateCheckboxes();
		updateSearchHighlighting(textSearchPattern, YesNoMaybe.MAYBE);
	}

	/**
	 * Only called by LabeledLabels.getLabel()
	 */
	public void updateOnCount() {
		final int onCount = facet.getOnCount();
		if (onCount != displayedOnCount) {
			maybeSetText(art().computeText(facet.getMarkupElement(), numW, perspectiveViz.nameW(), false,
					showChildIndicator, showCheckBox, true, this, onCount));
			// if (maybeIncfGGG()) {
			// System.out.println("eee " + this + " " + displayedOnCount + " ⇒
			// " + onCount);
			// }
			displayedOnCount = onCount;
		}
	}

	/**
	 * Only called by LabeledLabels.getLabel()
	 */
	public void setColorAndPTextOffset(final double midX) {
		updateHighlighting();
		setPTextOffset(midX);
	}

	public void setPTextOffset(final double midX) {
		final TagWall tagWall = perspectiveViz.tagWall();
		setOffset(Math.rint(midX + tagWall.rankLabelsXoffset()), Math.rint(tagWall.rankLabelsYoffset()));
	}

	@Override
	public boolean isUnderMouse(final boolean state, final PInputEvent e) {
		return isUnderMouse(getWidth(), state, e);
	}

	@Override
	public boolean brush(final boolean state, final PInputEvent e) {
		assert facet != null;
		assert isUnderMouse(getWidth(), state, e);
		if (state != art().isHighlighted(facet)) {
			super.brush(state, e);
		}
		return false;
	}

}
