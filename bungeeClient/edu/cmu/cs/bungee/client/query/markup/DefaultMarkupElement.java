package edu.cmu.cs.bungee.client.query.markup;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * Very minimal implementation of MarkupElement
 */
public abstract class DefaultMarkupElement implements MarkupElement {

	protected boolean isPlural;

	protected DefaultMarkupElement() {
		this(false);
	}

	protected DefaultMarkupElement(final boolean _isPlural) {
		isPlural = _isPlural;
	}

	@Override
	public boolean isEffectiveChildren() {
		return false;
	}

	@Override
	public @NonNull Markup description() {
		return DefaultMarkup.newMarkup(this);
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		assert !isPlural : this;
		isPlural = true;
		return this;
	}

	@Override
	public @NonNull String getName(@SuppressWarnings("unused") final RedrawCallback _redraw) {
		final String name = getName();
		assert name != null : "If you ignore _redraw, you better find the name the first time.";
		return name;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, getName());
	}

	@Override
	public @Nullable MarkupElement merge(@SuppressWarnings("unused") final MarkupElement nextElement) {
		return null;
	}

	@Override
	public boolean shouldUnderline() {
		return false;
	}

}
