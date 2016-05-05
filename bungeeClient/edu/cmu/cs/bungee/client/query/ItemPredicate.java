package edu.cmu.cs.bungee.client.query;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;

public interface ItemPredicate extends MarkupElement, Comparable<ItemPredicate> {
	static final long serialVersionUID = -959016825795947094L;

	/**
	 * @return the facets for which this ItemPredicate holds.
	 *
	 *         Only called by MexPerspectives.merge()
	 */
	public Collection<Perspective> getFacets();

	Perspective compareBy();

	Perspective fromPerspective();

	Perspective toPerspective();

	@NonNull
	edu.cmu.cs.bungee.javaExtensions.YesNoMaybe isPlural();

	@NonNull
	YesNoMaybe isNegated();

	/**
	 * @return render this ItemPredicate as a negative requirement
	 */
	ItemPredicate negate();

	public int getTotalCount();

}
