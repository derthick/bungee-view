package edu.cmu.cs.bungee.piccoloUtils.gui;

import org.eclipse.jdt.annotation.NonNull;

import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PDragEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;

public class ClickDragHandler extends PDragEventHandler {

	private static final @NonNull ClickDragHandler CLICK_DRAG_HANDLER = new ClickDragHandler();

	public static @NonNull ClickDragHandler getClickDragHandler() {
		return CLICK_DRAG_HANDLER;
	}

	// TODO Remove unused code found by UCDetector
	// public static boolean isAnyDragging() {
	// return CLICK_DRAG_HANDLER.isDragging();
	// }

	@Override
	protected void startDrag(final PInputEvent event) {
		super.startDrag(event);
		event.setHandled(true);

		final DraggableNode node = getNode();
		node.startDrag(event.getPositionRelativeTo((PNode) node));
	}

	@Override
	protected void endDrag(final PInputEvent event) {
		getNode().endDrag(event);
		event.setHandled(true);
		super.endDrag(event);
	}

	@Override
	public void drag(final PInputEvent event) {
		// No, we don't want to actually move the node here, but to let
		// node.drag() do it.
		// super.drag(event);

		getNode().drag(event.getDelta());
		event.setHandled(true);
	}

	private @NonNull DraggableNode getNode() {
		return getNode(getDraggedNode());
	}

	private static @NonNull DraggableNode getNode(final PNode pickedNode) {
		final DraggableNode result = (DraggableNode) PiccoloUtil.findAncestorNodeType(pickedNode, DraggableNode.class);
		assert result != null;
		return result;
	}

	@Override
	public void mouseEntered(final PInputEvent event) {
		final DraggableNode node = getNode(event.getPickedNode());
		node.enter();
		event.setHandled(true);
	}

	@Override
	public void mouseExited(final PInputEvent event) {
		final DraggableNode node = getNode(event.getPickedNode());
		node.exit();
		event.setHandled(true);
	}

}
