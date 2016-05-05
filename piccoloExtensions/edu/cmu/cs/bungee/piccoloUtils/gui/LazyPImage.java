package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.event.EventListenerList;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode.PickableMode;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PColorActivity.Target;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;
import edu.umd.cs.piccolo.event.PInputEventListener;
import edu.umd.cs.piccolo.nodes.PImage;
import edu.umd.cs.piccolo.util.PBounds;

public class LazyPImage extends PImage implements Target, LazyNode {

	public LazyPImage() {
	}

	// TODO Remove unused code found by UCDetector
	// public static final double MINIMUM_VISIBLE_SIZE = 0.3;

	private @NonNull PickableMode pickableMode = PickableMode.PICKABLE_MODE_AUTOMATIC;

	@Override
	public PNode pNode() {
		return this;
	}

	@Override
	public void setMouseDoc(final String doc) {
		if (getParent() instanceof MouseDoc) {
			((MouseDoc) getParent()).setMouseDoc(doc);
		}
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
	public void setVisible(final boolean state) {
		if (getVisible() != state) {
			super.setVisible(state);
			setPickableMode(pickableMode);
		}
	}

	public PickableMode getPickableMode() {
		return pickableMode;
	}

	@Override
	public void setPickableMode(final PickableMode _pickableMode) {
		pickableMode = _pickableMode;
		final boolean pickableState = (pickableMode == PickableMode.PICKABLE_MODE_AUTOMATIC) ? getVisible()
				: pickableMode == PickableMode.PICKABLE_MODE_ALWAYS;
		setPickable(pickableState);
		setChildrenPickable(pickableState);
	}

	@Override
	public void setOffset(final double x, final double y) {
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
	public PInterpolatingActivity animateToBounds(final Rectangle2D bounds, final long duration) {
		return animateToBounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), duration);
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
			scale(scale / getScale());
		}
	}

	@Override
	public void setCenterX(final double x) {
		setXoffset(x - getWidth() * getScale() / 2.0);
	}

	@Override
	public void setCenterY(final double y) {
		setXoffset(y - getHeight() * getScale() / 2.0);
	}

	@Override
	public void setCenter(final Point2D.Double point) {
		setCenterX(point.getX());
		setCenterY(point.getY());
	}

	@Override
	public Point2D.Double getCenter() {
		return new Point2D.Double(getCenterX(), getCenterY());
	}

	@Override
	public double getCenterX() {
		return getXOffset() + getWidth() * getScale() / 2.0;
	}

	@Override
	public double getCenterY() {
		return getYOffset() + getHeight() * getScale() / 2.0;
	}

	@Override
	public double getMaxX() {
		return getX() + getXOffset() + getWidth() * getScale();
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
		return getY() + getYOffset() + getHeight() * getScale();
	}

	@Override
	public double getIntMaxY() {
		return Math.rint(getMaxY());
	}

	@Override
	public boolean setBoundsFromFullBounds() {
		final PBounds bounds = getFullBoundsReference();
		return setBounds(Math.rint(bounds.getX()), Math.rint(bounds.getY()), Math.rint(bounds.getWidth()),
				Math.rint(bounds.getHeight()));
	}

	// TODO Remove unused code found by UCDetector
	// protected boolean setBoundsFromChildrenBounds() {
	// final PBounds bounds = getUnionOfChildrenBounds(null);
	// return setBounds(Math.rint(bounds.getX()), Math.rint(bounds.getY()),
	// Math.rint(bounds.getWidth()),
	// Math.rint(bounds.getHeight()));
	// }

	@Override
	public void repaintNow() {
		validateFullBounds();
		validateFullPaint();
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return the minimum width this node requires
	// */
	// public double minWidth() {
	// assert false : "Should override LazyPNode.minWidth";
	// return getWidth();
	// }

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return the maximum width this node can handle
	// */
	// public double maxWidth() {
	// assert false : "Should override LazyPNode.maxWidth: " +
	// PiccoloUtil.ancestorString(this);
	// return getWidth();
	// }

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return the minimum height this node requires
	// */
	// public double minHeight() {
	// assert false : "Should override LazyPNode.minHeight";
	// return getHeight();
	// }

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return the maximum height this node can handle
	// */
	// public double maxHeight() {
	// assert false : "Should override LazyPNode.maxHeight";
	// return getHeight();
	// }

	@Override
	public void addChild(final PNode child) {
		assert child != null;
		assert edu.cmu.cs.bungee.javaExtensions.Util.assertMouseProcess();
		if (child.getParent() != this) {
			super.addChild(child);
		}
	}

	@Override
	public void moveInBackOf(final PNode sibling) {
		final PNode parent = getParent();
		if (parent != null && parent.indexOfChild(this) > parent.indexOfChild(sibling)) {
			super.moveInBackOf(sibling);
		}
	}

	@Override
	public void moveInFrontOf(final PNode sibling) {
		final PNode parent = getParent();
		if (parent != null && parent.indexOfChild(this) < parent.indexOfChild(sibling)) {
			super.moveInFrontOf(sibling);
		}
	}

	@Override
	public void moveToFront() {
		final PNode parent = getParent();
		if (parent != null && parent.getChild(parent.getChildrenCount() - 1) != this) {
			super.moveToFront();
		}
	}

	@Override
	public void moveAncestorsToFront() {
		edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil.moveAncestorsToFront(this);
	}

	@Override
	public void moveToBack() {
		final PNode p = getParent();
		if (p != null && p.getChild(0) != this) {
			super.moveToBack();
		}
	}

	@Override
	public Color getColor() {
		return (Color) getPaint();
	}

	@Override
	public void setColor(final Color color) {
		setPaint(color);
	}

	public PNode[] getChildrenAsPNodeArray() {
		final PNode[] children = new PNode[getChildrenCount()];
		int i = 0;
		for (final Iterator<PNode> it = getChildrenIterator(); it.hasNext(); i++) {
			children[i] = it.next();
		}
		return children;
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
	public String nodeDesc() {
		return "";
	}

}
