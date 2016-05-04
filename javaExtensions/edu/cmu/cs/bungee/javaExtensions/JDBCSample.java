package edu.cmu.cs.bungee.javaExtensions;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.mysql.jdbc.AbandonedConnectionCleanupThread;

public class JDBCSample implements Serializable, AutoCloseable {
	private static final long serialVersionUID = 1L;

	public static final @NonNull String COLLATE = "utf8mb4_unicode_ci";

	/**
	 * Log queries that take longer than this. (Log all queries and extra info
	 * if slowQueryTime = 0.) Never log if slowQueryTime < 0.
	 *
	 * Use log level = warn, because we'll only call it if slowQueryTime>=0.
	 */
	public int slowQueryTime = -1;

	// Need to figure out how to unregister it in shutdown()
	private /* transient */ final Driver driver;

	private transient final @NonNull Map<String, MyPreparedStatement> myPreparedStatements = new HashMap<>();

	private final @NonNull String connectString; // Only used by toString()

	public transient final @NonNull Connection con;

	/**
	 * Only used by showSlow.
	 */
	private transient int nQueriesInProgress = 0;

	public final @NonNull String dbName;

	MyLogger myLogger = null;
	@NonNull
	Level myLoggerLevel = MyLogger.DEFAULT_LOGGER_LEVEL;

	@NonNull
	MyLogger getMyLogger() {
		if (myLogger == null) {
			myLogger = MyLogger.getMyLogger(JDBCSample.class);
			myLogger.setLevel(myLoggerLevel);
		}
		assert myLogger != null;
		return myLogger;
	}

	public void setLogLevel(final @NonNull Level level) {
		if (myLogger == null) {
			myLoggerLevel = level;
		} else {
			myLogger.setLevel(level);
		}
	}

	// private void logp(final Object msg, final Throwable e, final String
	// sourceMethod) {
	// MyLogger.logp(myLogger, msg, MyLogger.SEVERE, e, "Database",
	// sourceMethod);
	// }

	/**
	 * Using
	 *
	 * if () {errorp();}
	 *
	 * instead of myAssertp() prevents unnecessary evaluation of msg.
	 */
	public void errorp(final @NonNull Object msg, final @NonNull String sourceMethod) {
		logp(msg, Util.nonNull(MyLogger.SEVERE), sourceMethod);
		throw new AssertionError("In dbName=" + dbName + "." + sourceMethod + ": " + msg);
	}

	void errorp(final @NonNull Object msg, final @NonNull Throwable e, final @NonNull String sourceMethod) {
		logp(msg, Util.nonNull(MyLogger.SEVERE), e, sourceMethod);
		throw new AssertionError("In dbName=" + dbName + "." + sourceMethod + ": " + msg + ": " + e);
	}

	public void logp(final @NonNull Object msg, final @NonNull Level level, final @NonNull Throwable e,
			final @NonNull String sourceMethod) {
		MyLogger.logp(getMyLogger(), addDBtoMsg(msg), level, e, "JDBCSample", sourceMethod);
	}

	public void logp(final @NonNull Object msg, final @NonNull Level level, final @NonNull String sourceMethod) {
		MyLogger.logp(getMyLogger(), addDBtoMsg(msg), level, "JDBCSample", sourceMethod);
	}

	private String addDBtoMsg(final @NonNull Object msg) {
		return "In dbName=" + dbName + ": " + msg;
	}

	// private boolean myAssertp(final boolean condition, final Object msg,
	// final String sourceMethod) {
	// return MyLogger.myAssertp(myLogger, condition, msg, "JDBCSample",
	// sourceMethod);
	// }

	public static @NonNull JDBCSample getMySqlJDBCSample(final @NonNull String server, final @NonNull String dbName,
			final @NonNull String user, final @NonNull String pass) throws SQLException {
		final StringBuilder connectStringBuilder = new StringBuilder();
		connectStringBuilder.append(server).append(dbName);

		connectStringBuilder.append("?useCompression=");
		// You get an error trying to use compression for localhost.
		// Plus it's not important in that case.
		connectStringBuilder.append(server.indexOf("localhost") < 0);

		final int thirtyMinutesInMS = 30 * 60 * 1000;
		connectStringBuilder
				// .append("&autoReconnect=true")
				.append("&connectTimeout=").append(thirtyMinutesInMS).append("&dontTrackOpenResources=true")
				.append("&useUnicode=true").append("&characterEncoding=UTF-8").append("&characterSetResults=UTF-8")

				// .append("&useUsageAdvisor=true")

				.append("&connectionCollation=" + COLLATE);
		final JDBCSample jdbc = new JDBCSample("com.mysql.jdbc.Driver", Util.nonNull(connectStringBuilder.toString()),
				dbName, user, pass);

		return jdbc;
	}

	// "jdbc:oracle:thin:@//localhost:1521/orcl", "scott", "tiger"
	// // @//machineName:port/SID, userid, password
	public static @NonNull JDBCSample getOracleJDBCSample(final @NonNull String _connectString,
			final @NonNull String user, final @NonNull String pass) throws SQLException {
		return new JDBCSample("oracle.jdbc.OracleDriver", _connectString, user, user, pass);
	}

	private JDBCSample(final @NonNull String driverClassName, final @NonNull String _connectString,
			final @NonNull String _dbName, final @NonNull String user, final @NonNull String pass) throws SQLException {
		driver = getDriver(driverClassName);
		dbName = Util.nonNull(_dbName);
		final Date date = new Date();
		logp("Connecting to " + _connectString + " at " + date, Util.nonNull(MyLogger.FINE), "<init>");
		try {
			con = Util.nonNull(DriverManager.getConnection(_connectString, user, pass));
		} catch (final SQLException e) {
			logp("While DriverManager.getConnection(" + _connectString + ", " + user + ", " + pass + ")",
					MyLogger.SEVERE, e, "<init>");
			close();
			throw (e);
		}
		connectString = _connectString + "&date=" + date;
		assert con != null;
	}

	public void ensureDatabase(final @NonNull String _dbName, final @NonNull String[] tablesToCreate,
			final @NonNull String[] tablesToCopy) throws SQLException {
		if (!databaseExists(_dbName)) {
			sqlUpdate("DROP DATABASE IF EXISTS " + _dbName);
			sqlUpdate("CREATE DATABASE " + _dbName);
			sqlUpdate("GRANT SELECT, INSERT, UPDATE, CREATE, DELETE, ALTER ROUTINE, EXECUTE,"
					+ " CREATE TEMPORARY TABLES, CREATE VIEW, DROP, CREATE ROUTINE ON " + _dbName
					+ ".* TO bungee@localhost");
			assert databaseExists(_dbName) : _dbName + " " + this;
		}
		createTablesIfNotExists(_dbName, tablesToCreate, tablesToCopy);
	}

	void createTablesIfNotExists(final String newDBname, final String[] tablesToCreate, final String[] tablesToCopy)
			throws SQLException {
		for (final String table : tablesToCopy) {
			createTable(table, newDBname, true);
		}
		for (final String table : tablesToCreate) {
			createTable(table, newDBname, false);
		}
	}

	private void createTable(final String table, final String newDBname, final boolean isCopy) throws SQLException {
		final String qualifiedTableName = newDBname + "." + table;
		if (!tableExists(newDBname, table)) {
			logp(qualifiedTableName, MyLogger.FINE, "createTable");
			sqlUpdate("CREATE TABLE " + qualifiedTableName + " LIKE " + table);
			if (isCopy) {
				sqlUpdate("INSERT INTO " + qualifiedTableName + " SELECT * FROM " + table);
			}
		}
	}

	public void dropAndCreateTable(final String table, final String createSQL) throws SQLException {
		dropTable(table);
		sqlUpdate("CREATE TABLE " + table + " " + createSQL);
	}

	public void dropTable(final String table) throws SQLException {
		sqlUpdate("DROP TABLE IF EXISTS " + table);
	}

	public int nRows(final String table) throws SQLException {
		return sqlQueryInt("SELECT COUNT(*) FROM " + table);
	}

	public void renameTable(final String table, final String newName) throws SQLException {
		sqlUpdate("ALTER TABLE " + table + " RENAME TO " + newName);
	}

	private Driver getDriver(final String driverClassName) {
		Driver d = null;
		try {
			switch (driverClassName) {
			// Use constants in calls to Class.forName to keep ProGuard happy.
			case "com.mysql.jdbc.Driver":
				d = (Driver) Class.forName("com.mysql.jdbc.Driver").newInstance();
				break;
			case "oracle.jdbc.OracleDriver":
				d = (Driver) Class.forName("oracle.jdbc.OracleDriver").newInstance();
				break;
			default:
				assert false : driverClassName;
				break;
			}
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			logp("While getting Driver for " + driverClassName, MyLogger.SEVERE, e, "getDriver");
		}
		return d;
	}

	@Override
	public void close() throws SQLException {
		logp("JDBCSample.close " + this, MyLogger.INFO, "close");
		con.close();
		if (driver == null) {
			logp("driver == null!", MyLogger.SEVERE, "close");
		} else {
			// Catalina still complains that driver is registered.
			DriverManager.deregisterDriver(driver);
		}
		try {
			// MySQL driver leaves around a thread. This cleans it up.
			AbandonedConnectionCleanupThread.shutdown();
		} catch (final InterruptedException e) {
			logp("While AbandonedConnectionCleanupThread.shutdown ", MyLogger.SEVERE, e, "close");
		}
	}

	public static @NonNull String quoteForSQL(@Nullable final String s) {
		String result = "NULL";
		if (s != null) {
			result = s.replaceAll("\\\\", "\\\\\\\\").replaceAll("'", "\\\\'");
			if (!NumberUtils.isDigits(result)) {
				// LIMIT requires integer constants, so EXPLAIN must substitute
				// for ? without 's.
				result = "'" + result + "'";
			}
		}
		assert result != null;
		return result;
	}

	// private Date initSlow(final String[] queryDesc) {
	// final String concat = slowQueryTime == 0 ? UtilString.join(queryDesc, ";
	// ") : null;
	// return initSlow(concat);
	// }

	@Nullable
	Date initSlow(final Object psOrString) {
		Date start = null;
		if (slowQueryTime >= 0) {
			if (java.awt.EventQueue.isDispatchThread()) {
				logp("Calling JDBCSample in EventDispatchThread: " + psOrString, MyLogger.WARNING, "initSlow");
			}
			if (slowQueryTime == 0) {
				final StringBuilder buf = new StringBuilder(2000);
				if (nQueriesInProgress > 0) {
					buf.append("\n** ").append(nQueriesInProgress).append(" in progress **  ");
				}
				buf.append("\n           starting query: ").append(psOrString);
				logp(Util.nonNull(buf.toString()), MyLogger.WARNING, "initSlow");
			}
			start = new Date();
			nQueriesInProgress++;
		}
		return start;
	}

	// private void showSlow(final Date start, final int[] nRows, final String[]
	// queryDesc) {
	// showSlowInternal(start, UtilString.join(nRows),
	// UtilString.join(queryDesc, "; "));
	// }

	// void showSlow(final Date start, final int nRows, final PreparedStatement
	// queryDesc) {
	// final String nRowsString = nRows < 0 ? "?" : UtilString.addCommas(nRows);
	// showSlowInternal(start, nRowsString, queryDesc);
	// }

	void showSlowInternal(final QueryDesc queryDesc) {
		if (slowQueryTime >= 0) {
			nQueriesInProgress--;
			if (queryDesc.duration >= slowQueryTime) {
				if (getMyLogger().isLoggable(MyLogger.WARNING)) {
					final StringBuilder buf = new StringBuilder(2000);
					if (nQueriesInProgress > 0) {
						buf.append("\n** ").append(nQueriesInProgress).append(" in progress **  ");
					}
					buf.append("For database ").append(dbName).append("\n   ").append(queryDesc);
					logp(buf, MyLogger.WARNING, "showSlowInternal");
				}
				updateSlowQueryTypes(queryDesc.duration, queryDesc);
			}
		}
	}

	private final Map<QueryDesc, Long> slowQueryTypes = new HashMap<>();

	/**
	 * If queryDuration exceeds previous high for this querySpec, EXPLAIN
	 * querySpec and then print accumulated slow queries
	 */
	private synchronized void updateSlowQueryTypes(final long queryDuration, final QueryDesc querySpec) {
		final Long old = slowQueryTypes.get(querySpec);
		if (old == null || queryDuration > old.longValue()) {
			slowQueryTypes.put(querySpec, Long.valueOf(-queryDuration));
			explain(querySpec, queryDuration);
			logAccumulatedSlowQueries();
		}
	}

	private void logAccumulatedSlowQueries() {
		final StringBuilder buf = new StringBuilder();
		final SortedSet<Long> durations = new TreeSet<>(slowQueryTypes.values());
		for (final Long duration : durations) {
			for (final Entry<QueryDesc, Long> entry : slowQueryTypes.entrySet()) {
				if (entry.getValue().equals(duration)) {
					if (buf.length() > 0) {
						buf.append("\n");
					}
					@SuppressWarnings("resource")
					final QueryDesc queryDesc = entry.getKey();
					buf.append(UtilString.addCommas(-entry.getValue())).append("\t").append(queryDesc);
				}
			}
		}
		logp(dbName + " " + slowQueryTypes.size() + " slow queries\n\n" + buf.toString(), MyLogger.WARNING,
				"updateSlowQueryTypes");
	}

	/**
	 * Always called in a try-with-resources
	 */
	@SuppressWarnings("resource")
	@NonNull
	QueryDesc getQueryDesc(final PreparedStatement ps, final boolean isUpdate) throws SQLException {
		if (ps == null) {
			errorp("ps==null", "getQueryDesc");
		}
		assert ps != null;
		QueryDesc result;
		try {
			final Date start = initSlow(ps);
			int nRows;
			ResultSet rs = null;
			if (isUpdate) {
				nRows = ps.executeUpdate();
			} else {
				rs = ps.executeQuery();
				// if (rs == null) {
				// errorp("rs==null for " + ps, "getQueryDesc");
				// }
				if (isClosed(rs)) {
					errorp("rs.isClosed() for " + ps, "getQueryDesc");
				}
				nRows = MyResultSet.nRows(rs);
			}
			final long duration = start == null ? 0L : new Date().getTime() - start.getTime();
			result = new QueryDesc(ps, duration, nRows, rs);
			showSlowInternal(result);
		} catch (final Throwable e) {
			logp("For " + ps, MyLogger.SEVERE, e, "getQueryDesc");
			throw (e);
		}
		return result;
	}

	private static class QueryDesc implements AutoCloseable {
		final @NonNull Object psOrStringOrStringArray;

		/**
		 * null iff psOrStringOrStringArray is an update
		 */
		final @Nullable ResultSet rs;
		final long duration;
		final int nRows;

		QueryDesc(final @NonNull Object _psOrStringOrStringArray, final long _duration, final int _nRows,
				final @Nullable ResultSet _rs) {
			assert _psOrStringOrStringArray instanceof MyPreparedStatement || _psOrStringOrStringArray instanceof String
					|| _psOrStringOrStringArray instanceof String[] : _psOrStringOrStringArray;
			psOrStringOrStringArray = _psOrStringOrStringArray;
			duration = _duration;
			nRows = _nRows;
			rs = _rs;
		}

		@NonNull
		String sql() {
			String result;
			if (psOrStringOrStringArray instanceof MyPreparedStatement) {
				result = ((MyPreparedStatement) psOrStringOrStringArray).substitutedSQL();
			} else if (psOrStringOrStringArray instanceof String) {
				result = ((String) psOrStringOrStringArray);
			} else {
				result = UtilString.join((String[]) psOrStringOrStringArray, "; ");
			}
			assert result != null;
			return result;
		}

		@Override
		public String toString() {
			return UtilString.toString(this, "  duration=" + UtilString.addCommas(duration) + "ms nRows="
					+ UtilString.addCommas(nRows) + "\n" + sql());
		}

		// buf.append(UtilString.addCommas(queryDuration)).append("ms
		// for\nEXPLAIN ")
		// .append(stupidPrettyPrint(sql)).append("\n").append(MyResultSet.valueOfDeep(explainRS))
		// .append("\n");

		@Override
		public void close() throws Exception {
			// if (psOrStringOrStringArray instanceof MyPreparedStatement) {
			// ((MyPreparedStatement) psOrStringOrStringArray).close();
			// }
		}
	}

	/**
	 * logp the EXPLAIN of queryDesc (if MySQL can explain queries like
	 * queryDesc).
	 *
	 * @param queryDesc
	 *            is a PreparedStatement, SQL String, or array of SQL Strings
	 * @param queryDuration
	 */
	private void explain(final QueryDesc queryDesc, final long queryDuration) {
		final String sql = queryDesc.sql();
		final String explainableSQL = explainableSQL(sql);
		if (explainableSQL != null) {
			try (final ResultSet explainRS = sqlQuery("EXPLAIN " + explainableSQL);) {
				final StringBuilder buf = new StringBuilder();
				buf.append("For database ").append(dbName).append(" ").append(UtilString.addCommas(queryDuration))
						.append("ms for\nEXPLAIN ").append(stupidPrettyPrint(sql)).append("\n")
						.append(MyResultSet.valueOfDeep(explainRS)).append("\n");
				logp(buf, MyLogger.WARNING, "explain");
			} catch (final SQLException e) {
				logp("explain(\"" + sql + "\") ignoring", MyLogger.SEVERE, e, "explain");
			}
		}
	}

	private static final String[] BREAK_ONS = { "SELECT", "FROM", "INNER", "GROUP", "ORDER", "LIMIT", "LEFT", "WHERE",
			"AND", "OR", "UNION", "HAVING" };

	static String stupidPrettyPrint(String sql) {
		for (final String s : BREAK_ONS) {
			// regex to match an entire word, ignoring case
			sql = sql.replaceAll("(?i)\\b" + s + "\\b", "\n" + s);
		}
		int indent = 0;
		final String[] lines = sql.split("\n");
		final StringBuilder buf = new StringBuilder();
		for (final String line : lines) {
			assert line != null;
			buf.append(UtilString.getSpaces(4 * indent)).append(line);
			indent += UtilString.nOccurrences(line, '(') - UtilString.nOccurrences(line, ')');
		}
		return buf.toString();
	}

	private static final Pattern CREATEAS_PATTERN = Pattern.compile("\\s*CREATE\\s*TABLE\\s*\\w*\\s*((?:AS)?)(.*)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private final static String[] UNEXPLAINABLES = { "ALTER TABLE", "OPTIMIZE TABLE", "EXPLAIN", "TRUNCATE" };

	private static String explainableSQL(@Nullable String sql) {
		String result = null;
		if (sql != null) {
			// If sql contains multiple statements, only explain the first.
			sql = sql.split(";")[0];
			@SuppressWarnings("null")
			final String sqlUPPER = sql.toUpperCase(Locale.getDefault());
			for (final String unexplainable : UNEXPLAINABLES) {
				if (sqlUPPER.contains(unexplainable)) {
					return null;
				}
			}

			final Matcher matcher = CREATEAS_PATTERN.matcher(sql);
			if (matcher.matches()) {
				if (matcher.group(1).equals("AS")) {
					result = matcher.group(2);
				}
			} else {
				result = sql;
			}
		}
		return result;
	}

	// private static String[] slowQueryDescEquals(final Object desc1, final
	// Object desc2) {
	// if(desc1 instanceof String && desc2 instanceof String) {
	// return desc1.equals(desc2)
	// }
	// }

	/**
	 * @return nRows
	 */
	public int sqlUpdate(final @NonNull String sql) throws SQLException {
		int result = -1;
		try {
			result = sqlUpdateInternal(sql);
		} catch (final Throwable e) {
			logp(sql, MyLogger.SEVERE, e, "sqlUpdate");
			throw (e);
		}
		return result;
	}

	public int sqlUpdateWithStringArgs(final @NonNull PreparedStatement ps, final String... args) throws SQLException {
		substituteArgs(ps, args);
		return sqlUpdate(ps);
	}

	public int sqlUpdateWithIntArgs(final @NonNull PreparedStatement ps, final int... args) throws SQLException {
		substituteArgs(ps, args);
		return sqlUpdate(ps);
	}

	// /**
	// * @return nRows
	// */
	// public int sqlUpdate(final PreparedStatement ps) throws SQLException {
	// return sqlUpdateInternal(ps);
	// }

	// private Statement myCreateStatement() throws SQLException {
	// final Statement result = con.createStatement(
	// ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
	// // if (slowQueryTime < 0) {
	// // // Don't use caching if we're measuring times. Hmmm, dunno about
	// // // that.
	// // statements.put(result, SQL);
	// // }
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// public String describeSchema(final String[] tables) {
	// final StringBuilder buf = new StringBuilder();
	// for (final String table : tables) {
	// try (ResultSet rs = sqlQuery("SHOW CREATE TABLE " + table);) {
	// buf.append(MyResultSet.valueOfDeep(rs, Integer.MAX_VALUE))
	// .append("\n");
	// } catch (final SQLException e) {
	// e.printStackTrace();
	// }
	// }
	// return buf.toString();
	// }

	/**
	 * @return nRows
	 */
	public int sqlUpdate(final @NonNull PreparedStatement ps) {
		int result = -1;
		try (QueryDesc queryDesc = getQueryDesc(ps, true);) {
			result = queryDesc.nRows;
		} catch (final Exception e) {
			errorp("Exception for " + ps, e, "sqlUpdate");
		}
		return result;

		// final Date start = initSlow(ps);
		// int nRows = 0;
		// try {
		// nRows = ps.executeUpdate();
		// } catch (final Throwable e) {
		// logp("For db " + dbName + ", while executing:\n" + ps,
		// MyLogger.WARNING,
		// e, "sqlUpdateInternal");
		// // For some reason, se message is not being printed by catcher
		// // System.err.println("SQL Exception: " + se.getMessage());
		// // se.printStackTrace();
		// throw (e);
		// }
		// showSlow(start, nRows, ps);
		// return nRows;
	}

	/**
	 * @return nRows
	 */
	private int sqlUpdateInternal(final @NonNull String sql) throws SQLException {
		return sqlUpdate(lookupPS(sql));
		// final Date start = initSlow(SQL);
		// int nRows = 0;
		// try (Statement statement = con.createStatement();) {
		// nRows = statement.executeUpdate(SQL);
		// } catch (final SQLException se) {
		// logp("For db " + dbName + ", while executing:\n" + SQL,
		// MyLogger.WARNING, se, "JDBCSample", "sqlUpdateInternal");
		// // For some reason, se message is not being printed by catcher
		// // System.err.println("SQL Exception: " + se.getMessage());
		// // se.printStackTrace();
		// throw (se);
		// }
		// showSlow(start, nRows, SQL);
		// return nRows;
	}

	// /**
	// * @return nRows
	// */
	// public int[] sqlUpdate(final String[] sql) throws SQLException {
	// final int nStatements = sql.length;
	// assert nStatements > 0;
	// final Date start = initSlow(sql);
	// int[] nRows = null;
	// try (final Statement stmt = con.createStatement();) {
	// for (int i = 0; i < nStatements; i++) {
	// // print(SQL[i]);
	// stmt.addBatch(sql[i]);
	// }
	// nRows = stmt.executeBatch();
	// } catch (final Throwable e) {
	// logp("For db " + dbName + ", while executing:\n" + Arrays.toString(sql),
	// MyLogger.WARNING, e, "sqlUpdate");
	// // For some reason, se message is not being printed by catcher
	// // System.err.println("SQL Exception: " + se.getMessage());
	// // se.printStackTrace();
	// throw (e);
	// }
	// showSlow(start, nRows, sql);
	// return nRows;
	// }

	/**
	 * Always called in a try-with-resources
	 */
	@NonNull
	QueryDesc sqlQueryDesc(final @NonNull String sql) throws SQLException {
		return sqlQueryDesc(lookupPS(sql));
	}

	/**
	 * Always called in a try-with-resources
	 */
	public @NonNull ResultSet sqlQuery(final @NonNull String sql) throws SQLException {
		return sqlQuery(lookupPS(sql));
	}

	/**
	 * Always called in a try-with-resources
	 */
	@NonNull
	QueryDesc sqlQueryDesc(final @NonNull PreparedStatement ps) {
		QueryDesc result = null;
		try (QueryDesc queryDesc = getQueryDesc(ps, false);) {
			result = queryDesc;
		} catch (final Exception e) {
			errorp(ps + "", e, "sqlQueryDesc");
			// e.printStackTrace();
		}
		assert result != null;
		return result;
		// ResultSet rs = null;
		// try {
		// final Date start = initSlow(ps);
		// rs = ps.executeQuery();
		// showSlow(start, MyResultSet.nRowsNoThrow(rs), ps);
		// } catch (final Throwable e) {
		// logp("For " + ps, MyLogger.SEVERE, e, "sqlQuery");
		// throw (e);
		// }
		// if (isClosed(rs)) {
		// errorp("rs.isClosed() for " + ps, "sqlQuery");
		// }
		// assert rs != null;
		// return rs;
	}

	/**
	 * Always called in a try-with-resources
	 */
	public @NonNull ResultSet sqlQuery(final @NonNull PreparedStatement ps) {
		ResultSet result = null;
		try (QueryDesc queryDesc = getQueryDesc(ps, false);) {
			result = queryDesc.rs;
		} catch (final Exception e) {
			errorp(ps + "", e, "sqlQuery");
			// e.printStackTrace();
		}
		if (result == null) {
			errorp("result==null for " + ps, "sqlQuery");
		}
		assert result != null;
		return result;
		// ResultSet rs = null;
		// try {
		// final Date start = initSlow(ps);
		// rs = ps.executeQuery();
		// showSlow(start, MyResultSet.nRowsNoThrow(rs), ps);
		// } catch (final Throwable e) {
		// logp("For " + ps, MyLogger.SEVERE, e, "sqlQuery");
		// throw (e);
		// }
		// if (isClosed(rs)) {
		// errorp("rs.isClosed() for " + ps, "sqlQuery");
		// }
		// assert rs != null;
		// return rs;
	}

	boolean isClosed(final ResultSet rs) throws SQLException {
		return !isOracle() && rs.isClosed();
	}

	public boolean isSqlQueryIntPositive(final @NonNull String sql) throws SQLException {
		return sqlQueryInt(sql, true) > 0;
	}

	private boolean isSqlQueryIntPositive(final @NonNull PreparedStatement ps) throws SQLException {
		return sqlQueryInt(ps, true) > 0;
	}

	public int sqlQueryInt(final @NonNull String sql) throws SQLException {
		return sqlQueryInt(sql, false);
	}

	/**
	 * @return first column of first and only row of rs, or -1 if no rows.
	 */
	int sqlQueryInt(final @NonNull String sql, final boolean noRowsOK) throws SQLException {
		int sqlQueryIntInternal;
		try (final ResultSet rs = sqlQuery(sql);) {
			// if (rs == null) {
			// errorp("rs==null for " + sql, "sqlQueryInt");
			// }
			sqlQueryIntInternal = sqlQueryIntInternal(rs, sql, noRowsOK);
		}
		return sqlQueryIntInternal;
	}

	public int sqlQueryInt(final @NonNull PreparedStatement ps, final boolean noRowsOK, final String... args)
			throws SQLException {
		substituteArgs(ps, args);
		return sqlQueryInt(ps, noRowsOK);
	}

	public int sqlQueryInt(final @NonNull PreparedStatement ps, final String... args) throws SQLException {
		substituteArgs(ps, args);
		return sqlQueryInt(ps);
	}

	public int sqlQueryInt(final @NonNull PreparedStatement ps, final boolean noRowsOK, final int... args)
			throws SQLException {
		substituteArgs(ps, args);
		return sqlQueryInt(ps, noRowsOK);
	}

	public int sqlQueryInt(final @NonNull PreparedStatement ps, final int... args) throws SQLException {
		substituteArgs(ps, args);
		return sqlQueryInt(ps);
	}

	private static void substituteArgs(final PreparedStatement ps, final int... args) throws SQLException {
		assert args.length == ps.getParameterMetaData().getParameterCount() : args.length + " "
				+ ps.getParameterMetaData().getParameterCount();
		for (int arg = 0; arg < args.length; arg++) {
			ps.setInt(arg + 1, args[arg]);
		}
	}

	private static void substituteArgs(final PreparedStatement ps, final String... args) throws SQLException {
		assert args.length == ps.getParameterMetaData().getParameterCount() : args.length + " "
				+ ps.getParameterMetaData().getParameterCount();
		for (int arg = 0; arg < args.length; arg++) {
			ps.setString(arg + 1, args[arg]);
		}
	}

	public int sqlQueryInt(final @NonNull PreparedStatement ps) throws SQLException {
		return sqlQueryInt(ps, false);
	}

	/**
	 * @return first column of first and only row of rs, or -1 if no rows.
	 * @throws SQLException
	 */
	public int sqlQueryInt(final @NonNull PreparedStatement ps, final boolean noRowsOK) throws SQLException {
		int result = -1;
		try (final ResultSet rs = sqlQuery(ps);) {
			result = sqlQueryIntInternal(rs, ps.toString(), noRowsOK);
		} catch (final Throwable e) {
			errorp(ps + "", e, "sqlQueryInt");
			// System.err.println("While sqlQueryInt " + ps +":\n");
			// throw (e);
		}
		return result;
	}

	/**
	 * @param dbName
	 * @return first column of first and only row of rs, or -1 if no rows.
	 */
	private int sqlQueryIntInternal(final @NonNull ResultSet rs, final String sql, final boolean noRowsOK)
			throws SQLException {
		// if (rs == null) {
		// errorp("rs==null for " + sql, "sqlQueryIntInternal");
		// }
		int result = -1;
		int nRows = 0;
		if (rs.next()) {
			result = rs.getInt(1);
			nRows = 1;
			if (rs.next()) {
				nRows = 2;
			}
		}
		if (nRows != 1 && (nRows != 0 || !noRowsOK)) {
			errorp("Non-unique result for " + dbName + " " + sql + "\n" + MyResultSet.valueOfDeep(rs, 10),
					"sqlQueryIntInternal");
		}
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return first column of first and only row of rs, or -1 if no rows.
	// */
	// public List<Object> sqlSingleRowQuery(final @NonNull PreparedStatement
	// ps) throws SQLException {
	// List<Object> result;
	// try (final ResultSet rs = sqlQuery(ps);) {
	// result = MyResultSet.valueOfSingleRowRS(rs, ps.toString());
	// }
	// return result;
	// }

	/**
	 * @return first column of rs.
	 */
	public int[] sqlQueryIntArray(final @NonNull PreparedStatement ps) throws SQLException {
		int[] result = null;
		try (final ResultSet rs = sqlQuery(ps);) {
			result = new int[MyResultSet.nRows(rs)];
			for (int i = 0; i < result.length; i++) {
				rs.next();
				result[i] = rs.getInt(1);
			}
		}
		return result;
	}

	/**
	 * @return first column of rs.
	 */
	String[] sqlQueryStringArray(final @NonNull PreparedStatement ps) throws SQLException {
		String[] result = null;
		try (final ResultSet rs = sqlQuery(ps);) {
			result = new String[MyResultSet.nRows(rs)];
			for (int i = 0; i < result.length; i++) {
				rs.next();
				result[i] = rs.getString(1);
			}
		}
		return result;
	}

	public @NonNull String sqlQueryString(final @NonNull String sql) {
		final String result = sqlQueryString(sql, false);
		assert result != null : dbName + " " + sql;
		return result;
	}

	/**
	 * @return first column of first and only row of rs, or null if no rows.
	 */
	@Nullable
	public String sqlQueryString(final @NonNull String sql, final boolean noRowsOK) {
		String result = null;
		try (final QueryDesc queryDesc = sqlQueryDesc(sql);) {
			result = sqlQueryStringInternal(queryDesc, noRowsOK);
		} catch (final Exception e) {
			errorp(sql, e, "sqlQueryString");
			// e.printStackTrace();
		}
		return result;
	}

	public @NonNull String sqlQueryString(final @NonNull PreparedStatement ps) {
		final String result = sqlQueryString(ps, false);
		assert result != null : dbName + " " + ps;
		return result;
	}

	/**
	 * @return first column of first and only row of rs, or null if no rows.
	 */
	public @Nullable String sqlQueryString(final @NonNull PreparedStatement ps, final boolean noRowsOK) {
		String result = null;
		try (final QueryDesc queryDesc = sqlQueryDesc(ps);) {
			result = sqlQueryStringInternal(queryDesc, noRowsOK);
		} catch (final Exception e) {
			errorp(ps + "", e, "sqlQueryString");
			// e.printStackTrace();
		}
		return result;
	}

	/**
	 * @return first column of first and only row of rs, or null if no rows.
	 */
	private @Nullable String sqlQueryStringInternal(final QueryDesc queryDesc, final boolean noRowsOK)
			throws SQLException {
		String result = null;
		final ResultSet rs = queryDesc.rs;
		assert rs != null;
		// try {
		final int nRows = MyResultSet.nRows(rs);
		if (nRows != 1 && (nRows != 0 || !noRowsOK)) {
			errorp(queryDesc + " returned " + nRows + " records.", "sqlQueryStringInternal");
		}
		if (nRows > 0) {
			rs.next();
			result = rs.getString(1);
		}
		// } finally {
		// close(rs);
		// }
		// } catch (SQLException se) {
		// System.err.println("SQL Exception: " + se.getMessage());
		// se.printStackTrace();
		// }
		return result;
	}

	/**
	 * @return MyPreparedStatement, or null if sql is null
	 */
	public @Nullable PreparedStatement maybeLookupPS(final @Nullable String sql) throws SQLException {
		if (sql == null) {
			return null;
		}
		return lookupPS(sql);
	}

	public @NonNull PreparedStatement lookupPS(final @NonNull String sql) throws SQLException {
		return lookupPS(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
	}

	public @NonNull PreparedStatement lookupPS(final @NonNull String sql, final int resultSetType,
			final int resultSetConcurrency) throws SQLException {
		final boolean isCache = resultSetType == ResultSet.TYPE_SCROLL_SENSITIVE
				&& resultSetConcurrency == ResultSet.CONCUR_READ_ONLY;
		MyPreparedStatement ps = isCache ? myPreparedStatements.get(sql) : null;
		if (ps == null) {
			ps = new MyPreparedStatement(sql, this, resultSetType, resultSetConcurrency);
			if (ps.isClosed()) {
				errorp("Closed MyPreparedStatement:\n" + ps + "\n" + this, "lookupPS");
			}
			if (isCache) {
				myPreparedStatements.put(sql, ps);
			}
		} else if (ps.isClosed()) {
			errorp("Closed MyPreparedStatement:\n" + ps + "\n" + this, "lookupPS");
		}

		// slow
		// myAssertp(ps.getResultSetType() != ResultSet.TYPE_FORWARD_ONLY,
		// ps.toString(), "lookupPS");
		return ps;
	}

	/**
	 * @return {TINYINT | SMALLINT | MEDIUMINT | INT} UNSIGNED NOT NULL
	 */
	public @NonNull String unsignedTypeForMaxValue(final @NonNull String sql) throws SQLException {
		return unsignedTypeForMaxValue(sqlQueryInt(sql));
	}

	/**
	 * @return {TINYINT | SMALLINT | MEDIUMINT | INT} UNSIGNED NOT NULL
	 */
	public static @NonNull String unsignedTypeForMaxBits(final int nBits) {
		return unsignedTypeForMaxValue(1 << nBits);
	}

	/**
	 * @return {TINYINT | SMALLINT | MEDIUMINT | INT} UNSIGNED NOT NULL
	 */
	public static @NonNull String unsignedTypeForMaxValue(final int max) {
		assert max >= 0;
		String type = null;
		if (max < 256) {
			type = "TINYINT";
		} else if (max < 256 * 256) {
			type = "SMALLINT";
		} else if (max < 256 * 256 * 256) {
			type = "MEDIUMINT";
		} else {
			type = "INT";
			// } else {
			// Since max is a Java int, it must an SQL INT.
			// assert false : max + " is too big.";
		}
		type += " UNSIGNED NOT NULL";
		return type;
	}

	public void ensureIndex(final String table, final String name, final String columnNames) throws SQLException {
		ensureIndex(dbName, table, name, columnNames, "");
	}

	public void ensureIndex(final String table, final String name, final String columnNames, final String type)
			throws SQLException {
		ensureIndex(dbName, table, name, columnNames, type);
	}

	@SuppressWarnings("resource")
	private void ensureIndex(final String db, final String table, String name, final String columnNames,
			final String type) throws SQLException {
		assert type.length() == 0 || "PRIMARY, UNIQUE, FULLTEXT, SPATIAL, BTREE, HASH, RTREE".indexOf(type) >= 0;
		final PreparedStatement ps = lookupPS("SELECT GROUP_CONCAT(CONCAT(column_name, "
				+ "IF(sub_part IS NULL,'',CONCAT('(',sub_part,')')))) " + "FROM information_schema.STATISTICS "
				+ "WHERE table_schema = ? AND table_name = ? " + "AND index_name = ? ORDER BY seq_in_index");
		ps.setString(1, db);
		ps.setString(2, table);
		ps.setString(3, name);
		final String oldColumns = sqlQueryString(ps, true);
		if (!columnNames.equalsIgnoreCase(oldColumns)) {
			if (oldColumns != null) {
				sqlUpdate("ALTER TABLE " + table + " DROP INDEX " + name);
			}
			String indexSpec = "INDEX";
			if ("UNIQUE, FULLTEXT, SPATIAL".indexOf(type) >= 0) {
				indexSpec = type + " INDEX";
			} else if ("PRIMARY".indexOf(type) >= 0) {
				indexSpec = "PRIMARY KEY";
				assert name.equals("");
			} else if ("BTREE, HASH, RTREE".indexOf(type) >= 0) {
				name += " USING " + type;
			}
			sqlUpdate("ALTER TABLE " + table + " ADD " + indexSpec + " " + name + " (" + columnNames + ")");
		}
	}

	@SuppressWarnings("resource")
	public boolean databaseExists(final String dbName1) throws SQLException {
		boolean result;
		if (isOracle()) {
			final int nCols = sqlQueryInt("SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE OWNER='" + dbName1 + "' ");
			result = nCols > 0;
		} else {
			final PreparedStatement ps = lookupPS(
					"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?");
			ps.setString(1, dbName1);
			result = isSqlQueryIntPositive(ps);
		}
		return result;
	}

	public boolean isOracle() {
		final boolean result = connectString.contains("oracle");
		assert result || connectString.contains("mysql") : connectString;
		return result;
	}

	public boolean tableExists(final String table) throws SQLException {
		return tableExists(dbName, table);
	}

	@SuppressWarnings("resource")
	boolean tableExists(final String _dbName, final String table) throws SQLException { // NO_UCD
		// (use
		// default)
		final PreparedStatement ps = lookupPS(
				"SELECT COUNT(*) FROM information_schema.TABLES " + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?");
		ps.setString(1, _dbName);
		ps.setString(2, table);
		return isSqlQueryIntPositive(ps);
	}

	@SuppressWarnings("resource")
	public String[] getTables() throws SQLException {
		final PreparedStatement ps = lookupPS(
				"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?");
		ps.setString(1, dbName);
		return sqlQueryStringArray(ps);
	}

	public boolean columnExists(final String table, final String column) throws SQLException {
		return columnExists(dbName, table, column);
	}

	@SuppressWarnings("resource")
	boolean columnExists(final String schema, final String table, final String column) throws SQLException {
		final PreparedStatement ps = lookupPS("SELECT COUNT(*) FROM information_schema.columns "
				+ "WHERE table_schema = ? AND table_name = ? AND column_name = ?");
		ps.setString(1, schema);
		ps.setString(2, table);
		ps.setString(3, column);
		return isSqlQueryIntPositive(ps);
	}

	@SuppressWarnings("resource")
	public @NonNull String[] columnNames(final String table) throws SQLException {
		final PreparedStatement ps = lookupPS("SELECT GROUP_CONCAT(column_name) FROM information_schema.columns "
				+ "WHERE table_schema = ? AND table_name = ?" + " GROUP BY table_name");
		ps.setString(1, dbName);
		ps.setString(2, table);
		final String[] result = sqlQueryString(ps).split(",");
		assert result != null : dbName + " " + table;
		return result;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, connectString);
	}

	public void copyOracleTableToMySQL(final @NonNull PreparedStatement ps, final ResultSet rs) throws SQLException {
		final int columnCount = rs.getMetaData().getColumnCount();
		// final String[] columnTypes = new String[columnCount];
		// for (int column = 0; column < columnCount; column++) {
		// columnTypes[column] = rs.getMetaData().getColumnTypeName(column);
		// }
		while (rs.next()) {
			// System.out.println();
			for (int column = 1; column <= columnCount; column++) {
				ps.setString(column, rs.getString(column));
				// System.out.print(rs.getString(column) + "\t");
			}
			sqlUpdate(ps);
		}
	}

	// public void createTableFromRS(final String tableName, final ResultSet rs)
	// throws SQLException {
	// dropTable(tableName);
	// StringBuilder buf=new StringBuilder();
	// buf.append("CREATE TABLE "+tableName+"(");
	// final int columnCount = rs.getMetaData().getColumnCount();
	// // final String[] columnTypes = new String[columnCount];
	// for (int column = 0; column < columnCount; column++) {
	// String columnTypeName= rs.getMetaData().getColumnTypeName(column);
	// buf.append(columnTypeName);
	// int columnType = rs.getMetaData().getColumnType(column);
	// }
	// while (rs.next()) {
	// for (int column = 1; column <= columnCount; column++) {
	// ps.setString(1, rs.getString(column));
	// }
	// sqlUpdate(ps);
	// }
	// }

	private final Pattern varcharPattern = Pattern.compile("varchar\\((\\d+)\\)");

	public void setCollation(final String newCollation) throws SQLException {
		final String charSet = newCollation.split("_")[0];
		final boolean isMultiByte = charSet.toLowerCase(Locale.getDefault()).endsWith("mb4");
		@SuppressWarnings("resource")
		final PreparedStatement ps1 = lookupPS("ALTER SCHEMA CHARACTER SET ? COLLATE ?");
		sqlUpdateWithStringArgs(ps1, charSet, newCollation);
		final String[] sql1 = { "ALTER TABLE ", "",
				" CONVERT TO CHARACTER SET " + charSet + " COLLATE " + newCollation };
		final String[] sql2 = { "ALTER TABLE ", "", " CHANGE COLUMN ", "", " ", "",
				" VARCHAR(250) NOT NULL DEFAULT ''" };
		final String[] tables = getTables();
		for (final String table : tables) {
			boolean needsFixn = false;
			try (final ResultSet rs = sqlQuery("SHOW FULL COLUMNS FROM " + table);) {
				while (rs.next()) {
					final String oldCollation = rs.getString("Collation");
					if (oldCollation != null && !oldCollation.equals(newCollation)) {
						needsFixn = true;
						// break;
						if (isMultiByte) {
							final String type = rs.getString("Type");
							if (type != null) {
								final Matcher m = varcharPattern.matcher(type);
								if (m.matches() && Integer.parseInt(m.group(1)) > 250) {
									final String colName = rs.getString("Field");
									System.out.println("Changing type for " + dbName + "." + table + "." + colName
											+ " to VARCHAR(250)");
									sql2[1] = table;
									sql2[3] = colName;
									sql2[5] = colName;
									@NonNull
									String sql = Util.nonNull(UtilString.join(sql2, ""));
									if (rs.getString("Null").equalsIgnoreCase("yes")) {
										sql = Util.nonNull(sql.replace("NOT NULL ", ""));
									}
									sqlUpdate(sql);
								}
							}
						}
					}
				}
			}
			if (needsFixn) {
				System.out.println("Changing collation for " + dbName + "." + table + " to " + newCollation);
				sql1[1] = table;
				sqlUpdate(Util.nonNull(UtilString.join(sql1, "")));
			}
		}
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * @return whether facet names are ordered correctly (according to
	// COLLATE).
	// */
	// public static boolean checkCollation(final JDBCSample jdbc) throws
	// SQLException {
	// boolean result = true;
	// try (ResultSet rs = jdbc.sqlQuery("SELECT facet.name, nextFacet.name FROM
	// facet, facet nextFacet "
	// + "WHERE facet.facet_id + 1 = nextFacet.facet_id "
	// + "AND facet.parent_facet_id = nextFacet.parent_facet_id AND facet.name >
	// nextFacet.name " + "COLLATE "
	// + COLLATE);) {
	// while (rs.next()) {
	// result = false;
	// System.err.println(rs.getString(1) + " should precede\n" +
	// rs.getString(2));
	// }
	// }
	// return result;
	// }

}
