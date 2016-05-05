package edu.cmu.cs.bungee.client.viz.tagWall;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants.Significance;
import edu.cmu.cs.bungee.client.viz.markup.BungeeClickHandler;
import edu.cmu.cs.bungee.client.viz.markup.FacetNode;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.Alignment;
import edu.cmu.cs.bungee.piccoloUtils.gui.Arrow;
import edu.umd.cs.piccolo.event.PInputEvent;

final class MedianArrow extends Arrow implements FacetNode {

	private @NonNull Significance significance = Significance.UNASSOCIATED;
	private boolean isBrushed = true;
	private final @NonNull PerspectiveViz perspectiveViz;

	MedianArrow(final @NonNull PerspectiveViz _perspectiveViz, final int size, final int length) {
		super(null, size, length, 1);
		setVisible(ArrowPart.LEFT_TAIL, true);
		perspectiveViz = _perspectiveViz;
		addInputEventListener(BungeeClickHandler.getBungeeClickHandler());
		redraw();
	}

	// Hard to eliminate parameter scaleY, because getGlobalScale() doesn't
	// recognize front's transform as scaling.
	public void layout(final double conditionalX, final double unconditionalX, final double scaleY) {
		// System.out.println("MedianArrow.layout " + this + " logicalWidth=" +
		// perspectiveViz.logicalWidth + " leftEdge="
		// + perspectiveViz.leftEdge + " " + unconditionalX + " => " +
		// conditionalX);
		final int length = (int) (conditionalX - unconditionalX);
		setLengthAndDirection(length);
		updateColor(getFacet().medianTestSignificance());
		setTransform(UtilMath.scaleNtranslate(1.0, scaleY, unconditionalX, 1.0));
		Alignment.ensureGlobalBounds(this);
	}

	void updateColor(final @NonNull Significance _significance) {
		if (_significance != significance) {
			significance = _significance;
			redraw();
		}
	}

	@SuppressWarnings("unused")
	@Override
	public boolean updateHighlighting(final int queryVersion, final YesNoMaybe isRerender) {
		assert false;
		// rely on highlight
		return false;
	}

	@Override
	public boolean brush(final boolean state, @SuppressWarnings("unused") final PInputEvent e) {
		if (state != isBrushed) {
			isBrushed = state;
			redraw();

			art().showMedianArrowDesc(state ? getFacet() : null);
		}
		return true;
	}

	private void redraw() {
		setStrokePaint(Bungee.significanceColor(significance, isBrushed));
	}

	@Override
	public @NonNull Bungee art() {
		return perspectiveViz.art();
	}

	@Override
	public boolean isUnderMouse(@SuppressWarnings("unused") final boolean state,
			@SuppressWarnings("unused") final PInputEvent e) {
		return true;
	}

	@SuppressWarnings("unused")
	@Override
	public void printUserAction(final int modifiers) {
		assert false;
	}

	@Override
	public void setMouseDoc(final @Nullable String doc) {
		art().setClickDesc(doc);
	}

	@Override
	public int getModifiersEx(final @NonNull PInputEvent e) {
		return art().getModifiersEx(e);
	}

	@Override
	public @NonNull Perspective getFacet() {
		return perspectiveViz.p;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, perspectiveViz);
	}

}