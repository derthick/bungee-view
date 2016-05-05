package edu.cmu.cs.bungee.servlet;

import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyLogger;
import edu.cmu.cs.bungee.javaExtensions.MyResultSetColumnTypeList;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilFiles;
import edu.cmu.cs.bungee.javaExtensions.UtilImage;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

class DatabaseEditing implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final String[] ITEM_FACET_TABLES = { "item_facetNtype_heap", "item_facet" };

	MyLogger myLogger = null;
	Level myLoggerLevel = MyLogger.DEFAULT_LOGGER_LEVEL;

	MyLogger getMyLogger() {
		if (myLogger == null) {
			myLogger = MyLogger.getMyLogger(DatabaseEditing.class);
			myLogger.setLevel(myLoggerLevel);
		}
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

	private final @NonNull JDBCSample jdbc;
	private final Database db;

	private boolean facetTableMucked = false;

	private void logp(final String msg, final Level level, final String sourceMethod) {
		MyLogger.logp(getMyLogger(), msg, level, "DatabaseEditing", sourceMethod);
	}

	/**
	 * Using
	 *
	 * if () {errorp();}
	 *
	 * instead of myAssertp() prevents unnecessary evaluation of msg.
	 */
	private void errorp(final String msg, final String sourceMethod) {
		logp(msg, MyLogger.SEVERE, sourceMethod);
		throw new AssertionError(msg);
	}

	// private static boolean myAssertp(final boolean condition, final String
	// msg, final String sourceMethod) {
	// return MyLogger.myAssertp(MY_LOGGER, condition, msg, "DatabaseEditing",
	// sourceMethod);
	// }

	DatabaseEditing(final Database _db) throws SQLException, IOException, InterruptedException {
		super();
		db = _db;
		jdbc = _db.jdbc;
		ensureBackup();
	}

	String dbName() {
		return db.dbName;
	}

	/**
	 * Add facet and all its ancestors to item.
	 */
	void addItemFacet(final int facet, final int item, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		checkNonNegative(item); // Dummy instance has ID=0
		addItemsFacet(facet, "SELECT " + item + " record_num", out);
	}

	/**
	 * Add facet and all its ancestors to all query results (as stored in table)
	 */
	void addItemsFacet(final int facet, final OnItemsTable table, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		addItemsFacet(facet, "SELECT record_num FROM " + table, out);
	}

	private void addItemsFacet(final int facet, final String itemExpr, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		checkPositive(facet);
		// ensureFacetMap();
		for (int ancestor = facet; ancestor > 0; ancestor = parentFacetID(ancestor)) {
			for (final String itemFacetTable : ITEM_FACET_TABLES) {
				replaceIntoItemFacetTable(itemExpr, ancestor, itemFacetTable);
			}
			final int rawAncestor = lookupRawFacet(ancestor);
			if (rawAncestor > 0) {
				replaceIntoItemFacetTable(itemExpr, rawAncestor, "raw_item_facet");
			}
		}
		updateFacetCounts(facet, out);
	}

	@SuppressWarnings("resource")
	private int lookupRawFacet(final int canonicalFacet) throws SQLException {
		final PreparedStatement lookupRawFacetPS = jdbc
				.lookupPS("SELECT raw_facet_id FROM facet_map WHERE canonical_facet_id = ?");
		return jdbc.sqlQueryInt(lookupRawFacetPS, true, canonicalFacet);
	}

	private void replaceIntoItemFacetTable(final String itemExpr, final int ancestor, final String itemFacetTable)
			throws SQLException {
		jdbc.sqlUpdate(
				"REPLACE INTO " + itemFacetTable + " SELECT record_num, " + ancestor + " FROM (" + itemExpr + ") foo");
	}

	Set<Integer> removeItemFacet(final int facet, final int item, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		checkPositive(facet);
		checkPositive(item);
		return removeItemsFacet(facet, "SELECT " + item + " record_num", out);

		// jdbc.sqlUpdate("DELETE FROM item_facetNtype_heap WHERE record_num = "
		// + item + " AND facet_id = " + facet);
		// final Set<Integer> result = new HashSet<>();
		// boolean hasChildren = false;
		// try (final ResultSet rs = jdbc
		// .sqlQuery("SELECT facet_id FROM facet WHERE parent_facet_id = "
		// + facet);) {
		// while (rs.next()) {
		// hasChildren = true;
		// result.addAll(removeItemFacet(rs.getInt(1), item, null));
		// }
		// }
		// if (!hasChildren) {
		// result.add(facet);
		// }
		// final int grandparent = jdbc
		// .sqlQueryInt("SELECT parent.parent_facet_id FROM facet f "
		// + "INNER JOIN facet parent ON f.parent_facet_id = parent.facet_id "
		// + "WHERE f.facet_id = " + facet);
		// checkNonNegative(grandparent);
		// if (grandparent == 0) {
		// final int parent = parentFacetID(facet);
		// checkPositive(parent);
		// jdbc.sqlUpdate("DELETE FROM item_facetNtype_heap WHERE record_num = "
		// + item + " AND facet_id = " + parent);
		// }
		// if (out != null) {
		// updateFacetsCounts(result, out);
		// }
		// return result;
	}

	Set<Integer> removeItemsFacet(final int facet, final OnItemsTable table, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		return removeItemsFacet(facet, "SELECT record_num FROM " + table, out);
	}

	Set<Integer> removeItemsFacet(final int facet, final String itemExpr, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		checkPositive(facet);
		// final String tableName = Database.onItemsTable(table);
		for (final String itemFacetTable : ITEM_FACET_TABLES) {
			removeFromItemFacetTable(facet, itemExpr, itemFacetTable);
		}
		final int rawFacet = lookupRawFacet(facet);
		if (rawFacet > 0) {
			removeFromItemFacetTable(rawFacet, itemExpr, "raw_item_facet");
		}
		final Set<Integer> result = new HashSet<>();
		boolean hasChildren = false;
		try (final ResultSet rs = jdbc.sqlQuery("SELECT facet_id FROM facet WHERE parent_facet_id = " + facet);) {
			while (rs.next()) {
				hasChildren = true;
				result.addAll(removeItemsFacet(rs.getInt(1), itemExpr, out));
			}
		}
		if (!hasChildren) {
			result.add(facet);
		}
		// final int grandparent = jdbc
		// .sqlQueryInt("SELECT parent.parent_facet_id FROM facet f "
		// + "INNER JOIN facet parent ON f.parent_facet_id = parent.facet_id "
		// + "WHERE f.facet_id = " + facet);
		// checkNonNegative(grandparent);
		// if (grandparent == 0) {
		// final int parent = parentFacetID(facet);
		// checkPositive(parent);
		// jdbc.sqlUpdate("DELETE FROM ifh USING item_facetNtype_heap ifh, "
		// + table
		// + " oi "
		// + "WHERE ifh.record_num = oi.record_num AND ifh.facet_id = "
		// + parent);
		// }
		if (out != null) {
			updateFacetsCounts(result, out);
		}
		return result;
	}

	private void removeFromItemFacetTable(final int facet, final String itemExpr, final String itemFacetTable)
			throws SQLException {
		jdbc.sqlUpdate(
				"DELETE FROM " + itemFacetTable + " WHERE record_num IN (" + itemExpr + ") AND facet_id = " + facet);
	}

	void rotate(final int item, final int clockwiseDegrees) throws SQLException, IOException {
		try (final ResultSet rs = jdbc
				.sqlQuery("SELECT image, URI FROM " + "item LEFT JOIN images ON item.record_num = images.record_num "
						+ "WHERE item.record_num = " + item);) {
			if (rs.next()) {

				// Rotate images.image
				final BufferedImage im = ImageIO.read(rs.getBlob(1).getBinaryStream());
				final BufferedImage rot = UtilImage.rotate(im, Math.toRadians(clockwiseDegrees));
				final int w = rot.getWidth();
				final int h = rot.getHeight();
				final File tempFile = File.createTempFile("temp", "jpg");
				final String tempFilePath = tempFile.getCanonicalPath();
				UtilImage.writeJPGImage(rot, 85, tempFilePath);
				jdbc.sqlUpdate("UPDATE images SET image = LOAD_FILE(" + JDBCSample.quoteForSQL(tempFilePath) + "), w = "
						+ w + ", h = " + h + "WHERE record_num = " + item);
				tempFile.delete();

				// Rotate URI
				final String filename = rs.getString(2);
				if (!filename.endsWith(".jpg")) {
					errorp("rotate filname=" + filename, "rotate");
				}
				final File f = new File(filename);
				final BufferedImage im2 = ImageIO.read(f.toURI().toURL());
				final File newName = new File(filename.substring(0, filename.length() - 4) + "_unrotated.jpg");
				final boolean isRenamed = f.renameTo(newName);
				assert isRenamed : "Rename " + f + " => " + newName + " failed.";
				final BufferedImage rot2 = UtilImage.rotate(im2, Math.toRadians(clockwiseDegrees));
				UtilImage.writeJPGImage(rot2, 85, filename);
			}
		}
	}

	void rename(final int facet, final String name) throws SQLException {
		if (parentFacetID(facet) == 0) {
			final String oldName = jdbc.sqlQueryString("SELECT name FROM facet WHERE facet_id = " + facet);
			jdbc.sqlUpdate("UPDATE raw_facet_type SET name = '" + name + "' WHERE name = '" + oldName + "'");
		} else {
			jdbc.sqlUpdate("UPDATE raw_facet SET name = '" + name + "' WHERE facet_id = " + lookupRawFacet(facet));
		}
		jdbc.sqlUpdate("UPDATE facet SET name = '" + name + "' WHERE facet_id = " + facet);
		// muckWithFacetTable();
		// renumber(parentFacetID(facet));
	}

	void addChildFacet(final int parent, final String name, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		checkNonNegative(parent);
		int child = jdbc.sqlQueryInt("SELECT MAX(facet_id) + 1 FROM facet");
		checkPositive(child);

		// facet is [facet_id, name, parent_facet_id, n_items, n_child_facets,
		// first_child_offset, is_alphabetic]
		jdbc.sqlUpdate("INSERT INTO facet VALUES(" + child + ", '" + name + "', " + parent + ", 1, 0, 0, 1)");
		if (parent > 0) {
			addItemFacet(child, 0, null);
			renumber(parent);
			child = jdbc.sqlQueryInt("SELECT facet_id FROM renames WHERE old_facet_id = " + child);
			checkPositive(child);
			updateFacetCounts(child, out);
		} else {
			final int order = jdbc.sqlQueryInt("SELECT MAX(sort) + 1 FROM raw_facet_type");
			final int type = jdbc.sqlQueryInt("SELECT MAX(facet_type_id) + 1 FROM raw_facet_type");
			if (order <= 0 || order > 127) {
				errorp("Bad order " + order, "addChildFacet");
			}
			jdbc.sqlUpdate("INSERT INTO raw_facet_type VALUES(" + type + ", '" + name
					+ "', 'content', ' that show ; that don\\'t show ', " + order + ", 0, 1)");
			addChildFacet(child, "dummy", out);
		}
	}

	/**
	 * Does not remove instances from old parent, because they might be there
	 * for a different reason.
	 *
	 * @param parent
	 * @param child
	 * @param out
	 * @throws SQLException
	 * @throws ServletException
	 * @throws IOException
	 */
	void reparent(final int parent, int child, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		checkNonNegative(parent);
		checkPositive(child);
		final int oldParent = parentFacetID(child);
		jdbc.sqlUpdate("UPDATE facet SET parent_facet_id = " + parent + " WHERE facet_id = " + child);
		addItemsFacet(parent, "SELECT record_num FROM item_facetNtype_heap WHERE facet_id = " + child, out);

		// ResultSet rs = jdbc
		// .SQLquery("SELECT record_num FROM item_facetNtype_heap WHERE facet_id
		// = "
		// + child);
		// while (rs.next()) {
		// int item = rs.getInt(1);
		// addItemFacet(parent, item, null);
		// }

		renumber(oldParent);
		renumber(parent);
		child = jdbc.sqlQueryInt("SELECT facet_id FROM renames WHERE old_facet_id = " + child);
		updateFacetsCounts(UtilArray.getUnmodifiableList(oldParent, child), out);
	}

	/**
	 * Renumber parent's scattered and/or unordered children by moving them to a
	 * fresh set of IDs greater than all existing IDs and sorting by name (since
	 * we don't know sort). Recurse on each child.
	 *
	 * @param parent
	 * @throws SQLException
	 * @throws ServletException
	 */
	@SuppressWarnings("resource")
	private void renumber(final int parent) throws SQLException, ServletException {

		// // // Remove children (with old ID) from relevantFacets table
		// // final boolean relevant = jdbc
		// // .isSqlQueryIntPositive("SELECT f.facet_id FROM relevantFacets "
		// // + "INNER JOIN facet f USING (facet_id) WHERE parent_facet_id = "
		// // + parent + " LIMIT 1");
		// // if (relevant) {
		// db.updateRelevantFacets(null, Integer.toString(parent));
		// // }

		// Create renames table if necessary
		muckWithFacetTable();

		final int firstChildID = jdbc.sqlQueryInt("SELECT MAX(facet_id) + 1 FROM facet");
		jdbc.sqlUpdate("UPDATE facet SET first_child_offset = " + (firstChildID - 1) + " WHERE facet_id = " + parent);
		final PreparedStatement ps = jdbc
				.lookupPS("SELECT facet_id FROM facet" + " WHERE parent_facet_id = ? ORDER BY name");
		ps.setInt(1, parent);
		int newChildID = firstChildID;
		try (ResultSet rs = jdbc.sqlQuery(ps);) {
			while (rs.next()) {
				final int oldChildID = rs.getInt(1);
				// jdbc.print("Renumber " + oldChildID + " => " + newChildID);
				changeID(oldChildID, newChildID++);
			}
		}

		// // Replace children (with new ID) into relevantFacets table
		// // if (relevant) {
		// db.updateRelevantFacets(Integer.toString(parent), null);
		// // }

		// Wait until all children are moved to recurse, so MAX(facet_id) will
		// be correct
		for (int child = firstChildID; child < newChildID; child++) {
			renumber(child);
		}
	}

	@SuppressWarnings({ "resource", "null" })
	private void changeID(final int oldID, final int newID) throws SQLException {
		final String[] newOld = { "UPDATE facet_map SET canonical_facet_id = ? WHERE canonical_facet_id = ?",
				"UPDATE item_facet SET facet_id = ? WHERE facet_id = ?",
				"UPDATE item_facetNtype_heap SET facet_id = ? WHERE facet_id = ?",
				"UPDATE facet SET facet_id = ? WHERE facet_id = ?",
				"UPDATE facet SET parent_facet_id = ? WHERE parent_facet_id = ?" };
		final String[] oldNew = { "INSERT INTO renames VALUES(?, ?)" };

		for (final @NonNull String sql : newOld) {
			final PreparedStatement ps = jdbc.lookupPS(sql);
			jdbc.sqlUpdateWithIntArgs(ps, newID, oldID);
		}
		for (final @NonNull String sql : oldNew) {
			final PreparedStatement ps = jdbc.lookupPS(sql);
			jdbc.sqlUpdateWithIntArgs(ps, oldID, newID);
		}
	}

	private void updateFacetCounts(final int facet, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		updateFacetsCounts(Collections.singleton(facet), out);
	}

	/**
	 * Recompute n_items and n_child_facets for these leafFacets and all their
	 * ancestors. Add a dummy instance if there are no real instances yet.
	 *
	 * @param facets
	 * @param out
	 *            for all renamed facets and their ancestors, write:
	 *
	 *            facet_id, old_facet_id, n_items, first_child_offset,
	 *            parent_facet_id
	 *
	 * @throws SQLException
	 * @throws ServletException
	 * @throws IOException
	 */
	private void updateFacetsCounts(final Collection<Integer> facets, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		final int[] updated = updateFacetsCounts(facets);
		sendUpdatedFacetsCounts(updated, out);
	}

	/**
	 * Recompute n_items and n_child_facets for these facets and all their
	 * ancestors. Add a dummy instance if there are no real instances yet.
	 */
	@SuppressWarnings("resource")
	private int[] updateFacetsCounts(final Collection<Integer> facets)
			throws SQLException, ServletException, IOException {
		final PreparedStatement psNchildren = jdbc.lookupPS("SELECT COUNT(*) FROM facet WHERE parent_facet_id = ?");
		final PreparedStatement psNitems = jdbc
				.lookupPS("SELECT COUNT(*) FROM item_facetNtype_heap WHERE facet_id = ?");
		final PreparedStatement psUpdate = jdbc
				.lookupPS("UPDATE facet SET n_items = ?, n_child_facets = ? WHERE facet_id = ?");
		int[] updated = null;
		for (final Integer leafFacetID : facets) {
			for (int ancestorID = leafFacetID.intValue(); ancestorID > 0
					&& !ArrayUtils.contains(updated, ancestorID); ancestorID = parentFacetID(ancestorID)) {
				int nItems = jdbc.sqlQueryInt(psNitems, ancestorID);
				if (nItems == 0) {
					addItemFacet(ancestorID, 0, null);
					nItems = 1;
				}
				final int nChildren = jdbc.sqlQueryInt(psNchildren, ancestorID);
				jdbc.sqlUpdateWithIntArgs(psUpdate, nItems, nChildren, ancestorID);
				updated = ArrayUtils.add(updated, 0, ancestorID);
			}
		}
		return updated;
	}

	/**
	 * For each updatedFacet, write:
	 *
	 * [facet_id, old_facet_id, n_items, first_child_offset, parent_facet_id]
	 */
	private void sendUpdatedFacetsCounts(final int[] updatedFacets, final DataOutputStream out)
			throws SQLException, ServletException, IOException {
		if (out == null) {
			errorp("out is null", "sendUpdatedFacetsCounts");
		}
		try (final ResultSet rs = jdbc
				.sqlQuery("SELECT facet_id, facet_id, n_items, " + "first_child_offset, parent_facet_id "
						+ "FROM facet " + "WHERE facet_id IN (" + UtilString.join(updatedFacets) + ") ORDER BY facet_id"
		// "SELECT facet.facet_id facet_id, IFNULL(old_facet_id, facet.facet_id)
		// old, n_items, "
		// + "first_child_offset, parent_facet_id "
		// + "FROM facet LEFT JOIN renames USING (facet_id) "
		// + "WHERE facet.facet_id IN ("
		// + UtilString.join(updatedFacets)
		// + ") ORDER BY facet.facet_id"
		);) {
			// logp(MyResultSet.valueOfDeep(rs), MyLogger.WARNING,
			// "writeUpdatedCounts");
			Encoder.sendResultSet(rs, MyResultSetColumnTypeList.SMINT_INT_INT_INT_INT, out);
		}
	}

	// /**
	// * For all updatedFacets and renamed facets, write:
	// *
	// * [facet_id, old_facet_id, n_items, first_child_offset, parent_facet_id]
	// */
	// private void updateRenames(final int[] updatedFacets,
	// final DataOutputStream out) throws SQLException, ServletException,
	// IOException {
	// myAssertp(out != null, "out is null", "updateRenames");
	// if (out != null) {
	// muckWithFacetTable();
	// // jdbc.print(jdbc.SQLqueryString("SELECT
	// // group_concat(concat(old_facet_id, ' => ', facet_id)) FROM renames
	// // GROUP BY 1"));
	// final String renamed = jdbc
	// .sqlQueryString("SELECT GROUP_CONCAT(facet_id) FROM renames");
	// final String updated = UtilString.join(updatedFacets) + ", "
	// + renamed;
	// try (final ResultSet rs = jdbc
	// .sqlQuery("SELECT facet.facet_id facet_id, IFNULL(old_facet_id,
	// facet.facet_id) old, n_items, "
	// + "first_child_offset, parent_facet_id "
	// + "FROM facet LEFT JOIN renames USING (facet_id) "
	// + "WHERE facet.facet_id IN ("
	// + updated
	// + ") ORDER BY facet.facet_id");) {
	// logp(MyResultSet.valueOfDeep(rs), MyLogger.WARNING,
	// "updateRenames");
	// Encoder.sendResultSet(rs,
	// MyResultSetColumnTypeList.SMINT_INT_INT_INT_INT, out);
	// }
	// jdbc.dropTable("renames");
	// }
	// }

	/**
	 * Set facetTableMucked, create renames
	 *
	 * Only renumber -> changeID writes to renames. addChildFacet and reparent
	 * read from it.
	 */
	private void muckWithFacetTable() throws SQLException {
		if (!facetTableMucked) {
			facetTableMucked = true;
			// ensureFacetMap();
			jdbc.sqlUpdate(
					"CREATE TEMPORARY TABLE renames" + " (old_facet_id INT, facet_id INT, PRIMARY KEY (facet_id))");
		}
	}

	// /**
	// * The only reason for facet_map is to retrieve raw_facet.sort, which is
	// not
	// * present in facet.
	// */
	// private void ensureFacetMap() throws SQLException {
	// // Get the column data types from raw_facet, as sort type can vary.
	// // Then truncate it so we can insert the real data.
	// if (!jdbc.tableExists("facet_map")) {
	// jdbc.sqlUpdate("CREATE TEMPORARY TABLE facet_map AS"
	// +
	// " (SELECT facet_id canonical_facet_id, parent_facet_id raw_facet_id, sort
	// FROM raw_facet LIMIT 1)");
	// jdbc.sqlUpdate("TRUNCATE TABLE facet_map");
	//
	// // Insert facet_types
	// jdbc.sqlUpdate("INSERT INTO facet_map (SELECT facet_id, facet_type_id,
	// sort"
	// + " FROM facet INNER JOIN raw_facet_type USING (name)"
	// + " WHERE parent_facet_id = 0)");
	//
	// // Insert facets
	// try (final ResultSet rs = jdbc
	// .sqlQuery("SELECT canonical_facet_id, raw_facet_id FROM facet_map");) {
	// while (rs.next()) {
	// final int facet = rs.getInt(1);
	// final int raw_facet = rs.getInt(2);
	// ensureFacetMapInternal(facet, raw_facet);
	// }
	// }
	// jdbc.sqlUpdate("ALTER TABLE facet_map ADD PRIMARY KEY
	// (canonical_facet_id)");
	// }
	// }
	//
	// @SuppressWarnings("resource")
	// private void ensureFacetMapInternal(final int parent, final int
	// raw_parent)
	// throws SQLException {
	// final PreparedStatement insertPS = jdbc
	// .lookupPS("INSERT INTO facet_map (SELECT f.facet_id, rf.facet_id,
	// rf.sort"
	// + " FROM facet f, raw_facet rf"
	// +
	// " WHERE f.parent_facet_id = ? AND rf.parent_facet_id = ? AND f.name =
	// rf.name)");
	// insertPS.setInt(1, parent);
	// insertPS.setInt(2, raw_parent);
	// jdbc.sqlUpdate(insertPS);
	//
	// final PreparedStatement findPS = jdbc
	// .lookupPS("SELECT f.facet_id facet, rf.facet_id raw_facet"
	// + " FROM facet f, raw_facet rf"
	// +
	// " WHERE f.parent_facet_id = ? AND rf.parent_facet_id = ? AND f.name =
	// rf.name"
	// + " AND f.n_child_facets > 0");
	// findPS.setInt(1, parent);
	// findPS.setInt(2, raw_parent);
	// try (final ResultSet rs = jdbc.sqlQuery(findPS);) {
	// final int[][] rsCache = new int[MyResultSet.nRows(rs)][];
	// int i = 0;
	// while (rs.next()) {
	// // Have to store these temporarily because recursive call reuses
	// // findPS
	// final int facet = rs.getInt(1);
	// final int raw_facet = rs.getInt(2);
	// final int[] temp = { facet, raw_facet };
	// rsCache[i++] = temp;
	// }
	// for (final int[] is : rsCache) {
	// ensureFacetMapInternal(is[0], is[1]);
	// }
	// }
	// }

	void writeBack() throws SQLException {
		if (facetTableMucked) {
			final int delta = jdbc.sqlQueryInt("SELECT MAX(facet_id) FROM facet");
			updateFacetIDs(delta);
			recomputeRawFacetType(delta);
			recomputeRawFacet();

			final Compile converter = new Compile(jdbc);
			converter.findBrokenLinks(true);
			converter.compile(!jdbc.tableExists("correlations"));
		}
	}

	/**
	 * Renumber non-top-level facet.facet_id's to leave a big gap after the
	 * top-level ones
	 *
	 * @param delta
	 * @throws SQLException
	 */
	private void updateFacetIDs(final int delta) throws SQLException {
		jdbc.sqlUpdate("UPDATE facet f, facet parent SET f.parent_facet_id = f.parent_facet_id + " + delta
				+ " WHERE f.parent_facet_id = parent.facet_id " + "AND parent.parent_facet_id > 0");
		jdbc.sqlUpdate("UPDATE facet SET facet_id = facet_id + " + delta + " WHERE parent_facet_id > 0");

		// It turns out to be faster to use item_facet as a scratchpad compared
		// to item_facet_heap.
		jdbc.sqlUpdate("TRUNCATE TABLE item_facet");
		jdbc.sqlUpdate("INSERT INTO item_facet SELECT * FROM item_facetNtype_heap WHERE facet_id > " + db.maxFacetType);
		jdbc.sqlUpdate("UPDATE item_facet SET facet_id = facet_id + " + delta);
		jdbc.sqlUpdate("DELETE FROM item_facet WHERE record_num = 0");
	}

	private void recomputeRawFacetType(final int delta) throws SQLException {
		jdbc.dropTable("rft");
		jdbc.sqlUpdate("CREATE TEMPORARY TABLE rft AS" + " SELECT IFNULL(facet_id, -1) oldID, COUNT(*) ID, rft.name, "
				+ "rft.descriptionCategory, rft.descriptionPreposition, rft.sort, rft.isOrdered "
				+ "FROM raw_facet_type rft LEFT JOIN facet USING (name) "
				+ "INNER JOIN raw_facet_type prev ON prev.name <= rft.name " + "WHERE parent_facet_id = 0 "
				// + "OR parent_facet_id IS NULL "
				+ "GROUP BY rft.name ORDER BY null");

		recomputeRawFacetTypeInternal(delta, "parent_facet_id");
		recomputeRawFacetTypeInternal(delta, "facet_id");

		jdbc.sqlUpdate("TRUNCATE TABLE raw_facet_type");
		jdbc.sqlUpdate("INSERT INTO raw_facet_type SELECT ID, name, "
				+ "descriptionCategory, descriptionPreposition, sort, isOrdered " + "FROM rft");
		jdbc.dropTable("rft");
	}

	// Use high IDs in facet as a scratchpad for updating all rows in parallel
	private void recomputeRawFacetTypeInternal(final int delta, final String column) throws SQLException {
		jdbc.sqlUpdate("UPDATE facet f, rft SET f." + column + " = rft.ID + " + (2 * delta) + " WHERE f." + column
				+ " = rft.oldID");
		jdbc.sqlUpdate("UPDATE facet SET " + column + " = " + column + " - " + (2 * delta) + " WHERE " + column + " > "
				+ (2 * delta));
	}

	// facet_map is: raw_facet_id, canonical_facet_id, sort
	private void recomputeRawFacet() throws SQLException {
		jdbc.sqlUpdate("TRUNCATE TABLE raw_item_facet");
		jdbc.sqlUpdate("INSERT INTO raw_item_facet SELECT * FROM item_facet");

		jdbc.sqlUpdate("TRUNCATE TABLE raw_facet");
		jdbc.sqlUpdate("INSERT INTO raw_facet SELECT facet_id, name, parent_facet_id, sort" + " FROM facet"
				+ " LEFT JOIN facet_map ON canonical_facet_id = facet_id " + "WHERE parent_facet_id > 0");
	}

	private void ensureBackup() throws SQLException, IOException, InterruptedException {
		// try {
		if (!jdbc.isSqlQueryIntPositive("SELECT @@log_bin")) {
			errorp("MySQL binary logging is not enabled.  You should start MySQL with log-bin option.", "ensureBackup");
		}

		// e.g. C:\ProgramData\MySQL\MySQL Server 5.6\data\
		final File dataDir = new File(jdbc.sqlQueryString("SELECT @@datadir"));
		final File dbDir = new File(dataDir, dbName());
		// e.g. C:\ProgramData\MySQL\MySQL Server 5.6\dataBKP\personal
		final File bkpDir = ensureDirectory(ensureDirectory(dataDir.getParentFile(), "dataBKP"), dbName());

		final File restoreDatesFile = new File(bkpDir, "restoreDates.txt");
		if (!restoreDatesFile.exists()) {
			logRestore(bkpDir);
			revertTableFiles(bkpDir, dbDir);
		}
		// } catch (final Exception e) {
		// e.printStackTrace();
		// }
	}

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private static void logRestore(final File bkpDir) {
		final File restoreDatesFile = new File(bkpDir, "restoreDates.txt");
		final List<String> restoreDates = restoreDatesFile.exists()
				? Arrays.asList(UtilFiles.readFile(restoreDatesFile)) : new ArrayList<String>(1);
		synchronized (DATE_FORMAT) {
			restoreDates.add(DATE_FORMAT.format(new Date()));
			UtilFiles.writeFile(restoreDatesFile, UtilString.join(restoreDates, "\n"));
		}
	}

	private static File ensureDirectory(final File parentDirectory, final String directoryName) {
		final File result = new File(parentDirectory, directoryName);
		if (!result.exists()) {
			final boolean success = result.mkdirs();
			assert success : result;
		}
		return result;
	}

	/**
	 * revert database to most recent restore before dateString, and then use
	 * mysqlbinlog to catch up to dateString.
	 */
	void revert(final String dateString, final String user, final String pass)
			throws ParseException, IOException, InterruptedException {
		// e.g. C:\Program Files\MySQL\MySQL Server 5.6\bin
		final File mySQLbinDir = new File(jdbc.sqlQueryString("SELECT @@basedir") + "bin");

		// e.g. C:\ProgramData\MySQL\MySQL Server 5.6\data\
		final File dataDir = new File(jdbc.sqlQueryString("SELECT @@datadir"));
		final File dbDir = new File(dataDir, dbName());

		// e.g. C:\ProgramData\MySQL\MySQL Server 5.6\dataBKP\personal
		final File bkpDir = new File(new File(dataDir.getParentFile(), "dataBKP"), dbName());
		// revertTableFiles stops MySQL, so jdbc gets disconnected. Set to null
		// to get a clear error quickly.
		// jdbc = null;
		revertTableFiles(dbDir, bkpDir);
		logRestore(bkpDir);

		synchronized (DATE_FORMAT) {
			final Date date = DATE_FORMAT.parse(dateString);
			final Date restoreDate = restoreDate(date, bkpDir);
			final String userPass = " --user=" + user + " --password=" + pass;
			exec("mysqlbinlog --short-form --database=" + dbName() + userPass + " --start-datetime=\""
					+ DATE_FORMAT.format(restoreDate) + "\" --stop-datetime=\"" + DATE_FORMAT.format(date) + "\""
					+ getLogFileNames(dataDir, restoreDate) + " | " + "mysql" + userPass
					+ " --force --one-database --database=" + dbName(), mySQLbinDir);
			logp("revert to " + restoreDate + " completed", MyLogger.INFO, "revert");
		}
	}

	private static String getLogFileNames(final File dataDir, final Date from) {
		// Grab all the log files, and rely on from & date to pick out the
		// operations we want
		final StringBuilder logFiles = new StringBuilder();
		final File[] logFileNames = dataDir.listFiles(UtilFiles.getFilenameFilter(".*\\.\\d+"));
		assert logFileNames != null : dataDir;
		for (final File file : logFileNames) {
			if (new Date(file.lastModified()).after(from)) {
				logFiles.append(" \"").append(file.getAbsolutePath()).append("\"");
			}
		}
		return logFiles.toString();
	}

	/**
	 * Find the Date of the last restore before date, and then log this new
	 * restore in "restoreDates.txt".
	 *
	 * @return the Date of the last restore before date
	 */
	private Date restoreDate(final Date date, final File bkpDir) throws ParseException {
		final File restoreDatesFile = new File(bkpDir, "restoreDates.txt");
		final String restoreFileString = UtilFiles.readFile(restoreDatesFile);
		final String[] restoreDateStrings = restoreFileString.split("\n");
		// restoreFileString += "\n" + DATE_FORMAT.format(new Date());
		// UtilFiles.writeFile(restoreDatesFile, restoreFileString);

		Date from = null;
		for (final String restoreDateString : restoreDateStrings) {
			synchronized (DATE_FORMAT) {
				final Date restoreDate = DATE_FORMAT.parse(restoreDateString);
				if (!restoreDate.after(date)) {
					from = restoreDate;
				} else if (from == null) {
					errorp("Attempt to revert to " + date + ", which predates the full backup of " + restoreDate,
							"revert");
				} else {
					break;
				}
			}
		}
		return from;
	}

	private void revertTableFiles(final File dbDir, final File bkpDir) throws IOException, InterruptedException {
		// Make sure logs are flushed and that files won't be in use when we
		// try to delete them
		exec("net stop \"MySQL\"");

		final String[] corruptTables = { "facet", "raw_facet", "raw_facet_type", "raw_item_facet", "globals", "item",
				"item_facet", "item_facetNtype_heap" };
		for (final String corruptTable : corruptTables) {
			exec("del /S /Q \"" + corruptTable + ".*", dbDir);
			exec("copy \"" + bkpDir + "\\" + corruptTable + ".* .", dbDir);
		}

		exec("net start \"MySQL\"");
	}

	private void exec(final String command) throws IOException, InterruptedException {
		exec(command, null);
	}

	private void exec(String command, final File workingDirectory) throws IOException, InterruptedException {
		command = command.trim();
		if (command.charAt(0) != '"') {
			// I don't really understand this, but it avoids the Scylla of
			// never returning and the Charybdis of "'C:/Program' is not
			// recognized as an internal or external command"
			command = "cmd.exe /C " + command;
		}
		final String descString = "In directory " + workingDirectory + ", for " + command;
		logp(descString, MyLogger.INFO, "exec");
		Process process = null;
		try {
			process = Runtime.getRuntime().exec(command, null, workingDirectory);
			try (final InputStream inputstream = process.getInputStream();) {
				if (inputstream != null) {
					final String inputString = UtilFiles.inputStreamToString(inputstream);
					logp(descString + "\n output = " + inputString, MyLogger.INFO, "exec");
				}
			}

			// check for failure
			final int exitValue = process.waitFor();
			if (exitValue != 0) {
				String errorString = descString + "\n exit value = " + exitValue;
				try (final InputStream errorStream = process.getErrorStream();) {
					if (errorStream != null) {
						errorString += "\n" + UtilString.html2text(UtilFiles.inputStreamToString(errorStream));
					}
				}
				errorp(errorString, "exec");
			}
		} finally {
			if (process != null) {
				process.destroy();
			}
		}
	}

	private void checkNonNegative(final int id) {
		if (id < 0) {
			errorp("Bad ID: " + id, "checkNonNegative");
		}
	}

	private void checkPositive(final int id) {
		if (id <= 0) {
			errorp("Bad ID: " + id, "checkPositive");
		}
	}

	private int parentFacetID(final int facet) throws SQLException {
		return jdbc.sqlQueryInt("SELECT parent_facet_id FROM facet WHERE facet_id = " + facet);
	}

}
