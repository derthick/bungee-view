package edu.cmu.cs.bungee.javaExtensions;

import java.awt.Color;

import org.eclipse.jdt.annotation.NonNull;

public class UtilColor {

	public static final @NonNull Color WHITE = Util.nonNull(Color.WHITE);
	public static final @NonNull Color LIGHT_GRAY = Util.nonNull(Color.LIGHT_GRAY);
	public static final @NonNull Color DARK_GRAY = Util.nonNull(Color.DARK_GRAY);
	public static final @NonNull Color BLACK = Util.nonNull(Color.BLACK);
	public static final @NonNull Color RED = Util.nonNull(Color.RED);
	public static final @NonNull Color GREEN = Util.nonNull(Color.GREEN);
	public static final @NonNull Color BLUE = Util.nonNull(Color.BLUE);
	public static final @NonNull Color YELLOW = Util.nonNull(Color.YELLOW);
	public static final @NonNull Color ORANGE = Util.nonNull(Color.ORANGE);
	public static final @NonNull Color CYAN = Util.nonNull(Color.CYAN);
	public static final @NonNull Color MAGENTA = Util.nonNull(Color.MAGENTA);

	private static @NonNull float[] getHSBcomponents(final @NonNull Color color) {
		final float[] result = new float[3];
		Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), result);
		return result;
	}

	private static final float COLOR_COMPONENT_CHANGE_FACTOR = 2.0f;

	// TODO Remove unused code found by UCDetector
	// /**
	// * @param color
	// * @return Increases brightness. If factor < 1 it will get dimmer.
	// */
	// public static Color fade(final Color color) {
	// return brighten(color, 1.0f / COLOR_COMPONENT_CHANGE_FACTOR);
	// }

	// TODO Remove unused code found by UCDetector
	// /**
	// * @param color
	// * @return Increases brightness. If factor < 1 it will get dimmer.
	// */
	// public static Color brighten(final Color color) {
	// return brighten(color, COLOR_COMPONENT_CHANGE_FACTOR);
	// }

	// TODO Remove unused code found by UCDetector
	// public static @NonNull Color randomColor() {
	// return getHSBColor((float) Math.random(), (float)
	// Math.random(), (float) Math.random());
	// }

	public static @NonNull Color getHSBColor(final float h, final float s, final float b) {
		final Color result = Color.getHSBColor(h, s, b);
		assert result != null;
		return result;
	}

	public static @NonNull Color brighter(final @NonNull Color color) {
		final Color result = color.brighter();
		assert result != null;
		return result;
	}

	/**
	 * @param color
	 * @param factor
	 * @return Multiplies brightness by factor. If factor < 1 it will get
	 *         dimmer.
	 */
	public static @NonNull Color brighten(final @NonNull Color color, float factor) {
		if (factor <= 0.0) {
			factor = COLOR_COMPONENT_CHANGE_FACTOR;
		}
		final float[] hsb = getHSBcomponents(color);
		return getHSBColor(hsb[0], hsb[1], Math.min(1.0f, hsb[2] * factor));
	}

	/**
	 * @param color
	 * @return Increases brightness, and decreases saturation. If factor < 1 it
	 *         will get darker.
	 */
	public static @NonNull Color lighten(final @NonNull Color color) {
		return lighten(color, COLOR_COMPONENT_CHANGE_FACTOR);
	}

	/**
	 * @param color
	 * @param factor
	 * @return Multiplies brightness by factor, and divides saturation by
	 *         factor. If factor < 1 it will get darker.
	 */
	public static @NonNull Color lighten(final @NonNull Color color, float factor) {
		if (factor <= 0.0) {
			factor = COLOR_COMPONENT_CHANGE_FACTOR;
		}
		final float[] hsb = getHSBcomponents(color);
		return Util
				.nonNull(Color.getHSBColor(hsb[0], Math.min(1.0f, hsb[1] / factor), Math.min(1.0f, hsb[2] * factor)));
	}

	public static @NonNull Color desaturate(final @NonNull Color color, float factor) {
		if (factor <= 0.0) {
			factor = COLOR_COMPONENT_CHANGE_FACTOR;
		}
		final float[] hsb = getHSBcomponents(color);
		return getHSBColor(hsb[0], hsb[1] / factor, hsb[2]);
	}

	// TODO Remove unused code found by UCDetector
	// public static Color getHSBColor(final float h, final float s,
	// final float b, final float alpha) {
	// final Color c = Color.getHSBColor(h, s, b);
	// return new Color(c.getRed(), c.getGreen(), c.getBlue(),
	// (int) (alpha * 255 + 0.5));
	// }
}
