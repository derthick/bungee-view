package edu.cmu.cs.bungee.client.viz.grid;

import java.awt.Color;
import java.awt.Paint;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.DefaultLabeledBungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.grid.PerspectiveVScrollLabeled.BoxedText;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.umd.cs.piccolo.PNode;

/**
 * Our height is functionally what DefaultLabeledPNode considers width.
 */
class PerspectiveVScrollLabeled extends DefaultLabeledBungee<Perspective, BoxedText> {

	private final @NonNull Bungee art;

	/**
	 * Represents records that have no value for sortedBy
	 */
	private @NonNull DummyPerspective dummyPerspective;

	PerspectiveVScrollLabeled(final @NonNull Bungee _art, final @NonNull Perspective sortedBy,
			final @NonNull LazyPNode _parentPNode) {
		super((int) _parentPNode.getHeight(), labelW(_art), sortedBy, _parentPNode);
		art = _art;
		dummyPerspective = getDummyPerspective();
		updateDummyPerspective();

		// Need labels visible so validate will work, but don't want user to see
		// them until moused over.
		_parentPNode.setVisible(false);
		validate();
	}

	@SuppressWarnings({ "null", "unused" })
	@NonNull
	DummyPerspective getDummyPerspective() {
		if (dummyPerspective == null) {
			dummyPerspective = new DummyPerspective(parent);
		}
		return dummyPerspective;
	}

	// @SuppressWarnings({ "null", "unused" })
	// private @NonNull Perspective getDummyPerspective() {
	// Perspective _dummyPerspective = dummyPerspective;
	// if (_dummyPerspective == null) {
	// _dummyPerspective = new Perspective(-1, "<dummy>", -1, 0,
	// DescriptionCategory.OBJECT, " ; ", parent.query(),
	// 0, 0);
	// }
	// return _dummyPerspective;
	// }

	@SuppressWarnings({})
	private void updateDummyPerspective() {
		getDummyPerspective();
		dummyPerspective.setName("<no " + parent.getName() + ">");
		dummyPerspective.setOnCount(query().getOnCount() - parent.getOnCount());
		dummyPerspective.setTotalCount(query().getTotalCount() - parent.getTotalCount());
		dummyPerspective.setDummyParent(parent, parent.nChildrenRaw());
	}

	// setWidth of parentPNode and all BoxedTexts to the maximum of the their
	// widths, and moveToFront() those whose height is less than their label's
	// height.
	@Override
	protected void validate() {
		if (!query().isQueryValid()) {
			query().queueRedraw(this);
		} else {
			// setCountsHashInvalid();
			super.validate();
			if (parentPNode.getVisible()) {
				assert assertTotalChildCountOK();

				try {
					double maxLabelW = 0.0;
					// final List<LazyPNode> moveToFront = new LinkedList<>();
					for (final Iterator<BoxedText> it = parentPNode.getChildrenIterator(); it.hasNext();) {
						final BoxedText box = it.next();
						maxLabelW = Math.max(maxLabelW, box.getWidth());
						if (box.getHeight() < box.label.getHeight()) {
							box.moveToFront();
							// Why wait until later?
							// moveToFront.add(box);
						}
					}
					for (final Iterator<BoxedText> it = parentPNode.getChildrenIterator(); it.hasNext();) {
						it.next().setWidth(maxLabelW);
					}
					// for (final LazyPNode box : moveToFront) {
					// box.moveToFront();
					// }
					parentPNode.setWidth(maxLabelW + art().lineH());
				} catch (final ConcurrentModificationException e) {
					// Quicker to ignore error than synch on adding/removing
					// children.
				}
			}
		}
	}

	/**
	 * count() returns child.onCount for real children plus (query.onCount -
	 * sortedBy.onCount) for dummyPerspective. This gives the correct count for
	 * dummyPerspective, but over-counts items with multiple parents. Thus the
	 * totalChildCount can be greater than query.onCount.
	 */
	protected boolean assertTotalChildCountOK() {
		assert totalChildCount >= query().getOnCount() : this + "\n totalChildCount="
				+ UtilString.addCommas(totalChildCount) + "\n p.getTotalChildOnCount()="
				+ UtilString.addCommas(parent.getTotalChildOnCount()) + "\n query.getOnCount()="
				+ UtilString.addCommas(query().getOnCount());
		return true;
	}

	@Override
	protected boolean setLogicalWperItem(final double _leftEdge, final double _logicalWidth,
			final int _totalChildCount) {
		final boolean result = super.setLogicalWperItem(_leftEdge, _logicalWidth, _totalChildCount);
		assert !isZoomed() : this + " leftEdge=" + leftEdge + " visibleWidth=" + visibleWidth + " logicalWidth="
				+ logicalWidth;
		return result;
	}

	@Override
	public void setWidth(final double width) {
		super.setWidth(width);
		if (logicalWidth != visibleWidth) {
			setLogicalBoundsAndValidateIfChanged(leftEdge, visibleWidth);
			assert UtilMath.approxEqualsAbsolute(logicalWidth, visibleWidth) : this + " visibleWidth=" + visibleWidth
					+ " logicalWidth=" + logicalWidth;
		}
	}

	@Override
	protected @NonNull Object getCountsHash() {
		final Object[] result = { parent, query().version() };
		return result;
	}

	@SuppressWarnings("null")
	private boolean isInitted() {
		return parentPNode != null;
	}

	@Override
	public Perspective setParent(final @NonNull Perspective sortedBy) {
		final Perspective result = super.setParent(sortedBy);
		updateDummyPerspective();
		if (isInitted()) {
			validate();
		}
		return result;
	}

	@Override
	protected void addBoundsChangedListener() {
		parentPNode.addPropertyChangeListener(PNode.PROPERTY_BOUNDS, new PropertyChangeListener() {
			@Override
			public void propertyChange(@SuppressWarnings("unused") final PropertyChangeEvent evt) {
				setWidth(parentPNode.getHeight());
			}
		});
	}

	static int labelW(final Bungee art) {
		return (int) art.lineH();
	}

	@Override
	@NonNull
	protected Bungee art() {
		return art;
	}

	/**
	 * Perspectives queued for NameGetter, but which have not been verified
	 * named by allNamed
	 */
	private final Collection<Perspective> unnameds = new LinkedList<>();

	@Override
	public List<Perspective> getChildren() {
		final List<Perspective> children2 = new ArrayList<>(parent.nChildrenRaw() + 1);
		children2.addAll(parent.getChildrenRaw());
		updateDummyPerspective();
		children2.add(dummyPerspective);
		return children2;
	}

	@Override
	protected int childIndex(final @Nullable Perspective child) {
		return child != null ? child.whichChildRaw() : -1;
	}

	/**
	 * @return always >= 0
	 */
	@Override
	public int count(final @NonNull Perspective facet) {
		return facet.getOnCount();
	}

	@SuppressWarnings("null")
	@Override
	public void updateCounts() {
		if (parentPNode != null && parentPNode.getVisible()) {
			super.updateCounts();
			assertTotalChildCountOK();
		}
	}

	@Override
	protected @Nullable BoxedText getLabel(final Perspective facet, final int from, final int to) {
		BoxedText label1 = null;
		final String text = facet.getNameOrDefaultAndCallback(null, this);
		if (text == null) {
			// Don't cache empty label; wait for callback
			unnameds.add(facet);
		} else {
			label1 = getFromCache(facet);
			if (label1 == null) {
				label1 = new BoxedText(text, art());
			} else {
				label1.setText(text);
			}
			final Color facetColor = art().facetColor(facet);
			label1.setTextPaint(facetColor);
			label1.setPosition(from, to, parentPNode);
			assert label1.getParent() == null : label1;
		}
		return label1;
	}

	@Override
	protected Perspective perspective() {
		return parent;
	}

	/**
	 * parentPNode.setVisible(state)
	 */
	public void setVisible(final boolean state) {
		parentPNode.setVisible(state);
	}

	static class BoxedText extends LazyPNode {

		final APText label;

		BoxedText(final String text, final Bungee art) {
			label = art.oneLineLabel();
			setPaint(BungeeConstants.GRID_SCROLL_BG_COLOR);
			label.maybeSetText(text);
			addChild(label);
		}

		public void setTextPaint(final Paint paint) {
			label.setTextPaint(paint);
		}

		public void setText(final String text) {
			label.setConstrainWidthToTextWidth(true);
			label.setText(text);
			label.setConstrainWidthToTextWidth(false);
		}

		void setPosition(final int from, final int to, final PNode parentPNode) {
			setOffset(parentPNode.getX(), parentPNode.getY() + from + 1.0);
			final int boxH = Math.max(0, to - from - 2);
			setBounds(0.0, 0.0, label.getWidth(), boxH);
			label.setCenterY(boxH / 2.0);
		}

		@Override
		public String toString() {
			return UtilString.toString(this, label.getText());

		}
	}

	class DummyPerspective extends Perspective {

		private int onCount;
		private int totalCount;

		public DummyPerspective(final @NonNull Perspective _parent) {
			super(_parent);
		}

		void setDummyParent(final @NonNull Perspective _parent, final int _whichChild) {
			parent = _parent;
			whichChildRaw = _whichChild;
		}

		@Override
		public int getOnCount() {
			assert totalCount >= onCount && onCount >= 0;
			return onCount;
		}

		@Override
		public int getTotalCount() {
			assert totalCount >= onCount && totalCount <= parentTotalCount();
			return totalCount;
		}

		@Override
		public void setOnCount(final int _onCount) {
			assert _onCount >= 0;
			onCount = _onCount;
		}

		@Override
		public void setTotalCount(final int count) {
			assert count >= 0;
			totalCount = count;
		}

		@Override
		public void setName(final @NonNull String _name) {
			name = _name;
		}

	}

}
