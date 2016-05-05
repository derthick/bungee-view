package edu.cmu.cs.bungee.client.viz.tagWall;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.markup.DefaultFacetNode;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPPath;
import edu.umd.cs.piccolo.util.PPaintContext;

/**
 * y-coordinates range from 0 if observedPercent = 1.0 to 1 if observedPercent =
 * 0.0, except that rect extends an extra MIN_BAR_HEIGHT/2 above and below.
 */
public final class Bar extends DefaultFacetNode {

	private static final double MIN_BAR_HEIGHT = 0.02;
	private static final double MIDPOINT_MINUS_HALF_MIN_BAR_HEIGHT = 0.5 - MIN_BAR_HEIGHT / 2.0;
	private static final double MIDPOINT_PLUS_HALF_MIN_BAR_HEIGHT = 0.5 + MIN_BAR_HEIGHT / 2.0;

	private final @NonNull PerspectiveViz pv;

	/**
	 * Used to paint efficiently
	 */
	private final @NonNull Rectangle2D.Double rect;

	private double startY = 0.5;

	private double goalY = 0.5;

	private double currentY = 0.5;

	/**
	 * A Bar (with no parent) with the specified parameters
	 */
	Bar(final @NonNull PerspectiveViz _pv, final double from, final double to, final @NonNull Perspective _facet) {
		super(_pv.art(), _facet);
		assert _pv.p == _facet.getParent() : _facet;
		pv = _pv;
		rect = new Rectangle2D.Double();
		// setRelativeTargetValue(0.0);
		rect.setRect(from, MIDPOINT_MINUS_HALF_MIN_BAR_HEIGHT, to, MIN_BAR_HEIGHT);
		update(from, to);
	}

	void update(final double from, final double to) {
		assert checkXparam(from);
		assert checkXparam(to);
		final double w = to - from + 1.0;
		rect.setRect(from, rect.getY(), w, rect.getHeight());

		// bounds must include the whole frontH, for picking.
		setBounds(from, 0.0, w, 1.0);
		updateHighlighting(pv.getCachedQueryVersion(), YesNoMaybe.NO);
	}

	private boolean checkXparam(final double param) {
		assert !Double.isInfinite(param) && !Double.isNaN(param) : param;
		assert param >= 0.0 : param;
		assert param < pv.getWidth() : param + " " + pv.getWidth();
		assert Math.rint(param) == param : param;
		return true;
	}

	@Override
	public void redrawCallback() {
		final double oldGoalY = goalY;
		queryValidRedraw();
		if (goalY != oldGoalY && oldGoalY == startY) {
			// no longer animating
			setRelativeTargetValue(1.0);
		}
		updateHighlighting(query().version(), YesNoMaybe.NO);
	}

	/**
	 * Set goalY based on logOddsRatio. No other side effects.
	 */
	public void queryValidRedraw() {
		final double constrainedLogOddsRatio = pv.rank.constrainedLogOddsRatio(facet);
		goalY = 0.5 * (1.0 - constrainedLogOddsRatio / Perspective.LOG_ODDS_RANGE);
	}

	/**
	 * Only called by TagWall.barHeightAnimator and redrawCallback()
	 *
	 * Animate rect height
	 */
	void setRelativeTargetValue(final double zeroToOne) {
		final double y = lerp(zeroToOne, startY, goalY);
		final boolean animationFinished = zeroToOne == 1.0;
		if (animationFinished) {
			startY = y;
		}
		final double slop = animationFinished ? 0.0 : 0.1;
		if (Math.abs(y - currentY) > slop) {
			currentY = y;
			final double top = Math.min(y, MIDPOINT_MINUS_HALF_MIN_BAR_HEIGHT);
			final double bottom = Math.max(y, MIDPOINT_PLUS_HALF_MIN_BAR_HEIGHT);
			assert bottom - top >= MIN_BAR_HEIGHT;
			rect.setRect(rect.getX(), top, rect.getWidth(), bottom - top);
			invalidatePaint();
			// System.out.println("Bar.animateData " + facet + " goalY=" +
			// goalY);
		}
	}

	@Override
	protected void paint(final PPaintContext paintContext) {
		final Graphics2D g2 = paintContext.getGraphics();
		g2.setPaint(getPaint()); // getColor(1));
		g2.fill(rect);

		if (currentY == goalY) {
			// Save time by not painting boundaries during animation
			g2.setPaint(BungeeConstants.BVBG);
			g2.setStroke(LazyPPath.getStrokeInstance(0));
			g2.draw(getBoundsReference());
		}
	}

	// @Override
	// public void mayHideTransients(@SuppressWarnings("unused") final PNode
	// ignore) {
	// art().mayHideTransients();
	// }

	@Override
	public Perspective getFacet() {
		return facet;
	}

	@Override
	public Bungee art() {
		return pv.art();
	}

	@Override
	public void printUserAction(final int modifiers) {
		art().printUserAction(ReplayLocation.BAR, facet, modifiers);
	}

}
