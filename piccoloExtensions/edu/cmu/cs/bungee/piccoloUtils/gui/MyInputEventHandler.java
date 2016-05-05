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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.Serializable;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PBasicInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.util.PPickPath;

/**
 * Extends PBasicInputEventHandler by automatically looking up the pick path for
 * a node of the given type, and for each gesture calling a new version of the
 * function on that node.
 *
 * @author mad
 *
 */

public class MyInputEventHandler<T> extends PBasicInputEventHandler implements Serializable {
	private final Class<?> nodeType;
	private static final int SHIFT_KEYS_CHANGED = MouseEvent.MOUSE_LAST + 1;

	javax.swing.Timer timer;
	@Nullable
	T lastPressedNode = null;

	/**
	 * Functions should return true iff they handle the event.
	 *
	 * @param _nodeType
	 *            Search up the PNode hierarchy for a _nodeType
	 */
	public MyInputEventHandler(final Class<?> _nodeType) {
		nodeType = _nodeType;
	}

	void initTimer(final int delayMS, final int repeatMS) {
		if (timer != null) {
			timer.stop();
		}
		final ActionListener taskPerformer = new ActionListener() {
			@Override
			public void actionPerformed(@SuppressWarnings("unused") final ActionEvent evt) {
				assert lastPressedNode != null;
				click(lastPressedNode);
			}
		};
		timer = new javax.swing.Timer(delayMS, taskPerformer);
		timer.setDelay(repeatMS);
	}

	public static boolean isArrowKeyOrCtrlA(final int keyCode, final int modifiers) {
		return ArrayUtils.contains(getARROW_KEYS(), keyCode) || isControlA(keyCode, modifiers);
	}

	/**
	 * Mask for up, down, left, right, home, end
	 */
	private static final int[] ARROW_KEYS = { java.awt.event.KeyEvent.VK_KP_DOWN, java.awt.event.KeyEvent.VK_KP_UP,
			java.awt.event.KeyEvent.VK_KP_LEFT, java.awt.event.KeyEvent.VK_KP_RIGHT, java.awt.event.KeyEvent.VK_DOWN,
			java.awt.event.KeyEvent.VK_UP, java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_RIGHT,
			java.awt.event.KeyEvent.VK_END, java.awt.event.KeyEvent.VK_HOME };

	public static boolean isControlA(final int keyCode, final int modifiers) {
		final boolean isControlA = keyCode == java.awt.event.KeyEvent.VK_A && Util.isControlDown(modifiers);
		return isControlA;
	}

	public static boolean isShiftKey(final int keyCode) {
		return ArrayUtils.contains(SHIFT_KEYS, keyCode);
	}

	/**
	 * alt, shift, and control
	 */
	static final int[] SHIFT_KEYS = shiftKeys();

	private static int[] shiftKeys() {
		final int[] shifts = { java.awt.event.KeyEvent.VK_ALT, java.awt.event.KeyEvent.VK_CONTROL,
				java.awt.event.KeyEvent.VK_SHIFT };
		// Arrays.sort(shifts);
		return shifts;
	}

	// These have to be public so that they can be overridden by handlers that
	// extend this class, but are in other packages.

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean click(final @NonNull T node) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean enter(final @NonNull T node) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean exit(final @NonNull T node) {
		// Override this
		return false;
	}

	// /**
	// * @return whether event was handled
	// */
	// @SuppressWarnings({ "unused" })
	// public boolean press(final T node) {
	// // Override this
	// return false;
	// }
	//
	// /**
	// * @return whether event was handled
	// */
	// @SuppressWarnings({ "unused" })
	// public boolean release(final T node) {
	// // Override this
	// return false;
	// }

	boolean press(final @NonNull T node) {
		final boolean result = timer != null;
		if (result) {
			lastPressedNode = node;
			timer.restart();
		}
		// System.out.println("MyInputEventHandler.press isLastPressedNode="
		// + (lastPressedNode != null) + " result=" + result);
		return false;
	}

	public boolean release(@SuppressWarnings("unused") final @NonNull T node) {
		final boolean result = timer != null;
		if (result) {
			lastPressedNode = null;
			timer.stop();
		}
		// System.out.println("MyInputEventHandler.release isLastPressedNode="
		// + (lastPressedNode != null) + " result=" + result);
		return false;
	}

	@SuppressWarnings({ "unused" })
	private boolean drag(final @NonNull T node) {
		// Override this
		return false;
	}

	@SuppressWarnings({ "unused" })
	private boolean moved(final @NonNull T node) {
		// Override this
		return false;
	}

	@SuppressWarnings({ "unused" })
	public boolean shiftKeysChanged(final @NonNull T node) { // NO_UCD (use
																// default)
		// Override this
		return false;
	}

	@SuppressWarnings({ "static-method", "unused" })
	private boolean keyPress(final int keyCode) {
		// Override this
		return false;
	}

	@SuppressWarnings({ "static-method", "unused" })
	private boolean keyRelease(final int keyCode) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean click(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean enter(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean exit(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean press(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean release(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean drag(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean moved(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "unused" })
	public boolean shiftKeysChanged(final @NonNull T node, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	/**
	 * @return whether event was handled
	 */
	@SuppressWarnings({ "static-method", "unused" })
	public boolean keyPress(final int keyCode, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	@SuppressWarnings({ "static-method", "unused" })
	private boolean keyRelease(final int keyCode, final @NonNull PInputEvent e) {
		// Override this
		return false;
	}

	@SuppressWarnings({ "unused" })
	private void wrongType(final @NonNull PInputEvent e, final String eventType) {
		// gui.Util.printDescendents(e.getPickedNode());
		//
		// Exception ex = new Exception(
		// "MyInputEventHandler " + eventType + " a PNode that's not a "
		// + nodeType + ": " + e.getPickedNode());
		//
		// // Don't need both of these
		// // System.out.println(ex);
		// ex.printStackTrace();
		// // throw(ex);
	}

	// /**
	// * Nothing calls this!
	// */
	// @SuppressWarnings({ "unused" })
	// public void mayHideTransients(final T node) {
	// // Override this
	// }

	/*
	 * see mousePressed
	 */
	@Override
	@SuppressWarnings({ "unused" })
	public final void mouseClicked(final @Nullable PInputEvent e) {
		// Toolkit.getDefaultToolkit().beep();
		// T node = findNodeType(e);
		// if (false && node != null) {
		// mayHideTransients(node);
		// e.setHandled(click(node) || click(node, e));
		// }
	}

	@Override
	public void mouseEntered(final PInputEvent e) {
		@SuppressWarnings("null")
		final @Nullable T node = findNodeType(e);
		if (node != null) {
			e.setHandled(enter(node) || enter(node, e));
		}
	}

	@Override
	public void mouseExited(final PInputEvent e) {
		@SuppressWarnings("null")
		final @Nullable T node = findNodeType(e);
		if (node != null) {
			e.setHandled(exit(node) || exit(node, e));
		}
	}

	/*
	 * On Alex's computer, clicks are sometimes getting lost, but pressed
	 * doesn't. Therefore treat pressed as both. This means a click will act
	 * like 2 presses and a click, so don't do any actions on press, just set
	 * drag initial states.
	 *
	 * Note that modifiers may be different for press and click, at least for
	 * mouse button modifiers
	 */
	@Override
	public final void mousePressed(final PInputEvent e) {
		// System.out.println("mousePressed");
		@SuppressWarnings("null")
		final @Nullable T node = findNodeType(e);
		if (node != null/* &&e.getClickCount()==1 */) {
			// This was hiding PerspectiveList when you clicked on a RankLabel
			// to hide the PL, which would toggle it back to visible! Do it on
			// mouseReleased instead. That's no good either, as it hides PL as
			// soon as its shown.
			// mayHideTransients(node);
			e.setHandled(press(node) || press(node, e));
			e.setHandled(click(node) || click(node, e));
		}
	}

	@Override
	public void mouseReleased(final PInputEvent e) {
		@SuppressWarnings("null")
		final @Nullable T node = findNodeType(e);
		if (node != null) {
			e.setHandled(release(node) || release(node, e));
		}
	}

	@Override
	public void mouseDragged(final PInputEvent e) {
		@SuppressWarnings("null")
		final @Nullable T node = findNodeType(e);
		if (node != null) {
			e.setHandled(drag(node) || drag(node, e));
		}
	}

	@Override
	public void mouseMoved(final PInputEvent e) {
		@SuppressWarnings("null")
		final @Nullable T node = findNodeType(e);
		if (node != null) {
			e.setHandled(moved(node) || moved(node, e));
		}
	}

	private @Nullable T findNodeType(final @NonNull PInputEvent e) {
		prevModifiers = e.getModifiersEx();
		@Nullable
		T node = null;
		if (!e.isHandled()) {
			node = findNodeType(e.getPickedNode());
			if (node == null) {
				wrongType(e, e.getSourceSwingEvent().toString());
			}
		}
		return node;
	}

	@SuppressWarnings("unchecked")
	private T findNodeType(PNode node) {
		while (node != null && !nodeType.isInstance(node)) {
			// System.out.println(nodeType + " " + node);
			node = node.getParent();
		}
		// System.out.println(nodeType + " => " + node);
		return (T) node;
	}

	@Override
	public void keyPressed(final PInputEvent e) {
		// mayHideTransients(null);
		final int keyCode = e.getKeyCode();
		// System.out.println("keyPressed " + e.getKeyCode() + " " + keyCode);
		if (keyPress(keyCode) || keyPress(keyCode, e)) {
			e.setHandled(true);
		} else {
			maybeShiftKeysChanged(e);
		}
	}

	@Override
	public void keyReleased(final PInputEvent e) {
		// mayHideTransients(null);
		final int keyCode = e.getKeyCode();
		if (keyRelease(keyCode) || keyRelease(keyCode, e)) {
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
	private static void maybeShiftKeysChanged(final @NonNull PInputEvent e) {
		if (ArrayUtils.contains(SHIFT_KEYS, e.getKeyCode())) {
			final PPickPath path = e.getInputManager().getMouseOver();
			final @NonNull PInputEvent ePrime = new PInputEvent(e.getInputManager(),
					new MouseEvent((Component) e.getSourceSwingEvent().getSource(), SHIFT_KEYS_CHANGED, e.getWhen(),
							e.getModifiersEx(), -1, -1, 0, false, MouseEvent.NOBUTTON));
			try {
				path.processEvent(ePrime, SHIFT_KEYS_CHANGED);
			} catch (final Throwable ignore) {
				// ePrime might be handled by a listener that isn't a
				// MyInputEventHandler,
				// in which case it will barf on SHIFT_KEYS_CHANGED
			}
			// Make sure our super doesn't get an unhandled event type it
			// doesn't recognize.
			e.setHandled(true);
		}
	}

	@Override
	public void processEvent(final PInputEvent e, final int type) {
		if (type == SHIFT_KEYS_CHANGED) {
			// System.out.println("SHIFT_KEYS_CHANGED");
			if (e.getModifiersEx() != prevModifiers) {
				final @Nullable T node = findNodeType(e);
				if (node != null) {
					e.setHandled(shiftKeysChanged(node) || shiftKeysChanged(node, e));
				}
			}
		} else {
			super.processEvent(e, type);
		}
	}

	public static int[] getARROW_KEYS() {
		return ARROW_KEYS;
	}
}
