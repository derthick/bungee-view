package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilColor;

public class CheckBox extends IconButton {

	protected final boolean isRequire;

	private static final @NonNull Color DARK_GREEN = UtilColor.getHSBColor(0.33f, 1.0f, 0.8f);

	private static final @NonNull float[] REQUIRE_XS = { 22.0f, 0.0f, 58.0f, 93.0f, 106.0f, 138.0f, 417.0f, 450.0f,
			456.0f, 112.0f, 69.0f, 22.0f };
	private static final @NonNull float[] REQUIRE_YS = { 376.0f, 193.0f, 133.0f, 132.0f, 150.0f, 261.0f, 0.0f, 0.0f,
			73.0f, 415.0f, 415.0f, 376.0f };
	private static final @NonNull float[] EXCLUDE_XS = { 0.0f, 100.0f, 27.0f, 90.0f, 178.0f, 316.0f, 370.0f, 232.0f,
			321.0f, 228.0f, 153.0f, 60.0f, 0.0f };
	private static final @NonNull float[] EXCLUDE_YS = { 355.0f, 229.0f, 72.0f, 24.0f, 147.0f, 0.0f, 61.0f, 220.0f,
			334.0f, 411.0f, 321.0f, 441.0f, 358.0f };

	/**
	 * Return a green/red FG, light green/light red BG CheckBox.
	 */
	public CheckBox(final boolean _isRequire, final double x, final double y, // NO_UCD
																				// (unused
																				// code)
			final double size, final @NonNull String documentation) {
		this(_isRequire, x, y, size, documentation, defaultColor(_isRequire),
				UtilColor.lighten(defaultColor(_isRequire), 5.0f));
	}

	/**
	 * Return a √/x square (sides are size long) CheckBox whose state is false
	 * (mark is invisible).
	 */
	public CheckBox(final boolean _isRequire, final double x, final double y, final double size,
			final @Nullable String documentation, final @NonNull Color fg, final @NonNull Color bg) {
		super(x, y, size, size, "Should never be disabled", documentation, false, fg, bg);
		isRequire = _isRequire;
		setState(false);
		child.setStroke(null);

		final float[] Xs = isRequire ? REQUIRE_XS : EXCLUDE_XS;
		final float[] Ys = isRequire ? REQUIRE_YS : EXCLUDE_YS;
		child.setPathToPolyline(Xs, Ys);
		child.setScale(size / 20.0);
		positionChild();
	}

	private static @NonNull Color defaultColor(final boolean _isRequire) {
		return _isRequire ? DARK_GREEN : UtilColor.RED;
	}

	private boolean getState() {
		return child.getVisible();
	}

	@Override
	public boolean setState(final boolean _state) {
		// System.out.println("CheckBox.setState " + state);
		// final boolean result = super.setState(_state);
		final boolean result = _state != getState();
		if (result) {
			child.setVisible(_state);
			// state = _state;
		}
		return result;
	}

	@Override
	public void doPick() {
		setState(!getState());
	}

	public boolean isRequire() {
		return isRequire;
	}
}
