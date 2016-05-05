package log;

import java.sql.ResultSet;
import java.sql.SQLException;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.UtilFiles;

class Histograms {

	private final static String sessionsToDelete = "FALSE";

	@SuppressWarnings("null")
	static void printHistograms(final JDBCSample jdbc, final String[] dbNames) {
		createTables(jdbc, dbNames);
		System.out.println("All clients except 128.2.*.* and 127.0.0.1");

		final String clientQuery = "SELECT CONCAT(IFNULL(CONCAT(name, ' '), ''),client), COUNT(*) cnt"
				+ " FROM all_actions LEFT JOIN IPnames USING (client)" + " GROUP BY client ORDER BY cnt DESC";
		final String range = "SELECT CONCAT(',', POW(2, log_cnt), ' - ', POW(2, log_cnt+1) - 1) ";
		final String sessionQuery = range + "_range," + " COUNT(*) cnt FROM"
				+ " (SELECT TRUNCATE(LOG2(COUNT(*)), 0) log_cnt" + " FROM all_actions"
				+ " WHERE location IN (1, 2, 6, 16)" + " GROUP BY session) foo" + " GROUP BY log_cnt";
		try (ResultSet rs1 = jdbc.sqlQuery("SELECT CONCAT(YEAR(timestamp), ' ', MONTHNAME(timestamp)), COUNT(*) cnt"
				+ " FROM all_actions GROUP BY EXTRACT(YEAR_MONTH FROM timestamp) ORDER BY timestamp");
				final ResultSet rs2 = jdbc.sqlQuery("SELECT name, COUNT(*) cnt"
						+ " FROM all_actions INNER JOIN dbs USING (db) GROUP BY name ORDER BY cnt DESC");
				final ResultSet rs3 = jdbc.sqlQuery(clientQuery.replace("*", "DISTINCT session"));
				final ResultSet rs4 = jdbc.sqlQuery(clientQuery);
				final ResultSet rs5 = jdbc.sqlQuery(range + "'# session operations'," + " COUNT(*) cnt FROM"
						+ " (SELECT client, TRUNCATE(LOG2(COUNT(DISTINCT session)), 0) log_cnt"
						+ " FROM all_actions GROUP BY client) foo" + " GROUP BY log_cnt ORDER BY log_cnt");
				final ResultSet rs6 = jdbc.sqlQuery(range + "'session duration'," + " COUNT(*) cnt FROM"
						+ " (SELECT TRUNCATE(LOG2(GREATEST(1, TIMESTAMPDIFF(SECOND, MIN(timestamp), MAX(timestamp)))), 0) log_cnt"
						+ " FROM all_actions GROUP BY session) foo" + " GROUP BY log_cnt");
				final ResultSet rs7 = jdbc.sqlQuery(
						range + "_range," + " COUNT(*) cnt FROM" + "(SELECT TRUNCATE(LOG2(COUNT(*)), 0) log_cnt"
								+ " FROM all_actions GROUP BY session) foo" + " GROUP BY log_cnt");
				final ResultSet rs8 = jdbc.sqlQuery(
						"SELECT name, COUNT(*) cnt" + " FROM operations LEFT JOIN all_actions USING (location)"
								+ " GROUP BY location ORDER BY cnt DESC");
				final ResultSet rs9 = jdbc.sqlQuery(sessionQuery);
				final ResultSet rs10 = jdbc.sqlQuery(sessionQuery.replace("1, 2, 6, 16", "2, 6, 16"));
				final ResultSet rs11 = jdbc.sqlQuery(sessionQuery.replace("1, 2, 6, 16", "10"));) {
			System.out.println(
					"\nInterval\tTotal Number of Operations\n" + MyResultSet.valueOfDeep(rs1, Integer.MAX_VALUE));
			System.out.println(
					"\nDatabase\tTotal Number of Operations\n" + MyResultSet.valueOfDeep(rs2, Integer.MAX_VALUE));
			printIPnames(jdbc);
			System.out.println("\nClient\tNumber of Sessions\n" + MyResultSet.valueOfDeep(rs3, 10));
			System.out.println("\nClient\tTotal Number of Operations\n" + MyResultSet.valueOfDeep(rs4, 10));
			System.out.println(
					"\nNumber of Sessions per Client\tCount\n" + MyResultSet.valueOfDeep(rs5, Integer.MAX_VALUE));
			System.out
					.println("\nSession Duration (seconds)\tCount\n" + MyResultSet.valueOfDeep(rs6, Integer.MAX_VALUE));
			System.out.println("\n# Session Operations\tCount\n" + MyResultSet.valueOfDeep(rs7, Integer.MAX_VALUE));
			System.out.println("\nOperation\tCount\n" + MyResultSet.valueOfDeep(rs8, Integer.MAX_VALUE));
			System.out.println(
					"\n# Ambiguous Session Facet Operations\tCount" + MyResultSet.valueOfDeep(rs9, Integer.MAX_VALUE));
			System.out.println("\n# Unambiguous Session Facet Operations\tCount\n"
					+ MyResultSet.valueOfDeep(rs10, Integer.MAX_VALUE));
			System.out.println(
					"\n# Session Search Operations\tCount\n" + MyResultSet.valueOfDeep(rs11, Integer.MAX_VALUE));

		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	private static void createTables(final JDBCSample jdbc, final String[] dbNames) {
		final StringBuffer buf = new StringBuffer();
		for (final String dbName : dbNames) {
			if (buf.length() > 0) {
				buf.append(" UNION ");
			}
			buf.append("(SELECT '").append(dbName).append("' db, user_actions.* FROM ").append(dbName)
					.append(".user_actions WHERE NOT client = '127.0.0.1' AND NOT LEFT(client, 6) = '128.2.')");
		}
		try {
			final String sql = "CREATE TEMPORARY TABLE all_actions AS " + buf.toString();
			jdbc.sqlUpdate(sql);
			jdbc.sqlUpdate("CREATE TEMPORARY TABLE sessions AS "
					+ "(SELECT session, COUNT(*) nOperations, TIMESTAMPDIFF(SECOND, MIN(timestamp), MAX(timestamp)) duration"
					+ " FROM all_actions GROUP BY session)");
			jdbc.sqlUpdate("DELETE FROM all_actions USING all_actions, sessions"
					+ " WHERE all_actions.session=sessions.session AND " + sessionsToDelete);

			jdbc.sqlUpdate("CREATE TEMPORARY TABLE operations"
					+ " (name VARCHAR(45) NOT NULL, location SMALLINT UNSIGNED NOT NULL,"
					+ " PRIMARY KEY (location));");
			jdbc.sqlUpdate("INSERT INTO operations VALUES('BAR', 1),('BAR_LABEL', 2),"
					+ "('RANK_LABEL', 3),('THUMBNAIL', 4),('IMAGE', 5),('FACET_TREE', 6),"
					+ "('KEYPRESS (no longer used)', 8),('BUTTON', 9),('SEARCH', 10),"
					+ "('SETSIZE', 11),('RESTRICT', 13),"
					+ "('SHOW_CLUSTERS', 14),('GRID_ARROW', 15),('FACET_ARROW', 16),"
					+ "('REORDER', 17),('TOGGLE_CLUSTER_EXCLUSION', 18),"
					+ "('TOGGLE_CLUSTER', 19),('TOGGLE_POPUPS', 20),('SHOW_MORE_HELP', 21),"
					+ "('ZOOM', 22),('MODE', 23),('ERR', 24);");

			jdbc.sqlUpdate("CREATE TEMPORARY TABLE dbs" + " (db VARCHAR(45) NOT NULL, name VARCHAR(45) NOT NULL,"
					+ " PRIMARY KEY (db));");
			jdbc.sqlUpdate("INSERT INTO dbs VALUES('loc', 'loc'),('OLDloc', 'loc'),"
					+ "('ChartresVezelay', 'ChartresVezelay'),('Visuals', 'Visuals'),"
					+ "('Movie', 'Movie'),('personal', 'personal'),('music', 'music'),"
					+ "('wpa', 'wpa'),('hp2', 'hp'),('hp3', 'hp');");
		} catch (final SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@SuppressWarnings("resource")
	private static void printIPnames(final JDBCSample jdbc) throws SQLException {
		jdbc.sqlUpdate("CREATE TEMPORARY TABLE IPnames" + " (client VARCHAR(45) NOT NULL, name VARCHAR(200) NOT NULL,"
				+ " PRIMARY KEY (client));");

		final java.sql.PreparedStatement IPnames = jdbc.lookupPS("INSERT into IPnames VALUES(?, ?)");

		// It's good to have a large limit here, so e.g. all Pitt computers are
		// counted, but the lookup is slow.
		try (ResultSet rs = jdbc
				.sqlQuery("SELECT client from all_actions GROUP BY client ORDER BY COUNT(*) DESC LIMIT 500");) {
			while (rs.next()) {
				final String client = rs.getString("client");
				IPnames.setString(1, client);
				// IPnames.setString(2, getGeoInfo(client));
				IPnames.setString(2, UtilFiles.reverseDNSlookup(client));
				jdbc.sqlUpdateWithIntArgs(IPnames);
			}
		}
		try (ResultSet rs2 = jdbc.sqlQuery("SELECT name, COUNT(*) cnt"
				+ " FROM all_actions INNER JOIN IPnames USING (client)" + " GROUP BY name ORDER BY cnt DESC");) {
			System.out.println("\nDomain\tTotal Number of Operations\n" + MyResultSet.valueOfDeep(rs2, 20));
		}
	}
}
