package edu.cmu.cs.bungee.javaExtensions;

import java.awt.Component;

/*

 Created on May 22, 2014

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

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Blob;
import java.sql.SQLException;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;

import org.eclipse.jdt.annotation.NonNull;

import com.sun.image.codec.jpeg.ImageFormatException;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;

/**
 * misc static functions on images
 *
 */
public class UtilImage {

	static {
		// Unless images are large, it's better not to cache
		ImageIO.setUseCache(false);

		// load plug-ins for various image formats (from
		// commonJars/twelvemonkeys...)
		IIORegistry.getDefaultInstance().registerApplicationClasspathSpis();
		// printIIOServiceProviders();
	}

	public static @NonNull File captureComponent(final Component component, final String baseFileName) {
		File file = null;
		final Rectangle rect = component.getBounds();

		try {
			final String format = "png";
			// final String baseFileName = component.getName() + "_" + new
			// Date();
			final BufferedImage captureImage = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_ARGB);
			component.paint(captureImage.getGraphics());

			file = new File(baseFileName + "." + format);
			ImageIO.write(captureImage, format, file);

			// System.out.printf("The screenshot of %s was saved!", file);
		} catch (final IOException ex) {
			System.err.println(ex);
		}
		assert file != null;
		return file;
	}

	// See if we can read multiple image formats without JAI
	public static BufferedImage readBufferedImage(final URL url) throws IOException {
		final String urlString = url.toString();
		assert urlString != null;
		// final String extension = UtilString.getLastSplit(urlString, "\\.");
		// if (extension.toLowerCase(Locale.getDefault()).equals("pdf")) {
		// return pdfToBufferedImage(url);
		// } else {
		return ImageIO.read(url);
		// }
	}

	// Commented out pdf stuff to reduce .jar size 2016/03
	public static BufferedImage blobToBufferedImage(final Blob blob) throws IOException, SQLException {
		BufferedImage result = null;
		try (final InputStream blobStream = blob.getBinaryStream();) {
			result = ImageIO.read(blobStream);
		}
		return result;
	}

	// private static BufferedImage pdfToBufferedImage(final URL url) throws
	// IOException {
	// BufferedImage result = null;
	// try (PDDocument document = PDDocument.load(url);) {
	// final int numberOfPages = document.getNumberOfPages();
	// if (numberOfPages >= 1) {
	// result = ((PDPage)
	// document.getDocumentCatalog().getAllPages().get(0)).convertToImage();
	// } else {
	// System.err.println(
	// "Unable to load pdf '" + url + "' as an image because it has " +
	// numberOfPages + " pages.");
	// }
	// }
	// return result;
	// }

	public static void writeJPGImage(final BufferedImage thumbImage, int quality, final String outFile)
			throws ImageFormatException, IOException {
		try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));) {
			final JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
			final JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(thumbImage);
			quality = Math.max(0, Math.min(quality, 100));
			param.setQuality(quality / 100.0f, false);
			encoder.setJPEGEncodeParam(param);
			encoder.encode(thumbImage);
		}
	}

	public static ByteArrayOutputStream getJPGstream(final Image image, final int newW, final int newH,
			final int quality) throws IOException {

		final ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
		// non-JAI - JPEG
		final JPEGImageEncoder jpegImageEncoder = JPEGCodec.createJPEGEncoder(byteArrayStream);
		final BufferedImage resized = UtilImage.resize(image, newW, newH, false);
		final JPEGEncodeParam param = jpegImageEncoder.getDefaultJPEGEncodeParam(resized);
		param.setQuality(quality / 100f, false);
		jpegImageEncoder.setJPEGEncodeParam(param);
		jpegImageEncoder.encode(resized);
		return byteArrayStream;
	}

	/**
	 * @return a BufferedImage whose dimensions are the minimum of w, h and the
	 *         original dimensions. If image is already a BufferedImage, and
	 *         !alwaysCopy, image is returned unchanged. (Does not maintain
	 *         aspect ratio.)
	 */
	static BufferedImage resize(Image image, int w, int h, final boolean alwaysCopy) {
		final int originalW = image.getWidth(null);
		final int originalH = image.getHeight(null);
		w = Math.min(w, originalW);
		h = Math.min(h, originalH);
		if (alwaysCopy || w != originalW || h != originalH || !(image instanceof BufferedImage)) {
			final BufferedImage resized = createCompatibleImage(w, h);
			final Graphics2D g = (Graphics2D) resized.getGraphics();
			// g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			// RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			// RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			final boolean finished = g.drawImage(image, 0, 0, w, h, null);
			g.dispose();
			image.flush();
			assert finished;
			assert resized.getWidth() == w : resized.getWidth() + " " + w;
			assert resized.getHeight() == h : resized.getHeight() + " " + h;
			image = resized;
		}
		return (BufferedImage) image;
	}

	/**
	 * @return a BufferedImage whose dimensions are the minimum of maxW/maxH and
	 *         the original dimensions. If image is already this small, it is
	 *         returned unchanged.
	 */
	public static BufferedImage resizeMaintainingAspectRatio(BufferedImage image, final int maxW, final int maxH) {
		final int[] desiredSize = downsize(image, maxW, maxH);
		if (desiredSize != null) {

			final int desiredImageWidth = desiredSize[0];
			final int desiredImageHeight = desiredSize[1];

			final BufferedImage resized = getScaledInstance(image, desiredImageWidth, desiredImageHeight,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);

			assert resized.getWidth() == desiredImageWidth : resized.getWidth() + " " + desiredImageWidth;
			assert resized.getHeight() == desiredImageHeight : resized.getHeight() + " " + desiredImageHeight;
			image = resized;
		}
		return image;
	}

	/**
	 * @return size for downscaled image, or null if it is already small enough
	 *         to fit in maxImageW/H.
	 */
	public static int[] downsize(final Image image, final int maxImageW, final int maxImageH) {
		return image == null ? null : downsizedSize(image.getWidth(null), image.getHeight(null), maxImageW, maxImageH);
	}

	/**
	 * @return size for downscaled image, or null if it is already small enough
	 *         to fit in maxImageW/H.
	 */
	public static int[] downsizedSize(final double imageW, final double imageH, final int maxImageW,
			final int maxImageH) {
		int[] result = null;
		assert imageW > 0.0 && imageH > 0.0 : "Illegal image size: " + imageW + "x" + imageH;
		final double ratio = Math.min(maxImageW / imageW, maxImageH / imageH);
		if (ratio < 1.0) {
			final int downsizedW = Math.max(1, UtilMath.roundToInt(imageW * ratio));
			final int downsizedH = Math.max(1, UtilMath.roundToInt(imageH * ratio));
			final int[] result2 = { downsizedW, downsizedH };
			result = result2;
		}
		return result;
	}

	/**
	 * From https://today.java.net/pub/a/today/2007/04/03/perils-of-image-
	 * getscaledinstance.html
	 *
	 * Convenience method that returns a scaled instance of the provided
	 * {@code BufferedImage}.
	 *
	 * @param img
	 *            the original image to be scaled
	 * @param targetWidth
	 *            the desired width of the scaled instance, in pixels
	 * @param targetHeight
	 *            the desired height of the scaled instance, in pixels
	 * @param hint
	 *            one of the rendering hints that corresponds to
	 *            {@code RenderingHints.KEY_INTERPOLATION} (e.g.
	 *            {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
	 *            {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
	 *            {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
	 * @param higherQuality
	 *            if true, this method will use a multi-step scaling technique
	 *            that provides higher quality than the usual one-step technique
	 *            (only useful in downscaling cases, where {@code targetWidth}
	 *            or {@code targetHeight} is smaller than the original
	 *            dimensions, and generally only when the {@code BILINEAR} hint
	 *            is specified)
	 * @return a scaled version of the original {@code BufferedImage}
	 */
	static BufferedImage getScaledInstance(final BufferedImage img, final int targetWidth, final int targetHeight,
			final Object hint, final boolean higherQuality) {
		final int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB
				: BufferedImage.TYPE_INT_ARGB;
		BufferedImage ret = img;
		int w, h;
		if (higherQuality) {
			// Use multi-step technique: start with original size, then
			// scale down in multiple passes with drawImage()
			// until the target size is reached
			w = img.getWidth();
			h = img.getHeight();
		} else {
			// Use one-step technique: scale directly from original
			// size to target size with a single drawImage() call
			w = targetWidth;
			h = targetHeight;
		}

		do {
			if (higherQuality && w > targetWidth) {
				w /= 2;
				if (w < targetWidth) {
					w = targetWidth;
				}
			}

			if (higherQuality && h > targetHeight) {
				h /= 2;
				if (h < targetHeight) {
					h = targetHeight;
				}
			}

			final BufferedImage tmp = new BufferedImage(w, h, type);
			final Graphics2D g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			g2.drawImage(ret, 0, 0, w, h, null);
			g2.dispose();

			ret = tmp;
		} while (w != targetWidth || h != targetHeight);

		return ret;
	}

	public static BufferedImage rotate(final BufferedImage image, final double theta) {
		final boolean swap = Math.round(2.0 * theta / Math.PI) % 2 == 1;
		final int w = image.getWidth(null);
		final int h = image.getHeight(null);
		final double delta = swap ? (w - h) / 2.0 : 0.0;
		final AffineTransform at = AffineTransform.getTranslateInstance(-delta, delta);
		at.rotate(theta, w / 2.0, h / 2.0);
		final BufferedImage rotated = swap ? createCompatibleImage(h, w) : createCompatibleImage(w, h);
		final Graphics2D g = (Graphics2D) rotated.getGraphics();
		// g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
		// RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		// RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(image, at, null);
		g.dispose();
		return rotated;
	}

	public static BufferedImage createCompatibleImage(final int w, final int h) {
		return GRAPHIS_CONFIGURATION.createCompatibleImage(w, h, Transparency.OPAQUE);
	}

	public static GraphicsConfiguration getGraphicsConfiguration() {
		return GRAPHIS_CONFIGURATION;
	}

	private static final GraphicsConfiguration GRAPHIS_CONFIGURATION = GraphicsEnvironment.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice().getDefaultConfiguration();

}
