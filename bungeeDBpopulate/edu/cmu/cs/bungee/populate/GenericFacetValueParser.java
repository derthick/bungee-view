package edu.cmu.cs.bungee.populate;

import org.eclipse.jdt.annotation.NonNull;

/**
 * return value unchanged: [[<value>]]
 */
public class GenericFacetValueParser implements FacetValueParser {

	private static final GenericFacetValueParser SELF = new GenericFacetValueParser();

	static GenericFacetValueParser getInstance() {
		return SELF;
	}

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler handler) {
		if (value == null) {
			return new String[0][];
		}
		final String[][] result = { { value.replace('\n', ' ') } };
		return result;
	}

	public static @NonNull String trim(final String value) {
		// Get rid of any characters before the first word
		String result = value.replaceAll("\\A.*?\\b", "");

		// Get rid of any characters after the last word (but retain ')')
		result = result.replaceAll("\\b(?:[^\\)]\\B)*\\z", "");

		// if (!result.equals(value))
		// System.out.println(value + " => " + result);

		assert result != null;
		return result;
	}
}