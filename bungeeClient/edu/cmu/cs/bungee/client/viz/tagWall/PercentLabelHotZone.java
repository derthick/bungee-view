package edu.cmu.cs.bungee.client.viz.tagWall;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;

import java.awt.Component;
import java.awt.Font;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil;
import edu.umd.cs.piccolo.activities.PActivity;
import edu.umd.cs.piccolo.activities.PActivity.PActivityDelegate;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * Just wide enough for children *100 and /100 labels
 *
 * This will have xOffset<0.0
 *
 * Transparency changes (in setConnected()), rather than visibility or
 * add/remove child.
 *
 * Lazily initialized by validate() ⇒ createOrDeleteHotZone()
 */
class PercentLabelHotZone extends LazyPNode implements PActivityDelegate {
	private static final HotZoneListener HOT_ZONE_LISTENER = new HotZoneListener();
	private static final double HOTZONE_PERCENT_LABEL_SCALE = 0.75;

	/**
	 * [0] * 100+
	 *
	 * [1] = or, when mouse enters, anything
	 *
	 * [2] / 100+
	 */
	private final APText[] hotZonePercentLabels;
	private final double[] hotZonePercentLabelYs;
	/**
	 * White line that extends over the bars
	 *
	 * This will have xOffset==0.0
	 */
	private final LazyPNode hotLine;
	/**
	 * Redundant orange label that appears above percentLabelHotZone on
	 * mouseover
	 */
	private final APText hotZonePopupLabel;
	private final PerspectiveViz pv;
	float goalTransparency = 0f;
	private @Nullable PInterpolatingActivity animationJob;

	public PercentLabelHotZone(final PerspectiveViz _pv) {
		pv = _pv;
		setTransparency(goalTransparency);
		addInputEventListener(HOT_ZONE_LISTENER);

		hotZonePercentLabels = new APText[3];
		hotZonePercentLabelYs = new double[3];
		for (int i = 0; i < 3; i++) {
			final APText hotZonePercentLabel = art().oneLineLabel();
			hotZonePercentLabels[i] = hotZonePercentLabel;
			hotZonePercentLabel.setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
			hotZonePercentLabel.setTextPaint(BungeeConstants.PERCENT_LABEL_COLOR);
			hotZonePercentLabel.setJustification(Component.RIGHT_ALIGNMENT);
			hotZonePercentLabel.setConstrainWidthToTextWidth(false);
			addChild(hotZonePercentLabel);
			hotZonePercentLabelYs[i] = 0.5 * i;
		}

		hotLine = new LazyPNode();
		hotLine.setPaint(BungeeConstants.HELP_COLOR /*
													 * BungeeConstants.
													 * PERCENT_LABEL_COLOR
													 */);
		hotLine.setVisible(false);
		addChild(hotLine);

		hotZonePopupLabel = art().oneLineLabel();
		hotZonePopupLabel.setPaint(BungeeConstants.BVBG);
		hotZonePopupLabel.setTextPaint(BungeeConstants.HELP_COLOR);
		hotZonePopupLabel.setVisible(false);
		addChild(hotZonePopupLabel);

		setFont(art().getCurrentFont());

		hotZonePercentLabels[0].maybeSetText(formatOddsRatio(Perspective.ODDS_RANGE) + "+");
		hotZonePercentLabels[1].maybeSetText(formatOddsRatio(1.0));
		hotZonePercentLabels[2].maybeSetText(formatOddsRatio(Perspective.INVERSE_ODDS_RANGE) + "+");
	}

	void setFont(final Font font) {
		// if (hotZonePercentLabels != null) {
		final double percentLabelW = art().getStringWidth("* 100+");
		final double percentLabelScaledW = Math.round((percentLabelW * HOTZONE_PERCENT_LABEL_SCALE) * 0.67);
		for (int i = 0; i < 3; i++) {
			hotZonePercentLabels[i].setWidth(percentLabelW);
			// hotZonePercentLabels[i].setBounds(0.0, 0.0, percentLabelW,
			// art().lineH());
			// System.out.println("PercentLabelHotZone.setFont 0 percentLabelW="
			// + percentLabelW + " "
			// + hotZonePercentLabels[i].getBounds());
			hotZonePercentLabels[i].setX(-percentLabelScaledW);
			// hotZonePercentLabels[i].setX(0.0);
			hotZonePercentLabels[i].setFont(font);
			layoutHotZonePercentLabel(i);
		}

		hotZonePopupLabel.setFont(font);
		setBounds(-percentLabelScaledW, 0.0, percentLabelScaledW, 1.0);
		// setBounds(0.0, 0.0, percentLabelScaledW, 1.0);
		// }
	}

	public void setConnected(final boolean isFirstPVandConnected) {
		moveToFront();
		final float _goalTransparency = isFirstPVandConnected ? 1f : 0f;
		// System.out.println("PercentLabelHotZone.setConnected " + getParent()
		// + " oldGoalTransparency="
		// + goalTransparency + " goalTransparency=" + _goalTransparency + "
		// getTransparency=" + getTransparency()
		// + " animationJob=" + animationJob);
		if (goalTransparency != _goalTransparency) {
			goalTransparency = _goalTransparency;
			if (animationJob != null) {
				animationJob.terminate();
			}
			assert getRoot() != null : PiccoloUtil.ancestorString(this);
			animationJob = animateToTransparency(goalTransparency, BungeeConstants.DATA_ANIMATION_MS);
			assert animationJob != null;
			animationJob.setDelegate(this);
		}
		// testHotZoneTransparency(isFirstPVandConnected);
	}

	@Override
	public void activityStarted(final PActivity activity) {
		assert activity == animationJob;
	}

	@Override
	public void activityStepped(final PActivity activity) {
		assert activity == animationJob;
	}

	@Override
	public void activityFinished(final PActivity activity) {
		assert activity == animationJob;
		animationJob = null;
	}

	// public boolean testHotZoneTransparency(final boolean
	// isFirstPVandConnected) {
	// if (pv.p.getID() == 6544) {
	// System.out.println("PercentLabelHotZone.testHotZoneTransparency " +
	// getParent() + " goalTransparency="
	// + goalTransparency + " getTransparency=" + getTransparency() + "
	// animationJob=" + animationJob + " "
	// + UtilString.timeString());
	// }
	//
	// assert goalTransparency == (isFirstPVandConnected ? 1f : 0f) :
	// isFirstPVandConnected + " " + pv
	// + "\n rank=" + pv.rank + "\n connectedRank=" +
	// pv.rank.tagWall.connectedRank();
	// // if (isFirstPVandConnected) {
	// // PiccoloUtil.whyCantISee(this);
	// // }
	// assert getTransparency() == goalTransparency || animationJob != null :
	// getParent() + " getTransparency="
	// + getTransparency() + " " + animationJob;
	// return true;
	// }

	void layout() {
		if (pv.isConnected()) {
			for (int i = 0; i < hotZonePercentLabels.length; i++) {
				layoutHotZonePercentLabel(i);
			}

			final double yScale = 1.0 / pv.rank.frontH();
			// assert getX() == 0.0 : getX();
			hotZonePopupLabel.setTransform(
					UtilMath.scaleNtranslate(1.0, yScale, getX(), yScale * (-1.5 * hotZonePopupLabel.getHeight())));

			// hotZonePopupLabel.setOffset(getX(), tagWall().selectedFoldH() -
			// 1.5
			// * hotZonePopupLabel.getHeight());
		}
	}

	private Bungee art() {
		return pv.art();
	}

	static @NonNull String formatOddsRatio(final double ratio) {
		if (ratio == Double.POSITIVE_INFINITY) {
			return "* Infinity";
		}
		final int iRatio = UtilMath.roundToInt(ratio > 1.0 ? ratio : 1.0 / ratio);
		return iRatio == 1 ? "=" : (ratio > 1.0 ? "* " : "/ ") + iRatio;
	}

	// called only when mouse enters/moves up and down/exits
	public void updateHotZoneForMouseEventOrHide(final double y, final boolean isVisible) {
		assert assertInRange(y, 0.0, 1.0) : y + " percentLabelHotZone.getBounds=" + getBounds();
		final double oddsRatio = unwarp(1.0 - y);
		hotZonePercentLabels[1].maybeSetText(formatOddsRatio(oddsRatio));
		hotZonePercentLabelYs[1] = y;
		layoutHotZonePercentLabel(1);
		hotLine.setVisible(isVisible);
		hotZonePopupLabel.setVisible(isVisible);
		if (isVisible) {
			if (!hotLine.getVisible()) {
				hotLine.moveAncestorsToFront();
				hotZonePopupLabel.moveToFront();
			}
			final double yScale = 1.0 / pv.rank.frontH();
			final TagWall tagWall = tagWall();
			hotLine.setBounds(0.0, /* front.getYOffset() + */yScale * y * tagWall.selectedRankFrontH(),
					tagWall.getWidth() + tagWall.rankLabelX(), yScale);

			final String msg = (oddsRatio > 0.666666666) ? Math.round(oddsRatio) + " times more likely than others"
					: "1 / " + Math.round(1.0 / oddsRatio) + " as likely as others";
			hotZonePopupLabel.maybeSetText(msg);
		}
	}

	/**
	 * @param y
	 *            1-y-coordinate, so most positive association is 0
	 * @return the odds ratio for this y value
	 */
	static double unwarp(final double y) {
		final double logOdds = (y - 0.5) * 2.0 * Perspective.LOG_ODDS_RANGE;
		final double odds = Math.exp(logOdds);
		return odds;
	}

	/**
	 * setTransform [scale and offset]
	 */
	private void layoutHotZonePercentLabel(final int labelIndex) {
		final APText hotZonePercentLabel = hotZonePercentLabels[labelIndex];
		final double y = hotZonePercentLabelYs[labelIndex];
		assert assertInRange(y, 0.0, 1.0);
		// assert getX() == 0.0 : getX();
		final double scaleY = HOTZONE_PERCENT_LABEL_SCALE / tagWall().selectedRankFrontH();
		final double halfLabelHeight = hotZonePercentLabel.getHeight() * 0.5 * scaleY;
		hotZonePercentLabel.setTransform(
				UtilMath.scaleNtranslate(HOTZONE_PERCENT_LABEL_SCALE, scaleY, getX(), y - halfLabelHeight));
	}

	private TagWall tagWall() {
		return pv.tagWall();
	}

	private static final class HotZoneListener extends MyInputEventHandler<PercentLabelHotZone> {

		HotZoneListener() {
			super(PercentLabelHotZone.class);
		}

		@Override
		public boolean moved(final PercentLabelHotZone hotZone, final PInputEvent e) {
			return enter(hotZone, e);
		}

		@Override
		public boolean enter(final PercentLabelHotZone hotZone, final PInputEvent e) {
			final double y = e.getPositionRelativeTo(hotZone).getY();
			final boolean result = y >= 0.0 && y <= 1.0;
			// assert result : "e.getPosition=" + e.getPosition() + "
			// hotZone.getGlobalBounds=" + hotZone.getGlobalBounds()
			// + " y=" + y;
			if (result) {
				hotZone.updateHotZoneForMouseEventOrHide(y, true);
			}
			return result;
		}

		@Override
		public boolean exit(final PercentLabelHotZone hotZone) {
			hotZone.updateHotZoneForMouseEventOrHide(0.5, false);
			return true;
		}

	}

}
