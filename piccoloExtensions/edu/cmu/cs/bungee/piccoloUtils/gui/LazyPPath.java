/*

Created on Mar 10, 2006

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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.event.EventListenerList;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode.PickableMode;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;
import edu.umd.cs.piccolo.event.PInputEventListener;
import edu.umd.cs.piccolo.nodes.PPath;
import edu.umd.cs.piccolo.util.PBounds;

public class LazyPPath extends PPath implements LazyNode {

	@Override
	public PInterpolatingActivity animateToBounds(final Rectangle2D bounds, final long duration) {
		return animateToBounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), duration);
	}

	private static final @NonNull BasicStroke[] STROKE_CACHE = new BasicStroke[11];

	public LazyPPath() {
		super(new GeneralPath(), null);
	}

	@Override
	public @NonNull PNode pNode() {
		return this;
	}

	@Override
	public void setMouseDoc(final String doc) {
		if (getParent() instanceof MouseDoc) {
			((MouseDoc) getParent()).setMouseDoc(doc);
		}
	}

	private @NonNull PickableMode pickableMode = PickableMode.PICKABLE_MODE_AUTOMATIC;

	@Override
	public void setPickableMode(final @NonNull PickableMode _pickableMode) {
		pickableMode = _pickableMode;
		final boolean pickableState = (pickableMode == PickableMode.PICKABLE_MODE_AUTOMATIC) ? getVisible()
				: (pickableMode == PickableMode.PICKABLE_MODE_NEVER) ? false : true;
		setPickable(pickableState);
		setChildrenPickable(pickableState);
	}

	public boolean setMyTransparency(final float newTransparency) {
		final boolean result = newTransparency != getTransparency();
		if (result) {
			setTransparency(newTransparency);
		}
		return result;
	}

	public boolean setMyPaint(final Paint newPaint) {
		final boolean result = newPaint != getPaint();
		if (result) {
			setPaint(newPaint);
		}
		return result;
	}

	@Override
	public void setVisible(final boolean state) {
		if (getVisible() != state) {
			super.setVisible(state);
			setPickableMode(pickableMode);
		}
	}

	@Override
	public void setOffset(final double x, final double y) {
		assert x == Math.rint(x);
		assert y == Math.rint(y);
		if (x != getXOffset() || y != getYOffset()) {
			super.setOffset(x, y);
		}
	}

	@Override
	public void setXoffset(final double x) {
		setOffset(x, getYOffset());
	}

	@Override
	public void setYoffset(final double y) {
		setOffset(getXOffset(), y);
	}

	@Override
	public boolean setWidthHeight(final double w, final double h) {
		return setBounds(getX(), getY(), w, h);
	}

	@Override
	public void setScale(final double xScale, final double yScale) {
		if (xScale != getXscale() || yScale != getYscale()) {
			getTransformReference(true).scale(xScale, yScale);
			invalidatePaint();
			invalidateFullBounds();
			firePropertyChange(PROPERTY_CODE_TRANSFORM, PROPERTY_TRANSFORM, null, getTransformReference(true));
		}
	}

	@Override
	public double getXscale() {
		return getTransformReference(true).getScaleX();
	}

	@Override
	public double getYscale() {
		return getTransformReference(true).getScaleY();
	}

	@Override
	public void setScale(final double scale) {
		if (scale != getScale()) {
			super.setScale(scale);
		}
	}

	@Override
	public void setCenterX(final double x) {
		setXoffset(x - getWidth() * getScale() / 2.0);
	}

	@Override
	public void setCenterY(final double y) {
		setYoffset(y - getHeight() * getScale() / 2.0);
	}

	@Override
	public void setCenter(final @NonNull Point2D.Double point) {
		setCenterX(point.getX());
		setCenterY(point.getY());
	}

	@Override
	public @NonNull Point2D.Double getCenter() {
		return new Point2D.Double(getCenterX(), getCenterY());
	}

	@Override
	public void removeChildren(final Collection childrenNodes) {
		if (!childrenNodes.isEmpty()) {
			final Iterator<LazyNode> i = childrenNodes.iterator();
			while (i.hasNext()) {
				final PNode each = (PNode) i.next();
				each.setParent(null);
			}
			final List<LazyNode> _children = getChildrenReference();
			_children.removeAll(childrenNodes);
			invalidatePaint();
			invalidateFullBounds();

			firePropertyChange(PROPERTY_CODE_CHILDREN, PROPERTY_CHILDREN, null, _children);
		}
	}

	@Override
	public void addChildren(final Collection childrenNodes) {
		if (!childrenNodes.isEmpty()) {
			final List<PNode> _children = getChildrenReference();
			final Iterator<PNode> it = childrenNodes.iterator();
			while (it.hasNext()) {
				final PNode each = it.next();
				assert each.getParent() == null : each;
				each.setParent(this);
			}
			_children.addAll(childrenNodes);
			invalidatePaint();
			invalidateFullBounds();

			firePropertyChange(PROPERTY_CODE_CHILDREN, PROPERTY_CHILDREN, null, _children);
		}
	}

	@Override
	public void setStrokePaint(final @Nullable Paint aPaint) {
		if (aPaint == null) {
			if (getStrokePaint() != null) {
				super.setStrokePaint(null);
			}
		} else if (!aPaint.equals(getStrokePaint())) {
			super.setStrokePaint(aPaint);
		}
	}

	@Override
	public void setStroke(final @Nullable Stroke aStroke) {
		if (aStroke == null) {
			if (getStroke() != null) {
				super.setStroke(null);
			}
		} else if (!aStroke.equals(getStroke())) {
			super.setStroke(aStroke);
		}
	}

	public static @NonNull LazyPPath createEllipse(final float x, final float y, final float width, final float height,
			final int strokeW, final @NonNull Color bgColor) {
		final LazyPPath path = new LazyPPath();
		path.setStroke(getStrokeInstance(strokeW));
		path.setPaint(bgColor);
		path.setPathToEllipse(x, y, width, height);
		return path;
	}

	public static @NonNull LazyPPath createLine(final float x1, final float y1, // NO_UCD
			// (unused
			// code)
			final float x2, final float y2, final int strokeW, final @NonNull Paint blockOutlineColor) {
		final LazyPPath path = new LazyPPath();
		path.setStroke(getStrokeInstance(strokeW));
		path.setStrokePaint(blockOutlineColor);
		final float[] Xs = { x1, x2 };
		final float[] Ys = { y1, y2 };
		path.setPathToPolyline(Xs, Ys);
		return path;
	}

	@Override
	public final @NonNull Object clone() {
		final LazyPPath result = (LazyPPath) super.clone();
		result.setPaint(getPaint());
		result.setStroke(getStroke());
		result.setStrokePaint(getStrokePaint());
		result.setPathTo(getPathReference());
		return result;
	}

	public static @NonNull Stroke getStrokeInstance(final int i) {
		assert i >= 0;
		if (i >= STROKE_CACHE.length) {
			return new BasicStroke(i);
		}
		BasicStroke result = STROKE_CACHE[i];
		if (result == null) {
			result = new BasicStroke(i);
			STROKE_CACHE[i] = result;
		}
		return result;
	}

	/*
	 * This is more efficient when the Stroke is a BasicStroke, which we assume
	 * it always will be.
	 */
	@Override
	public @NonNull Rectangle2D getPathBoundsWithStroke() {
		final Rectangle2D rect = getPathReference().getBounds2D();
		if (getStroke() != null) {
			final double dx = ((BasicStroke) getStroke()).getLineWidth();
			rect.setRect(rect.getX() - dx / 2.0, rect.getY() - dx / 2.0, rect.getWidth() + dx, rect.getHeight() + dx);
		}
		assert rect != null;
		return rect;
	}

	@Override
	public double getCenterX() {
		return getBoundsReference().getCenterX();
	}

	@Override
	public double getCenterY() {
		return getBoundsReference().getCenterY();
	}

	@Override
	public double getMaxX() {
		return getXOffset() + getBoundsReference().getMaxX();
	}

	@Override
	public double getGlobalMaxX() {
		final PBounds globalBounds = getGlobalBounds();
		return globalBounds.getX() + globalBounds.getWidth();
	}

	@Override
	public double getIntMaxX() {
		return Math.rint(getMaxX());
	}

	@Override
	public double getMaxY() {
		return getYOffset() + getBoundsReference().getMaxY();
	}

	@Override
	public double getIntMaxY() {
		return Math.rint(getMaxY());
	}

	@Override
	public boolean setBoundsFromFullBounds() {
		return setBounds(getFullBounds());
	}

	@Override
	public void moveAncestorsToFront() {
		edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil.moveAncestorsToFront(this);
	}

	@Override
	public void repaintNow() {
		validateFullBounds();
		validateFullPaint();
	}

	@Override
	public void removeInputListeners() {
		final EventListenerList listenerList = getListenerList();
		final PInputEventListener[] listeners = listenerList.getListeners(PInputEventListener.class);
		for (final PInputEventListener listener : listeners) {
			removeInputEventListener(listener);
		}
	}

	@Override
	public @NonNull String nodeDesc() {
		return "";
	}

}
