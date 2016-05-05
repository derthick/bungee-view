package edu.cmu.cs.bungee.client.viz.markup;

import java.awt.Font;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * APText with protected final Bungee art and NO-OP methods for
 * BungeeClickHandler;
 */
public abstract class BungeeAPText extends APText implements KnowsBungee {
	protected final @NonNull Bungee art;

	/**
	 * constrainWidth defaults to true; constrainHeight and isWrap default to
	 * false. justification defaults to LEFT_ALIGNMENT.
	 */
	public BungeeAPText(final @Nullable String s, final @NonNull Bungee _art, final @Nullable Font font) {
		super(font == null ? _art.getCurrentFont() : font);
		art = _art;

		setConstrainHeightToTextHeight(false);
		setConstrainWidthToTextWidth(true);
		setWrap(false);
		setHeight(art.lineH());

		// if (s == null) {
		// s = art.computeText(treeObject, -1, _nameW, false, false, false,
		// false, null, 1);
		// }
		if (s != null) {
			maybeSetText(s);
		}
	}

	public void setUnderline(final @NonNull ReplayLocation replayLocation) {
		setUnderline(UserAction.isUnderline(art, this, Util.nonNull(replayLocation.perspectiveMarkupElementLocation())),
				YesNoMaybe.MAYBE);
	}

	@Override
	public @NonNull Bungee art() {
		return art;
	}

	@Override
	public void setMouseDoc(final @Nullable String doc) {
		art().setClickDesc(doc);
	}

	public void queryValidRedraw(final @Nullable Pattern textSearchPattern) {
		updateSearchHighlighting(textSearchPattern, YesNoMaybe.MAYBE);
	}

	@Override
	public boolean maybeSetText(final @Nullable String _text) {
		final boolean result = super.maybeSetText(_text, YesNoMaybe.NO);
		if (result) {
			updateSearchHighlighting(art.getQuery().textSearchPattern(), YesNoMaybe.YES);
		}
		return result;
	}

	protected void updateSearchHighlighting(final @NonNull Query query) {
		updateSearchHighlighting(query.textSearchPattern(), YesNoMaybe.MAYBE);
	}

	@Override
	public int getModifiersEx(final @NonNull PInputEvent e) {
		return art.getModifiersEx(e);
	}

	@SuppressWarnings("unused")
	@Override
	public boolean isUnderMouse(final boolean state, final PInputEvent e) {
		// return isUnderMouse(getWidth(), state, e);
		return true;
	}

}
