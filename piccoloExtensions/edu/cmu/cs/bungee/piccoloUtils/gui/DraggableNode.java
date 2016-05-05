package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.geom.Point2D;

import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.util.PDimension;

public interface DraggableNode extends MouseDoc {

	void enter();

	void exit();

	void startDrag(Point2D positionRelativeTo);

	void drag(PDimension delta);

	void endDrag(PInputEvent e);

}
