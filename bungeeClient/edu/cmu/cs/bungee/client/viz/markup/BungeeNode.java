package edu.cmu.cs.bungee.client.viz.markup;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.MouseDoc;
import edu.umd.cs.piccolo.event.PInputEvent;

public interface BungeeNode extends MouseDoc {

	@NonNull
	Bungee art();

	/**
	 * Initiate brushing if applicable (currently only applicable to
	 * FacetNodes), or else just change appearance locally.
	 */
	void highlight(boolean state, final @NonNull PInputEvent e);

	boolean isUnderMouse(final boolean state, final @NonNull PInputEvent e);

	// /**
	// * Brush to this node (currently only applicable to FacetNodes).
	// *
	// * @return whether highlighting changed
	// */
	// boolean updateHighlighting();

	void printUserAction(final int modifiers);

	// /**
	// * // * The user did something, indicating that temporary messages or
	// * whatever // * should be removed. // * // * @param node // * the PNode
	// * gestured on //
	// */
	// void mayHideTransients(PNode node);

	int getModifiersEx(final @NonNull PInputEvent e);

}
