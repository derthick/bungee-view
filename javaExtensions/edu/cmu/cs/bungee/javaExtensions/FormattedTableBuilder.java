package edu.cmu.cs.bungee.javaExtensions;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilString.Justification;

public class FormattedTableBuilder {

	static final @NonNull Font DEFAULT_FONT = new Font("Courier New", Font.PLAIN, 10);

	int maxFractionChars = Integer.MAX_VALUE;
	@NonNull
	String paddingChar = " ";
	private int minSpacing = 3;

	final @NonNull Table table;
	private final @NonNull List<UtilString.Justification> justifications = new LinkedList<>();
	private static final @NonNull UtilString.Justification DEFAULT_JUSTIFICATION = UtilString.Justification.LEFT;

	public FormattedTableBuilder() {
		table = new Table();
	}

	private @NonNull UtilString.Justification getColumnJustification(final int column) {
		final UtilString.Justification result = (justifications.size() > column) ? justifications.get(column)
				: DEFAULT_JUSTIFICATION;
		assert result != null;
		return result;
	}

	public void setColumnJustifications(final UtilString.Justification... _justifications) {
		final int nJustifications = _justifications.length;
		assert nJustifications <= table.columnCount() : UtilString.valueOfDeep(_justifications) + " specifies "
				+ nJustifications + " columns, but table only has " + table.columnCount();
		for (int column = 0; column < nJustifications; column++) {
			final Justification justification = _justifications[column];
			assert justification != null;
			setColumnJustification(column, justification);
		}
	}

	void setColumnJustification(final int column, final @NonNull UtilString.Justification justification) {
		assert column < table.columnCount() && column >= 0 : column + " columnCount=" + table.columnCount();
		for (int c = 0; c < table.columnCount(); c++) {
			if (justifications.size() > c) {
				if (c == column) {
					justifications.set(c, justification);
				}
			} else {
				justifications.add(c == column ? justification : DEFAULT_JUSTIFICATION);
			}
		}
	}

	/**
	 * set the character that is used for values that don't exist.
	 *
	 * @param paddingChar1
	 *            padding char
	 */
	public void setPaddingChar(final @NonNull String paddingChar1) {
		paddingChar = paddingChar1;
	}

	public void setMaxFractionChars(final int _maxFractionChars) {
		maxFractionChars = _maxFractionChars;
	}

	/**
	 * the minimum amount of spaces between columns in the formatted output.
	 *
	 * Must be at least 0, but a minimum of at least 1 is suggested.
	 *
	 * @param minSpacing1
	 *            minimum of spaces (must be at least 0)
	 */
	public void setMinSpacing(final int minSpacing1) {
		if (minSpacing1 < 0) {
			throw new IllegalArgumentException("min spacing must be at least 0");
		}
		minSpacing = minSpacing1;
	}

	// TODO Remove unused code found by UCDetector
	// interface Formatter<T> {
	// String[] format(T object);
	// }

	// TODO Remove unused code found by UCDetector
	// public <T> FormattedTableBuilder add(final List<T> objects,
	// final Formatter<T> formatter) {
	// for (final T object : objects) {
	// addLine(formatter.format(object));
	// }
	// return this;
	// }

	// public <T extends Object> FormattedTableBuilder addLine(
	// final List<T> lineContent) {
	// return addLine(table.lineCount(), lineContent);
	// }

	public <T extends Object> FormattedTableBuilder addLine(final List<T> lineContent) {
		return addLine(table.lineCount(), lineContent);
	}

	public FormattedTableBuilder addLine(final Object... lineContent) {
		return addLine(table.lineCount(), Arrays.asList(lineContent));
	}

	<T extends Object> FormattedTableBuilder addLine(final int position, final List<T> lineContent) {
		table.addLine(position, new Line(lineContent));
		return this;
	}

	// TODO Remove unused code found by UCDetector
	// public FormattedTableBuilder addLine(final Object... lineContent) {
	// final List<String> fields = new ArrayList<>(lineContent.length);
	// for (final Object field : lineContent) {
	// fields.add(field.toString());
	// }
	// return addLine(table.lineCount(), fields);
	// }

	// TODO Remove unused code found by UCDetector
	// public FormattedTableBuilder addLine(final int position,
	// final String... lineContent) {
	// return addLine(position, new ArrayList<>(Arrays.asList(lineContent)));
	// }

	@NonNull
	FormattedTableBuilder addColumn(final int position, final @NonNull List<String> columnContent) {
		table.addColumn(position, columnContent);
		return this;
	}

	// TODO Remove unused code found by UCDetector
	// public FormattedTableBuilder addColumn(final List<String> columnContent)
	// {
	// return addColumn(table.columnCount(), columnContent);
	// }

	// TODO Remove unused code found by UCDetector
	// public FormattedTableBuilder addColumn(final String... columnContent) {
	// return addColumn(table.columnCount(),
	// new ArrayList<>(Arrays.asList(columnContent)));
	// }

	// TODO Remove unused code found by UCDetector
	// public FormattedTableBuilder addColumn(final int position,
	// final String... columnContent) {
	// return addColumn(position,
	// new ArrayList<>(Arrays.asList(columnContent)));
	// }

	/**
	 * sets the value at the given column and row to the given content.
	 *
	 * If the value doesn't exist already, an IllegalArgumentException will be
	 * thrown.
	 *
	 * @param column
	 *            column
	 * @param row
	 *            row
	 * @param value
	 *            value
	 * @return previous value
	 */
	public Object set(final int column, final int row, final Object value) {
		return table.set(column, row, value);
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * passes the formatted string to consumer.
	// *
	// * To get the formatted string, use format.
	// *
	// * @param consumer
	// * consumer
	// */
	// public void output(final Consumer<String> consumer) {
	// consumer.accept(format());
	// }

	public @NonNull String format() {
		return format(DEFAULT_FONT);
	}

	/**
	 * Weirdness: some fonts render spaces smaller after supplemental Unicode
	 * chars, and things don't line up, e.g. the 0 and 1 below:
	 *
	 * 0: ☜ 0
	 *
	 * 2: r 1
	 *
	 * Formats all given content so it presents as a well aligned table.
	 *
	 * @return formatted content
	 */
	@NonNull
	String format(final @NonNull Font font) {
		convertToStrings();
		final List<Double> maxContentLengths = getMaxLengths(font);
		final StringBuilder out = new StringBuilder();
		for (final Line line : table.lines) {
			for (int columnIndex = 0; columnIndex < line.size(); columnIndex++) {
				String columnContent = (String) line.get(columnIndex);
				if (columnContent == null) {
					columnContent = "";
				}
				final UtilString.Justification columnJustification = getColumnJustification(columnIndex);
				final double paddingW = maxContentLengths.get(columnIndex)
						- UtilString.getStringWidth(columnContent, font);
				final String padding = UtilString.stringForWidth(
						columnJustification == UtilString.Justification.CENTER ? paddingW / 2f : paddingW, font, " ");

				if (columnJustification == UtilString.Justification.CENTER
						|| columnJustification == UtilString.Justification.RIGHT) {
					out.append(padding);
				}

				out.append(columnContent);

				if (columnJustification == UtilString.Justification.CENTER
						|| columnJustification == UtilString.Justification.LEFT) {
					out.append(padding);
				}

				out.append(UtilString.getSpaces(minSpacing));
			}
			out.append("\n");
		}
		final String result = out.toString();
		assert result != null;
		return result;
	}

	/**
	 * Only called by Format()
	 */
	private void convertToStrings() {
		for (int columnIndex = 0; columnIndex < table.columnCount(); columnIndex++) {
			int maxIntChars = 0;
			int maxFracChars = 0;
			for (final Line line : table.lines) {
				final Object value = line.get(columnIndex);
				if (value instanceof Integer) {
					maxIntChars = Math.max(maxIntChars, UtilString.numChars((int) value));
				} else if (value instanceof Long) {
					maxIntChars = Math.max(maxIntChars, UtilString.numChars((long) value));
				} else if (value instanceof Double) {
					final double d = (double) value;
					maxFracChars = Math.max(maxFracChars, fractionLength(d));
					maxIntChars = Math.max(maxIntChars, UtilString.numChars((int) d));
				} else if (!(value instanceof String)) {
					line.set(columnIndex, value == null ? "<null>" : value.toString());
				}
			}
			if (maxIntChars > 0) {
				for (final Line line : table.lines) {
					final Object value = line.get(columnIndex);
					if (value instanceof Integer) {
						line.set(columnIndex, UtilString.addCommas((int) value, maxIntChars, maxFracChars));
					} else if (value instanceof Long) {
						line.set(columnIndex, UtilString.addCommas((long) value, maxIntChars, maxFracChars));
					} else if (value instanceof Double) {
						line.set(columnIndex, UtilString.addCommas((double) value, maxIntChars, maxFracChars));
					}
				}
			}
		}
	}

	private int fractionLength(final double value) {
		final String fraction = UtilString.fraction(value);
		return " ".equals(fraction) ? 0 : Math.min(maxFractionChars, fraction.length());
	}

	@Override
	public String toString() {
		final StringBuilder out = new StringBuilder();
		for (final Line line : table.lines) {
			for (final Object field : line) {
				out.append(field).append("            ");
			}
		}
		return UtilString.toString(this, out);
	}

	/**
	 * returns a list containing the length of the longest string of each
	 * column.
	 *
	 * @return lengths
	 */
	private @NonNull List<Double> getMaxLengths(final @NonNull Font font) {
		final int columnCount = table.columnCount();
		final List<Double> maxContentLengths = new ArrayList<>(columnCount);
		for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
			double colMax = 0f;
			for (final Line line : table.lines) {
				if (line.size() > columnIndex) {
					final String columnContent = (String) line.get(columnIndex);
					if (columnContent != null) {
						final double length = UtilString.getStringWidth(columnContent, font);
						if (length > colMax) {
							colMax = length;
						}
					}
				}
			}
			maxContentLengths.add(colMax);
		}

		// for (final Line line : table.lines) {
		// for (int columnIndex = 0; columnIndex < line.size(); columnIndex++) {
		// final String columnContent = (String) line.get(columnIndex);
		// if (columnContent != null) {
		// final float length = UtilString.getStringWidth(columnContent,
		// font) /* + minSpacing */;
		// if (length > maxContentLengths.get(columnIndex)) {
		// maxContentLengths.set(columnIndex, length);
		// }
		// }
		// }
		// }
		return maxContentLengths;
	}

	private class Table {

		@NonNull
		List<Line> lines;

		public Table() {
			lines = new ArrayList<>();
		}

		public int lineCount() {
			return lines.size();
		}

		public int columnCount() {
			if (lines.isEmpty()) {
				return 0;
			}
			return lines.get(0).size(); // all lines are always the same length
										// because of padding
		}

		/**
		 * @param column
		 *            0 - columnCount()-1
		 * @param row
		 *            0 - lineCount()-1
		 * @return previous value
		 */
		public Object set(final int column, final int row, final Object value) {
			assert assertInRange(column, 0, columnCount() - 1);
			assert assertInRange(row, 0, lineCount() - 1);
			return lines.get(row).set(column, value);
		}

		public void addColumn(final int position, final @NonNull List<String> columnContent) {
			if (position < 0) {
				throw new IllegalArgumentException("Column position is negative");
			}

			// if the new position is outside the current content, extend short
			// lines to position
			if (position > columnCount()) {
				padLinesTo(position);
			}

			// add new lines for content of this column in case the column is
			// too long
			while (lines.size() < columnContent.size()) {
				addLinePaddedTo(position);
			}

			shiftAllAt(position, lines); // make space for new column
			for (int lineIndex = 0; lineIndex < columnContent.size(); lineIndex++) { // add
																						// new
																						// column
				set(position, lineIndex, columnContent.get(lineIndex));
			}

			if (columnContent.size() > columnCount()) {
				padLinesTo(table.columnCount());
			}
		}

		public void addLine(final int position, final List<Object> columnContent) {
			final Line newLine = new Line(columnContent);
			if (position < 0) {
				throw new IllegalArgumentException("Line position is negative");
			}

			// add elements to this line if it is too short
			newLine.padTo(columnCount(), paddingChar);

			if (newLine.size() > columnCount()) { // add elements to all other
													// lines if new line is the
													// new longest line
				padLinesTo(newLine.size()); // pad to new longest line
			}

			while (lines.size() < position) { // add new padded empty lines if
												// new line is outside field
				addLinePaddedTo(columnCount());
			}
			lines.add(position, newLine); // add actual line
		}

		/**
		 * adds a new line to the end of the content, padded to the given
		 * position.
		 *
		 * @param padTo
		 *            padTo
		 */
		private void addLinePaddedTo(final int padTo) {
			final Line emptyLine = new Line();
			emptyLine.padTo(padTo, paddingChar);
			lines.add(emptyLine);
		}

		/**
		 * adds padding char to all lines until they are the given length.
		 *
		 * @param padTo
		 *            padTo
		 */
		private void padLinesTo(final int padTo) {
			for (final Line line : lines) {
				line.padTo(padTo, paddingChar);
			}
		}

		/**
		 * shifts all given lines at given position one entry to the right.
		 *
		 * The padding character will be inserted at the new position.
		 *
		 * @param position
		 *            position
		 * @param list
		 *            list
		 */
		private void shiftAllAt(final int position, final @NonNull List<Line> list) {
			for (final Line line : list) {
				line.shiftAt(position, paddingChar);
			}
		}
	}

	private static class Line extends ArrayList<Object> {

		public Line() {
			super();
		}

		public <T extends Object> Line(final List<T> line) {
			super(line);
		}

		/**
		 * add padding char to this line until its length is the given max.
		 *
		 * @param max
		 *            max
		 */
		void padTo(final int max, final @NonNull String paddingChar) {
			assert paddingChar.equals(" ") : paddingChar;
			while (size() < max) {
				add(paddingChar);
			}
		}

		/**
		 * inserts the padding char at the given position, thus shifting the
		 * rest of the line one position to the right.
		 *
		 * @param position
		 *            position
		 */
		void shiftAt(final int position, final String paddingChar) {
			add(position, paddingChar);
		}
	}
}
