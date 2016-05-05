package edu.cmu.cs.bungee.client.query.markup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.ItemPredicate;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement.SequenceInfo;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;

/**
 * a subset of a Perspective's children. If the subset is a contiguous range,
 * use MarkupPerspectiveRange.
 */
public class MexPerspectives extends DefaultMarkupElement implements ItemPredicate {

	private final @NonNull SortedSet<Perspective> facets;

	protected boolean isNegated;

	// MexPerspectives(final Collection<PerspectiveMarkupElement> pmes) {
	// super(pmes.iterator().next().isPlural, pmes.iterator().next().isNegated);
	// // System.out.println("mexp "+facets);
	// assert pmes.size() > 1;
	// assert areAllCompatibleSiblings(pmes) : pmes
	// +
	// " do not all have a common parent or have different plurality/negation";
	// for (final PerspectiveMarkupElement pme : pmes) {
	// facets.add(pme.perspective);
	// }
	// }

	protected MexPerspectives(final @NonNull Collection<Perspective> _facets, final boolean _isPlural,
			final boolean _isNegated) {
		super(_isPlural);
		facets = new TreeSet<>(_facets);
		isNegated = _isNegated;
	}

	public int nFacetsRaw() {
		return facets.size();
	}

	@Override
	public @Nullable MarkupElement merge(final @NonNull MarkupElement nextElement) {
		return (nextElement instanceof ItemPredicate
		// && fromPerspective().getParent() == ((ItemPredicate)
		// nextElement).fromPerspective().getParent()
		) ? new SequenceInfo(this).merge((ItemPredicate) nextElement) : null;
	}

	static @NonNull MarkupElement getMexPerspectives(final @NonNull SortedSet<Perspective> newFacets,
			final @NonNull SequenceInfo sequenceInfo) {
		MarkupElement result;
		final boolean pluralBoolean = sequenceInfo.isPlural == YesNoMaybe.YES;
		final boolean negatedBoolean = sequenceInfo.isNegated == YesNoMaybe.YES;
		if (newFacets.size() == sequenceInfo.parentFacet().nChildrenRaw()) {
			result = sequenceInfo.parentFacet().getMarkupElement(pluralBoolean, negatedBoolean);
		} else if (isSequence(newFacets)) {
			final Perspective first = newFacets.first();
			final Perspective last = newFacets.last();
			assert first != null && last != null;
			result = new MarkupPerspectiveRange(first, last, pluralBoolean, negatedBoolean);
		} else {
			result = new MexPerspectives(newFacets, pluralBoolean, negatedBoolean);
		}
		return result;
	}

	private static boolean isSequence(final @NonNull SortedSet<Perspective> newFacets) {
		return newFacets.size() == newFacets.last().whichChildRaw() - newFacets.first().whichChildRaw() + 1;
	}

	@Override
	public @NonNull Collection<Perspective> getFacets() {
		return facets;
	}

	@Override
	public @NonNull String getName() {
		return getName(null);
	}

	@Override
	public @NonNull String getName(final @Nullable RedrawCallback _redraw) {
		return getNameOrPossible(_redraw, false);
	}

	/**
	 * @param ifPossible
	 *            ignore _redraw; skip uncached facets.
	 *
	 *            If false, render as "ID=67" and call _redraw.
	 */
	private @NonNull String getNameOrPossible(final @Nullable RedrawCallback _redraw, final boolean ifPossible) {
		final StringBuilder buf = new StringBuilder();
		final Perspective last = toPerspective();
		for (final Perspective p : facets) {
			if (buf.length() > 0) {
				if (facets.size() > 2 && p == last) {
					buf.append(", or ");
				} else if (facets.size() > 2) {
					buf.append(", ");
				} else if (p == last) {
					buf.append(" or ");
				}
			}
			String name = ifPossible ? p.getNameIfCached() : p.getName(_redraw);
			if (isPlural && name != null) {
				name = UtilString.pluralize(name);
			}
			buf.append(name);
		}
		String name = UtilString.bufToString(buf);
		if (isNegated) {
			name = " (but NOT " + name + ")";
		}
		return name;
	}

	@Override
	public boolean isEffectiveChildren() {
		boolean result = false;
		for (final Perspective p : facets) {
			if (p.isEffectiveChildren()) {
				result = true;
				break;
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, getNameOrPossible(null, true));
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		assert !isPlural;
		isPlural = true;
		return this;
	}

	@Override
	public @NonNull ItemPredicate negate() {
		assert !isNegated;
		isNegated = true;
		return this;
	}

	@Override
	public @NonNull Markup description() {
		final Markup description = newMarkup(facets, isPlural, false).addConnectors(DefaultMarkup.CONNECTOR_OR);
		if (isNegated) {
			description.add(0, DefaultMarkup.FILTER_TYPE_BUT_NOT);
			description.add(DefaultMarkup.FILTER_TYPE_CLOSE);
		}
		return description;
	}

	private static @NonNull Markup newMarkup(final @NonNull SortedSet<Perspective> facets2, final boolean _isPlural,
			final boolean _isNegated) {
		final List<PerspectiveMarkupElement> facetMarkupElements = new ArrayList<>(facets2.size());
		for (final Perspective p : facets2) {
			facetMarkupElements.add(p.getMarkupElement(_isPlural, _isNegated));
		}
		return DefaultMarkup.newMarkup(facetMarkupElements);
	}

	@Override
	public int compareTo(final ItemPredicate o) {
		return compareBy().compareTo(o.compareBy());
	}

	@Override
	public @NonNull Perspective compareBy() {
		return fromPerspective();
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
	public @NonNull Perspective fromPerspective() {
		final Perspective result = facets.first();
		assert result != null : this;
		return result;
	}

	@Override
	public @NonNull Perspective toPerspective() {
		final Perspective result = facets.last();
		assert result != null : this;
		return result;
	}

	@Override
	public int getTotalCount() {
		int result = 0;
		for (final Perspective type : facets) {
			result += type.getTotalCount();
		}
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + facets.hashCode();
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
		final MexPerspectives other = (MexPerspectives) obj;
		if (!facets.equals(other.facets)) {
			return false;
		}
		return true;
	}

}
