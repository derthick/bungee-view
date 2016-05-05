package JSci.maths.statistics;

public class FisherExactTest {
	private double sn11;
	private double sn;
	private double sn1_;
	private double sn_1;
	private double sprob;
	private double sleft;
	private double sright;
	private double sless;
	private double slarg;
	private double left;
	private double right;

	// private double twotail;

	public FisherExactTest(final ChiSqParams chiSqParams) {
		exact22(chiSqParams.a(), chiSqParams.b(), chiSqParams.c(),
				chiSqParams.d());
	}

	// public FisherExactTest(final int a, final int b, final int c, final int
	// d) {
	// if (a < 0 || b < 0 || c < 0 || d < 0) {
	// throw new OutOfRangeException(
	// "Parameters of FisherExactTest constructor less than 0 found; parameters must equal or greater than 0");
	// }
	// exact22(a, b, c, d);
	// }

	public static double lngamm(final double z)
	// Reference: "Lanczos, C. 'A precision approximation
	// of the gamma function', J. SIAM Numer. Anal., B, 1, 86-96, 1964."
	// Translation of Alan Miller's FORTRAN-implementation
	// See http://lib.stat.cmu.edu/apstat/245
	{
		double x = 0.0;
		x += 0.1659470187408462e-06 / (z + 7.0);
		x += 0.9934937113930748e-05 / (z + 6.0);
		x -= 0.1385710331296526 / (z + 5.0);
		x += 12.50734324009056 / (z + 4.0);
		x -= 176.6150291498386 / (z + 3.0);
		x += 771.3234287757674 / (z + 2.0);
		x -= 1259.139216722289 / (z + 1.0);
		x += 676.5203681218835 / (z);
		x += 0.9999999999995183;
		return (Math.log(x) - 5.58106146679532777 - z + (z - 0.5)
				* Math.log(z + 6.5));
	}

	public static double lnfact(final double n) {
		if (n <= 1.0) {
			return (0.0);
		}
		return (lngamm(n + 1.0));
	}

	public static double lnbico(final double n, final double k) {
		return (lnfact(n) - lnfact(k) - lnfact(n - k));
	}

	/*
	 * 
	 * private double FisherExactCompute(int min,int diag,int other1,int
	 * other2){ double prob=0,sprob=0; int i;
	 * 
	 * for(i=0;i<=min;i++){ if(i==0 || ((min-i)%10)==0){ sprob =
	 * hyper_323(min-i,min+other1,min+other2,min+diag+other1+other2); }else{
	 * sprob = sprob * (min-i+1) / (other1+i) * (diag-i+1) / (other2+i); } prob
	 * += sprob; }
	 * 
	 * return prob; }
	 * 
	 * public double FisherExact(int a,int b,int c,int d) { if(a<=b && a<=c &&
	 * a<=d){ return FisherExactCompute(a,d,b,c); }else if(b<=a && b<=c &&
	 * b<=d){ return FisherExactCompute(b,c,a,d); }else if(c<=a && c<=b &&
	 * c<=d){ return FisherExactCompute(c,b,a,d); }else{ // d<=a && d<=b && d<=c
	 * return FisherExactCompute(d,a,b,c); } }
	 */

	public static double hyper_323(final double n11, final double n1_,
			final double n_1, final double n) {
		return (Math.exp(lnbico(n1_, n11) + lnbico(n - n1_, n_1 - n11)
				- lnbico(n, n_1)));
	}

	public double hyper0(final double n11i, final double n1_i,
			final double n_1i, final double ni) {
		if (n1_i == 0.0 && n_1i == 0.0 && ni == 0.0) {
			if (!(n11i % 10.0 == 0.0)) {
				if (n11i == sn11 + 1.0) {
					sprob *= ((sn1_ - sn11) / (n11i))
							* ((sn_1 - sn11) / (n11i + sn - sn1_ - sn_1));
					sn11 = n11i;
					return sprob;
				}
				if (n11i == sn11 - 1.0) {
					sprob *= ((sn11) / (sn1_ - n11i))
							* ((sn11 + sn - sn1_ - sn_1) / (sn_1 - n11i));
					sn11 = n11i;
					return sprob;
				}
			}
			sn11 = n11i;
		} else {
			sn11 = n11i;
			sn1_ = n1_i;
			sn_1 = n_1i;
			sn = ni;
		}
		sprob = hyper_323(sn11, sn1_, sn_1, sn);
		return sprob;
	}

	public double hyper(final double n11) {
		return (hyper0(n11, 0.0, 0.0, 0.0));
	}

	public double exact(final int n11, final int n1_, final int n_1, final int n) {
		double prob;
		final int max = Math.min(n_1, n1_);
		// int max = n1_;
		// if (n_1 < max) {
		// max = n_1;
		// }
		final int min = Math.max(0, n1_ + n_1 - n);
		// int min = n1_ + n_1 - n;
		// if (min < 0) {
		// min = 0;
		// }
		if (min == max) {
			sless = 1.0;
			sright = 1.0;
			sleft = 1.0;
			slarg = 1.0;
			prob = 1.0;
		} else {
			prob = hyper0(n11, n1_, n_1, n);
			final int i = exactInternal(min, false, prob);
			final int j = exactInternal(max, true, prob);

			// sleft = 0.0;
			// double p = hyper(min);
			// int i;
			// for (i = min + 1; p < 0.99999999 * prob; i++) {
			// sleft += p;
			// p = hyper(i);
			// }
			// i--;
			// if (p < 1.00000001 * prob) {
			// sleft += p;
			// } else {
			// i--;
			// }
			// sright = 0.0;
			// p = hyper(max);
			// int j;
			// for (j = max - 1; p < 0.99999999 * prob; j--) {
			// sright += p;
			// p = hyper(j);
			// }
			// j++;
			// if (p < 1.00000001 * prob) {
			// sright += p;
			// } else {
			// j++;
			// }

			if (Math.abs(i - n11) < Math.abs(j - n11)) {
				sless = sleft;
				slarg = 1.0 - sleft + prob;
			} else {
				sless = 1.0 - sright + prob;
				slarg = sright;
			}
		}
		// System.out.println("FisherExactTest.exact n11=" + n11 + " n1=" + n1_
		// + " n_1=" + n_1 + " n=" + n + " min=" + min + " max=" + max
		// + " prob=" + prob + "\n sless=" + sless + " slarg=" + slarg
		// + "\n sleft=" + sleft + " sright=" + sright);
		return prob;
	}

	private int exactInternal(final int minOrMax, final boolean isRight,
			final double prob) {
		double sLeftOrRight = 0.0;
		double p = hyper(minOrMax);
		final int increment = isRight ? -1 : 1;
		int j;
		for (j = minOrMax + increment; p < 0.99999999 * prob; j += increment) {
			sLeftOrRight += p;
			p = hyper(j);
		}
		j++;
		if (p < 1.00000001 * prob) {
			sLeftOrRight += p;
		} else {
			j++;
		}
		if (isRight) {
			sright = sLeftOrRight;
		} else {
			sleft = sLeftOrRight;
		}
		return j;
	}

	private void exact22(final int n11, final int n12, final int n21,
			final int n22) {
		final int n1_ = n11 + n12;
		final int n_1 = n11 + n21;
		final int n = n11 + n12 + n21 + n22;
		exact(n11, n1_, n_1, n);
		left = sless;
		right = slarg;
		// double twotail = sleft + sright;
		// if (twotail > 1.0) {
		// twotail = 1.0;
		// }
	}

	public Double getLeft() {
		return Double.valueOf(left);
	}

	public Double getRight() {
		return Double.valueOf(right);
	}

	private static final Double ONE = Double.valueOf(1.0);

	public Double getTwoTail() {
		if (sleft + sright >= 1.0) {
			return ONE;
		} else {
			return Double.valueOf(sleft + sright);
		}
	}

	public double getTwoTailPrimitive() {
		return Math.min(1.0, sleft + sright);
	}
}
