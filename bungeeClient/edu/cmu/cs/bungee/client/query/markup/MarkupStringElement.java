package edu.cmu.cs.bungee.client.query.markup;

import java.util.Hashtable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class MarkupStringElement extends DefaultMarkupElement {

	private static final @NonNull Hashtable<String, MarkupStringElement> CACHE = new Hashtable<>();

	private final @NonNull String markupString;

	public static @NonNull MarkupStringElement getElement(final @NonNull String s) {
		assert UtilString.isNonEmptyString(s);
		MarkupStringElement result = CACHE.get(s);
		if (result == null) {
			result = new MarkupStringElement(s);
			CACHE.put(s, result);
		}
		return result;
	}

	protected MarkupStringElement(final @NonNull String s) {
		super();
		assert UtilString.isNonEmptyString(s);
		markupString = s;
	}

	@Override
	public boolean isEffectiveChildren() {
		assert false : this;
		return false;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "\"" + markupString + "\"");
	}

	@Override
	public @NonNull String getName() {
		assert!markupString.startsWith("Generic Object Label");
		return markupString;
	}

	@Override
	public @NonNull String getName(@SuppressWarnings("unused") final RedrawCallback _redraw) {
		return getName();
	}

	@Override
	public int hashCode() {
		return markupString.hashCode();
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
		final MarkupStringElement other = (MarkupStringElement) obj;
		// if (markupString == null) {
		// if (other.markupString != null) {
		// return false;
		// }
		// } else
		if (!markupString.equals(other.markupString)) {
			return false;
		}
		return true;
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		if (this == DefaultMarkup.GENERIC_OBJECT_LABEL) {
			return null;
		} else {
			return getElement(UtilString.pluralize(markupString));
		}
	}

	@Override
	public @Nullable MarkupElement merge(final @NonNull MarkupElement nextElement) {
		MarkupElement result = null;
		if (nextElement instanceof MarkupStringElement && !(nextElement instanceof MarkupSearchElement)
				&& this != DefaultMarkup.GENERIC_OBJECT_LABEL) {
			result = getElement(getName() + nextElement.getName());
		}
		return result;
	}

	@Override
	public boolean shouldUnderline() {
		return this == DefaultMarkup.GENERIC_OBJECT_LABEL;
	}
}
