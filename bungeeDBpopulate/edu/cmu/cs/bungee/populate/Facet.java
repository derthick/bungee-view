package edu.cmu.cs.bungee.populate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.compile.Database;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class Facet extends Field {

	private final FacetValueParser parser;

	/**
	 * cleanUp will set raw_facet.sort to the concatenation of all groups
	 * matched by this pattern.
	 */
	public String sortPattern = null;

	// private static Hashtable<String, Facet> fieldCache = new
	// Hashtable<String, Facet>();

	Facet(final @NonNull String name1, final FacetValueParser _parser) {
		super(name1);
		parser = _parser;
	}

	public static @NonNull Facet getParsingFacet(final @NonNull String name, final FacetValueParser parser) {
		// Facet result = fieldCache.get(name);
		// if (result == null) {
		// result = new Facet(name, parser);
		// } else {
		// assert result.parser == parser;
		// }
		return new Facet(name, parser);
	}

	public static Facet getTimeFacet(final @NonNull String name) { // NO_UCD
																	// (unused
		// code)
		final Facet facet = getParsingFacet(name, TimeFacetValueParser.getInstance());
		// facet.sortPattern =
		// "(?:\\d+:\\d{2}:(\\d{2}))|(?:\\d+:(\\d{2}):00-\\d+:\\d{2}:59)|(?:(\\d+):00-\\d+:59)"
		// ;
		facet.sortPattern = "(?:\\d+:\\d{2}:(\\d{2}))|(?:\\d+:(\\d{2}):ss)|(?:(\\d+):mm:ss)";
		return facet;
	}

	private static final String DATE_SORT_PATTERN = "(?:\\d{1,2}/(\\d{1,2})/\\d{4})|(?:(\\d{1,2})/\\d{4})|(?:(\\d+)(?:s|(?:.*century))?)";

	public static @NonNull Facet getDateFacet(final @NonNull String name) {
		final Facet facet = getParsingFacet(name, DateFacetValueParser.getInstance());
		facet.sortPattern = DATE_SORT_PATTERN;
		return facet;
	}

	public static @NonNull Facet getGenericFacet(final @NonNull String name) {
		return getParsingFacet(name, GenericFacetValueParser.getInstance());
	}

	public static Facet getPreparsedFacet(final @NonNull String name) {
		return getParsingFacet(name, PreparsedFacetValueParser.getInstance());
	}

	static Facet getDate045Facet(final @NonNull String name) {
		final Facet facet = getParsingFacet(name, Date045FacetValueParser.getInstance());
		facet.sortPattern = DATE_SORT_PATTERN;
		return facet;
	}

	public static Facet getNumericFacet(final @NonNull String name) { // NO_UCD
																		// (unused
		// code)
		final Facet facet = getParsingFacet(name, GenericFacetValueParser.getInstance());
		// with this pattern, will only sort on [unsigned] integer part
		facet.sortPattern = "(\\d+)";
		return facet;
	}

	public static Facet getRoundToCommonValuesFacet(final @NonNull String name, final int[] commonValues,
			final boolean isGeometric) { // NO_UCD
		// (unused
		// code)
		final Facet facet = getParsingFacet(name, new RoundToCommonIntegerValuesParser(commonValues, isGeometric));
		facet.sortPattern = "(\\d+)";
		return facet;
	}

	public static Facet getRoundToSigFigsFacet(final @NonNull String name) { // NO_UCD
		// (unused code)
		final Facet facet = new RoundToSigFigsFacet(name);
		return facet;
	}

	static class RoundToSigFigsFacet extends Facet {

		RoundToSigFigsFacet(final @NonNull String _name) {
			super(_name, new RoundToSigFigsParser());
		}

		@Override
		void cleanUp(final Database db) throws SQLException {
			@SuppressWarnings("resource")
			final PreparedStatement ps = db.lookupPS("UPDATE raw_facet SET sort = ? WHERE name = ?");
			final int[] grandChildren = db.getChildIDs(db.getFacetType(name, false));
			for (final int childID : grandChildren) {
				final String childName = db.getRawFacetName(childID);
				final String sort = childName.replace(",", "").replace('x', '0');
				ps.setString(1, sort);
				ps.setString(2, childName);
				db.updateOrPrint(ps, "Set sort");
			}
		}

	}

	public static Facet getRoundToCommon2DValuesFacet(final @NonNull String name, final int[][] commonValues,
			final boolean isGeometric) { // NO_UCD
		// (unused
		// code)
		final Facet facet = getParsingFacet(name, new RoundToCommon2DIntegerValuesParser(commonValues, isGeometric));
		facet.sortPattern = "(\\d+)x(\\d+)";
		return facet;
	}

	public static Facet getPlaceFacet(final @NonNull String name) {
		return getParsingFacet(name, PlaceFacetValueParser.getInstance());
	}

	static Facet getPlace043Facet(final @NonNull String name) {
		return getParsingFacet(name, Place043FacetValueParser.getInstance());
	}

	public static Facet getSubjectFacet(final @NonNull String name) {
		return getParsingFacet(name, SubjectFacetValueParser.getInstance());
	}

	public static Facet getANDedValuesFacet(final @NonNull String name) {
		return getParsingFacet(name, ANDedValuesParser.getInstance());
	}

	@SuppressWarnings("null")
	@Override
	public boolean insert(final String value, final PopulateHandler handler) throws SQLException {
		final String[][] facets = parse(value, handler);

		// if (facets.length > 0 && facets[0].length > 0
		// && facets[0][0].contains("--")) {
		// System.out.println(parser + "\n" + UtilString.getStackTrace());
		// }
		// System.out.println("Field.insert " + this + " value=" + value
		// + " facets=" + UtilString.valueOfDeep(facets));

		for (final @NonNull String[] pathFacets : facets) {
			final List<String> path = path(value, pathFacets);
			handler.insertFacet(this, path);
			// System.out.println("... " + path);
		}
		return facets.length > 0;
	}

	/**
	 * Validate pathFacets and
	 *
	 * @param pathFacets
	 *            [<facetName> ...]
	 * @return [name, pathFacets[0], pathFacets[1], ...]
	 */
	public List<String> path(final String rawValue, final @NonNull String[] pathFacets) {
		assert pathFacets != null : "Null component in " + this + ": " + rawValue;
		final List<String> path = new ArrayList<>(pathFacets.length + 1);
		path.add(name);
		for (final String parsedVal : pathFacets) {
			assert parsedVal.length() > 0 : "Zero-length component in " + this + ": rawValue='" + rawValue
					+ "' pathFacets='" + UtilString.valueOfDeep(pathFacets) + "'";
			if (parsedVal.startsWith(" ")) {
				System.err.println(
						"Component starts with space in " + this + ": '" + UtilString.join(pathFacets, "', '") + "'");
			}
			path.add(parsedVal);
		}
		// System.out.println("Facet.path rawValue='" + rawValue +
		// "' pathFacets="
		// + UtilString.valueOfDeep(pathFacets) + " return " + path);
		return path;
	}

	public @NonNull String[][] parse(final String value, final PopulateHandler handler) {
		final String[][] facets = parser.parse(value, handler);
		return facets;
	}

	@Override
	void cleanUp(final Database db) throws SQLException {
		// System.out.println("Facet.cleanUp " + name + " sortPattern='"
		// + sortPattern + "'");
		if (sortPattern != null) {
			setSort(db.getFacetType(name, false), Pattern.compile(sortPattern), db);
		}
	}

	@SuppressWarnings("resource")
	private void setSort(final int facet, final Pattern pattern, final Database db) throws SQLException {
		assert facet > 0;
		if (facet > db.getMaxRawFacetTypeID()) {
			final String facetName = db.getRawFacetName(facet);
			final Matcher matcher = pattern.matcher(facetName);
			if (matcher.matches()) {
				final StringBuilder sort = new StringBuilder();
				for (int i = 0; i < matcher.groupCount(); i++) {
					final String group = matcher.group(i + 1);
					// System.out.println(i + " " + group);
					if (group != null) {
						sort.append(group);
					}
				}
				// System.out.println(facetName + " " + sort);
				final PreparedStatement ps = db.lookupPS("UPDATE raw_facet SET sort = ? WHERE name = ?");
				ps.setString(1, sort.toString());
				ps.setString(2, facetName);
				db.updateOrPrint(ps, "Set sort");
			}
		}
		final int[] grandChildren = db.getChildIDs(facet);
		for (final int element : grandChildren) {
			setSort(element, pattern, db);
		}
	}

	@Override
	public String toString() {
		return UtilString.toString(this, name);
	}
}