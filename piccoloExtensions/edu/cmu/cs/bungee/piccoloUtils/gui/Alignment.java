package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PActivity;
import edu.umd.cs.piccolo.util.PAffineTransform;
import edu.umd.cs.piccolo.util.PBounds;

/**
 * Utility to let PNodes align with each other with offsets, and to create
 * LazyPPath lines
 */
public class Alignment implements Serializable {

	protected static final long serialVersionUID = 1L;

	// Left Center Right None
	// 000000 000001 000010 000100 Top
	// 001000 001001 001010 001100 Center
	// 010000 010001 010010 010100 Bottom
	// 100000 100001 100010 100100 None
	//
	// IS_BOTTOM_MASK >0 010000
	// IS_CHANGE_X_MASK =0 000100
	// IS_CHANGE_Y_MASK =0 100000
	// IS_LEFT_MASK =0 000111
	// IS_RIGHT_MASK >0 000010
	// IS_TOP_MASK =0 111000

	private static final int IS_BOTTOM_MASK = 0x10;
	// private static final int IS_CHANGE_X_MASK = 0x04;
	// private static final int IS_CHANGE_Y_MASK = 0x20;
	private static final int IS_LEFT_MASK = 0x07;
	private static final int IS_RIGHT_MASK = 0x02;
	private static final int IS_TOP_MASK = 0x38;

	/**
	 * The possible attachment points
	 */
	public static final int TOP_LEFT = 0B000000;
	public static final int TOP_CENTER = 0B000001;
	public static final int TOP_RIGHT = 0B000010;
	public static final int TOP = 0B000100;
	public static final int CENTER_LEFT = 0B001000;
	public static final int CENTER_CENTER = 0B001001;
	public static final int CENTER_RIGHT = 0B001010;
	public static final int CENTER_Y = 0B001100;
	public static final int BOTTOM_LEFT = 0B010000;
	public static final int BOTTOM_CENTER = 0B010001;
	public static final int BOTTOM_RIGHT = 0B010010;
	public static final int BOTTOM = 0B010100;
	public static final int LEFT = 0B100000;
	public static final int CENTER_X = 0B100001;
	public static final int RIGHT = 0B100010;
	public static final int NONE = 0B100100;

	private static final @NonNull int[] OPPOSITE_DIRECTIONS = new int[NONE + 1];

	static {
		addAllOppositeDirections();
	}

	public Alignment() {
	}

	/**
	 * bounds will be empty if width or height is zero, and we don't want that
	 * to prevent using the offset for alignment. Therefore, force width and
	 * height to be > 0.0.
	 *
	 * @return global bounds
	 */
	public static @NonNull PBounds ensureGlobalBounds(final @NonNull PNode node) {
		// getRoot is expensive. Make caller responsible for these checks.
		// assert node.getRoot() != null :
		// edu.cmu.cs.bungee.piccoloUtils.gui.Util
		// .printAncestors(node);

		final double width = node.getWidth();
		final double height = node.getHeight();
		if (width <= 0.0 || height <= 0.0) {
			node.setBounds(node.getX(), node.getY(), width <= 0.0 ? 1.0 : width, height <= 0.0 ? 1.0 : height);
		}

		final PBounds result = node.getGlobalBounds();
		assert !result.isEmpty() : PiccoloUtil.ancestorString(node) + "\n " + node.getBounds() + " " + width + " "
				+ height;
		return result;
	}

	private static boolean isBottom(final int direction) {
		return (direction & IS_BOTTOM_MASK) != 0;
	}

	private static boolean isLeft(final int direction) {
		return (direction & IS_LEFT_MASK) == 0;
	}

	private static boolean isRight(final int direction) {
		return (direction & IS_RIGHT_MASK) != 0;
	}

	private static boolean isTop(final int direction) {
		return (direction & IS_TOP_MASK) == 0;
	}

	private static boolean isPoint(final int direction) {
		return oppositeDirection(direction) >= 0;
	}

	private static int oppositeDirection(final int direction) {
		if (direction >= 0 && direction <= NONE) {
			return OPPOSITE_DIRECTIONS[direction];
		} else {
			return -1;
		}
	}

	// dX
	// 00 01 02 04 dY
	// 08 09 10 12
	// 16 17 18 20
	// 32 33 34 36
	private static void addOppositeDirections(final int direction1, final int direction2) {
		OPPOSITE_DIRECTIONS[direction1] = direction2;
		OPPOSITE_DIRECTIONS[direction2] = direction1;
	}

	private static void addAllOppositeDirections() {
		for (int i = 0; i <= NONE; i++) {
			OPPOSITE_DIRECTIONS[i] = -1;
		}
		addOppositeDirections(TOP_LEFT, BOTTOM_RIGHT);
		addOppositeDirections(TOP_CENTER, BOTTOM_CENTER);
		addOppositeDirections(TOP_RIGHT, BOTTOM_LEFT);
		addOppositeDirections(CENTER_LEFT, CENTER_RIGHT);
		addOppositeDirections(CENTER_CENTER, CENTER_CENTER);
		addOppositeDirections(LEFT, RIGHT);
		addOppositeDirections(TOP, BOTTOM);

		addOppositeDirections(CENTER_X, CENTER_X);
		addOppositeDirections(CENTER_Y, CENTER_Y);
		addOppositeDirections(NONE, NONE);
	}

	/**
	 * @param point
	 *            encodes an x coordinate on a rectangle, a y coordinate, or
	 *            both. the low three bits encode x, and the next three encode
	 *            y. 0 = left/top, 1 = middle, 2 = right/bottom, 4 = n/a
	 * @return the x-coordinate of the point on the rect
	 */
	private static double pointX(final @NonNull Rectangle2D rect, int point) {
		point = point & IS_LEFT_MASK;
		assert point < 4 : point + " does not specify x";
		final double result = rect.getX() + point * rect.getWidth() / 2.0;
		return (int) result;
	}

	/**
	 * @return the Y-coordinate of the point on the rect
	 */
	private static double pointY(final @NonNull Rectangle2D rect, int point) {
		point = (point >> 3) & IS_LEFT_MASK;
		assert point < 4 : point + " does not specify y";
		final double result = rect.getY() + point * rect.getHeight() / 2.0;
		return (int) result;
	}

	/**
	 * @return Piccolo's format for points: left=0; right = 1; top=0; bottom=1
	 *         -1 means don't change
	 */
	public static @NonNull Point2D point2DPercent(final int point) {
		assert isPoint(point);
		return new Point2D.Double(pointPercent(point), pointPercent(point >> 3));
	}

	/**
	 * @return Piccolo's format for points: left=0; right = 1 -1 means don't
	 *         change
	 */
	private static double pointPercent(int maskedPoint) {
		double result = -1.0;
		maskedPoint &= IS_LEFT_MASK;
		if (maskedPoint < 4) { // T & L
			result = maskedPoint * 0.5;
		}
		return result;
	}

	/**
	 * Make the line connect the two attachment points, with the offset from the
	 * base1 point.
	 */
	public static void stretchLine(final @NonNull LazyPPath line, final @NonNull PNode base1, final int base1Point,
			final @NonNull PNode base2, final int base2Point, final double base1VerticalOffset) {
		Rectangle2D base1Bounds, base2Bounds;
		try {
			base1Bounds = ensureGlobalBounds(base1);
		} catch (final AssertionError e) {
			System.err.println("Alignment.stretchLine: while checking base1:");
			throw (e);
		}
		try {
			base2Bounds = ensureGlobalBounds(base2);
		} catch (final AssertionError e) {
			System.err.println("Alignment.stretchLine: while checking base2:");
			throw (e);
		}

		stretchLine(line, base1Bounds, base1Point, base2Bounds, base2Point, base1VerticalOffset);
	}

	/**
	 * @param line
	 *            PPath to update: draw horzontal-vertical-horizontal line
	 *            segments between the bases
	 * @param base1Bounds
	 *            define start
	 * @param base1Point
	 *            define start
	 * @param base2Bounds
	 *            define end
	 * @param base2Point
	 *            define end
	 * @param base1VerticalOffset
	 *            distance to the vertical line segment
	 */
	public static void stretchLine(final @NonNull LazyPPath line, final @NonNull Rectangle2D base1Bounds,
			final int base1Point, final @NonNull Rectangle2D base2Bounds, final int base2Point,
			final double base1VerticalOffset) {
		final double x1 = pointX(base1Bounds, base1Point);
		final double y1 = pointY(base1Bounds, base1Point);
		final double x2 = pointX(base2Bounds, base2Point);
		final double y2 = pointY(base2Bounds, base2Point);

		final PNode parent = line.getParent();
		final Point2D point1 = parent.globalToLocal(new Point2D.Double(x1, y1));
		final Point2D point2 = parent.globalToLocal(new Point2D.Double(x1 + base1VerticalOffset, y1));
		final Point2D point3 = parent.globalToLocal(new Point2D.Double(x1 + base1VerticalOffset, y2));
		final Point2D point4 = parent.globalToLocal(new Point2D.Double(x2, y2));

		line.reset();
		line.moveTo((float) point1.getX(), (float) point1.getY());
		if (y1 != y2) {
			line.lineTo((float) point2.getX(), (float) point2.getY());
			line.lineTo((float) point3.getX(), (float) point3.getY());
		}
		line.lineTo((float) point4.getX(), (float) point4.getY());
	}

	/**
	 * Make the attachment's attachmentPoint align with the base's basePoint
	 */
	public static void align(final @NonNull PNode attachment, final int attachmentPoint, final @NonNull PNode base,
			final int basePoint) {
		align(attachment, attachmentPoint, base, basePoint, 0.0, 0.0);
	}

	public static void alignAndAddMargin(final @NonNull PNode attachment, final int attachmentPoint,
			final @NonNull PNode base, final int basePoint, final double margin) {
		final PBounds ab = attachment.getFullBounds();
		final double dx = isRight(attachmentPoint) && isLeft(basePoint) ? margin
				: isRight(basePoint) && isLeft(attachmentPoint) ? -margin : 0.0;
		final double dy = isBottom(attachmentPoint) && isTop(basePoint) ? margin
				: isBottom(basePoint) && isTop(attachmentPoint) ? -margin : 0;
		align(attachment, attachmentPoint, base, basePoint, dx - ab.getX(), dy - ab.getY());
	}

	/**
	 * translate attachment immediately to align with base. Margins are added to
	 * base.
	 */
	public static void align(final @NonNull PNode attachment, final int attachmentPoint, final @NonNull PNode base,
			final int basePoint, final double baseXoffset, final double baseYoffset) {
		alignInternal(attachment, attachmentPoint, base, basePoint, baseXoffset, baseYoffset, 0L);
	}

	/**
	 * translate attachment to align with base. Margins are added to base.
	 */
	private static @Nullable PActivity alignInternal(final @NonNull PNode attachment, final int attachmentPoint,
			final @NonNull PNode base, final int basePoint, final double baseXoffset, final double baseYoffset,
			final long duration) {
		PBounds baseBounds;
		try {
			baseBounds = ensureGlobalBounds(base);
		} catch (final AssertionError e) {
			System.err.println("Alignment.alignInternal: while aligning " + attachment + " with EMPTY base " + base);
			throw (e);
		}
		try {
			ensureGlobalBounds(attachment);
		} catch (final AssertionError e) {
			System.err.println(
					"Alignment.alignInternal: while aligning EMPTY attachment " + attachment + " with base " + base);
			throw (e);
		}
		// if (isChangeX(attachmentPoint) && isChangeY(attachmentPoint)) {
		final Point2D srcPt = point2DPercent(attachmentPoint);
		final Point2D dstPt = point2DPercent(basePoint);
		return position(attachment, srcPt, dstPt, baseBounds, baseXoffset, baseYoffset, duration);
	}

	/**
	 * This will calculate the necessary transform in order to make this node
	 * appear at a particular position relative to the specified bounding box.
	 * The source point specifies a point in the unit square (0, 0) - (1, 1)
	 * that represents an anchor point on the corresponding node to this
	 * transform. The destination point specifies an anchor point on the
	 * reference node. The position method then computes the transform that
	 * results in transforming this node so that the source anchor point
	 * coincides with the reference anchor point. This can be useful for layout
	 * algorithms as it is straightforward to position one object relative to
	 * another.
	 * <p>
	 * For example, If you have two nodes, A and B, and you call
	 *
	 * <PRE>
	 * Point2D srcPt = new Point2D.Double(1.0, 0.0);
	 * Point2D destPt = new Point2D.Double(0.0, 0.0);
	 * A.position(srcPt, destPt, B.getGlobalBounds(), 750, null);
	 * </PRE>
	 *
	 * The result is that A will move so that its upper-right corner is at the
	 * same place as the upper-left corner of B, and the transition will be
	 * smoothly animated over a period of 750 milliseconds.
	 *
	 * @param srcPt
	 *            The anchor point on this transform's node (normalized to a
	 *            unit square) if its x (y) coord < 0, it won't change x (y)
	 * @param destPt
	 *            The anchor point on destination bounds (normalized to a unit
	 *            square)
	 * @param destBounds
	 *            The bounds (in global coordinates) used to calculate this
	 *            transform's node
	 * @param duration
	 *            Number of milliseconds over which to perform the animation
	 * @return the animation PActivity
	 */
	static @Nullable PActivity position(final @NonNull PNode src, final @NonNull Point2D srcPt,
			final @NonNull Point2D destPt, final @NonNull Rectangle2D destBounds, final double baseXoffset,
			final double baseYoffset, final long duration) {
		PActivity result = null;
		if (src.getParent() != null) {
			// First compute translation amount in global coordinates
			final Rectangle2D srcBounds = src.getGlobalBounds();
			final double srcx = PNode.lerp(srcPt.getX(), srcBounds.getX(), srcBounds.getX() + srcBounds.getWidth());
			final double srcy = PNode.lerp(srcPt.getY(), srcBounds.getY(), srcBounds.getY() + srcBounds.getHeight());
			final double destx = PNode.lerp(destPt.getX(), destBounds.getX(), destBounds.getX() + destBounds.getWidth())
					+ baseXoffset;
			final double desty = PNode.lerp(destPt.getY(), destBounds.getY(),
					destBounds.getY() + destBounds.getHeight()) + baseYoffset;

			// Convert vector to local coordinates
			final Point2D localSrcPt = new Point2D.Double(srcx, srcy);
			src.globalToLocal(localSrcPt);
			final Point2D localDstPt = new Point2D.Double(destx, desty);
			src.globalToLocal(localDstPt);
			final double dx = srcPt.getX() < 0.0 ? 0.0 : (localDstPt.getX() - localSrcPt.getX());
			final double dy = srcPt.getY() < 0.0 ? 0.0 : (localDstPt.getY() - localSrcPt.getY());

			// Finally, animate change
			final PAffineTransform at = src.getTransform();
			at.translate(dx, dy);
			result = src.animateToTransform(at, duration);
		}
		return result;
	}

	/**
	 * translate attachment to align with base. Margins are added to base.
	 */
	public void animateToAlignment(final @NonNull PNode attachment, final int attachmentPoint,
			final @NonNull PNode base, final int basePoint, final double baseXoffset, final double baseYoffset,
			final long duration) {
		final PActivity a = alignInternal(attachment, attachmentPoint, base, basePoint, baseXoffset, baseYoffset,
				duration);
		trackAnimationJob(a);
	}

	/**
	 * @param node
	 *            set bounds to minimally include virtualChildren, plus margins
	 */
	public static void setBoundsFromNodes(final @NonNull PNode node, final @NonNull PNode[] virtualChildren,
			final double xMargin, final double yMargin) {
		final Rectangle2D local = getChildrenBounds(node, virtualChildren, xMargin, yMargin);
		assert !local.isEmpty() : node + " " + xMargin + " " + yMargin;
		node.setBounds(local);
		assert !node.getBounds().isEmpty() : local;
	}

	/**
	 * @param node
	 *            used to determine globalToLocal transform
	 * @return union of virtualChildren's bounds, plus margins on all four
	 *         sides.
	 */
	public static @NonNull Rectangle2D getChildrenBounds(final @NonNull PNode node,
			final @NonNull PNode[] virtualChildren, final double xMargin, final double yMargin) {
		final Rectangle2D globalBounds = getChildrenGlobalBounds(virtualChildren, xMargin, yMargin);
		final Rectangle2D local = node.globalToLocal(globalBounds);
		assert !local.isEmpty() : globalBounds;
		return local;
	}

	/**
	 * @return union of virtualChildren's global bounds, plus margins on all
	 *         four sides.
	 */
	public static @NonNull Rectangle2D getChildrenGlobalBounds(final @NonNull PNode[] virtualChildren,
			final double xMargin, final double yMargin) {
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (final PNode element : virtualChildren) {
			final PNode child = element;
			if (child != null && child.getRoot() != null && child.getVisible() && child.getTransparency() > 0f
					&& (!(child instanceof APText) || ((APText) child).getText().trim().length() > 0)) {
				final PBounds childBounds = child.getGlobalBounds();
				final double x0 = childBounds.getX();
				final double x1 = x0 + childBounds.getWidth();
				final double y0 = childBounds.getY();
				final double y1 = y0 + childBounds.getHeight();
				minX = Math.min(minX, x0);
				minY = Math.min(minY, y0);
				maxX = Math.max(maxX, x1);
				maxY = Math.max(maxY, y1);
			}
		}
		if (Double.isInfinite(maxY)) {
			System.err.println("Alignment.getChildrenGlobalBounds: No visible, partially opaque, rooted,"
					+ " non-empty-text, finitely-bounded virtual children among ");
			for (final PNode element : virtualChildren) {
				System.err.println("   "+PiccoloUtil.ancestorString(element));
			}
			System.err.println(UtilString.getStackTrace());
			minX = 0.0;
			minY = 0.0;
			maxX = 1.0;
			maxY = 1.0;
		}
		final PBounds pBounds = new PBounds((minX - xMargin), (minY - yMargin), (maxX - minX + 2.0 * xMargin),
				(maxY - minY + 2.0 * yMargin));
		assert !pBounds.isEmpty() : minX + "-" + maxX + " " + xMargin + " " + minY + "-" + maxY + " " + yMargin;
		return pBounds;
	}

	/**
	 * @return union of virtualChildren's bounds + offset, plus margins on all
	 *         four sides.
	 */
	public static @NonNull Rectangle2D getChildrenBounds(final @NonNull PNode pNode, final double xMargin,
			final double yMargin) {
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (final Iterator<PNode> it = pNode.getChildrenIterator(); it.hasNext();) {
			final PNode child = it.next();
			if (child.getVisible() && child.getTransparency() > 0f
					&& (!(child instanceof APText) || ((APText) child).getText().trim().length() > 0)) {
				final PBounds childBounds = child.getBounds();
				final Point2D childOffset = child.getOffset();
				final double x0 = childBounds.getX() + childOffset.getX();
				final double x1 = x0 + childBounds.getWidth();
				final double y0 = childBounds.getY() + childOffset.getY();
				final double y1 = y0 + childBounds.getHeight();
				minX = Math.min(minX, x0);
				minY = Math.min(minY, y0);
				maxX = Math.max(maxX, x1);
				maxY = Math.max(maxY, y1);
			}
		}
		if (Double.isInfinite(maxY)) {
			System.err.println(
					"Alignment.getChildrenGlobalBounds: " + pNode + " has no visible, partially opaque, rooted,"
							+ " non-empty-text, finitely-bounded virtual children" + UtilString.getStackTrace());
			minX = 0.0;
			minY = 0.0;
			maxX = 1.0;
			maxY = 1.0;
		}
		final PBounds pBounds = new PBounds((minX - xMargin), (minY - yMargin), (maxX - minX + 2.0 * xMargin),
				(maxY - minY + 2.0 * yMargin));
		assert !pBounds.isEmpty() : minX + "-" + maxX + " " + xMargin + " " + minY + "-" + maxY + " " + yMargin;
		return pBounds;
	}

	private transient final @NonNull List<PActivity> animationJobs = new ArrayList<>();

	/**
	 * This doesn't schedule anything; it just means maxFinishTime() and
	 * terminateAnimationJobs() will take it into account.
	 */
	public void trackAnimationJob(final @Nullable PActivity job) {
		if (job != null) {
			animationJobs.add(job);
		}
	}

	/**
	 * @return the maximum finish time of all the tracked animation jobs.
	 *
	 */
	public long maxFinishTime() {
		long result = System.currentTimeMillis();
		for (final PActivity job : animationJobs) {
			final long stopTime = job.getStopTime();
			assert stopTime > 0L : "getStopTime < 0 for " + job
					+ "\nYou are probably the victim of arithmetic overflow."
					+ " Try not to schedule jobs with duartion near Long.MAX_VALUE.";
			result = Math.max(result, stopTime);
		}
		return result;
	}

	public void terminateAnimationJobs() {
		if (!isTerminatingAnimations) {
			try {
				isTerminatingAnimations = true;
				for (final Iterator<PActivity> it = animationJobs.iterator(); it.hasNext();) {
					final PActivity job = it.next();
					// remove first to avoid infinite recursion
					it.remove();
					job.terminate(PActivity.TERMINATE_AND_FINISH);
				}
			} catch (final Throwable e) {
				e.printStackTrace();
			} finally {
				isTerminatingAnimations = false;
			}
		}
	}

	/**
	 * Ignore recursive calls (via terminate)
	 */
	private boolean isTerminatingAnimations = false;

	public boolean isTerminatingAnimations() {
		return isTerminatingAnimations;
	}

	public void scaleAboutPoint(final @NonNull PNode node, final int point, double scale, final long duration) {

		// scale to an absolute factor
		scale = scale / node.getScale();

		final PBounds bounds = node.getBounds();
		assert bounds != null;
		final double x = pointX(bounds, point);
		final double y = pointY(bounds, point);
		final PAffineTransform xform = node.getTransform();
		xform.translate(x, y);
		xform.scale(scale, scale);
		xform.translate(-x, -y);
		trackAnimationJob(node.animateToTransform(xform, duration));
	}

}
