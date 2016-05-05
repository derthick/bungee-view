package edu.cmu.cs.bungee.client.viz.markup;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.tagWall.LetterLabeled;
import edu.cmu.cs.bungee.client.viz.tagWall.PerspectiveViz;
import edu.cmu.cs.bungee.client.viz.tagWall.TagWall;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * For LetterLabeled. Works around Piccolo rotated-text selection bugs.
 */
public final class LetterLabeledAPText extends BungeeAPText {

	public final @NonNull PerspectiveViz perspectiveViz;
	private final @NonNull LetterLabeled letterLabeled;

	/**
	 * constrainWidth defaults to true; constrainHeight and isWrap default to
	 * false. justification defaults to LEFT_ALIGNMENT.
	 */
	public LetterLabeledAPText(final @NonNull String s, final @NonNull PerspectiveViz _perspectiveViz,
			final @NonNull LetterLabeled _letterLabeled) {
		super(null, _perspectiveViz.art(), null);
		letterLabeled = _letterLabeled;
		perspectiveViz = _perspectiveViz;
		setTextPaint(BungeeConstants.TAGWALL_FG_COLOR.darker());
		setUnderline(true, YesNoMaybe.NO);
		setRotation(-RotatedFacetText.TEXT_ANGLE);
		addInputEventListener(BungeeClickHandler.getBungeeClickHandler());
		maybeSetText(s);
		assert getFont() == art().getCurrentFont() : getFont() + " should be " + art().getCurrentFont();
	}

	public void setPTextOffset(final double midX) {
		final TagWall tagWall = perspectiveViz.tagWall();
		setOffset(Math.rint(midX + tagWall.rankLettersXoffset()), Math.rint(tagWall.rankLettersYoffset()));
	}

	@Override
	public boolean isUnderMouse(final boolean state, final @NonNull PInputEvent e) {
		return isUnderMouse(getWidth(), state, e);
	}

	@Override
	public boolean brush(final boolean state, @SuppressWarnings("unused") final @NonNull PInputEvent e) {
		letterLabeled.brush(this, state);
		return false;
	}

	@Override
	public void printUserAction(@SuppressWarnings("unused") final int modifiers) {
		final String text = getText();
		final char zoomChar = text.charAt(text.length() - 1);
		art.printUserAction(ReplayLocation.ZOOM, perspectiveViz.p, zoomChar);
	}
}
