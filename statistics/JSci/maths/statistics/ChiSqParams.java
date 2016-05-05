package JSci.maths.statistics;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class ChiSqParams {
	private final int a;
	private final int b;
	private final int c;
	private final int d;

	/**
	 * a=myOnCount_ b _ parentOnCount
	 *
	 * c___________ d _ -
	 *
	 * myTotalCount - _ parentTotalCount
	 *
	 *
	 * @return the 4 parameters [a, b, c, d]
	 *
	 *         or NULL if the resulting ChiSq2x2 is known to be uninformative.
	 */
	public static @Nullable ChiSqParams getChiSqParams(final int parentTotalCount, final int parentOnCount,
			final int myTotalCount, final int myOnCount) {
		ChiSqParams result = null;
		if (parentTotalCount > parentOnCount && parentOnCount > 0 && parentTotalCount >= myTotalCount
				&& myTotalCount >= myOnCount && myOnCount >= 0) {
			result = new ChiSqParams(myOnCount, parentOnCount - myOnCount, myTotalCount - myOnCount,
					parentTotalCount + myOnCount - parentOnCount - myTotalCount);
		}
		return result;
	}

	// public static ChiSqParams getChiSqParamsABCD(final int a, final int b,
	// final int c, final int d) {
	// return new ChiSqParams(a, b, c, d);
	// }

	ChiSqParams(final int _a, final int _b, final int _c, final int _d) {
		super();
		a = _a;
		b = _b;
		c = _c;
		d = _d;
		assert _a >= 0 && _b >= 0 && _c >= 0 && _d >= 0 : getChiSq().printTable();
	}

	public int a() {
		return a;
	}

	public int b() {
		return b;
	}

	public int c() {
		return c;
	}

	public int d() {
		return d;
	}

	public @NonNull ChiSq2x2 getChiSq() {
		return ChiSq2x2.getInstanceABCD(a, b, c, d);
	}

	// public int parentTotalCount() {
	// return a;
	// }
	//
	// public int parentOnCount() {
	// return b;
	// }
	//
	// public int myTotalCount() {
	// return c;
	// }
	//
	// public int myOnCount() {
	// return d;
	// }

	@Override
	public String toString() {
		return UtilString.toString(this, getChiSq().printTable());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + a;
		result = prime * result + b;
		result = prime * result + c;
		result = prime * result + d;
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ChiSqParams other = (ChiSqParams) obj;
		if (a != other.a) {
			return false;
		}
		if (b != other.b) {
			return false;
		}
		if (c != other.c) {
			return false;
		}
		if (d != other.d) {
			return false;
		}
		return true;
	}

}