package edu.cmu.cs.bungee.client.viz.bungeeCore;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.tagWall.LetterLabeled;
import edu.cmu.cs.bungee.piccoloUtils.gui.Menu;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;

public final class KeyEventHandler extends MyInputEventHandler<Bungee> {

	private final Bungee bungee;

	KeyEventHandler(final Bungee _bungee) {
		super(Bungee.class);
		bungee = _bungee;
	}

	@Override
	// Handle keys without Unicode equivalents here, and normal keys in
	// handleKey
	public boolean keyPress(final int keyCode, final PInputEvent e) {
		final int modifiers = e.getModifiersEx();

		// System.out.println("KeyEventHandler.keyPress keyCode=" + keyCode
		// + (MyInputEventHandler.isShiftKey(keyCode) ? " (shift key)" : "")
		// + (MyInputEventHandler.isArrowKey(keyCode) ? " (arrow key)" : ""));

		if (MyInputEventHandler.isShiftKey(keyCode) || !bungee.isReady()) {
			return false;
		} else if (isArrowKeyOrCtrlA(keyCode, modifiers)) {
			return bungee.handleArrow(keyCode, modifiers);
		} else {
			return handleKey(e);
		}
	}

	private static final char CONTROL_P = 16;
	private static final char ESCAPE = 27;

	private boolean handleKey(final PInputEvent e) {
		char keyChar = e.getKeyChar();
		switch (keyChar) {
		case ESCAPE:
			bungee.mayHideTransients();
			break;
		case CONTROL_P:
			bungee.printUserAction(ReplayLocation.TOGGLE_POPUPS, 0, 0);
			bungee.togglePopups();
			break;
		// case CONTROL_S:
		// bungee.stopInformediaServer();
		// break;
		// case CONTROL_T:
		// bungee.setTip("Tetrad is commented out of this version");
		// // TagWall.convertgraphToTetrad();
		// break;
		// case CONTROL_X:
		// bungee.testInformediaExport();
		// break;
		// case CONTROL_Z:
		// bungee.testInformediaImport();
		// break;

		default:
			final Menu editMenu = bungee.editMenu();
			if (editMenu != null) {
				final int menuIndex = Character.digit(keyChar, editMenu.nButtons() + 1) - 1;
				editMenu.choose(menuIndex);
			} else if (isZoomChar(keyChar)) {
				keyChar = LetterLabeled.convertSuffix(keyChar);
				bungee.printUserAction(ReplayLocation.ZOOM, 0, keyChar);
				return bungee.getTagWall().zoomTo(keyChar, null);
			} else {
				return false;
			}
			break;
		}
		return true;
	}

	public static boolean isZoomChar(final char c) {
		return !Character.isISOControl(c) || c == '\b';
	}

	// @Override
	// public void mayHideTransients(
	// @SuppressWarnings("unused") final Bungee ignore) {
	// // System.out.println("KeyEventHandler.mayHideTransients");
	// bungee.mayHideTransients();
	// }

}
