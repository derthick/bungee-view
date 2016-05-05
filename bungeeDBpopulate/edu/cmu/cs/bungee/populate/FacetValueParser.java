package edu.cmu.cs.bungee.populate;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * Maps source string into an array of of tag hierarchies to add to the current
 * item. Each hierarchy is an array of tag names, ordered from the facet just
 * below the facet type on down:
 *
 * [ [<child of facet type>, <grandcild>, ...], ... ]
 *
 * A ParseOAIHandler is passed in so that the parser can get at resources like
 * lists of named entities.
 *
 */
interface FacetValueParser {

	/**
	 * The ancestor facets must not include the facet type; this is encoded in
	 * the field.
	 *
	 * @param value
	 *            raw string from XML file
	 * @return [<ancestor facets alternative 1>, ...]
	 */
	@NonNull
	String[][] parse(String value, PopulateHandler handler);
}

/**
 * parse '5:51:52' and return [ ["5:mm:ss", "5:51:ss", "5:51:52"], ... ]; or
 * '123' (as seconds) and return [ ["0:mm:ss", "0:02:ss", "0:02:03"], ... ].
 */
class TimeFacetValueParser implements FacetValueParser {

	private static final TimeFacetValueParser SELF = new TimeFacetValueParser();

	static TimeFacetValueParser getInstance() {
		return SELF;
	}

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler _handler) {
		String[][] result = { { value } };

		// This is the format we generate
		Pattern p = Pattern.compile("(\\d+):((?:mm)|(?:\\d{2})):((?:ss)|(?:\\d{2}))");
		Matcher m = p.matcher(value);
		if (m.find()) {
			final String hour = m.group(1);
			final String minute = m.group(2);
			final String second = m.group(3);
			final String[][] x = {
					{ hour + ":mm:ss", hour + ":" + minute + ":ss", hour + ":" + minute + ":" + second } };
			result = x;
		}

		// Integer interpreted as seconds
		p = Pattern.compile("\\d+");
		m = p.matcher(value);
		if (m.matches()) {
			int second = Integer.parseInt(value);
			final int hour = second / 60 / 60;
			second -= hour * 60 * 60;
			final int minute = second / 60;
			second -= minute * 60;
			// String hourName = String.valueOf(hour);
			String minuteName = String.valueOf(minute);
			if (minuteName.length() == 1) {
				minuteName = "0" + minuteName;
			}
			String secondName = String.valueOf(second);
			if (secondName.length() == 1) {
				secondName = "0" + secondName;
			}
			final String[][] x = {
					{ hour + ":mm:ss", hour + ":" + minuteName + ":ss", hour + ":" + minuteName + ":" + secondName } };
			result = x;
		}
		return result;
	}
}

/**
 * parse an arbitrary string representing a date and return, e.g., [[
 * "20th century", "1990s", "1996", "12/1996", "12/31/1996"]]
 */
class Date045FacetValueParser implements FacetValueParser {

	private static final Date045FacetValueParser SELF = new Date045FacetValueParser();

	static Date045FacetValueParser getInstance() {
		return SELF;
	}

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler handler) {
		// System.out.println("045 code = '" + value + "'");
		String newValue = null;
		if (value.length() == 4) {
			// see http://www.oclc.org/bibformats/en/0xx/045.shtm
			String lowRange = code045(value.substring(0, 2));
			final String hiRange = code045(value.substring(2, 4));
			if (lowRange != null && hiRange != null) {
				if (!lowRange.equals(hiRange)) {
					lowRange = lowRange.split("-")[0] + "-" + hiRange.split("-")[1];
				}
				newValue = lowRange;
			}
		} else {
			// see
			// http://www.loc.gov/marc/bibliographic/ecbdnumb.html#mrcb045
			final Pattern p = Pattern.compile("\\b([cd])(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?\\z");
			final Matcher m = p.matcher(value);
			if (m.find()) {
				final String era = m.group(1);
				final String year = m.group(2);
				final String month = m.group(3);
				final String day = m.group(4);
				final String parsedValue = (day != null) ? month + "/" + day + "/" + year
						: (month != null) ? month + "/" + year : year;
				if (era.charAt(0) == 'd') {
					newValue = parsedValue;
				} else if (era.charAt(0) == 'c') {
					newValue = parsedValue + " B.C.";
				}
			}
		}
		String[] result0;
		if (newValue != null) {
			result0 = DateFacetValueParser.hierDateValues(newValue, false);
		} else {
			System.err.println("Bad 045 Date Code " + value);
			result0 = new String[0];
		}
		final String[][] result = { result0 };
		return result;
	}

	private static String code045(final String code) {
		String result = null;
		final int code1 = code.charAt(0) - 'a';
		final int code2 = code.charAt(1) - '0';
		if (code.length() == 2 && code1 >= 0 && code1 <= 25 && (code.charAt(1) == '-' || (code2 >= 0 && code2 <= 9))) {
			if (code1 == 0) {
				assert code2 == 0;
				result = "before 2999 B.C.";
			} else if (// code1 > 0 &&
			code1 < 4) {
				final int digit1 = 2 - (code1 - 1);
				final int digit2 = 9 - code2;
				final int end = 100 * (digit1 * 10 + digit2);
				final int start = end + 99;
				result = start + "-" + end;
			} else if (code.charAt(1) == '-') {
				final int digit1 = code1 - 4;
				final int start = digit1 * 100;
				final int end = start + 99;
				result = start + "-" + end;
			} else {
				final int digit1 = code1 - 4;
				final int digit2 = code2;
				final int start = 10 * (digit1 * 10 + digit2);
				final int end = start + 9;
				result = start + "-" + end;
			}
		}
		// System.out.println("code 045 " + code + " => " + result);
		return result;
	}

}

/**
 * parse an arbitrary string representing a place and return, e.g., [[
 * "North America", "United States", "Utah"]]
 */
class PlaceFacetValueParser implements FacetValueParser {

	private static final PlaceFacetValueParser SELF = new PlaceFacetValueParser();

	private static final String[] ABBREVS = new String[] { "Alabama;al:ala", "Alaska;ak", "American Samoa;as:a.s",
			"Arizona;az:ariz", "Arkansas;ar:ark", "British Columbia;bc:b.c", "California;ca:cal:calif",
			"Colorado;co:col:colo", "Connecticut;ct:conn", "Delaware;de:del", "Federated States Of Micronesia;fm",
			"Florida;fl:fla", "Georgia;ga", "Guam;gu", "Hawaii;hi", "Idaho;id", "Illinois;il:ill", "Indiana;in:ind",
			"Iowa;ia", "Kansas;ks:kan", "Kentucky;ky", "Louisiana;la", "Maine;me", "Marshall Islands;mh", "Maryland;md",
			"Massachusetts;ma:mass", "Michigan;mi:mich", "Minnesota;mn:minn", "Mississippi;ms:miss", "Missouri;mo",
			"Montana;mt:mont", "Nebraska;ne:neb", "Nevada;nv:nev", "New Hampshire;nh", "New Jersey;nj:n.j",
			"New Mexico;nm:n.m", "New York;ny:n.y", "North Carolina;nc:n.c", "North Dakota;nd:n.d",
			"Northern Mariana Islands;mp", "Nova Scotia;ns:n.s", "Ohio;oh", "Oklahoma;ok:okla", "Ontario;ont",
			"Oregon;or:ore", "Palau;pw", "Pennsylvania;pa:penn", "Puerto Rico;pr:p.r", "Rhode Island;ri:r.i",
			"South Carolina;sc:s.c", "South Dakota;sd:s.d", "Tennessee;tn:tenn", "Texas;tx:tex",
			// "United States;us",
			"United States;usa", "Utah;ut", "Vermont;vt", "Virgin Islands;vi:v.i", "Virginia;va", "Washington;wa:wash",
			"Washington DC;dc:d.c:washington, d.c", "West Virginia;wv:w va:wva:w.v", "Wisconsin;wi:wis:wisc",
			"Wyoming;wy:wyo" };

	/**
	 * These are used for place fields only
	 */
	private static final Hashtable<String, String> PLACE_ABBREVS = createPlaceAbbrevs();

	private static Hashtable<String, String> createPlaceAbbrevs() {
		final Hashtable<String, String> _placeAbbrevs = new Hashtable<>();

		for (final String abbrev : ABBREVS) {
			final String[] x = abbrev.split(";");
			_placeAbbrevs.put(x[0].toLowerCase(), x[0]);
			final String[] abbrs = x[1].split(":");
			for (final String abbr : abbrs) {
				_placeAbbrevs.put(abbr.toLowerCase(), x[0]);
			}
		}
		return _placeAbbrevs;
	}

	static Hashtable<String, String> getPlaceAbbrevs() {
		return PLACE_ABBREVS;
	}

	static PlaceFacetValueParser getInstance() {
		return SELF;
	}

	@Override
	public String[][] parse(final String value, final PopulateHandler _handler) {
		// handler = _handler;
		String[][] result = trimPlaces(value, _handler);
		if (result.length == 0) {
			System.err.println("FacetValueParser.parse value=" + value + " result=" + UtilString.valueOfDeep(result));
		}
		if (result[0].length == 1) {
			result = hierPlaces(result[0][0], _handler);
		}
		return result;
	}

	/**
	 * This just looks up atomic locations, not 'Ogden, UT'
	 *
	 * @param value
	 * @return e.g. [["North America", "United States", "Utah"]]
	 */
	private static @NonNull String[][] hierPlaces(final String value, final PopulateHandler handler) {
		String[][] result = { { value } };
		if (handler.moves != null) {
			final String ancestorss = handler.moves.get("places -- " + value.toLowerCase());
			// System.out.println("FacetValueParser.hierPlaces " + value + " " +
			// ancestorss);
			if (ancestorss != null) {
				final String[] ancestors = UtilString.splitSemicolon(ancestorss);
				result = new String[ancestors.length][];
				for (int i = 0; i < ancestors.length; i++) {
					@SuppressWarnings("null")
					final String[] ancestorFacets = Compile.ancestors(ancestors[i]);
					assert ancestorFacets[0].equalsIgnoreCase("Places") : "'" + value + "' " + ancestorss;
					result[i] = UtilArray.subArray(ancestorFacets, 1);
				}
			}
		}
		// System.out.println("FacetValueParser.hierPlaces " + value + " => " +
		// UtilString.valueOfDeep(result));
		return result;
	}

	/**
	 * @param value
	 *            e.g. "downtown (ogden, utah)" or "ogden (utah)" or "ogden,
	 *            utah" 'Victoria Falls (Zambia and Zimbabwe)' 'Keystone, W. Va'
	 * @return [["North America", "United States", "Utah", "Ogden", "downtown"]]
	 *         [[Africa, Zambia, Victoria Falls ], [Africa, Zimbabwe, Victoria
	 *         Falls ]] [[North America, United States, West Virginia,
	 *         Keystone]]
	 */
	private static @NonNull String[][] trimPlaces(final String value, final PopulateHandler _handler) {
		final boolean debug = false; // && value.matches(".*, Italy");
		String[][] result = { { value } };
		// group(1) is everything up to the first comma or open parenthesis
		// group(2) is everything between the first comma or paren and the next
		// close parenthesis, colon, or the end of the string
		final Pattern p = Pattern.compile("\\b([^(]+?)(?:,|\\()\\s*(.*?)\\s*(\\z|:|\\))");
		final Matcher m = p.matcher(value);
		if (debug) {
			System.out.println("trimPlaces " + value);
		}
		if (m.find()) {
			final String narrow = m.group(1); // e.g. "downtown"
			final String broadAbbrev = m.group(2); // e.g. "ogden"
			if (debug) {
				System.out.println(" narrow/broad '" + narrow + "' '" + broadAbbrev + "'");
			}
			if (broadAbbrev != null) {
				final String[] parents = broadAbbrev.replaceAll("\\.", "").split(" [aA][nN][dD] ");
				final int nParents = parents.length;
				if (nParents > 1) {
					final String[][] x = new String[nParents][];
					x[0] = result[0];

					// [[<value>], null, null, ...]
					result = x;
				}
				for (int parentIndex = 0; parentIndex < nParents; parentIndex++) {
					final String[] trimPlace = trimPlace(_handler, debug, broadAbbrev, narrow, parents[parentIndex]);
					if (trimPlace != null) {
						result[parentIndex] = trimPlace;
					}
				}
				result = UtilArray.delete(result, null);
			}
		}
		assert result != null;
		return result;
	}

	public static String[] trimPlace(final PopulateHandler _handler, final boolean debug, final String broadAbbrev,
			final String narrow, final String parent) {
		// e.g. ["Ogden", "UT"] or ["UT"]
		final String[] broads = parent.split(",");

		// e.g. "ut"
		final String lastBroad = broads[broads.length - 1].trim().toLowerCase();

		// e.g. ["North America", "United States", "Utah"]
		String[] broad = lookupLocation(lastBroad, _handler);

		if (debug) {
			System.out.println("lookupLocation('" + lastBroad + "') => " + UtilString.valueOfDeep(broad));
		}
		if (broad == null && !lastBroad.equalsIgnoreCase(broadAbbrev)) {
			broad = lookupLocation(broadAbbrev, _handler);
			if (debug) {
				System.out.println("lookupLocation('" + broadAbbrev + "') => " + UtilString.valueOfDeep(broad));
			}
		}
		// If broad is still null, it might be "Russia
		// (Federation)", which isn't a place so we should ignore it
		if (broad != null) {
			// result[parentIndex] = broad;
			for (int i = broads.length - 2; i >= 0; i--) {
				// use loop in case broads is ["Ogden", "UT", "US"]
				broad = ArrayUtils.add(broad, broads[i].trim());
			}

			// e.g. ["Location", "North America", "United States", "Utah",
			// "Ogden", "downtown"]
			broad = ArrayUtils.add(broad, narrow);
		}
		return broad;
	}

	/**
	 * This just looks up atomic locations, not 'Ogden, UT'
	 *
	 * @param possibleLocation
	 * @return e.g. ["North America", "United States", "Utah"]
	 */
	private static String[] lookupLocation(String possibleLocation, final PopulateHandler _handler) {
		String[] result = null;
		final String unabbreviated = getPlaceAbbrevs().get(possibleLocation.toLowerCase());
		if (unabbreviated != null) {
			possibleLocation = unabbreviated;
			result = new String[1];
			result[0] = unabbreviated;
		}
		// if (result == null) {
		final String[][] hierPlaces = hierPlaces(possibleLocation, _handler);
		if (hierPlaces.length == 1 && hierPlaces[0].length > 1) {
			result = hierPlaces[0];
		} else if (hierPlaces.length > 1) {
			System.err.println("Warning: " + possibleLocation + " is ambiguous: " + UtilString.valueOfDeep(hierPlaces));
		}
		// }
		return result;
	}

	// int[] getFacets(Field field, String value, int parent) throws
	// SQLException {
	// int[] result = null;
	// int facetType = field.getFacetType();
	// // System.out.println("getFacets " + field + " " + value + " " + parent +
	// " " +
	// // facetType);
	// if (parent < 0) {
	// result = lookupFacets(value, facetType);
	// parent = facetType;
	// } else {
	// int theResult = lookupFacet(value, parent);
	// // if (theResult < 0) {
	// // theResult = lookupFacet(value, facetType);
	// // if (theResult > 0)
	// // setParent(theResult, parent);
	// // }
	// if (theResult > 0)
	// result = UtilArray.push(result, theResult);
	// }
	// if (result == null) {
	// // if (parent != facetType) {
	// // if (UtilString.join(foo, " -- ").indexOf("Pearce, James
	// // Alfred") >= 0)
	// // System.out.println("getFacets adding " + value + " " + parent + "["
	// // + facetType + "] " + UtilString.join(foo, " -- "));
	// int theResult = newFacet(value, parent);
	// result = UtilArray.push(result, theResult);
	// // }
	// // else
	// // System.err.println("getFacets not adding " + value + " " + parent +
	// "["
	// // + facetType + "] " + UtilString.join(foo, " -- "));
	// }
	// // if (result != null)
	// // System.out.println("..getFacets(" + value + ") => "
	// // + getFacetName(result[0]));
	// // else
	// // System.out.println("..getFacets(" + value + ") => null");
	// return result;
	// }

}

/**
 * parses a facet name and a key (either "z" for Places or "corp" for Specific
 * Organizations)
 */
class SubPlaceFacetValueParser extends PlaceFacetValueParser {
	@SuppressWarnings("static-method")
	String getKey() {
		assert false;
		return null;
	}

	@Override
	public String[][] parse(final String value, final PopulateHandler handler) {
		/**
		 * Holds successive subfield values for concatenating hierarchical
		 * lists. E.g. in
		 *
		 * <marc:subfield code="z">Virgina</marc:subfield>
		 *
		 * <marc:subfield code="z">Richmond</marc:subfield>
		 */
		return super.parse(getSubFacets(value, getKey(), handler), handler);
	}

	/**
	 * @param value
	 *            facet name
	 * @param key
	 *            either "z" for Places or "corp" for Specific Organizations
	 * @return ancestor facets
	 */
	private @SuppressWarnings("unchecked") static String getSubFacets(String value, final String key,
			final PopulateHandler handler) {
		final Map<String, String[]> subFacetTable = (Hashtable<String, String[]>) handler.parserState;
		final String[] prev = subFacetTable.get(key);
		if (prev == null || !prev[prev.length - 1].equals(value)) {
			final String[] updated = ArrayUtils.add(prev, value);
			subFacetTable.put(key, updated);
		}
		if (prev != null) {
			value += ", " + UtilString.join(prev);
		}
		return value;
	}
}

/**
 * parses a facet name with key = "corp" for Specific Organizations.
 */
class NamedEntitySubFacetValueParser extends SubPlaceFacetValueParser {

	private static final NamedEntitySubFacetValueParser SELF = new NamedEntitySubFacetValueParser();

	static NamedEntitySubFacetValueParser getInstance() {
		return SELF;
	}

	@Override
	String getKey() {
		return "corp";
	}
}

/**
 * parse value using GAC
 */
class Place043FacetValueParser implements FacetValueParser {

	private static final Place043FacetValueParser SELF = new Place043FacetValueParser();

	static Place043FacetValueParser getInstance() {
		return SELF;
	}

	@Override
	public String[][] parse(final String value, final PopulateHandler handler) {
		final String[][] result = { handler.lookupGAC(GenericFacetValueParser.trim(value)) };
		return result;
	}
}

class RoundToCommonIntegerValuesParser implements FacetValueParser {

	private final String[] commonValueStrings;
	private final int[] commonValueBreakpoints;
	private final int nCommonValueBreakpoints;

	RoundToCommonIntegerValuesParser(final int[] _commonValues, final boolean isGeometric) {
		final int nCommonValues = _commonValues.length;
		nCommonValueBreakpoints = nCommonValues - 1;
		commonValueStrings = new String[nCommonValues];
		final int[] commonValues = new int[nCommonValues];
		for (int i = 0; i < nCommonValues; i++) {
			final int common = _commonValues[i];
			commonValueStrings[i] = Integer.toString(common);
			commonValues[i] = common;
		}
		Arrays.sort(commonValues);
		commonValueBreakpoints = new int[nCommonValueBreakpoints];
		for (int i = 0; i < commonValueBreakpoints.length; i++) {
			if (isGeometric) {
				commonValueBreakpoints[i] = (int) Math
						.exp((Math.log(commonValues[i]) + Math.log(commonValues[i + 1])) / 2.0);
			} else {
				commonValueBreakpoints[i] = UtilMath.safeIntAverage(commonValues[i], commonValues[i + 1]);
			}
		}
		// System.out.println("RoundToCommonIntegerValuesParser "
		// + UtilString.valueOfDeep(_commonValues) + " "
		// + UtilString.valueOfDeep(commonValueBreakpoints));
	}

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler handler) {
		final int commonValueIndex = commonValueIndex(value);
		final String[][] result = { { commonValueStrings[commonValueIndex] } };
		// System.out.println("RoundToCommonIntegerValuesParser.parse " + value
		// + " => " + UtilString.valueOfDeep(result));
		return result;
	}

	private int commonValueIndex(final String value) {
		final int iValue = (int) Double.parseDouble(GenericFacetValueParser.trim(value));
		int commonValueIndex = nCommonValueBreakpoints;
		for (int i = 0; i < nCommonValueBreakpoints; i++) {
			if (iValue <= commonValueBreakpoints[i]) {
				commonValueIndex = i;
				break;
			}
		}
		return commonValueIndex;
	}

}

class RoundToSigFigsParser implements FacetValueParser {

	RoundToSigFigsParser() {
	}

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler handler) {
		final String commaedValue = UtilString.addCommas(Integer.parseInt(value));
		final String finalValue = commaedValue.substring(0, 1) + commaedValue.substring(1).replaceAll("\\d", "x");
		final String[][] result = { { finalValue } };
		// System.out.println("RoundToSigFigsParser.parse " + value
		// + " => " + UtilString.valueOfDeep(result));
		return result;
	}

}

class RoundToCommon2DIntegerValuesParser implements FacetValueParser {

	private final String[] commonValueStrings;
	private final double[][] commonValues;
	private final boolean isGeometric;

	RoundToCommon2DIntegerValuesParser(final int[][] _commonValues, final boolean _isGeometric) {
		isGeometric = _isGeometric;
		commonValueStrings = new String[_commonValues.length];
		commonValues = new double[_commonValues.length][2];
		for (int i = 0; i < _commonValues.length; i++) {
			commonValueStrings[i] = _commonValues[i][0] + "x" + _commonValues[i][1];
			for (int j = 0; j < 2; j++) {
				final int common = _commonValues[i][j];
				if (isGeometric) {
					commonValues[i][j] = Math.log(common);
				} else {
					commonValues[i][j] = common;
				}
			}
		}
		// System.out.println("RoundToCommon2DIntegerValuesParser "
		// + UtilString.valueOfDeep(_commonValues) + " "
		// + UtilString.valueOfDeep(commonValues));
	}

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler handler) {
		final String[] values = value.split("x");
		final double[] dValues = { Double.parseDouble(GenericFacetValueParser.trim(values[0])),
				Double.parseDouble(GenericFacetValueParser.trim(values[1])) };
		if (isGeometric) {
			for (int i = 0; i < 2; i++) {
				dValues[i] = Math.log(dValues[i]);
			}
		}
		double bestScore = Double.MAX_VALUE;
		int commonValueIndex = -1;
		for (int i = 0; i < commonValues.length; i++) {
			final double[] commonValue = commonValues[i];
			double score = 0.0;
			for (int j = 0; j < 2; j++) {
				score += penalty(dValues[j], commonValue[j]);
			}
			if (score < bestScore) {
				bestScore = score;
				commonValueIndex = i;
			}
		}
		final String[][] result = { { commonValueStrings[commonValueIndex] } };
		// System.out.println("RoundToCommon2DIntegerValuesParser.parse " +
		// value
		// + " => " + UtilString.valueOfDeep(result));
		return result;
	}

	private static double penalty(final double value, final double commonValue) {
		return Math.abs(value - commonValue);
	}

}

class SubjectFacetValueParser implements FacetValueParser {

	private static final SubjectFacetValueParser SELF = new SubjectFacetValueParser();

	static SubjectFacetValueParser getInstance() {
		return SELF;
	}

	@Override
	public String[][] parse(final String value, final PopulateHandler handler) {
		final String[] subjects = value.split("\\.?; ?");
		String[][] result = new String[subjects.length][];
		for (int i = 0; i < result.length; i++) {
			if (i < subjects.length) {
				result[i] = subjects[i].split("-{2,}");
			}
			for (int j = 0; j < result[i].length; j++) {
				result[i][j] = result[i][j].trim();
				String s = result[i][j];
				s = handler.rename(s);
				if (s.length() == 0) {
					System.out.println("Ignoring zero length facet name in '" + result[i][j] + "' in '"
							+ UtilString.join(result[i], edu.cmu.cs.bungee.compile.Compile.FACET_HIERARCHY_SEPARATOR)
							+ "' in '" + value + "'");
					result[i] = null;
					break;
				} else if (s.charAt(0) == '-' || (s.charAt(s.length() - 1) == '-' && result[i].length > j + 1)
						|| s.indexOf("-pitts") >= 0 || s.indexOf("-penns") >= 0
						|| (s.indexOf("enns") != s.indexOf("ennsylvania"))
						|| (s.indexOf("itts") != s.indexOf("ittsburgh"))) {
					System.out.println("Warning ignoring suspicious facet name '" + s + "' in '" + result[i][j]
							+ "' in '" + UtilString.join(result[i], Compile.FACET_HIERARCHY_SEPARATOR) + "' in '"
							+ value + "'");
					result[i] = null;
					break;
				}
				final int k = ArrayUtils.indexOf(result[i], s, j + 1);
				if (k > 0) {
					if (s.equalsIgnoreCase("Pennsylvania") && result[i][k - 1].equalsIgnoreCase("Pittsburgh")) {
						result[i] = ArrayUtils.remove(result[i], k);
					} else {
						System.out.println("Warning repeated facet name '" + result[i][j] + "' in '"
								+ UtilString.join(result[i], Compile.FACET_HIERARCHY_SEPARATOR) + "' in '" + value
								+ "'");
						result[i] = ArrayUtils.subarray(result[i], 0, j + 1);
						System.out.println("...treating as: [" + UtilString.join(result[i], ",") + "] ");
					}
				}
				if (!result[i][0].equals("Places")
						&& PlaceFacetValueParser.getPlaceAbbrevs().get(s.toLowerCase()) != null) {
					final String[] place = ArrayUtils.add(UtilArray.subArray(result[i], j), 0, "Places");
					if (j > 0) {
						result = ArrayUtils.add(result, place);
						result[i] = ArrayUtils.subarray(result[i], 0, j);
					} else {
						result[i] = place;
					}
				}

				final Matcher m = CITY_STATE.matcher(s);
				if (m.find()) {
					String state = PlaceFacetValueParser.getPlaceAbbrevs().get(m.group(3).toLowerCase());
					if (state != null) {
						state = "Places--" + state;
						final String city = m.group(2);
						if (UtilString.isNonEmptyString(city)) {
							state += "--" + city;
						}
						if (handler.cities != null && handler.cities.contains(m.group(1).toLowerCase())) {
							state += "--" + m.group(1);
							result[i] = state.split("--");
						} else if (m.group(1).length() == 0) {
							result[i] = state.split("--");
						} else {
							result[i][j] = m.group(1);
							result = ArrayUtils.add(result, state.split("--"));
						}
					} else {
						// System.out.println("Not a state: " + s);
						final Pattern p2 = Pattern.compile("(.+)\\s*\\((.+)\\)\\z");
						final Matcher m2 = p2.matcher(s);
						if (m2.find()) {
							String[] augmented = null;
							result[i][j] = m2.group(1);
							if (j > 0) {
								augmented = ArrayUtils.subarray(result[i], 0, j);
							}
							augmented = ArrayUtils.add(augmented, m2.group(2));
							assert augmented != null;
							augmented = UtilArray.append(augmented, UtilArray.subArray(result[i], j));
							result[i] = augmented;
						} else {
							System.err.println("Warning: SubjectFacetValueParser: No geographic match for " + s);
						}
					}
				}
				// if (s.startsWith("(Pittsburgh, Pa.)"))
				// System.err.println(s + " => " + UtilString.join(result[i]));
			}
		}
		result = UtilArray.delete(result, null);
		for (int i = 0; i < result.length; i++) {
			if (/* result[i] != null && */!result[i][0].equals("Places")
					&& ArrayUtils.indexOf(result[i], "Pennsylvania", 1) > 0) {
				System.out.println("Should this be a place? [" + UtilString.join(result[i], ",") + "] " + value);
			}
		}
		return result;
	}

	/**
	 * E.g. "Downtown (Pittsburgh, Pa.)" where Pa is in abbrevs. Only adds
	 * Downtown if it is in cities.
	 */
	private static final Pattern CITY_STATE = Pattern
			.compile("\\s*(.*?)\\,?\\s*\\((\\w*?)\\.?,?\\s*(\\w+)\\.?\\)\\.?\\z");

}

/**
 * input: <term1> AND <term2> AND ...
 *
 * output: {{<term1>}, {<term2>}, ... }
 */
class ANDedValuesParser implements FacetValueParser {

	private static final ANDedValuesParser SELF = new ANDedValuesParser();

	static ANDedValuesParser getInstance() {
		return SELF;
	}

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler handler) {
		final String[] terms = value.split(" and ");
		final String[][] result = new String[terms.length][];
		for (int i = 0; i < result.length; i++) {
			result[i] = new String[1];
			result[i][0] = terms[i];
		}
		return result;
	}

}
