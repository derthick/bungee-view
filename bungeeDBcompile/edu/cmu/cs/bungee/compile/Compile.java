package edu.cmu.cs.bungee.compile;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.FormattedTableBuilder;
//import edu.cmu.cs.bungee.javaExtensions.FormattedTableBuilder.Justification;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyLogger;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.ApplicationSettings;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.StringToken;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.Token;

public class Compile {

	public static final @NonNull String FACET_HIERARCHY_SEPARATOR = " -- ";

	private static final int MAX_ERRORS_TO_PRINT = 8;
	private static final int MIN_FACETS = 1000;
	private static final double MAX_CORRELATION_QUERY_INTERMEDIATE_ROWS = 2_000_000_000;
	private static final int MAX_HEAP_TABLE_ROWS = 1_000_000;

	private static final String[] TEMPORARY_TABLES = { "relevantFacets", "onItems", "restricted", "renames",
			"random_item_ID", "duplicates", "temp", "rawItemCounts" };

	private final @NonNull JDBCSample jdbc;
	private final Database db;

	private String item_idType;
	private String countType;
	private String facet_idType;

	private static final StringToken SM_DB = new StringToken("db", "database to compile", "",
			Token.optRequired | Token.optSwitch, "");
	private static final StringToken SM_SERVER = new StringToken("servre", "MySQL server", "", Token.optSwitch,
			"jdbc:mysql://localhost/");
	private static final StringToken SM_USER = new StringToken("user", "MySQL user", "", Token.optSwitch, "bungee");
	private static final StringToken SM_PASS = new StringToken("pass", "MySQL user password", "", Token.optSwitch,
			"p5pass");
	private static final ApplicationSettings SM_MAIN = new ApplicationSettings();

	static {
		// try {
		SM_MAIN.addToken(SM_DB);
		SM_MAIN.addToken(SM_SERVER);
		SM_MAIN.addToken(SM_USER);
		SM_MAIN.addToken(SM_PASS);
		// } catch (final Throwable t) {
		// JDBCSample.logp("Got error", MyLogger.SEVERE, t, "static
		// initialization");
		// throw t;
		// }
	}

	public static void main(final String[] args) throws SQLException {
		if (SM_MAIN.parseArgs(args)) {
			final Compile compile = new Compile(Util.nonNull(SM_SERVER.getValue()), Util.nonNull(SM_DB.getValue()),
					Util.nonNull(SM_USER.getValue()), Util.nonNull(SM_PASS.getValue()));
			// final Level oldLevel = MyLogger.getLevel();
			// MyLogger.setLevel(MyLogger.INFO);

			compile.compile(false);
			compile.jdbc.close();

			// MyLogger.setLevel(oldLevel);
		}
	}

	public static void maybeCreateCorrelationsTable(final @NonNull JDBCSample jdbc) throws SQLException {
		final Compile compile = new Compile(jdbc);
		compile.maybeCreateCorrelationsTable(false);
	}

	private Compile(final @NonNull String _server, final @NonNull String _db, final @NonNull String _user,
			final @NonNull String _pass) throws SQLException {
		this(JDBCSample.getMySqlJDBCSample(_server, _db, _user, _pass));
	}

	public Compile(final @NonNull JDBCSample _jdbc) throws SQLException {
		jdbc = _jdbc;
		db = new Database(jdbc, false);
	}

	public Compile(final Database _db) {
		db = _db;
		jdbc = db.getJdbc();
	}

	public void compile(final boolean dontComputeCorrelations) throws SQLException {
		UtilString.printDateNmessage(
				"Compile.compile " + jdbc.dbName + " dontComputeCorrelations=" + dontComputeCorrelations);
		jdbc.setCollation(JDBCSample.COLLATE);
		dropObsoleteTables(jdbc);
		while (checkRawErrors() > 0) {
			// After all, tomorrow is another day.
		}
		summarize();
		createTemp();
		computeIDs();
		computeIDtypes();
		createFacet();
		createItemFacet();
		createItemOrder();
		createUserActions();
		setUpFacetSearch();
		checkOutputTables();
		maybeCreateCorrelationsTable(dontComputeCorrelations);
		jdbc.setCollation(JDBCSample.COLLATE);
		cleanUp();
		UtilString.printDateNmessage("Compile.compile Done.\n");
	}

	private void checkOutputTables() throws SQLException {
		UtilString.printDateNmessage("Checking output tables for errors...");
		final int nErrors = findBrokenLinks(false);
		UtilString.printDateNmessage("...found " + nErrors + " errors in output tables.");
	}

	private void cleanUp() throws SQLException {
		UtilString.printDateNmessage("Cleaning up...");
		jdbc.dropAndCreateTable("facet_map", "AS SELECT facet_id raw_facet_id, canonical_facet_id, sort"
				+ " FROM temp INNER JOIN raw_facet USING (facet_id)");
		jdbc.sqlUpdate("ALTER TABLE facet_map" + " ADD PRIMARY KEY (canonical_facet_id)");

		// In case we've created non-temporary versions for debugging
		for (final String tempTable : TEMPORARY_TABLES) {
			jdbc.dropTable(tempTable);
		}
		jdbc.sqlUpdate("DROP INDEX `PRIMARY` ON raw_item_facet");
		jdbc.sqlUpdate("DROP INDEX `facet` ON raw_item_facet");
		optimizeTables();
	}

	private void maybeCreateCorrelationsTable(boolean dontComputeCorrelations) throws SQLException {
		if (!dontComputeCorrelations) {
			// Use doubles to avoid overflow
			final double nItemFacets = jdbc.nRows("item_facetNtype_heap");
			final double nItemsPerFacet = nItemFacets / jdbc.nRows("facet");
			dontComputeCorrelations = nItemFacets * nItemsPerFacet > MAX_CORRELATION_QUERY_INTERMEDIATE_ROWS;
			if (dontComputeCorrelations) {
				System.err.println("Not computing correlations because " + jdbc.dbName + " nItemFacets=" + nItemFacets
						+ " and nItemsPerFacet=" + nItemsPerFacet + "; product is " + (nItemFacets * nItemsPerFacet)
						+ " vs. MAX_CORRELATION_QUERY_INTERMEDIATE_ROWS=" + MAX_CORRELATION_QUERY_INTERMEDIATE_ROWS);
			}
		}
		if (!dontComputeCorrelations) {
			createCorrelationsTable();
			populateCorrelations();
		} else {
			jdbc.dropTable("correlations");
		}
	}

	private void createCorrelationsTable() throws SQLException {
		if (facet_idType == null) {
			final int maxFacetID = jdbc.sqlQueryInt("SELECT MAX(facet_id) FROM facet");
			facet_idType = JDBCSample.unsignedTypeForMaxValue(Math.max(MIN_FACETS, maxFacetID)) + " NOT NULL";
		}
		jdbc.dropAndCreateTable("correlations",
				"(facet1 " + facet_idType + ",  facet2 " + facet_idType + ",  correlation FLOAT NOT NULL, "
						+ "KEY facet1 (facet1), KEY facet2 (facet2))" + "ENGINE=MyISAM PACK_KEYS=1 ROW_FORMAT=FIXED");
	}

	private void populateCorrelations() throws SQLException {
		UtilString.printDateNmessage("Finding pair correlations...");
		jdbc.sqlUpdate("DROP FUNCTION IF EXISTS correlation");
		final int nItems = jdbc.sqlQueryInt("SELECT COUNT(*) FROM item");
		jdbc.sqlUpdate("CREATE FUNCTION correlation(n1 INT, n2 INT, n12 INT) " + " RETURNS FLOAT DETERMINISTIC NO SQL "
				+ "BEGIN " + " DECLARE p1 FLOAT DEFAULT n1/" + nItems + "; " + " DECLARE p2 FLOAT DEFAULT n2/" + nItems
				+ "; " + " DECLARE p12 FLOAT DEFAULT n12/" + nItems + "; " + " DECLARE num FLOAT DEFAULT p12 - p1*p2; "
				+ " RETURN IF(num=0, 0, num/SQRT((p1-p1*p1)*(p2-p2*p2))); " + "END");
		// ensureDBinitted();
		final String[][] itemFacetTablePairs = { { "item_facetNtype_heap", "item_facetNtype_heap" }, };
		for (final String[] itemFacetTablePair : itemFacetTablePairs) {
			jdbc.sqlUpdate("INSERT INTO correlations " + "SELECT itemFacetTable1.facet_id, itemFacetTable2.facet_id,"
					+ " correlation(facet1.n_items, facet2.n_items, COUNT(*)) correlation " + "FROM "
					+ itemFacetTablePair[0] + " itemFacetTable1 INNER JOIN " + itemFacetTablePair[1]
					+ " itemFacetTable2 USING (record_num) "
					+ "INNER JOIN facet facet1 ON facet1.facet_id = itemFacetTable1.facet_id "
					+ "INNER JOIN facet facet2 ON facet2.facet_id = itemFacetTable2.facet_id "
					+ "WHERE itemFacetTable1.facet_id < itemFacetTable2.facet_id "
					+ "GROUP BY itemFacetTable1.facet_id, itemFacetTable2.facet_id " + "HAVING correlation != 0");
		}
		jdbc.sqlUpdate("DROP FUNCTION correlation");
	}

	private void computeIDtypes() throws SQLException {
		final boolean isTemp = jdbc.tableExists("temp");
		final int maxItemID = jdbc.sqlQueryInt("SELECT MAX(record_num) FROM item");
		item_idType = JDBCSample.unsignedTypeForMaxValue(maxItemID);
		final int nCounts = jdbc.sqlQueryInt("SELECT MAX(cnt) FROM rawItemCounts"
		// + "INNER JOIN raw_facet_type ft ON f.facet_type_idxx =
		// ft.facet_type_id "
		// + "WHERE ft.sort > 0"
		);
		countType = JDBCSample.unsignedTypeForMaxValue(nCounts);
		final int maxFacetID = jdbc.sqlQueryInt(
				isTemp ? "SELECT MAX(canonical_facet_id) FROM temp" : "SELECT MAX(facet_id) FROM raw_facet");
		facet_idType = JDBCSample.unsignedTypeForMaxValue(Math.max(MIN_FACETS, maxFacetID)) + " NOT NULL";
		final FormattedTableBuilder align = new FormattedTableBuilder();

		// final DecimalFormat myFormatter = new DecimalFormat("###,###,##0");
		// align.addLine("countType:", myFormatter.format(nCounts), countType);
		// align.addLine("tagType:", myFormatter.format(maxFacetID),
		// facet_idType);
		// align.addLine("itemType:", myFormatter.format(maxItemID),
		// item_idType);

		align.addLine("countType:", nCounts, countType);
		align.addLine("tagType:", maxFacetID, facet_idType);
		align.addLine("itemType:", maxItemID, item_idType);
		// align.setColumnJustification(0, Justification.RIGHT);
		// align.setColumnJustification(1, Justification.RIGHT);
		System.out.println(align.format());
	}

	private void optimizeTables() throws SQLException {
		final String[] tables = "correlations, facet, images, item, item_facet, item_order, user_actions"
				// , raw_facet, raw_facet_type, raw_item_facet
				.split(",");
		for (final String table : tables) {
			// correlations may not exist
			if (jdbc.tableExists(table)) {
				UtilString.printDateNmessage("Optimizing " + table);
				jdbc.sqlUpdate("OPTIMIZE TABLE " + table);
			}
		}
		UtilString.printDateNmessage("Optimizing done.");
	}

	public static void dropObsoleteTables(final @NonNull JDBCSample jdbc) throws SQLException {
		final String[] obsolete = { "pairs", "temp", "tetrad_facets", "tetrad_items", "ancestor" };
		for (final String table : obsolete) {
			jdbc.dropTable(table);
		}
	}

	private static final @NonNull String SUMMARIZE_SQL = "SELECT IF(sort < 0 OR nChildren < 1, '  no', '  yes') include, descendents,"
			+ "nChildren n_children, name, minTag, maxTag FROM"
			+ " (SELECT raw_facet_type.sort, IF(raw_facet.parent_facet_id IS NULL, 0, COUNT(*)) nChildren,"
			+ " IF(descendents IS NULL, 0, descendents) descendents," + " raw_facet_type.name name,"
			// +
			// " SUBSTRING_INDEX(GROUP_CONCAT(raw_facet.name SEPARATOR '%$%'),
			// '%$%', 3) example"
			+ " SUBSTRING_INDEX(GROUP_CONCAT(raw_facet.name ORDER BY raw_facet.sort, raw_facet.name SEPARATOR '%$%'), '%$%', 1) minTag,"
			+ " SUBSTRING_INDEX(GROUP_CONCAT(raw_facet.name ORDER BY raw_facet.sort DESC, raw_facet.name DESC SEPARATOR '%$%'), '%$%', 1) maxTag"
			// + " LEFT(MIN(raw_facet.name), 20) minTag,"
			// + " LEFT(MAX(raw_facet.name), 20) maxTag"
			+ " FROM raw_facet_type"
			+ " LEFT JOIN raw_facet ON raw_facet.parent_facet_id = raw_facet_type.facet_type_id"
			+ " LEFT JOIN FacetTypeDescendents ON raw_facet_type.facet_type_id = FacetTypeDescendents.facet_type_id"
			+ " GROUP BY raw_facet_type.facet_type_id) foo " + "ORDER BY sort";

	private void summarize() throws SQLException {
		createFacet2FacetType();
		System.out.println("\n\n\n\n");
		printErrors(SUMMARIZE_SQL, "Summary:", "Include?,Descendents,Children,Facet,Min Child,Max Child\n",
				Integer.MAX_VALUE, Integer.MAX_VALUE);
		System.out.println("\n\n\n\n");
		jdbc.dropTable("Facet2FacetType");
		jdbc.dropTable("FacetTypeDescendents");
	}

	private void createFacet2FacetType() throws SQLException {
		jdbc.dropAndCreateTable("Facet2FacetType", "(facet_id INT UNSIGNED NOT NULL DEFAULT 0,"
				+ " facet_type_id INT UNSIGNED NOT NULL DEFAULT 0," + " PRIMARY KEY (facet_id)" + " ) ENGINE=MyISAM");
		@SuppressWarnings("resource")
		final PreparedStatement ps = jdbc.lookupPS("INSERT INTO Facet2FacetType VALUES (?, ?)");
		try (ResultSet rs = jdbc.sqlQuery("SELECT facet_id FROM raw_facet");) {
			while (rs.next()) {
				final int facet_id = rs.getInt(1);
				final int facet_type_ID = db.getRawFacetType(facet_id);
				jdbc.sqlUpdateWithIntArgs(ps, facet_id, facet_type_ID);
			}
		}
		jdbc.dropAndCreateTable("FacetTypeDescendents", "(facet_type_id INT UNSIGNED NOT NULL DEFAULT 0,"
				+ "descendents INT UNSIGNED NOT NULL DEFAULT 0," + " PRIMARY KEY (facet_type_id)" + " ) ENGINE=MyISAM");
		jdbc.sqlUpdate(
				"INSERT INTO FacetTypeDescendents (SELECT facet_type_id, COUNT(*) FROM Facet2FacetType GROUP BY facet_type_id)");
	}

	private void createTemp() throws SQLException {
		// System.out.println("Creating temp...");

		// Can't make this temporary, or you get "can't reopen table foo"
		// errors.
		jdbc.dropAndCreateTable("temp", "(facet_id INT UNSIGNED NOT NULL DEFAULT 0," + " name VARCHAR(255) NOT NULL,"
		// Increasing varcharsize above 255 slows things waaaay
		// down
				+ " parent_facet_id INT UNSIGNED NOT NULL DEFAULT 0,"
				+ " canonical_facet_id INT UNSIGNED NOT NULL DEFAULT 0," + " n_child_facets INT NOT NULL DEFAULT 0,"
				+ " children_offset INT NOT NULL DEFAULT 0,"
				+ " PRIMARY KEY (facet_id), KEY parent_facet_id (parent_facet_id )"
				// + ", KEY name (name)"
				+ " ) ENGINE=Memory");
	}

	/**
	 * temp is: facet_id, name, parent_facet_id, canonical_facet_id,
	 * n_child_facets, children_offset
	 */
	private void computeIDs() throws SQLException {
		jdbc.sqlUpdate("DROP PROCEDURE IF EXISTS computeIDs");
		jdbc.sqlUpdate(
				"CREATE PROCEDURE computeIDs(parent INT, grandparent INT, canonicalID INT, parent_name VARCHAR(255)) "
						+ "BEGIN " + " DECLARE done INT DEFAULT 0; "
						+ " DECLARE child, nChildren, childOffset INT DEFAULT 0; "
						+ " DECLARE child_name VARCHAR(255); "
						+ " DECLARE cur CURSOR FOR SELECT facet_id, name FROM raw_facet"
						+ " WHERE parent_facet_id = parent ORDER BY sort, name;"
						+ " DECLARE CONTINUE HANDLER FOR SQLSTATE '02000' SET done = 1; "
						+ " SELECT COUNT(*) INTO nChildren FROM raw_facet WHERE parent_facet_id = parent; "
						+ " SET childOffset = @child_offset; "
						+ " INSERT INTO temp VALUES(parent, parent_name, grandparent, canonicalID, nChildren, childOffset);"
						+ " SET @child_offset = @child_offset + nChildren; " + " OPEN cur; " + " REPEAT "
						+ "  FETCH cur INTO child, child_name; " + "  IF NOT done THEN "
						+ "   SET childOffset = childOffset + 1; "
						+ "   CALL computeIDs(child, parent, childOffset, child_name);" + "  END IF; "
						+ " UNTIL done END REPEAT; " + " CLOSE cur; " + "END ");

		// Skip facet_types that have no children.
		try (final ResultSet rs0 = jdbc.sqlQuery(facetTypesWithNchildren("= 0"));
				final ResultSet rs = jdbc.sqlQuery(facetTypesWithNchildren("> 0"));) {
			if (MyResultSet.nRows(rs0) > 0) {
				System.err.println("Warning: raw_facet_type with no children:\n" + MyResultSet.valueOfDeep(rs0));
			}
			final int nFacetTypes = MyResultSet.nRows(rs);
			jdbc.sqlUpdate("SET @child_offset = " + nFacetTypes + ", max_sp_recursion_depth = 100;");
			int ID = 1;
			@SuppressWarnings("resource")
			final PreparedStatement computeIDs = jdbc.lookupPS("CALL computeIDs(?, 0, ?, ?)");

			while (rs.next()) {
				// for each facetType
				computeIDs.setInt(1, rs.getInt("facet_type_id"));
				computeIDs.setInt(2, ID++);
				computeIDs.setString(3, rs.getString("name"));
				jdbc.sqlUpdate(computeIDs);
			}
		}
		jdbc.sqlUpdate("DROP PROCEDURE computeIDs");
	}

	private static @NonNull String facetTypesWithNchildren(final @NonNull String nChildrenPredicate) {
		return "SELECT facet_type_id, raw_facet_type.name " + "FROM raw_facet_type "
				+ "INNER JOIN raw_facet ON parent_facet_id = facet_type_id " + "WHERE raw_facet_type.sort >= 0 "
				+ "GROUP BY facet_type_id " + "HAVING COUNT(*) " + nChildrenPredicate + " ORDER BY raw_facet_type.sort";
	}

	/**
	 * For every combination of
	 *
	 * grandparent -- nonrestrictingFacet -- grandchild
	 *
	 * (where nonrestrictingFacet.totalCount == grandparent.totalCount and
	 * nonrestrictingFacet has no siblings)
	 *
	 * Delete nonrestrictingFacet and make each grandchild a direct child of
	 * grandparent.
	 */
	int promoteNonrestrictingFacets() throws SQLException {
		UtilString.printDateNmessage("Finding non-restrictive facets...");
		// fixMissingItemFacets();
		// findBrokenLinks();
		computeRawItemCounts();
		computeIDtypes();

		return promoteNonrestrictingFacetsInternal();

		// computeIDtypes uses rawItemCounts; compile drops it
		// jdbc.sqlUpdate("DROP TABLE rawItemCounts");
	}

	private static final @NonNull String NON_RESTRICTING_FACETS_QUERY = "(SELECT grandparent.facet_id AS grandparentID,"
			+ " nonrestrictingFacet.facet_id AS nonrestrictingFacetID,"
			+ " nonrestrictingFacet.name AS nonrestrictingFacetName, grandparent.sort AS grandparentSort"
			+ " FROM raw_facet nonrestrictingFacet"
			+ " INNER JOIN raw_facet grandparent ON nonrestrictingFacet.parent_facet_id = grandparent.facet_id"
			+ " INNER JOIN rawItemCounts nonrestrictingFacetCounts ON nonrestrictingFacet.facet_id = nonrestrictingFacetCounts.facet_id"
			+ " INNER JOIN rawItemCounts grandparentCounts ON grandparent.facet_id = grandparentCounts.facet_id"
			+ " INNER JOIN raw_facetNtype_nChildren grandparentNchildren ON grandparent.facet_id = grandparentNchildren.facet_id"
			+ " WHERE grandparentCounts.cnt = nonrestrictingFacetCounts.cnt"
			+ " AND grandparentNchildren.nChildren = 1)"

			+ " UNION "

			+ "(SELECT grandparent.facet_type_id AS grandparentID,"
			+ " nonrestrictingFacet.facet_id AS nonrestrictingFacetID,"
			+ " nonrestrictingFacet.name AS nonrestrictingFacetName, grandparent.sort AS grandparentSort"
			+ " FROM raw_facet nonrestrictingFacet"
			+ " INNER JOIN raw_facet_type grandparent ON nonrestrictingFacet.parent_facet_id = grandparent.facet_type_id"
			+ " INNER JOIN rawItemCounts nonrestrictingFacetCounts ON nonrestrictingFacet.facet_id = nonrestrictingFacetCounts.facet_id"
			+ " INNER JOIN rawItemCounts grandparentCounts ON grandparent.facet_type_id = grandparentCounts.facet_id"
			+ " INNER JOIN raw_facetNtype_nChildren grandparentNchildren ON grandparent.facet_type_id = grandparentNchildren.facet_id"
			+ " WHERE grandparentCounts.cnt = nonrestrictingFacetCounts.cnt"
			+ " AND grandparentNchildren.nChildren = 1)"

			// See whether this order eliminates the need to recurse
			+ " ORDER BY nonrestrictingFacetID DESC";

	@SuppressWarnings("resource")
	private int promoteNonrestrictingFacetsInternal() throws SQLException {
		final int nExternalChanges = db.mergeDuplicateFacets();
		int nChanges = 0;
		jdbc.dropAndCreateTable("raw_facetNtype_nChildren",
				"(facet_id " + facet_idType + ", nChildren " + item_idType + ", PRIMARY KEY (facet_id)) ENGINE=Memory");
		jdbc.sqlUpdate("INSERT INTO raw_facetNtype_nChildren (SELECT parent_facet_id, COUNT(*) FROM raw_facet"
				+ " GROUP BY parent_facet_id)");

		final PreparedStatement setSortPS = db.lookupPS("UPDATE raw_facet SET sort = ? WHERE parent_facet_id = ?");
		final PreparedStatement getChildrenPS = db.lookupPS("SELECT facet_id FROM raw_facet WHERE parent_facet_id = ?");
		try (final ResultSet rs = jdbc.sqlQuery(NON_RESTRICTING_FACETS_QUERY);) {
			printErrors(rs, "\nParent tag has exactly the same items as child",
					"Parent ID,Child ID,ChildName,Parent Sort");
			while (rs.next()) {
				final int nonrestrictingFacetID = rs.getInt("nonrestrictingFacetID");
				final int grandparentID = rs.getInt("grandparentID");
				if (!db.facetOrTypeExists(grandparentID)) {
					// db.log("Can't promote " + grandparentID + "."
					// + nonrestrictingFacetID + " because "
					// + grandparentID + " has been deleted.");
				} else if (!db.facetOrTypeExists(nonrestrictingFacetID)) {
					// db.log("Can't promote " + grandparentID + "."
					// + nonrestrictingFacetID + " because "
					// + nonrestrictingFacetID + " has been deleted.");
				} else {
					setSortPS.setString(1, rs.getString("grandparentSort"));
					setSortPS.setInt(2, nonrestrictingFacetID);
					db.updateOrPrint(setSortPS, "Update sort");

					getChildrenPS.setInt(1, nonrestrictingFacetID);
					try (final ResultSet getChildrenRS = jdbc.sqlQuery(getChildrenPS);) {
						if (MyResultSet.nRows(getChildrenRS) > 0) {
							while (getChildrenRS.next()) {
								final int childID = getChildrenRS.getInt("facet_id");
								final String originalChildAncestors = db.rawAncestorString(childID);
								db.log("Promoting nonrestrictingFacet's child " + originalChildAncestors
										+ "\n                                to be "
										+ db.rawAncestorString(grandparentID) + "'s child...");
								db.setParent(childID, grandparentID);
								db.log("...Promoted nonrestrictingFacet's child " + originalChildAncestors
										+ "\n                                   to " + db.rawAncestorString(childID));
							}
						} else {
							final String nonrestrictingFacetPath = db.rawAncestorString(nonrestrictingFacetID);
							// If grandparent is a top-level facet, setName is a
							// no-op.
							db.setName(grandparentID, rs.getString("nonrestrictingFacetName"));
							db.log("Promoting nonrestrictingFacet " + nonrestrictingFacetPath
									+ "\n                           to " + db.rawAncestorString(grandparentID));
						}
						db.deleteFacet(nonrestrictingFacetID);
						nChanges++;
					}
				}
			}
			if (nChanges > 0) {
				UtilString.printDateNmessage("PopulateHandler.promoteNonrestrictingFacetsInternal deleted " + nChanges
						+ " redundant facets; recursing");
				// assert fixMissingItemFacets() == 0; // too slow
				nChanges += promoteNonrestrictingFacetsInternal();
			}
		}
		jdbc.dropTable("raw_facetNtype_nChildren");
		return nExternalChanges + nChanges;
	}

	/**
	 * Populate rawItemCounts with counts for both raw_facet's and
	 * raw_facet_type's.
	 */
	void computeRawItemCounts() throws SQLException {
		// UtilString.printDateNmessage("Computing item counts...");

		jdbc.dropAndCreateTable("rawItemCounts", "(facet_id INT UNSIGNED NOT NULL," + " cnt INT UNSIGNED NOT NULL,"
				+ " PRIMARY KEY (facet_id)" + ") ENGINE=Memory");

		jdbc.sqlUpdate("INSERT INTO rawItemCounts" + " SELECT raw_item_facet.facet_id, COUNT(*) cnt"
				+ " FROM raw_item_facet" + " INNER JOIN raw_facet ON raw_facet.facet_id = raw_item_facet.facet_id"
				+ " GROUP BY facet_id");

		jdbc.sqlUpdate("INSERT INTO rawItemCounts" + " SELECT facet_type_id, COUNT(DISTINCT record_num) cnt"
				+ " FROM raw_item_facet" + " INNER JOIN raw_facet USING (facet_id)"
				+ " INNER JOIN raw_facet_type type ON type.facet_type_id = raw_facet.parent_facet_id"
				+ " GROUP BY facet_type_id");
	}

	private void createFacet() throws SQLException {
		UtilString.printDateNmessage("Creating facet...");
		// facet_idType += " NOT NULL";

		jdbc.dropAndCreateTable("facet", "(facet_id " + facet_idType

				+ ", name VARCHAR(200) NOT NULL, "

				+ "parent_facet_id " + facet_idType

				+ ", n_items " + countType

				+ ", n_child_facets " + facet_idType

				+ ", first_child_offset " + facet_idType

				+ ", is_alphabetic BIT NOT NULL, "

				+ "PRIMARY KEY (facet_id), KEY parent USING BTREE (parent_facet_id) )" + "ENGINE=MyISAM PACK_KEYS=1");

		// parent.canonical_facet_id and cnt will be NULL for facet types.
		jdbc.sqlUpdate("INSERT INTO facet " + "SELECT" + " child.canonical_facet_id AS facet_id," + " child.name,"
				+ " IFNULL(parent.canonical_facet_id, 0) AS parent_facet_id," + " IFNULL(cnt, 0) AS n_items,"
				+ " child.n_child_facets," + " IF(child.n_child_facets > 0, child.children_offset, 0),"
				+ " TRUE AS is_alphabetic "

				+ "FROM (temp child LEFT JOIN rawItemCounts USING (facet_id)) "
				+ "LEFT JOIN temp parent ON child.parent_facet_id = parent.facet_id");

		// compute is_alphabetic
		jdbc.sqlUpdate(" UPDATE facet," + " (SELECT DISTINCT parent.facet_id unalph"
				+ " FROM facet child1, facet child2, facet parent" + " WHERE child1.parent_facet_id = parent.facet_id "
				+ " AND child2.parent_facet_id = parent.facet_id " + " AND child2.facet_id = child1.facet_id + 1 "
				+ " AND child2.name < child1.name) foo" + " SET is_alphabetic = FALSE WHERE facet_id = unalph");
	}

	/**
	 * Create and populate item_facet (doesn't include facet_types).
	 *
	 * Create and populate item_facetNtype_heap.
	 *
	 * Compute n_items for facet_types.
	 */
	private void createItemFacet() throws SQLException {
		UtilString.printDateNmessage("Creating itemFacet...");
		createItemFacetTable("MyISAM", "item_facet");

		jdbc.sqlUpdate("INSERT INTO item_facet SELECT record_num," + " canonical_facet_id AS facet_id"
				+ " FROM raw_item_facet INNER JOIN temp USING (facet_id)"
		// + " WHERE canonical_facet_id > 0"
		);

		createItemFacetHeap();

		UtilString.printDateNmessage("Populating item_facetNtype_heap...");
		populateHeapTable(jdbc, "item_facetNtype_heap", "item_facet");

		UtilString.printDateNmessage("Computing facet counts...");
		jdbc.sqlUpdate("UPDATE facet SET n_items = "
				+ "(SELECT COUNT(*) FROM item_facetNtype_heap WHERE item_facetNtype_heap.facet_id = facet.facet_id) "
				+ "WHERE facet.parent_facet_id = 0");
	}

	/**
	 * Use ENGINE=Memory if it will fit
	 */
	private void createItemFacetHeap() throws SQLException {
		UtilString.printDateNmessage("Creating MEMORY table item_facetNtype_heap...");
		createItemFacetTable("MEMORY", "item_facetNtype_heap");
		final int itemFacetNbytes = jdbc.sqlQueryInt("SELECT data_length FROM information_schema.TABLES"
				+ " WHERE table_schema = '" + jdbc.dbName + "' AND table_name = 'item_facet'");

		final int maxNbytes = jdbc.sqlQueryInt("SELECT max_data_length FROM information_schema.TABLES"
				+ " WHERE table_schema = '" + jdbc.dbName + "' AND table_name = 'item_facetNtype_heap'");

		// System.out.println("Compile.createItemFacetHeap max data size="
		// + UtilString.addCommas(maxNbytes)
		// + " estimated itemFacetNbytes="
		// + UtilString.addCommas(2 * itemFacetNbytes));

		// 2 * allows for addition of facet_type relationships
		if (2 * itemFacetNbytes > maxNbytes) {
			UtilString.printDateNmessage("OOPS, too big. Creating VIEW item_facetNtype_heap...");
			jdbc.dropTable("item_facetNtype_heap");
			jdbc.sqlUpdate("CREATE VIEW item_facetNtype_heap AS SELECT * FROM item_facet");
		}
	}

	private void createItemFacetTable(final @NonNull String engine, final @NonNull String table) throws SQLException {
		// UtilString.printDateNmessage("createItemFacetHeapInternal " + table
		// + " " + engine);

		jdbc.sqlUpdate("DROP VIEW IF EXISTS " + table);
		jdbc.dropAndCreateTable(table,
				"(record_num " + item_idType + ", facet_id " + facet_idType + ", PRIMARY KEY (record_num, facet_id)"
						+ ", KEY facet (facet_id)" + (engine.equals("MyISAM") ? "" : ", KEY item (record_num)")
						+ ") ENGINE=" + engine + " PACK_KEYS=1 ROW_FORMAT=FIXED");
	}

	// public static void populateItemFacetNtypeHeap(final @NonNull JDBCSample
	// jdbc) throws SQLException {
	// if (jdbc.nRows("item_facetNtype_heap") == 0) {
	// jdbc.sqlUpdate("INSERT INTO item_facetNtype_heap SELECT * FROM
	// item_facet");
	//
	// JDBCSample.errorp("item_facetNtype_heap has fewer rows than item_facet in
	// " + jdbc.dbName,
	// "Compile.populateItemFacetNtypeHeap");
	// }
	// }

	public static boolean populateOrReplaceHeapTable(final @NonNull JDBCSample jdbc, final String heapTable,
			final String copyFrom, final @NonNull String replaceSQL) throws SQLException {
		boolean result = true;
		try {
			assert jdbc.nRows(copyFrom) < MAX_HEAP_TABLE_ROWS;
			result = populateHeapTable(jdbc, heapTable, copyFrom);
		} catch (final AssertionError e) {
			jdbc.logp("Changing " + heapTable + " from MEMORY to MyISAM in " + jdbc.dbName, MyLogger.WARNING,
					"Compile.populateOrReplaceHeapTable");
			jdbc.renameTable(heapTable, heapTable + "_OLD");
			jdbc.sqlUpdate(replaceSQL);
			populateHeapTable(jdbc, heapTable, copyFrom);
		}
		return result;
	}

	public static boolean populateHeapTable(final @NonNull JDBCSample jdbc, final String heapTable,
			final String copyFrom) throws SQLException {
		final boolean result = jdbc.nRows(heapTable) == 0;
		if (result) {
			jdbc.sqlUpdate("INSERT INTO " + heapTable + " SELECT * FROM " + copyFrom);

			if (jdbc.nRows(heapTable) != jdbc.nRows(copyFrom)) {
				jdbc.errorp(heapTable + " has fewer rows than " + copyFrom + " in " + jdbc.dbName,
						"Compile.populateHeapTable");
			}
		}
		return result;
	}

	private void createItemOrder() throws SQLException {
		createItemOrderStatic(jdbc);
	}

	/**
	 * Create item_order and item_order_heap. Initialize the record_num column,
	 * and call populateItemOrder to initialize the others.
	 *
	 * @throws SQLException
	 */
	public static void createItemOrderStatic(final @NonNull JDBCSample jdbc) throws SQLException {
		UtilString.printDateNmessage("Creating item_order...");
		final int maxItemID = jdbc.sqlQueryInt("SELECT MAX(record_num) FROM item");
		final String _item_idType = JDBCSample.unsignedTypeForMaxValue(maxItemID);
		final int maxFacetID = jdbc.sqlQueryInt("SELECT MAX(facet_id) FROM facet");
		final String _facet_idType = JDBCSample.unsignedTypeForMaxValue(maxFacetID);

		final StringBuilder create = new StringBuilder(" (record_num ");
		create.append(_item_idType).append(", random_id ").append(_item_idType);

		final StringBuilder indexes = new StringBuilder(
				"ALTER TABLE item_order_heap ADD UNIQUE INDEX random_id USING BTREE (random_id)");

		final StringBuilder update = new StringBuilder("INSERT INTO item_order SELECT record_num, 0");

		try (final ResultSet rs = jdbc.sqlQuery("SELECT facet_id facet_type_id, first_child_offset FROM facet "
				+ "WHERE parent_facet_id = 0 ORDER BY facet_id");) {
			while (rs.next()) {
				update.append(", 0");

				final String columnName = sortColumnName(rs.getInt("facet_type_id"));
				create.append(", ").append(columnName).append(" ").append(_item_idType);
				appendIndex(indexes, columnName);
			}
			update.append(" FROM item");
			create.append(", PRIMARY KEY (record_num)) ENGINE=");
			final String misc = (" PACK_KEYS=1 ROW_FORMAT=FIXED");

			jdbc.dropAndCreateTable("item_order", create.toString() + "MyISAM" + misc);
			jdbc.dropAndCreateTable("item_order_heap", create.toString() + "HEAP" + misc);

			UtilString.printDateNmessage("Populating item_order with (item.record_num, 0, 0, ...)");
			final String sql = update.toString();
			assert sql != null;
			jdbc.sqlUpdate(sql);

			UtilString.printDateNmessage("Populating item_order remaining columns");
			populateItemOrder(jdbc, rs, _item_idType, _facet_idType);
		}

		UtilString.printDateNmessage("Indexing item_order...");
		final String sql = indexes.toString();
		assert sql != null;
		jdbc.sqlUpdate(sql);
		// jdbc.sqlUpdate("INSERT INTO item_order_heap SELECT * FROM
		// item_order");
	}

	public static final int SORT_BY_RECORD_NUM = 0;
	public static final int SORT_BY_RANDOM = -1;

	public static @NonNull String sortColumnName(final int facetTypeToSortBy) {
		return facetTypeToSortBy == SORT_BY_RANDOM ? "random_ID"
				: facetTypeToSortBy == SORT_BY_RECORD_NUM ? "record_num" : "col" + facetTypeToSortBy;
	}

	private static void appendIndex(final @NonNull StringBuilder indexes, final @NonNull String name) {
		indexes.append(", ADD UNIQUE INDEX ").append(name).append(" USING BTREE (").append(name).append(")");
	}

	/**
	 * Set the order for each facet_type column.
	 *
	 * @param facetTypeRS
	 *            [facet_type_id, first_child_offset]
	 */
	private static void populateItemOrder(final @NonNull JDBCSample jdbc, final @NonNull ResultSet facetTypeRS,
			final @NonNull String item_id_column_type, final @NonNull String facet_id_column_type) throws SQLException {
		reRandomizeItemOrder(item_id_column_type, jdbc);
		createOrderTable("columnOrder", item_id_column_type, jdbc);
		createPopulateItemOrderTables(facet_id_column_type, jdbc);
		@SuppressWarnings("resource")
		final PreparedStatement insertIntoColumnOrderPS = jdbc
				.lookupPS("INSERT INTO columnOrder" + " SELECT record_num, NULL FROM"

						+ " (SELECT record_num, MAX(facet_id) facet_id" + "  FROM item_facetNtype_heap"
						// ..Ensure facet_id is a descendent of facet_type_id
						+ "  WHERE facet_id BETWEEN ? AND ?" + "  GROUP BY record_num) foo"

						+ " INNER JOIN facetPaths USING (facet_id)" + " ORDER BY path, record_num");
		facetTypeRS.afterLast();
		int right = Integer.MAX_VALUE;
		while (facetTypeRS.previous()) {
			jdbc.sqlUpdate("TRUNCATE TABLE columnOrder");
			final String columnName = sortColumnName(facetTypeRS.getInt("facet_type_id"));
			final int left = facetTypeRS.getInt("first_child_offset") + 1;
			jdbc.sqlUpdateWithIntArgs(insertIntoColumnOrderPS, left, right);
			jdbc.sqlUpdate(itemOrderUpdateSQL("columnOrder", columnName));

			// Patch up any items that weren't handled above because they
			// have no tags of this type.
			jdbc.sqlUpdate("INSERT INTO columnOrder " + " SELECT record_num, NULL FROM item_order " + " WHERE "
					+ columnName + " = 0 ORDER BY record_num");

			jdbc.sqlUpdate(itemOrderUpdateSQL("columnOrder", columnName));
			right = left - 1;
		}
		jdbc.dropTable("facetPaths");
		jdbc.dropTable("columnOrder");
		jdbc.dropTable("itemOrder");
	}

	/**
	 * reRandomize item_order.random_id
	 *
	 * This should only happen during compile, or on server restarts.
	 */
	public static void reRandomizeItemOrder(final @NonNull String item_id_column_type, final @NonNull JDBCSample jdbc)
			throws SQLException {
		createOrderTable("random_item_ID", item_id_column_type, jdbc);

		jdbc.sqlUpdate("INSERT INTO random_item_ID" + " SELECT record_num, NULL FROM item ORDER BY RAND()");

		jdbc.sqlUpdate(itemOrderUpdateSQL("random_item_ID", "random_id"));
		jdbc.dropTable("random_item_ID");
	}

	/**
	 * <tableName> is a scratchpad for updating item_order. For each item_order
	 * column, <tableName> is re-computed with different orders. Order is random
	 * for random_id, deterministic for others.
	 *
	 * This should only happen during compile, or on server restarts.
	 */
	private static void createOrderTable(final @NonNull String tableName, final @NonNull String item_id_column_type,
			final @NonNull JDBCSample jdbc) throws SQLException {
		jdbc.dropTable(tableName);

		jdbc.sqlUpdate("CREATE TEMPORARY TABLE " + tableName + " (record_num " + item_id_column_type

				+ ", _order " + item_id_column_type + " AUTO_INCREMENT"

				+ ", PRIMARY KEY (record_num)"
				// AUTO_INCREMENT columns must be keys
				+ ", UNIQUE INDEX _order USING BTREE (_order)" + ")" + " ENGINE=HEAP PACK_KEYS=1 ROW_FORMAT=FIXED");
	}

	/**
	 * <tableName> is a scratchpad for updating item_order. For each item_order
	 * column, <tableName> is re-computed with different orders. Order is based
	 * on facet tree for each item.
	 *
	 * This should only happen during compile, or on server restarts.
	 */
	private static void createPopulateItemOrderTables(final @NonNull String facet_id_column_type,
			final @NonNull JDBCSample jdbc) throws SQLException {
		jdbc.dropTable("facetPaths");
		// Can't be TEMPORARY or you get 'Can't reopen table'
		jdbc.sqlUpdate("CREATE TABLE facetPaths (facet_id " + facet_id_column_type + ", path VARCHAR(255) NOT NULL"
				+ ", PRIMARY KEY (facet_id)" + ") ENGINE=HEAP PACK_KEYS=1 ROW_FORMAT=FIXED");
		jdbc.sqlUpdate("INSERT INTO facetPaths SELECT facet_id, LPAD(facet_id, 7, '0')" + " FROM facet "
				+ " WHERE parent_facet_id = 0");
		while (jdbc.sqlUpdate("INSERT IGNORE INTO facetPaths"
				+ " SELECT facet.facet_id, CONCAT(fp.path, ' -- ', LPAD(facet.facet_id, 7, '0'))"
				+ " FROM facet INNER JOIN facetPaths fp ON fp.facet_id = facet.parent_facet_id"
				+ " WHERE parent_facet_id > 0;") > 0) {
			// Repeat while paths percolate to descendents
		}
	}

	private static @NonNull String itemOrderUpdateSQL(final @NonNull String tableName,
			final @NonNull String columnName) {
		return "UPDATE item_order, " + tableName + " order_table SET item_order." + columnName
				+ " = order_table._order " + "WHERE item_order.record_num = order_table.record_num";
	}

	private void createUserActions() throws SQLException {
		jdbc.sqlUpdate("CREATE TABLE IF NOT EXISTS user_actions (" + "timestamp DATETIME NOT NULL,"
				+ " location TINYINT(3) UNSIGNED NOT NULL," + " object VARCHAR(255) NOT NULL,"
				+ " modifiers MEDIUMINT(9) NOT NULL," + " session INT(11) NOT NULL," + " client VARCHAR(255) NOT NULL)"
				+ "ENGINE=MyISAM PACK_KEYS=1");
	}

	private int printErrors(final @NonNull String query, final String message, final String fieldList)
			throws SQLException {
		return printErrors(query, message, fieldList, MAX_ERRORS_TO_PRINT, Integer.MAX_VALUE);
	}

	private int printErrors(final @NonNull String query, final String message, final String fieldList,
			final int maxPrint, final int group_concat_max_len) throws SQLException {
		return printErrors(query, null, message, fieldList, maxPrint, group_concat_max_len);
	}

	private int printErrors(final ResultSet rs, final String message, final String fieldList) throws SQLException {
		return printErrors(rs, message, fieldList, MAX_ERRORS_TO_PRINT, Integer.MAX_VALUE);
	}

	private int printErrors(final ResultSet rs, final String message, final String fieldList, final int maxPrint,
			final int group_concat_max_len) throws SQLException {
		return printErrors(null, rs, message, fieldList, maxPrint, group_concat_max_len);
	}

	@SuppressWarnings("resource")
	private int printErrors(final String query, ResultSet rs, final String message, final String fieldList,
			final int maxPrint, final int group_concat_max_len) throws SQLException {
		final int oldMaxLength = jdbc.sqlQueryInt("SELECT @@group_concat_max_len");
		jdbc.sqlUpdate("SET SESSION group_concat_max_len = " + group_concat_max_len);
		int nErrors = 0;
		if (rs == null) {
			assert query != null;
			rs = jdbc.sqlQuery(query);
		}
		final int row = rs.getRow();
		try {
			nErrors = MyResultSet.nRows(rs);
			if (nErrors > 0 && maxPrint > 0) {
				UtilString.printDateNmessage(
						message + " (printing " + (Math.min(nErrors, maxPrint)) + " of " + nErrors + " such errors)");
				final FormattedTableBuilder align = new FormattedTableBuilder();
				final String[] fields = fieldList.split(",");
				final int nFields = fields.length;
				assert nFields < 20 : fieldList;
				align.addLine(Arrays.asList(fields));
				while (rs.next() && rs.getRow() <= maxPrint) {
					align.addLine(MyResultSet.getRowValue(rs, query));
				}
				System.out.println(align.format());
			}
		} catch (final SQLException se) {
			jdbc.logp("SQL Exception: ", MyLogger.SEVERE, se, "Compile.printErrors");
			se.printStackTrace(System.out);
		} finally {
			MyResultSet.resetRow(rs, row);
		}
		jdbc.sqlUpdate("SET SESSION group_concat_max_len = " + oldMaxLength);
		return nErrors;
	}

	private int checkRawErrors() throws SQLException {
		UtilString.printDateNmessage("Checking input tables for errors...");
		int nErrors = 0;

		final String[] tablesWithNames = { "raw_facet", "raw_facet_type" };
		for (final String table : tablesWithNames) {
			final int nErrs = printErrors("SELECT name FROM " + table + " WHERE name != TRIM(name)",
					"Warning: removing initial blank space from " + table + ".name", "Name");
			if (nErrs > 0) {
				nErrors += nErrs;
				jdbc.sqlUpdate("UPDATE " + table + " SET name = TRIM(name);");
			}
		}

		nErrors += nameUnnamedFacets();
		nErrors += setEmptyItemDescFieldsToNull();
		nErrors += findOrphans();
		// assert nErrors == 0 : nErrors + " fatal errors; aborting";

		nErrors += fixMissingParentRawItemFacets();
		nErrors += findBrokenLinks(true);
		nErrors += promoteNonrestrictingFacets();

		// nErrors += db.mergeDuplicateFacets(); // promoteNonrestrictingFacets
		// already did this

		assert findOrphans() == 0 : "fatal error; aborting";

		UtilString.printDateNmessage("\n...found " + UtilString.addCommas(nErrors) + " errors in raw tables.");

		return nErrors;
	}

	private int nameUnnamedFacets() throws SQLException {
		final int nErrors = printErrors("SELECT child.facet_id, child.parent_facet_id, parent.name"
				+ " FROM raw_facet child LEFT JOIN raw_facet parent" + " ON child.parent_facet_id = parent.facet_id"
				+ " WHERE child.name IS NULL OR child.name = ''", "ERROR: No name in raw_facet",
				"facet_id, parent_facet_id, parent.name");
		jdbc.sqlUpdate("REPLACE INTO raw_facet (SELECT parent.facet_id, CONCAT('<unnamed', parent.facet_id, '>'),"
				+ " parent.parent_facet_id, parent.sort FROM raw_facet parent"
				+ " INNER JOIN raw_facet child ON child.parent_facet_id = parent.facet_id"
				+ " WHERE parent.name IS NULL OR parent.name = '')");
		assert !jdbc.isSqlQueryIntPositive("SELECT 1 FROM raw_facet WHERE name IS NULL OR name = '' LIMIT 1");
		// jdbc.sqlUpdate("DELETE FROM raw_facet WHERE name IS NULL OR name =
		// ''");
		return nErrors;
	}

	private int findOrphans() throws SQLException {
		return printErrors(
				"SELECT raw.facet_id, raw.name, raw.parent_facet_id"
						+ " FROM raw_facet raw LEFT JOIN raw_facet parent ON raw.parent_facet_id = parent.facet_id"
						+ " WHERE parent.facet_id IS NULL" + " AND raw.parent_facet_id > "
						+ jdbc.sqlQueryInt("SELECT MAX(facet_type_id) FROM raw_facet_type"),
				"ERROR: No parent in raw_facet", "facet_id, name, parent_facet_id");
	}

	public int findBrokenLinks(final boolean isRaw) throws SQLException {
		final String itemFacetTable = isRaw ? "raw_item_facet" : "item_facet";
		final String facetNtypeTable = isRaw ? "raw_facet_n_type" : "facet";
		if (isRaw) {
			jdbc.sqlUpdate("CREATE OR REPLACE VIEW raw_facet_n_type AS"
					+ " SELECT facet_id, name, parent_facet_id FROM raw_facet UNION"
					+ " SELECT facet_type_id, name, 0 FROM raw_facet_type");
		}

		int nErrors = deleteUnusedItems(itemFacetTable) + deleteUnusedFacets(isRaw);

		// find itemFacets that refer to non-existent items
		final String q0 = "SELECT missingItemIDs.record_num, " + "CONCAT('[', COUNT(*), ' facets: ', "
				+ "GROUP_CONCAT(DISTINCT facet_id ORDER BY facet_id), ']')" + "FROM " + itemFacetTable
				+ " INNER JOIN (SELECT itemFacetRecordNums.record_num FROM" + "(SELECT DISTINCT record_num FROM "
				+ itemFacetTable + ") itemFacetRecordNums " + "LEFT JOIN item USING (record_num)"
				+ "WHERE item.record_num IS NULL) missingItemIDs USING (record_num)"
				+ "GROUP BY missingItemIDs.record_num";
		int noSuch = printErrors(q0, "ERROR: " + itemFacetTable + " refers to a non-existent item",
				"record_num, facetIDs", MAX_ERRORS_TO_PRINT, 100);

		// find itemFacets that refer to non-existent facets
		final String sql = "SELECT missingFacetIDs.facet_id, " + "CONCAT('[', COUNT(*), ' records: ', "
				+ "GROUP_CONCAT(DISTINCT record_num ORDER BY record_num), ']')" + "FROM " + itemFacetTable
				+ " INNER JOIN" + "(SELECT itemFacetFacetIDs.facet_id FROM" + "(SELECT DISTINCT facet_id FROM "
				+ itemFacetTable + ") itemFacetFacetIDs " + "LEFT JOIN " + facetNtypeTable + " facet USING (facet_id)"
				+ "WHERE facet.facet_id IS NULL) missingFacetIDs USING (facet_id)"
				+ "GROUP BY missingFacetIDs.facet_id";

		noSuch += printErrors(sql, "ERROR: " + itemFacetTable + " refers to a non-existent facet", "facet_id, items",
				MAX_ERRORS_TO_PRINT, 100);
		nErrors += noSuch;

		if (isRaw) {
			if (noSuch > 0) {
				UtilString.printDateNmessage("Deleting " + noSuch + " bogus facet/item links from raw_item_facet\n");

				jdbc.sqlUpdate("DELETE raw_item_facet FROM raw_item_facet, "

						+ "(SELECT itemFacet.facet_id, itemFacet.record_num" + " FROM raw_item_facet itemFacet"
						+ " LEFT JOIN raw_facet_n_type facet USING (facet_id)" + " LEFT JOIN item USING (record_num)"
						+ " WHERE facet.facet_id IS NULL OR item.record_num IS NULL) unused "

						+ "WHERE raw_item_facet.record_num = unused.record_num "
						+ "AND raw_item_facet.facet_id = unused.facet_id");
			}
			jdbc.sqlUpdate("DROP VIEW IF EXISTS raw_facet_n_type");
		}
		return nErrors;
	}

	private int deleteUnusedItems(final @NonNull String itemFacet) throws SQLException {

		jdbc.dropAndCreateTable("usedItems",
				"(record_num INT UNSIGNED NOT NULL DEFAULT 0," + " PRIMARY KEY (record_num)" + ") ENGINE=Memory");
		jdbc.sqlUpdate("INSERT INTO usedItems" + " SELECT DISTINCT record_num FROM " + itemFacet);

		jdbc.dropAndCreateTable("unusedItems",
				"(record_num INT UNSIGNED NOT NULL DEFAULT 0,"
						// + " description TEXT,"
						+ " PRIMARY KEY (record_num)" + ") ENGINE=Memory");
		jdbc.sqlUpdate("INSERT INTO unusedItems "
				+ "(SELECT item.record_num FROM item LEFT JOIN usedItems used USING (record_num)"
				+ " WHERE used.record_num IS NULL)");

		final String descField = jdbc
				.sqlQueryString("SELECT SUBSTRING_INDEX(itemDescriptionFields, ',', 1) FROM globals");
		final int nErrors = printErrors(
				"SELECT record_num, " + descField + " FROM unusedItems INNER JOIN item USING (record_num)",
				"ERROR: Item is not associated with any facets", "record_num, " + descField);

		// assert nErrors == 0 : itemFacet + " has " + jdbc.nRows("unusedItems")
		// + " unassociated items";

		if (nErrors > 0) {
			UtilString.printDateNmessage("Deleting " + nErrors + " bogus items with no facets");
			jdbc.sqlUpdate("DELETE FROM item WHERE record_num IN (SELECT record_num FROM unusedItems)");
		}

		jdbc.dropTable("usedItems");
		jdbc.dropTable("unusedItems");
		return nErrors;
	}

	private int deleteUnusedFacets(final boolean isRaw) throws SQLException {
		final String itemFacetTable = isRaw ? "raw_item_facet" : "item_facet";
		final String facetTable = isRaw ? "raw_facet" : "facet";

		jdbc.dropAndCreateTable("usedFacets",
				"(facet_id INT UNSIGNED NOT NULL DEFAULT 0," + " PRIMARY KEY (facet_id)" + ") ENGINE=Memory");
		jdbc.sqlUpdate("REPLACE INTO usedFacets" + " SELECT DISTINCT facet_id FROM " + itemFacetTable);
		if (!isRaw) {
			jdbc.sqlUpdate("REPLACE INTO usedFacets" + " SELECT facet_id FROM facet WHERE parent_facet_id = 0");
		}

		jdbc.dropAndCreateTable("unusedFacets",
				"AS SELECT CONCAT(parent.name, ' (', parent.facet_id, ') <', (SELECT COUNT(*) FROM " + itemFacetTable
						+ " WHERE parent.facet_id = " + itemFacetTable + ".facet_id), '>') parent_name"
						+ ", facet.name, facet.facet_id" + " FROM " + facetTable + " facet LEFT JOIN " + facetTable
						+ " parent" + " ON facet.parent_facet_id = parent.facet_id"
						+ " LEFT JOIN usedFacets ON facet.facet_id = usedFacets.facet_id"
						+ " WHERE usedFacets.facet_id IS NULL");

		int nUnusedFacets = printErrors(
				"SELECT parent_name, "
						+ "CONCAT('[', COUNT(*), ' facets: ', GROUP_CONCAT(CONCAT(name, ' (', facet_id, ')')), ']') "
						+ "FROM unusedFacets GROUP BY parent_name",
				"\nERROR: Facet is not associated with any items", "parent, facets");
		if (isRaw && nUnusedFacets > 0) {
			nUnusedFacets = jdbc.nRows("unusedFacets");
			UtilString.printDateNmessage("Deleting " + nUnusedFacets + " unused facets from raw_facet");
			jdbc.sqlUpdate("DELETE facet FROM " + facetTable
					+ " facet, unusedFacets WHERE facet.facet_id = unusedFacets.facet_id");
		}
		jdbc.dropTable("usedFacets");
		jdbc.dropTable("unusedFacets");
		return nUnusedFacets;
	}

	/**
	 * Find facet children that link to an item, but the parent doesn't
	 * (including facet_type's). This is linear in raw_item_facet.nRows, and
	 * thus very slow for Elamp_shot databases.
	 *
	 * if raw_item_facet(item, childFacet), ensure raw_item_facet(item,
	 * parentFacet)
	 *
	 * @return nErrors
	 */
	public int fixMissingParentRawItemFacets() throws SQLException {
		return fixMissingParentRawItemFacetsInternal(0);
	}

	int fixMissingParentRawItemFacetsInternal(final int tableSuffix) throws SQLException {
		UtilString.printDateNmessage("Finding missing raw_item_facets...");

		final String missingTableName = " missingParentItemFacets" + tableSuffix + " ";
		jdbc.dropAndCreateTable(missingTableName, "AS" + fixMissingRawItemFacetsSQL(false, tableSuffix));
		int nRows = jdbc.nRows(missingTableName);
		UtilString.printDateNmessage("...found " + nRows + " missing raw_item_facets.");
		if (nRows == 0) {
			System.out.println(" Finding missing raw_item_type_facets...");
			nRows = jdbc.sqlUpdate("INSERT INTO " + missingTableName + fixMissingRawItemFacetsSQL(true, tableSuffix));
			UtilString.printDateNmessage("...found " + nRows + " missing raw_item_type_facets");
		}

		// int nErrors = printErrors(
		// "SELECT name, facet_id, parentName, parent_facet_id, CONCAT('[',
		// COUNT(*), ' records: ', "
		// + "GROUP_CONCAT(DISTINCT record_num ORDER BY record_num), ']') FROM"
		// + missingTableName + "GROUP BY facet_id",
		// "ERROR: Item has facet child, but not its parent",
		// "childName, childFacetID, parentName, parentFacetID, items", 4,
		// 10);

		int nErrors = jdbc.nRows(missingTableName);
		if (nErrors > 0) {
			UtilString.printDateNmessage("Adding missing raw_item_facets...");

			jdbc.sqlUpdate(
					"INSERT INTO raw_item_facet" + "	SELECT record_num, parent_facet_id FROM" + missingTableName);
			UtilString.printDateNmessage("fixMissingParentRawItemFacetsInternal added " + UtilString.addCommas(nErrors)
					+ " missing raw_item_facets; recursing");

			// Keep propagating up
			nErrors += fixMissingParentRawItemFacetsInternal(tableSuffix + 1);
		}
		jdbc.dropTable(missingTableName);
		return nErrors;
	}

	private static @NonNull String fixMissingRawItemFacetsSQL(final boolean isType, final int tableSuffix) {
		if (tableSuffix == 0) {
			return fixMissingRawItemFacetsTopLevelSQL(isType);
		} else {
			return fixMissingRawItemFacetsRecursiveSQL(isType, tableSuffix);
		}
	}

	// childItemFacet is already in raw_item_facet
	static @NonNull String fixMissingRawItemFacetsTopLevelSQL(final boolean isType) {
		final String type = isType ? "_type" : "";
		return " SELECT DISTINCT childItemFacet.record_num, parentFacet.facet" + type + "_id parent_facet_id"
				+ " FROM raw_facet childFacet INNER JOIN raw_item_facet childItemFacet USING (facet_id) INNER JOIN raw_facet"
				+ type + " parentFacet ON childFacet.parent_facet_id = parentFacet.facet" + type + "_id"
				+ " LEFT JOIN raw_item_facet parentItemFacet ON"
				+ " (parentItemFacet.facet_id = childFacet.parent_facet_id"
				+ "  AND parentItemFacet.record_num = childItemFacet.record_num)"
				+ " WHERE parentItemFacet.facet_id IS NULL";
	}

	// missingParentItemFacetsN is already in raw_item_facet
	static @NonNull String fixMissingRawItemFacetsRecursiveSQL(final boolean isType, final int tableSuffix) {
		final String type = isType ? "_type" : "";
		final String missingTableName = " missingParentItemFacets" + (tableSuffix - 1) + " ";

		return " SELECT DISTINCT " + missingTableName + ".record_num, parentFacet.facet" + type + "_id parent_facet_id"
				+ " FROM " + missingTableName + " INNER JOIN raw_facet childFacet ON childFacet.facet_id = "
				+ missingTableName + ".parent_facet_id" + " INNER JOIN raw_facet" + type
				+ " parentFacet ON childFacet.parent_facet_id = parentFacet.facet" + type + "_id"
				+ " LEFT JOIN raw_item_facet parentItemFacet ON" + " (parentItemFacet.facet_id = parentFacet.facet"
				+ type + "_id" + "  AND parentItemFacet.record_num = " + missingTableName + ".record_num)"
				+ " WHERE parentItemFacet.facet_id IS NULL";
	}

	private void setUpFacetSearch() throws SQLException {
		UtilString.printDateNmessage("Setting up facet search...");
		final int oldMaxLength = jdbc.sqlQueryInt("SELECT @@group_concat_max_len");
		jdbc.sqlUpdate("SET SESSION group_concat_max_len = " + 65_000);

		// jdbc.sqlUpdate("UPDATE item SET facet_names = "
		// + "(SELECT GROUP_CONCAT(name) "
		// + "FROM item_facetNtype_heap INNER JOIN facet USING (facet_id)"
		// + " WHERE item.record_num = item_facetNtype_heap.record_num)");

		jdbc.dropAndCreateTable("item_temp", "(record_num INT NOT NULL," + " facet_names TEXT NOT NULL"
		// + ", PRIMARY KEY (record_num)"
				+ ")" + " ENGINE=MyISAM");

		jdbc.sqlUpdate("INSERT INTO item_temp" + " SELECT STRAIGHT_JOIN record_num, GROUP_CONCAT(name)"
				+ " FROM item_facet" + " INNER JOIN facet USING (facet_id)" + " GROUP BY record_num");

		jdbc.sqlUpdate("UPDATE item, item_temp" + " SET item.facet_names = item_temp.facet_names"
				+ " WHERE item.record_num = item_temp.record_num");

		jdbc.dropTable("item_temp");

		jdbc.ensureIndex("item", "search",
				"facet_names," + jdbc.sqlQueryString("SELECT itemDescriptionFields FROM globals").replace("item.", ""),
				"FULLTEXT");

		jdbc.sqlUpdate("SET SESSION group_concat_max_len = " + oldMaxLength);
		// return nErrors;
	}

	private int setEmptyItemDescFieldsToNull() throws SQLException {
		int nEmptyFields = 0;

		final String itemDescs = jdbc.sqlQueryString("SELECT itemDescriptionFields FROM globals");
		// if (itemDescs != null) {
		final String[] itemDescFields = itemDescs.split(",");
		for (final String field : itemDescFields) {
			// CONCAT_WS ignores nulls, so replace empty strings with NULLs.
			nEmptyFields = jdbc.sqlUpdate("UPDATE item SET " + field + " = NULL WHERE LENGTH(TRIM(" + field + ")) = 0");
			if (nEmptyFields > 0) {
				UtilString.printDateNmessage(
						"Compile.setUpFacetSearch: found " + nEmptyFields + " empty values in item." + field);
			}
		}
		// }
		return nEmptyFields;
	}

	public static void ensureGlobalColumns(final JDBCSample jdbc2) throws SQLException {
		// JDBCSample.getMyLogger().logp(
		// "enter " + jdbc2.dbName + " "
		// + jdbc2.columnExists("globals", "thumb_regexp"),
		// MyLogger.WARNING, "Compile.ensureGlobalColumns");
		if (!jdbc2.columnExists("globals", "build_args")) {
			jdbc2.sqlUpdate("ALTER TABLE globals ADD COLUMN build_args TEXT");
		}
		if (!jdbc2.columnExists("globals", "thumb_URL")) {
			jdbc2.sqlUpdate("ALTER TABLE globals ADD COLUMN thumb_URL TEXT");
		}
		if (!jdbc2.columnExists("globals", "thumb_regexp")) {
			jdbc2.sqlUpdate("ALTER TABLE globals ADD COLUMN thumb_regexp TEXT");
		}
		// JDBCSample.getMyLogger().logp(
		// "exit " + jdbc2.dbName + " "
		// + jdbc2.columnExists("globals", "thumb_regexp"),
		// MyLogger.WARNING, "Compile.ensureGlobalColumns");
	}

	public static @NonNull String[] ancestors(final @NonNull String ancestorString) {
		final String[] result = ancestorString.split(FACET_HIERARCHY_SEPARATOR);
		assert result != null;
		return result;
	}
}
