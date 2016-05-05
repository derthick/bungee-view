package edu.cmu.cs.bungee.servlet;

import java.awt.image.BufferedImage;

/*

 Created on Mar 4, 2005

 Bungee View lets you search, browse, and data-mine an image collection.
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at
 mad@cs.cmu.edu,
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.compile.PopulateImageTable;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyLogger;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet.ColumnType;
import edu.cmu.cs.bungee.javaExtensions.MyResultSetColumnTypeList;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * CREATE USER 'bungee'@'localhost' IDENTIFIED BY 'p5pass';
 *
 * Permissions to add (ALTER ROUTINE is for finding clusters; EXECUTE is for
 * tetrad; DROP is for truncate table):
 *
 * GRANT SELECT, INSERT, UPDATE, CREATE, DELETE, ALTER ROUTINE, EXECUTE, CREATE
 * VIEW, CREATE TEMPORARY TABLES, DROP ON chartresvezelay.* TO bungee@localhost
 *
 * In order to edit, you also need these:
 *
 * GRANT DROP, ALTER, CREATE ROUTINE ON *.* TO BVeditor@localhost
 *
 * For revert, need SUPER, REPLICATION CLIENT
 *
 * For now, we're just using root to revert
 *
 * Dump like this, and make sure Windows let you write to the destination
 * directory:
 *
 * "C:\Program Files\MySQL\MySQL Server 5.6\bin\mysqldump"
 *
 * -u root -p --add-drop-database --databases wpa >
 *
 * "C:\Users\mad\Google Drive\MySQL dumps\wpa.sql"
 *
 * Load like this:
 *
 * CREATE DATABASE newdatabase;
 *
 * mysql -u [username] -p newdatabase < [database name].sql
 *
 *
 * All the tables referred to by Database:
 *
 * globals, item, images, raw_facet_type, facet, item_facet_heap,
 * item_facetNtype_heap, item_order_heap, item_order, correlations, user_actions
 *
 * Temporary tables created by Database:
 *
 * onItems, restricted, relevantFacets, on_count_matrix_facets,
 * on_count_matrix_items
 */
public class Database implements AutoCloseable, Serializable { // NO_UCD (use
																// default)

	protected static final long serialVersionUID = 8922913873736902656L;

	private static final int SQL_INT_BITS = 32;

	final @NonNull String dbName;
	final @NonNull JDBCSample jdbc;

	final int maxFacetType;

	/**
	 * {itemDescriptionFields, genericObjectLabel, itemURLdoc, isEditable}
	 */
	private final @NonNull String[] globals;
	private final @NonNull String descriptionGetter;
	private final @NonNull String item_id_column_type;
	private final @NonNull String facet_id_column_type;
	/**
	 * Used when nItems > edu.cmu.cs.bungee.compile.Database.MAX_THUMBS
	 */
	private final @Nullable String uncachedThumbGetter;
	private final @Nullable Pattern uncachedThumbRegex;

	/**
	 * "SELECT " + itemURLgetter + " FROM item WHERE record_num = ?"
	 */
	private final @Nullable PreparedStatement itemIdPS;
	/**
	 * "SELECT record_num FROM item WHERE " + itemURLgetter + " = ?"
	 */
	private final @Nullable PreparedStatement itemURLPS;

	/**
	 * Should be able to maintain this ourselves, right?
	 */
	private boolean myIsRestrictedData = false;

	private PreparedStatement filteredCountQuery;
	private PreparedStatement unfilteredCountQuery;
	private PreparedStatement getItemInfoQuery;
	// private PreparedStatement getItemInfoQueryRestricted;
	private PreparedStatement getCountsIgnoringFacetQuery;
	private PreparedStatement getLetterOffsetsQuery;
	/**
	 * Return an ordinal (among all items - may not be sequential) for a single
	 * Item.
	 */
	private PreparedStatement[] itemOffsetQuerys;
	/**
	 * Return the recordNum's for a range of [0-based] offsets.
	 */
	private PreparedStatement[] offsetItemsQuerys;
	private PreparedStatement printUserActionStmt;
	/**
	 * [description, image, w, h]
	 */
	private PreparedStatement imageQuery;
	/**
	 * [description]
	 */
	private PreparedStatement itemDescQuery;

	/**
	 * "CREATE TEMPORARY TABLE " or "CREATE TABLE "
	 */
	private final @NonNull String createTemporaryTable;

	MyLogger myLogger = null;
	@NonNull
	Level myLoggerLevel = MyLogger.DEFAULT_LOGGER_LEVEL;

	@NonNull
	MyLogger getMyLogger() {
		if (myLogger == null) {
			myLogger = MyLogger.getMyLogger(Database.class);
			myLogger.setLevel(myLoggerLevel);
		}
		assert myLogger != null;
		return myLogger;
	}

	void setLogLevel(final @NonNull Level level) {
		if (myLogger == null) {
			myLoggerLevel = level;
		} else {
			myLogger.setLevel(level);
		}
		jdbc.setLogLevel(level);
	}

	private void logp(final @NonNull String msg, final @NonNull Throwable e, final @NonNull String sourceMethod) {
		MyLogger.logp(getMyLogger(), msg, MyLogger.SEVERE, e, "Database", sourceMethod);
	}

	/**
	 * Using
	 *
	 * if () {errorp();}
	 *
	 * instead of myAssertp() prevents unnecessary evaluation of msg.
	 */
	private void errorp(final @NonNull String msg, final @NonNull String sourceMethod) {
		logp(msg, MyLogger.SEVERE, sourceMethod);
		throw new AssertionError(msg);
	}

	private void logp(final @NonNull String msg, final @NonNull Level level, final @NonNull String sourceMethod) {
		MyLogger.logp(getMyLogger(), msg, level, "Database", sourceMethod);
	}

	static @NonNull String threadID() {
		return "currentThread=" + Thread.currentThread().getId() + "; ";
	}

	Database(final @NonNull String _server, final @NonNull String _dbname, final @NonNull String _user,
			final @NonNull String _pass, final boolean noTemporaryTables) throws SQLException, ServletException {
		try {
			dbName = _dbname;
			createTemporaryTable = "CREATE " + (noTemporaryTables ? "" : "TEMPORARY ") + "TABLE ";
			jdbc = JDBCSample.getMySqlJDBCSample(_server, dbName, _user, _pass);
			maxFacetType = jdbc.sqlQueryInt("SELECT MAX(facet_id) FROM facet WHERE parent_facet_id = 0");

			final String[][] g = globals();
			if (g[0] == null || g[1][0] == null) {
				errorp("globals() contains a null: " + UtilString.valueOfDeep(g), "<init>");
			}
			globals = Util.nonNull(g[0]);
			descriptionGetter = Util.nonNull(g[1][0]);
			itemIdPS = jdbc.maybeLookupPS(g[1][1]);
			itemURLPS = jdbc.maybeLookupPS(g[1][2]);

			item_id_column_type = jdbc.unsignedTypeForMaxValue("SELECT MAX(record_num) FROM item");
			facet_id_column_type = jdbc.unsignedTypeForMaxValue("SELECT MAX(facet_id) FROM facet");

			// print DB summary: nFacets, nItems, nRelationships
			// printStats();

			onetimeCleanups();

			uncachedThumbGetter = uncachedThumbGetter();
			uncachedThumbRegex = uncachedThumbRegex();

			// These happen once per MySQL server start
			populateHeapTables();

			// These happen for each jdbc connection
			reorderOffsetQueries(Compile.SORT_BY_RANDOM); // order by random,
															// initially
			createTempTables();
			cachePreparedStatements();
		} catch (final Throwable e) {
			logp(Util.nonNull(toString()), e, "<init>");
			close();
			throw (e);
		}
	}

	// private void printStats() {
	// try {
	// final int nRelations = jdbc.nRows("item_facetNtype_heap");
	// final int nFacets = jdbc.nRows("facet");
	// final int nItems = itemCount();
	// logp(dbName + "\t" + nRelations + "\t" + nFacets + "\t" + nItems,
	// MyLogger.WARNING, "printStats");
	// } catch (final SQLException e) {
	// e.printStackTrace();
	// }
	// }

	private void onetimeCleanups() throws SQLException {
		// Compile.dropObsoleteTables(jdbc);
		// Compile.ensureGlobalColumns(jdbc);
		// jdbc.sqlUpdate("REPLACE INTO item_facet SELECT DISTINCT
		// record_num, parent.facet_id "
		// +
		// "FROM facet child INNER JOIN facet parent ON
		// child.parent_facet_id = parent.facet_id "
		// +
		// "INNER JOIN item_facet ON item_facet.facet_id = child.facet_id "
		// + "WHERE parent.parent_facet_id = 0");
		// Compile.createItemOrderStatic(jdbc);
		// fixImageSizes();

		if (!jdbc.tableExists("correlations")) {
			Compile.maybeCreateCorrelationsTable(jdbc);
		}
		jdbc.sqlUpdate("DROP VIEW IF EXISTS item_facet_heap");
		jdbc.sqlUpdate("DROP VIEW IF EXISTS item_facet_type_heap");
		ensureUserActionsOncountColumn();
		// loseFacetNameNewlines();
	}

	// private void loseFacetNameNewlines() throws SQLException {
	// jdbc.sqlUpdate("UPDATE facet SET name = REPLACE(name, '\n', '↳')");
	// jdbc.sqlUpdate("UPDATE raw_facet SET name = REPLACE(name, '\n', '↳')");
	// }

	private void cachePreparedStatements() throws SQLException {

		unfilteredCountQuery = jdbc.lookupPS("SELECT facet_id, n_items FROM facet ORDER BY facet_id");

		filteredCountQuery = jdbc.lookupPS("SELECT facet_id, COUNT(*) FROM item_facetNtype_heap "
				+ "INNER JOIN onItems USING (record_num) GROUP BY facet_id ORDER BY NULL");

		getItemInfoQuery = jdbc.lookupPS("SELECT facet_id, parent_facet_id, name, n_child_facets, first_child_offset"
				+ " FROM facet INNER JOIN item_facetNtype_heap ifh1 USING (facet_id)"
				+ " WHERE ifh1.record_num = ? AND parent_facet_id != 0 ORDER BY facet_id");

		printUserActionStmt = jdbc.lookupPS("INSERT INTO user_actions VALUES(NOW(), ?, ?, ?, ?, ?, ?, ?)");

		getCountsIgnoringFacetQuery = jdbc.lookupPS("SELECT item_facetNtype_heap.facet_id, COUNT(record_num) AS cnt "
				+ "FROM item_facetNtype_heap INNER JOIN facet USING(facet_id) INNER JOIN counts_ignoring_facet"
				+ " USING (record_num) WHERE facet.parent_facet_id = ?"
				+ " GROUP BY facet.facet_id ORDER BY facet.facet_id");

		// ORDER BY clause doesn't make any difference if facets are ordered
		// correctly (by utf8mb4_unicode_ci),
		// but will correct for bad alphabetization in old databases.
		//
		// Used to have "LOCATE(?, name) = 1" instead of LIKE, but LOCATE
		// doesn't respect the collation properly
		getLetterOffsetsQuery = jdbc.lookupPS(
				"SELECT LCASE(SUBSTRING(name, ?, 1)) letter, MIN(facet_id) min_facet, MAX(facet_id) max_facet, "
						+ "MIN(name), MAX(name) FROM facet IGNORE INDEX (parent) "
						+ "WHERE name LIKE ? AND parent_facet_id = ? GROUP BY letter ORDER BY letter");
	}

	private void createTempTables() throws SQLException {
		createOnItemsLikeTable("onItems");
		createOnItemsLikeTable("restricted");
	}

	private void createOnItemsLikeTable(final @NonNull String table) throws SQLException {
		jdbc.dropTable(table);
		jdbc.sqlUpdate(createTemporaryTable + table + " (record_num " + item_id_column_type + ", sort "
				+ item_id_column_type
				+ " AUTO_INCREMENT, PRIMARY KEY (record_num), UNIQUE KEY (sort)) ENGINE=MEMORY PACK_KEYS=1 ROW_FORMAT=FIXED");
	}

	/**
	 * reRandomizeItemOrder, and populate all [two] heap tables:
	 * item_facetNtype_heap, item_order_heap
	 */
	private void populateHeapTables() throws SQLException {
		if (Compile.populateOrReplaceHeapTable(jdbc, "item_order_heap", "item_order",
				"CREATE TABLE item_order_heap (record_num " + item_id_column_type + ", random_id " + item_id_column_type
						+ ", col1 " + item_id_column_type + ", col2 " + item_id_column_type + ", col3 "
						+ item_id_column_type + ", col4 " + item_id_column_type + ", col5 " + item_id_column_type
						+ ", col6 " + item_id_column_type + ", col7 " + item_id_column_type + ", col8 "
						+ item_id_column_type + ", col9 " + item_id_column_type + ", col10 " + item_id_column_type
						+ ", col11 " + item_id_column_type + ", PRIMARY KEY (record_num),"
						+ " UNIQUE KEY random_id (random_id) USING BTREE, UNIQUE KEY col1 (col1) USING BTREE,"
						+ " UNIQUE KEY col2 (col2) USING BTREE, UNIQUE KEY col3 (col3) USING BTREE,"
						+ " UNIQUE KEY col4 (col4) USING BTREE, UNIQUE KEY col5 (col5) USING BTREE,"
						+ " UNIQUE KEY col6 (col6) USING BTREE, UNIQUE KEY col7 (col7) USING BTREE,"
						+ " UNIQUE KEY col8 (col8) USING BTREE, UNIQUE KEY col9 (col9) USING BTREE,"
						+ " UNIQUE KEY col10 (col10) USING BTREE, UNIQUE KEY col11 (col11) USING BTREE"
						+ ") DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
			Compile.reRandomizeItemOrder(item_id_column_type, jdbc);
		}
		Compile.populateOrReplaceHeapTable(jdbc, "item_facetNtype_heap", "item_facet",
				"CREATE VIEW item_facetNtype_heap AS SELECT * FROM item_facet");
	}

	@Override
	public void close() throws SQLException {
		jdbc.close();
	}

	@NonNull
	String aboutCollection() {
		return jdbc.sqlQueryString("SELECT aboutURL FROM globals");
	}

	/**
	 * This is only used by CONNECT command to initialize Query.allPersectives.
	 * If IDs are screwed up, it's better to make it big enough to not get an
	 * array index out of bounds error.
	 */
	int facetCount() throws SQLException {
		return jdbc.sqlQueryInt("SELECT MAX(facet_id) FROM facet");
	}

	int itemCount() throws SQLException {
		return jdbc.nRows("item");
	}

	/**
	 * Called by Servlet.doPostInternal
	 *
	 * {itemDescriptionFields, genericObjectLabel, itemURLdoc, isEditable}
	 */
	@NonNull
	String[] getGlobals() {
		return globals;
	}

	private @NonNull String[][] globals() throws SQLException {
		try (ResultSet rs = jdbc.sqlQuery("SELECT itemDescriptionFields, genericObjectLabel, itemURL, "
				+ "itemURLdoc, isEditable FROM globals");) {
			if (!rs.next()) {
				errorp("Can't get globals", "globals");
			}
			final String itemDescriptionFields = rs.getString("itemDescriptionFields");
			final String imageQuerySelect = "SELECT CONCAT_WS('\n\n',"

					// Use qualifiedDescriptionFields in case both item and
					// image have a URI column. NO! Rely on
					// globals.itemDescriptionFields to include qualifiers.
					// + qualifiedDescriptionFields

					+ itemDescriptionFields + ") description";

			final String imageQueryFrom = " FROM item LEFT JOIN images USING (record_num) WHERE record_num = ?";

			itemDescQuery = jdbc.lookupPS(imageQuerySelect + imageQueryFrom);
			imageQuery = jdbc.lookupPS(imageQuerySelect + ", image, w, h" + imageQueryFrom);
			final String[][] result = {
					{ itemDescriptionFields, rs.getString("genericObjectLabel"), rs.getString("itemURLdoc"),
							Boolean.toString(rs.getBoolean("isEditable")) },
					globalsInternal(rs, itemDescriptionFields) };
			return result;
		}
	}

	/**
	 * itemURLgetter should stick to atomic table references in any FROM clause,
	 * so that Populate.copyImagesNoURI can parse it and prepend explicit schema
	 * names. Any FROM clause should be followed immediately by WHERE. I.e.
	 * don't use JOIN syntax in expressions like this:
	 *
	 * (SELECT xref FROM movie, shotbreak WHERE movie.movie_id =
	 * shotbreak.movie_id AND shotbreak.shotbreak_id = item.shotbreak)
	 */
	private static String[] globalsInternal(final ResultSet rs, final String itemDescriptionFields)
			throws SQLException {
		String itemIdQuery = null;
		String itemURLquery = null;
		final String itemURLgetter = rs.getString("itemURL");
		if (UtilString.isNonEmptyString(itemURLgetter)) {
			itemIdQuery = "SELECT " + itemURLgetter + " FROM item WHERE record_num = ?";
			itemURLquery = "SELECT record_num FROM item WHERE " + itemURLgetter + " = ?";
		}
		final String[] result = { "CONCAT_WS('\n'," + itemDescriptionFields + ")", itemIdQuery, itemURLquery };
		return result;
	}

	/**
	 * Update each offsetItemsQuerys[] (used by offsetItems) and
	 * itemOffsetQuerys[] (used by itemOffset) so they return indexes sorted
	 * according to facetTypeToSortBy.
	 *
	 * The indexes into these arrays show which table contains the on items.
	 *
	 * @param facetTypeToSortBy
	 *            the item_order_heap column to sort by: random, ID, or the
	 *            facet_type_ID
	 * @throws SQLException
	 * @throws ServletException
	 */
	void reorderOffsetQueries(final int facetTypeToSortBy) throws SQLException, ServletException {
		synchronized (globals) {
			final String sortColumn = Compile.sortColumnName(facetTypeToSortBy);
			if (offsetItemsQuerys == null) {
				final int nOnItemsTables = OnItemsTable.values().length;
				offsetItemsQuerys = new PreparedStatement[nOnItemsTables];
				itemOffsetQuerys = new PreparedStatement[nOnItemsTables];
			}
			for (final OnItemsTable onItemsTable : OnItemsTable.values()) {
				final int i = onItemsTable.ordinal();
				if (isUnrestricted(onItemsTable)) {
					// ITEM_ORDER_HEAP is an easy special case: no join!
					offsetItemsQuerys[i] = jdbc.lookupPS("SELECT record_num FROM item_order_heap WHERE " + sortColumn
							+ " >= ? AND " + sortColumn + " <= ?");

					itemOffsetQuerys[i] = jdbc
							.lookupPS("SELECT " + sortColumn + "-1 FROM item_order_heap WHERE record_num = ?");
				} else {
					offsetItemsQuerys[i] = jdbc.lookupPS("SELECT record_num FROM " + onItemsTable
							+ " onItemsTable INNER JOIN item_order_heap USING (record_num) ORDER BY " + sortColumn
							+ " LIMIT ?, ?");

					itemOffsetQuerys[i] = jdbc.lookupPS("SELECT sort-1 FROM " + onItemsTable + " WHERE record_num = ?");
				}
			}
		}
	}

	private static boolean isUnrestricted(final OnItemsTable onItemsTable) {
		return onItemsTable == OnItemsTable.ITEM_ORDER_HEAP;
	}

	int getItemFromURL(final @NonNull String urlString) throws SQLException {
		int result = 0;
		if (itemURLPS != null) {
			synchronized (itemURLPS) {
				result = jdbc.sqlQueryInt(itemURLPS, urlString);
				if (result < 0) {
					errorp("Can't get item ID for '" + urlString + "': " + itemURLPS, "getItemFromURL");
				}
			}
		}
		return result;
	}

	@Nullable
	String getItemURL(final int itemID) throws SQLException {
		String result = null;
		if (itemIdPS != null) {
			synchronized (itemIdPS) {
				itemIdPS.setInt(1, itemID);
				result = jdbc.sqlQueryString(itemIdPS);
			}
		}
		return result;
	}

	void getCountsIgnoringFacet(final @NonNull String subQuery, final int ignoreParent,
			final @NonNull DataOutputStream out) throws SQLException, ServletException, IOException {
		jdbc.sqlUpdate(createTemporaryTable + "IF NOT EXISTS counts_ignoring_facet (record_num " + item_id_column_type
				+ ", PRIMARY KEY (record_num)) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
		jdbc.sqlUpdate("TRUNCATE TABLE counts_ignoring_facet");
		jdbc.sqlUpdate("INSERT INTO counts_ignoring_facet " + subQuery);
		getCountsIgnoringFacetQuery.setInt(1, ignoreParent);
		assert getCountsIgnoringFacetQuery != null;
		try (final ResultSet rs = jdbc.sqlQuery(getCountsIgnoringFacetQuery);) {
			sendResultSet(rs, MyResultSetColumnTypeList.SMINT_PINT, out);
		}
	}

	void getFilteredCounts(final @NonNull OnItemsTable onItemsTable, final @NonNull DataOutputStream out)
			throws SQLException, ServletException, IOException {
		final boolean isRestricted = !isUnrestricted(onItemsTable);
		@SuppressWarnings("resource")
		final PreparedStatement ps = isRestricted ? filteredCountQuery : unfilteredCountQuery;
		final List<ColumnType> columnTypes = isRestricted ? MyResultSetColumnTypeList.PINT_PINT
				: MyResultSetColumnTypeList.SMINT_PINT;
		assert ps != null;
		// synchronized (ps) {
		try (final ResultSet rs = jdbc.sqlQuery(ps);) {
			sendResultSet(rs, columnTypes, out);
			final int nRows = MyResultSet.nRowsNoThrow(rs);
			if (nRows <= 0) {
				System.out.println("Database.getFilteredCounts " + this + " nRows=" + nRows + "\n " + ps);
				errorp(dbName + " nRows=" + nRows + "\n " + ps, "getFilteredCounts");
			}
		}
	}

	protected static void getChildIDsInternal(final @NonNull StringBuilder buf, final int first_child_ID,
			final int last_child_ID_exclusive) {
		for (int childID = first_child_ID; childID < last_child_ID_exclusive; childID++) {
			if (buf.length() > 0) {
				buf.append(",");
			}
			buf.append(Integer.toString(childID));
		}
	}

	void initFacetTypes(final @NonNull DataOutputStream out) throws SQLException, ServletException, IOException {
		try (final ResultSet rs = jdbc
				// Need to join in raw_facet_type to get descriptionCategory,
				// descriptionPreposition, isOrdered.
				.sqlQuery("SELECT name, descriptionCategory, descriptionPreposition, "
						+ "n_child_facets, first_child_offset, "
						+ "is_alphabetic + 2*isOrdered FROM raw_facet_type INNER JOIN facet USING (name) "
						+ "WHERE facet.parent_facet_id = 0 ORDER BY facet.facet_id");) {
			sendResultSet(rs, MyResultSetColumnTypeList.STRING_STRING_STRING_INT_INT_INT, out);
		}
	}

	/**
	 * @return onCount
	 */
	int updateOnItems(final @NonNull String onSQL) throws SQLException {
		if (onSQL.length() == 0) {
			return 1;
		} else {
			jdbc.sqlUpdate("TRUNCATE TABLE onItems");
			return jdbc.sqlUpdate("INSERT INTO onItems " + onSQL);
		}
	}

	void prefetch(final @NonNull String parentFacetIDs, final @NonNull FetchType prefetchType,
			final @NonNull DataOutputStream out) throws SQLException, ServletException, IOException {
		prefetchInternalSendParentParameters(parentFacetIDs, out);
		prefetchInternalSendChildData(parentFacetIDs, prefetchType, out);

	}

	/**
	 * Write [children_offset, isAlphabetic]
	 */
	private void prefetchInternalSendParentParameters(final @NonNull String parentFacetIDs,
			final @NonNull DataOutputStream out) throws SQLException, IOException, ServletException {
		final String sql = "SELECT first_child_offset, is_alphabetic FROM facet WHERE"
				+ inList("facet_id", parentFacetIDs, false, true);
		try (final ResultSet rs = jdbc.sqlQuery(sql);) {
			if (MyResultSet.nRows(rs) != parentFacetIDs.split(",").length) {
				errorp("Bad nRows: " + parentFacetIDs + " " + MyResultSet.nRows(rs),
						"prefetchInternalSendParentParameters");
			}
			sendResultSet(rs, MyResultSetColumnTypeList.INT_INT, out);
		}
	}

	private void prefetchInternalSendChildData(final @NonNull String parentFacetIDs,
			final @NonNull FetchType prefetchType, final @NonNull DataOutputStream out)
			throws SQLException, ServletException, IOException {
		if (myIsRestrictedData != prefetchType.isRestrictedData()) {
			errorp("Bad FetchType: myIsRestrictedData=" + myIsRestrictedData + " but prefetchType=" + prefetchType,
					"prefetchInternalSendChildData");
		}

		String indexHint = "";
		switch (prefetchType) {
		case PREFETCH_FACET_WITH_NAME_RESTRICTED_DATA:
		case PREFETCH_FACET_TYPE_WITH_NAME:
		case PREFETCH_FACET_WITH_NAME:
			break;
		case PREFETCH_FACET_RESTRICTED_DATA:
		case PREFETCH_FACET_TYPE:
		case PREFETCH_FACET:
			indexHint = "IGNORE INDEX (parent)";
			break;

		default:
			errorp("prefetch args=" + prefetchType, "prefetchInternalSendChildData");
			return;
		}
		final String prefetchSQL = "SELECT " + prefetchType.getFields() + " FROM facet " + indexHint + " WHERE"
				+ inList("parent_facet_id", parentFacetIDs, false, false) + " ORDER BY parent_facet_id, facet_id";

		try (final ResultSet rs = jdbc.sqlQuery(prefetchSQL);) {
			sendResultSet(rs, prefetchType.getColumnTypes(), out);
		}
	}

	// private @NonNull String prefetchSQLRestricted(final @NonNull String
	// fields, final @NonNull String facetIDs) {
	// // Need LEFT JOIN and COUNT(restricted.record_num) so all children show
	// // up, even if isRestrictedData
	// return "SELECT " + fields + " FROM facet INNER JOIN item_facetNtype_heap
	// USING (facet_id)"
	// + " LEFT JOIN restricted USING (record_num) WHERE" +
	// inList("parent_facet_id", facetIDs, false, false)
	// + " GROUP BY facet_id ORDER BY facet_id";
	// }

	void getLetterOffsets(final int parentFacetID, final @NonNull String prefix, final @NonNull DataOutputStream out)
			throws SQLException, IOException, ServletException {
		assert getLetterOffsetsQuery != null;
		synchronized (getLetterOffsetsQuery) {
			getLetterOffsetsQuery.setInt(1, prefix.length() + 1);
			getLetterOffsetsQuery.setString(2, prefix + "%");
			getLetterOffsetsQuery.setInt(3, parentFacetID);
			try (ResultSet rs = jdbc.sqlQuery(getLetterOffsetsQuery);) {
				sendResultSet(rs, MyResultSetColumnTypeList.STRING_SMINT_SMINT_STRING_STRING, out);
			}
		}
	}

	void getNames(final @NonNull String facets, final @NonNull DataOutputStream out)
			throws SQLException, ServletException, IOException {
		try (final ResultSet rs = jdbc
				.sqlQuery("SELECT name FROM facet WHERE" + inList("facet_id", facets, false, true));) {
			if (MyResultSet.nRows(rs) != facets.split(",").length) {
				errorp("Wrong nRows: " + facets + "\n" + MyResultSet.valueOfDeep(rs), "getNames");
			}
			sendResultSet(rs, MyResultSetColumnTypeList.STRING, out);
		}
	}

	/**
	 * Write single-column rs of items in min/maxOffset range.
	 */
	@SuppressWarnings("resource")
	void offsetItems(final int minOffsetInclusive, final int maxOffsetExclusive,
			final @NonNull OnItemsTable onItemsTable, final @NonNull DataOutputStream out)
			throws SQLException, ServletException, IOException {
		final int expectedRows = maxOffsetExclusive - minOffsetInclusive;
		if (expectedRows <= 0) {
			errorp(minOffsetInclusive + " - " + maxOffsetExclusive, "offsetItems");
		}
		final PreparedStatement ps = offsetItemsQuerys[onItemsTable.ordinal()];
		synchronized (ps) {
			ps.setInt(1, minOffsetInclusive + (isUnrestricted(onItemsTable) ? 1 : 0));
			ps.setInt(2, expectedRows + (isUnrestricted(onItemsTable) ? minOffsetInclusive : 0));
			try (final ResultSet rs = jdbc.sqlQuery(ps);) {
				final int actualRows = MyResultSet.nRows(rs);
				if (actualRows != expectedRows) {
					final int onCount = jdbc.nRows(onItemsTable.toString());
					if (onCount <= 0) {
						errorp("Zero onCount", "offsetItems");
					}
					if (actualRows != onCount - minOffsetInclusive) {
						errorp("range=" + minOffsetInclusive + "-" + maxOffsetExclusive + ", but nRows returned="
								+ actualRows + ". onCount=" + onCount + " using table " + onItemsTable + "\n" + ps,
								"offsetItems");
					}
				}
				sendResultSet(rs, MyResultSetColumnTypeList.INT, out);
			}
		}
	}

	@SuppressWarnings("resource")
	void fixImageSizes() {
		try (final ResultSet rs = jdbc.sqlQuery("SELECT record_num, image, w, h FROM images WHERE w*h=0");) {
			final PreparedStatement ps = jdbc.lookupPS("UPDATE images SET w = ?, h = ? WHERE record_num = ?");
			while (rs.next()) {
				final int recordNum = rs.getInt("record_num");
				final BufferedImage bufferedImage = ImageIO.read(rs.getBlob("image").getBinaryStream());
				if (bufferedImage == null) {
					errorp("bufferedImage is null for db=" + dbName + ", record_num=" + recordNum, "fixImageSizes");
				}
				assert bufferedImage != null;
				jdbc.sqlUpdateWithIntArgs(ps, bufferedImage.getWidth(), bufferedImage.getHeight(), recordNum);
			}
		} catch (final SQLException | IOException e) {
			e.printStackTrace();
		}
	}

	void getThumbs(final @NonNull String items, final int imageW, final int imageH, final int quality,
			final @NonNull DataOutputStream out) throws SQLException, ServletException, IOException {
		cacheThumbs(items, imageW, imageH, quality);
		try (ResultSet rs1 = jdbc.sqlQuery("SELECT record_num, " + descriptionGetter
				+ " description, image, w, h FROM item LEFT JOIN images USING (record_num) WHERE"
				+ inList("record_num", items, false, true));
				ResultSet rs2 = jdbc.sqlQuery(
						"SELECT * FROM item_facetNtype_heap WHERE" + inList("record_num", items, false, false));) {
			Encoder.sendResultSet(rs1, MyResultSetColumnTypeList.SMINT_STRING_IMAGE_INT_INT, imageW, imageH, quality,
					out);
			sendResultSet(rs2, MyResultSetColumnTypeList.PINT_PINT, out);
		}
	}

	private @Nullable String uncachedThumbGetter() throws SQLException {
		String result = null;
		if (jdbc.nRows("item") >= edu.cmu.cs.bungee.compile.Database.MAX_THUMBS) {
			result = jdbc.sqlQueryString("SELECT thumb_URL FROM globals");
		}
		return result;
	}

	private @Nullable Pattern uncachedThumbRegex() {
		Pattern result = null;
		final String regex = jdbc.sqlQueryString("SELECT thumb_regexp FROM globals");
		result = UtilString.isNonEmptyString(regex) ? Pattern.compile(regex) : null;
		return result;
	}

	/**
	 * Ensure db has images for these items.
	 */
	private void cacheThumbs(final @NonNull String items, final int maxW, final int maxH, final int quality)
			throws SQLException {
		if (uncachedThumbGetter != null) {
			PopulateImageTable.populateImageTable(uncachedThumbGetter, uncachedThumbRegex, maxW, maxH, quality, jdbc,
					false, items);
		}
	}

	/**
	 * @param desiredImageW
	 *            ≤0 means don't retrieve an image
	 */
	void getDescAndImage(final int item, final int desiredImageW, final int desiredImageH, final int quality,
			final @NonNull DataOutputStream out) throws SQLException, ServletException, IOException {
		PreparedStatement ps;
		if (desiredImageW > 0) {
			cacheThumbs(Util.nonNull(Integer.toString(item)), desiredImageW, desiredImageH, quality);
			ps = imageQuery;
		} else {
			ps = itemDescQuery;
		}

		ps.setInt(1, item);
		try (final ResultSet rs = jdbc.sqlQuery(ps);) {
			if (MyResultSet.nRows(rs) != 1) {
				errorp("Non-unique result for " + ps, "getDescAndImage");
			}
			final List<ColumnType> columnTypeList = desiredImageW > 0 ? MyResultSetColumnTypeList.STRING_IMAGE_INT_INT
					: MyResultSetColumnTypeList.STRING;
			Encoder.sendResultSet(rs, columnTypeList, desiredImageW, desiredImageH, quality, out);
		}
	}

	/**
	 * Send [parent_facet_id, facet_id, name, n_child_facets,
	 * first_child_offset]
	 */
	void getItemInfo(final int item, final @NonNull DataOutputStream out)
			throws SQLException, ServletException, IOException {
		final PreparedStatement ps = getItemInfoQuery;

		synchronized (ps) {
			ps.setInt(1, item);
			try (final ResultSet rs = jdbc.sqlQuery(ps);) {
				sendResultSet(rs, MyResultSetColumnTypeList.SMINT_PINT_STRING_INT_INT, out);
			}
		}
	}

	void getFacetInfo(final @NonNull String facetIDs, final boolean isRestrictedData,
			final @NonNull DataOutputStream out) throws SQLException, ServletException, IOException {
		if (isRestrictedData != myIsRestrictedData) {
			errorp(isRestrictedData + " " + myIsRestrictedData, "getFacetInfo");
		}
		final String sql = "SELECT facet_id, parent_facet_id, name, n_child_facets, first_child_offset"
				+ " FROM facet WHERE" + inList("facet_id", facetIDs, false, true);
		try (final ResultSet rs = jdbc.sqlQuery(sql);) {
			if (MyResultSet.nRows(rs) != UtilString.splitComma(facetIDs).length) {
				errorp("Wrong nRows: " + facetIDs + MyResultSet.valueOfDeep(rs), "getFacetInfo");
			}
			sendResultSet(rs, MyResultSetColumnTypeList.SMINT_PINT_STRING_INT_INT, out);
		}
	}

	/**
	 * @param item
	 * @param onItemsTable
	 *            see reorderItems
	 * @return the offset into the on items table of this item. -1 means not
	 *         found (not in onItems).
	 * @throws SQLException
	 */
	@SuppressWarnings("resource")
	int itemOffset(final int item, final @NonNull OnItemsTable onItemsTable) throws SQLException {
		final int tableID = onItemsTable.ordinal();
		int offset = -1;
		final PreparedStatement ps = itemOffsetQuerys[tableID];
		synchronized (ps) {
			ps.setInt(1, item);
			offset = jdbc.sqlQueryInt(ps, true);
		}
		return offset;
	}

	/**
	 * Given facetSpecs="1,2,3" return for each candidate the counts in the
	 * 2^3-1 non-zero states where the candidate is on and at least one other is
	 * on. From that you can reconstruct the 2^4 states from the 2^3 base states
	 * and the candidate's totalCount.
	 *
	 * @param facetSpecs
	 *            list of facets, or facet ranges, to find co-occurence counts
	 *            among: [f1, f2, f3-f4, f5, ...]
	 *
	 *            11/14/2015 There are never any facet ranges!!!
	 */
	void onCountMatrix(final @NonNull String facetSpecs, final @NonNull String candidates,
			final boolean isRestrictedData, final boolean needBaseCounts, final @NonNull DataOutputStream out)
			throws SQLException, ServletException, IOException {
		if (isRestrictedData != myIsRestrictedData) {
			errorp(isRestrictedData + " " + myIsRestrictedData, "onCountMatrix");
		}
		final boolean needCandidateCounts = candidates.length() > 0;
		setOnCountMatrixFacets(facetSpecs, needCandidateCounts);
		if (needBaseCounts) {
			@SuppressWarnings("resource")
			final PreparedStatement ps = jdbc
					.lookupPS("SELECT SQL_SMALL_RESULT state, COUNT(*) FROM "
							+ (needCandidateCounts ? "on_count_matrix_items"
									: "(SELECT BIT_OR(bit) state FROM on_count_matrix_facets "
											+ "INNER JOIN item_facetNtype_heap USING (facet_id) "
											+ "GROUP BY record_num ORDER BY NULL) foo")
							+ " GROUP BY state ORDER BY NULL");
			try (final ResultSet baseCountsRS = jdbc.sqlQuery(ps);) {
				logp("Database.onCountMatrix sending baseCounts\n" + MyResultSet.valueOfDeep(baseCountsRS, 3) + "\n",
						MyLogger.FINE, "onCountMatrix");
				sendResultSet(baseCountsRS, MyResultSetColumnTypeList.PINT_PINT, out);
			}
		}
		if (needCandidateCounts) {
			try (final ResultSet candidateCounts = jdbc.sqlQuery("SELECT SQL_SMALL_RESULT facet_id, state, COUNT(*) "
					+ "FROM on_count_matrix_items INNER JOIN item_facetNtype_heap USING (record_num) WHERE"
					+ inList("facet_id", candidates, false, false) + " GROUP BY facet_id, state ORDER BY facet_id"
			// Distribution.cacheCandidateDistributionsInternal requires
			// facet_id's to be grouped together.
			// + " ORDER BY NULL"
			);) {
				logp("Sending candidateCounts\n" + MyResultSet.valueOfDeep(candidateCounts, 3) + "\n", MyLogger.FINE,
						"onCountMatrix");
				sendResultSet(candidateCounts, MyResultSetColumnTypeList.PINT_PINT_PINT, out);
			}
		}
	}

	/**
	 * facetIDranges is [f1, f2, f3-f4, f5, ...]
	 *
	 * 11/14/2015 There are never any facet ranges!!!
	 *
	 * sql is
	 * "INSERT INTO on_count_matrix_facets VALUES(f1, 1),(f2, 2),(f3, 4),(f4, 4),(f5, 8), ..."
	 *
	 * @param facetSpecs
	 * @param needCandidateCounts
	 */
	private void setOnCountMatrixFacets(final @NonNull String facetSpecs, final boolean need_on_count_matrix_items)
			throws SQLException {
		final String[] facetIDranges = UtilString.splitComma(facetSpecs);
		final int nBits = facetIDranges.length;
		if (nBits >= SQL_INT_BITS) {
			errorp("Too many facet ranges (" + facetIDranges.length + "): " + facetSpecs, "onCountMatrix");
		}
		ensureOnCountMatrixTablesAndTruncate(nBits, need_on_count_matrix_items);
		final StringBuilder buf = new StringBuilder("INSERT INTO on_count_matrix_facets VALUES");
		for (int i = 0; i < nBits; i++) {
			final String[] mexFacets = facetIDranges[i].split("-");
			final int nMexFacets = mexFacets.length;
			assert nMexFacets == 1 : facetSpecs;
			for (int j = 0; j < nMexFacets; j++) {
				if (i + j > 0) {
					buf.append(",");
				}
				buf.append("(").append(mexFacets[j]).append(", ").append((1 << i)).append(")");
			}
		}
		final String sql = buf.toString();
		assert sql != null;
		jdbc.sqlUpdate(sql);

		if (need_on_count_matrix_items) {
			jdbc.sqlUpdate(
					"INSERT INTO on_count_matrix_items SELECT record_num, BIT_OR(bit) FROM on_count_matrix_facets "
							+ "INNER JOIN item_facetNtype_heap USING (facet_id) "
							+ (myIsRestrictedData ? "INNER JOIN restricted USING (record_num) " : "")
							+ "GROUP BY record_num ORDER BY NULL");
		}
	}

	private String onCountMatrixFacetsStateIDtype = null;

	private void ensureOnCountMatrixTablesAndTruncate(final int nBits, final boolean need_on_count_matrix_items)
			throws SQLException {
		final String stateIDtype = JDBCSample.unsignedTypeForMaxBits(nBits);
		if (stateIDtype.equals(onCountMatrixFacetsStateIDtype)) {
			jdbc.sqlUpdate("TRUNCATE TABLE on_count_matrix_facets");
			if (need_on_count_matrix_items) {
				jdbc.sqlUpdate("TRUNCATE TABLE on_count_matrix_items");
			}
		} else {
			onCountMatrixFacetsStateIDtype = stateIDtype;
			dropAndCreateTemporaryHeapTable("on_count_matrix_facets",
					"facet_id " + facet_id_column_type + ", bit " + stateIDtype, "facet_id");
			if (need_on_count_matrix_items) {
				dropAndCreateTemporaryHeapTable("on_count_matrix_items",
						"record_num " + item_id_column_type + ", state " + stateIDtype, "record_num");
			}
		}
	}

	private void dropAndCreateTemporaryHeapTable(final @NonNull String table, final @NonNull String createSQL,
			final @NonNull String primaryKey) throws SQLException {
		jdbc.dropTable(table);
		jdbc.sqlUpdate(createTemporaryTable + table + " (" + createSQL + ", PRIMARY KEY (" + primaryKey
				+ ")) ENGINE=MEMORY PACK_KEYS=1 ROW_FORMAT=FIXED");
	}

	/**
	 * Sends 2 ResultSets with the same structure. First the candidates, then
	 * their ancestors.
	 */
	void topCandidates(final @NonNull String perspectiveIDexpr, final int maxCandidates,
			final @NonNull DataOutputStream out) throws SQLException, ServletException, IOException {
		if (myIsRestrictedData) {
			errorp("topCandidates needs a second case for restrictedData queries, "
					+ "because it is using facet.n_items rather than counting rows", "topCandidates");
			Encoder.writeString("Error", out);
		} else {
			final int nCandidates = jdbc
					.sqlQueryInt("SELECT COUNT(*) FROM facet WHERE " + perspectiveIDexpr.replace("?", "facet_id"));
			String sql = null;
			if (nCandidates == 0) {
				errorp("No candidates from " + perspectiveIDexpr, "topCandidates");
			} else {
				String groupHavingOrderBy;
				if (nCandidates == 1) {
					groupHavingOrderBy = "ORDER BY corr DESC";
				} else {
					groupHavingOrderBy = "GROUP BY facet_id HAVING POW(SUM(corr),2) - SUM(corr*corr) > 0 "
							+ "ORDER BY POW(SUM(corr),2) - SUM(corr*corr) DESC";
				}
				final String facet1expr = perspectiveIDexpr.replace("?", "facet1");
				final String facet2expr = perspectiveIDexpr.replace("?", "facet2");

				// UNION ALL avoids collapsing primes with equal correlation.
				sql = "SELECT facet_id FROM (SELECT facet1 facet_id, ABS(correlation) corr "
						+ "FROM correlations WHERE " + facet2expr + " UNION ALL "
						+ "SELECT facet2 facet_id, ABS(correlation) corr FROM correlations WHERE " + facet1expr
						+ ") AS facetIDs " + groupHavingOrderBy + " LIMIT " + maxCandidates;
				try (final ResultSet rs = jdbc.sqlQuery(sql); final ResultSet rs2 = nonTopLevelAncestorInfo(rs);) {
					sendResultSet(rs, MyResultSetColumnTypeList.PINT, out);
					sendResultSet(rs2, MyResultSetColumnTypeList.SMINT_PINT_STRING_INT_INT, out);
				}
			}
		}
	}

	private @NonNull ResultSet nonTopLevelAncestorInfo(final @NonNull ResultSet rs) throws SQLException {
		final Set<Integer> leafs = new HashSet<>();
		rs.beforeFirst();
		while (rs.next()) {
			final int facetID = rs.getInt("facet_id");
			if (facetID > maxFacetType) {
				leafs.add(facetID);
			}
		}
		final Set<Integer> ancestors = nonTopLevelAncestors(new HashSet<>(leafs));

		// Let's send leafs here, too, because ordering by correlation in the
		// other rs could make a child come before its parent.
		// ancestors.removeAll(leafs);
		return jdbc
				.sqlQuery("SELECT facet_id, parent_facet_id, name, n_child_facets, first_child_offset FROM facet WHERE"
						+ inList("facet_id", idsToString(ancestors), false, false) + " ORDER BY facet_id");
	}

	/**
	 * @param facetIDs
	 *            is destroyed
	 * @return facetIDs + their ancestors;
	 * @throws SQLException
	 */
	private @NonNull Set<Integer> nonTopLevelAncestors(@NonNull Set<Integer> facetIDs) throws SQLException {
		boolean isChanges = false;
		final String ids = idsToString(facetIDs);
		final String sql = "SELECT DISTINCT parent_facet_id FROM facet WHERE parent_facet_id > " + maxFacetType
				+ (ids.length() == 0 ? ""
						: " AND NOT" + inList("parent_facet_id", ids, false, false) + " AND"
								+ inList("facet_id", ids, false, false));
		try (final ResultSet rs = jdbc.sqlQuery(sql);) {
			while (rs.next()) {
				if (facetIDs.add(rs.getInt("parent_facet_id"))) {
					isChanges = true;
				} else {
					errorp(rs.getInt("parent_facet_id") + " " + ids, "nonTopLevelAncestors");
				}
			}
		}
		if (isChanges) {
			facetIDs = nonTopLevelAncestors(facetIDs);
		}
		return facetIDs;
	}

	private @NonNull static String idsToString(final @NonNull Collection<Integer> facetIDs) {
		final StringBuilder buf = new StringBuilder();
		for (final Integer facetID : facetIDs) {
			if (buf.length() > 0) {
				buf.append(",");
			}
			buf.append(facetID.toString());
		}
		return Util.nonNull(buf.toString());
	}

	void printUserAction(final @NonNull String client, final int session, final int actionIndex, final int location,
			final String object, final int modifiers, final int onCount) throws SQLException {
		assert printUserActionStmt != null;
		synchronized (printUserActionStmt) {
			printUserActionStmt.setInt(1, actionIndex);
			printUserActionStmt.setInt(2, location);
			printUserActionStmt.setString(3, object);
			printUserActionStmt.setInt(4, modifiers);
			printUserActionStmt.setInt(5, session);
			printUserActionStmt.setString(6, client);
			printUserActionStmt.setInt(7, onCount);
			jdbc.sqlUpdateWithIntArgs(printUserActionStmt);
		}
	}

	private void ensureUserActionsOncountColumn() throws SQLException {
		if (!jdbc.columnExists("user_actions", "onCount")) {
			final int nItems = jdbc.sqlQueryInt("SELECT COUNT(*) FROM item");
			String type = JDBCSample.unsignedTypeForMaxValue(2 * nItems);
			type = type.substring(0, type.length() - " UNSIGNED NOT NULL".length());
			type += " NOT NULL DEFAULT -1";
			jdbc.sqlUpdate("ALTER TABLE user_actions ADD COLUMN onCount " + type);
		}
	}

	void restrict() throws SQLException {
		myIsRestrictedData = true;
		jdbc.sqlUpdate("TRUNCATE TABLE restricted");
		jdbc.sqlUpdate("INSERT INTO restricted SELECT * FROM onItems");
	}

	/**
	 * @return " " + column + " IN (" + ids + ") ORDER BY " + column
	 */
	private String inList(final @NonNull String column, final @NonNull String ids, final boolean isGroupBy,
			final boolean isOrderBy) {
		if (ids.length() == 0) {
			errorp("ids is empty", "inList");
		}
		String result = " " + column + " IN (" + ids + ")";
		if (isGroupBy) {
			result += " GROUP BY " + column;
		}
		if (isOrderBy) {
			result += " ORDER BY " + column;
		}
		return result;
	}

	private static void sendResultSet(final ResultSet rs, final List<ColumnType> columnTypes,
			final DataOutputStream out) throws ServletException, SQLException, IOException {
		Encoder.sendResultSet(rs, columnTypes, out);
	}

	void setItemDescription(final int item, final String displayText) throws SQLException {
		final String[] values = displayText.split("(?:\\n|\\r)+<\\w+>(?:\\n|\\r)+");
		String fieldNames = jdbc.sqlQueryString("SELECT itemDescriptionFields FROM globals");
		final String[] fields = fieldNames.split(",");
		final Pattern p = Pattern.compile("(?:\\n|\\r)+<(\\w+)>(?:\\n|\\r)+");
		final Matcher m = p.matcher(displayText);
		int valueIndex = 1;
		while (m.find()) {
			final String fieldName = m.group(1);
			if (!ArrayUtils.contains(fields, fieldName)) {
				fieldNames += "," + fieldName;
				jdbc.sqlUpdate("UPDATE globals SET itemDescriptionFields = " + JDBCSample.quoteForSQL(fieldNames));
				jdbc.sqlUpdate("ALTER TABLE item ADD COLUMN `" + fieldName + "` TEXT DEFAULT NULL");
			}
			final String value = values[valueIndex++];
			jdbc.sqlUpdate("UPDATE item SET " + fieldName + " = " + JDBCSample.quoteForSQL(value)
					+ " WHERE record_num = " + item);
		}
	}

	/**
	 * session's first op has different onCount than totalCount. Remove all
	 * oncount's.
	 *
	 * @throws SQLException
	 */
	public void loseSession(final int session) throws SQLException {
		@SuppressWarnings("resource")
		final PreparedStatement ps = jdbc.lookupPS("UPDATE user_actions SET onCount=-1 WHERE session = ?");
		ps.setInt(1, session);
		jdbc.sqlUpdate(ps);
	}

	@SuppressWarnings("resource")
	void opsSpec(final int session, final DataOutputStream out) throws SQLException, ServletException, IOException {
		// order by timestamp for sessions that predate action_numbers
		final PreparedStatement ps = jdbc.lookupPS("SELECT CONCAT_WS(',',"
				+ " TIMESTAMPDIFF(SECOND, (SELECT MIN(timestamp) FROM user_actions WHERE session = ?),"
				+ " timestamp), location, object, modifiers, oncount) FROM user_actions"
				+ " WHERE session = ? ORDER BY action_number, timestamp");
		ps.setInt(1, session);
		ps.setInt(2, session);
		try (final ResultSet rs = jdbc.sqlQuery(ps);) {
			sendResultSet(rs, MyResultSetColumnTypeList.STRING, out);
		}
	}

	void randomOpsSpec(final DataOutputStream out) throws SQLException, IOException {
		final int session = jdbc.sqlQueryInt("SELECT session FROM user_actions ORDER BY RAND() LIMIT 1");
		Encoder.writeString(Integer.toString(session), out);
	}

	String dbDescs(final @NonNull String dbNameList) {
		if (!UtilString.isNonEmptyString(dbNameList)) {
			errorp("Empty db name list", "dbDescs");
		}
		final String[] dbNames = UtilString.splitComma(dbNameList);
		final StringBuilder dbDescs = new StringBuilder();
		for (final String dbName2 : dbNames) {
			dbDescsInternal(dbDescs, dbName2);
		}
		if (!ArrayUtils.contains(dbNames, dbName)) {
			// If URL specified a "hidden" database, get its description too.
			dbDescsInternal(dbDescs, dbName);
		}
		return dbDescs.toString();
	}

	private void dbDescsInternal(final StringBuilder dbDescs, final String name) {
		final String desc = jdbc.sqlQueryString("SELECT description FROM " + name + ".globals");
		if (dbDescs.length() > 0) {
			dbDescs.append(";");
		}
		dbDescs.append(name).append(",").append(desc);
	}

	boolean isCorrelations() throws SQLException {
		return jdbc.tableExists("correlations");
	}

	@Override
	public String toString() {
		return UtilString.toString(this, dbName);
	}

}
