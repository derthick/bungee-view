package edu.cmu.cs.bungee.javaExtensions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import jdk.nashorn.internal.ir.annotations.Immutable;

public class UtilArray {

	@SuppressWarnings("rawtypes")
	public static final @NonNull Set EMPTY_SET = emptySet();

	private static @NonNull <T> Set<T> emptySet() {
		final Set<T> result = Collections.emptySet();
		assert result != null;
		return result;
	}

	@SuppressWarnings("rawtypes")
	public static final @NonNull List EMPTY_LIST = emptyList();

	private static @NonNull <T> List<T> emptyList() {
		final List<T> result = Collections.emptyList();
		assert result != null;
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// public static @NonNull <T> List<T> singletonList(final T t) {
	// final List<T> result = Collections.singletonList(t);
	// assert result != null;
	// return result;
	// }

	public static boolean assertNoNulls(final Collection<?> collection) {
		assert !collection.contains(null);
		return true;
	}

	public static boolean assertNoNaNs(final double[] collection) {
		assert !ArrayUtils.contains(collection, Double.NaN);
		return true;
	}

	// TODO Remove unused code found by UCDetector
	// public static boolean assertAllPositive(final int[] collection) {
	// for (final int n : collection) {
	// assert n > 0 : n;
	// }
	// return true;
	// }

	// TODO Remove unused code found by UCDetector
	// public static <T> void reverse(final T[] a) {
	// final int n = a.length;
	// for (int i = 0; i <= n / 2; i++) {
	// final T p = a[i];
	// a[i] = a[n - i];
	// a[n - i] = p;
	// }
	// }

	public static <T> T[] append(final @NonNull T[] a1, final @NonNull T[] a2) {
		if (a1.length == 0) {
			return a2;
		} else if (a2.length == 0) {
			return a1;
		} else {
			@SuppressWarnings({ "unchecked", "null" })
			final @NonNull T[] a = (T[]) java.lang.reflect.Array.newInstance(a1.getClass().getComponentType(),
					a1.length + a2.length);
			System.arraycopy(a1, 0, a, 0, a1.length);
			try {
				System.arraycopy(a2, 0, a, a1.length, a2.length);
			} catch (final Exception e) {
				System.err.println("a=" + UtilString.valueOfDeep(a) + " a.length=" + a.length + " a1.length="
						+ a1.length + " a2.length=" + a2.length + " a2=" + UtilString.valueOfDeep(a2) + " a1 class="
						+ a1.getClass() + " a class=" + a.getClass());
				e.printStackTrace();
			}
			return a;
		}
	}

	// TODO Remove unused code found by UCDetector
	// public static double[] append(final double[] a1, final double[] a2) {
	// if (a1 == null) {
	// return a2;
	// } else if (a2 == null) {
	// return a1;
	// } else {
	// final double[] a = new double[a1.length + a2.length];
	// System.arraycopy(a1, 0, a, 0, a1.length);
	// System.arraycopy(a2, 0, a, a1.length, a2.length);
	// return a;
	// }
	// }

	// TODO Remove unused code found by UCDetector
	// public static String[] append(final String[] a1, final String[] a2) {
	// if (a1 == null) {
	// return a2;
	// } else if (a2 == null) {
	// return a1;
	// } else {
	// final String[] a = new String[a1.length + a2.length];
	// System.arraycopy(a1, 0, a, 0, a1.length);
	// System.arraycopy(a2, 0, a, a1.length, a2.length);
	// return a;
	// }
	// }

	// TODO Remove unused code found by UCDetector
	// public static int[] append(final int[] a1, final int[] a2) {
	// if (a1 == null) {
	// return a2;
	// } else if (a2 == null) {
	// return a1;
	// } else {
	// final int[] a = new int[a1.length + a2.length];
	// System.arraycopy(a1, 0, a, 0, a1.length);
	// System.arraycopy(a2, 0, a, a1.length, a2.length);
	// return a;
	// }
	// }

	// TODO Remove unused code found by UCDetector
	// public static Object[] copy(final Object[] a, final Class<?> type) {
	// if (a == null) {
	// return a;
	// }
	// final Object[] a2 = (Object[]) Array.newInstance(type, a.length);
	// System.arraycopy(a, 0, a2, 0, a.length);
	// return a2;
	// }
	//
	// public static double[] copy(final double[] a) {
	// if (a == null) {
	// return a;
	// }
	// final double[] a2 = new double[a.length];
	// System.arraycopy(a, 0, a2, 0, a.length);
	// return a2;
	// }

	public static boolean hasDuplicates(final Collection<?> a) {
		return a == null ? false : a.size() > new HashSet<>(a).size();
	}

	public static boolean hasDuplicates(final int[] a) {
		if (a != null) {
			for (final int element : a) {
				if (UtilString.nOccurrences(a, element) > 1) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean hasDuplicates(final Object[] a) {
		if (a != null) {
			final int n = a.length;
			if (n < 20) {
				for (int i = 0; i < n; i++) {
					if (nOccurrences(a, a[i]) > 1) {
						return true;
					}
				}
			} else {
				boolean hasNull = false;
				final Hashtable<Object, Object[]> hashtable = new Hashtable<>();
				for (int i = 0; i < n; i++) {
					final Object ai = a[i];
					if (ai == null) {
						if (hasNull) {
							return true;
						} else {
							hasNull = true;
						}
					} else if (hashtable.get(ai) != null) {
						return true;
					} else {
						hashtable.put(ai, a);
					}
				}
			}
		}
		return false;
	}

	public static @NonNull <T> T get(final SortedSet<T> set, final int i) {
		assert i >= 0 && i < set.size() : i + " " + set.size();
		final Iterator<T> it = set.iterator();
		for (int i0 = 0; i0 < i; i0++) {
			it.next();
		}
		final T result = it.next();
		assert result != null;
		return result;
	}

	public static <T> int indexOf(final SortedSet<T> ts, final T t) {
		int result = 0;
		boolean isResult = false;
		for (final T t2 : ts) {
			isResult = t2 == t;
			if (isResult) {
				break;
			}
			result++;
		}
		return isResult ? result : -1;

		// return facets.headSet(t).size();
	}

	public static @NonNull <K, V> Collection<K> inverseGet(final Map<K, V> map, final Object value) {
		final Collection<K> result = new LinkedList<>();
		for (final Entry<K, V> name : map.entrySet()) {
			if (Objects.deepEquals(value, name.getValue())) {
				result.add(name.getKey());
			}
		}
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// public static @NonNull <K, V> List<V> ensureMapListValue(final Map<K,
	// List<V>> map, final K key) {
	// List<V> value = map.get(key);
	// if (value == null) {
	// value = new LinkedList<>();
	// map.put(key, value);
	// }
	// return value;
	// }

	public static @NonNull <K, V> SortedSet<V> ensureMapSortedSetValue(final Map<K, SortedSet<V>> map, final K key) {
		SortedSet<V> value = map.get(key);
		if (value == null) {
			value = new TreeSet<>();
			map.put(key, value);
		}
		return value;
	}

	// Use Arrays.equals()
	//
	// public static boolean equals(final int[] a1, final int[] a2) {
	// final int n = a1.length;
	// boolean result = a2.length == n;
	// for (int i = 0; i < n && result; i++) {
	// result = a1[i] == a2[i];
	// }
	// return result;
	// }
	//
	// public static boolean equals(final Object[] a1, final Object[] a2) {
	// final int n = a1.length;
	// boolean result = a2.length == n;
	// for (int i = 0; i < n && result; i++) {
	// result = a1[i].equals(a2[i]);
	// }
	// return result;
	// }

	public static int intersectionCardinalilty(final int[] a1, final int[] a2) { // NO_UCD
																					// (use
																					// default)
		int n = 0;
		if (a2 != null && a1 != null) {
			for (final int element : a2) {
				if (ArrayUtils.contains(a1, element)) {
					n++;
				}
			}
		}
		return n;
	}

	// TODO Remove unused code found by UCDetector
	// public static int intersectionCardinaliltySorted(final int[] a1,
	// final int[] a2) {
	// assert !hasDuplicates(a1);
	// assert !hasDuplicates(a2);
	// int n = 0;
	// int index = 0;
	// if (a2 != null && a1 != null) {
	// final int a1l = a1.length;
	// final int a2l = a2.length;
	// for (int i = 0; i < a2l; i++) {
	// assert i == 0 || a2[i - 1] < a2[i];
	// final int elt2 = a2[i];
	// int elt1 = elt2 + 1;
	// while (index < a1l && (elt1 = a1[index]) < elt2) {
	// index++;
	// assert index == a1l || a1[index - 1] < a1[index];
	// }
	// if (elt1 == elt2) {
	// n++;
	// }
	// }
	// }
	// return n;
	// }

	public static int intersectionCardinalilty(final Object[] a1, // NO_UCD (use
																	// default)
			final Object[] a2) {
		int n = 0;
		if (a2 != null && a1 != null) {
			for (final Object element : a2) {
				if (ArrayUtils.contains(a1, element)) {
					n++;
				}
			}
		}
		return n;
	}

	/**
	 * @param s1
	 * @param s2
	 * @return whether s1 and s2 have an item in common
	 */
	public static boolean intersects(final Collection<?> s1, final Collection<?> s2) {
		for (final Object name : s2) {
			if (s1.contains(name)) {
				return true;
			}
		}
		return false;
	}

	public static <T extends Object> boolean assertIsSorted(final Collection<T> collection,
			final Comparator<Object> comparator) { // NO_UCD
		// (unused
		// code)
		final ArrayList<Object> sortedC = new ArrayList<>(new TreeSet<>(comparator));
		sortedC.addAll(collection);
		final ArrayList<T> listC = new ArrayList<>(collection);
		if (!listC.equals(sortedC)) {
			for (int i = 0; i < listC.size(); i++) {
				if (!sortedC.get(i).equals(listC.get(i))) {
					throw new AssertionError("Collection is not sorted at c[" + i + "] = '" + listC.get(i)
							+ "'. The next sorted value is '" + sortedC.get(i) + "'\n      c=" + collection
							+ "'\n sorted c=" + sortedC);
				}
			}
		}
		return true;
	}

	public static boolean isSorted(final Collection<?> c) { // NO_UCD (unused
															// code)
		return new ArrayList<>(c).equals(new ArrayList<>(new TreeSet<>(c)));
	}

	/**
	 * @return elements in s1 or s2 but not both (non-destructive).
	 */
	public static @NonNull <V> Set<V> symmetricDifference(final Collection<V> s1, final Collection<V> s2) {
		final Set<V> s = new HashSet<>(s1);
		for (final V elt : s2) {
			if (!s.remove(elt)) {
				s.add(elt);
			}
		}
		return s;
	}

	/**
	 * @return < (s1 - s2), (s2 - s1) >
	 */
	public static @NonNull <V> List<Set<V>> symmetricDifferences(final Collection<V> s1, // NO_UCD
			// (unused
			// code)
			final Collection<V> s2) {
		final List<Set<V>> result = new ArrayList<>(2);
		final Set<V> set1 = new HashSet<>(s1);
		final Set<V> set2 = new HashSet<>(s2);
		set1.removeAll(s2);
		set2.removeAll(s1);
		result.add(set1);
		result.add(set2);
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// public static Object[] setDifference(final Object[] a1, final Object[]
	// a2,
	// final Class<?> type) {
	// if (a1 == null || a2 == null) {
	// return a1;
	// }
	// final int n = intersectionCardinalilty(a1, a2);
	// if (n == 0) {
	// return a1;
	// }
	// final Object[] a = (Object[]) java.lang.reflect.Array.newInstance(type,
	// a1.length - n);
	// int j = 0;
	// for (int i = 0; i < a1.length; i++) {
	// if (!isMember(a2, a1[i])) {
	// Array.set(a, j++, a1[i]);
	// }
	// }
	// return a;
	// }

	// TODO Remove unused code found by UCDetector
	// public static int[] setDifference(final int[] a1, final int[] a2) {
	// if (a1 == null || a2 == null) {
	// return a1;
	// }
	// final int n = intersectionCardinalilty(a1, a2);
	// if (n == 0) {
	// return a1;
	// }
	// final int[] a = new int[a1.length - n];
	// int j = 0;
	// for (int i = 0; i < a1.length; i++) {
	// if (!isMember(a2, a1[i])) {
	// a[j++] = a1[i];
	// }
	// }
	// return a;
	// }

	private static int nOccurrences(final Object[] a1, final Object p) {
		int result = 0;
		if (a1 != null) {
			for (final Object element : a1) {
				if (Objects.deepEquals(element, p)) {
					result++;
				}
			}
		}
		return result;
	}

	public static @NonNull <T> T[] delete(final @NonNull T[] a1, final T p) {
		// if (a1 == null) {
		// return a1;
		// }
		final int n = nOccurrences(a1, p);
		if (n == 0) {
			return a1;
		}
		@SuppressWarnings({ "unchecked", "null" })
		final @NonNull T[] a = (T[]) java.lang.reflect.Array.newInstance(a1.getClass().getComponentType(),
				a1.length - n);
		int j = 0;
		for (final T element : a1) {
			if (element != p) {
				a[j++] = element;
				// Array.set(a, j++, element);
			}
		}
		return a;
	}

	public static @NonNull <T> T[] subArray(final T[] a, final int startInclusive) {
		@SuppressWarnings("null")
		final @NonNull T[] result = ArrayUtils.subarray(a, startInclusive, a.length);
		return result;
	}

	public static int sum(final int[] a) {
		int result = 0;
		for (final int element : a) {
			result += element;
		}
		return result;
	}

	/**
	 * reduce roundoff error
	 */
	public static double kahanSum(final double[] a) {
		double sum = 0.0;
		double correction = 0.0;
		for (final double anA : a) {
			final double corrected_next_term = anA - correction;
			final double new_sum = sum + corrected_next_term;
			correction = (new_sum - sum) - corrected_next_term;
			sum = new_sum;
		}
		return sum;
	}

	/**
	 * Remove elements not in [startInclusive, endExclusive>
	 */
	public static <S extends Collection<?>> void retainSubset(final S collection, // NO_UCD
			// (unused
			// code)
			final int startInclusive, final int endExclusive) {
		assert collection.size() >= endExclusive && endExclusive > startInclusive
				&& startInclusive >= 0 : startInclusive + "-" + endExclusive + " " + collection.size();
		int i = 0;
		for (final Iterator<?> it = collection.iterator(); it.hasNext(); it.next(), i++) {
			if (i < startInclusive || i >= endExclusive) {
				it.remove();
			}
		}
	}

	@SafeVarargs
	public static @NonNull <T> ArrayList<T> getArrayList(final T... ts) { // NO_UCD
		// (unused
		// code)
		return new ArrayList<>(Arrays.asList(ts));
	}

	/**
	 * Use this if List may be modified (specifically, if size may change,
	 * because array-backed Lists don't support this). Since modifiable but
	 * size-invariant lists are rarely needed, I didn't create a method to
	 * create them.
	 */
	@SafeVarargs
	public static @NonNull <T> LinkedList<T> getLinkedList(final T... ts) { // NO_UCD
		// (unused
		// code)
		return new LinkedList<>(Arrays.asList(ts));
	}

	@SafeVarargs
	public static @NonNull @Immutable <T> List<T> getUnmodifiableList(final T... ts) { // NO_UCD
		// (unused
		// code)
		final List<T> result = Collections.unmodifiableList(Arrays.asList(ts));
		assert result != null;
		return result;
	}

	// public static <T> List<T> getUnmodifiableList(final T t1, final T t2) {
	// final List<T> list = new ArrayList<>(1);
	// list.add(t1);
	// list.add(t2);
	// return Collections.unmodifiableList(list);
	// }
	//
	// public static <T> List<T> getUnmodifiableList(final T t1, final T t2, //
	// NO_UCD
	// // (unused
	// // code)
	// final T t3) {
	// final List<T> list = new ArrayList<>(1);
	// list.add(t1);
	// list.add(t2);
	// list.add(t3);
	// return Collections.unmodifiableList(list);
	// }
	//
	// public static <T> List<T> getUnmodifiableList(final T t1, final T t2, //
	// NO_UCD
	// // (unused
	// // code)
	// final T t3, final T t4) {
	// final List<T> list = new ArrayList<>(1);
	// list.add(t1);
	// list.add(t2);
	// list.add(t3);
	// list.add(t4);
	// return Collections.unmodifiableList(list);
	// }
	//
	// public static <T> List<T> getUnmodifiableList(final T t1, final T t2,
	// final T t3, final T t4, final T t5) {
	// final List<T> list = new ArrayList<>(1);
	// list.add(t1);
	// list.add(t2);
	// list.add(t3);
	// list.add(t4);
	// list.add(t5);
	// return Collections.unmodifiableList(list);
	// }

	/**
	 * @return the element before element, or null if element is not in
	 *         collection or is the first element.
	 */
	public static @Nullable <T> T previous(final Collection<T> collection, final T element) {
		@Nullable
		T result = null;
		@Nullable
		T prev = null;
		for (final T next : collection) {
			if (next.equals(element)) {
				result = prev;
				break;
			} else {
				prev = next;
			}
		}
		return result;
	}

	/**
	 * @return the element after element, or null if element is not in
	 *         collection or is the last element.
	 */
	public static @Nullable <T> T next(final Collection<T> collection, final T element) {
		@Nullable
		T result = null;
		for (final Iterator<T> it = collection.iterator(); it.hasNext();) {
			if (it.next().equals(element)) {
				if (it.hasNext()) {
					result = it.next();
				}
				break;
			}
		}
		return result;
	}

	/**
	 * @return sublist starting from from, or the empty list if from is not
	 *         included.
	 */
	public static @NonNull <T> List<T> sublist(final List<T> list, final T from) {
		int index = list.indexOf(from);
		// assert index >= 0 : from + " is not in " + list;
		final int size = list.size();
		if (index < 0) {
			index = size;
		}
		final List<T> result = list.subList(index, size);
		assert result != null;
		return result;
	}

	/**
	 * @return sublist prior to to (exclusive), or the empty list if
	 *         !list.includes(to).
	 */
	public static @NonNull <T> List<T> sublistTo(final List<T> list, final T to) {
		int index = list.indexOf(to);
		// assert index >= 0 : to + " is not in " + list;
		if (index < 0) {
			index = 0;
		}
		final List<T> result = list.subList(0, index);
		assert result != null;
		return result;
	}

	static @NonNull <E> ArrayList<E> tailList(final List<E> list, final E element) {
		final int elementIndex = list.indexOf(element);
		return new ArrayList<>(list.subList(elementIndex, list.size()));
	}

	/**
	 * @param collection
	 * @return an element of collection, or null if collection is empty
	 */
	public static @Nullable <T> T some(final Collection<T> collection) {
		@Nullable
		T result = null;
		final Iterator<T> iterator = collection.iterator();
		if (iterator.hasNext()) {
			result = iterator.next();
		}
		return result;
	}

	/**
	 * @param msg
	 *            only used in assert statement
	 * @return true, or throw AssertionError
	 */
	public static <K, V> boolean putNew(final @NonNull Map<K, V> map, final @NonNull K k, final @NonNull V v,
			final @Nullable Object msg) {
		final V oldValue = map.put(k, v);
		assert oldValue == null : msg + " key=" + k + "\n    " + oldValue + "\n => " + v;
		return true;
	}

	public static @NonNull <T> List<T> unmodifiableList(final List<T> list) {
		final List<T> result = Collections.unmodifiableList(list);
		assert result != null;
		return result;
	}

}
