package edu.cmu.cs.bungee.client.query.markup;

import java.io.Serializable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * {positive pattern, negative pattern} where the patterns are
 * MarkupStringElements. '~'s in a pattern are replaced with a ItemPredicate
 * descriptions.
 */
public class DescriptionPreposition implements Serializable {

	// TODO Remove unused code found by UCDetector
	// public static final @NonNull DescriptionPreposition
	// SPACENOT_DESCRIPTION_PREPOSITION = new DescriptionPreposition(
	// " ; NOT ");

	protected static final long serialVersionUID = 1L;

	private final @Nullable MarkupStringElement positive;
	private final @Nullable MarkupStringElement negative;

	public DescriptionPreposition(final @NonNull String string) {
		final @NonNull String[] strings = UtilString.splitSemicolon(string);
		assert strings.length == 2 : UtilString.valueOfDeep(strings);
		positive = empty2null(strings[0]);
		negative = empty2null(strings[1]);
	}

	private static @Nullable MarkupStringElement empty2null(final String s) {
		return (s.length() == 0) ? null : MarkupStringElement.getElement(s);
	}

	public @Nullable MarkupStringElement getPattern(final boolean polarity) {
		return polarity ? positive : negative;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, positive + "; " + negative);
	}

}
