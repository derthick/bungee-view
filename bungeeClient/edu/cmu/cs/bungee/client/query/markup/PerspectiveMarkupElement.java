package edu.cmu.cs.bungee.client.query.markup;

import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.ItemPredicate;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query.DescriptionCategory;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;

/**
 * A DefaultMarkupElement with public final Perspective perspective;
 */
public class PerspectiveMarkupElement extends DefaultMarkupElement implements ItemPredicate {

	public final @NonNull Perspective perspective;

	protected final boolean isNegated;

	public PerspectiveMarkupElement(final @NonNull Perspective _perspective, final boolean _plural,
			final boolean _negated) {
		super(_plural);
		assert _perspective != null;
		perspective = _perspective;
		isNegated = _negated;
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		assert !isPlural : this;
		return perspective.getMarkupElement(true, isNegated);
	}

	@Override
	public @NonNull PerspectiveMarkupElement negate() {
		assert !isNegated;
		return perspective.getMarkupElement(isPlural, true);
	}

	@Override
	public boolean shouldUnderline() {
		return true;
	}

	/**
	 * @return always >= 0
	 */
	public int getOnCount() {
		return perspective.getOnCount();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, perspective + " isPlural=" + isPlural + " isNegated=" + isNegated);
	}

	@Override
	public boolean isEffectiveChildren() {
		return perspective.isEffectiveChildren();
	}

	@Override
	public @NonNull String getName() {
		return getName(null);
	}

	@Override
	public @NonNull String getName(final @Nullable RedrawCallback callback) {
		String name = perspective.getName(callback);
		if (isPlural) {
			name = UtilString.pluralize(name);
		}
		if (isNegated) {
			name = " (but NOT " + name + ")";
		}
		return name;
	}

	// @Override
	// public @Nullable MarkupElement merge(final @NonNull MarkupElement
	// nextElement) {
	// MarkupElement result = null;
	// if (nextElement instanceof ItemPredicate
	// && perspective.next() == ((ItemPredicate) nextElement).fromPerspective())
	// {
	// final SequenceInfo sequenceInfo = new SequenceInfo(this);
	// if (sequenceInfo.merge((ItemPredicate) nextElement)) {
	// final Perspective toPerspective = ((ItemPredicate)
	// nextElement).toPerspective();
	// assert toPerspective != null;
	// result = new MarkupPerspectiveRange(perspective, toPerspective, isPlural,
	// isNegated);
	// }
	// }
	// return result;
	// }

	@Override
	public @Nullable MarkupElement merge(final @NonNull MarkupElement nextElement) {
		final MarkupElement result = (nextElement instanceof ItemPredicate
		// For Query description, group elements by FacetType rather than facet.
		// && perspective.getParent() == ((ItemPredicate)
		// nextElement).fromPerspective().getParent()
		) ? new SequenceInfo(this).merge((ItemPredicate) nextElement) : null;
		// System.out.println("PerspectiveMarkupElement.merge " + this + " " +
		// nextElement + " => " + result);
		return result;
	}

	/**
	 * Substitute restrictions(polarity) (possibly adding connectors) into
	 * descriptionPreposition(polarity), insert color tags for !polarity case,
	 * and combine polarities into a linear Markup.
	 *
	 *
	 * @param descriptionPreposition
	 *            e.g. " that are ~ cm² ; that aren't ~ cm² "
	 *
	 * @return if restrictions is empty, emptyMarkup()
	 *
	 *         else [{descriptionCategory} that {itemPredicate} EXCLUDED_COLOR
	 *         (but that aren't {itemPredicate}) DEFAULT_COLOR_TAG]
	 */
	public @NonNull Markup getPhraseFromRestrictions(final @NonNull DescriptionPreposition descriptionPreposition,
			final @NonNull DescriptionCategory descriptionCategory) {
		final Restrictions restrictions = descendentRestrictions();

		// 1 for polarity==POLARITY_POSITIVE;
		// 1 or 2 for polarity==POLARITY_NEGATIVE
		int nPolaritiesUsed = 0;

		final Markup result = DefaultMarkup.emptyMarkup();
		for (final boolean polarity : Util.BOOLEAN_VALUES) {
			final SortedSet<Perspective> polarityRestrictions = restrictions.restrictions(polarity);
			if (polarityRestrictions.size() > 0) {
				nPolaritiesUsed++;
				if (result.size() == 0) {
					result.add(descriptionCategory);
					if (nPolaritiesUsed == 1 && !polarity && descriptionCategory.equals(DescriptionCategory.OBJECT)) {
						// No positive objects, so need generic stand-in
						result.add(DefaultMarkup.GENERIC_OBJECT_LABEL);
						nPolaritiesUsed++;
					}
				}
				final Markup polarityDesc = DefaultMarkup.newMarkup(polarityRestrictions)
						.compile(perspective.query().getGenericObjectLabel(false));
				updatePolarityDesc(descriptionPreposition, nPolaritiesUsed == 2, polarity, polarityDesc);
				// System.out.println("PerspectiveMarkupElement.getPhraseFromRestrictions
				// " + this + " polarity="
				// + polarity + "\n polarityDesc=" + polarityDesc + "\n
				// polarityRestrictions="
				// + polarityRestrictions);
				result.addAll(polarityDesc);
			}
		}
		return result;
	}

	/**
	 * @return the subset of Query.getRestrictions() that hasAncestor(this).
	 */
	@NonNull
	public Restrictions descendentRestrictions() {
		if (!perspective.isInstantiated()) {
			return Restrictions.EMPTY_RESTRICTIONS;
		}
		final Restrictions nonLocalRestrictions = new Restrictions(perspective.query().getNonImpliedRestrictions());
		for (final boolean polarity : Util.BOOLEAN_VALUES) {
			final Collection<Perspective> toRemove = new LinkedList<>();
			for (final Perspective restriction : nonLocalRestrictions.restrictions(polarity)) {
				if (!restriction.hasAncestor(perspective)) {
					toRemove.add(restriction);
				}
			}
			nonLocalRestrictions.deleteAll(toRemove, polarity);
		}
		return nonLocalRestrictions;
	}

	/**
	 * Only called by describeRestrictions
	 *
	 * @param descriptionPreposition
	 *
	 * @param descriptionPrepositionPattern
	 *            e.g. " that aren't ~ cm² "
	 *
	 * @param polarityDesc
	 *            If descriptionPrepositionPattern!=null, destructively update
	 *            from, e.g. [5xx or 6xx] to
	 *
	 *            [EXCLUDED_COLOR that aren't 5xx or 6xx cm² DEFAULT_COLOR_TAG]
	 */
	private static void updatePolarityDesc(final DescriptionPreposition descriptionPreposition,
			final boolean isBothPolaritiesUsed, final boolean polarity, final @NonNull Markup polarityDesc) {
		final @Nullable MarkupStringElement descriptionPrepositionPattern = descriptionPreposition.getPattern(polarity);
		if (descriptionPrepositionPattern != null) {
			final @NonNull String pattern = descriptionPrepositionPattern.getName();
			assert pattern.length() > 0 : descriptionPrepositionPattern;
			final int index = pattern.indexOf(DefaultMarkup.FILTER_TYPE_SUBSTITUTE);
			if (index >= 0) {
				// surround existing ~ replacement with the rest of the pattern
				polarityDesc.add(0, MarkupStringElement.getElement(Util.nonNull(pattern.substring(0, index))));
				polarityDesc.add(Util.nonNull(pattern.substring(index + 1)));
			} else if (isBothPolaritiesUsed) {
				// ignore pattern; probably ' NOT '
				polarityDesc.set(0, ((ItemPredicate) polarityDesc.get(0)).negate());
			} else {
				// sometimes, at least, this is a redundant ' '
				polarityDesc.add(0, descriptionPrepositionPattern);
			}
			if (!polarity) {
				// Assume current color is default
				polarityDesc.add(0, DefaultMarkup.EXCLUDED_COLOR);
				polarityDesc.add(DefaultMarkup.DEFAULT_COLOR_TAG);
			}
		}
	}

	/**
	 * @return convert sequential Perspectives into MarkupPerspectiveRanges.
	 */
	public static @NonNull Collection<MarkupElement> mergePerspectives(
			final @NonNull SortedSet<Perspective> perspectives) {
		final Collection<MarkupElement> result = new LinkedList<>();
		Perspective first = null;
		Perspective last = null;
		for (final Perspective perspective : perspectives) {
			if (last == null || last.next() != perspective) {
				maybeAddRange(first, last, result);
				first = perspective;
			}
			last = perspective;
		}
		maybeAddRange(first, last, result);
		return result;
	}

	private static void maybeAddRange(final @Nullable Perspective start, final @Nullable Perspective last,
			final Collection<MarkupElement> result) {
		if (last != null) {
			assert start != null;
			result.add((start == last) ? start : new MarkupPerspectiveRange(start, last, false, false));
		}
	}

	public @NonNull Markup defaultClickDesc() {
		assert getParentPME() != null : perspective;
		final Markup result = DefaultMarkup.newMarkup(DefaultMarkup.FILTER_CONSTANT_ARROW_KEYS, getParentPME(),
				DefaultMarkup.FILTER_CONSTANT_FROM, this);
		return result;
	}

	public @Nullable PerspectiveMarkupElement getParentPME() {
		final Perspective parent = perspective.getParent();
		final PerspectiveMarkupElement result = parent != null ? parent.getMarkupElement() : null;
		return result;
	}

	@Override
	public int compareTo(final ItemPredicate o) {
		return compareBy().compareTo(o.compareBy());
	}

	@Override
	public @NonNull SortedSet<Perspective> getFacets() {
		final SortedSet<Perspective> result = new TreeSet<>();
		result.add(perspective);
		return result;
	}

	@Override
	public @NonNull Perspective compareBy() {
		return perspective;
	}

	@Override
	public @NonNull Perspective fromPerspective() {
		return perspective;
	}

	@Override
	public @NonNull Perspective toPerspective() {
		return perspective;
	}

	@Override
	public @NonNull YesNoMaybe isPlural() {
		return YesNoMaybe.asYesNoMaybe(isPlural);
	}

	@Override
	public @NonNull YesNoMaybe isNegated() {
		return YesNoMaybe.asYesNoMaybe(isNegated);
	}

	@Override
	public int getTotalCount() {
		return perspective.getTotalCount();
	}

	static class SequenceInfo {
		final @NonNull ItemPredicate itemPredicate;
		@NonNull
		YesNoMaybe isPlural = YesNoMaybe.MAYBE;
		@NonNull
		YesNoMaybe isNegated = YesNoMaybe.MAYBE;

		SequenceInfo(final @NonNull ItemPredicate _itemPredicate) {
			itemPredicate = _itemPredicate;
			isPlural = itemPredicate.isPlural();
			isNegated = itemPredicate.isNegated();
		}

		public @Nullable MarkupElement merge(final @NonNull ItemPredicate nextElement) {
			MarkupElement result = null;
			if (isPluralNnegatedCompatible(nextElement)) {
				final SortedSet<Perspective> newFacets = new TreeSet<>(nextElement.getFacets());
				assert newFacets.size() > 0;
				newFacets.addAll(itemPredicate.getFacets());
				result = MexPerspectives.getMexPerspectives(newFacets, this);
			}
			// System.out.println("SequenceInfo.merge " + this + " " +
			// nextElement + " => " + result);
			return result;
		}

		/**
		 * @return whether updated SequenceInfo is valid.
		 */
		boolean isPluralNnegatedCompatible(final @NonNull ItemPredicate itemPredicate1) {
			assert itemPredicate.fromPerspective().getFacetType() == itemPredicate1.fromPerspective()
					.getFacetType() : itemPredicate + " " + itemPredicate1;
			isPlural = isPlural.intersect(itemPredicate1.isPlural());
			isNegated = isNegated.intersect(itemPredicate1.isNegated());

			// We want to render Places=... -- Pittsburgh and Places=Europe as
			// "Pittsburgh or Europe", so try combining everything with the same
			// facet type (by making parent = facet type).
			// if (parent != itemPredicate.fromPerspective().getParent()) {
			// if (parent != itemPredicate.fromPerspective().getFacetType()) {
			// isPlural = YesNoMaybe.CONFLICT;
			// }

			return !isConflict();
		}

		public @NonNull Perspective parentFacet() {
			final Perspective _parentFacet = itemPredicate.fromPerspective().getParent();
			assert _parentFacet != null;
			return _parentFacet;
		}

		public boolean isConflict() {
			return isPlural == YesNoMaybe.CONFLICT || isNegated == YesNoMaybe.CONFLICT;
		}

		@Override
		public String toString() {
			return UtilString.toString(this,
					"itemPredicate=" + itemPredicate + " isPlural=" + isPlural + " isNegated=" + isNegated);
		}

	}

}
