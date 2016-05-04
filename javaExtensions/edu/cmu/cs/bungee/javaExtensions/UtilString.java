package edu.cmu.cs.bungee.javaExtensions;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextMeasurer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.Collator;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.jboss.dna.Inflector;

public class UtilString {
	public enum Justification {
		LEFT, CENTER, RIGHT
	}

	static final @NonNull Pattern IS_PRINTABLE_PATTERN = Util.nonNull(Pattern.compile("[^\\p{Cntrl}]"));

	public static boolean isPrintableChar(final char c) {
		return IS_PRINTABLE_PATTERN.matcher(Character.toString(c)).matches();
	}

	/**
	 * The end of line string for this machine.
	 */
	public static final @NonNull String LINE_SEPARATOR = Util.nonNull(System.getProperty("line.separator", "\n"));

	public static boolean isNonEmptyString(final @Nullable String s) {
		return s != null && s.length() > 0;
	}

	public static @Nullable ParseException isNotParsable(final @NonNull String s, final @NonNull Format format) {
		ParseException result = null;
		try {
			format.parseObject(s);
		} catch (final ParseException e) {
			result = e;
		}
		return result;
	}

	private static final @NonNull DecimalFormat PVALUE_FORMAT = new DecimalFormat("0E0");

	private static final @NonNull DecimalFormat PVALUE_FORMAT2 = new DecimalFormat("0.0#");

	private static final @NonNull FieldPosition STUPID = new FieldPosition(NumberFormat.FRACTION_FIELD);

	public static @NonNull StringBuffer appendPvalue(final double pValue, final @NonNull StringBuffer buf) {
		if (!Double.isNaN(pValue)) {
			buf.append("p=");
			if (pValue == 0.0) {
				buf.append("0");
			} else if (pValue == 1.0) {
				buf.append("1");
			} else if (pValue > 0.0095) {
				PVALUE_FORMAT2.format(pValue, buf, STUPID);
			} else {
				PVALUE_FORMAT.format(pValue, buf, STUPID);
			}
		}
		return buf;
	}

	private static final @NonNull NumberFormat INTEGER_FORMAT = Util.nonNull(NumberFormat.getIntegerInstance());
	private static final @NonNull NumberFormat DOUBLE_FORMAT = Util.nonNull(NumberFormat.getNumberInstance());

	static @NonNull String addCommas(final double d) {
		final String result = d > -1000.0 && d < 1000.0 ? Double.toString(d) : DOUBLE_FORMAT.format(d) + " (" + d + ")";
		return Util.nonNull(result);
	}

	/**
	 * @param d
	 * @param padToLengthLeft
	 * @param padToLengthRight
	 *            number of characters after decimal point.
	 *
	 *            If 0, add no decimal point or spaces on the right.
	 *
	 *            (If d is an integer and padToLengthRight>0, it will be
	 *            followed by padToLengthRight+1 spaces, with no decimal point.)
	 */
	static @NonNull String addCommas(final double d, final int padToLengthLeft, final int padToLengthRight) {
		String result = addCommas((int) d, padToLengthLeft);
		if (padToLengthRight > 0) {
			final String fraction = fraction(d);
			final int lengthDiff = padToLengthRight - (fraction.length() - 1);
			if (lengthDiff < 0) {
				result += fraction.substring(0, padToLengthRight + 1);
			} else {
				result += fraction;
				if (lengthDiff > 0) {
					result += getSpaces(lengthDiff);
				}
			}
		}
		return result;
	}

	/**
	 * @return " " if d is an integer, else '\\.\\d+'
	 */
	static @NonNull String fraction(final double d) {
		String result = " ";
		if (d != (int) d) {
			final String[] split = Double.toString(d).split("\\.");
			assert assertInRange(split.length, 1, 2);
			if (split.length == 2) {
				result = "." + split[1];
			}
		}
		return result;
	}

	/**
	 * @return format n with commas separating thousands, millions, etc.,
	 *         left-padded with spaces to padToLength.
	 */
	static @NonNull String addCommas(final int n, final int padToLength) {
		String result = addCommas(n);
		final int nSpaces = padToLength - result.length();
		if (nSpaces < 0) {
			System.err.println("Warning: UtilString.addCommas padToLength=" + padToLength + " is too short for " + n
					+ UtilString.getStackTrace());
		} else {
			result = getSpaces(nSpaces) + result;
		}
		return result;
	}

	/**
	 * @return format n with commas separating thousands, millions, etc.,
	 *         left-padded with spaces to padToLength.
	 */
	static @NonNull String addCommas(final long n, final int padToLength) {
		String result = addCommas(n);
		final int nSpaces = padToLength - result.length();
		if (nSpaces < 0) {
			System.err.println("UtilString.addCommas padToLength=" + padToLength + " is too short for " + n
					+ UtilString.getStackTrace());
		} else {
			result = getSpaces(nSpaces) + result;
		}
		return result;
	}

	/**
	 * @return format n with commas separating thousands, millions, etc
	 */
	public static @NonNull String addCommas(final int n) {
		final String result = n > -1000 && n < 1000 ? Integer.toString(n) : INTEGER_FORMAT.format(n);
		return Util.nonNull(result);
	}

	public static @NonNull String addCommas(final long n) {
		final String result = n > -1000 && n < 1000 ? Long.toString(n) : INTEGER_FORMAT.format(n);
		return Util.nonNull(result);
	}

	static int numChars(final int n) {
		final int absN = Math.abs(n);
		final int nDigits = 1 + (absN == 0 ? 0 : (int) Math.log10(absN));
		final int nCommas = nDigits < 4 ? 0 : (nDigits - 1) / 3;
		final int result = (n >= 0 ? 0 : 1) + nDigits + nCommas;
		return result;
	}

	static int numChars(final long n) {
		final long absN = Math.abs(n);
		final int nDigits = 1 + (absN == 0 ? 0 : (int) Math.log10(absN));
		final int nCommas = nDigits < 4 ? 0 : (nDigits - 1) / 3;
		final int result = (n >= 0 ? 0 : 1) + nDigits + nCommas;
		return result;
	}

	private static @NonNull String[] spaceStrings = repeatStrings(" ", 200);

	/**
	 * @param n
	 *            number of spaces
	 * @return string composed of n spaces
	 */
	static @NonNull public String getSpaces(final int n) {
		assert n >= 0 : n;
		if (spaceStrings.length <= n) {
			spaceStrings = repeatStrings(" ", n + 50);
		}
		final String result = spaceStrings[n];
		assert result != null;
		return result;
	}

	public static @NonNull String bufToString(final @NonNull StringBuilder buf) {
		final String result = buf.toString();
		assert result != null;
		return result;
	}

	public static @NonNull String[] repeatStrings(final @NonNull String chars, final int n) {
		assert assertInRange(n, 1, 2000);
		final String[] result = new String[n];
		final StringBuilder buf = new StringBuilder(n);
		for (int i = 0; i < n; i++) {
			result[i] = bufToString(buf);
			buf.append(chars);
		}
		return result;
	}

	public static @NonNull String maybePluralize(final @NonNull String s, final int n) {
		if (n == 1) {
			return s;
		} else {
			return pluralize(s);
		}
	}

	/**
	 * @param s
	 * @return add s or es
	 */
	@SuppressWarnings("null")
	public static @NonNull String pluralize(final @NonNull String s) {
		assert isNonEmptyString(s);
		final String pluralized = s.trim().length() == 0 ? s : Inflector.pluralize(s);
		assert isNonEmptyString(pluralized) : "'" + s + "'";
		return pluralized;
	}

	/**
	 * {a, e, i, o, u, A, E, I, O, U}
	 */
	private static final @NonNull char[] VOWELS = { 'a', 'e', 'i', 'o', 'u', 'A', 'E', 'I', 'O', 'U' };

	/**
	 * @param noun
	 * @return "a" or "an"
	 */
	public static @NonNull String indefiniteArticle(final @NonNull String noun) {
		String result = " a ";
		final char c = noun.charAt(0);
		if (ArrayUtils.contains(VOWELS, c)) {
			result = " an ";
		}
		return result;
	}

	public static int nOccurrences(final @NonNull String s, final char c) {
		int n = 0;
		int index = -1;
		while ((index = s.indexOf(c, index + 1)) >= 0) {
			n += 1;
		}
		return n;
	}

	static int nOccurrences(final @NonNull int[] a, final int i) {
		int n = 0;
		for (final int element : a) {
			if (element == i) {
				n++;
			}
		}
		return n;
	}

	public static @NonNull String getLastSplit(final @NonNull String s, final @NonNull String regex) {
		final String[] parts = s.split(regex);
		final String result = parts[parts.length - 1];
		return Util.nonNull(result);
	}

	private static final @NonNull Pattern SEMICOLON_PATTERN = Util.nonNull(Pattern.compile(";"));

	/**
	 * @param s
	 * @return more efficient than s.split(";")
	 */
	public static @NonNull String[] splitSemicolon(final @NonNull String s) {
		final String[] result = SEMICOLON_PATTERN.split(s);
		return Util.nonNull(result);
	}

	private static final @NonNull Pattern COMMA_PATTERN = Util.nonNull(Pattern.compile(","));

	/**
	 * @param s
	 * @return more efficient than s.split(",")
	 */
	public static @NonNull String[] splitComma(final @NonNull String s) {
		final String[] result = COMMA_PATTERN.split(s);
		return Util.nonNull(result);
	}

	/**
	 * Opposite of split; concatenates STRINGLIST using DELIMITER as the
	 * separator. The separator is only added between strings, so there will be
	 * no separator at the beginning or end.
	 * <p>
	 *
	 * @param stringList
	 *            The list of strings that will to be put together
	 * @param delimiter
	 *            The string to put between the strings of stringList
	 * @return string that has DELIMITER put between each of the elements of
	 *         stringList
	 */
	public static @Nullable String join(final @Nullable Object[] stringList, final @NonNull String delimiter) {
		String result = null;
		final int len = stringList == null ? 0 : stringList.length;
		if (len > 0) {
			assert stringList != null;
			final StringBuilder buf = new StringBuilder(len * 20);
			synchronized (stringList) {
				for (int i = 0; i < len - 1; i++) {
					buf.append(stringList[i]);
					buf.append(delimiter);
				}
				// if (len > 0) {
				buf.append(stringList[len - 1]);
				// }
			}
			result = buf.toString();
		}
		return result;
	}

	/**
	 * Opposite of split; concatenates STRINGLIST using DELIMITER as the
	 * separator. The separator is only added between strings, so there will be
	 * no separator at the beginning or end.
	 * <p>
	 *
	 * @param stringList
	 *            The list of strings that will to be put together
	 * @param delimiter
	 *            The string to put between the strings of stringList
	 * @return string that has DELIMITER put between each of the elements of
	 *         stringList
	 */
	public static @NonNull String join(final @NonNull Collection<?> stringList, final @NonNull String delimiter) {
		final StringBuilder buf = new StringBuilder();
		for (final Object object : stringList) {
			if (buf.length() > 0) {
				buf.append(delimiter);
			}
			buf.append(object);
		}
		return bufToString(buf);
	}

	private static @Nullable String join(final @Nullable int[] stringList, final @NonNull String delimiter) {
		String result = null;
		final int len = stringList == null ? 0 : stringList.length;
		if (len > 0) {
			assert stringList != null;
			final StringBuilder buf = new StringBuilder(len * 20);
			synchronized (stringList) {
				for (int i = 0; i < len - 1; i++) {
					buf.append(stringList[i]);
					buf.append(delimiter);
				}
				// if (len > 0) {
				buf.append(stringList[len - 1]);
				// }
			}
			result = buf.toString();
		}
		return result;
	}

	public static @Nullable String join(final @Nullable int[] s) {
		return join(s, ", ");
	}

	public static @Nullable String join(final @Nullable Object[] s) {
		return join(s, ", ");
	}

	public static boolean containsAny(final @NonNull String s, final @NonNull CharSequence charSequence) {
		for (int i = 0; i < charSequence.length(); i++) {
			final char ch = charSequence.charAt(i);
			if (s.indexOf(ch) >= 0) {
				return true;
			}
		}
		return false;
	}

	public static @NonNull String html2text(final @NonNull String html) {
		// final String text = Jsoup.parse(html).text();
		// System.out.println("html2text: " + text);

		final JEditorPane p = new JEditorPane();
		p.setContentType("text/html");
		p.setText(html.replace("\n", "</p><p>"));
		final HTMLDocument htmlDocument = (HTMLDocument) p.getDocument();
		String text = null;
		try {
			text = htmlDocument.getText(0, htmlDocument.getLength());
		} catch (final BadLocationException e) {
			e.printStackTrace();
		}
		assert text != null;
		return text;
	}

	public static void printDeep(final @Nullable Object a) {
		System.out.println(valueOfDeep(a));
	}

	public static @NonNull String valueOfDeep(final @Nullable Object a) {
		return valueOfDeep(a, ", ");
	}

	public static @NonNull String valueOfDeep(final @Nullable Object a, final @NonNull NumberFormat numberFormat) {
		return valueOfDeep(a, ", ", numberFormat);
	}

	public static @NonNull String valueOfDeep(final @Nullable Object a, final @NonNull String separator) { // NO_UCD
		// (use
		// default)
		final StringBuilder buf = new StringBuilder();
		valueOfDeepInternal(a, buf, separator, DOUBLE_FORMAT);
		return bufToString(buf);
	}

	static @NonNull String valueOfDeep(final @Nullable Object a, final @NonNull String separator,
			final @NonNull NumberFormat numberFormat) { // NO_UCD
		// (use
		// default)
		final StringBuilder buf = new StringBuilder();
		valueOfDeepInternal(a, buf, separator, numberFormat);
		return bufToString(buf);
	}

	private static void valueOfDeepInternal(final @Nullable Object a, final @NonNull StringBuilder buf,
			final @NonNull String separator, final NumberFormat numberFormat) {
		if (a == null) {
			buf.append("<null>");
		} else if (isArray(a)) {
			buf.append("[");
			if (a instanceof Object[]) {
				final Object[] ar = (Object[]) a;
				for (int i = 0; i < ar.length; i++) {
					valueOfDeepInternal(ar[i], buf, separator, numberFormat);
					if (i < (ar.length - 1)) {
						buf.append(separator);
					}
				}
			} else if (a instanceof float[]) {
				final float[] ar = (float[]) a;
				for (int i = 0; i < ar.length; i++) {
					buf.append(numberFormat.format(ar[i]));
					if (i < (ar.length - 1)) {
						buf.append(separator);
					}
				}
			} else if (a instanceof double[]) {
				final double[] ar = (double[]) a;
				for (int i = 0; i < ar.length; i++) {
					buf.append(numberFormat.format(ar[i]));
					if (i < (ar.length - 1)) {
						buf.append(separator);
					}
				}
			} else if (a instanceof char[]) {
				final char[] ar = (char[]) a;
				for (int i = 0; i < ar.length; i++) {
					buf.append(ar[i]);
					if (i < (ar.length - 1)) {
						buf.append(separator);
					}
				}
			} else if (a instanceof int[]) {
				final int[] ar = (int[]) a;
				for (int i = 0; i < ar.length; i++) {
					buf.append(ar[i]);
					if (i < (ar.length - 1)) {
						buf.append(separator);
					}
				}
			} else {
				System.err.println("Can't find match for " + a.getClass());
				buf.append(a);
			}
			buf.append("]");
		} else if (a instanceof Collection) {
			buf.append("<");
			for (final Iterator<?> iterator = ((Collection<?>) a).iterator(); iterator.hasNext();) {
				valueOfDeepInternal(iterator.next(), buf, separator, numberFormat);
				if (iterator.hasNext()) {
					buf.append(separator);
				}
			}
			buf.append(">");
		} else {
			buf.append(a);
		}
	}

	private static boolean isArray(final @NonNull Object target) {
		final Class<? extends Object> targetClass = target.getClass();
		return targetClass.isArray();
	}

	public static @NonNull String shortClassName(final @NonNull Object object) {
		String className = object.getClass().getName();
		className = className.substring(className.lastIndexOf('.') + 1);
		return Util.nonNull(className);
	}

	static @NonNull String shortClassName(final @NonNull Class<?> object) {
		String className = object.getName();
		assert className != null;
		// className = className.substring(className.lastIndexOf('.') + 1);
		className = getLastSplit(className, "\\.");
		return className;
	}

	// public static @NonNull String toString(final @NonNull Object object,
	// final @NonNull StringBuilder buf) {
	// final String desc = buf.toString();
	// assert desc != null;
	// return toString(object, desc);
	// }

	public static @NonNull String toString(final @NonNull Object object, final Object desc) {
		return "<" + shortClassName(object) + " " + desc + ">";
	}

	/**
	 * Save a few keystrokes.
	 */
	public static void printDateNmessage(final @Nullable Object msg) {
		final String msgString = msg == null ? "<null>" : msg.toString();
		System.out.println("\n" + (new Date().toString()) + "  " + msgString);
	}

	private static @NonNull String indent = "";

	public static void indent0(final @NonNull String msg) { // NO_UCD (unused
															// code)
		indent = "";
		indent(msg);
	}

	public static void indentMore(final @NonNull String msg) { // NO_UCD (unused
																// code)
		indent(msg);
		indent += "    ";
	}

	/**
	 * @param msg
	 *            if null, don't print. Just change indentation.
	 */
	public static void indentLess(final @Nullable String msg) { // NO_UCD
																// (unused code)
		final int newLength = indent.length() - 4;
		assert newLength >= 0 : newLength;
		final int l = Math.max(0, newLength);
		final String _indent = indent.substring(0, l);
		assert _indent != null;
		indent = _indent;
		if (msg != null) {
			indent(msg);
		}
	}

	public static void indent(final @NonNull String msg) { // NO_UCD (use
															// default)
		final String lines = msg.replaceAll("\n", "\n" + indent);
		System.out.println(indent + lines);
	}

	public static @NonNull String getStackTrace() {
		return getStackTrace(new RuntimeException("Relax.  There's no error, we're just printing the stack trace"));
	}

	public static @NonNull String getStackTrace(final @NonNull Throwable e) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		final String result = "\n" + sw.toString();
		assert result != null;
		return result;
	}

	public static @NonNull String commonPrefix(final @NonNull String s1, final @NonNull String s2) {
		String prefix = s1;
		for (int i = 0; i < s1.length(); i++) {
			if (s2.length() <= i || !stringEqualsUS(s1.substring(i, i + 1), s2.substring(i, i + 1))) {
				prefix = prefix.substring(0, i);
				break;
			}
		}
		assert prefix != null;
		return prefix;
	}

	/**
	 * Locale.US, setStrength(Collator.PRIMARY),
	 * getRules().replaceAll("<'\u005f'", "<' '<'\u005f'")
	 *
	 * Ignores SECONDARY ("a" vs "ä") and TERTIARY ("a" vs "A") differences
	 *
	 * This must be equivalent to MySQL collation (utf8mb4_unicode_ci), or
	 * LetterLabeled will break.
	 */
	public static final @NonNull Collator MY_US_COLLATOR = getMyCollator();

	private static @NonNull Collator getMyCollator() {
		final Collator usCollator = Collator.getInstance(Locale.US);
		// Normally, whitespace is a secondary difference, but getLetterOffsets
		// needs to distinguish 'Wright, " from "Wright,". This removes ' ' from
		// ignorable characters.
		// final String rules = ((RuleBasedCollator)
		// usCollator).getRules().replaceAll(";' '", "");
		final String rules = ((RuleBasedCollator) usCollator).getRules().replaceAll("<'\u005f'", "<' '<'\u005f'");
		RuleBasedCollator result = null;
		try {
			result = new RuleBasedCollator(rules);
			result.setStrength(Collator.PRIMARY);
		} catch (final ParseException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	public static boolean stringEqualsUS(final @Nullable String c1, final @Nullable String c2) {
		if (c1 == null) {
			return c2 == null;
		} else if (c2 == null) {
			return false;
		} else {
			return MY_US_COLLATOR.equals(c1, c2);
		}
	}

	public static int stringCompareUS(final @NonNull String c1, final @NonNull String c2) {
		return MY_US_COLLATOR.compare(c1, c2);
	}

	public static @NonNull StringBuffer formatPercent(final int numerator, final int denominator,
			@Nullable StringBuffer buf, final boolean isNegativeOK) {
		if (buf == null) {
			buf = new StringBuffer();
		}
		final double percent = (numerator >= 0 && denominator > 0) ? (100.0 * numerator) / denominator : -1.0;
		if (percent < 0) {
			assert isNegativeOK : numerator + " / " + denominator;
			buf.append("?");
		} else if (percent >= 0.95 || percent == 0.0) {
			// Anything larger than this will round to 1.0 if we go down to
			// tenths.
			buf.append(((int) (percent + 0.5)));
		} else if (percent >= 0.095 || percent < 0.00095) {
			buf.append("0.").append(((int) (percent * 10.0 + 0.5)));
		} else if (percent >= 0.0095) {
			buf.append("0.0").append(((int) (percent * 100.0 + 0.5)));
		} else if (percent >= 0.00095) {
			buf.append("0.00").append(((int) (percent * 1000.0 + 0.5)));
		}
		buf.append("%");
		return buf;
	}

	public static @NonNull String capitalize(final @NonNull String s) {
		if (s.length() == 0) {
			return s;
		}
		return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
	}

	public static @NonNull String trimLeadingZeros(final @NonNull String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) != '0') {
				if (i == 0) {
					return s;
				} else {
					return Util.nonNull(s.substring(i));
				}
			}
		}
		return "0";
	}

	/**
	 * @return time in milliseconds
	 */
	public static long now() {
		return new Date().getTime();
	}

	/**
	 * @return labeled (in ms) elapsed time
	 */
	public static @NonNull String elapsedTimeString(final long start, final int padToLength) {
		return timeString(elapsedTime(start), padToLength);
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return labeled (in ms) time
	// */
	// public static @NonNull String timeString() {
	// return addCommas(now()) + "ms";
	// }

	/**
	 * @return labeled (in ms) time
	 */
	static @NonNull String timeString(final long ms, final int padToLength) {
		return addCommas(ms, padToLength) + "ms";
	}

	/**
	 * @return elapsed time in ms, or -1 if start > now().
	 */
	public static long elapsedTime(final long start) {
		final long now = now();
		long result;
		if (start <= now) {
			// avoid overflow
			result = now - start;
		} else {
			result = -1L;
		}
		// System.out.println("UtilString.elapsedTime start=" + addCommas(start)
		// + " now=" + addCommas(now) + " => "
		// + addCommas(result));
		return result;
	}

	/**
	 * @return match(s, "(.*)<suffix>*").group(1)
	 */
	public static @NonNull String removeSuffix(@NonNull String s, final @NonNull String suffix) {
		while (s.endsWith(suffix)) {
			s = Util.nonNull(s.substring(0, s.length() - suffix.length()));
		}
		return s;
	}

	/**
	 * @return "[\n" + <element 1> + "\n" + ... <+ element n> + "]"
	 */
	public static @NonNull String formatCollection(final @NonNull Collection<?> collection) {
		final StringBuilder buf = new StringBuilder("[");
		for (final Object element : collection) {
			buf.append("\n    ").append(element);
		}
		buf.append("]");
		return bufToString(buf);
	}

	/**
	 * @return a double that equals an int
	 */
	public static double getStringWidth(final @Nullable String text, final @NonNull Font font) {
		double result = 0.0;
		if (UtilString.isNonEmptyString(text)) {
			// System.out.println("getStringWidth " + text);
			assert text != null;
			// final TextMeasurer measurer = getTextMeasurer(text, font);
			// result = measurer.getAdvanceBetween(0, text.length());

			result = Math.ceil(font.getStringBounds(text, RENDER_QUALITY_HIGH_FRC).getWidth());
		}
		return result;
	}

	private static final @NonNull FontRenderContext RENDER_QUALITY_HIGH_FRC = new FontRenderContext(null, true, true);

	public static @NonNull TextMeasurer getTextMeasurer(final @NonNull String text, final @NonNull Font font) {
		final AttributedString atString = new AttributedString(text);
		atString.addAttribute(TextAttribute.FONT, font);
		final AttributedCharacterIterator itr = atString.getIterator();
		return new TextMeasurer(itr, RENDER_QUALITY_HIGH_FRC);
	}

	// Returns "" if width <= 0
	// TODO Remove unused code found by UCDetector
	// static @NonNull String stringForWidth(final double width, final @NonNull
	// Font font) {
	// return stringForWidth(width, font, THIN_SPACE);
	// }

	// Returns "" if width <= 0
	static @NonNull String stringForWidth(final double width, final @NonNull Font font, final @NonNull String padWith) {
		// assert width >= 0.0 : "stringForWidth given negative width: " + width
		// + "\n" + UtilString.getStackTrace();
		final int nChars = Math.max(0, (int) (width / averageCharWidth(padWith, font) + 0.5));
		final String result = StringUtils.repeat(padWith, nChars);
		assert result != null;
		return result;
	}

	// /**
	// * A very thin space, so alignment can be precise.
	// */
	// private static final @NonNull String THIN_SPACE = "\u200A";
	//
	// private static @NonNull String thinSpacesString(final float width,Font
	// font) {
	// float thinSpaceWidth=averageCharWidth(THIN_SPACE, font);
	// int nChars=(int)width/
	// if (thinSpaceStrings.length <= nChars) {
	// thinSpaceStrings = UtilString.repeatStrings(THIN_SPACE, nChars + 50);
	// }
	// final String result = thinSpaceStrings[nChars];
	// assert result != null;
	// return result;
	// }

	private static final Map<Font, Map<String, Double>> AVERAGE_CHAR_WIDTHS = new HashMap<>();

	private static double averageCharWidth(final @NonNull String s, final @NonNull Font font) {
		Map<String, Double> map = AVERAGE_CHAR_WIDTHS.get(font);
		if (map == null) {
			map = new HashMap<>();
			AVERAGE_CHAR_WIDTHS.put(font, map);
		}
		double result;
		final Double resultF = map.get(s);
		if (resultF == null) {
			final int sLength = s.length();
			final int repeats = 1000 / sLength;
			final StringBuilder repeated = new StringBuilder();
			for (int i = 0; i < repeats; i++) {
				repeated.append(s);
			}
			result = getStringWidth(Util.nonNull(repeated.toString()), font) / (sLength * repeats);
			map.put(s, result);
		} else {
			result = resultF;
		}
		return result;
	}

}
