package edu.cmu.cs.bungee.javaExtensions;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Only used by ServletInterface (so don't use MyLogger)
 */
public class MyResultSet implements ResultSet, Serializable {

	protected static final long serialVersionUID = -959016825795947094L;

	public enum ColumnType {
		INTEGER, POSITIVE_INTEGER, SORTED_MONOTONIC_INTEGER, STRING, IMAGE, SORTED_NM_INTEGER, DOUBLE
	}

	// private static final MyLogger myLogger =
	// MyLogger.getMyLogger(MyResultSet.class);
	//
	// private static void logp(final String msg, final Level level, final
	// String sourceMethod) {
	// MyLogger.logp(myLogger, msg, level, "DatabaseEditing", sourceMethod);
	// }

	public static final @NonNull MyResultSet DUMMY_RS = new MyResultSet();

	private static void logp(final String msg, final Level level, final String sourceMethod) {
		System.err.println("MyResultSet." + sourceMethod + " " + level + ": " + msg);
	}

	private int currentRow;
	private final int nRows;
	final Column[] columns;
	final List<ColumnType> columnTypes;

	private MyResultSet() {
		nRows = 0;
		columns = null;
		columnTypes = null;
	}

	public MyResultSet(final @NonNull DataInputStream dataInputStream) {
		// assert dataInputStream != null : "InputStream is NULL!";
		final ReadCompressedIntsIterator readCompressedIntsIterator = new ReadCompressedIntsIterator(dataInputStream,
				3);
		nRows = readCompressedIntsIterator.next();
		// System.out.println(nRows + " rows");
		assert nRows >= 0 : "nRows=" + nRows;
		final int nCols = readCompressedIntsIterator.next() + 1;
		readCompressedIntsIterator.incfNints(nCols - 1);
		columns = new Column[nCols];
		columnTypes = new ArrayList<>(nCols);
		final ColumnType[] colTypeValues = ColumnType.values();
		for (final ReadCompressedIntsIterator it = readCompressedIntsIterator; it.hasNext();) {
			columnTypes.add(colTypeValues[it.next()]);
		}
		assert columnTypes.size() == nCols && readCompressedIntsIterator.finished();
		for (int i = 0; i < nCols; i++) {
			final ColumnType type = columnTypes.get(i);
			try {
				switch (type) {
				case SORTED_MONOTONIC_INTEGER:
				case POSITIVE_INTEGER:
				case INTEGER:
				case SORTED_NM_INTEGER:
					columns[i] = new IntColumn(dataInputStream, nRows, intColumnProperties(type));
					break;
				case STRING:
					columns[i] = new StringColumn(dataInputStream, nRows);
					break;
				case DOUBLE:
					columns[i] = new DoubleColumn(dataInputStream, nRows);
					break;
				case IMAGE:
					columns[i] = new BlobColumn(dataInputStream, nRows);
					break;
				default:
					assert false : "Unknown ColumnType: " + type;
				}
			} catch (final Exception e) {
				System.err.println("While reading " + type + " column " + (i + 1) + " of " + columnTypes + ":\n");
				for (int j = 0; j < i; j++) {
					System.err.println("Column " + (j + 1) + " (" + columns[j].getLabel() + "): "
							+ UtilString.valueOfDeep(columns[j].getValues()));
				}
				e.printStackTrace();
			}
		}
	}

	// private static MyLogger getMyLogger() {
	// if (myLogger == null) {
	// synchronized (myLoggerLock) {
	// myLogger = new MyLogger(
	// UtilString
	// .classForName("edu.cmu.cs.bungee.javaExtensions.MyResultSet"));
	// }
	// }
	// return myLogger;
	// }

	@Override
	public boolean absolute(final int row) {
		// System.out.println("absolute " + row);
		if (row >= 0) {
			currentRow = row;
		} else {
			currentRow = nRows + row + 1;
		}
		return currentRow >= 1 && currentRow <= nRows;
	}

	@Override
	public int getRow() {
		return currentRow;
	}

	@Override
	public void afterLast() {
		currentRow = nRows + 1;
	}

	@Override
	public void beforeFirst() {
		currentRow = 0;
	}

	@Override
	public boolean next() {
		return ++currentRow <= nRows;
	}

	@Override
	public void close() {
		// Only the garbage collector needs to know
	}

	// with this, you don't need to bother with a try/catch
	public static void close(final ResultSet rs) { // NO_UCD (unused code)
		try {
			rs.close();
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	public @NonNull Object[] getValues(final int columnIndex) {
		return columns[columnIndex - 1].getValues();
	}

	private boolean checkCurrentRowCol(final int columnIndex) throws SQLException {
		if (currentRow < 1 || currentRow > nRows) {
			throw new SQLException("Not on valid [1, " + nRows + "] row: " + currentRow + "\n" + valueOfDeep(10));
		}
		if (columnIndex < 1 || columnIndex > columns.length) {
			throw new SQLException("Invalid column index: " + columnIndex + ". The valid column index range is [1, "
					+ columns.length + "]");
		}
		return true;
	}

	@Override
	public String getString(final int columnIndex) throws SQLException {
		assert checkCurrentRowCol(columnIndex);
		return columns[columnIndex - 1].getString(currentRow);
	}

	@Override
	public int getInt(final int columnIndex) throws SQLException {
		assert checkCurrentRowCol(columnIndex);
		return columns[columnIndex - 1].getInt(currentRow);
	}

	@Override
	public double getDouble(final int columnIndex) throws SQLException {
		assert checkCurrentRowCol(columnIndex);
		return columns[columnIndex - 1].getDouble(currentRow);
	}

	public BufferedImage getBufferedImage(final int columnIndex) throws SQLException {
		assert checkCurrentRowCol(columnIndex);
		return columns[columnIndex - 1].getBlob(currentRow);
	}

	@Override
	public String getString(final String columnName) {
		return columns[findColumn(columnName) - 1].getString(currentRow);
	}

	@Override
	public int getInt(final String columnName) {
		final Column column = columns[findColumn(columnName) - 1];
		int result = -1;
		try {
			result = column.getInt(currentRow);
		} catch (final AssertionError e) {
			System.err.println(
					"While MyResultSet.getInt columnName=" + columnName + " column=" + column + " " + getMetaData());
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public int findColumn(final String columnName) {
		// System.out.println("findColumn " + columnName);
		for (int i = 0; i < columns.length; i++) {
			// System.out.println(i + " " + columns[i].getLabel());
			if (columns[i].getLabel().equals(columnName)) {
				return i + 1;
			}
		}
		assert false : "Can't find column '" + columnName + "'. Columns are:\n " + UtilString.valueOfDeep(columns);
		return -1;
	}

	// TODO Remove unused code found by UCDetector
	// public Column getColumn(final int columnIndex) {
	// return columns[columnIndex];
	// }

	@Override
	public boolean first() {
		currentRow = 1;
		return nRows > 0;
	}

	static class ReadCompressedIntsIterator // implements Iterator<Integer>
	{
		final int[] readIntOrTwoResult = new int[2];
		final DataInputStream dataInputStream;
		int nInts;
		int nRead = 0;
		int whichResult = 2;

		ReadCompressedIntsIterator(final DataInputStream _s, final int _nInts) {
			dataInputStream = _s;
			nInts = _nInts;
		}

		boolean finished() {
			assert !hasNext();
			return true;
		}

		void incfNints(final int incf) {
			assert incf >= 0 && nRead < nInts : incf + " " + nRead + " " + nInts;
			nInts += incf;
		}

		boolean hasNext() {
			return nRead < nInts;
		}

		public int next() {
			if (mustRead()) {
				try {
					readIntOrTwo(dataInputStream, readIntOrTwoResult);
					whichResult = 0;
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
			int result = readIntOrTwoResult[whichResult];
			whichResult++;
			if (result < 0) {
				// whichResult was 1 and readIntOrTwoResult[1] is -1
				assert mustRead() : whichResult + " " + UtilString.valueOfDeep(readIntOrTwoResult) + " nRead=" + nRead
						+ " nInts=" + nInts;
				result = next();
			} else {
				nRead++;
				assert hasNext() || mustRead()
						|| readIntOrTwoResult[whichResult] < 0 : "barf if it is on the last row but reads two values";
			}
			return result;
		}

		private boolean mustRead() {
			return whichResult > 1;
		}

	}

	// 00 xxxxxx
	// 01 xxx xxx
	// 10 xxxxxx,xxxxxxxx
	// 110 xxxxx,xxxxxxxx,xxxxxxxx
	// 111 xxxxx,xxxxxxxx,xxxxxxxx,xxxxxxxx
	// (highest bytes first)
	/**
	 * @param result
	 *            Updates result[0] with a decoded int in [0, 2^29 - 1], and
	 *            result[1] with the next decoded int (in which case both are in
	 *            [0, 7]), or -2 if it only read one int.
	 */
	static void readIntOrTwo(final DataInputStream in, final int[] result) throws IOException {
		result[1] = -2;
		final int n = in.readUnsignedByte();
		// System.out.println("riot " + n);
		if (n >= /* 64 */0b0100_0000) {
			if (n >= /* 128 */0b1000_0000) {
				if (n >= /* 192m */0b1100_0000) {
					if (n >= /* 224 */0b1110_0000) {
						// starts 111; read 4 bytes
						result[0] = ((n & /* 31 */0b0001_1111) << 24) + (in.readUnsignedByte() << 16)
								+ (in.readUnsignedByte() << 8) + in.readUnsignedByte();
					} else {
						// starts 110; read 3 bytes
						result[0] = ((n & /* 31 */0b0001_1111) << 16) + (in.readUnsignedByte() << 8)
								+ in.readUnsignedByte();
					}
				} else {
					// starts 10; read 2 bytes
					result[0] = ((n & /* 63 */0b0011_1111) << 8) + in.readUnsignedByte();
				}
			} else {
				// starts 01; read two half-bytes
				result[0] = (n & /* 56 */0b0011_1000) >> 3;
				result[1] = n & /* 7 */0b0000_0111;
			}
		} else {
			// starts 00; read 1 bytes
			result[0] = n;
		}
		// System.out.println("readIntOrTwo return " +
		// UtilString.valueOfDeep(result));
	}

	// 0 xxxxxxx
	// 10 xxxxxx,xxxxxxxx
	// 110 xxxxx,xxxxxxxx,xxxxxxxx
	// 111 xxxxx,xxxxxxxx,xxxxxxxx,xxxxxxxx
	// (highest bytes first)
	/**
	 * @return decoded int in [0, 2^29 - 1], or -1 and prints a stack trace if
	 *         there is an error.
	 */
	public static int readInt(final DataInputStream in) {
		int result = -1;
		try {
			final int n = in.readUnsignedByte();
			if (n >= /* 128 */0b1000_0000) {
				if (n >= /* 192m */0b1100_0000) {
					if (n >= /* 224 */0b1110_0000) {
						// starts 111; read 4 bytes
						result = ((n & /* 31 */0b0001_1111) << 24) + (in.readUnsignedByte() << 16)
								+ (in.readUnsignedByte() << 8) + in.readUnsignedByte();
					} else {
						// starts 110; read 3 bytes
						result = ((n & /* 31 */0b0001_1111) << 16) + (in.readUnsignedByte() << 8)
								+ in.readUnsignedByte();
					}
				} else {
					// starts 10; read 2 bytes
					result = ((n & /* 63 */0b0011_1111) << 8) + in.readUnsignedByte();
				}
			} else {
				// starts 0; read 1 bytes
				result = n;
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * @param in
	 * @return value read from in, or false if there's an error.
	 */
	public static boolean readBoolean(final DataInputStream in) {
		boolean result = false;
		try {
			result = in.readBoolean();
			// System.out.println("readDouble => " + result);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		// String result = null;
		// int nChars = readInt(in) - 1;
		// if (nChars >= 0) {
		// try {
		// StringBuilder buf = new StringBuilder(nChars);
		// for (int i = 0; i < nChars; i++) {
		// buf.append(in.readChar());
		// }
		// result = buf.toString();
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// }
		return result;
	}

	static double readDouble(final DataInputStream in) {
		double result = Double.NaN;
		try {
			result = in.readDouble();
			// System.out.println("readDouble => " + result);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		// String result = null;
		// int nChars = readInt(in) - 1;
		// if (nChars >= 0) {
		// try {
		// StringBuilder buf = new StringBuilder(nChars);
		// for (int i = 0; i < nChars; i++) {
		// buf.append(in.readChar());
		// }
		// result = buf.toString();
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// }
		return result;
	}

	public static @NonNull String readString(final DataInputStream in) throws IOException {
		String result = null;
		// try {
		result = in.readUTF();
		// } catch (final IOException e) {
		// e.printStackTrace();
		// }
		assert result != null;
		return result;
	}

	static BufferedImage readBlob(final DataInputStream s) {
		BufferedImage result = null;
		final int nBytes = readInt(s) - 1;
		// System.out.println("Blob bytes = " + nBytes);
		if (nBytes >= 0) {
			// <0 means no blob
			try {
				final byte[] blob = new byte[nBytes];
				s.readFully(blob, 0, nBytes);
				// System.out.println("\nreadBlob " + nBytes);
				final ByteArrayInputStream s2 = new ByteArrayInputStream(blob);
				// result = ImageUtil.readCompatibleImage(s2);
				result = ImageIO.read(s2);
			} catch (final IOException e1) {
				e1.printStackTrace();
			}
		}
		return result;
	}

	// Blob blob = resultSet.getBlob(index);
	// InputStream stream = blob.getBinaryStream(0, blob.length());
	// BufferedImage image = ImageIO.read(stream);

	public static boolean[] intColumnProperties(final ColumnType columnType) {
		return new boolean[] { isSorted(columnType), isPositive(columnType) };
	}

	private static boolean isPositive(final ColumnType columnType) {
		boolean result = false;
		switch (columnType) {
		case INTEGER:
		case SORTED_NM_INTEGER:
			break;
		case SORTED_MONOTONIC_INTEGER:
		case POSITIVE_INTEGER:
			result = true;
			break;
		default:
			assert false : columnType;
			break;
		}
		return result;
	}

	private static boolean isSorted(final ColumnType columnType) {
		boolean result = false;
		switch (columnType) {
		case INTEGER:
		case POSITIVE_INTEGER:
			break;
		case SORTED_MONOTONIC_INTEGER:
		case SORTED_NM_INTEGER:
			result = true;
			break;
		default:
			assert false : columnType;
			break;
		}
		return result;
	}

	/**
	 * INTEGER contains integers from 0 to about 1,073,741,824
	 *
	 * SORTED_INTEGER contains integers from 1 to about 1,073,741,824 Each value
	 * must be at least 1 greater than previous value. Encode as n - prev - 1.
	 *
	 * POSITIVE_INTEGER contains integers from 1 to about 1,073,741,824
	 *
	 * SORTED_NM_INTEGER contains integers from 0 to about 1,073,741,824 each
	 * value must be at least as large as previous value. Encode as n - prev.
	 */
	static abstract class Column implements Serializable {

		protected final @NonNull String label;

		Column(final DataInputStream s) throws IOException {
			label = readString(s);
		}

		protected @NonNull String getLabel() {
			return label;
		}

		@Override
		public String toString() {
			return UtilString.toString(this, label);
		}

		protected abstract String getString(int row);

		protected abstract BufferedImage getBlob(int currentRow);

		protected abstract int getInt(int row);

		protected abstract double getDouble(int row);

		public abstract @NonNull Object[] getValues();

	}

	private static class StringColumn extends Column {

		private final @NonNull String[] values;

		StringColumn(final DataInputStream s, final int nRows) throws IOException {
			super(s);
			values = new String[nRows];
			for (int row = 0; row < nRows; row++) {
				values[row] = readString(s);
			}
		}

		@Override
		protected String getString(final int row) {
			return values[row - 1];
		}

		@Override
		protected int getInt(final int row) {
			assert false : "Can't getInt() on a StringColumn: " + this;
			return Integer.parseInt(values[row - 1]);
		}

		@Override
		protected BufferedImage getBlob(final int row) {
			Util.ignore(row);
			assert false;
			return null;
		}

		@Override
		protected double getDouble(final int row) {
			assert false;
			return Double.parseDouble(values[row - 1]);
		}

		@Override
		public @NonNull Object[] getValues() {
			final String[] result = values.clone();
			assert result != null;
			return result;
		}
	}

	private static class DoubleColumn extends Column {

		private final double[] values;

		DoubleColumn(final DataInputStream s, final int nRows) throws IOException {
			super(s);
			values = new double[nRows];
			for (int row = 0; row < nRows; row++) {
				values[row] = readDouble(s);
			}
		}

		@Override
		protected String getString(final int row) {
			assert false;
			return Double.toString(values[row - 1]);
		}

		@Override
		protected int getInt(@SuppressWarnings("unused") final int row) {
			assert false;
			// return (int) values[row - 1];
			return Integer.MIN_VALUE;
		}

		@Override
		protected BufferedImage getBlob(@SuppressWarnings("unused") final int row) {
			assert false;
			return null;
		}

		@Override
		protected double getDouble(final int row) {
			return values[row - 1];
		}

		/**
		 * new DoubleColumn has already read the column as doubles into values.
		 * Now convert to Doubles.
		 */
		@Override
		public Object[] getValues() {
			final Double[] result = new Double[values.length];
			for (int i = 0; i < values.length; i++) {
				result[i] = Double.valueOf(values[i]);
			}
			return result;
		}
	}

	private static class IntColumn extends Column {

		private final int[] values;

		IntColumn(final DataInputStream s, final int nRows, final boolean[] properties) throws Exception {
			super(s);
			final boolean sorted = properties[0];
			final boolean positive = properties[1];
			values = new int[nRows];
			int cumCount = 0;
			int row = 0;
			for (final ReadCompressedIntsIterator it = new ReadCompressedIntsIterator(s, nRows); it.hasNext(); row++) {
				final int value = it.next() + (positive ? 1 : 0);
				if (sorted) {
					cumCount += value;
					values[row] = cumCount;
				} else {
					values[row] = value;
				}
			}
		}

		@Override
		protected String getString(@SuppressWarnings("unused") final int row) {
			assert false;
			// return Integer.toString(values[row - 1]);
			return null;
		}

		@Override
		protected int getInt(final int row) {
			return values[row - 1];
		}

		@Override
		protected BufferedImage getBlob(@SuppressWarnings("unused") final int row) {
			assert false;
			return null;
		}

		@Override
		protected double getDouble(@SuppressWarnings("unused") final int row) {
			assert false;
			// return values[row - 1];
			return Double.NaN;
		}

		/**
		 * new IntColumn has already read the column as ints into values. Now
		 * convert to Integers.
		 */
		@Override
		public Object[] getValues() {
			final Integer[] result = new Integer[values.length];
			for (int i = 0; i < values.length; i++) {
				result[i] = Integer.valueOf(values[i]);
			}
			return result;
		}
	}

	private static class BlobColumn extends Column {

		private final BufferedImage[] values;

		BlobColumn(final DataInputStream s, final int nRows) throws IOException {
			super(s);
			values = new BufferedImage[nRows];
			for (int row = 0; row < nRows; row++) {
				values[row] = readBlob(s);
			}
		}

		@Override
		protected String getString(@SuppressWarnings("unused") final int row) {
			assert false;
			return null;
		}

		@Override
		protected int getInt(@SuppressWarnings("unused") final int row) {
			assert false;
			return -1;
		}

		@Override
		protected BufferedImage getBlob(final int row) {
			return values[row - 1];
		}

		@Override
		protected double getDouble(@SuppressWarnings("unused") final int row) {
			assert false;
			return Double.NaN;
		}

		@Override
		public Object[] getValues() {
			assert false;
			return new Object[0];
		}
	}

	@Override
	public boolean wasNull() {

		assert false;
		return false;
	}

	@Override
	public boolean getBoolean(@SuppressWarnings("unused") final int arg0) {
		assert false;
		return false;
	}

	@Override
	public byte getByte(@SuppressWarnings("unused") final int arg0) {
		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public short getShort(final int arg0) {
		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public long getLong(final int arg0) {
		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public float getFloat(final int arg0) {
		assert false;
		return 0;
	}

	@Override
	@SuppressWarnings({ "deprecation", "unused" })
	public BigDecimal getBigDecimal(final int arg0, final int arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public byte[] getBytes(final int arg0) {
		assert false;
		return new byte[0];
	}

	@SuppressWarnings("unused")
	@Override
	public Date getDate(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Time getTime(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Timestamp getTimestamp(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public InputStream getAsciiStream(final int arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings({ "deprecation", "unused" })
	@Deprecated
	@Override
	public InputStream getUnicodeStream(final int arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public InputStream getBinaryStream(final int arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public boolean getBoolean(final String arg0) {

		assert false;
		return false;
	}

	@SuppressWarnings("unused")
	@Override
	public byte getByte(final String arg0) {

		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public short getShort(final String arg0) {

		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public long getLong(final String arg0) {

		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public float getFloat(final String arg0) {

		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public double getDouble(final String arg0) {

		assert false;
		return 0;
	}

	@Deprecated
	@SuppressWarnings({ "unused", "deprecation" })
	@Override
	public BigDecimal getBigDecimal(final String arg0, final int arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public byte[] getBytes(final String arg0) {
		assert false;
		return new byte[0];
	}

	@SuppressWarnings("unused")
	@Override
	public Date getDate(final String arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Time getTime(final String arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Timestamp getTimestamp(final String arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public InputStream getAsciiStream(final String arg0) {

		assert false;
		return null;
	}

	@Deprecated
	@SuppressWarnings({ "unused", "deprecation" })
	@Override
	public InputStream getUnicodeStream(final String arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public InputStream getBinaryStream(final String arg0) {

		assert false;
		return null;
	}

	@Override
	public SQLWarning getWarnings() {

		assert false;
		return null;
	}

	@Override
	public void clearWarnings() {
		// Ignore
	}

	@Override
	public String getCursorName() {

		assert false;
		return null;
	}

	@Override
	public ResultSetMetaData getMetaData() {
		return new MyMetaData();
	}

	@SuppressWarnings("unused")
	@Override
	public Object getObject(final int arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Object getObject(final String arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Reader getCharacterStream(final int arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Reader getCharacterStream(final String arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public BigDecimal getBigDecimal(final int arg0) {

		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public BigDecimal getBigDecimal(final String arg0) {

		assert false;
		return null;
	}

	@Override
	public boolean isBeforeFirst() {

		assert false;
		return false;
	}

	@Override
	public boolean isAfterLast() {

		assert false;
		return false;
	}

	@Override
	public boolean isFirst() {

		assert false;
		return false;
	}

	@Override
	public boolean isLast() {
		assert false;
		return false;
	}

	@Override
	public boolean last() {
		currentRow = nRows;
		return nRows > 0;
	}

	@SuppressWarnings("unused")
	@Override
	public boolean relative(final int arg0) {
		assert false;
		return false;
	}

	@Override
	public boolean previous() {
		assert false;
		return false;
	}

	@SuppressWarnings("unused")
	@Override
	public void setFetchDirection(final int arg0) {
		// Ignore
	}

	@Override
	public int getFetchDirection() {
		assert false;
		return 0;
	}

	@SuppressWarnings("unused")
	@Override
	public void setFetchSize(final int arg0) {
		// Ignore
	}

	@Override
	public int getFetchSize() {
		assert false;
		return 0;
	}

	@Override
	public int getType() {
		return ResultSet.TYPE_SCROLL_INSENSITIVE;
	}

	@Override
	public int getConcurrency() {
		assert false;
		return 0;
	}

	@Override
	public boolean rowUpdated() {
		assert false;
		return false;
	}

	@Override
	public boolean rowInserted() {
		assert false;
		return false;
	}

	@Override
	public boolean rowDeleted() {
		assert false;
		return false;
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNull(final int arg0) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBoolean(final int arg0, final boolean arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateByte(final int arg0, final byte arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateShort(final int arg0, final short arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateInt(final int arg0, final int arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateLong(final int arg0, final long arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateFloat(final int arg0, final float arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateDouble(final int arg0, final double arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBigDecimal(final int arg0, final BigDecimal arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateString(final int arg0, final String arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBytes(final int arg0, final byte[] arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateDate(final int arg0, final Date arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateTime(final int arg0, final Time arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateTimestamp(final int arg0, final Timestamp arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateAsciiStream(final int arg0, final InputStream arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBinaryStream(final int arg0, final InputStream arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateCharacterStream(final int arg0, final Reader arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateObject(final int arg0, final Object arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateObject(final int arg0, final Object arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNull(final String arg0) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBoolean(final String arg0, final boolean arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateByte(final String arg0, final byte arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateShort(final String arg0, final short arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateInt(final String arg0, final int arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateLong(final String arg0, final long arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateFloat(final String arg0, final float arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateDouble(final String arg0, final double arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBigDecimal(final String arg0, final BigDecimal arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateString(final String arg0, final String arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBytes(final String arg0, final byte[] arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateDate(final String arg0, final Date arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateTime(final String arg0, final Time arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateTimestamp(final String arg0, final Timestamp arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateAsciiStream(final String arg0, final InputStream arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBinaryStream(final String arg0, final InputStream arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateCharacterStream(final String arg0, final Reader arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateObject(final String arg0, final Object arg1, final int arg2) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateObject(final String arg0, final Object arg1) {
		// Ignore
	}

	@Override
	public void insertRow() {
		// Ignore
	}

	@Override
	public void updateRow() {
		// Ignore
	}

	@Override
	public void deleteRow() {
		// Ignore
	}

	@Override
	public void refreshRow() {
		// Ignore
	}

	@Override
	public void cancelRowUpdates() {
		// Ignore
	}

	@Override
	public void moveToInsertRow() {
		// Ignore
	}

	@Override
	public void moveToCurrentRow() {
		// Ignore
	}

	@Override
	public Statement getStatement() {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Object getObject(final int arg0, final Map<String, Class<?>> arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Ref getRef(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Blob getBlob(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Clob getClob(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Array getArray(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Object getObject(final String arg0, final Map<String, Class<?>> arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Ref getRef(final String arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Blob getBlob(final String arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Clob getClob(final String arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Array getArray(final String arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Date getDate(final int arg0, final Calendar arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Date getDate(final String arg0, final Calendar arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Time getTime(final int arg0, final Calendar arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Time getTime(final String arg0, final Calendar arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Timestamp getTimestamp(final int arg0, final Calendar arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public Timestamp getTimestamp(final String arg0, final Calendar arg1) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public URL getURL(final int arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public URL getURL(final String arg0) {
		assert false;
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public void updateRef(final int arg0, final Ref arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateRef(final String arg0, final Ref arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBlob(final int arg0, final Blob arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBlob(final String arg0, final Blob arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateClob(final int arg0, final Clob arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateClob(final String arg0, final Clob arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateArray(final int arg0, final Array arg1) {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateArray(final String arg0, final Array arg1) {
		// Ignore
	}

	public int nRows() throws SQLException {
		return nRows(this);
	}

	public static int nRowsNoThrow(final ResultSet rs) {
		int result = -1;
		try {
			result = nRows(rs);
		} catch (final Throwable e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * @return -1 if ResultSet.TYPE_FORWARD_ONLY;
	 *
	 *         nRows otherwise
	 */
	public static int nRows(final ResultSet rs) throws SQLException {
		int result = -1;
		if (rs.getType() != ResultSet.TYPE_FORWARD_ONLY) {
			final int row = rs.getRow();
			rs.last();
			result = rs.getRow();
			resetRow(rs, row);
		}
		return result;
	}

	public static void resetRow(final ResultSet rs, final int row) throws SQLException {
		if (row == 0) {
			// absolute(0) may barf
			rs.beforeFirst();
		} else {
			rs.absolute(row);
		}
	}

	static List<Object> valueOfSingleRowRS(final ResultSet rs, final String SQL) {
		List<Object> result = null;
		try {
			final int nRows = MyResultSet.nRows(rs);
			assert nRows == 1 : nRows;
			final int row = rs.getRow();
			rs.beforeFirst();
			rs.next();
			result = getRowValue(rs, SQL);
			resetRow(rs, row);
		} catch (final SQLException e) {
			System.err.println("For query " + SQL);
			e.printStackTrace();
		}
		return result;
	}

	public static List<Object> getRowValue(final ResultSet rs, final @Nullable String sql) {
		List<Object> result = null;
		try {
			final ResultSetMetaData metaData = rs.getMetaData();
			final int nCols = metaData.getColumnCount();
			result = new ArrayList<>(nCols);
			for (int j = 1; j <= nCols; j++) {
				final int type = metaData.getColumnType(j);
				try {
					switch (type) {
					case java.sql.Types.BIT:
					case java.sql.Types.BIGINT:
					case java.sql.Types.INTEGER:
					case java.sql.Types.SMALLINT:
					case java.sql.Types.TINYINT:
					case java.sql.Types.DECIMAL:
						result.add(rs.getInt(j));
						break;
					case java.sql.Types.VARBINARY:
					case java.sql.Types.CHAR:
					case java.sql.Types.VARCHAR:
						result.add(rs.getString(j));
						break;
					case java.sql.Types.DOUBLE:
					case java.sql.Types.NUMERIC:
						result.add(rs.getDouble(j));
						break;
					case java.sql.Types.BLOB:
						result.add(rs.getBlob(j));
						break;
					default:
						final String typeName = metaData.getColumnTypeName(j);
						switch (typeName) {
						case "MEDIUMBLOB":
							result.add(rs.getBlob(j));
							break;
						case "NUMBER":
							result.add(rs.getBigDecimal(j));
							break;
						case "LONGBLOB":
						case "VARCHAR":
							result.add(rs.getString(j));
							break;
						default:
							logp("For query " + sql + "\nUnknown ColumnType for column " + j + ": " + type
									+ " getColumnTypeName=" + typeName, MyLogger.SEVERE, "valueOfSingleRowRS");
							break;
						}
					}
				} catch (final SQLException e) {
					System.err.println("For column " + j + " (" + metaData.getColumnTypeName(j) + ")");
					throw (e);
				}
			}
		} catch (final SQLException e) {
			System.err.println("For query " + sql);
			e.printStackTrace();
		}
		return result;
	}

	public String valueOfDeep(final int maxRows) {
		return valueOfDeep(this, maxRows);
	}

	public static String valueOfDeep(final ResultSet rs) {
		return valueOfDeep(rs, Integer.MAX_VALUE);
	}

	public static String valueOfDeep(final ResultSet rs, final int maxRows) {
		assert maxRows > 0 : maxRows;
		String result = "";
		if (rs == null) {
			result = "<NULL ResultSet>";
		} else {
			try {
				final FormattedTableBuilder align = new FormattedTableBuilder();
				final ResultSetMetaData metaData = rs.getMetaData();
				final int nRows = MyResultSet.nRows(rs);
				final int nCols = metaData.getColumnCount();
				final StringBuilder buf = addHeader(metaData, nRows, nCols, align);
				if (nRows >= 0) {
					// nRows < 0 most likely means that rs is FORWARD_ONLY
					align.addLine();
					final int originalRow = rs.getRow();
					rs.beforeFirst();
					for (int row = 0; row < nRows && row < maxRows; row++) {
						rs.next();
						// buf.append("\n");
						valueOfDeepInternal(rs, align, metaData, nCols);
					}
					buf.append("\n").append(align.format());
					if (nRows > maxRows) {
						buf.append("\n... ").append(UtilString.addCommas(nRows - maxRows)).append(" more records");
					}
					resetRow(rs, originalRow);
				}
				result = buf.toString();
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	private static void valueOfDeepInternal(final ResultSet rs, final FormattedTableBuilder align,
			final ResultSetMetaData metaData, final int nCols) throws SQLException {
		final List<Object> fields = new ArrayList<>(nCols);
		for (int j = 1; j <= nCols; j++) {
			final int type = metaData.getColumnType(j);
			switch (type) {
			case java.sql.Types.BIT:
			case java.sql.Types.VARBINARY:
			case java.sql.Types.BIGINT:
			case java.sql.Types.INTEGER:
			case java.sql.Types.SMALLINT:
			case java.sql.Types.TINYINT:
				// fields.add(UtilString.addCommas(rs.getInt(j), 6));
				fields.add(rs.getInt(j));
				break;
			case java.sql.Types.CHAR:
			case java.sql.Types.VARCHAR:
				fields.add("\"" + rs.getString(j) + "\"");
				break;
			case java.sql.Types.DOUBLE:
				fields.add(rs.getDouble(j));
				break;
			case java.sql.Types.BLOB:
				fields.add("<image>");
				break;
			default:
				fields.add(valueOfMiscType(rs, metaData, j, type));
			}
		}
		align.addLine(fields);
	}

	private static Object valueOfMiscType(final ResultSet rs, final ResultSetMetaData metaData, final int j,
			final int type) throws SQLException {
		Object value = null;
		final String typeName = metaData.getColumnTypeName(j);
		switch (typeName) {
		case "MEDIUMBLOB":
			value = "<image>";
			break;
		case "NUMBER":
			value = rs.getBigDecimal(j);
			break;
		default:
			logp("Unknown ColumnType for column " + j + ": " + type + " getColumnTypeName=" + typeName, MyLogger.SEVERE,
					"valueOfDeep");
			break;
		}
		return value;
	}

	private static StringBuilder addHeader(final ResultSetMetaData metaData, final int nRows, final int nCols,
			final FormattedTableBuilder align) throws SQLException {
		final StringBuilder buf = new StringBuilder();
		buf.append(UtilString.addCommas(nRows)).append(" rows, ").append(nCols).append(" cols in result set");
		if (nRows > 0) {
			buf.append("\n\n");
			// for (int row = 0; row < 4; row++) {
			final List<String> fields = new ArrayList<>(nCols);
			for (int col = 1; col <= nCols; col++) {
				// switch (row) {
				// case 0:
				// fields.add("Column " + col + "/name/type");
				// break;
				// case 1:

				fields.add(metaData.getColumnLabel(col));

				// break;
				// case 2:
				// fields.add("(" + metaData.getColumnTypeName(col));
				// break;
				// case 3:
				// fields.add(metaData.getColumnClassName(col) + ")");
				// break;
				//
				// default:
				// assert false : col;
				// break;
				// }
			}
			align.addLine(fields);
		}
		return buf;
	}

	class MyMetaData implements ResultSetMetaData {

		@Override
		public String toString() {
			return UtilString.toString(this, UtilString.valueOfDeep(columns));
		}

		@SuppressWarnings("unused")
		@Override
		public String getCatalogName(final int column) throws SQLException {
			return null;
		}

		@SuppressWarnings("unused")
		@Override
		public String getColumnClassName(final int column) throws SQLException {
			return null;
		}

		@SuppressWarnings("unused")
		@Override
		public int getColumnCount() throws SQLException {
			return columns.length;
		}

		@SuppressWarnings("unused")
		@Override
		public int getColumnDisplaySize(final int column) throws SQLException {
			return 0;
		}

		/**
		 * @throws SQLException
		 */
		@Override
		public String getColumnLabel(final int column) throws SQLException {
			return columns[column - 1].getLabel();
		}

		@Override
		public String getColumnName(final int column) throws SQLException {
			return getColumnLabel(column);
		}

		/**
		 * @throws SQLException
		 */
		@Override
		public int getColumnType(final int column) throws SQLException {
			int result = -1;
			switch (columnTypes.get(column - 1)) {
			case INTEGER:
			case SORTED_MONOTONIC_INTEGER:
			case POSITIVE_INTEGER:
			case SORTED_NM_INTEGER:
				result = java.sql.Types.INTEGER;
				break;
			case STRING:
				result = java.sql.Types.VARCHAR;
				break;
			case DOUBLE:
				result = java.sql.Types.DOUBLE;
				break;
			case IMAGE:
				result = java.sql.Types.BLOB;
				break;

			default:
				break;
			}
			return result;
		}

		/**
		 * @throws SQLException
		 */
		@Override
		public String getColumnTypeName(final int column) throws SQLException {
			String result = "Unknown";
			switch (columnTypes.get(column - 1)) {
			case INTEGER:
			case SORTED_MONOTONIC_INTEGER:
			case POSITIVE_INTEGER:
			case SORTED_NM_INTEGER:
				result = "INTEGER";
				break;
			case STRING:
				result = "VARCHAR";
				break;
			case DOUBLE:
				result = "DOUBLE";
				break;
			case IMAGE:
				result = "BLOB";
				break;

			default:
				break;
			}
			return result;
		}

		@SuppressWarnings("unused")
		@Override
		public int getPrecision(final int column) throws SQLException {
			return 0;
		}

		@SuppressWarnings("unused")
		@Override
		public int getScale(final int column) throws SQLException {
			return 0;
		}

		@SuppressWarnings("unused")
		@Override
		public String getSchemaName(final int column) throws SQLException {
			return null;
		}

		@SuppressWarnings("unused")
		@Override
		public String getTableName(final int column) throws SQLException {
			return null;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isAutoIncrement(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isCaseSensitive(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isCurrency(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isDefinitelyWritable(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public int isNullable(final int column) throws SQLException {
			return 0;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isReadOnly(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isSearchable(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isSigned(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isWritable(final int column) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public boolean isWrapperFor(final Class<?> iface) throws SQLException {
			return false;
		}

		@SuppressWarnings("unused")
		@Override
		public <T> T unwrap(final Class<T> iface) throws SQLException {
			return null;
		}

	}

	/**
	 * @throws SQLException
	 */
	@Override
	public int getHoldability() throws SQLException {
		return 0;
	}

	/**
	 * @param arg0
	 * @throws SQLException
	 */
	@Override
	public Reader getNCharacterStream(final int arg0) throws SQLException {
		return null;
	}

	/**
	 * @param arg0
	 * @throws SQLException
	 */
	@Override
	public Reader getNCharacterStream(final String arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public NClob getNClob(final int arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public NClob getNClob(final String arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public String getNString(final int arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public String getNString(final String arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public RowId getRowId(final int arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public RowId getRowId(final String arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public SQLXML getSQLXML(final int arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public SQLXML getSQLXML(final String arg0) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public boolean isClosed() throws SQLException {
		return false;
	}

	@SuppressWarnings("unused")
	@Override
	public void updateAsciiStream(final int arg0, final InputStream arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateAsciiStream(final String arg0, final InputStream arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateAsciiStream(final int arg0, final InputStream arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateAsciiStream(final String arg0, final InputStream arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBinaryStream(final int arg0, final InputStream arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBinaryStream(final String arg0, final InputStream arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBinaryStream(final int arg0, final InputStream arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBinaryStream(final String arg0, final InputStream arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBlob(final int arg0, final InputStream arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBlob(final String arg0, final InputStream arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBlob(final int arg0, final InputStream arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateBlob(final String arg0, final InputStream arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateCharacterStream(final int arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateCharacterStream(final String arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateCharacterStream(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateCharacterStream(final String arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateClob(final int arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateClob(final String arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateClob(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateClob(final String arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNCharacterStream(final int arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNCharacterStream(final String arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNCharacterStream(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNCharacterStream(final String arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNClob(final int arg0, final NClob arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNClob(final String arg0, final NClob arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNClob(final int arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNClob(final String arg0, final Reader arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNClob(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNClob(final String arg0, final Reader arg1, final long arg2) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNString(final int arg0, final String arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateNString(final String arg0, final String arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateRowId(final int arg0, final RowId arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateRowId(final String arg0, final RowId arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateSQLXML(final int arg0, final SQLXML arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public void updateSQLXML(final String arg0, final SQLXML arg1) throws SQLException {
		// Ignore
	}

	@SuppressWarnings("unused")
	@Override
	public boolean isWrapperFor(final Class<?> iface) throws SQLException {
		return false;
	}

	@SuppressWarnings("unused")
	@Override
	public <T> T unwrap(final Class<T> iface) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
		return null;
	}

	@SuppressWarnings("unused")
	@Override
	public <T> T getObject(final String columnLabel, final Class<T> type) throws SQLException {
		return null;
	}

}
