/*

 Created on Feb 20, 2006

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

import static edu.cmu.cs.bungee.javaExtensions.UtilColor.YELLOW;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.font.TextMeasurer;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.sun.image.codec.jpeg.ImageFormatException;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGDecodeParam;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;

import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.Arrow.ArrowPart;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.PRoot;
import edu.umd.cs.piccolo.activities.PActivity;
import edu.umd.cs.piccolo.activities.PActivityScheduler;
import edu.umd.cs.piccolo.activities.PColorActivity;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;
import edu.umd.cs.piccolo.activities.PTransformActivity;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PUtil;
import edu.umd.cs.piccolox.activities.PPositionPathActivity;

public final class PiccoloUtil {

	private static final int WHY_CANT_I_SEE_THICKNESS = 3;

	private PiccoloUtil() {
		// Disallow instantiation
	}

	// TODO Remove unused code found by UCDetector
	// static void showFonts(final PFrame frame, final int textH) {
	// final String[] fonts = java.awt.GraphicsEnvironment
	// .getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
	// for (int i = 0; i < fonts.length; i++) {
	// final APText s = new APText();
	// s.setText("Location of Origin " + fonts[i]);
	// s.setOffset(5, textH * i);
	// final Font f = new Font(fonts[i], Font.PLAIN, textH);
	// // Util.printDeep(f.getAvailableAttributes());
	// s.setFont(f);
	//
	// frame.getCanvas().getLayer().addChild(s);
	// System.out.println(fonts[i]);
	// }
	// }

	// TODO Remove unused code found by UCDetector
	// static void showChars(final PFrame frame, final char start, int n,
	// final Font f) {
	// final PText s = new PText();
	// s.setFont(f);
	// s.setConstrainWidthToTextWidth(false);
	// s.setWidth(frame.getCanvas().getLayer().getWidth());
	// final StringBuffer buf = new StringBuffer(n);
	// for (char i = start; n > 0; i++) {
	// if (Character.getType(i) == Character.MATH_SYMBOL
	// && f.canDisplay(i)) {
	// n--;
	// buf.append(i);
	// }
	// }
	// s.setText(buf.toString());
	// System.out.println(buf.toString());
	// frame.getCanvas().getLayer().addChild(s);
	// }

	// /**
	// * @return null iff buf!=null
	// */
	// public static @Nullable String descendentsString(final @NonNull PNode
	// node, // NO_UCD
	// // (unused
	// // code)
	// final int maxChildren, final int maxDepth, final boolean
	// invalidPaintOnly, @Nullable StringBuffer buf) {
	// final boolean needResult = buf == null;
	// if (buf == null) {
	// buf = new StringBuffer();
	// }
	// ancestorString(node, buf);
	// final String indent = "\n ";
	// if (node.getChildrenCount() > 0) {
	// printDescendentsInternal(node, indent, maxChildren, maxDepth,
	// invalidPaintOnly, buf);
	// } else {
	// buf.append(indent).append("<no descendents>");
	// }
	// buf.append("\n");
	// return needResult ? buf.toString() : null;
	// }
	//
	// private static void printDescendentsInternal(final @NonNull PNode node,
	// final @NonNull String indent,
	// final int maxChildren, final int maxDepth, final boolean
	// invalidPaintOnly,
	// final @NonNull StringBuffer buf) {
	// if (maxDepth > 0) {
	// int index = 0;
	// for (final Iterator<PNode> i = node.getChildrenIterator(); i.hasNext() &&
	// index < maxChildren;) {
	// final PNode each = i.next();
	// assert each != null;
	// if (!invalidPaintOnly || each.getPaintInvalid()) {
	// buf.append(indent).append(nodeDesc(each));
	// printDescendentsInternal(each, indent + " ", maxChildren, maxDepth - 1,
	// invalidPaintOnly, buf);
	// index++;
	// }
	// }
	// final int nChildren = node.getChildrenCount();
	// if (nChildren > index && !invalidPaintOnly) {
	// buf.append(indent).append("...").append(nChildren - index).append("
	// more");
	// }
	// }
	// }

	public static @NonNull String ancestorString(final @Nullable PNode pickedNode) {
		String result = "<null> has no ancestors.";
		if (pickedNode != null) {
			result = ancestorString(pickedNode, null);
		}
		assert result != null;
		return result;
	}

	/**
	 * @return null iff buf!=null
	 */
	public static @Nullable String ancestorString(final @NonNull PNode pickedNode, @Nullable StringBuffer buf) { // NO_UCD
		// (use
		// default)
		final boolean needResult = buf == null;
		if (buf == null) {
			buf = new StringBuffer();
		}
		if (pickedNode.getParent() == null) {
			buf.append("  <no ancestors>");
		}
		printAncestorsInternal(pickedNode, "\n", buf);
		return needResult ? buf.toString() : null;
	}

	private static void printAncestorsInternal(final @NonNull PNode pickedNode, final @NonNull String indent,
			final @NonNull StringBuffer buf) {
		final PNode parent = pickedNode.getParent();
		if (parent != null) {
			printAncestorsInternal(parent, indent + "  ", buf);
		}
		buf.append(indent).append(nodeDesc(pickedNode));
	}

	/**
	 * @return <name>
	 *
	 *         [LazyNode.nodeDesc()]
	 *
	 *         [globalScale != 1.0 ? " globalScale=" + globalScale ]
	 *
	 *         [transparency != 1.0 ? " transparency=" + transparency ]
	 *
	 *         [!visible ? " visible=false"]
	 */
	static @NonNull String nodeDesc(final @NonNull PNode pickedNode) {
		String name = UtilString.shortClassName(pickedNode);
		try {
			if (pickedNode instanceof LazyNode) {
				name += ((LazyNode) pickedNode).nodeDesc();
			}
		} catch (final java.lang.Exception se) {
			se.printStackTrace(System.out);
		}
		final double globalScale = pickedNode.getGlobalScale();
		final float transparency = pickedNode.getTransparency();
		final boolean visible = pickedNode.getVisible();
		return name + " getPaint=" + pickedNode.getPaint() + " globalBounds=" + pickedNode.getGlobalBounds()
				+ (globalScale != 1.0 ? " globalScale=" + globalScale : "")
				+ (transparency != 1.0 ? " transparency=" + transparency : "") + (!visible ? " visible=false" : "");
	}

	static @NonNull String truncateText(final @NonNull String text, final float availableWidth,
			final @NonNull Font font) {
		return truncateText(text, availableWidth, UtilString.getTextMeasurer(text, font));
	}

	/**
	 * Does not pay attention to word breaks.
	 */
	public static @NonNull String truncateText(final @NonNull String text, final float availableWidth,
			final @NonNull TextMeasurer measurer) {
		String result = "";
		if (UtilString.isNonEmptyString(text)) {
			assert availableWidth > 0f : "availableWidth=" + availableWidth + " for '" + text + "'";
			int charAtMaxAdvance = measurer.getLineBreakIndex(0, availableWidth);

			// Without this, it sometimes screws up on Arabic text
			while (charAtMaxAdvance > 0 && measurer.getAdvanceBetween(0, charAtMaxAdvance) > availableWidth) {
				charAtMaxAdvance--;
			}

			result = text.substring(0, charAtMaxAdvance);
		}
		assert result != null;
		return result;
	}

	/**
	 * PInterpolatingActivity barfs if duration==0. Use this instead.
	 */
	public abstract static class MyPInterpolatingActivity {

		final @Nullable PInterpolatingActivity pInterpolatingActivity;

		public MyPInterpolatingActivity(final @NonNull PNode node, final long duration, final long delay,
				final long stepRate) {
			if (duration == 0L) {
				pInterpolatingActivity = null;
				// maybe could delay by making duration=delay=steprate???
				assert delay == 0L : "Ignoring delay of " + delay;
				setRelativeTargetValue(1f);
			} else {
				final MyPInterpolatingActivity myPInterpolatingActivity = this;

				pInterpolatingActivity = new PInterpolatingActivity(duration, stepRate,
						delay + System.currentTimeMillis(), 1, PInterpolatingActivity.SOURCE_TO_DESTINATION) {

					@Override
					public void setRelativeTargetValue(final float zeroToOne1) {
						myPInterpolatingActivity.setRelativeTargetValue(zeroToOne1);
					}
				};

				node.addActivity(pInterpolatingActivity);
			}
		}

		/**
		 * Subclasses should override this method and set the value on their
		 * target (the object that they are modifying) accordingly.
		 */
		public abstract void setRelativeTargetValue(float zeroToOne);

	}

	/**
	 * This offers more parameters than PNode.animateToTransparency
	 */
	public static @Nullable PInterpolatingActivity animateToTransparency(final @NonNull PNode node,
			final float goalTransparency, final long delay, final long duration) {
		final float startTransparency = node.getTransparency();
		final MyPInterpolatingActivity myPInterpolatingActivity = new MyPInterpolatingActivity(node, duration, delay,
				PUtil.DEFAULT_ACTIVITY_STEP_RATE) {

			@Override
			public void setRelativeTargetValue(final float zeroToOne1) {
				node.setTransparency(UtilMath.interpolate(startTransparency, goalTransparency, zeroToOne1));
			}
		};

		return myPInterpolatingActivity.pInterpolatingActivity;
	}

	/**
	 * @param pnode
	 *            node to print
	 * @param file
	 *            destination jpg
	 * @param dpi
	 *            dots per inch in jpg file. image size is the same as the size
	 *            of the pnode on the screen.
	 * @param quality
	 *            jpg quality from 1 - 100
	 * @throws ImageFormatException
	 * @throws IOException
	 */
	public static void savePNodeAsJPEG(final @NonNull PNode pnode, final @NonNull File file, // NO_UCD
			// (use
			// default)
			final int dpi, final int quality) throws ImageFormatException, IOException {
		// JpegOptionsFileDialog fd = JpegOptionsFileDialog.saveFile(
		// "Save the current summary tree", directory, 85, (int) tWin
		// .getWidth(), (int) tWin.getHeight());

		final GraphicsConfiguration gc = edu.cmu.cs.bungee.javaExtensions.UtilImage.getGraphicsConfiguration();
		final AffineTransform normalizingTransform = gc.getNormalizingTransform();
		final double relativeScale = dpi / 72.0;
		normalizingTransform.scale(relativeScale, relativeScale);
		final PBounds bounds = pnode.getFullBounds();
		final double width = bounds.getWidth();
		final int pageW = (int) (width * normalizingTransform.getScaleX());
		final double height = bounds.getHeight();
		final int pageH = (int) (height * normalizingTransform.getScaleY());

		final BufferedImage im = edu.cmu.cs.bungee.javaExtensions.UtilImage.createCompatibleImage(pageW, pageH);
		final Graphics2D g = (Graphics2D) im.getGraphics();
		g.setTransform(gc.getDefaultTransform());
		g.transform(normalizingTransform);

		g.setPaint(UtilColor.WHITE);
		g.fillRect(0, 0, pageW, pageH);
		pnode.print(g, pageFormat(width, height), 0);

		try (OutputStream out = edu.cmu.cs.bungee.javaExtensions.UtilFiles.getOutputStream(file);) {
			final JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
			final JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(im);
			param.setQuality(quality / 100f, false);
			param.setDensityUnit(JPEGDecodeParam.DENSITY_UNIT_DOTS_INCH);
			param.setXDensity(dpi);
			param.setYDensity(dpi);
			encoder.setJPEGEncodeParam(param);
			encoder.encode(im);
		}
	}

	public static @NonNull PageFormat pageFormat(final double pageW, final double pageH) { // NO_UCD
		// (use
		// default)
		final Paper paper = new Paper();
		paper.setSize(pageW, pageH);
		paper.setImageableArea(0, 0, pageW, pageH);
		final PageFormat pageFormat = new PageFormat();
		pageFormat.setPaper(paper);
		return pageFormat;
	}

	// These are lazily initialized to @NonNull
	private static volatile BevelBorder whyCantISeeBorder;
	private static Arrow whyCantISeePointer;

	private static @NonNull BevelBorder getWhyCantISeeBorder(final @NonNull PLayer layer) {
		if (whyCantISeeBorder == null) {
			whyCantISeeBorder = new SolidBorder(YELLOW, WHY_CANT_I_SEE_THICKNESS);
			layer.addChild(whyCantISeeBorder);
		}
		whyCantISeeBorder.setTransparency(1f);
		assert whyCantISeeBorder != null;
		return whyCantISeeBorder;
	}

	private static @NonNull Arrow getWhyCantISeePointer(final @NonNull PLayer layer) {
		if (whyCantISeePointer == null) {
			whyCantISeePointer = new Arrow(YELLOW, 10, 1, 2);
			whyCantISeePointer.setVisible(ArrowPart.LEFT_TAIL, true);
			whyCantISeePointer.setPickable(false);
			layer.addChild(whyCantISeePointer);
		}
		whyCantISeePointer.setTransparency(1f);
		assert whyCantISeePointer != null;
		return whyCantISeePointer;
	}

	// TODO Remove unused code found by UCDetector
	// public static void setWhyCantISeeTransparency(final float transparency) {
	// if (whyCantISeeBorder != null) {
	// whyCantISeeBorder.setTransparency(transparency);
	// }
	// if (whyCantISeePointer != null) {
	// whyCantISeePointer.setTransparency(transparency);
	// }
	// }

	public static void whyCantISee(final @Nullable PNode node) { // NO_UCD
																	// (unused
																	// code)
		System.out.println("\nUtil.whyCantISee:");
		if (node == null) {
			System.out.println(" because node is null.\n");
		} else {
			final PLayer layer = whyCantISeeInternal(node, "");
			System.out.println();
			if (layer != null) {
				final PBounds globalBounds = node.getGlobalBounds();
				getWhyCantISeeBorder(layer).borderBounds = globalBounds;
				getWhyCantISeeBorder(layer).moveToFront();

				final PBounds layerBounds = layer.getGlobalBounds();
				final float x1 = (float) layerBounds.getCenterX();
				final float y1 = (float) layerBounds.getCenterY();
				final float x2 = (float) globalBounds.getCenterX();
				final float y2 = (float) globalBounds.getCenterY();
				getWhyCantISeePointer(layer).setEndpoints(x1, y1, x2, y2);
				getWhyCantISeePointer(layer).moveToFront();
			}
		}
	}

	private static PLayer whyCantISeeInternal(final @NonNull PNode node, final @NonNull String indent) {
		PLayer result = null;
		final PNode parent = node.getParent();
		if (parent != null) {
			result = whyCantISeeInternal(parent, indent + "    ");
		}
		if (node instanceof PLayer) {
			result = (PLayer) node;
		}
		System.out.println(indent + node.getGlobalBounds() + " " + node);
		if (parent == null && !(node instanceof PRoot)) {
			System.out.println(indent + "Does not descend from the root!");
		}
		if (!node.getVisible()) {
			System.out.println(indent + "Is not visible!");
		}
		if (node.getTransparency() < .1f) {
			System.out.println(indent + "Has transparency " + node.getTransparency() + "!");
		}
		if (node.getPaint() == null && (!(node instanceof PText) || ((PText) node).getTextPaint() == null)) {
			System.out.println(indent + "Has no paint!");
		}
		final double area = node.getHeight() * node.getWidth();
		if (area < 7.0) {
			System.out.println(indent + "Has area " + area + "!");
		}
		return result;
	}

	static void moveAncestorsToFront(final @NonNull PNode pNode) {
		final PNode parent = pNode.getParent();
		if (parent != null) {
			pNode.moveToFront();
			moveAncestorsToFront(parent);
		}
	}

	// TODO Remove unused code found by UCDetector
	// static void moveAncestorsToBack(final @NonNull PNode pNode) {
	// final PNode parent = pNode.getParent();
	// if (parent != null) {
	// pNode.moveToBack();
	// moveAncestorsToBack(parent);
	// }
	// }

	public static @Nullable PNode findAncestorNodeType(@Nullable PNode pickedNode, final @NonNull Class<?> nodeType) {
		while (pickedNode != null && !nodeType.isInstance(pickedNode)) {
			pickedNode = pickedNode.getParent();
		}
		return pickedNode;
	}

	static @Nullable PNode findDescendentNodeType(final @NonNull PNode pickedNode, final @NonNull Class<?> nodeType) {
		if (nodeType.isInstance(pickedNode)) {
			return pickedNode;
		}
		for (final Iterator<PNode> it = pickedNode.getChildrenIterator(); it.hasNext();) {
			final PNode next = it.next();
			assert next != null;
			final PNode result = findDescendentNodeType(next, nodeType);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	// TODO Remove unused code found by UCDetector
	// public static void colorDescendentsRandomly(final PNode node) {
	// node.setTransparency(0.5f);
	// node.setPaint(Color.getHSBColor((float) Math.random(), 1, 1));
	// for (int i = 0; i < node.getChildrenCount(); i++) {
	// colorDescendentsRandomly(node.getChild(i));
	// }
	// }

	/**
	 * Replacement for PActivityScheduler.getAnimating, which barfs if
	 * activities changes out from under it.
	 *
	 * Return true if any of the scheduled activities return true to the message
	 * isAnimation();
	 */
	public static boolean getAnimating(final @NonNull PActivityScheduler pActivityScheduler) {
		boolean result = false;
		final List<PActivity> _activities = new ArrayList<>(pActivityScheduler.getActivitiesReference());
		for (final PActivity each : _activities) {
			if (each != null && isAnimation(each)) {
				result = true;
				break;
			}
		}
		return result;
	}

	// if (activitiesChanged) {
	// animating = false;
	// for(int i=0; i<activities.size(); i++) {
	// PActivity each = (PActivity) activities.get(i);
	// animating |= each.isAnimation();
	// }
	// activitiesChanged = false;
	// }
	// return animating;

	private static boolean isAnimation(final @NonNull PActivity pActivity) {
		assert !pActivity.isStepping() || pActivity instanceof PInterpolatingActivity
				|| pActivity instanceof PColorActivity || pActivity instanceof PTransformActivity
				|| pActivity instanceof PPositionPathActivity : pActivity;
		return pActivity.isStepping();
	}

}
