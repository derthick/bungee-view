package edu.cmu.cs.bungee.client.query.markup;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * required and excluded lists of restrictions
 */
public class Restrictions implements Serializable {

	private static final long serialVersionUID = 1L;

	private final @NonNull SortedSet<Perspective> require = new TreeSet<>();
	private final @NonNull SortedSet<Perspective> exclude = new TreeSet<>();
	/**
	 * This is kept up to date and always equals require+exclude
	 */
	private final @NonNull SortedSet<Perspective> allRestrictions = new TreeSet<>();
	/**
	 * This is kept up to date and always equals allRestrictions
	 */
	private final @NonNull List<Perspective> allRestrictionsList = new LinkedList<>();

	@SuppressWarnings("null")
	private final @Immutable @NonNull SortedSet<Perspective> unmodifiableRequire = Collections
			.unmodifiableSortedSet(require);
	@SuppressWarnings("null")
	private final @Immutable @NonNull SortedSet<Perspective> unmodifiableExclude = Collections
			.unmodifiableSortedSet(exclude);
	@SuppressWarnings("null")
	private final @Immutable @NonNull SortedSet<Perspective> unmodifiableAllRestrictions = Collections
			.unmodifiableSortedSet(allRestrictions);
	@SuppressWarnings("null")
	private final @Immutable @NonNull List<Perspective> unmodifiableAllRestrictionsList = Collections
			.unmodifiableList(allRestrictionsList);

	public static final @NonNull Restrictions EMPTY_RESTRICTIONS = new Restrictions();

	public Restrictions(final @NonNull Restrictions _restrictions) {
		this(_restrictions.require, _restrictions.exclude);
	}

	Restrictions(final @NonNull Collection<Perspective> _require, final @NonNull Collection<Perspective> _exclude) {
		super();
		for (final Perspective perspective : _require) {
			assert perspective != null;
			add(perspective, true);
		}
		for (final Perspective perspective : _exclude) {
			assert perspective != null;
			add(perspective, false);
		}
	}

	public Restrictions() {
		super();
	}

	public void clear() {
		require.clear();
		exclude.clear();
		allRestrictions.clear();
		allRestrictionsList.clear();
	}

	public boolean delete(final @NonNull Perspective child) {
		return delete(child, isRestriction(child, true));
	}

	public void deleteAll(final @NonNull Collection<Perspective> toRemoves, final boolean polarity) {
		for (final Perspective toRemove : toRemoves) {
			assert toRemove != null;
			delete(toRemove, polarity);
		}
	}

	boolean delete(final @NonNull Perspective child, final boolean polarity) {
		// System.out.println("Perspective.delete " + this + "." + child + " " +
		// polarity);
		final boolean result = isRestriction(child, polarity);
		if (result) {
			if (polarity) {
				require.remove(child);
			} else {
				exclude.remove(child);
			}
			allRestrictions.remove(child);
			allRestrictionsList.remove(child);
		}
		return result;
	}

	public void add(final @Nullable Restrictions childRestrictions) {
		if (childRestrictions != null) {
			for (final boolean polarity : Util.BOOLEAN_VALUES) {
				for (final Perspective child : childRestrictions.restrictions(polarity)) {
					assert child != null;
					add(child, polarity);
				}
			}
		}
	}

	/**
	 * Remove implied restrictions: remove positive ancestors and negative
	 * descendents.
	 */
	public void canonicalize() {
		for (final boolean polarity : Util.BOOLEAN_VALUES) {
			final Collection<Perspective> toRemove = new LinkedList<>();
			for (final Perspective child : restrictions(polarity)) {
				for (final Perspective ancestor : restrictions(polarity)) {
					assert ancestor != null;
					if (child != ancestor && child.hasAncestor(ancestor)) {
						toRemove.add(polarity ? ancestor : child);
					}
				}
			}
			deleteAll(toRemove, polarity);
		}
	}

	public void add(final @NonNull Perspective child, final boolean polarity) {
		// System.out.println("add " + child);
		assert child != null;
		assert !polarity || child.getTotalCount() > 0 : child.path(true, true) + " polarity=" + polarity + "\n require="
				+ require + "\n exclude=" + exclude;
		assert polarity || child.getTotalCount() < child.query().getTotalCount() : child.path(true, true) + " polarity="
				+ polarity + "\n require=" + require + "\n exclude=" + exclude;
		assert !isRestriction(child, polarity) : child.path(true, true) + " polarity=" + polarity + "\n require="
				+ require + "\n exclude=" + exclude;
		if (polarity) {
			assert !exclude.contains(child) : child.path(true, true) + " polarity=" + polarity + "\n require=" + require
					+ "\n exclude=" + exclude;
			require.add(child);
		} else {
			assert !require.contains(child) : child.path(true, true) + " polarity=" + polarity + "\n require=" + require
					+ "\n exclude=" + exclude;
			exclude.add(child);
		}
		allRestrictions.add(child);
		allRestrictionsList.add(child);
	}

	public int nRestrictions() {
		return allRestrictions.size();
	}

	public int nRestrictions(final boolean polarity) {
		return restrictions(polarity).size();
	}

	public boolean isRestricted() {
		return !allRestrictions.isEmpty();
	}

	public boolean isRestricted(final boolean polarity) {
		return !restrictions(polarity).isEmpty();
	}

	public boolean isRestriction(final @NonNull Perspective child) {
		return allRestrictions.contains(child);
	}

	public boolean isRestriction(final @NonNull Perspective child, final boolean polarity) {
		return restrictions(polarity).contains(child);
	}

	public @Immutable @NonNull SortedSet<Perspective> restrictions(final boolean polarity) {
		return polarity ? unmodifiableRequire : unmodifiableExclude;
	}

	// public @NonNull SortedSet<Perspective> allRestrictionsReference() {
	// return allRestrictions;
	// }

	public @Immutable @NonNull SortedSet<Perspective> allRestrictions() {
		return unmodifiableAllRestrictions;
	}

	public @Immutable @NonNull List<Perspective> allRestrictionsList() {
		return unmodifiableAllRestrictionsList;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, require + ", " + exclude);
	}
}