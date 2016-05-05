package edu.cmu.cs.bungee.client.viz.markup;

import static edu.cmu.cs.bungee.javaExtensions.Util.printModifiersEx;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;

public class BungeeClickHandler<T extends KnowsBungee> extends MyInputEventHandler<T> {

	private static final @NonNull BungeeClickHandler<KnowsBungee> BUNGEE_CLICK_HANDLER = new BungeeClickHandler<>(
			KnowsBungee.class);

	protected BungeeClickHandler(final @NonNull Class<?> _nodeType) {
		super(_nodeType);
	}

	public static @NonNull BungeeClickHandler<KnowsBungee> getBungeeClickHandler() {
		return BUNGEE_CLICK_HANDLER;
	}

	@Override
	public boolean shiftKeysChanged(final @NonNull KnowsBungee node, final @NonNull PInputEvent e) {
		return enter(node, e);
	}

	@Override
	public boolean enter(final @NonNull KnowsBungee node, final @NonNull PInputEvent e) {
		// System.out.println("BungeeClickHandler.enter " + node + "
		// getModifiersEx=" + e.getModifiersEx()
		// + " isUnderMouse=" + node.isUnderMouse(true, e));

		boolean result = isMouseEventApplicable(node, true, e);
		if (result && !node.brush(true, e)) {
			result = UserAction.setClickDesc(e, node.getModifiersEx(e), node.art());
		}
		return result;
	}

	@Override
	public boolean exit(final @NonNull KnowsBungee node, final @NonNull PInputEvent e) {
		// System.out.println("BungeeClickHandler.exit " + node);
		// userAction = null;

		final boolean result = isMouseEventApplicable(node, false, e);
		if (result) {
			node.brush(false, e);
			node.art().resetClickDesc();
		}
		return result;
	}

	@Override
	public boolean click(final @NonNull KnowsBungee node, final @NonNull PInputEvent e) {
		// System.out.println("BungeeClickHandler.click " + node);

		boolean result = false;
		if (isMouseEventApplicable(node, true, e)) {
			final int modifiers = node.getModifiersEx(e);
			final UserAction userAction = UserAction.getAction(e, node.art(), modifiers);
			if (userAction != null) {
				node.printUserAction(modifiers);
				result = userAction.performWhenQueryValid();
			} else {
				System.err.println("BungeeClickHandler.click: No userAction found " + e.getPickedNode()
						+ printModifiersEx(modifiers));
			}
		}
		return result;
	}

	protected static boolean isMouseEventApplicable(final @NonNull KnowsBungee node, final boolean state,
			final @NonNull PInputEvent e) {
		return node.art().isReady() && node.isUnderMouse(state, e);
	}

	// @Override
	// public void mayHideTransients(final DraggableFacetNode node) {
	// node.mayHideTransients((PNode) node);
	// }

}
