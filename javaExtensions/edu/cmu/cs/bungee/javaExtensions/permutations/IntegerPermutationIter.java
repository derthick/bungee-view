package edu.cmu.cs.bungee.javaExtensions.permutations;

import java.util.Iterator;
import java.util.NoSuchElementException;

/* ==========================================
 * JGraphT : a free Java graph-theory library
 * ==========================================
 *
 * Project Info:  http://jgrapht.sourceforge.net/
 * Project Creator:  Barak Naveh (http://sourceforge.net/users/barak_naveh)
 *
 * (C) Copyright 2003-2006, by Barak Naveh and Contributors.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */
/* -----------------
 * IntegerPermutationIter.java
 * -----------------
 * (C) Copyright 2005-2006, by Assaf Lehr and Contributors.
 *
 * Original Author:  Assaf Lehr
 * Contributor(s):   -
 *
 * $Id: IntegerPermutationIter.java,v 1.2 2007/05/22 15:52:58 kjellw Exp $
 *
 * Changes
 * -------
 */

/**
 * Iterates through permutations of N elements.
 * <li>use getNext() to get the next permutation order, for example(N=4):
 * perm0=[1,2,3,4] perm1=[1,2,4,3] perm2=[1,3,2,4] .
 * <li>use hasNext() or verify by counter<getTotalNumberOfPermutations () that
 * you do not overflow the max number. RunTimeException will be thrown if you
 * do. Getting the next permutation is done in O(N) Note: This class is not
 * thread safe.
 *
 * @author Assaf
 * @since May 20, 2005
 */
class IntegerPermutationIter implements Iterator<int[]> {
	// ~ Instance fields
	// --------------------------------------------------------

	private int[] value;

	private int n;

	private long permutationCounter;

	private boolean endWasReached = false;

	private boolean wasNextValueCalculatedAlready = false;

	/**
	 * Will hold the current value. Will be changed only by getNext()
	 */
	private int[] currentValueBackup;

	// ~ Constructors
	// -----------------------------------------------------------

	/**
	 * Creates an array of size N with elements 1,2,...,n-1 Useful for
	 * describing regular permutations. See IntegerPermutationIter(int[] array)
	 * for the other kind of permutations; efficency of initiation is O(N)
	 *
	 * @param _N
	 */
	IntegerPermutationIter(final int _N) {
		final int[] newArray = new int[_N];

		// fill the array with 1,2,3...
		for (int i = 0; i < newArray.length; i++) {
			newArray[i] = i;
		}
		init(newArray);
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * Uses a predefined array (sorted), for example:
	// [3,1,1,2,1]-->[1,1,1,2,3];
	// * note that there are much less than 5! premutations here, because of the
	// * repetitive 1s.
	// *
	// * @param array
	// * creates a copy of it (so sort / later changes will not matter)
	// */
	// public IntegerPermutationIter(int[] array) {
	// int[] newArray = new int[array.length];
	// System.arraycopy(array, 0, newArray, 0, array.length);
	// Arrays.sort(newArray);
	// init(newArray);
	// }

	// ~ Methods
	// ----------------------------------------------------------------

	private void init(final int[] array) {
		n = array.length;
		value = array;
		currentValueBackup = value;
		permutationCounter = 0;
	}

	/**
	 * Swaps by array indexes
	 *
	 * @param i
	 * @param j
	 */
	private void swap(final int i, final int j) {
		final int temp = value[i];
		value[i] = value[j];
		value[j] = temp;
	}

	private static int[] arrayClone(final int[] sourceArray) {
		final int[] destArray = new int[sourceArray.length];
		System.arraycopy(sourceArray, 0, destArray, 0, sourceArray.length);
		return destArray;
	}

	private int[] getNextStartingWith2() {
		permutationCounter++;
		int i = n - 1;

		if (i <= 0) // may happen only on N<=1

		{
			endWasReached = true;
			return null;
		}

		/**
		 * while (Value[i-1] >= Value[i]) { i = i-1; }
		 */
		while (value[i - 1] >= value[i]) {
			i = i - 1;
			if (i == 0) {
				endWasReached = true;
				return null;
			}
		}

		int j = n;

		while (value[j - 1] <= value[i - 1]) {
			j = j - 1;
		}

		swap(i - 1, j - 1); // swap values at positions (i-1) and (j-1)

		i++;
		j = n;

		while (i < j) {
			swap(i - 1, j - 1);
			i++;
			j--;
		}
		return value;
	}

	/**
	 * Efficiency: O(N) implementation, try to take the next!
	 */
	@Override
	public boolean hasNext() {
		if ((permutationCounter == 0) || (wasNextValueCalculatedAlready)) {
			return true;
		} else if (endWasReached) {
			return false;
		}

		final boolean result = true;
		// calculate the next value into value save the current result. in
		// the end swap the arrays there is no way to know when to stop , but
		// the out-of-bound
		/*
		 * try { wasNextValueCalculatedAlready=true; getNextStartingWith2(); }
		 * catch (ArrayIndexOutOfBoundsException outOfBoundException) {
		 * endWasReached=true; result=false; }
		 */

		getNextStartingWith2();
		wasNextValueCalculatedAlready = true;
		if (endWasReached) {
			return false;
		}

		// ////////////////////////////
		return result;
	}

	@Override
	public int[] next() {
		int[] result = null;
		if (hasNext()) {
			// if it is the first one , return original
			int[] internalArray;
			if (permutationCounter == 0) {
				permutationCounter++;
				internalArray = value;
			} else {
				// if hasNext() has precaclulated it , take this value.
				if (wasNextValueCalculatedAlready) {
					internalArray = value;
					wasNextValueCalculatedAlready = false;
				} else {
					internalArray = getNextStartingWith2();
					// if (endWasReached) {
					// return null;
					// }
				}
			}
			if (!endWasReached) {
				currentValueBackup = arrayClone(internalArray);
				result = arrayClone(internalArray);
			}
		}
		if (result == null) {
			throw new NoSuchElementException();
		}
		return result;
	}

	public int[] getCurrent() {
		return arrayClone(currentValueBackup);
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * Utility method to convert the array into a string examples: [] [0]
	// * [0,1][1,0]
	// *
	// * @param array
	// */
	// public static String toString(final int[] array) {
	// if (array.length <= 0) {
	// return "[]";
	// }
	// final StringBuilder stBuffer = new StringBuilder("[");
	// for (int i = 0; i < (array.length - 1); i++) {
	// stBuffer.append(array[i]).append(",");
	// }
	// stBuffer.append(array[array.length - 1]).append("]");
	// return stBuffer.toString();
	// }

	/**
	 * UNIMPLEMENTED. always throws new UnsupportedOperationException
	 *
	 * @see java.util.Iterator#remove()
	 */
	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	// TODO Remove unused code found by UCDetector
	// public int[] nextPermutation() {
	// return next();
	// }

	// TODO Remove unused code found by UCDetector
	// public boolean hasNextPermutaions() {
	// return hasNext();
	// }
}

// End IntegerPermutationIter.java
