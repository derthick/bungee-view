package edu.cmu.cs.bungee.client.viz.grid;

import java.awt.Image;
import java.awt.image.BufferedImage;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.javaExtensions.UtilImage;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPImage;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * rawW/H is the size of the image in the database.
 *
 * getImageWidth/Height is the size of the cached image (<= rawW/H).
 *
 * getWidth/Height is the size to be drawn (<= getImageWidth/Height). Also
 * called actualW/H.
 *
 * bigEnough should use getImageWidth/Height.
 *
 * drawGrid should use getWidth/Height.
 */
public class GridImage extends LazyPImage implements GridElement {

	public final @NonNull GridElementWrapper wrapper;

	/**
	 * This is the size of the image in the database
	 */
	private final int rawW;
	private final int rawH;

	GridImage(final @NonNull GridElementWrapper _wrapper, final @Nullable Image _image, final int _rawW,
			final int _rawH) {
		super();
		wrapper = _wrapper;
		if (_image == null) {
			final BufferedImage missing = art().getMissingImage();
			rawW = missing.getWidth();
			rawH = missing.getHeight();
			setImage(missing);
		} else {
			assert _rawW > 0 && _rawH > 0 : _rawW + " x " + _rawH + " " + wrapper.getItem();
			rawW = _rawW;
			rawH = _rawH;
			setImage(_image);
		}
		assert getImage() != null : _image;
	}

	@Override
	public Item getItem() {
		return wrapper.getItem();
	}

	@Override
	public void setImage(final Image _image) {
		assert _image != null;
		super.setImage(_image);
		assert getImageHeight() <= rawH && getImageWidth() <= rawW : "GridImage " + getImageWidth() + "x"
				+ getImageHeight() + " " + rawW + "x" + rawH + " item=" + wrapper.getItem();
	}

	/**
	 * Only called by SelectedItem.
	 *
	 * Set parameters needed when displayed in the SelectedItem column.
	 *
	 * @return height of wrapper.
	 */
	public double setSelectedItemMode(double y, final int maxImageW, final int maxImageH, final double w) {
		setDisplaySize(maxImageW, maxImageH);
		final int x = UtilMath.roundToInt((w - getWidth() * getScale()) / 2.0);
		setOffset(x, y);
		y += getHeight() * getScale();
		return y;
	}

	@Override
	public void setDisplaySize(final int maxImageW, final int maxImageH) {
		final int[] newSize = UtilImage.downsize(getImage(), maxImageW, maxImageH);
		if (newSize == null) {
			if (getWidth() < getImageWidth()) {
				setWidthHeight(getImageWidth(), getImageHeight());
			}
		} else {
			setWidthHeight(newSize[0], newSize[1]);
		}
	}

	@Override
	public double getImageHeight() {
		return getImage().getHeight(null);
	}

	@Override
	public double getImageWidth() {
		return getImage().getWidth(null);
	}

	/**
	 * If either the cachedW >= desiredW OR cachedH >= desiredH, the cached
	 * image is at least as big as the scaled thumbnail from an infinitely big
	 * original would be. If it is as big as the original, we use it as is, too.
	 *
	 * @return whether to use the cached rawImage
	 */
	@Override
	public boolean isBigEnough(final int desiredW, final int desiredH, @SuppressWarnings("unused") final int _quality) {
		assert getImage() != null : this;
		final int cachedW = (int) getImageWidth();
		final int cachedH = (int) getImageHeight();

		assert (rawW == cachedW) == (rawH == cachedH);
		final boolean isOriginal = (rawW == cachedW);
		final boolean result = isOriginal || cachedW >= desiredW || cachedH >= desiredH;

		// could allow for lower quality if the image is to be reduced. Would
		// need to compute the real amount of reduction, which isn't just
		// (actualH * actualW) / (w * h).
		// final boolean result = (cachedW >= w || rawW == cachedW || cachedH >=
		// h || rawH == cachedH)
		// && quality >= _quality;

		return result;
	}

	@Override
	public @NonNull GridElementWrapper getWrapper() {
		return wrapper;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "for " + wrapper.getItem());
	}

	@Override
	public @NonNull Bungee art() {
		return getWrapper().art();
	}

	@Override
	public void setMouseDoc(final @Nullable String doc) {
		art().setClickDesc(doc);
	}

	@SuppressWarnings("unused")
	@Override
	public boolean isUnderMouse(final boolean state, final PInputEvent e) {
		return true;
	}

	@Override
	public boolean brush(final boolean state, @SuppressWarnings("unused") final PInputEvent e) {
		getWrapper().brush(state);
		return false;
	}

	@Override
	public void printUserAction(@SuppressWarnings("unused") final int modifiers) {
		getWrapper().printUserAction();
	}

	@Override
	public int getModifiersEx(final @NonNull PInputEvent e) {
		return e.getModifiersEx();
	}
}
