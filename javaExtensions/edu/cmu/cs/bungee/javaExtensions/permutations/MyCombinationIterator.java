package edu.cmu.cs.bungee.javaExtensions.permutations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.cmu.cs.bungee.javaExtensions.UtilMath;

public class MyCombinationIterator<V> implements Iterator<List<V>> {

	private final V[] objects;
	private int index = 0;
	private final int lastIndexPlusOne;

	@SuppressWarnings("unchecked")
	public MyCombinationIterator(final Collection<V> collection) {
		objects = (V[]) collection.toArray();
		lastIndexPlusOne = 1 << objects.length;
	}

	@Override
	public boolean hasNext() {
		return index < lastIndexPlusOne;
	}

	/*
	 * Value is a List ordered the same way as constuctor argument
	 */
	@Override
	public List<V> next() {
		if (index >= lastIndexPlusOne) {
			throw new NoSuchElementException();
		}
		final List<V> result = new ArrayList<>(objects.length);
		for (int i = 0; i < objects.length; i++) {
			if (UtilMath.isBit(index, i)) {
				result.add(objects[i]);
			}
		}
		index++;
		return result;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}
