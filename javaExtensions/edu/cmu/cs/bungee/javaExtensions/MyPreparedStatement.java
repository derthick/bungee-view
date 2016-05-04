package edu.cmu.cs.bungee.javaExtensions;

import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;

/**
 * A PreparedStatement that remembers its source SQL and parameter values.
 */
class MyPreparedStatement implements PreparedStatement, Serializable {
	private static final long serialVersionUID = 1L;

	private static final Pattern SUBSTITUTED_SQL_PATTERN = Pattern.compile("\\?");

	/**
	 * paramValues[0] is not used, so indexes match up with
	 * PreparedStatement.get/set arguments.
	 */
	private final @NonNull String[] paramValues;

	private final @NonNull JDBCSample jdbc;

	private final @NonNull String sql;

	private final @NonNull PreparedStatement ps;

	/**
	 * Only called by JDBCSample.lookupPS
	 *
	 * @param resultSetConcurrency
	 *            must not be ResultSet.TYPE_FORWARD_ONLY only for the benefit
	 *            of assertions based on MyResultSet.nRows().
	 */
	MyPreparedStatement(final @NonNull String _sql, final @NonNull JDBCSample _jdbc, final int resultSetType,
			final int resultSetConcurrency) throws SQLException {
		jdbc = _jdbc;
		sql = _sql;
		// if (sql == null) {
		// jdbc.errorp("_sql==null", "MyPreparedStatement.<init>");
		// }
		if (resultSetConcurrency == ResultSet.TYPE_FORWARD_ONLY) {
			jdbc.errorp("resultSetConcurrency == ResultSet.TYPE_FORWARD_ONLY", "MyPreparedStatement.<init>");
		}
		try {
			final int nParams = UtilString.nOccurrences(sql, '?');
			paramValues = new String[nParams + 1];
			final PreparedStatement _ps = jdbc.con.prepareStatement(sql, resultSetType, resultSetConcurrency);
			assert _ps != null;
			ps = _ps;
		} catch (final Throwable e) {
			jdbc.errorp("While preparing: " + sql, e, "MyPreparedStatement.<init>");
			throw (e);
		}
	}

	@NonNull
	String substitutedSQL() {
		String result = sql;
		final String[] params = UtilArray.subArray(paramValues, 1);
		for (final String arg : params) {
			String quotedArg = null;
			try {
				quotedArg = arg == null ? "NULL" : JDBCSample.quoteForSQL(Matcher.quoteReplacement(arg));
				result = SUBSTITUTED_SQL_PATTERN.matcher(result).replaceFirst(quotedArg);
			} catch (final Throwable e) {
				jdbc.errorp("While " + sql + " (with paramValues=" + UtilString.valueOfDeep(params) + ") .replaceFirst "
						+ arg + " (" + quotedArg + ")", e, "MyPreparedStatement.substitutedSQL");
			}
		}
		return Util.nonNull(result);
	}

	@Override
	public String toString() {
		String substitutedSQL = sql;
		try {
			substitutedSQL = substitutedSQL();
		} catch (final Throwable e) {
			jdbc.getMyLogger().logp("Ignoring " + e, MyLogger.WARNING, "MyPreparedStatement.toString");
		}
		return UtilString.toString(this, substitutedSQL);
	}

	@Override
	public void addBatch() throws SQLException {
		ps.addBatch();
	}

	@Override
	public void clearParameters() throws SQLException {
		ps.clearParameters();
	}

	@Override
	public boolean execute() throws SQLException {
		return ps.execute();
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		return ps.executeQuery();
	}

	@Override
	public int executeUpdate() throws SQLException {
		return ps.executeUpdate();
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return ps.getMetaData();
	}

	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		return ps.getParameterMetaData();
	}

	@Override
	public void setArray(final int i, final Array x) throws SQLException {
		paramValues[i] = x.toString();
		ps.setArray(i, x);
	}

	@Override
	public void setAsciiStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setAsciiStream(parameterIndex, x, length);
	}

	@Override
	public void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setBigDecimal(parameterIndex, x);
	}

	@Override
	public void setBinaryStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setBinaryStream(parameterIndex, x, length);
	}

	@Override
	public void setBlob(final int i, final Blob x) throws SQLException {
		paramValues[i] = x.toString();
		ps.setBlob(i, x);
	}

	@Override
	public void setBoolean(final int parameterIndex, final boolean x) throws SQLException {
		paramValues[parameterIndex] = Boolean.toString(x);
		ps.setBoolean(parameterIndex, x);
	}

	@Override
	public void setByte(final int parameterIndex, final byte x) throws SQLException {
		paramValues[parameterIndex] = Byte.toString(x);
		ps.setByte(parameterIndex, x);
	}

	@Override
	public void setBytes(final int parameterIndex, final byte[] x) throws SQLException {
		paramValues[parameterIndex] = Arrays.toString(x);
		ps.setBytes(parameterIndex, x);
	}

	@Override
	public void setCharacterStream(final int parameterIndex, final Reader reader, final int length)
			throws SQLException {
		paramValues[parameterIndex] = reader.toString();
		ps.setCharacterStream(parameterIndex, reader, length);
	}

	@Override
	public void setClob(final int i, final Clob x) throws SQLException {
		paramValues[i] = x.toString();
		ps.setClob(i, x);
	}

	@Override
	public void setDate(final int parameterIndex, final java.sql.Date x) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setDate(parameterIndex, x);
	}

	@Override
	public void setDate(final int parameterIndex, final java.sql.Date x, final Calendar cal) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setDate(parameterIndex, x, cal);
	}

	@Override
	public void setDouble(final int parameterIndex, final double x) throws SQLException {
		paramValues[parameterIndex] = Double.toString(x);
		ps.setDouble(parameterIndex, x);
	}

	@Override
	public void setFloat(final int parameterIndex, final float x) throws SQLException {
		paramValues[parameterIndex] = Float.toString(x);
		ps.setFloat(parameterIndex, x);
	}

	@Override
	public void setInt(final int parameterIndex, final int i) throws SQLException {
		paramValues[parameterIndex] = Integer.toString(i);
		ps.setInt(parameterIndex, i);
	}

	@Override
	public void setLong(final int parameterIndex, final long x) throws SQLException {
		paramValues[parameterIndex] = Long.toString(x);
		ps.setLong(parameterIndex, x);
	}

	@Override
	public void setNull(final int parameterIndex, final int sqlType) throws SQLException {
		ps.setNull(parameterIndex, sqlType);
	}

	@Override
	public void setNull(final int paramIndex, final int sqlType, final String typeName) throws SQLException {
		ps.setNull(paramIndex, sqlType, typeName);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setObject(parameterIndex, x);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setObject(parameterIndex, x, targetSqlType);
	}

	@Override
	public void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scale)
			throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setObject(parameterIndex, x, targetSqlType, scale);
	}

	@Override
	public void setRef(final int i, final Ref x) throws SQLException {
		paramValues[i] = x.toString();
		ps.setRef(i, x);
	}

	@Override
	public void setShort(final int parameterIndex, final short x) throws SQLException {
		paramValues[parameterIndex] = Short.toString(x);
		ps.setShort(parameterIndex, x);
	}

	@Override
	public void setString(final int parameterIndex, final String x) throws SQLException {
		paramValues[parameterIndex] = x;
		ps.setString(parameterIndex, x);
	}

	@Override
	public void setTime(final int parameterIndex, final Time x) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setTime(parameterIndex, x);
	}

	@Override
	public void setTime(final int parameterIndex, final Time x, final Calendar cal) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setTime(parameterIndex, x, cal);
	}

	@Override
	public void setTimestamp(final int parameterIndex, final Timestamp x) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setTimestamp(parameterIndex, x);
	}

	@Override
	public void setTimestamp(final int parameterIndex, final Timestamp x, final Calendar cal) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setTimestamp(parameterIndex, x, cal);
	}

	@Override
	public void setURL(final int parameterIndex, final URL x) throws SQLException {
		paramValues[parameterIndex] = x.toString();
		ps.setURL(parameterIndex, x);
	}

	@SuppressWarnings("deprecation")
	@Deprecated
	@Override
	public void setUnicodeStream(final int parameterIndex, final InputStream x,
			@SuppressWarnings("unused") final int length) {
		paramValues[parameterIndex] = x.toString();
		// ps.setUnicodeStream(parameterIndex, x,
		// length);
	}

	@Override
	public void addBatch(final String arg0) throws SQLException {
		ps.addBatch(arg0);
	}

	@Override
	public void cancel() throws SQLException {
		ps.cancel();
	}

	@Override
	public void clearBatch() throws SQLException {
		ps.clearBatch();
	}

	@Override
	public void clearWarnings() throws SQLException {
		ps.clearWarnings();
	}

	/**
	 * @throws SQLException
	 */
	@Override
	public void close() throws SQLException {
		assert false;
		// ps.close();
	}

	@Override
	public boolean execute(final String arg0) throws SQLException {
		return ps.execute(arg0);
	}

	@Override
	public boolean execute(final String arg0, final int arg1) throws SQLException {
		return ps.execute(arg0, arg1);
	}

	@Override
	public boolean execute(final String arg0, final int[] arg1) throws SQLException {
		return ps.execute(arg0, arg1);
	}

	@Override
	public boolean execute(final String arg0, final String[] arg1) throws SQLException {
		return ps.execute(arg0, arg1);
	}

	@Override
	public int[] executeBatch() {
		assert false;
		return new int[0];
	}

	@Override
	public ResultSet executeQuery(final String arg0) throws SQLException {
		return ps.executeQuery(arg0);
	}

	@Override
	public int executeUpdate(final String arg0) throws SQLException {
		return ps.executeUpdate(arg0);
	}

	@Override
	public int executeUpdate(final String arg0, final int arg1) throws SQLException {
		return ps.executeUpdate(arg0, arg1);
	}

	@Override
	public int executeUpdate(final String arg0, final int[] arg1) throws SQLException {
		return ps.executeUpdate(arg0, arg1);
	}

	@Override
	public int executeUpdate(final String arg0, final String[] arg1) throws SQLException {
		return ps.executeUpdate(arg0, arg1);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return ps.getConnection();
	}

	@Override
	public int getFetchDirection() throws SQLException {
		return ps.getFetchDirection();
	}

	@Override
	public int getFetchSize() throws SQLException {
		return ps.getFetchSize();
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		return ps.getGeneratedKeys();
	}

	@Override
	public int getMaxFieldSize() throws SQLException {
		return ps.getMaxFieldSize();
	}

	@Override
	public int getMaxRows() throws SQLException {
		return ps.getMaxRows();
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		return ps.getMoreResults();
	}

	@Override
	public boolean getMoreResults(final int arg0) throws SQLException {
		return ps.getMoreResults(arg0);
	}

	@Override
	public int getQueryTimeout() throws SQLException {
		return ps.getQueryTimeout();
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return ps.getResultSet();
	}

	@Override
	public int getResultSetConcurrency() throws SQLException {
		return ps.getResultSetConcurrency();
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		return ps.getResultSetHoldability();
	}

	@Override
	public int getResultSetType() throws SQLException {
		return ps.getResultSetType();
	}

	@Override
	public int getUpdateCount() throws SQLException {
		return ps.getUpdateCount();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return ps.getWarnings();
	}

	@Override
	public void setCursorName(final String arg0) throws SQLException {
		ps.setCursorName(arg0);
	}

	@Override
	public void setEscapeProcessing(final boolean arg0) throws SQLException {
		ps.setEscapeProcessing(arg0);
	}

	@Override
	public void setFetchDirection(final int arg0) throws SQLException {
		ps.setFetchDirection(arg0);
	}

	@Override
	public void setFetchSize(final int arg0) throws SQLException {
		ps.setFetchSize(arg0);
	}

	@Override
	public void setMaxFieldSize(final int arg0) throws SQLException {
		ps.setMaxFieldSize(arg0);
	}

	@Override
	public void setMaxRows(final int arg0) throws SQLException {
		ps.setMaxRows(arg0);
	}

	@Override
	public void setQueryTimeout(final int arg0) throws SQLException {
		ps.setQueryTimeout(arg0);
	}

	@SuppressWarnings("unused")
	@Override
	public void setAsciiStream(final int arg0, final InputStream arg1) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setAsciiStream(final int arg0, final InputStream arg1, final long arg2) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setBinaryStream(final int arg0, final InputStream arg1) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setBinaryStream(final int arg0, final InputStream arg1, final long arg2) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setBlob(final int arg0, final InputStream arg1) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setBlob(final int arg0, final InputStream arg1, final long arg2) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setCharacterStream(final int arg0, final Reader arg1) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setCharacterStream(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setClob(final int arg0, final Reader arg1) throws SQLException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	@Override
	public void setClob(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// TODO Auto-generated method stub
	}

	@Override
	@SuppressWarnings("unused")
	public void setNCharacterStream(final int arg0, final Reader arg1) throws SQLException {
		// TODO Auto-generated method stub
	}

	@Override
	@SuppressWarnings("unused")
	public void setNCharacterStream(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// TODO Auto-generated method stub
	}

	@Override
	@SuppressWarnings("unused")
	public void setNString(final int arg0, final String arg1) throws SQLException {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isClosed() throws SQLException {
		final boolean result = ps.isClosed();
		assert result == false : this;
		return result;
	}

	@Override
	@SuppressWarnings("unused")
	public boolean isPoolable() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	@SuppressWarnings("unused")
	public void setPoolable(final boolean arg0) throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	@SuppressWarnings("unused")
	public boolean isWrapperFor(final Class<?> arg0) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	@SuppressWarnings("unused")
	public void closeOnCompletion() throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		final boolean result = ps.isCloseOnCompletion();
		assert result == false : this;
		return result;
	}

	@Override
	@SuppressWarnings("unused")
	public void setNClob(final int arg0, final NClob arg1) throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	@SuppressWarnings("unused")
	public void setNClob(final int arg0, final Reader arg1) throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	@SuppressWarnings("unused")
	public void setNClob(final int arg0, final Reader arg1, final long arg2) throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	@SuppressWarnings("unused")
	public void setRowId(final int arg0, final RowId arg1) throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	@SuppressWarnings("unused")
	public void setSQLXML(final int arg0, final SQLXML arg1) throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	@SuppressWarnings("unused")
	public <T> T unwrap(final Class<T> iface) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

}
