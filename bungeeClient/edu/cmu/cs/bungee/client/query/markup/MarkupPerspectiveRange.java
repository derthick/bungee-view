package edu.cmu.cs.bungee.client.query.markup;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class MarkupPerspectiveRange extends MexPerspectives {

	private final @NonNull Perspective fromPerspective;
	private final @NonNull Perspective toPerspective;

	// TODO Remove unused code found by UCDetector
	// MarkupPerspectiveRange(final @NonNull PerspectiveMarkupElement _from,
	// final @NonNull PerspectiveMarkupElement _to) {
	// this(_from.perspective, _to.perspective, _from.isPlural,
	// _from.isNegated);
	// assert isPlural == _to.isPlural && isNegated == _to.isNegated;
	// }

	MarkupPerspectiveRange(final @NonNull Perspective _from, final @NonNull Perspective _to, final boolean _isPlural,
			final boolean _isNegated) {
		super(UtilArray.EMPTY_LIST, _isPlural, _isNegated);
		fromPerspective = _from;
		toPerspective = _to;
	}

	@Override
	public @NonNull Collection<Perspective> getFacets() {
		final Perspective parent = fromPerspective.getParent();
		assert parent != null;
		return parent.getChildren(fromPerspective, toPerspective);
	}

	@Override
	public int nFacetsRaw() {
		return toPerspective.getID() - fromPerspective.getID();
	}

	@Override
	public @NonNull Perspective fromPerspective() {
		return fromPerspective;
	}

	@Override
	public @NonNull Perspective toPerspective() {
		return toPerspective;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, fromPerspective + " - " + toPerspective);
	}

	@Override
	public @NonNull String getName() {
		return fromPerspective.getName() + " - " + toPerspective.getName();
	}

	@Override
	public @NonNull String getName(final @Nullable RedrawCallback _redraw) {
		return fromPerspective.getName(_redraw) + " - " + toPerspective.getName(_redraw);
	}

	@Override
	public @NonNull Markup description() {
		Markup markup;
		if (isNegated) {
			markup = new MarkupPerspectiveRange(fromPerspective, toPerspective, isPlural, false).description();
			markup.add(0, DefaultMarkup.FILTER_TYPE_BUT_NOT);
			markup.add(DefaultMarkup.FILTER_TYPE_CLOSE);
		} else {
			markup = DefaultMarkup.emptyMarkup();
			final PerspectiveMarkupElement fromPME = fromPerspective.getMarkupElement(isPlural, isNegated);
			final PerspectiveMarkupElement toPME = toPerspective.getMarkupElement(isPlural, isNegated);
			final boolean isFirstChild = fromPerspective.previous() == null;
			final boolean isLastChild = toPerspective.next() == null;
			if (fromPME == toPME) {
				markup.add(fromPME);
			} else if (isFirstChild && isLastChild) {
				markup.add(DefaultMarkup.CONNECTOR_ANY);
				markup.add(toPME.getParentPME());
			} else if (isFirstChild == isLastChild) {
				markup.add(fromPME);
				markup.add(DefaultMarkup.CONNECTOR_DASH);
				markup.add(toPME);
			} else if (isFirstChild) {
				markup.add(DefaultMarkup.CONNECTOR_LESS_THAN);
				markup.add(toPME);
			} else if (isLastChild) {
				markup.add(DefaultMarkup.CONNECTOR_GREATER_THAN);
				markup.add(fromPME);
			}
		}
		return markup;
	}

}
