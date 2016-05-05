package edu.cmu.cs.bungee.client.viz.popup;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.getBit;

import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.explanation.Distribution;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Alignment;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPPath;
import edu.cmu.cs.bungee.piccoloUtils.gui.SolidBorder;
import edu.umd.cs.piccolo.util.PBounds;

class EulerDiagram extends LazyPPath {

	private static final double ATOMIC_BLOCK_SIZE = 200.0;
	private static final float TRANSPARENCY = 0.3F;

	static final @NonNull Paint TEXT_COLOR = UtilColor.BLACK;
	static final @NonNull Color BLOCK_OUTLINE_COLOR = UtilColor.BLACK;
	private static final @NonNull Paint BG_COLOR = UtilColor.WHITE;
	static final @NonNull Paint COMPOSITE_BG_COLOR = new Color(240, 240, 240);
	static final @NonNull Color COLOR001 = UtilColor.BLUE;
	static final @NonNull Color COLOR010 = UtilColor.ORANGE;
	static final @NonNull Color COLOR100 = UtilColor.CYAN;
	static final @NonNull Color[] PRIMARY_COLORS = { COLOR001, COLOR010, COLOR100 };
	public static final double SLOP = 1e-9;

	/**
	 * Use a list instead of SortedSet to allow the convenience of get(i).
	 */
	final @NonNull List<Perspective> nonPrimaryFacets;
	final @NonNull SortedSet<Perspective> primaryFacets;
	final @NonNull Distribution distribution;
	final @NonNull Bungee art;
	final double margin;

	EulerDiagram(final @NonNull Distribution _distribution, // NO_UCD (unused
															// code)
			final Collection<Perspective> _primary, // NO_UCD (unused code)
			final SortedSet<Perspective> _nonPrimaryFacets, final @NonNull Bungee _art, final String _label) {
		// System.out.println("EulerDiagram " + _label + "\n " + _distribution
		// + "\n _primary=" + _primary + "\n _nonPrimaryFacets="
		// + _nonPrimaryFacets);
		assert checkEulerFacets(_primary);
		art = _art;
		margin = art.buttonMargin();
		primaryFacets = new TreeSet<>(Perspective.TOTAL_COUNT_DESCENDING_COMPARATOR);
		primaryFacets.addAll(_primary);
		nonPrimaryFacets = new ArrayList<>(_nonPrimaryFacets);
		distribution = _distribution;
		// assert distribution.
		// distribution = observed.getMarginalDistribution(_nonPrimaryFacets);
		// assert nonPrimaryFacets.containsAll(primaryFacets) : distribution +
		// " "
		// + primaryFacets;
		// nonPrimaryFacets.removeAll(primaryFacets);
		draw(_label);
	}

	public static boolean checkEulerFacets(final Collection<Perspective> _primaryFacets) {
		final int nPrimaryFacets = _primaryFacets.size();
		final boolean result = nPrimaryFacets >= 2 && nPrimaryFacets <= 3;
		if (!result) {
			System.err.println(
					"Warning: Need to write more code for primary.size = " + nPrimaryFacets + ": " + _primaryFacets);
		}
		return result;
	}

	private void draw(final String _label) {
		removeAllChildren();
		setPaint(BG_COLOR);

		// StrokePaint is set by caller?!
		// setStrokePaint(BORDER_COLOR);

		final EulerBlock block = getEulerBlock(new ArrayList<Boolean>());
		addChild(block);

		final float y = (float) addLabels(_label);

		block.setYoffset(y - block.getFullBounds().y);
		drawBorder();

		final PBounds fullBounds = getFullBounds();
		final LazyPPath blockBorder = LazyPPath.createLine((float) (getFullBounds().x + margin), y,
				(float) (fullBounds.x + fullBounds.width - margin), y, 1, BLOCK_OUTLINE_COLOR);
		addChild(blockBorder);

		drawColorKey(y);
	}

	/**
	 * draw circles around a circle of diameter w/2 centered at x,y=(w - w/4 -
	 * size/2)
	 */
	private void drawColorKey(final float bottom) {
		final PBounds fullBounds = getFullBounds();
		final float right = (float) (fullBounds.x + fullBounds.width);
		final float smallDiamater = (float) art.lineH() * 2.5f;
		final float smallRadius = smallDiamater / 2f;
		final float bigRadius = smallDiamater / 5f;
		final float bigCenterOffset = bigRadius + smallDiamater;
		final double dAngle = 2.0 * Math.PI / primaryFacets.size();
		for (int i = 0; i < primaryFacets.size(); i++) {
			final float x = Math
					.round(right - bigCenterOffset - smallRadius + (float) Math.cos(i * dAngle) * bigRadius);
			final float y = Math
					.round(bottom - bigCenterOffset - smallRadius / 2f - (float) Math.sin(i * dAngle) * bigRadius);
			@SuppressWarnings("null")
			final LazyPPath colorCircle = LazyPPath.createEllipse(x, y, smallDiamater, smallDiamater, 0,
					PRIMARY_COLORS[i]);
			colorCircle.setTransparency(TRANSPARENCY);
			addChild(colorCircle);
		}
	}

	private void drawBorder() {
		reset();
		final float outlineW = (float) margin;
		setStroke(LazyPPath.getStrokeInstance((int) outlineW));
		final Rectangle2D.Float fullBounds = new Rectangle2D.Float();
		fullBounds.setRect(getUnionOfChildrenBounds(null));
		final float halfOutlineW = outlineW / 2f;
		final float x0 = fullBounds.x - halfOutlineW;
		final float y0 = fullBounds.y - halfOutlineW;
		final float x1 = x0 + fullBounds.width + outlineW;
		final float y1 = y0 + fullBounds.height + outlineW;
		final float[] xs = { x0, x1, x1, x0, x0 };
		final float[] ys = { y0, y0, y1, y1, y0 };
		setPathToPolyline(xs, ys);
	}

	private double addLabels(final String _label) {
		APText prev = addLabel(_label + "\n total = " + distribution.totalCount, TEXT_COLOR, null);
		prev.setTransparency(1f);
		int i = 0;
		for (final Perspective p : primaryFacets) {
			prev = addLabel(p.getName(), PRIMARY_COLORS[i++], prev);
		}
		return margin + prev.getIntMaxY();
	}

	APText addLabel(final String text, final Paint textPaint, final APText prev) {
		final APText l = art.getAPText();
		l.setTextPaint(textPaint);
		l.maybeSetText(text);
		l.setTransparency(TRANSPARENCY);
		final PBounds fullBounds = getFullBounds();
		l.setOffset(fullBounds.x + (prev == null ? 1.0 : 2.0) * margin,
				margin + (prev == null ? 0.0 : prev.getIntMaxY()));
		addChild(l);
		return l;
	}

	Distribution getConditionalDistribution(final List<Boolean> nonPrimaryStates) {
		final SortedSet<Perspective> falseFacets = new TreeSet<>();
		final SortedSet<Perspective> trueFacets = new TreeSet<>();
		for (int i = 0; i < nonPrimaryStates.size(); i++) {
			final Perspective nonPrim = nonPrimaryFacets.get(i);
			final Boolean state = nonPrimaryStates.get(i);
			if (state != null) {
				if (state.booleanValue()) {
					trueFacets.add(nonPrim);
				} else {
					falseFacets.add(nonPrim);
				}
			}
		}
		final Distribution _conditionalDistribution = distribution.getConditionalDistribution(falseFacets, trueFacets);

		// System.out.println("EulerDiagram.getConditionalDistribution "
		// + distribution + "\n " + falseFacets + " " + trueFacets + "\n"
		// + _conditionalDistribution);
		return _conditionalDistribution;
	}

	EulerBlock getEulerBlock(final List<Boolean> nonPrimaryStates) {
		EulerBlock result = null;
		final Distribution _conditionalDistribution = getConditionalDistribution(nonPrimaryStates);
		if (_conditionalDistribution != null) {
			result = new EulerBlock(nonPrimaryStates, _conditionalDistribution);
		}
		return result;
	}

	private class EulerBlock extends LazyPNode {

		final Distribution conditionalDistribution;

		EulerBlock(final List<Boolean> nonPrimaryStates, final Distribution _conditionalDistribution) {
			// System.out.println("EulerBlock " + nonPrimaryFacets + " "
			// + nonPrimaryStates + " " + primaryFacets);

			conditionalDistribution = _conditionalDistribution;
			if (nonPrimaryStates.size() < nonPrimaryFacets.size()) {
				final SolidBorder outline = new SolidBorder(BLOCK_OUTLINE_COLOR,
						BungeeConstants.EULER_DIAGRAM_OUTLINE_THICKNESS);
				addChild(outline);
				initCompositeEulerBlock(nonPrimaryStates);
			} else {
				if (conditionalDistribution != null) {
					final EulerBlock prim = new EulerBlock(conditionalDistribution);
					setBounds(prim.getBounds());
					addChild(prim);
					prim.initPrimaryEulerBlock();
				}
			}
		}

		private EulerBlock(final Distribution _conditionalDistribution) {
			conditionalDistribution = _conditionalDistribution;
			setBounds(0.0, 0.0, ATOMIC_BLOCK_SIZE, ATOMIC_BLOCK_SIZE);
		}

		/**
		 * Let states be { s000, s001, ..., s111 } Also probabilities p000, etc.
		 *
		 * where the LSB is on the right, and represents the facet with the
		 * largest count, followed by the next largest count. Warning: inside
		 * compositeEulerBlocks, this order may not be preserved!
		 *
		 * and allow s0X1, etc.
		 *
		 * sXX1 is a rectangle along the bottom.
		 *
		 * sX1X is a rectangle on the left, possibly with extra space on top or
		 * bottom, but not both.
		 *
		 * s1X0 and s1X1 are hopefully 2 rectangles.
		 *
		 * Also graphical coordinates x1X0, y1X0, w1X0, h1X0, etc, and COLOR001,
		 * COLOR010, COLOR100.
		 *
		 * @param nonPrimaryStates
		 */
		private void initPrimaryEulerBlock() {
			setPaint(UtilColor.WHITE);
			setBounds(0.0, 0.0, 1.0, 1.0);
			setScale(ATOMIC_BLOCK_SIZE
					* Math.pow(conditionalDistribution.totalCount / (double) distribution.totalCount, 0.5));

			final int[] cnts = conditionalDistribution.getMarginalCounts(primaryFacets);
			// System.out.println("qwsdfvb " + conditionalDistribution + "\n"
			// + primaryFacets + " " + UtilString.valueOfDeep(cnts));

			final double pX11 = p("X11", cnts);
			final double pXX1 = p("XX1", cnts);
			final double pX1X = p("X1X", cnts);
			final double p110 = p("110", cnts);
			final double p1X0 = p("1X0", cnts);
			final double p1X1 = p("1X1", cnts);
			final double p111 = p("111", cnts);

			final double yXX1 = 1.0 - pXX1;
			if (pXX1 > 0.0) {
				// along the bottom; h=pXX1
				addRect(0.0, yXX1, 1.0, pXX1, COLOR001);
			}

			double wX1X = (pX1X - pX11) / yXX1;
			double hX1X = pX1X / wX1X;
			double yX1X = 0.0;
			if (!isZeroToOne(wX1X) || !isZeroToOne(hX1X)) {
				wX1X = pX11 / pXX1;
				hX1X = pX1X / wX1X;
				yX1X = 1.0 - pX1X / wX1X;
			}
			if (pX1X > 0.0) {
				assert isZeroToOne(wX1X);
				assert isZeroToOne(hX1X) : "hX1X=" + hX1X + " wX1X=" + wX1X + " yXX1=" + yXX1;
				// along the left side
				addRect(0.0, yX1X, wX1X, hX1X, COLOR010);
			} else {
				wX1X = 0.0;
			}

			if (primaryFacets.size() == 2) {
				addCount((1.0 + wX1X) * 0.5, yXX1 * 0.5, cnts[0]);
				addCount((1.0 + wX1X) * 0.5, 1.0 - pXX1 * 0.5, cnts[1]);
				addCount(wX1X * 0.5, (yX1X + yXX1) * 0.5, cnts[2]);
				addCount(wX1X * 0.5, (yXX1 + yX1X + hX1X) * 0.5, cnts[3]);
			} else if (primaryFacets.size() == 3) {

				final double wX10 = yX1X >= yXX1 ? 0.0 : wX1X;
				double w1X0 = wX10 * p1X0 / p110;
				double h1X0 = p1X0 / w1X0;
				double x1X0 = 0.0;
				double y1X0 = yXX1 - h1X0;
				if (!isZeroToOne(w1X0) || !isZeroToOne(h1X0) || !isZeroToOne(y1X0)) {
					w1X0 = (wX10 - 1.0) / (p110 / p1X0 - 1.0);
					h1X0 = p1X0 / w1X0;
					x1X0 = 1.0 - w1X0;
					y1X0 = yXX1 - h1X0;
				}
				if ((!isZeroToOne(w1X0) || !isZeroToOne(h1X0) || !isZeroToOne(y1X0)) && p110 == 0.0) {
					w1X0 = 1.0;
					h1X0 = p1X0 / w1X0;
					x1X0 = 1.0 - w1X0;
					y1X0 = yXX1 - h1X0;
				}
				if (p1X0 > 0.0 && isZeroToOne(w1X0) && isZeroToOne(h1X0) && isZeroToOne(y1X0)) {
					// Just give up if we haven't found valid coordinates.
					assert isZeroToOne(x1X0) : "x1X0=" + x1X0 + " w1X0=" + w1X0 + " wX10=" + wX10;
					// above sXX1; on left if positivelylCorrelated, else on
					// right
					addRect(x1X0, y1X0, w1X0, h1X0, COLOR100);
				} else {
					y1X0 = yXX1;
					w1X0 = 0.0;
					h1X0 = 0.0;
				}

				final double wX11 = (yX1X + hX1X) <= yXX1 ? 0.0 : wX1X;
				double w1X1 = wX11 * p1X1 / p111;
				double x1X1 = 0.0;
				if (!isZeroToOne(w1X1)) {
					w1X1 = (wX11 - 1.0) / (p111 / p1X1 - 1.0);
					x1X1 = 1.0 - w1X1;
				}
				double h1X1 = p1X1 / w1X1;
				if (p1X1 > 0.0 && isZeroToOne(w1X1) && isZeroToOne(h1X1)) {
					// Just give up if we haven't found valid coordinates.
					assert isZeroToOne(yXX1 + h1X1) : " yXX1=" + yXX1 + " h1X1=" + h1X1 + " w1X1=" + w1X1 + " wX11="
							+ wX11 + " x1X1=" + x1X1;
					// below sXX1; on left if positivelylCorrelated, else on
					// right
					addRect(x1X1, yXX1, w1X1, h1X1, COLOR100);
				} else {
					h1X1 = 0.0;
				}

				addCount((1.0 + wX1X) * 0.5, y1X0 * 0.5, cnts[0]);
				addCount((1.0 + wX1X) * 0.5, 1.0 - (pXX1 - h1X1) * 0.5, cnts[1]);
				addCount(wX1X * 0.5, (yX1X + (x1X0 >= wX1X ? yXX1 : y1X0)) * 0.5, cnts[2]);
				addCount(wX1X * 0.5, (yXX1 + /* h1X1 + */yX1X + hX1X) * 0.5, cnts[3]);
				addCount((x1X0 + w1X0 + wX1X) * 0.5, yXX1, Alignment.BOTTOM_CENTER, cnts[4]);
				addCount((wX1X + x1X1 + w1X1) * 0.5, yXX1, Alignment.TOP_CENTER, cnts[5]);
				addCount((wX1X + x1X0) * 0.5, yXX1, Alignment.BOTTOM_CENTER, cnts[6]);
				addCount((x1X1 + wX1X) * 0.5, yXX1, Alignment.TOP_CENTER, cnts[7]);
			}
		}

		private double p(final String state, final int[] counts) {
			final int nBits = state.length();
			final YesNoMaybe[] bits = new YesNoMaybe[nBits];
			for (int bit = 0; bit < nBits; bit++) {
				switch (state.charAt(nBits - bit - 1)) {
				case 'X':
					bits[bit] = YesNoMaybe.MAYBE;
					break;
				case '0':
					bits[bit] = YesNoMaybe.NO;
					break;
				case '1':
					bits[bit] = YesNoMaybe.YES;
					break;

				default:
					assert false : Character.getName(state.charAt(bit));
					break;
				}
			}
			// System.err.println(" EulerDiagram.p " + state + " ⇒ "
			// + p(bits, counts));
			return p(bits, counts);
		}

		/**
		 * @param bits
		 *            { bit001, bit010, bit100 };
		 */
		private double p(final YesNoMaybe[] bits, final int[] counts) {
			int sum = 0;
			int total = 0;
			for (int state = 0; state < counts.length; state++) {
				final int count = counts[state];
				total += count;
				boolean match = true;
				int bit = 0;
				for (final YesNoMaybe yesNoMaybe : bits) {
					if (!yesNoMaybe.isCompatible(getBit(state, bit++) == 1)) {
						match = false;
						break;
					}
				}
				if (match) {
					sum += count;
				}
			}
			final double result = sum / (double) total;
			assert assertZeroToOne(result);
			return result;
		}

		private boolean assertZeroToOne(final double x) {
			final boolean result = isZeroToOne(x);
			assert result : x;
			return result;
		}

		private boolean isZeroToOne(final double x) {
			final boolean result = -SLOP <= x && x <= 1.0 + SLOP;
			return result;
		}

		private void addRect(double x, double y, double w, double h, final Color color) {
			// System.out.println("EulerDiagram.addRect " + x + " " + y + " " +
			// w + " " + h + " " + color);
			assert assertZeroToOne(x);
			assert assertZeroToOne(y);
			assert assertZeroToOne(w + x);
			assert assertZeroToOne(h + y);
			if (w * h > 0.0) {
				final double minSize = 2.0 / getScale();
				w = Math.max(minSize, w);
				h = Math.max(minSize, h);
				x = Math.min(x, getWidth() - w);
				y = Math.min(y, getHeight() - h);
				final LazyPNode rect = new LazyPNode();
				rect.setBounds(x, y, w, h);
				rect.setPaint(color);
				rect.setTransparency(TRANSPARENCY);
				addChild(rect);
			}
		}

		private void addCount(final double centerX, final double centerY, final int rectCount) {
			addCount(centerX, centerY, Alignment.CENTER_CENTER, rectCount);
		}

		private void addCount(final double x, final double y, final int attachmentPoint, final int rectCount) {
			// System.out.println("EulerDiagram.addCount " + centerX + " "
			// + centerY + " " + rectCount);
			if (rectCount > 0) {
				assert assertZeroToOne(x);
				assert assertZeroToOne(y);
				final APText text = new APText();
				text.setScale(1.0 / getScale());
				text.setTextPaint(TEXT_COLOR);
				text.maybeSetText(Integer.toString(rectCount));

				final Point2D srcPt = Alignment.point2DPercent(attachmentPoint);
				text.setX(Math.rint(x * getScale() - fixAttachment(srcPt.getX()) * text.getWidth()));
				text.setY(Math.rint(y * getScale() - fixAttachment(srcPt.getY()) * text.getHeight()));

				addChild(text);
			}
		}

		private double fixAttachment(final double zeroToOne) {
			return zeroToOne < 0.0 ? 0.5 : zeroToOne;
		}

		private void initCompositeEulerBlock(final List<Boolean> nonPrimaryStates) {
			setPaint(COMPOSITE_BG_COLOR);

			nonPrimaryStates.add(Boolean.FALSE);
			final EulerBlock left = getEulerBlock(nonPrimaryStates);
			nonPrimaryStates.remove(nonPrimaryStates.size() - 1);
			nonPrimaryStates.add(Boolean.TRUE);
			final EulerBlock right = getEulerBlock(nonPrimaryStates);
			nonPrimaryStates.remove(nonPrimaryStates.size() - 1);
			nonPrimaryStates.add(null);
			final EulerBlock either = getEulerBlock(nonPrimaryStates);
			nonPrimaryStates.remove(nonPrimaryStates.size() - 1);

			final String name = nonPrimaryFacets.get(nonPrimaryStates.size()).getName();

			addSubBlock(left, nonPrimaryStates, "~ " + name);
			addSubBlock(right, nonPrimaryStates, name);
			addSubBlock(either, nonPrimaryStates, "Either");
			if (nonPrimaryStates.size() % 2 == 0) {
				final double offset = either.getHeight() + margin;
				if (left != null) {
					left.setYoffset(offset);
				}
				either.setYoffset(2.0 * offset);
			} else {
				final double offset = either.getWidth() + margin;
				if (left != null) {
					left.setXoffset(offset);
				}
				either.setXoffset(2.0 * offset);
			}

			// if (right != null) {
			// addChild(right);
			// final APText l2 = art.getAPText();
			// addChild(l2);
			// l2.setTextPaint(TEXT_COLOR);
			// l2.setText(name + "\ntotal: "
			// + right.conditionalDistribution.totalCount);
			// if (nonPrimaryStates.size() % 2 == 0) {
			// right.setOffset(left.getXOffset(), left.getIntMaxY()
			// + margin);
			// } else {
			// right.setOffset(left.getIntMaxX() + margin,
			// left.getYOffset());
			// l2.setOffset(right.getXOffset(), l1.getYOffset());
			// l2.setConstrainWidthToTextWidth(false);
			// l2.setWidth(right.getWidth());
			// }
			// }

			// addChild(either);
			// final APText l3 = art.getAPText();
			// addChild(l3);
			// l3.setTextPaint(TEXT_COLOR);
			// l3.setText("Either\ntotal: "
			// + either.conditionalDistribution.totalCount);
			// if (nonPrimaryStates.size() % 2 == 0) {
			// // l1.setCenterY(left.getCenterY());
			// // l1.setOffset(0.0, (left.getIntMaxY() - l1.getHeight()) /
			// // 2.0);
			// // left.setOffset(margin + l1.getIntMaxX(), 0.0);
			// // right.setOffset(left.getXOffset(), left.getIntMaxY() +
			// // margin);
			// // l1.setCenterY(right.getCenterY());
			// // l2.setOffset(
			// // l1.getXOffset(),
			// // right.getYOffset()
			// // + (right.getHeight() * right.getScale() - l2
			// // .getHeight()) / 2.0);
			// either.setOffset(right.getXOffset(), right.getIntMaxY()
			// + margin);
			// l3.setCenterY(either.getCenterY());
			// // l3.setOffset(
			// // l2.getXOffset(),
			// // either.getYOffset()
			// // + (either.getHeight() * either.getScale() - l3
			// // .getHeight()) / 2.0);
			// } else {
			// // l1.setOffset(0.0, 0.0);
			// // left.setOffset(0.0, l1.getIntMaxY() + margin);
			// // right.setOffset(left.getIntMaxX() + margin,
			// // left.getYOffset());
			// // l2.setOffset(right.getXOffset(), l1.getYOffset());
			// either.setOffset(right.getIntMaxX() + margin,
			// right.getYOffset());
			// l3.setOffset(either.getXOffset(), l2.getYOffset());
			// // l1.setConstrainWidthToTextWidth(false);
			// // l2.setConstrainWidthToTextWidth(false);
			// l3.setConstrainWidthToTextWidth(false);
			// // l1.setWidth(left.getWidth());
			// // l2.setWidth(right.getWidth());
			// l3.setWidth(either.getWidth());
			// }
			setBoundsFromFullBounds();
		}

		private void addSubBlock(final EulerBlock subBlock, final List<Boolean> nonPrimaryStates, final String name) {
			if (subBlock != null) {
				final APText l1 = art.getAPText();
				l1.setTextPaint(TEXT_COLOR);
				l1.maybeSetText(name + "\ntotal: " + subBlock.conditionalDistribution.totalCount);
				if (nonPrimaryStates.size() % 2 == 0) {
					// l1.setCenterY(subBlock.getCenterY());
					l1.setOffset(-(l1.getIntMaxX() + margin), 0.0);
					// l1.setCenterY(right.getCenterY());
				} else {
					l1.setOffset(0.0, -(l1.getIntMaxY() + margin));
					l1.setConstrainWidthToTextWidth(false);
					l1.setWidth(subBlock.getWidth());
				}
				subBlock.addChild(l1);
				addChild(subBlock);
				// System.out.println("EulerBlock.addSubBlock " + name
				// + "\n subBlock.getBounds=" + subBlock.getBounds()
				// + "\n l1.getBounds=" + l1.getBounds());
			}
		}

	}

	// static double r(final Distribution dist) {
	// final double[] d = dist.getProbDist();
	// assert d.length == 4;
	// final double p0x = d[0] + d[1];
	// final double p1x = d[2] + d[3];
	// final double px0 = d[0] + d[2];
	// final double px1 = d[1] + d[3];
	// double result = Math.sqrt(1 - (d[0] * d[1] / p0x + d[2] * d[3] / p1x)
	// / (px0 * px1));
	// if (Double.isNaN(result)) {
	// result = 0.0;
	// }
	//
	// // int[] cnts = dist.getCounts();
	// // ChiSq2x2 chisq = ChiSq2x2.getInstance(null, dist.getTotalCount(),
	// // cnts[3] + cnts[1], cnts[3] + cnts[2], cnts[3], null);
	// // System.out.println("r " + d[3] + " - " + (d[3] + d[1]) * (d[3] +
	// // d[2])
	// // +
	// // " = "
	// // + (d[3] - (d[3] + d[1]) * (d[3] + d[2])) + " " + dist + " "
	// // + UtilString.valueOfDeep(d) + " " + chisq.correlation() + "\n"
	// // + chisq.printTable());
	//
	// if (d[3] < (d[3] + d[1]) * (d[3] + d[2])) {
	// result = -result;
	// }
	// return result;
	// }

}
