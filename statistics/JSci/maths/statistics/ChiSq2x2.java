package JSci.maths.statistics;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.FormattedTableBuilder;
import edu.cmu.cs.bungee.javaExtensions.StringAlign;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.UtilString.Justification;

/**
 * A bunch of statistics that can be computed on a 2 x 2 table. Remembers the
 * object that generated the table, for use by TopTags.
 *
 * @author mad
 *
 */
public class ChiSq2x2 implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final int FISHER_THRESHOLD = 100; // 6;

	private static final ChiSqrDistribution CHI_SQR_DISTRIBUTION = new ChiSqrDistribution(1);
	private static final DecimalFormat DOUBLE_FORMAT = new DecimalFormat("0.00E0");
	private static final StringAlign ALIGN_13_LEFT = new StringAlign(13, Justification.LEFT);

	static final @NonNull public ChiSq2x2 UNINFORMATIVE_CHI_SQ_2X2 = new ChiSq2x2(0, 0, 0, 0);

	/**
	 * total=0 marks this as having no useful information
	 */
	private final int total;
	private final int row0;
	private final int col0;
	private final int table00;

	// -2 means "not computed yet"
	private double pvalue = -2.0;
	private int pvalueSign = -2;
	private double chiSq = -2.0;
	private double oddsRatio = -2.0;

	/**
	 * @param total
	 *            sum of all 4 table entries
	 * @param row0
	 *            sum of first row
	 * @param col0
	 *            sum of first column
	 * @param table00
	 *            count where both variables are true
	 *
	 * @throws OutOfRangeException
	 */
	public static @NonNull ChiSq2x2 getInstance(final int total, final int row0, final int col0, final int table00,
			final Object msg) {
		final ChiSq2x2 chiSq2x2 = new ChiSq2x2(total, row0, col0, table00);
		assert chiSq2x2.checkTable(msg.toString());
		return chiSq2x2;
	}

	static @NonNull ChiSq2x2 getInstanceABCD(final int table00, final int table01, final int table10,
			final int table11) {
		final int row0 = table00 + table01;
		final ChiSq2x2 chiSq2x2 = new ChiSq2x2(row0 + table10 + table11, row0, table00 + table10, table00);
		assert chiSq2x2.checkTable(null);
		return chiSq2x2;
	}

	private ChiSq2x2(final int _total, final int _row0, final int _col0, final int _table00) {
		if (_row0 == 0 || _row0 == _total || _col0 == 0 || _col0 == _total) {
			pvalue = 1.0;
			pvalueSign = 1;
			oddsRatio = 1.0;
			chiSq = Double.NaN;
		}
		total = _total;
		row0 = _row0;
		col0 = _col0;
		table00 = _table00;
	}

	int total() {
		return total;
	}

	int col0() {
		return col0;
	}

	int row0() {
		return row0;
	}

	private int table11() {
		return row1() - table10();
	}

	private int col1() {
		final int result = total - col0;
		return result;
	}

	private int row1() {
		final int result = total - row0;
		return result;
	}

	private int table10() {
		return col0 - table00;
	}

	private int table01() {
		return row0 - table00;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "p=" + (pvalueSign > 0 ? "+" : "-") + pvalue);
	}

	boolean checkTable(final Object msg) {
		assert table00 >= 0 && row0 >= table00 && col0 >= table00 && row1() >= 0 && col1() >= 0
		// && row0 >= table00 && col0 >= table00 // these must be meant to be
		// something else
				&& row1() >= table10() : checkTableMsg(msg);
		return true;
	}

	String checkTableMsg(final Object msg) {
		return (msg == null ? "" : msg) + "\n" + printTable();
	}

	// public static double test() {
	// final ChiSq2x2<String> _chisq = new ChiSq2x2<>(null, 24, 10, 12, 1);
	// return _chisq.pvalue();
	// }

	// static int f = 0;
	// static int c = 0;
	// static int badc = 0;
	// static long ftime = 0;
	// static long ctime = 0;
	// static int hits = 0;
	// static int misses = 0;

	/**
	 * @return the p-value in [0, POSITIVE_INFINITY]
	 */
	public double pvalue() {
		if (pvalue == -2.0) {
			// try {
			final ChiSqParams tableValues = new ChiSqParams(table00, table01(), table10(), table11());
			final Double result1 = cachedPvalues.get(tableValues);
			if (result1 == null) {
				// misses++;
				// final long start = System.nanoTime();
				if (useFisher()) {
					pvalue = new FisherExactTest(tableValues).getTwoTailPrimitive();
					// ftime += UtilString.nsElapsedTime(start);
					// f++;
				} else {
					pvalue = 1.0 - CHI_SQR_DISTRIBUTION.cumulative(chiSq());
					// ctime += UtilString.nsElapsedTime(start);
					// c++;
					// if (!UtilMath.approxEquals(pvalueFisher(), pvalue,
					// 1e-8,
					// 0.1)) {
					// badc++;
					// }
				}
				cachedPvalues.put(tableValues, pvalue);
			} else {
				// hits++;
				pvalue = result1;
			}
			// } catch (final Exception e) {
			// throw new OutOfRangeException(printTable());
			// }
		}
		return pvalue;
	}

	// public static void printStats() {
	// System.out.println("ChiSq2x2.printStats hits=" + hits + " misses="
	// + misses);
	//
	// System.out.println("ChiSq2x2.printStats "
	// // + pvalue
	// // + " pvalueFisher="
	// // + pvalueFisher()
	// + "\n f+c="
	// + (f + c)
	// + " f="
	// + f
	// + " c="
	// + c
	// + " badc="
	// + badc
	// + "\n avg ftime(ns)="
	// + UtilString.addCommas(UtilMath.roundToInt(ftime / (double) f))
	// + " avg ctime(ns)="
	// + UtilString.addCommas(UtilMath.roundToInt(ctime / (double) c))
	// + " total time(μs)="
	// + UtilString.addCommas(UtilMath
	// .roundToInt((ctime + ftime) / 1000.0))
	// // + " " + printTable()
	// );
	// }

	private boolean useFisher() {
		// final boolean useFisher = true;

		final boolean useFisher = Math.min(row0, total - row0) * Math.min(col0, total - col0) < FISHER_THRESHOLD
				* total;

		// final double expected00 = row0() * col0() / total();
		// final double expected01 = row0() * col1() / total();
		// final double expected10 = row1() * col0() / total();
		// final double expected11 = row1() * col1() / total();
		// final boolean useFisher = UtilMath.min(expected00, expected01,
		// expected10, expected11) < 10.0;

		return useFisher;
	}

	// private double pvalueFisher() {
	// double result = Double.NaN;
	// try {
	// final ChiSqParams tableValues = new ChiSqParams(table00, table01(),
	// table10(), table11());
	// Double result1 = cachedPvalues.get(tableValues);
	// if (result1 == null) {
	// result1 = new FisherExactTest(tableValues).getTwoTail();
	// cachedPvalues.put(tableValues, result1);
	// }
	// result = result1;
	// } catch (final Exception e) {
	// throw new OutOfRangeException(printTable());
	// }
	// // System.out.println("ChiSq2x2.pvalueFisher " + result);
	// // // + " " + printTable());
	// return result;
	// }

	private static final Map<ChiSqParams, Double> cachedPvalues = new HashMap<>();

	/**
	 * @return whether a significant pValue indicates a positive (+1) or
	 *         negative (-1) association.
	 */
	public int sign() {
		if (pvalueSign == -2) {
			pvalueSign = (row0 * (long) col0 < table00 * (long) total) ? 1 : -1;
		}
		return pvalueSign;
	}

	public double chiSq() {
		cache();
		return chiSq;
	}

	/**
	 * @return hack to reduce the effect of sample size (and thus increase the
	 *         effect of effect size).
	 */
	public double myCramersPhi() {
		final double result = chiSq() * sign() / Math.sqrt(total);
		assert!Double.isNaN(result) : chiSq() + " " + sign() + " " + total;
		return result;
	}

	/**
	 * @return p/q, where p=a/(a+c) and q = b/(b+d), or a(b+d)/b(a+c)
	 */
	double oddsRatio() {
		cache();
		return oddsRatio;
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return (p/(1-p))/(q/(1-q)), where p=a/(a+c) and q = b/(b+d), or ad/bc
	// */
	// public double oddsRatio() {
	// cache();
	// return oddsRatio;
	// }

	double facetOnPercent() {
		return table00 / (double) col0;
	}

	// TODO Remove unused code found by UCDetector
	// public double parentOnPercent() {
	// return row0 / (double) total;
	// }

	double siblingOnPercent() {
		return table01() / (double) col1();
	}

	public double correlation() {
		final double dTotal = total();
		final double p00 = table00 / dTotal;
		final double prow0 = row0() / dTotal;
		final double pcol0 = col0() / dTotal;
		final double corr1 = (p00 - prow0 * pcol0) / Math.sqrt((prow0 - prow0 * prow0) * (pcol0 - pcol0 * pcol0));
		// double corr2 = sampleCovariance() / Math.sqrt(sampleVariance(ROW) *
		// sampleVariance(COL));
		// assert corr1==corr2:corr1+" "+corr2;
		assert!Double.isNaN(corr1) : printTable();
		return corr1;
	}

	// /**
	// * @return correlation divided by the maximum possible correlation with
	// the
	// * same sign for the row, column, and table totals. I.e. the
	// * correlation when table00 = min(row0, col0) or zero.
	// */
	// public double correlationPercent() {
	// double dTotal = total();
	// double p00 = table00 / dTotal;
	// double prow0 = row0() / dTotal;
	// double pcol0 = col0() / dTotal;
	// double prowcol = prow0 * pcol0;
	// double numerator = p00 - prowcol;
	// double p00Max = numerator >= 0 ? Math.min(row0(), col0()) / dTotal : 0;
	// double result = numerator / (p00Max - prowcol);
	// // double corr2 = sampleCovariance() / Math.sqrt(sampleVariance(ROW) *
	// // sampleVariance(COL));
	// // assert corr1==corr2:corr1+" "+corr2;
	// assert result >= 0 && result <= 1 : result + " " + numerator + " "
	// + printTable();
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return Cov(X,Y) = E(X*Y) - E(X)*E(Y)
	// *
	// * see http://en.wikipedia.org/wiki/Covariance
	// */
	// public double sampleCovariance() {
	// final double dTotal = total;
	// return (table00 - col0 * (row0 / dTotal)) / (dTotal /*- 1*/);
	// }

	// TODO Remove unused code found by UCDetector
	// static final int ROW = 1;

	// TODO Remove unused code found by UCDetector
	// static final int COL = 2;

	// TODO Remove unused code found by UCDetector
	// public double sampleVariance(final int whichVar) {
	// final double nOn = whichVar == ROW ? row0() : col0();
	// final double nOff = whichVar == ROW ? row1() : col1();
	// final double dTotal = total;
	// return nOn * nOff / (dTotal * (dTotal /*- 1*/));
	// }

	// TODO Remove unused code found by UCDetector
	// double entropy(final int whichVar) {
	// assert total > 0;
	// final double nOn = whichVar == ROW ? row0() : col0();
	// final double nOff = whichVar == ROW ? row1() : col1();
	// final double dTotal = total;
	// final double nats = Math.log(dTotal) - (nLogn(nOn) + nLogn(nOff))
	// / dTotal;
	// final double bits = nats / LOG2;
	// return bits;
	// }

	public static double nLogn(final double n) {
		assert n >= 0;
		if (n == 0) {
			return 0;
		}
		return n * Math.log(n);
	}

	// TODO Remove unused code found by UCDetector
	// static final double LOG2 = Math.log(2);

	// TODO Remove unused code found by UCDetector
	// double entropy() {
	// assert total > 0;
	// final double dTotal = total;
	// final double nats = Math.log(dTotal)
	// - (nLogn(table00) + nLogn(table01()) + nLogn(table10()) +
	// nLogn(table11()))
	// / dTotal;
	// final double bits = nats / LOG2;
	// return bits;
	// }

	// TODO Remove unused code found by UCDetector
	// public double mutInf() {
	// return entropy(ROW) + entropy(COL) - entropy();
	// }

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return Measure of effect size. Phi eliminates sample size by dividing
	// * chi-square by n, the sample size, and taking the square root.
	// *
	// * see <a * href=
	// *
	// "http://www.people.vcu.edu/~pdattalo/702SuppRead/MeasAssoc/NominalAssoc.html"
	// * >Discussion * of effect size< /a>
	// *
	// */
	// public double phi() {
	//
	// final double dTotal = total;
	// final double dRow0 = row0;
	// final double dCol0 = col0;
	// final double dTable00 = table00;
	// final double dRow1 = dTotal - dRow0;
	// final double dCol1 = dTotal - dCol0;
	// final double table01 = dRow0 - dTable00;
	// final double table10 = dCol0 - dTable00;
	// final double table11 = dRow1 - table10;
	//
	// assert dRow0 <= dTotal;
	// assert dRow1 <= dTotal;
	// assert dCol0 <= dTotal;
	// assert dCol1 <= dTotal;
	//
	// return (dTable00 * table11 - table01 * table10)
	// / Math.sqrt(dRow0 * dRow1 * dCol0 * dCol1);
	// }

	/**
	 * Cache values for chiSq, pvalueSign, oddsRatio
	 */
	private void cache() {
		if (chiSq == -2.0) {
			final double dTotal = total;
			final double dRow0 = row0;
			final double dCol0 = col0;
			final double dTable00 = table00;
			final double dRow1 = dTotal - dRow0;
			final double dCol1 = dTotal - dCol0;
			final double table01 = dRow0 - dTable00;
			final double table10 = dCol0 - dTable00;
			final double table11 = dRow1 - table10;
			final double expected00 = dRow0 * dCol0 / dTotal;
			final double expected01 = dRow0 * dCol1 / dTotal;
			final double expected10 = dRow1 * dCol0 / dTotal;
			final double expected11 = dRow1 * dCol1 / dTotal;

			assert expected00 >= 0.0 && expected00 <= dRow0 && expected00 <= dCol0;
			assert expected10 >= 0.0 && expected10 <= dRow1 && expected10 <= dCol0;
			assert expected01 >= 0.0 && expected01 <= dRow0 && expected01 <= dCol1;
			assert expected11 >= 0.0 && expected11 <= dRow1 && expected11 <= dCol1;
			assert dRow0 <= dTotal;
			assert dRow1 <= dTotal;
			assert dCol0 <= dTotal;
			assert dCol1 <= dTotal;
			assert col0 > 0 && row0 > 0 && dCol1 > 0.0;

			chiSq = 0.0;
			double diff = Math.abs(expected00 - dTable00) - 0.5;
			// Don't try integer math, because you'll get overflows
			if (diff > 0.0) {
				chiSq = diff * diff / expected00;
			}
			diff = Math.abs(expected01 - table01) - 0.5;
			if (diff > 0.0) {
				chiSq += diff * diff / expected01;
			}
			diff = Math.abs(expected10 - table10) - 0.5;
			if (diff > 0.0) {
				chiSq += diff * diff / expected10;
			}
			diff = Math.abs(expected11 - table11) - 0.5;
			if (diff > 0.0) {
				chiSq += diff * diff / expected11;
			}
			// This was an attempt to color only bars that are much higher
			// or lower than the mean.
			// It doesn't seem to do what I want.
			// if (dTotal > PRACTICAL_SIGNIFICANCE_TOTAL) {
			// chiSq *= PRACTICAL_SIGNIFICANCE_TOTAL / dTotal;
			// }
			assert chiSq >= 0.0 : chiSq;

			pvalueSign = (dTable00 < expected00) ? -1 : 1;

			oddsRatio = table01 == 0.0 || table10 == 0.0 ? Double.POSITIVE_INFINITY
					: (dTable00 * table11) / (table01 * table10);
			assert oddsRatio >= 0.0 : printTable();
		}
	}

	public @NonNull String printTable() {
		// Don't use functions like col1() here, because assertion violations
		// might cause infinite recursion.
		final int col1 = total - col0;
		final int row1 = total - row0;
		final int table01 = row0 - table00;
		final int table10 = col0 - table00;
		final int table11 = col1 - table01;
		final FormattedTableBuilder align = new FormattedTableBuilder();
		align.addLine();
		align.addLine(table00, table01, row0);
		align.addLine(table10, table11, row1);
		align.addLine(col0, col1, total);
		// final StringBuffer buf = new StringBuffer();
		// buf.append("\n").append(ALIGN_8_RIGHT.format(table00,
		// DECIMAL_FORMAT))
		// .append(ALIGN_8_RIGHT.format(table01, DECIMAL_FORMAT))
		// .append(ALIGN_8_RIGHT.format(row0, DECIMAL_FORMAT));
		// buf.append("\n").append(ALIGN_8_RIGHT.format(table10,
		// DECIMAL_FORMAT))
		// .append(ALIGN_8_RIGHT.format(table11, DECIMAL_FORMAT))
		// .append(ALIGN_8_RIGHT.format(row1, DECIMAL_FORMAT));
		// buf.append("\n").append(ALIGN_8_RIGHT.format(col0, DECIMAL_FORMAT))
		// .append(ALIGN_8_RIGHT.format(col1,
		// DECIMAL_FORMAT)).append(ALIGN_8_RIGHT.format(total, DECIMAL_FORMAT));
		// return buf.toString();
		return align.format();
	}

	public static String statisticsHeading() {
		return ALIGN_13_LEFT.format("myCramersPhi") + ALIGN_13_LEFT.format("Chi square")
				+ ALIGN_13_LEFT.format("p-value") + ALIGN_13_LEFT.format("Facet Percent")
				+ ALIGN_13_LEFT.format("Sibling Percent") + ALIGN_13_LEFT.format("Percentage Ratio")
				+ ALIGN_13_LEFT.format("Correlation") + ALIGN_13_LEFT.format("Tag");
	}

	// public void setPrintObject(final T t) {
	// printObject = t;
	// }

	@SuppressWarnings("null")
	public StringBuilder statisticsLine(StringBuilder buf) {
		// if (p.getNameIfPossible() == null)
		// p.getName(mouseDoc);
		if (buf == null) {
			buf = new StringBuilder();
		}
		buf.append(ALIGN_13_LEFT.format(myCramersPhi(), DOUBLE_FORMAT));
		buf.append(ALIGN_13_LEFT.format(chiSq(), DOUBLE_FORMAT));
		buf.append(ALIGN_13_LEFT.format(pvalue(), DOUBLE_FORMAT));
		buf.append(ALIGN_13_LEFT.format(facetOnPercent(), DOUBLE_FORMAT));
		buf.append(ALIGN_13_LEFT.format(siblingOnPercent(), DOUBLE_FORMAT));
		buf.append(ALIGN_13_LEFT.format(oddsRatio(), DOUBLE_FORMAT));
		buf.append(ALIGN_13_LEFT.format(correlation(), DOUBLE_FORMAT));
		return buf;
	}
}
