package edu.cmu.cs.bungee.populate;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.jboss.dna.Inflector;
import org.xml.sax.helpers.DefaultHandler;

import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.compile.Database;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * Provides lower-level methods for populating a database. Only used by
 * Options.populate(), which directs the process (and by subClasses of Options).
 */
public class PopulateHandler extends DefaultHandler {

	/**
	 * Parser can park state information here for the benefit of
	 * FacetValueParser
	 */
	public Object parserState;
	private Compile converter;
	private final Database db;

	Map<String, String> moves;

	private Map<String, String> renames;

	/**
	 * Used by SubjectFacetValueParser. Places within states that help recognize
	 * place names in the subject hierarchy. Applies to the pattern
	 * "<city> ([<place>,] <state abbrev>)"
	 */
	Collection<String> cities;

	private final JDBCSample jdbc;

	protected boolean dontUpdate = false;

	protected PopulateHandler(final @NonNull JDBCSample _jdbc, final boolean _verbose) throws SQLException {
		jdbc = _jdbc;
		db = new Database(_jdbc, _verbose);
	}

	void setRenames(final Collection<String[]> _renames) {
		renames = stringPair2Map(_renames, false);
	}

	public void setMoves(final Collection<String[]> _moves) {
		moves = stringPair2Map(_moves, true);
	}

	void setPlaces(final Collection<String> _places) {
		cities = _places;
	}

	/**
	 * @param name
	 *            known to be a place name
	 * @return <ancestor hierarchy>
	 */
	@SuppressWarnings("resource")
	String[] lookupGAC(final String name) {
		String[] result = null;
		try {
			final PreparedStatement lookupGAC = jdbc.lookupPS("SELECT broader FROM wpa.043places WHERE term = ?");
			lookupGAC.setString(1, name);
			final String place = jdbc.sqlQueryString(lookupGAC);
			assert place != null : name;
			result = Compile.ancestors(place);
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	private static Map<String, String> stringPair2Map(final Collection<String[]> pairs,
			final boolean multipleValuesOK) {
		Map<String, String> result = null;
		if (pairs != null) {
			result = new LinkedHashMap<>();
			for (final String[] entry : pairs) {
				assert entry.length == 2 : "Bad entry: " + UtilString.valueOfDeep(entry);
				final String key = entry[0];
				String value = entry[1];
				final String old = result.get(key);
				if (old != null) {
					if (multipleValuesOK) {
						value += ";" + old;
					} else {
						System.err.println("Warning: Multiple entries for " + key + ": " + old + " " + value);
					}
				}
				result.put(key, value);
			}
		}
		return result;
	}

	void compile(final boolean dontComputeCorrelations) throws SQLException {
		getCompiler().compile(dontComputeCorrelations);
		// reddUpTables(OUTPUT_TABLES);
	}

	private void findBrokenLinks() throws SQLException {
		getCompiler().findBrokenLinks(true);
	}

	private int fixDuplicates() throws SQLException {
		return db.mergeDuplicateFacets();
	}

	private int fixMissingItemFacets() throws SQLException {
		return getCompiler().fixMissingParentRawItemFacets();
	}

	private Compile getCompiler() {
		if (converter == null) {
			converter = new Compile(db);
		}
		return converter;
	}

	// TODO Remove unused code found by UCDetector
	// static final String[] INPUT_TABLES = { "globals", "images", "item",
	// "raw_facet", "raw_facet_type", "raw_item_facet" };

	// void reddUpTables(final String[] tables) throws SQLException {
	// for (final String table : tables) {
	// try (final ResultSet rs = jdbc.sqlQuery("SHOW FULL COLUMNS FROM "
	// + table);) {
	// while (rs.next()) {
	// final String collation = rs.getString("Collation");
	// if (collation != null
	// && !collation.equals("utf8_general_ci")) {
	// final String colName = rs.getString("Field");
	// System.err.println("Changing collation for " + table
	// + "." + colName + " from " + collation
	// + " to utf8");
	// fixCollation(table);
	// }
	// }
	// }
	// }
	// }
	//
	// private void fixCollation(final String table) {
	// sqlUpdate("ALTER TABLE " + table
	// + " CONVERT TO CHARACTER SET 'utf8' COLLATE 'utf8_general_ci'");
	// }
	//
	// private static final String[] OUTPUT_TABLES = { "user_actions", "facet",
	// "item_facet", "item_facetNtype_heap", "item_order",
	// "item_order_heap" };

	protected void clearTables(final int maxItems) throws SQLException {
		db.ensureItemColumns(maxItems);
		final String[] tables = { "raw_facet", "raw_item_facet", "item" };
		for (final String table : tables) {
			db.updateOrPrint("TRUNCATE TABLE " + table);
		}
	}

	void singularizeFacetNames() throws SQLException {
		try (final ResultSet rs = db.getJdbc().sqlQuery("SELECT name FROM raw_facet");) {
			while (rs.next()) {
				final String pluralName = rs.getString(1);
				final String singularName = Inflector.singularize(pluralName.trim());
				if (!singularName.equals(pluralName)) {
					System.out.println(pluralName + " => " + singularName);
					renameRawFacets(pluralName, singularName);
				}
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		if (db.verbose()) {
			printDuplicates();
		}
	}

	void renameNmove() throws Throwable {
		if (renames != null) {
			rename();
		}
		if (moves != null) {
			move(10);
		}
		if (renames != null || moves != null) {
			fixMissingItemFacets();
			fixDuplicates();
			fixMissingItemFacets();
			findBrokenLinks();
		}
	}

	/**
	 * Rename facets according to the explicit list 'rename'. This can correct
	 * common spelling errors, for instance. Looks at all facets, irrespective
	 * of parent.
	 */
	private void rename() throws SQLException {
		for (final Map.Entry<String, String> entry : renames.entrySet()) {
			final String from = db.truncate_raw_facetName(entry.getKey(), false);
			final String to = db.truncate_raw_facetName(entry.getValue(), false);
			renameRawFacets(from, to);
		}
	}

	/**
	 * For all raw_facets named oldName, change their name to newName, merging
	 * if there's already a sibling with newName.
	 */
	@SuppressWarnings("resource")
	private void renameRawFacets(final String oldName, final String newName) {
		try {
			final PreparedStatement ps = jdbc.lookupPS("SELECT oldFacet.facet_id, newFacet.facet_id"
					+ " FROM raw_facet oldFacet LEFT JOIN raw_facet newFacet"
					+ " ON oldFacet.parent_facet_id = newFacet.parent_facet_id AND newFacet.name=?"
					+ " WHERE oldFacet.name = ?");
			ps.setString(1, newName);
			ps.setString(2, oldName);
			try (final ResultSet rs = jdbc.sqlQuery(ps);) {
				while (rs.next()) {
					final int fromID = rs.getInt(1);
					final int toID = rs.getInt(2);
					if (toID > 0) {
						// there's already a sibling with the new name
						db.merge(fromID, toID);
					} else {
						db.setName(fromID, oldName);
					}
				}
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param s
	 *            source facet name
	 * @return possibly renamed facet name
	 */
	String rename(final String s) {
		// System.out.println("rename " + renames);
		final String result = renames == null ? s : renames.getOrDefault(s.toLowerCase(), s);
		return result;
	}

	/**
	 * Change the facet hierarchy by moving the subtrees explicitly listed in
	 * moves. All children of the old lineage are reparented to the new lineage,
	 * which is created if nececssary. The now-childless old parent is then
	 * deleted.
	 *
	 * @throws Throwable
	 */
	private void move(final int maxIterations) throws Throwable {
		fixDuplicates();
		boolean change = false;
		if (maxIterations <= 0) {
			System.err.println("PopulateHandler.move: maxIterations exceeded.");
		} else {
			System.out.println("\n moving [" + maxIterations + "]...");

			// results maps from the parentName to a set of moved children
			// and a set of unmoved children
			final Map<String, List<String>[]> results = new Hashtable<>();
			for (final Entry<String, String> entry : moves.entrySet()) {
				try {
					final String oldName = entry.getKey();
					assert oldName != null;
					final String value = entry.getValue();
					assert value != null;
					final String[] newNames = UtilString.splitSemicolon(value);
					for (final String newName : newNames) {
						assert newName != null;
						final String[] olds = Compile.ancestors(oldName);
						final String[] news = Compile.ancestors(newName);
						@SuppressWarnings("null")
						int parentFacet = db.getFacetType(olds[0], true);
						// assert parentFacet > 0 : "Can't find '" + olds[0]
						// + "' in raw_facet_type";

						for (int j = 1; j < olds.length && parentFacet > 0 && !"*".equals(olds[j]); j++) {
							parentFacet = db.lookupFacet(olds[j], parentFacet);
						}
						final boolean ismoved = parentFacet > 0;
						if (ismoved) {
							final String[] leafs = { olds[olds.length - 1] };
							if ("*".equals(olds[olds.length - 1])) {
								assert false : "No need for '*': " + oldName + " " + newName;
								// leafs = db.getChildIDs(parentFacet);
							}
							for (final String leaf : leafs) {
								olds[olds.length - 1] = leaf;
								logmove(Arrays.asList(olds), ismoved, results);
								@SuppressWarnings("null")
								final int type = db.getFacetType(news[0], false);
								int newParent = type;
								for (int j = 1; j < news.length; j++) {
									int childType = db.lookupFacet(news[j], newParent);
									if (childType < 0) {
										childType = db.newFacet(news[j], newParent);
									}
									newParent = childType;
								}
								assert newParent > 0 : newName;
								assert newParent < 1000000 : newParent;
								assert parentFacet > 0 : oldName;
								assert parentFacet < 1000000 : oldName;
								// moveInternal(parentFacet, newParent);
								db.merge(parentFacet, newParent);
								if (!change) {
									System.out.println("PopulateHandler.move merging " + oldName + " (" + parentFacet
											+ ") and " + newName + "(" + newParent + ")");
								}
								change = true;
							}
						} else {
							// log("move: Can't find " + oldName + " to
							// move");
						}
					}
				} catch (final Throwable e) {
					System.err.println("While PopulateHandler.move " + UtilString.valueOfDeep(entry) + ":\n");
					throw (e);
				}
			}
			printmoveLog(results);
			System.out.println("... moving done");
		}
		if (change) {
			move(maxIterations - 1);
		}
	}

	private void logmove(final List<String> olds, final boolean ismoved, final Map<String, List<String>[]> results) {
		if (db.verbose()) {
			final List<String> names = new LinkedList<>(olds);
			// add a dummy child so it can go in either the good or bad list
			names.add("leaf");
			// System.out.println(names);
			logmoveInternal(names, names.size(), ismoved, results);
		}
	}

	@SuppressWarnings("unchecked")
	private void logmoveInternal(final List<String> olds, final int generation, final boolean ismoved,
			final Map<String, List<String>[]> results) {
		if (generation > 0) {
			final String oldName = UtilString.join(olds.subList(0, generation).toArray(),
					Compile.FACET_HIERARCHY_SEPARATOR);
			final String parentName = generation > 1
					? UtilString.join(olds.subList(0, generation - 1).toArray(), Compile.FACET_HIERARCHY_SEPARATOR)
					: "";
			List<String>[] prevResults = results.get(parentName);
			if (prevResults == null) {
				prevResults = (List<String>[]) new List<?>[2];
				prevResults[0] = new LinkedList<>();
				prevResults[1] = new LinkedList<>();
				results.put(parentName, prevResults);
			}
			final List<String> prev = prevResults[ismoved ? 0 : 1];
			if (!prev.contains(oldName)) {
				prev.add(oldName);
			}
			logmoveInternal(olds, generation - 1, ismoved, results);
			// if (ismoved)
			// System.out.println("move record for " + parentName + " "
			// + UtilString.valueOfDeep(prevResults));
		}
	}

	private void printmoveLog(final Map<String, List<String>[]> promotions) {
		printmoveLogInternal("", promotions);
	}

	private void printmoveLogInternal(final String name, final Map<String, List<String>[]> promotions) {
		final List<String>[] results = promotions.get(name);
		if (results != null) {
			// "leaf" record won't exist
			final List<String> bads = results[1];
			final List<String> goods = results[0];
			bads.removeAll(goods);
			if (bads.size() > 0 && name.length() > 0) {
				db.log("... failed to find " + bads.size() + " children of " + name + ", including " + bads.get(0));
			}
			// if (goods.size() > 0) {
			// log("... found " + goods.size() + " children of " + name
			// + ", including " + goods.get(0));
			// }
			for (final String goodName : goods) {
				printmoveLogInternal(goodName, promotions);
			}
		}
	}

	private final String[] renumberTables = { "images", "item", "raw_item_facet" };

	/**
	 * Re-assigns record numbers to items so that they are consecutive starting
	 * at 1. Starting at the lowest record num, it is either 1 or can be moved
	 * there, in each renumberTable. Then the same becomes true for each
	 * successive record.
	 *
	 * @throws SQLException
	 */
	@SuppressWarnings("resource")
	void renumber() throws SQLException {
		System.out.println("Renumbering items...");

		final PreparedStatement getNextRecordNum = jdbc
				.lookupPS("SELECT MIN(record_num) FROM item WHERE record_num >= ?");
		final int lowestRecordNum = jdbc.sqlQueryInt(getNextRecordNum, 0);
		for (int destinationRecordNum = 1; destinationRecordNum < lowestRecordNum; destinationRecordNum++) {
			final int oldRecordNum = jdbc.sqlQueryInt(getNextRecordNum, destinationRecordNum);
			if (oldRecordNum > destinationRecordNum) {
				for (final String renumberTable : renumberTables) {
					final PreparedStatement renumberPS = jdbc
							.lookupPS("UPDATE " + renumberTable + " SET record_num = ? WHERE record_num = ?");
					renumberPS.setInt(1, destinationRecordNum);
					renumberPS.setInt(2, oldRecordNum);
					db.updateOrPrint(renumberPS, "Renumber");
				}
			} else {
				break;
			}
		}
		System.out.println("...renumbering done");
	}

	private enum DuplicateStatus {
		SAME, DIFFERENT, NA
	}

	void mergeDuplicateItems(final String facetTypeNamesToMerge) throws SQLException {
		final String[] toMerge = facetTypeNamesToMerge.split(",");
		final int[] facetTypesToMerge = new int[toMerge.length];
		for (int i = 0; i < toMerge.length; i++) {
			@SuppressWarnings("null")
			final @NonNull String name = toMerge[i];
			facetTypesToMerge[i] = db.getFacetType(name, false);
		}
		jdbc.ensureIndex("item", "URI", "URI(51)");
		try (final ResultSet pairs = jdbc.sqlQuery("SELECT i1.record_num, i2.record_num, i1.URI FROM item i1, item i2 "
				+ "where i1.record_num < i2.record_num " + "and i1.URI = i2.URI");) {
			System.out.println(MyResultSet.nRows(pairs) + " possible duplicate items to merge...");
			while (pairs.next()) {
				final int item1 = pairs.getInt(1);
				final int item2 = pairs.getInt(2);
				final DuplicateStatus duplicateStatus = isDuplicate(item1, item2, facetTypesToMerge);
				switch (duplicateStatus) {
				case SAME:
					// UtilString.err("merge " + item1 + " " + item2 + " "
					// + pairs.getString(3));
					db.mergeItems(item1, item2);
					break;
				case DIFFERENT:
				case NA:
					System.err.println(
							"Warning: " + item1 + " and " + item2 + " have the same URI: " + pairs.getString(3));
					break;
				default:
					assert false : duplicateStatus;
					break;
				}
			}
		}
		System.out.println("...merging items done.");
	}

	@SuppressWarnings("resource")
	private DuplicateStatus isDuplicate(final int item1, final int item2, final int[] facetTypesToMerge)
			throws SQLException {
		final String desc1 = db.itemDescription(item1);
		final String desc2 = db.itemDescription(item2);
		// System.out.println("isDuplicate "+item1+" "+item2+" "+desc1);
		DuplicateStatus result = desc1 == null || desc2 == null ? DuplicateStatus.NA
				: desc1.equals(desc2) ? DuplicateStatus.SAME : DuplicateStatus.DIFFERENT;
		if (result == DuplicateStatus.SAME) {
			final PreparedStatement getFacets1 = jdbc
					.lookupPS("SELECT facet_id dummy1 " + "FROM raw_item_facet WHERE record_num = ? ORDER BY facet_id");
			// Need two copies since we need two ResultSets open simultaneously
			final PreparedStatement getFacets2 = jdbc
					.lookupPS("SELECT facet_id dummy2 " + "FROM raw_item_facet WHERE record_num = ? ORDER BY facet_id");
			getFacets1.setInt(1, item1);
			getFacets2.setInt(1, item2);
			try (final ResultSet rs1 = jdbc.sqlQuery(getFacets1); final ResultSet rs2 = jdbc.sqlQuery(getFacets2);) {
				while (result == DuplicateStatus.SAME) {
					final boolean isRs1 = scrollToUnmergedFacet(rs1, facetTypesToMerge);
					final boolean isRs2 = scrollToUnmergedFacet(rs2, facetTypesToMerge);
					if (!isRs1 && !isRs2) {
						break;
					} else if (!(isRs1 && isRs2 && rs1.getInt(1) == rs2.getInt(1))) {
						result = DuplicateStatus.DIFFERENT;
						// if (!result)
						// System.out.println("Difference: " +
						// getName(rs1.getInt(1)) + " "
						// + getName(rs2.getInt(1)));
						// else
						// System.out.println("Same: " + getName(rs1.getInt(1))
						// +
						// " "
						// + getName(rs2.getInt(1)));
					}
				}
			}
		} else {
			// System.out.println("Descriptions differ:\n" + desc1 + "\n\n" +
			// desc2);
		}
		return result;
	}

	private boolean scrollToUnmergedFacet(final ResultSet rs, final int[] facetTypesToMerge) throws SQLException {
		boolean result = rs.next();
		if (result && ArrayUtils.contains(facetTypesToMerge, db.getRawFacetType(rs.getInt(1)))) {
			result = scrollToUnmergedFacet(rs, facetTypesToMerge);
		}
		return result;
	}

	private void printDuplicates() throws SQLException {
		System.out.println("Listing possible duplicate facets based on name...");
		try (final ResultSet rs = jdbc.sqlQuery(
				"SELECT GROUP_CONCAT(facet_id)" + "FROM raw_facet INNER JOIN (SELECT f.name " + "FROM raw_facet f "
						+ "GROUP BY f.name HAVING COUNT(*) > 1) foo USING (name)" + "GROUP BY name");) {
			while (rs.next()) {
				@SuppressWarnings("null")
				final String[] facets = UtilString.splitComma(rs.getString(1));
				for (int i = 0; i < facets.length; i++) {
					final int facet1 = Integer.parseInt(facets[i]);
					// int[] ancestors1 = ancestors(facet1);
					// for (int j = 0; j < facets.length; j++) {
					// if (i != j) {
					// int facet2 = Integer.parseInt(facets[j]);
					// if (getName(facet1) != null && getName(facet2) != null) {
					// int facetType1 = getFacetType(facet1);
					// int facetType2 = getFacetType(facet2);
					// if (facetType1 > 0 && facetType2 > 0
					// && getName(facetType1).equals("Date")
					// && getName(facetType2).equals("Subject")) {
					// merge(facet2, facet1);
					// }
					// }
					// int[] ancestors2 = ancestors(facet2);
					// if (ArrayUtils.contains(ancestors1,
					// getParentFacet(facet2)))
					// merge(facet2, facet1);
					// }
					// }
					if (facets.length > 4 && i > 2) {
						System.out.println("... " + (facets.length - 2) + " more");
						break;
					} else {
						System.out.println(db.rawAncestorString(facet1));
					}
				}
				System.out.println("");
			}
		}
		System.out.println("Listing possible duplicate facets...done\n");
	}

	/**
	 *
	 * globals.itemURL should generate a unique ID for each item, so we can use
	 * that to identify corresponding records in another database and copy its
	 * images. However, we need to qualify that expression with copyFrom.
	 * qualifySQL is a kludge that we need to keep updated for all the databases
	 * we use.
	 *
	 * For instance, itemURL might be:
	 *
	 * (SELECT xref FROM movie, shotbreak WHERE movie.movie_id =
	 * shotbreak.movie_id AND shotbreak.shotbreak_id = item.shotbreak)
	 *
	 * Assumes that images looks like:
	 *
	 * record_num, image, [<globals.itemURL>,] w, h
	 *
	 * where [] denotes an optional column, and that copyFrom.images has at
	 * least the four other columns.
	 *
	 * @param copyFrom
	 *            database with images to copy
	 * @throws SQLException
	 */
	void copyThumbsNoURI(final String copyFrom) throws SQLException {
		System.out.println("\nCopying images from " + copyFrom);
		final String globals_itemURL = jdbc.sqlQueryString("SELECT itemURL FROM globals");
		assert UtilString.isNonEmptyString(globals_itemURL) : jdbc.dbName + ".globals.itemURL is empty";
		String copyFrom_globals_itemURL = jdbc.sqlQueryString("SELECT itemURL FROM " + copyFrom + ".globals");
		assert UtilString.isNonEmptyString(copyFrom_globals_itemURL) : copyFrom + ".globals.itemURL is empty";
		// jdbc.ensureIndex(copyFrom, "item", "URI", "URI(51)", "");

		// /**
		// * Does <this database>.images have a URI column? Assume columns
		// *
		// * record_num, image, [URI,] w, h
		// */
		// final boolean isURIcolumn = jdbc.columnExists(dbName, "images",
		// globals_itemURL);

		// globals_itemURL = "item." + globals_itemURL;
		copyFrom_globals_itemURL = // "copy_it." +
		substituteCopyTableForItem(copyFrom_globals_itemURL, "copy_it");
		final String sql = "REPLACE INTO images " + "SELECT item.record_num, copy_im.image, "
		// + (isURIcolumn ? copyFrom_globals_itemURL + ", " : "")
				+ "copy_im.w, copy_im.h " + "FROM item " + "LEFT JOIN images USING (record_num) " + "INNER JOIN "
				+ copyFrom + ".item copy_it ON " + copyFrom_globals_itemURL + " = " + globals_itemURL + " INNER JOIN "
				+ copyFrom + ".images copy_im ON copy_it.record_num = copy_im.record_num "
				+ "WHERE images.image is NULL";

		System.out.println(sql);

		final int n = db.updateOrPrint(sql);
		System.out.println("..." + n + " images copied");
	}

	// private static String qualifySQL(String sql, final String schema) {
	// UtilString.indentMore("PopulateHandler.qualifySQL "+schema+" "+sql);
	// final int fromIndex = sql.indexOf("from");
	// if (fromIndex > 0) {
	// final int whereIndex = sql.indexOf("where");
	// final String[] tokens = sql.substring(fromIndex,
	// whereIndex > fromIndex ? whereIndex : sql.length()).split(
	// ",");
	// for (int i = 0; i < tokens.length; i++) {
	// final String token = tokens[i].trim();
	// tokens[i] = schema + "." + token;
	// }
	// String qualified = sql.substring(0, fromIndex)
	// + UtilString.join(tokens);
	// if (whereIndex > 0) {
	// qualified += sql.substring(whereIndex);
	// }
	// sql = qualified;
	// }
	// UtilString.indentLess("PopulateHandler.qualifySQL return "+sql);
	// return sql;
	// }

	private static String substituteCopyTableForItem(final String sql, final String table) {
		// UtilString.indentMore("PopulateHandler.substituteCopyTableForItem
		// "+table+" "+sql);
		return sql.replace("item", table);
	}

	// TODO Remove unused code found by UCDetector
	// @SuppressWarnings("resource")
	// void updateImageSizes() {
	// try (final ResultSet rs = jdbc
	// .sqlQuery("SELECT record_num, image FROM images");) {
	// final PreparedStatement ps = jdbc
	// .lookupPS("UPDATE images SET w=?, h=? WHERE record_num=?");
	// while (rs.next()) {
	// final int recordNum = rs.getInt(1);
	// final BufferedImage image = ImageUtil.blob2image(rs.getBlob(2));
	// ps.setInt(1, image.getWidth());
	// ps.setInt(2, image.getHeight());
	// ps.setInt(3, recordNum);
	// jdbc.sqlUpdate(ps);
	// }
	// } catch (SQLException | IOException e) {
	// e.printStackTrace();
	// }
	// }

	public void newItem() throws SQLException {
		db.newItem();
	}

	public void newItem(final int record_num) throws SQLException {
		db.newItem(record_num);
	}

	public void setRecordNum(final int record_num) throws SQLException {
		db.setRecordNum(record_num);
	}

	String getAttribute(final String name) throws SQLException {
		return db.getItemAttribute(name);
	}

	public final Set<Field> fields = new HashSet<>();

	void cleanUp() throws SQLException {
		for (final Field field : fields) {
			field.cleanUp(db);
		}
	}

	boolean insertAttribute(final Field field, final String value) throws SQLException {
		fields.add(field);
		return db.insertAttribute(field.name, value);
	}

	void insertFacet(final Field field, final List<String> hierValue) throws SQLException {
		fields.add(field);
		db.insertFacet(hierValue);
	}

	protected int sqlUpdate(final @NonNull String sql) {
		int nRows = 0;
		if (dontUpdate) {
			System.out.println(sql);
		} else {
			try {
				nRows = jdbc.sqlUpdate(sql);
			} catch (final SQLException e) {
				System.err.println("SQLException for " + sql);
				e.printStackTrace();
			}
		}
		return nRows;
	}

	protected int sqlUpdate(final @NonNull PreparedStatement ps) {
		return sqlUpdate(ps, jdbc, dontUpdate);
	}

	protected static int sqlUpdate(final @NonNull PreparedStatement ps, final JDBCSample jdbc,
			final boolean dontUpdate) {
		int nRows = 0;
		if (dontUpdate) {
			System.out.println(ps);
		} else {
			nRows = jdbc.sqlUpdate(ps);
		}
		return nRows;
	}

	public Database getDB() {
		return db;
	}

	public JDBCSample getJDBC() {
		return jdbc;
	}

	// private static final String[] attrTables = { // "title" // ,
	// // "description"
	// // "series", "summary", "note"
	// };

	// JDBCSample getJdbc() {
	// return db.getJdbc();
	// }

	// private Image loadRemoteImage(final URL url) {
	// final String path = url.getPath();
	// final int extensionIndex = path.lastIndexOf(".");
	// final String extension = path.substring(extensionIndex);
	// assert extension.matches("(?i).*\\.(?:jpe?g|gif|png)(?:\\?.*)?")
	// : "Toolkit.createImage only accepts jpg, gif, and png: " + path;
	// final Image image = Toolkit.getDefaultToolkit().createImage(url);
	// final MediaTracker mediaTracker = new MediaTracker(new Container());
	// mediaTracker.addImage(image, 0);
	// try {
	// mediaTracker.waitForID(0);
	// }
	// catch (final InterruptedException e1) {
	// e1.printStackTrace();
	// }
	// // System.out.println("errors? => " + mediaTracker.isErrorAny());
	// return image;
	// }
}
