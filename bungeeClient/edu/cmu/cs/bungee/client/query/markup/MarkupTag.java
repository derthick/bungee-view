package edu.cmu.cs.bungee.client.query.markup;

import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class MarkupTag extends DefaultMarkupElement {

	private final Character character;

	MarkupTag(final char c) {
		super();
		character = Character.valueOf(c);
	}

	@Override
	public String getName() {
		return ""; // toString();
	}

	@Override
	public String getName(@SuppressWarnings("unused") final RedrawCallback _redraw) {
		return getName();
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		return this;
	}

	@Override
	public MarkupElement merge(final MarkupElement nextElement) {
		MarkupElement result = null;
		if (this == DefaultMarkup.PLURAL_TAG) {
			result = nextElement.pluralize();
		}
		return result;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, character.charValue() == '\n' ? "\\n" : character.toString());
	}

	@Override
	public int hashCode() {
		return character.hashCode();
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
		final MarkupTag other = (MarkupTag) obj;
		if (character == null) {
			if (other.character != null) {
				return false;
			}
		} else if (!character.equals(other.character)) {
			return false;
		}
		return true;
	}

}