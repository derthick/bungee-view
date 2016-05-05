package edu.cmu.cs.bungee.client.query.markup;

import java.io.Serializable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;

public interface MarkupElement extends Serializable {

	static final long serialVersionUID = 1L;

	boolean isEffectiveChildren();

	/**
	 * @return Generally, how this is rendered into a string for display
	 */
	@NonNull
	String getName();

	@NonNull
	String getName(RedrawCallback _redraw);

	/**
	 * @return a closer-to-final representation of this element. e.g.
	 *         MarkupPerspectiveRange ⇒ {fromPerspective, "-", toPerspective}
	 *         Should compile enclosing Markup first.
	 */
	Markup description();

	/**
	 * @return render (or replace) this element as a MarkupElement with a plural
	 *         suffix. If pluralizing makes no sense, return the non-pluralized
	 *         element. Returning null will prepend DefaultMarkup.PLURAL_TAG.
	 */
	@Nullable
	MarkupElement pluralize();

	/**
	 * @return A single MarkupElement representing both this and nextElement, or
	 *         null if that is not possible.
	 */
	@Nullable
	MarkupElement merge(@NonNull MarkupElement nextElement);

	/**
	 * @return whether element is a Perspective or GENERIC_OBJECT_LABEL (i.e.
	 *         Item).
	 */
	boolean shouldUnderline();
}
