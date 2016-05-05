package edu.cmu.cs.bungee.populate;

import java.util.regex.Pattern;

/**
 * input: "<term1> -- <term2; <term3> ... "
 *
 * output: { { <term1>, <term2> }, { <term3> }, ... }
 *
 */
public class PreparsedFacetValueParser implements FacetValueParser {

	private static final PreparsedFacetValueParser SELF = new PreparsedFacetValueParser();

	public static PreparsedFacetValueParser getInstance() {
		return SELF;
	}

	private static final Pattern PATTERN = Pattern.compile("\\s*--\\s*");

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler handler) {
		final String[] values = value.split("\\s*;\\s*");
		final String[][] result = new String[values.length][];
		for (int i = 0; i < result.length; i++) {
			result[i] = PATTERN.split(values[i]);
			// result[i] = values[i].split("\\s*--\\s*");
		}
		return result;
	}

}