package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode.PickableMode;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;
import edu.umd.cs.piccolo.event.PInputEventListener;
import edu.umd.cs.piccolo.util.PBounds;

public interface LazyNode {

	public @NonNull PNode pNode();

	public void setMouseDoc(final String doc);

	public String nodeDesc();

	public void setPickableMode(@NonNull PickableMode _pickableMode);

	public boolean setBoundsFromFullBounds();

	public PInterpolatingActivity animateToBounds(final Rectangle2D bounds, final long duration);

	public PBounds getGlobalFullBounds();

	public PBounds getBounds();

	public double getHeight();

	public double getWidth();

	public boolean setWidthHeight(final double w, final double h);

	public double getXOffset();

	public double getYOffset();

	public void setXoffset(double x);

	public void setYoffset(double y);

	public void setOffset(double xOffset, double yOffset);

	double getScale();

	public double getGlobalScale();

	public void setScale(double xScale, double yScale);

	public double getXscale();

	public double getYscale();

	// public void setCenter(double x, double y); // NO_UCD (unused code)

	public void setCenterX(double x);

	public void setCenterY(double y);

	public void setCenter(@NonNull Point2D.Double point);

	public Point2D.Double getCenter();

	/**
	 * @return x-coordinate of center in parents coordinate system
	 */
	public double getCenterX();

	/**
	 * @return y-coordinate of center in parents coordinate system
	 */
	public double getCenterY();

	/**
	 * @return x-coordinate of right edge in parents coordinate system
	 */
	public double getMaxX();

	/**
	 * @return x-coordinate of right edge in parents coordinate system
	 */
	public double getGlobalMaxX();

	public double getIntMaxX();

	/**
	 * @return y-coordinate of bottom edge in parents coordinate system
	 */
	public double getMaxY();

	public double getIntMaxY();

	/**
	 * Don't wait for normal damage control
	 */
	public void repaintNow();

	public void moveAncestorsToFront();

	void addInputEventListener(PInputEventListener listener);

	public void removeInputListeners();

	public PNode getParent();

	void addChild(PNode pNode);

	public int getChildrenCount();

	public Object getRoot();

	public Paint getPaint();

	public boolean getPaintInvalid();

	public float getTransparency();

	public boolean getVisible();

	public void setTransform(AffineTransform affineTransform);

	public PBounds getGlobalBounds();

}