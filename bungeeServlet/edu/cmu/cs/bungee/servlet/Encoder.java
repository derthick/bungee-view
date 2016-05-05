package edu.cmu.cs.bungee.servlet;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.logging.Level;

import javax.servlet.ServletException;

import edu.cmu.cs.bungee.javaExtensions.MyLogger;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet.ColumnType;
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
import edu.cmu.cs.bungee.javaExtensions.UtilImage;

/**
 * A bunch of static methods for encoding messages sent to the client. This is
 * logically a static nested class of Database, but I'm trying to make that file
 * smaller.
 *
 * Also, static logging functions for the Servlet project, which should
 * logically be separate from the individual calling classes.
 */
final class Encoder {
	private final static MyLogger myLogger = MyLogger.getMyLogger(Encoder.class);

	private static void logp(final String msg, final Level level, final Throwable e, final String sourceMethod) {
		MyLogger.logp(myLogger, msg, level, e, "Encoder", sourceMethod);
	}

	private static void logp(final String msg, final Level level, final String sourceMethod) {
		MyLogger.logp(myLogger, msg, level, "Encoder", sourceMethod);
	}

	/**
	 * Using
	 *
	 * if () {errorp();}
	 *
	 * instead of myAssertp() prevents unnecessary evaluation of msg.
	 */
	private static void errorp(final String msg, final String sourceMethod) {
		logp(msg, MyLogger.SEVERE, sourceMethod);
		throw new AssertionError(msg);
	}

	// private static boolean myAssertp(final boolean condition, final String
	// msg, final String sourceMethod) {
	// return MyLogger.myAssertp(myLogger, condition, msg, "Encoder",
	// sourceMethod);
	// }

	// TODO Remove unused code found by UCDetector
	// static void printRecords(final ResultSet rs) {
	// logp(MyResultSet.valueOfDeep(rs, 5), MyLogger.INFO, "printRecords");
	// }

	// private void writeCol(final ResultSet rs, final int colIndex,
	// final ColumnType type, final int desiredImageW,
	// final int desiredImageH, final int quality,
	// final DataOutputStream out) throws ServletException, SQLException,
	// IOException {
	// writeCol(rs, colIndex, type, desiredImageW, desiredImageH, quality,
	// out, getMyLogger());
	// }

	/**
	 * @param colLabel
	 * @param desiredImageW
	 * @param desiredImageH
	 * @param quality
	 *            These 3 only used by writeBlobCol
	 */
	private static void writeCol(final ResultSet rs, final int colIndex, final String colLabel, final ColumnType type,
			final int desiredImageW, final int desiredImageH, final int quality, final DataOutputStream out)
					throws ServletException, SQLException, IOException {
		rs.beforeFirst();
		writeString(colLabel, out);
		switch (type) {
		case SORTED_MONOTONIC_INTEGER:
		case POSITIVE_INTEGER:
		case INTEGER:
		case SORTED_NM_INTEGER:
			writeIntCol(rs, colIndex, out, MyResultSet.intColumnProperties(type));
			break;
		case STRING:
			writeStringCol(rs, colIndex, out);
			break;
		case DOUBLE:
			writeDoubleCol(rs, colIndex, out);
			break;
		case IMAGE:
			writeBlobCol(rs, colIndex, desiredImageW, desiredImageH, quality, out);
			break;
		default:
			throw (new ServletException("Unknown ColumnType: " + type));
		}
	}

	// private void writeIntCol(final ResultSet rs, final int colIndex,
	// final OutputStream out, final boolean[] properties)
	// throws SQLException, IOException {
	// writeIntCol(rs, colIndex, out, properties,getMyLogger());
	// }

	private static void writeIntCol(final ResultSet rs, final int colIndex, final OutputStream out,
			final boolean[] properties) throws SQLException, IOException {
		final boolean sorted = properties[0];
		final boolean positive = properties[1];
		// If possible, pass writeIntOrTwo 2 values; if it uses both, it will
		// return a negative int. In that case, we skip calling it the next
		// iteration, and instead fill up the "buffer" prevValue. Of course we
		// can't pass 2 values if we're on the last row. That case is handled by
		// the call following the loop.
		int previousReadValue = 0;

		final CompressedIntWriter compressedIntWriter = new CompressedIntWriter(out, positive);
		// int nextValueToWrite = -1;
		while (rs.next()) {
			int value = rs.getInt(colIndex);
			if (sorted) {
				final int diff = value - previousReadValue;
				if (diff < 0) {
					errorp("Column " + colIndex + " is not sorted: " + value + " < " + previousReadValue,
							"writeIntCol");
				}
				if (positive && diff <= 0) {
					errorp("Column " + colIndex + " is not monotonically increasing: " + value + " < "
							+ previousReadValue, "writeIntCol");
				}
				previousReadValue = value;
				value = diff;
			}
			if (value < (positive ? 1 : 0)) {
				errorp("Column " + colIndex + ", row " + rs.getRow() + " contains the "
						+ (positive ? "non-positive" : "negative") + " integer " + value, "writeIntCol");
			}

			compressedIntWriter.write(value);

			// if (nextValueToWrite < 0) {
			// nextValueToWrite = value;
			// } else {
			// // nextValueToWrite will become value or -1
			// nextValueToWrite = writeIntOrTwo(nextValueToWrite, value, out,
			// positive);
			// }
		}
		compressedIntWriter.flush();

		// if (nextValueToWrite >= 0) {
		// writeIntOrTwo(nextValueToWrite, -1, out, positive);
		// }
	}

	static class CompressedIntWriter {
		private final OutputStream out;
		private int outputBuffer = -1;
		private final boolean positive;

		CompressedIntWriter(final OutputStream _out, final boolean _positive) {
			out = _out;
			positive = _positive;
		}

		void write(final int i) throws IOException {
			assert i >= 0;
			if (outputBuffer >= 0) {
				outputBuffer = writeIntOrTwo(outputBuffer, i, out, positive);
			} else {
				outputBuffer = i;
			}
		}

		void flush() throws IOException {
			if (outputBuffer >= 0) {
				outputBuffer = writeIntOrTwo(outputBuffer, -1, out, positive);
			}
		}

	}

	/**
	 * Writes n, and if possible also writes nextN. In the former case, it
	 * returns nextN. In the latter, it returns -1.
	 *
	 * @param positive
	 *            whether n and nextN are known to be greater than zero.
	 */
	static int writeIntOrTwo(int n, int nextN, final OutputStream out, final boolean positive) throws IOException {
		// System.out.println("writeIntOrTwo " + n + " " + nextN + " " +
		// positive);
		if (positive) {
			if (n <= 0) {
				errorp("Tried to write non-positive value to a PINT: " + n + " " + nextN, "writeIntOrTwo");
			}
			n--;
			nextN--;
		}
		if (n < 0) {
			errorp(n + " Tried to write a negative int.", "writeIntOrTwo");
		}
		if (n >= 1073741824 || nextN >= 1073741824) {
			errorp(n + " Tried to write a too-large int:", "writeIntOrTwo");
		}
		if (n < 8 && nextN >= 0 && nextN < 8) {
			out.write(n << 3 | nextN | 64);
			nextN = -2;
		} else if (n < 64) {
			out.write(n);
		} else if (n < 16384) {
			out.write((n >> 8) | 128);
			out.write(n);
		} else if (n < 2097152) {
			out.write((n >> 16) | 192);
			out.write(n >> 8);
			out.write(n);
		} else {
			out.write((n >> 24) | 224);
			out.write(n >> 16);
			out.write(n >> 8);
			out.write(n);
		}
		if (positive) {
			// restore nextN to its original value (or to -1 if we wrote both n
			// and nextN)
			nextN++;
		}
		return nextN;
	}

	private static void writeStringCol(final ResultSet rs, final int colIndex, final DataOutputStream out)
			throws SQLException, IOException {
		while (rs.next()) {
			writeString(rs.getString(colIndex), out);
		}
	}

	private static void writeDoubleCol(final ResultSet rs, final int colIndex, final DataOutputStream out)
			throws SQLException, IOException {
		while (rs.next()) {
			writeDouble(rs.getDouble(colIndex), out);
		}
	}

	private static void writeBlobCol(final ResultSet rs, final int colIndex, final int desiredImageW,
			final int desiredImageH, final int quality, final OutputStream out)
					throws ServletException, SQLException, IOException {
		while (rs.next()) {
			// if (imageW > 0
			// && (rs.getInt(colIndex + 1) > imageW || rs
			// .getInt(colIndex + 2) > imageH))
			try {
				writeBlob(rs.getBlob(colIndex), desiredImageW, desiredImageH, quality, rs.getInt(colIndex + 1),
						rs.getInt(colIndex + 2), out);
			} catch (final IllegalArgumentException e) {
				// Java may barf "IllegalArgumentException: Invalid ICC Profile
				// Data"
				// on images that other applications handle fine. See
				// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6404011
				writeInt(0, out);
			} catch (final Exception e) {
				throw new ServletException("Got exception " + e + " while writing blob on row " + rs.getRow() + ", col "
						+ colIndex + " " + currentRowToString(rs));
			}
			// else
			// writeBlob(rs.getBlob(colIndex), -1, -1, out);
		}
	}

	private static String currentRowToString(final ResultSet rs) throws SQLException {
		final StringBuilder buf = new StringBuilder();
		final ResultSetMetaData meta = rs.getMetaData();
		for (int col = 1; col <= meta.getColumnCount(); col++) {
			buf.append(" Col ").append(col).append("=");
			final int type = meta.getColumnType(col);
			switch (type) {
			case Types.INTEGER:
			case Types.SMALLINT:
			case Types.TINYINT:
			case Types.BIT:
			case Types.BOOLEAN:
			case Types.DECIMAL:
				buf.append(rs.getInt(col));
				break;
			case Types.CHAR:
			case Types.VARCHAR:
				buf.append(rs.getString(col));
				break;
			case Types.CLOB:
			case Types.BLOB:
				buf.append("<BLOB>");
				break;
			default:
				buf.append("<Unhandled Type ").append(type).append(">");
				break;
			}
			buf.append(";");
		}
		return buf.toString();
	}

	static void writeString(String s, final DataOutputStream out) throws IOException {
		if (s == null) {
			s = "";
		}
		// Avoid an error, at least, and hope for the best.
		out.writeUTF(s);
	}

	private static void writeDouble(final double n, final DataOutputStream out) throws IOException {
		out.writeDouble(n);
	}

	static void writeBoolean(final boolean b, final DataOutputStream out) throws IOException {
		out.writeBoolean(b);
	}

	static int writeInt(final int n, final OutputStream out) throws IOException {
		if (n < 0) {
			errorp(n + " Tried to write a negative int.", "writeInt");
		}
		if (n >= 1073741824) {
			errorp(n + " Tried to write a too-large int:", "writeInt");
		}
		if (n < 128) {
			out.write(n);
		} else if (n < 16384) {
			out.write((n >> 8) | 128);
			out.write(n);
		} else if (n < 2097152) {
			out.write((n >> 16) | 192);
			out.write(n >> 8);
			out.write(n);
		} else {
			out.write((n >> 24) | 224);
			out.write(n >> 16);
			out.write(n >> 8);
			out.write(n);
		}
		return n;
	}

	private static void writeBlob(final Blob blob, final int desiredW, final int desiredH, final int quality,
			final int actualW, final int actualH, final OutputStream out)
					throws ServletException, IOException, SQLException {
		final boolean noBlob = blob == null || desiredW < 0;
		final int[] newResolution = noBlob ? null : UtilImage.downsizedSize(actualW, actualH, desiredW, desiredH);
		// myAssertp(
		// noBlob
		// || ConvertFromRaw.checkSize(actualW, actualH,
		// blob.getBytes(0L, (int) blob.length())),
		// "Bad image size " + actualW + "x" + actualH, "Encoder",
		// "writeBlob");
		if (noBlob) {
			writeInt(0, out);
		} else if (newResolution != null && 2 * newResolution[0] * newResolution[1] < actualW * actualH) {
			// will call this again with resized blob
			resize(blob, newResolution[0], newResolution[1], quality, out);
		} else {
			assert blob != null;
			final int n = (int) blob.length();
			writeInt(n + 1, out);
			try (InputStream s = blob.getBinaryStream();) {
				final byte[] buffer = new byte[1024];
				int count;
				while ((count = s.read(buffer)) != -1) {
					out.write(buffer, 0, count);
				}

				// int x;
				// while ((x = s.read()) >= 0) {
				// out.write(x);
				// }
			}
		}
	}

	// private static int[] scaledResolution(final int desiredW,
	// final int desiredH, final int actualW, final int actualH) {
	// final double ratio = Math.max(desiredW / ((double) actualW), desiredH
	// / ((double) actualH));
	// final int newW = UtilMath.roundToInt(actualW * ratio);
	// final int newH = UtilMath.roundToInt(actualH * ratio);
	// myAssertp(
	// newW == desiredW || newH == desiredH,
	// "Bad resize: desired=" + desiredW + "x" + desiredH
	// + " actual=" + actualW + "x" + actualH
	// + " new=" + newW + "x" + newH, "writeIntOrTwo");
	// final int[] result = { newW, newH };
	// return result;
	// }

	private static void resize(final Blob blob, final int newW, final int newH, final int quality,
			final OutputStream out) throws SQLException, IOException, ServletException {
		try (ByteArrayOutputStream byteArrayStream = UtilImage.getJPGstream(UtilImage.blobToBufferedImage(blob), newW,
				newH, quality);) {
			final int len = byteArrayStream.size();
			if (len * 3 < blob.length() * 2) {
				writeInt(len + 1, out);
				byteArrayStream.writeTo(out);
			} else {
				writeBlob(blob, newW, newH, quality, newW, newH, out);
			}
		}
	}

	// void sendResultSet(final ResultSet rs, final List<ColumnType> types,
	// final DataOutputStream out) throws ServletException, SQLException,
	// IOException {
	// sendResultSet(rs, types, out, myLogger);
	// }

	static void sendResultSet(final ResultSet rs, final List<ColumnType> columnTypes, final DataOutputStream out)
			throws ServletException, SQLException, IOException {
		myLogger.myAssertp(columnTypes != null, "types is null", "sendResultSet");
		sendResultSet(rs, columnTypes, -1, -1, -1, out);
	}

	static void sendResultSet(final ResultSet rs, final List<ColumnType> columnTypes, final int desiredImageW,
			final int desiredImageH, final int quality, final DataOutputStream out)
					throws ServletException, SQLException, IOException {
		assert columnTypes.size() > 0 : columnTypes;
		if (rs == null) {
			writeInt(0, out);
			logp("sendResultSet given null result set.", MyLogger.WARNING, "sendResultSet");
		} else {
			try {
				final int nCols = columnTypes.size();
				final ResultSetMetaData metaData = rs.getMetaData();
				assert nCols == metaData.getColumnCount() : "types and rs nCols differ";
				rs.last();
				final int nRows = rs.getRow();
				final CompressedIntWriter compressedIntWriter = new CompressedIntWriter(out, false);
				compressedIntWriter.write(nRows);
				compressedIntWriter.write(nCols - 1);
				for (final ColumnType columnType : columnTypes) {
					compressedIntWriter.write(columnType.ordinal());
				}
				compressedIntWriter.flush();
				for (int col = 0; col < nCols; col++) {
					if (columnTypes.get(col) == ColumnType.IMAGE
							&& (columnTypes.get(col + 1) != ColumnType.INTEGER || columnTypes.get(col + 2) != ColumnType.INTEGER)) {
						errorp("Images must be followed by width and height", "sendResultSet");
					}
					writeCol(rs, col + 1, metaData.getColumnLabel(col + 1), columnTypes.get(col), desiredImageW,
							desiredImageH, quality, out);
				}
			} catch (final Throwable e) {
				logp("While sending ResultSet with column types " + columnTypes + ":\n" + MyResultSet.valueOfDeep(rs, 100),
						MyLogger.SEVERE, e, "sendResultSet");
				throw (e);
				// } finally {
				// JDBCSample.close(rs);
			}
		}
	}

}
