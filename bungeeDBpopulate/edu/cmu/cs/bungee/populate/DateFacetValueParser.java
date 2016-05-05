package edu.cmu.cs.bungee.populate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * parse a date and return ["20th century", "1990s", "1996", "12/1996",
 * "12/31/1996"]
 *
 */
class DateFacetValueParser implements FacetValueParser {

	private static final DateFacetValueParser SELF = new DateFacetValueParser();

	static DateFacetValueParser getInstance() {
		return SELF;
	}

	// public static void main(final String[] args) {
	// System.out.println(UtilString.valueOfDeep(SELF.parse("9/15/13", null)));
	// }

	@Override
	public String[][] parse(final String value, @SuppressWarnings("unused") final PopulateHandler _handler) {
		final boolean debug = false;
		final String trimmedValue = trimDate(value, debug);
		final String[][] result = { hierDateValues(trimmedValue, debug) };
		// System.out.println("parse date " + value + "\n" + trimmedValue);
		return result;
	}

	private static String trimDate(final String value, final boolean debug) {
		// Get rid of prefixes in front of the first digit: 'ca', 'c', '(c)',
		// '(C)', 'approximately', etc.
		// possibly followed by '.' and whitespace
		String result = value.replaceAll(
				"\\b(?:ca|c|\\([cC]\\)|late-?|early-?|mid-?|by|approximately|between|before|after|photographed|published)\\.?\\s*(\\d)",
				"$1");

		// 1776, mumbled 1802 => 1776-1802
		result = result.replaceAll("(\\d{4})(?:,|/)\\D+(\\d{4})", "$1-$2");

		// Get rid of AD, A.D., etc.
		result = result.replaceAll("\\WA\\.?D\\.?\\W", "");

		// Get rid of (?) suffixes
		result = result.replaceAll("\\(\\?\\)", "");
		// Used to be "\\d\\?", but that loses on "1911?"
		result = result.replaceAll("\\?", "");

		// '[19]75' => '1975', '19]75' => '1975'
		result = result.replaceAll("\\[((?:17)|(?:18)|(?:19)|(?:20))\\]", "$1");
		result = result.replaceAll("\\b((?:17)|(?:18)|(?:19)|(?:20))\\]", "$1");
		result = result.replaceAll("19\\]", "19");

		// 19-- => '20th Century'
		Pattern p = Pattern.compile("\\b(\\d{2})--\\??");
		Matcher m = p.matcher(result);
		while (m.find()) {
			result = centuryNumberToName(m.group(1));
			m.reset(result);
		}

		// '1920's' => '1920s'
		result = result.replaceAll("\\b(\\d{4})'s\\z", "$1s");

		// '1942/1945' => '1942-1945'
		result = result.replaceAll("\\b(\\d{4})/(\\d{4})\\z", "$1-$2");

		// '1945-4-15' => '4/15/1945'
		result = result.replaceAll("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\z", "$2/$3/$1");

		// '1945-6' => '1945-1946'
		result = result.replaceAll("\\b(\\d{3})(\\d)-(\\d)\\z", "$1$2-$1$3");
		// '1945-46' => '1945-1946'
		result = result.replaceAll("\\b(\\d{2})(\\d{2})-(\\d{2})\\z", "$1$2-$1$3");

		// '194[5]' => '1945'
		result = result.replaceAll("\\b(\\d{3})\\[(\\d)\\]", "$1$2");

		// '194-?', '194-' => '1940s'
		result = result.replaceAll("\\b(\\d{3})-\\??(?:\\D|\\z)", "$10s");

		// '1956 or 1966' => 1956-1966
		p = Pattern.compile("\\b(\\d{4})\\s*(?:\\W|to|and|adn|or)\\s*(\\d{4})");
		m = p.matcher(result);
		if (m.find()) {
			String start = m.group(1);
			String end = m.group(2);
			if (end.compareTo(start) < 0) {
				final String s = end;
				end = start;
				start = s;
			}
			result = start + "-" + end;
		}

		// 2015 july, 4
		p = Pattern.compile("(\\d{4})\\s*([a-zA-Z]{3,})\\.?\\s*(\\d{0,2})");
		m = p.matcher(result);
		while (m.find()) {
			final String monthName = m.group(2);
			final int month = monthNameToNumber(monthName);
			if (month > 0) {
				final String day = m.group(3);
				if (day.length() == 0) {
					result = month + "/" + m.group(1);
				} else {
					result = month + "/" + day + "/" + m.group(1);
				}
				m.reset(result);
			}
		}

		// 4 july, 2015
		p = Pattern.compile("\\b(\\d{0,2})\\s*([a-zA-Z]{3,})(?:\\.|,)?\\s*(\\d{4})\\z");
		m = p.matcher(result);
		while (m.find()) {
			final String monthName = m.group(2);
			final int month = monthNameToNumber(monthName);
			if (month > 0) {
				final String day = m.group(1);
				final String year = m.group(3);
				if (day.length() == 0) {
					result = month + "/" + year;
				} else {
					result = month + "/" + day + "/" + year;
				}
				m.reset(result);
				// System.out.println(value + " " + result);
			}
		}

		// 'Nov. 20, 1908' => '11/20/1908'
		p = Pattern
				// .compile(
				// "\\b\\s*([a-zA-Z]{3,})(?:\\.|,)?\\s*(\\d{0,2})[a-z]{0,2},?\\s*(\\d{4})\\z"
				// );
				// .compile(
				// "\\b([a-zA-Z]{3,})(?:\\.|,)?\\s*(\\d{0,2})[a-z]{0,2},?\\s*(\\d{4})\\z"
				// );
				.compile("\\b\\s*([a-zA-Z]{3,})(?:\\.|,)?\\s*(\\d{0,2})[a-z]{0,2},?\\s*(\\d{4})(?:\\D|\\z)");
		m = p.matcher(result);
		while (m.find()) {
			final String monthName = m.group(1);
			final int month = monthNameToNumber(monthName);
			if (month > 0) {
				final String day = m.group(2);
				final String year = m.group(3);
				if (day.length() == 0) {
					result = month + "/" + year;
				} else {
					result = month + "/" + day + "/" + year;
				}
				m.reset(result);
			}
			if (debug) {
				System.out.println("'Nov. 20, 1908' => '11/20/1908' rule: " + monthName + " " + result);
			}
		}

		// 'foo, 1945' => '1945'
		result = result.replaceAll("\\b\\D+,(\\s*\\d{4})", "$1");

		// 2005-10-22 => 10/22/2005
		p = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
		m = p.matcher(result);
		if (m.find()) {
			final String group2 = m.group(2);
			assert group2 != null;
			final String group3 = m.group(3);
			assert group3 != null;
			result = UtilString.trimLeadingZeros(group2) + "/" + UtilString.trimLeadingZeros(group3) + "/" + m.group(1);
		}

		// for Chartres
		p = Pattern.compile("\\b(\\d{4})(\\d{2})(\\d{2})\\z");
		m = p.matcher(result);
		if (m.find()) {
			final String year = m.group(1);
			final String month = m.group(2);
			final String day = m.group(3);
			if (Integer.parseInt(month) <= 12 && Integer.parseInt(day) <= 31) {
				result = year;
			}
		}
		result = GenericFacetValueParser.trim(result);
		if (debug) {
			System.out.println("DateFacetValueParser.trimDate " + value + " => " + result);
		}
		return result;
	}

	/**
	 * @param value
	 *            Arbitrary string representing a date or date range.
	 * @param debug
	 * @return ["20th century", "1990s", "1996", "12/1996", "12/31/1996"]
	 */
	static @NonNull String[] hierDateValues(final String value, final boolean debug) {
		String[] result = { value };
		String parentName = null;

		// Split 5/20/1955-5/20/1956
		final String[] dateRange = value.split("-");
		if (dateRange.length == 1) {
			// Split month/day/year
			final String[] date = value.split("/");
			final int nDateParts = date.length;
			if (nDateParts == 1) {
				if (value.length() == 4) {
					parentName = value.substring(0, 3) + "0s";
				} else if (value.length() == 5 && value.endsWith("0s")) {
					try {
						parentName = centuryNumberToName(value.substring(0, 2));
					} catch (final java.lang.NumberFormatException e) {
						System.err.println("NumberFormatException for date " + value);
					}
				}
			} else if (2 <= nDateParts && nDateParts <= 3) {
				final String yearString = date[nDateParts - 1];
				final String year = yearString.length() == 2 ? "20" + yearString : yearString;
				if (yearString.length() == 2) {
					result[0] = date[0] + "/" + date[1] + "/" + year;
				}
				if (year.length() == 4) {
					if (nDateParts == 2) {
						parentName = year;
					} else // if (nDateParts == 3)
					{
						parentName = date[0] + "/" + year;
					}
				}
			}
		} else if (dateRange.length == 2 && isYear(dateRange[0]) && isYear(dateRange[1])) {
			if (dateRange[0].substring(0, 3).equals(dateRange[1].substring(0, 3))) {
				parentName = dateRange[0].substring(0, 3) + "0s";
			} else if (dateRange[0].substring(0, 2).equals(dateRange[1].substring(0, 2))) {
				parentName = centuryNumberToName(dateRange[0].substring(0, 2));
			} else {
				final int century1 = Integer.parseInt(dateRange[0].substring(0, 2));
				final int century2 = Integer.parseInt(dateRange[1].substring(0, 2));
				if (century1 + 1 == century2) {
					parentName = centuryNumberToName(dateRange[0].substring(0, 2)).substring(0, 4) + "-"
							+ centuryNumberToName(dateRange[1].substring(0, 2));
				}
			}
		}
		if (parentName != null) {
			result = UtilArray.append(hierDateValues(parentName, debug), result);
		}
		if (debug) {
			System.out
					.println("DateFacetValueParser.hierDateValues " + value + " => " + UtilString.valueOfDeep(result));
		}
		assert result != null;
		return result;
	}

	private static final String[] MONTHS = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct",
			"nov", "dec", "jany", "feby", "mar", "apl", "may", "jun", "jul", "augt", "sepr", "octr", "novr", "decr",
			"january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
			"november", "december", "sept" };

	/**
	 * @param monthName
	 * @return int between 1 and 12, or -1 if not interpretable
	 */
	private static int monthNameToNumber(final String monthName) {
		int month = ArrayUtils.indexOf(MONTHS, monthName.toLowerCase());
		if (month == 36) {
			month = 9;
		} else if (month >= 0) {
			month = 1 + (month % 12);
		}
		return month;
	}

	/**
	 * @param prefix
	 *            2-digit string, e.g. "20"
	 * @return e.g. "21st century"
	 */
	private static String centuryNumberToName(final String prefix) {
		try {
			String suffix = null;
			final int n = Integer.parseInt(prefix) + 1;
			if (n < 4 || n > 20) {
				switch (n % 10) {
				case 1:
					suffix = "st century";
					break;
				case 2:
					suffix = "nd century";
					break;
				case 3:
					suffix = "rd century";
					break;
				default:
					suffix = "th century";
					break;
				}
			}
			return n + suffix;
		} catch (final NumberFormatException e) {
			System.err.println("NumberFormatException for century " + prefix);
			// e.printStackTrace();
			return prefix;
		}
	}

	private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");

	/**
	 * @param s
	 * @return whether s matches "\\d{4}"
	 */
	private static boolean isYear(final String s) {
		return YEAR_PATTERN.matcher(s).matches();
	}

}
