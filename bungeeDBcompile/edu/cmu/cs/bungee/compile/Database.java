package edu.cmu.cs.bungee.compile;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * Utilities for low level updating of Bungee View items and facets. Assumes
 * you're populating a database, so keeps track of the next facet_id and
 * record_num to be created.
 */
public class Database {

	/**
	 * Don't update the database; just print the SQL updates
	 */
	private static final boolean DONT_UPDATE = false;
	public static final int MAX_THUMBS = 100_000;

	private final boolean verbose;
	private final @NonNull JDBCSample jdbc;
	private final @NonNull String descriptionFields;
	private final @NonNull PreparedStatement newItemQuery;

	/**
	 * Truncate names to this length before inserting
	 */
	private int maxNameLength = -1;

	/**
	 * The facet_id of the most recently created facet.
	 */
	private int facetID = -1;

	/**
	 * Detect when we start creating items, and cache all facets. Then as long
	 * as no one else updates the database, we can just work with the cache. As
	 * soon as someone asks for getJDBC, we update the database and clear our
	 * cache.
	 */
	private FacetCache facetCache;

	/**
	 * The implicit item argument for addItemFacet, getItemAttribute,
	 * insertAttribute, insertFacet
	 */
	int recordNum;

	private final int maxRawFacetTypeID;

	public Database(final @NonNull JDBCSample _jdbc, final boolean _verbose) throws SQLException {
		verbose = _verbose;
		jdbc = _jdbc;
		maxRawFacetTypeID = jdbc.sqlQueryInt("SELECT MAX(facet_type_id) FROM raw_facet_type");

		// String _descriptionFields = null;
		// try {
		// // createTablesIfNotExists(copyTablesFrom);

		recordNum = Math.max(1, jdbc.sqlQueryInt("SELECT MAX(record_num) FROM item"));
		descriptionFields = itemDescriptionFields();

		// } catch (final SQLException e) {
		// e.printStackTrace();
		// }
		// descriptionFields = _descriptionFields;
		newItemQuery = getNewItemQuery();
	}

	// TODO Remove unused code found by UCDetector
	// JDBCSample getJdbc() {
	// // No one else should muck with the database while we're reading data,
	// // so we can cache stuff.
	// try {
	// startReadingData(false);
	// } catch (final SQLException e) {
	// e.printStackTrace();
	// }
	// return jdbc;
	// }

	public @NonNull PreparedStatement lookupPS(final @NonNull String sql) throws SQLException {
		return jdbc.lookupPS(sql);
	}

	public boolean verbose() {
		return verbose;
	}

	boolean isReadingData() {
		return facetCache != null;
	}

	public void startReadingData() throws SQLException {
		if (!isReadingData()) {
			facetCache = new FacetCache();
		}
	}

	// /**
	// * This has to be here instead of in populate to avoid circular
	// dependencies
	// * between initializing facetID and creating the tables.
	// */
	// void createTablesIfNotExists(String dbToCopyFrom) throws SQLException {
	// final boolean dontOverwrite = true;
	// if (dbToCopyFrom == null) {
	// dbToCopyFrom = "hp4";
	// }
	//
	// final String[] tablesToCopy = { "raw_facet_type", "globals" };
	// for (final String table : tablesToCopy) {
	// createTable(table, dbToCopyFrom, dontOverwrite, true);
	// }
	//
	// final String[] tablesToCreate = { "images", "raw_facet",
	// "user_actions", "raw_item_facet", "item" };
	// for (final String table : tablesToCreate) {
	// createTable(table, dbToCopyFrom, dontOverwrite, false);
	// }
	// }
	//
	// private void createTable(final String table, final String dbToCopyFrom,
	// final boolean dontOverwrite, final boolean isCopy)
	// throws SQLException {
	// if (!dontOverwrite || !jdbc.tableExists(table)) {
	// updateOrPrint("DROP TABLE IF EXISTS " + table);
	// updateOrPrint("CREATE TABLE " + table + " LIKE " + dbToCopyFrom
	// + "." + table);
	// if (isCopy) {
	// updateOrPrint("INSERT INTO " + table + " SELECT * FROM "
	// + dbToCopyFrom + "." + table);
	// }
	// }
	// }

	private int recordNum() {
		assert recordNum > 0;
		return recordNum;
	}

	private @NonNull PreparedStatement getNewItemQuery() throws SQLException {
		PreparedStatement result = null;
		// record_num, facet_names, description fields
		final int nCols = jdbc.columnNames("item").length;
		final StringBuilder query = new StringBuilder("INSERT INTO item VALUES(?");

		// 1 is for record_num, specifid above
		for (int i = 1; i < nCols; i++) {
			query.append(", NULL");
		}
		query.append(")");
		final String sql = query.toString();
		assert sql != null;
		result = lookupPS(sql);
		return result;
	}

	private @NonNull String itemDescriptionFields() {
		return jdbc.sqlQueryString("SELECT itemDescriptionFields FROM globals");
	}

	public void newItem() throws SQLException {
		newItem(recordNum + 1);
	}

	public void newItem(final int itemID) throws SQLException {
		assert itemID > 0 : itemID;
		try {
			startReadingData();
			recordNum = itemID;
			if (!DONT_UPDATE) {
				jdbc.sqlUpdateWithIntArgs(newItemQuery, itemID);
			}
		} catch (final SQLException e) {
			System.err.println("dbScripts.Database " + newItemQuery);
			throw (e);
		}
	}

	public void setRecordNum(final int record_num) throws SQLException {
		startReadingData();
		recordNum = record_num;
	}

	@SuppressWarnings("resource")
	public boolean insertAttribute(final String column, final String value) throws SQLException {
		final PreparedStatement updateItem = lookupPS("UPDATE item SET " + column + " = ? WHERE record_num = ?");
		updateItem.setString(1, value);
		updateItem.setInt(2, recordNum);
		final int nRows = updateOrPrint(updateItem, "Update item");
		return nRows > 0;
	}

	// This is the only call we have to worry about as far as the facetCache
	public void insertFacet(final List<String> hierValues) throws SQLException {
		// System.out.println("Database.insertFacet " + hierValues);
		final int[] facets = getFacets(hierValues);
		for (final int facet : facets) {
			addItemFacet(facet);
		}
	}

	public int[] getFacets(final @NonNull String hierValues) throws SQLException {
		return getFacets(Arrays.asList(Compile.ancestors(hierValues)));
	}

	public int[] getFacets(final List<String> hierValues) throws SQLException {
		// System.out.println("Database.getFacets " + hierValues);
		assert isReadingData() : hierValues;

		// facetCache.getFacets will create ancestors
		return facetCache.getFacets(hierValues);
	}

	// int getFacet(final List<String> hierValues) throws SQLException {
	// // System.out.println("getFacet " + hierValue);
	// assert isReadingData();
	// int result;
	// // if (isReadingData()) {
	// // facetCache.getFacet will create ancestors
	//
	// result = facetCache.getFacet(hierValue);
	//
	// // } else {
	// // int ancestor = 0;
	// // for (final String name : hierValue) {
	// // final int child = lookupFacet(name, ancestor);
	// // if (child > 0) {
	// // ancestor = child;
	// // } else {
	// // ancestor = newFacet(name, ancestor);
	// // }
	// // }
	// // result = ancestor;
	// // }
	// return result;
	// }

	/**
	 * Cached value of raw_facet - hopefully faster access during parsing.
	 */
	private class FacetCache {
		// The int's are ordered from ancestor to descendent
		private final Map<List<String>, int[]> names2hierIDs = new Hashtable<>();

		@SuppressWarnings("resource")
		FacetCache() throws SQLException {
			final int nFacets = nextFacetID();
			UtilString
					.printDateNmessage("Starting to create records, with <= " + nFacets + " tags already in database.");
			final Map<Integer, List<String>> id2hierValues = new HashMap<>(nFacets);

			final PreparedStatement facetTypePS = lookupPS("SELECT facet_type_id, name FROM raw_facet_type");
			try (final ResultSet facetTypeRS = getJdbc().sqlQuery(facetTypePS);) {
				while (facetTypeRS.next()) {
					final List<String> hierValues = new ArrayList<>(1);
					final String facetTypeName = facetTypeRS.getString("name");
					hierValues.add(facetTypeName);
					final int facetTypeID = facetTypeRS.getInt("facet_type_id");
					final int[] hierIDs = { facetTypeID };
					names2hierIDs.put(hierValues, hierIDs);
					id2hierValues.put(facetTypeID, hierValues);
				}
			}

			final PreparedStatement facetPS = lookupPS(
					"SELECT facet_id, name, parent_facet_id FROM raw_facet ORDER BY parent_facet_id");
			try (final ResultSet facetRS = getJdbc().sqlQuery(facetPS);) {
				while (facetRS.next()) {
					final int facetID1 = facetRS.getInt("facet_id");
					final String facetName = facetRS.getString("name");
					final int parentID = facetRS.getInt("parent_facet_id");

					final List<String> parentHierValues = id2hierValues.get(parentID);
					assert parentHierValues != null : "parent is null: facet_id=" + facetID1 + " name=" + facetName
							+ " parentID=" + parentID;
					final int parentSize = parentHierValues.size();
					final List<String> hierValues = new ArrayList<>(parentSize + 1);
					hierValues.addAll(parentHierValues);
					hierValues.add(facetName);

					final int[] hierIDs = new int[parentSize + 1];
					System.arraycopy(names2hierIDs.get(parentHierValues), 0, hierIDs, 0, parentSize);
					hierIDs[parentSize] = facetID1;
					names2hierIDs.put(hierValues, hierIDs);

					id2hierValues.put(facetID1, hierValues);
				}
			}
		}

		int[] getFacets(final List<String> hierValues) throws SQLException {
			// final Integer id = names2hierIDs.get(hierValue);
			int[] hierIDs = names2hierIDs.get(hierValues);
			if (hierIDs == null) {
				final int nAncestors = hierValues.size() - 1;
				assert nAncestors > 0 : "Can't find tag category " + hierValues + ". Must add it to raw_facet_type.";
				// Defensive copy
				final ArrayList<String> hierValuesCopy = new ArrayList<>(hierValues);
				final List<String> parentHierValues = hierValuesCopy.subList(0, nAncestors);
				assert parentHierValues.size() == nAncestors : nAncestors + " " + parentHierValues.size() + " "
						+ hierValues + " " + parentHierValues;
				final int[] ancestorIDs = getFacets(parentHierValues);
				assert ancestorIDs.length == nAncestors;

				final int newFacet = newFacet(hierValuesCopy.get(nAncestors), ancestorIDs[nAncestors - 1]);

				hierIDs = new int[nAncestors + 1];
				System.arraycopy(ancestorIDs, 0, hierIDs, 0, nAncestors);
				hierIDs[nAncestors] = newFacet;
				names2hierIDs.put(hierValuesCopy, hierIDs);
			}
			assert hierIDs.length > 0;
			return hierIDs;
		}

	}

	@SuppressWarnings("resource")
	private void addItemFacet(final int facet_id) throws SQLException {
		// System.out.println("addItemFacet facet_id=" + facet_id +
		// " recordNum="
		// + recordNum());

		// These are a significant slowdown for big elamp databases.
		// Just wait for ConvertFromRaw.findBrokenLinks to find values that
		// don't correspond to valid record_nums/facet_ids.
		// assert getName(facet_id) != null;
		// assert itemExists() : recordNum();

		final PreparedStatement itemFacetPS = lookupPS("REPLACE INTO raw_item_facet VALUES(?, ?)");
		itemFacetPS.setInt(1, recordNum());
		itemFacetPS.setInt(2, facet_id);
		if (DONT_UPDATE) {
			System.out.println(itemFacetPS);
		} else {
			// System.out.println("dbScripts.Database.addItemFacet: "
			// + jdbc.dbName + " " + itemFacetPS);
			jdbc.sqlUpdate(itemFacetPS);
		}
	}

	int nextFacetID() throws SQLException {
		if (facetID < 0) {
			facetID = Math.max(jdbc.sqlQueryInt("SELECT MAX(facet_id) FROM raw_facet"), maxRawFacetTypeID + 100);
		}
		return ++facetID;
	}

	@SuppressWarnings("resource")
	public int newFacet(String name, final int parent) throws SQLException {
		if (name.startsWith(" ")) {
			System.err.println(name + " " + getRawFacetName(parent) + UtilString.getStackTrace());
		}
		// System.out.println("Database.newFacet parent=" + parent + " name="
		// + name);
		name = truncate_raw_facetName(name, false);
		if (DONT_UPDATE) {
			System.out.println("INSERT INTO raw_facet VALUES(<next facet_id>, " + name + ", " + parent + ", null)");
		} else {
			// facetCache compares names in binary mode, so may create facets
			// seen as duplicates by MySQL. We'll merge them later.
			try {
				assert isReadingData() || lookupFacet(name, parent) < 0 : name + " (" + lookupFacet(name, parent) + ") "
						+ getRawFacetName(parent) + " (" + parent + ")";

				final PreparedStatement newFacetPS = lookupPS("INSERT INTO raw_facet VALUES(?, ?, ?, NULL)");
				final int nextFacetID = nextFacetID();
				assert parent < nextFacetID : parent + "." + nextFacetID;
				newFacetPS.setInt(1, nextFacetID);
				newFacetPS.setString(2, name);
				newFacetPS.setInt(3, parent);
				jdbc.sqlUpdate(newFacetPS);
			} catch (final Throwable e) {
				System.err.println("Problem creating facet: name='" + name + "'; parent=" + getRawFacetName(parent));
				e.printStackTrace();
			}
		}
		return facetID;
	}

	public String truncate_raw_facetName(String name, final boolean noWarn) throws SQLException {
		assert name.length() > 0;
		if (maxNameLength < 0) {
			maxNameLength = jdbc.sqlQueryInt("SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
					+ "WHERE table_name = 'raw_facet' AND column_name = 'name'" + " AND table_schema = '" + jdbc.dbName
					+ "'");
			// log("Setting maxNameLength to " + maxNameLength);
		}
		if (name.length() > maxNameLength) {
			final String tName = name.substring(0, maxNameLength - 1);
			if (!noWarn) {
				System.out.println("Truncating facet '" + name + "' to '" + tName + "'");
			}
			name = tName;
		}
		return name.replace('\n', '↳');
	}

	@SuppressWarnings("resource")
	void setParent(final int child, final int newParent) {
		// assert 0 <= newParent && newParent < child : newParent + "." + child
		// + "\n newParent="
		// + rawAncestorString(newParent) + "\n child=" +
		// rawAncestorString(child);
		final String rawFacetName = getRawFacetName(child);
		assert rawFacetName != null : child;
		try {
			final PreparedStatement setParent = lookupPS("UPDATE raw_facet SET parent_facet_id = ? WHERE facet_id = ?");
			setParent.setInt(1, newParent);
			setParent.setInt(2, child);
			if (!DONT_UPDATE) {
				jdbc.sqlUpdateWithIntArgs(setParent);

				// final PreparedStatement ps =
				// lookupPS("SELECT facet_id FROM raw_facet "
				// + "WHERE parent_facet_id = ? AND name = ? LIMIT 1");
				// ps.setInt(1, newParent);
				// ps.setString(2, rawFacetName);
				//
				// // If there is an existing child with the same name, they
				// will
				// // be merged later
				// // final int oldChild =
				//
				// jdbc.sqlQueryInt(ps);

				// // if (oldChild > 0) {
				// // System.err
				// //
				// .println("Database.setParent I don't think this is ever
				// right: child="
				// // + ancestorString(child)
				// // + " newParent="
				// // + ancestorString(newParent)
				// // + " found oldChild="
				// // + ancestorString(oldChild));
				// // // merge(child, oldChild);
				// // } // else {
				// jdbc.sqlUpdate(setParent);
				// // }
			} else {
				System.out.println(setParent);
			}
		} catch (final SQLException e) {
			System.err.println(child + " " + newParent);
			e.printStackTrace();
		}
	}

	/**
	 * Clean up problems caused by comparing strings in binary mode.
	 *
	 * @return nMergers
	 */
	@SuppressWarnings("resource")
	public int mergeDuplicateFacets() throws SQLException {
		int nMergers = 0;
		// final PreparedStatement pairsPS = jdbc.lookupPS("SELECT f1.facet_id,
		// f2.facet_id FROM raw_facet f1"
		// + " INNER JOIN raw_facet f2 USING (parent_facet_id, name) " + "WHERE
		// f1.facet_id < f2.facet_id");

		final PreparedStatement pairsPS = jdbc.lookupPS("SELECT GROUP_CONCAT(facet_id ORDER BY facet_id) AS facet_ids"
				+ " FROM raw_facet" + " GROUP BY parent_facet_id, name" + " HAVING COUNT(*) > 1");

		try (final ResultSet pairs = jdbc.sqlQuery(pairsPS);) {
			final int rows = MyResultSet.nRows(pairs);
			if (rows > 0) {
				System.out.println(rows + " duplicate facet sets to merge...");
				while (pairs.next()) {

					System.out.println(pairs.getString(1));
					final String[] facets = pairs.getString(1).split(",");
					final int keepFacet = Integer.parseInt(facets[0]);
					for (final String facetString : ArrayUtils.subarray(facets, 1, facets.length)) {
						final int duplicateFacet = Integer.parseInt(facetString);
						nMergers += merge(duplicateFacet, keepFacet);
					}

					// final int facet1 = pairs.getInt(1);
					// final int facet2 = pairs.getInt(2);
					// nMergers += merge(facet1, facet2);
				}
				System.out.println("...merging facets done.");
			}
		}
		return nMergers;
	}

	@SuppressWarnings("resource")
	public int merge(final int source, final int dest) throws SQLException {
		// log("Database.merge " + source + " into " + dest);

		final int nChildMergers = mergeChildren(source, dest);
		setChildrensParent(source, dest);

		final String path1 = rawAncestorString(source);
		final String path2 = rawAncestorString(dest);
		assert !path1.equalsIgnoreCase(path2) : "merging " + path1 + " into " + path2;
		log("merging " + path1 + " into " + path2);
		final PreparedStatement mergeRawItemFacetPS = lookupPS(
				"REPLACE INTO raw_item_facet " + "SELECT record_num, ? FROM raw_item_facet WHERE facet_id = ?");
		mergeRawItemFacetPS.setInt(1, dest);
		mergeRawItemFacetPS.setInt(2, source);
		updateOrPrint(mergeRawItemFacetPS, "Update raw_item_facet");

		deleteFacet(source);
		// log("Database.merge return");
		return nChildMergers + 1;
	}

	@SuppressWarnings("resource")
	public int mergeChildren(final int source, final int dest) throws SQLException {
		int[][] childMergers;
		// Find children to merge
		final PreparedStatement mergeChildrenPS = lookupPS(
				"SELECT dest.facet_id AS destID, source.facet_id AS sourceID "
						+ "FROM raw_facet dest, raw_facet source "
						+ "WHERE dest.name = source.name and dest.parent_facet_id = ? AND source.parent_facet_id = ? LIMIT 1");
		mergeChildrenPS.setInt(1, dest);
		mergeChildrenPS.setInt(2, source);
		try (ResultSet rs = jdbc.sqlQuery(mergeChildrenPS);) {
			childMergers = new int[MyResultSet.nRows(rs)][2];
			int index = 0;
			while (rs.next()) {
				final int childsource = rs.getInt("sourceID");
				final int childdest = rs.getInt("destID");
				final int[] merger = { childsource, childdest };
				childMergers[index++] = merger;
			}
		}

		// Must close rs before recursing
		int nMergers = 0;
		for (final int[] childMerger : childMergers) {
			nMergers += merge(childMerger[0], childMerger[1]);
		}
		return nMergers;
	}

	/**
	 * setParent all children of from to to
	 *
	 * @param from
	 * @param to
	 * @throws SQLException
	 */
	@SuppressWarnings("resource")
	void setChildrensParent(final int from, final int to) throws SQLException {
		final PreparedStatement updateRawFacet = lookupPS(
				"UPDATE raw_facet set parent_facet_id = ? " + "WHERE parent_facet_id = ?");
		updateRawFacet.setInt(1, to);
		updateRawFacet.setInt(2, from);
		updateOrPrint(updateRawFacet, "Update raw_facet");
	}

	@SuppressWarnings("resource")
	void deleteFacet(final int facet) throws SQLException {
		final PreparedStatement deleteFromRawFacet = lookupPS("DELETE FROM raw_facet WHERE facet_id = ?");
		deleteFromRawFacet.setInt(1, facet);
		updateOrPrint(deleteFromRawFacet, "Delete from raw_facet");

		final PreparedStatement deleteFromRawItemFacet = lookupPS("DELETE FROM raw_item_facet WHERE facet_id = ?");
		deleteFromRawItemFacet.setInt(1, facet);
		updateOrPrint(deleteFromRawItemFacet, "Delete from raw_facet");
	}

	@SuppressWarnings("resource")
	private void deleteItem(final int item) throws SQLException {
		final PreparedStatement deleteItem = lookupPS("DELETE FROM item WHERE record_num = ?");
		deleteItem.setInt(1, item);
		updateOrPrint(deleteItem, "Delete merged item");

		final PreparedStatement deleteItemFacets = lookupPS("DELETE FROM raw_item_facet WHERE record_num = ?");
		deleteItemFacets.setInt(1, item);
		updateOrPrint(deleteItemFacets, "Delete merged itemFacets");
	}

	@SuppressWarnings("resource")
	public void setName(final int facet, final String name) throws SQLException {
		final PreparedStatement updateFromRawFacet = lookupPS("UPDATE raw_facet SET name = ? " + "WHERE facet_id = ?");
		updateFromRawFacet.setString(1, name);
		updateFromRawFacet.setInt(2, facet);
		updateOrPrint(updateFromRawFacet, "Update facet name");
	}

	/**
	 * Assumes attributes are the same, and we're just collecting all the facets
	 *
	 * @param from
	 * @param to
	 * @throws SQLException
	 */
	@SuppressWarnings("resource")
	public void mergeItems(final int from, final int to) throws SQLException {
		final PreparedStatement mergeFacets = lookupPS(
				"REPLACE INTO raw_item_facet " + "SELECT ?, facet_id FROM raw_item_facet WHERE record_num = ?");
		mergeFacets.setInt(1, to);
		mergeFacets.setInt(2, from);
		updateOrPrint(mergeFacets, "Merge item facets");

		deleteItem(from);
	}

	public int updateOrPrint(final @NonNull String sql) throws SQLException {
		int nRows = 0;
		if (DONT_UPDATE) {
			System.out.println(sql);
		} else {
			// System.out.println("Database.updateOrPrint " + sql);
			nRows = jdbc.sqlUpdate(sql);
		}
		return nRows;
	}

	/**
	 * @return nRows
	 * @throws SQLException
	 */
	public int updateOrPrint(final @NonNull PreparedStatement ps, final String desc) throws SQLException {
		int nRows = 0;
		if (DONT_UPDATE) {
			System.out.println(desc);
		} else {
			nRows = jdbc.sqlUpdate(ps);
		}
		return nRows;
	}

	public void log(final String s) {
		if (verbose) {
			System.out.println(s);
		}
	}

	public String getRawFacetName(final int facet_id) {
		assert facet_id > 0 : facet_id;
		String result = null;
		final String sql = (facet_id > maxRawFacetTypeID) ? "SELECT name FROM raw_facet WHERE facet_id = ?"
				: "SELECT name FROM raw_facet_type WHERE facet_type_id = ?";
		try {
			@SuppressWarnings("resource")
			final PreparedStatement ps = lookupPS(sql);
			ps.setInt(1, facet_id);
			result = jdbc.sqlQueryString(ps);
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public int getRawFacetType(int facet_id) throws SQLException {
		for (int facet_type_id = facet_id; facet_type_id > 0; facet_type_id = getParentRawFacet(facet_type_id)) {
			facet_id = facet_type_id;
		}
		return facet_id;
	}

	@SuppressWarnings("resource")
	private int getParentRawFacet(final int facet_id) throws SQLException {
		assert facet_id > 0 : facet_id;
		final PreparedStatement ps = lookupPS("SELECT parent_facet_id FROM raw_facet WHERE facet_id = ?");
		final int result = jdbc.sqlQueryInt(ps, true, facet_id);
		assert result != facet_id : result + "." + facet_id + " " + (result > 0 ? getRawFacetName(result) + "." : "")
				+ getRawFacetName(facet_id);
		return result;
	}

	/**
	 * @return whether facet_id is in the raw_facet or raw_facet_type tables
	 */
	@SuppressWarnings("resource")
	boolean facetOrTypeExists(final int facet_id) throws SQLException {
		boolean result = false;
		final String sql = (facet_id > maxRawFacetTypeID) ? "SELECT facet_id FROM raw_facet WHERE facet_id = ?"
				: "SELECT facet_type_id FROM raw_facet_type WHERE facet_type_id = ?";
		final PreparedStatement ps = lookupPS(sql);
		result = jdbc.sqlQueryInt(ps, true, facet_id) > 0;
		return result;
	}

	@SuppressWarnings("resource")
	public String getItemAttribute(final String column) throws SQLException {
		final PreparedStatement ps = lookupPS("SELECT " + column + " FROM item WHERE record_num = ?");
		ps.setInt(1, recordNum());
		return jdbc.sqlQueryString(ps);
	}

	@SuppressWarnings("resource")
	public int getFacetType(final @NonNull String name, final boolean missingOK) throws SQLException {
		assert name != null;
		final PreparedStatement ps = lookupPS("SELECT facet_type_id FROM raw_facet_type WHERE name = ?");
		final int result = jdbc.sqlQueryInt(ps, missingOK, name);
		assert missingOK || result > 0 : name;
		return result;
	}

	@SuppressWarnings("resource")
	int nItems(final int facetID1) throws SQLException {
		final PreparedStatement ps = lookupPS("SELECT COUNT(*) FROM raw_item_facet WHERE facet_id = ?");
		return jdbc.sqlQueryInt(ps, facetID1);
	}

	@SuppressWarnings("resource")
	public int lookupFacet(final String name, final int parent) throws Throwable {
		int result = -1;
		final String truncated_raw_facetName = truncate_raw_facetName(name, true);
		assert truncated_raw_facetName != null : name;
		assert parent >= 0 : parent;
		try {
			// Use index hint because it's spending lots of time in 'statistics'
			final PreparedStatement lookupFacet = lookupPS("SELECT facet_id FROM raw_facet USE INDEX (name)"
					+ " WHERE name = ? AND parent_facet_id = ? LIMIT 1");
			synchronized (lookupFacet) {
				lookupFacet.setString(1, truncated_raw_facetName);
				lookupFacet.setInt(2, parent);
				result = jdbc.sqlQueryInt(lookupFacet, true);
			}
		} catch (final Throwable e) {
			System.err.println("While looking up " + getRawFacetName(parent) + " (" + parent + ")." + name + " ("
					+ truncated_raw_facetName + "):\n");
			throw (e);
		}
		// System.out.println("lookupFacet " + name + " " + parent + " => " +
		// result);
		return result;
	}

	@SuppressWarnings("resource")
	public int[] getChildIDs(final int parentFacet) throws SQLException {
		final PreparedStatement ps = lookupPS("SELECT facet_id FROM raw_facet WHERE parent_facet_id = ?");
		ps.setInt(1, parentFacet);
		return jdbc.sqlQueryIntArray(ps);
	}

	public String rawAncestorString(final int raw_facet_id) throws SQLException {
		return UtilString.join(rawAncestors(raw_facet_id, true, true), Compile.FACET_HIERARCHY_SEPARATOR);
	}

	private @NonNull List<String> rawAncestors(final int facet_id, final boolean showID, final boolean showCount)
			throws SQLException {
		List<String> result;
		if (facet_id > 0) {
			result = rawAncestors(getParentRawFacet(facet_id), showID, showCount);
			result.add(getRawFacetName(facet_id) + (showID ? " (" + facet_id + ")" : "")
					+ (showCount ? " [" + nItems(facet_id) + "]" : ""));
		} else {
			result = new LinkedList<>();
		}
		return result;
	}

	@SuppressWarnings("resource")
	public String itemDescription(final int item) throws SQLException {
		final PreparedStatement getDescription = lookupPS(
				"SELECT CONCAT_WS(" + descriptionFields + ") FROM item WHERE record_num = ?");
		getDescription.setInt(1, item);
		return jdbc.sqlQueryString(getDescription);
	}

	/**
	 * Make sure item table columns correspond to globals.itemDescriptionFields
	 *
	 * @return item table columns, in order
	 */
	public String ensureItemColumns(final int maxItems) throws SQLException {
		final String columns = jdbc.sqlQueryString("SELECT GROUP_CONCAT(column_name) FROM information_schema.columns"
				+ " WHERE table_schema='" + jdbc.dbName + "' AND table_name='item'");
		final String textSearchColumns = "facet_names," + itemDescriptionFields();
		final String desiredColumns = "record_num," + textSearchColumns;
		if (!columns.equals(desiredColumns)) {
			System.out.println(
					"dbScripts.Database.ensureItemColumns creating new item table with columns " + desiredColumns);
			final String item_idType = JDBCSample.unsignedTypeForMaxValue(maxItems);
			final StringBuilder createSQL = new StringBuilder();
			createSQL.append("(record_num ").append(item_idType);
			for (final String colName : textSearchColumns.split(",")) {
				createSQL.append(", ").append(colName).append(" text");
			}
			createSQL.append(", PRIMARY KEY (record_num), FULLTEXT KEY search (").append(textSearchColumns)
					.append(")) ENGINE=MyISAM");
			jdbc.dropAndCreateTable("item", createSQL.toString());
			recordNum = 0;
		}
		return desiredColumns;
	}

	public @NonNull JDBCSample getJdbc() {
		return jdbc;
	}

	public int getMaxRawFacetTypeID() {
		return maxRawFacetTypeID;
	}

}
