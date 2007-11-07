/* 

 Created on Dec 9, 2005

 The Bungee View applet lets you search, browse, and data-mine an image collection.  
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at 
 mad@cs.cmu.edu, 
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.event.EventListenerList;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PBasicInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.event.PInputEventListener;
import edu.umd.cs.piccolo.util.PPickPath;

/**
 * Extends PBasicInputEventHandler by automatically looking up the pick path for
 * a node of the given type, and for each gesture calling a new version of the
 * function on that node.
 * 
 * @author mad
 * 
 */
public class MyInputEventHandler extends PBasicInputEventHandler {

	final Class nodeType;
	private int SHIFT_KEYS_CHANGED = MouseEvent.MOUSE_LAST + 1;

	/**
	 * Functions should return true iff they handle the event.
	 * 
	 * @param _nodeType
	 *            Search up the PNode hierarchy for a _nodeType
	 */
	public MyInputEventHandler(Class _nodeType) {
		nodeType = _nodeType;
	}

	protected boolean click(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean enter(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean exit(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean press(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean release(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean drag(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean moved(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean shiftKeysChanged(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		return false;
	}

	protected boolean keyPress(int key) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(key);
		return false;
	}

	protected boolean keyRelease(int key) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(key);
		return false;
	}

	protected boolean click(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean enter(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean exit(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean press(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean release(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean drag(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean moved(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean shiftKeysChanged(PNode node, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean keyPress(int key, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(key);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	protected boolean keyRelease(int key, PInputEvent e) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(key);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		return false;
	}

	private void wrongType(PInputEvent e, String eventType) {
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(e);
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(eventType);
		// gui.Util.printDescendents(e.getPickedNode());
		//		
		// Exception ex = new Exception(
		// "MyInputEventHandler " + eventType + " a PNode that's not a "
		// + nodeType + ": " + e.getPickedNode());
		//		
		// // Don't need both of these
		// // Util.print(ex);
		// ex.printStackTrace();
		// // throw(ex);
	}

	protected void mayHideTransients(PNode node) {
		// Override this
		assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
	}

	public void mouseClicked(PInputEvent e) {
		PNode node = findNodeType(e);
		if (node != null) {
			mayHideTransients(node);
			e.setHandled(click(node) || click(node, e));
		}
	}

	public void mouseEntered(PInputEvent e) {
		PNode node = findNodeType(e);
		if (node != null)
			e.setHandled(enter(node) || enter(node, e));
	}

	public void mouseExited(PInputEvent e) {
		PNode node = findNodeType(e);
		if (node != null)
			e.setHandled(exit(node) || exit(node, e));
	}

	public void mousePressed(PInputEvent e) {
		PNode node = findNodeType(e);
		if (node != null) {
			mayHideTransients(node);
			e.setHandled(press(node) || press(node, e));
		}
	}

	public void mouseReleased(PInputEvent e) {
		PNode node = findNodeType(e);
		if (node != null)
			e.setHandled(release(node) || release(node, e));
	}

	public void mouseDragged(PInputEvent e) {
		PNode node = findNodeType(e);
		if (node != null)
			e.setHandled(drag(node) || drag(node, e));
	}

	public void mouseMoved(PInputEvent e) {
		PNode node = findNodeType(e);
		if (node != null)
			e.setHandled(moved(node) || moved(node, e));
	}

	private PNode findNodeType(PInputEvent e) {
		prevModifiers = e.getModifiersEx();
		PNode node = null;
		if (!e.isHandled()) {
			node = findNodeType(e.getPickedNode());
			if (node == null) {
				wrongType(e, e.getSourceSwingEvent().toString());
			}
		}
		return node;
	}

	private PNode findNodeType(PNode node) {
		while (node != null && !nodeType.isInstance(node))
			node = node.getParent();
		return node;
	}

	/**
	 * Mask for shift and control
	 */
	public static final int[] shiftKeys = { java.awt.event.KeyEvent.VK_ALT,
			java.awt.event.KeyEvent.VK_CONTROL,
			java.awt.event.KeyEvent.VK_SHIFT };

	// Note that Windows catches the Alt key and the window loses focus!!!
	public void keyPressed(PInputEvent e) {
		mayHideTransients(null);
		int key = e.getKeyCode();
		if (keyPress(key) || keyPress(key, e)) {
			e.setHandled(true);
		} else {
			maybeShiftKeysChanged(e);
		}
	}

	public void keyReleased(PInputEvent e) {
//		mayHideTransients(null);
		int key = e.getKeyCode();
		if (keyRelease(key) || keyRelease(key, e)) {
			e.setHandled(true);
		} else {
			maybeShiftKeysChanged(e);
		}
	}
	
	private int prevModifiers;

	/**
	 * Transform a shift- or control-key event to a MouseEvent on the current
	 * mouseOver. I.e. treat control keys like additional mouse buttons.
	 * 
	 * @param e
	 */
	private void maybeShiftKeysChanged(PInputEvent e) {
		if (Util.isMember(shiftKeys, e.getKeyCode())) {
			PPickPath path = e.getInputManager().getMouseOver();
			PInputEvent ePrime = new PInputEvent(e.getInputManager(),
					new MouseEvent((Component) e.getSourceSwingEvent()
							.getSource(), SHIFT_KEYS_CHANGED, e.getWhen(), e
							.getModifiersEx(), -1, -1, 0, false,
							MouseEvent.NOBUTTON));
			try {
				path.processEvent(ePrime, SHIFT_KEYS_CHANGED);
			} catch (Throwable ignore) {
				// ePrime might be handled by a listener that isn't a
				// MyInputEventHandler,
				// in which case it will barf on SHIFT_KEYS_CHANGED
			}
			// Make sure our super doesn't get an unhandled event type it
			// doesn't recognize.
			e.setHandled(true);
		}
	}

	public void processEvent(PInputEvent e, int type) {
		if (type == SHIFT_KEYS_CHANGED) {
			if (e.getModifiersEx() != prevModifiers) {
			PNode node = findNodeType(e);
			if (node != null)
				e.setHandled(shiftKeysChanged(node)
						|| shiftKeysChanged(node, e));}
		} else {
			super.processEvent(e, type);
		}
	}
}
