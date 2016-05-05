package edu.cmu.cs.bungee.client.viz.markup;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.MouseDoc;
import edu.umd.cs.piccolo.event.PInputEvent;

public interface KnowsBungee extends MouseDoc {

	@NonNull
	Bungee art();

	/**
	 * Called only by BungeeClickHandler.enter/exit.
	 *
	 * Initiate brushing if applicable (currently only applicable to
	 * FacetNodes), or else just change appearance locally.
	 *
	 * @return whether mouse doc was set (and therefore no need to call
	 *         UserAction.clickDesc)
	 */
	boolean brush(boolean state, final @NonNull PInputEvent e);

	boolean isUnderMouse(final boolean state, final @NonNull PInputEvent e);

	void printUserAction(final int modifiers);

	// /**
	// * // * The user did something, indicating that temporary messages or
	// * whatever // * should be removed. // * // * @param node // * the PNode
	// * gestured on //
	// */
	// void mayHideTransients(PNode node);

	int getModifiersEx(final @NonNull PInputEvent e);

}
