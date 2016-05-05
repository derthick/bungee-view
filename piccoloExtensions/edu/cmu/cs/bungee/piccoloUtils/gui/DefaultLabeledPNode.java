package edu.cmu.cs.bungee.piccoloUtils.gui;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.DefaultLabeled;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.nodes.PText;

public abstract class DefaultLabeledPNode<V, Z extends PNode> extends DefaultLabeled<V> implements RedrawCallback {

	public final @NonNull LazyPNode parentPNode;

	/**
	 * The [displayed] Z Label representing a V. If isKeepCache(), entries are
	 * copied to zPool before being removed from parent.
	 */
	protected final @NonNull Map<V, Z> facetToLabelTable = new Hashtable<>(100);

	private final PNodePool<V, Z> zPool;

	/**
	 * If validate is called when invisible, set this flag and return. Then a
	 * PropertyChangeListener will validate when when this becomes visible and
	 * reset the flag.
	 */
	boolean mustValidateWhenVisible = false;
	boolean mustSetLogicalBoundsWhenVisible = false;

	public DefaultLabeledPNode(final int width, final int labelW, final @NonNull V _parentV,
			final @NonNull LazyPNode _parentPNode, final boolean _isKeepCache, final boolean isInterchangableCache) {
		super(_parentV, width, labelW);
		parentPNode = _parentPNode;
		assert _isKeepCache || !isInterchangableCache;
		zPool = _isKeepCache ? new PNodePool<V, Z>(isInterchangableCache) : null;
		parentPNode.addPropertyChangeListener(PNode.PROPERTY_VISIBLE, new PropertyChangeListener() {
			@Override
			public void propertyChange(@SuppressWarnings("unused") final PropertyChangeEvent evt) {
				if (mustSetLogicalBoundsWhenVisible || mustValidateWhenVisible) {
					validate();
					if (totalChildCount > 0) {
						if (mustSetLogicalBoundsWhenVisible) {
							final double _leftEdge = leftEdge;
							leftEdge = -1.0;
							setIntLogicalBounds(_leftEdge, logicalWidth, totalChildCount);
						}
					}
				}
			}
		});
		addBoundsChangedListener();
	}

	/**
	 * Separate this out so that PerspectiveVScrollLabeled can override it.
	 */
	protected void addBoundsChangedListener() {
		parentPNode.addPropertyChangeListener(PNode.PROPERTY_BOUNDS, new PropertyChangeListener() {
			@Override
			public void propertyChange(@SuppressWarnings("unused") final PropertyChangeEvent evt) {
				setWidth(parentPNode.getWidth());
			}
		});
	}

	@SuppressWarnings("null")
	@Override
	public void resetParent() {
		if (parentPNode == null || parentPNode.getVisible()) {
			// In case we're not fully initialized
			super.resetParent();
		} else {
			mustValidateWhenVisible = true;
		}
	}

	@Override
	public void redrawCallback() {
		validate();
	}

	@Override
	protected void validate() {
		final boolean isParentVisible = parentPNode.getVisible();
		mustValidateWhenVisible = !isParentVisible;
		if (isParentVisible) {
			super.validate();
		}
	}

	@Override
	public void setLogicalBoundsAndValidateIfChanged(final double _leftEdge, final double _logicalWidth) {
		if (parentPNode.getVisible()) {
			super.setLogicalBoundsAndValidateIfChanged(_leftEdge, _logicalWidth);
		} else if (_leftEdge != leftEdge || _logicalWidth != logicalWidth) {
			leftEdge = _leftEdge;
			logicalWidth = _logicalWidth;
			mustSetLogicalBoundsWhenVisible = true;
		}
	}

	@Override
	public boolean setIntLogicalBounds(final double _leftEdge, final double _logicalWidth, final int _totalChildCount) {
		mustSetLogicalBoundsWhenVisible = false;
		return super.setIntLogicalBounds(_leftEdge, _logicalWidth, _totalChildCount);
	}

	public void setFont(final Font font, final int labelW) {
		assert font != getFont() : font;
		removeAllLabels(true);
		super.setLabelW(labelW);
	}

	private @Nullable Font getFont() {
		Font result = null;
		final @Nullable Z label = UtilArray.some(getAllLabels());
		if (label != null) {
			assert label instanceof PText;
			result = ((PText) label).getFont();
		}
		return result;
	}

	@SuppressWarnings("null")
	public void removeAllLabels(final boolean isClearZcache) {
		if (facetToLabelTable != null) {
			// In case we're not fully initialized

			final Collection<Z> labels = getAllLabels();
			assert assertAllLabelsAreChildren();
			final List<PNode> nonLabelChildren = new ArrayList<>(parentPNode.getChildrenReference());
			nonLabelChildren.removeAll(labels);
			parentPNode.removeAllChildren();
			parentPNode.addChildren(nonLabelChildren);

			if (isKeepCache()) {
				if (isClearZcache) {
					zPool.clear();
				} else {
					zPool.putAll(facetToLabelTable);
				}
			}
			facetToLabelTable.clear();
		}
	}

	protected @Nullable Z getFromCache(final @NonNull V v) {
		assert isKeepCache();
		return zPool.pop(v);
	}

	// TODO Remove unused code found by UCDetector
	// protected @Nullable Z someFromCache() {
	// return zPool.some();
	// }

	/**
	 * @return zPool != null
	 */
	private boolean isKeepCache() {
		return zPool != null;
	}

	protected boolean assertAllLabelsAreChildren() {
		for (final Entry<V, Z> entry : facetToLabelTable.entrySet()) {
			final Z z = entry.getValue();
			assert z.getParent() == parentPNode : this + " " + z + " " + z.getParent() + ", but " + parentPNode
					+ " was expected. entry.getKey()=" + entry.getKey();
		}
		return true;
	}

	public @NonNull Collection<Z> getAllLabels() {
		final Collection<Z> result = facetToLabelTable.values();
		assert result != null;
		return result;
	}

	// protected void setForceLabels(final @NonNull Set<V> priorityVs) {
	// forceLabels = priorityVs;
	// }

	public Z lookupLabel(final V v) {
		return facetToLabelTable.get(v);
	}

	@Override
	protected void computeNdrawLabels() {
		assert parentPNode.getVisible() : parentPNode;
		super.computeNdrawLabels();
	}

	private final Collection<PNode> labelsToDraw = new LinkedList<>();

	@Override
	protected void drawComputedLabels() {
		removeAllLabels(false);
		assert labelsToDraw.isEmpty();
		super.drawComputedLabels();
		parentPNode.addChildren(labelsToDraw);
		labelsToDraw.clear();
	}

	@Override
	public void drawLabel(final @NonNull V v, final int[] pixelRange) {
		final int from = pixelRange[0];
		final int to = pixelRange[1];
		final int iMidX = (from + to) / 2;
		assert isInRange(iMidX, 0, iVisibleWidth - 1) : v + " " + iMidX + " " + iVisibleWidth;
		assert labelXs[iMidX] == childIndex(v) : "labelXs[" + iMidX + "]=" + labelXs[iMidX] + " childIndex(v)="
				+ childIndex(v) + " v=" + v + " " + from + " - " + to + "\n" + formatLabelXs();
		final @Nullable Z drawnLabel = getLabel(v, from, to);
		if (drawnLabel != null) {
			assert drawnLabel.getParent() == null : drawnLabel;
			labelsToDraw.add(drawnLabel);
			UtilArray.putNew(facetToLabelTable, v, drawnLabel, this);
		}
	}

	abstract protected @Nullable Z getLabel(final @NonNull V v, final int from, final int to);

	@Override
	public String toString() {
		return UtilString.toString(this, "parent='" + parent + "' visibleW=" + visibleWidth);
	}
}
