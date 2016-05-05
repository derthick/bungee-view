package edu.cmu.cs.bungee.client.viz.markup;

import java.awt.Color;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * Search strings in the query description, enabling click to remove.
 */
public class MarkupSearchText extends BungeeAPText {

	/**
	 * constrainWidth defaults to true; constrainHeight and isWrap default to
	 * false. justification defaults to LEFT_ALIGNMENT.
	 */
	MarkupSearchText(final MarkupElement treeObject, final @NonNull Bungee _art) {
		super(treeObject.getName(), _art, null);
		addInputEventListener(BungeeClickHandler.getBungeeClickHandler());
	}

	@Override
	public boolean brush(final boolean state, @SuppressWarnings("unused") final PInputEvent e) {
		final Color color = state ? BungeeConstants.TEXT_FG_COLOR : BungeeConstants.HEADER_FG_COLOR;
		setTextPaint(color);
		return false;
	}

	@Override
	public void printUserAction(final int modifiers) {
		final String searchText = getText();
		assert searchText != null;
		art.printUserAction(ReplayLocation.BUTTON, searchText, modifiers);
	}

}
