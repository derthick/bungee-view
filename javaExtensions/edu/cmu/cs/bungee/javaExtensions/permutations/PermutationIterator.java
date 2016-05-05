/**
 *
 */
package edu.cmu.cs.bungee.javaExtensions.permutations;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilArray;

public class PermutationIterator<V> implements Iterator<List<V>> {

	private final @NonNull ArrayList<V> objects;
	private final IntegerPermutationIter integerPermGenerator;
	private int nRemainingPerms;
	private final @NonNull List<V> perm;
	private final @NonNull List<V> unmodifiablePerm;

	public PermutationIterator(final List<V> collection) {
		objects = new ArrayList<>(collection);
		final int nObjects = objects.size();
		integerPermGenerator = new IntegerPermutationIter(nObjects);
		nRemainingPerms = factorial(nObjects);
		perm = new ArrayList<>(nObjects);
		unmodifiablePerm = UtilArray.unmodifiableList(perm);
		for (int i = 0; i < nObjects; i++) {
			perm.add(null);
		}
	}

	@Override
	public boolean hasNext() {
		return nRemainingPerms > 0;
	}

	/*
	 * Value is a List ordered the same way as constuctor argument
	 */
	@Override
	public List<V> next() {
		nRemainingPerms--;
		final int[] integerPerm = integerPermGenerator.next();
		for (int i = 0; i < integerPerm.length; i++) {
			perm.set(i, objects.get(integerPerm[i]));
		}
		return unmodifiablePerm;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	private static int factorial(final int n) {
		if (n <= 1) {
			return 1;
		} else {
			return n * factorial(n - 1);
		}
	}

}