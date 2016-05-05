package edu.cmu.cs.bungee.populate.scripts;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.compile.Database;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilFiles;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.Token;
import edu.cmu.cs.bungee.populate.Attribute;
import edu.cmu.cs.bungee.populate.Facet;
import edu.cmu.cs.bungee.populate.Field;
import edu.cmu.cs.bungee.populate.FunctionalAttribute;
import edu.cmu.cs.bungee.populate.Options;

/**
 * Run it like this:
 *
 * -user root -pass tartan01 -verbose -ItemType Movie -collectionIDs 5
 * -dontUseOcrAsr -copyTablesFromDB elamp -renames Rename.txt -moves
 * MyTGM.txt,places_hierarchy.txt,Moves.txt -thumbUrlGetter CONCAT(
 * 'http://aladdin2.inf.cs.cmu.edu/infmed2014_data/keyframes/origin/',movie_id,'
 * / ' , r e c o r d _ n u m , ' . j p g ' )
 *
 * -directory "C:\Users\mad\Documents\IDriveSync\IDriveSync\documents\Work
 * \Projects\ArtMuseum\PopulateDataFiles"
 *
 * -image_url_getter CONCAT(
 * 'http://aladdin2.inf.cs.cmu.edu/infmed2014_data/thumbnails/',item.name,'.jpg'
 * )
 *
 * -db elamp1234_shot_20150318 -server jdbc:mysql://localhost/
 *
 * Don't renumber - IDs match movie_IDs!
 *
 */
class Elamp extends Options { // NO_UCD (unused code)

	public static void main(final String[] args) {
		try {
			assert args != null;
			new Elamp().init(args);
		} catch (final Throwable ex) {
			ex.printStackTrace(System.out);
		}
	}

	@Override
	protected void readData() throws SQLException {
		db = populateHandler.getDB();

		final String tableName = sm_main.getStringValue("semanticFeaturesTable");
		if (tableName.equals("WEAPON_METADATA")) {
			jdbc.sqlUpdate("TRUNCATE TABLE raw_facet_type");
			jdbc.sqlUpdate("INSERT INTO raw_facet_type " + "VALUES(1,'DATE','meta',' from ; NOT from ',2,1,1), "
					+ "(46,'Collection','meta',' from ; NOT from ',2,1,1), "
					+ "(48,'Resolution','meta',' having resolution ; not having resolution ',45,1,1), "
					+ "(50,'Length','meta',' of length ; not of length ',40,1,1), "
					+ "(52,'FPS','meta',' with fps ; not with fps ',50,1,1), "
					+ "(6,'Subject','content',' that show ; that don''t show ',60,0,1), "
					+ "(2,'BRIGADE_FSA_NAME','meta',' that show ; that don''t show ',10,0,1), "
					+ "(3,'BATTALION_FSA_NAME','meta',' that show ; that don''t show ',20,0,1), "
					+ "(7,'COMPANY_FSA_NAME','meta',' that show ; that don''t show ',15,0,1), "
					+ "(8,'SPECIAL_BRIGADE_FSA_NAME','meta',' that show ; that don''t show ',25,0,1), "
					// +
					// "(9,'COMMENTS','meta',' that show ; that don''t show
					// ',30,0,1), "
					+ "(10,'CONFIRMED_COUNTRY_OF_ORIGIN','meta',' in ; NOT in ',1,0,1), "
					+ "(11,'POSSIBLE_COUNTRY_OF_ORIGIN','meta',' that show ; that don''t show ',35,0,1), "
					+ "(16,'SUBCOUNCIL_DIVISION_NAME','meta',' having resolution ; not having resolution ',45,1,1), "
					+ "(17,'COUNCIL_DIVISION_NAME','meta',' of length ; not of length ',40,1,1), "
					+ "(20,'CITY_COUNCIL_NAME','meta',' with fps ; not with fps ',50,1,1), "
					+ "(22,'REGIONAL_COUNCIL_NAME','meta',' that show ; that don''t show ',10,0,1), "
					+ "(24,'NATIONAL_COUNCIL_NAME','meta',' that show ; that don''t show ',10,0,1), "
					+ "(26,'INDIVIDUALS_SEEN_IN_VIDEO','meta',' that show ; that don''t show ',10,0,1), "
					+ "(28,'GEO_MOHAFAZA','meta',' that show ; that don''t show ',10,0,1), "
					+ "(30,'GEO_MANTAQA','meta',' that show ; that don''t show ',10,0,1), "
					+ "(32,'GEO_NAHIA','meta',' that show ; that don''t show ',10,0,1), "
					+ "(34,'GEO_CITY_TOWN','meta',' that show ; that don''t show ',10,0,1), "
					+ "(36,'GEO_NEIGHBORHOOD','meta',' that show ; that don''t show ',10,0,1), "
					+ "(38,'GEO_GENERAL_AREA','meta',' that show ; that don''t show ',10,0,1), "
					+ "(40,'ARMS_SEEN_IN_VIDEO','meta',' that show ; that don''t show ',10,0,1), "
					+ "(42,'ARMS_TYPE_SEEN_IN_VIDEO','meta',' that show ; that don''t show ',10,0,1), "
					+ "(44,'SERIAL_NUMBER','meta',' that show ; that don''t show ',10,0,1)");
		}

		jdbc.slowQueryTime = 100_000;

		jdbc.sqlUpdate("UPDATE globals SET genericObjectLabel = '" + getItemType().name() + "', description = '"
				+ sm_main.getStringValue("db") + "'");

		if (sm_main.getBooleanValue("dontCopyFromOracle")) {
			computeIDtypes();
		} else {
			readOracleData();
		}
		computeItemTypes();

		// Doesn't work well.
		// createWordNetHierarchyTable();

		// Depends on movie, shot
		readAttributes();
		if (!sm_main.getBooleanValue("dontUseOcrAsr")) {
			// Depends on movie, ASR/OCR, and a reset raw_item_facet.
			readWordFeature("ASR");
			readWordFeature("OCR");
		}
		jdbc.dropTable("parsed_hierarchy");

		final String semanticFeaturesTable = sm_main.getStringValue("semanticFeaturesTable");
		if (semanticFeaturesTable.equals("SemanticFeatures")) {
			readSemanticFeatures();
		}
	}

	@Override
	protected @NonNull String[] tablesToCopy() {
		final String[] extraTablesToCopy = { "my_feature", "movie", "semantic_features" };
		final String[] result = UtilArray.append(super.tablesToCopy(), extraTablesToCopy);
		assert result != null;
		return result;
	}

	// /////////// Everything below is private ///////////////

	private void readOracleData() throws SQLException {
		try (final JDBCSample oracle = JDBCSample.getOracleJDBCSample(CONNECT_STRING, USER, PASS);) {
			computeIDtypes(oracle);
			createMyFeatureTable(oracle);
			getSchema(oracle);
			copyMovieTable(oracle);
			if (getItemType() == ItemType.Shot) {
				copyShotTable(oracle);
			}
			copySemanticFeaturesTable(oracle);

			if (!sm_main.getBooleanValue("dontUseOcrAsr")) {
				copyOCRorASRTable("ASR", oracle);
				copyOCRorASRTable("OCR", oracle);
			}
		}
		// oracle.close();
	}

	private void init(final @NonNull String[] args) throws Throwable {
		sm_main.addStringToken("ItemType", "whether items are Movies or Shots", 0, "Movie");
		sm_main.addStringToken("collectionIDs", "collections to include, e.g. 1,2,3,4", Token.optRequired, "");
		sm_main.addBooleanToken("dontCopyFromOracle", "Don't copy movie, semanticFeatures, OCR, ASR from Oracle",
				Token.optSwitch);
		sm_main.addBooleanToken("dontUseOcrAsr", "Don't create OCR, ASR features", Token.optSwitch);
		// sm_main.addBooleanToken("printOracleSchema", "", Token.optSwitch);
		sm_main.addStringToken("semanticFeaturesTable", "", 0, "SemanticFeatures");

		// Have to parse before getting values, but need some args to compute
		// others, so use ugly hack to get those values.
		String[] allArgs = args;
		final int dbIndex = ArrayUtils.indexOf(args, "-db");
		if (dbIndex < 0) {
			final int itemTypeIndex = ArrayUtils.indexOf(args, "-ItemType");
			itemType = ItemType.valueOf(itemTypeIndex >= 0 ? args[itemTypeIndex + 1] : "Movie");
			assert itemType != null : "ItemType is not 'Movie' or 'Shot'";

			final int collectionIDsIndex = ArrayUtils.indexOf(args, "-collectionIDs");
			final String collectionIDsExpr = collectionIDsIndex >= 0 ? args[collectionIDsIndex + 1] : null;
			assert collectionIDsExpr != null : "Must specify 'collectionIDs'";
			assert Pattern.matches("(\\d+,)*\\d+", collectionIDsExpr) : collectionIDsExpr
					+ " must be a comma-separated list of positive integers, with no spaces.";
			// collectionsPredicate = "collection_id IN (" + collectionIDsExpr
			// + ")";
			final String[] dbArgs = { "-db",
					"Elamp" + collectionIDsExpr.replaceAll(",", "") + "_" + itemType + "_" + formatDate() };
			allArgs = UtilArray.append(args, dbArgs);
		}
		// System.out.println("DBElamp.init " +
		// UtilString.valueOfDeep(allArgs));
		super.init(false, allArgs);
	}

	private boolean isShot() {
		return getItemType() == ItemType.Shot;
	}

	private enum ItemType {
		Movie, Shot
	}

	private ItemType itemType;

	private ItemType getItemType() {
		if (itemType == null) {
			itemType = ItemType.valueOf(sm_main.getStringValue("ItemType"));
		}
		return itemType;
	}

	private String collectionsPredicate;

	private String getCollectionsPredicate(final String movieColumnName) {
		return "\"" + movieColumnName + "\" IN (SELECT MOVIE_ID FROM COLLECTIONMOVIE WHERE collection_id IN ("
				+ sm_main.getStringValue("collectionIDs") + "))";
	}

	private String getCollectionsPredicate() {
		if (collectionsPredicate == null) {
			final String collectionIDsExpr = sm_main.getStringValue("collectionIDs");
			assert Pattern.matches("(\\d+,)*\\d+", collectionIDsExpr) : collectionIDsExpr
					+ " must be a comma-separated list of positive integers, with no spaces.";
			collectionsPredicate = "movie_id IN (SELECT MOVIE_ID FROM COLLECTIONMOVIE WHERE collection_id IN ("
					+ collectionIDsExpr + "))";
		}
		return collectionsPredicate;
	}

	private static final @NonNull String PROTOCOL = "jdbc:oracle:thin:";
	private static final @NonNull String SERVER = "@//mmdb.inf.cs.cmu.edu:1521/mmdb";
	private static final @NonNull String USER = "ELAMP15"; // "MED14";
	private static final @NonNull String PASS = "informedia2015"; // "informedia";

	private static final @NonNull String CONNECT_STRING = PROTOCOL + SERVER;
	private static final boolean INSERT_ANCESTORS_INITIALLY = false;

	private Database db;
	private String collectionIDtype;
	private String movieIDtype;
	private String shotIDtype;
	private String semanticFeatureIDtype;
	/**
	 * "shot" or "movie"
	 */
	private String itemTable;
	/**
	 * "shot_id" or "movie_id"
	 */
	private String itemTableIDcolumn;
	/**
	 * shotIDtype or movieIDtype
	 */
	private String itemIDtype;
	/**
	 * "movie" or "shot INNER JOIN movie USING (movie_id)"
	 */
	private String enhancedItemTable;

	private static String formatDate() {
		return new SimpleDateFormat("yyyyMMdd").format(new Date());
	}

	private void computeIDtypes(final JDBCSample oracle) throws SQLException {
		collectionIDtype = oracle.unsignedTypeForMaxValue("SELECT MAX(collection_id) FROM collection");
		movieIDtype = oracle.unsignedTypeForMaxValue("SELECT MAX(movie_id) FROM movie");
		shotIDtype = oracle.unsignedTypeForMaxValue("SELECT MAX(shotbreak_id) FROM shotbreak");
		semanticFeatureIDtype = oracle.unsignedTypeForMaxValue("SELECT MAX(feature_id) FROM feature_info");
		itemIDtype = isShot() ? shotIDtype : movieIDtype;
	}

	/**
	 * featureIDtype is the only one that non-oracle methods use
	 */
	private void computeIDtypes() throws SQLException {
		semanticFeatureIDtype = jdbc.unsignedTypeForMaxValue("SELECT MAX(feature_id) FROM my_feature");
	}

	private void computeItemTypes() throws SQLException {
		itemTable = isShot() ? "shot" : "movie";
		enhancedItemTable = (isShot() ? "shot INNER JOIN movie USING (movie_id)" : "movie");
		itemTableIDcolumn = itemTable + "_id";
		itemIDtype = jdbc.unsignedTypeForMaxValue("SELECT MAX(" + itemTableIDcolumn + ") FROM " + itemTable);
	}

	private static void getSchema(final JDBCSample oracle) throws SQLException {
		final String schemaQuery = "SELECT TABLE_NAME, COLUMN_NAME, NUM_ROWS "
				+ "FROM ALL_TABLES INNER JOIN ALL_TAB_COLUMNS USING (OWNER, TABLE_NAME) "
				+ "WHERE OWNER='ELAMP15' order by TABLE_NAME";
		try (final ResultSet rs = oracle.sqlQuery(schemaQuery);) {
			System.out.println("Elamp.getSchema: \n" + MyResultSet.valueOfDeep(rs, Integer.MAX_VALUE));
		}
	}

	private void copyOCRorASRTable(final String facetTypeName, final JDBCSample oracle) throws SQLException {
		final String wordTable = facetTypeName + "raw";
		final boolean isShot = isShot();
		final String oracleItemTable = isShot ? "SHOTBREAK" : "movie";
		final String oracleIdColumn = oracleItemTable + "_id";

		jdbc.dropAndCreateTable(wordTable, "(record_num " + itemIDtype + ", " + facetTypeName + " VARCHAR(255) NOT NULL"
				+ ", KEY record_num (record_num)) ENGINE=MyISAM");

		final String stemWordSQL = "SELECT DISTINCT " + oracleIdColumn + ", stem_word FROM " + wordTable
				+ " wordTable INNER JOIN " + oracleItemTable + " itemTable USING (movie_id)"
				// + " INNER JOIN COLLECTIONMOVIE USING (movie_id)"
				+ " WHERE "
				+ (isShot ? "STARTOFFSET <= wordTable.FRAME" + " AND ENDOFFSET >= wordTable.FRAME AND " : "")
				+ getCollectionsPredicate();

		try (final PreparedStatement ps = jdbc.lookupPS("INSERT INTO " + wordTable + " VALUES(?, ?)");

				ResultSet rs = oracle.sqlQuery(stemWordSQL);) {

			UtilString.printDateNmessage(
					"Elamp.copyOCRorASRTable: " + facetTypeName + "\n" + MyResultSet.valueOfDeep(rs, 10));

			// jdbc.sqlUpdate("ALTER TABLE " + wordTable + " DISABLE KEYS");
			while (rs.next()) {
				jdbc.sqlUpdateWithStringArgs(ps, rs.getString(oracleIdColumn), rs.getString("stem_word"));
			}
			// jdbc.sqlUpdate("ALTER TABLE " + wordTable + " ENABLE KEYS");
		}
	}

	private void createMyFeatureTable(final JDBCSample oracle) throws SQLException {
		jdbc.dropAndCreateTable("my_feature", "(feature_id " + semanticFeatureIDtype
				+ ", my_feature_id VARCHAR(255) NOT NULL" + ", PRIMARY KEY (feature_id) " + ") ENGINE=MyISAM");

		try (final PreparedStatement ps = jdbc.lookupPS("INSERT INTO my_feature VALUES(?, ?)");

				ResultSet rs = oracle.sqlQuery("SELECT feature_id,"
						+ " CONCAT(CONCAT(FEATURE_GROUP, '_'), INNER_ID) AS my_feature_id " + "FROM feature_info");) {
			UtilString.printDateNmessage("Elamp.createMyFeatureTable:\n" + MyResultSet.valueOfDeep(rs, 6));

			// jdbc.sqlUpdate("ALTER TABLE my_feature DISABLE KEYS");
			while (rs.next()) {
				jdbc.sqlUpdateWithStringArgs(ps, rs.getString("feature_id"), rs.getString("my_feature_id"));
			}
			// jdbc.sqlUpdate("ALTER TABLE my_feature ENABLE KEYS");
		}
	}

	private void copySemanticFeaturesTable(final JDBCSample oracle) throws SQLException {
		final boolean isShot = isShot();
		final String mySqlIdColumn = isShot ? "shot_id" : "movie_id";
		final String oracleIdColumn = isShot ? "shotbreak_id" : "movie_id";
		final String tableName = sm_main.getStringValue("semanticFeaturesTable");
		if (tableName.equals("WEAPON_METADATA")) {
			copyWEAPON_METADATATable(oracle);
		} else {

			jdbc.dropAndCreateTable("Semantic_Features", "(" + mySqlIdColumn + " " + itemIDtype + ", feature_id "
					+ semanticFeatureIDtype + ", INDEX feature_id (feature_id)) ENGINE=MyISAM");

			final String sql = "SELECT DISTINCT \"" + oracleIdColumn + "\" AS oracleId, \"concept_id\" AS concept_id "
					+ "FROM \"" + tableName + "\"  WHERE " + getCollectionsPredicate("movie_id");

			try (final PreparedStatement insertPS = jdbc.lookupPS("INSERT INTO Semantic_Features VALUES(?, ?)");

					// Try using ResultSet.TYPE_FORWARD_ONLY to avoid running
					// out of
					// memory.
					// Means we can't call MyResultSet.nRows()
					final PreparedStatement queryPS = oracle.con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY,
							ResultSet.CONCUR_READ_ONLY);
					final ResultSet rs = oracle.sqlQuery(Util.nonNull(queryPS));) {

				UtilString.printDateNmessage("Elamp.copySemanticFeaturesTable: starting to copy...");
				// + MyResultSet.valueOfDeep(rs, 10));

				// jdbc.sqlUpdate("ALTER TABLE Semantic_Features DISABLE KEYS");
				while (rs.next()) {
					jdbc.sqlUpdateWithStringArgs(insertPS, rs.getString("oracleId"), rs.getString("concept_id"));
				}
				// jdbc.sqlUpdate("ALTER TABLE Semantic_Features ENABLE KEYS");
			}
		}
	}

	private void copyWEAPON_METADATATable(final JDBCSample oracle) throws SQLException {
		jdbc.dropAndCreateTable("Semantic_Features", "(BRIGADE_FSA_NAME VARCHAR(255), "
				+ "BATTALION_FSA_NAME VARCHAR(255), " + "COMPANY_FSA_NAME VARCHAR(255), " + "DATE VARCHAR(255), "
				+ "MOVIE_ID VARCHAR(255), " + "SPECIAL_BRIGADE_FSA_NAME VARCHAR(255), " + "COMMENTS VARCHAR(255), "
				+ "CONFIRMED_COUNTRY_OF_ORIGIN VARCHAR(255), " + "POSSIBLE_COUNTRY_OF_ORIGIN VARCHAR(255), "
				+ "SUBCOUNCIL_DIVISION_NAME VARCHAR(255), " + "COUNCIL_DIVISION_NAME VARCHAR(255), "
				+ "CITY_COUNCIL_NAME VARCHAR(255), " + "REGIONAL_COUNCIL_NAME VARCHAR(255), "
				+ "NATIONAL_COUNCIL_NAME VARCHAR(255), " + "INDIVIDUALS_SEEN_IN_VIDEO VARCHAR(255), "
				+ "GEO_MOHAFAZA VARCHAR(255), " + "GEO_MANTAQA VARCHAR(255), " + "GEO_NAHIA VARCHAR(255), "
				+ "GEO_CITY_TOWN VARCHAR(255), " + "GEO_NEIGHBORHOOD VARCHAR(255), " + "GEO_GENERAL_AREA VARCHAR(255), "
				+ "ARMS_SEEN_IN_VIDEO VARCHAR(255), " + "ARMS_TYPE_SEEN_IN_VIDEO VARCHAR(255), "
				+ "SERIAL_NUMBER VARCHAR(255) " + ", PRIMARY KEY (MOVIE_ID)" + ") ENGINE=MyISAM");

		final String sql = "SELECT BRIGADE_FSA_NAME, " + "BATTALION_FSA_NAME, " + "COMPANY_FSA_NAME, " + "\"DATE\", "
				+ "MOVIE_ID, " + "SPECIAL_BRIGADE_FSA_NAME, " + "COMMENTS, " + "CONFIRMED_COUNTRY_OF_ORIGIN, "
				+ "POSSIBLE_COUNTRY_OF_ORIGIN, " + "SUBCOUNCIL_DIVISION_NAME, " + "COUNCIL_DIVISION_NAME, "
				+ "CITY_COUNCIL_NAME, " + "REGIONAL_COUNCIL_NAME, " + "NATIONAL_COUNCIL_NAME, "
				+ "INDIVIDUALS_SEEN_IN_VIDEO, " + "GEO_MOHAFAZA, " + "GEO_MANTAQA, " + "GEO_NAHIA, " + "GEO_CITY_TOWN, "
				+ "GEO_NEIGHBORHOOD, " + "GEO_GENERAL_AREA, " + "ARMS_SEEN_IN_VIDEO, " + "ARMS_TYPE_SEEN_IN_VIDEO, "
				+ "SERIAL_NUMBER FROM WEAPON_METADATA" + " WHERE " + getCollectionsPredicate("MOVIE_ID");

		try (final PreparedStatement insertPS = jdbc.lookupPS(
				"INSERT INTO Semantic_Features VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

				// Try using ResultSet.TYPE_FORWARD_ONLY to avoid running out of
				// memory.
				// Means we can't call MyResultSet.nRows()
				final PreparedStatement queryPS = oracle.lookupPS(sql, ResultSet.TYPE_FORWARD_ONLY,
						ResultSet.CONCUR_READ_ONLY);

				final ResultSet rs = oracle.sqlQuery(queryPS);) {

			UtilString.printDateNmessage("Elamp.copySemanticFeaturesTable: starting to copy...");
			// + MyResultSet.valueOfDeep(rs, 10));

			// jdbc.sqlUpdate("ALTER TABLE Semantic_Features DISABLE KEYS");
			jdbc.copyOracleTableToMySQL(insertPS, rs);
			// while (rs.next()) {
			// jdbc.sqlUpdate(insertPS, rs.getString("oracleId"),
			// rs.getString("concept_id"));
			// }
			// jdbc.sqlUpdate("ALTER TABLE Semantic_Features ENABLE KEYS");
		}
	}

	private void copyMovieTable(final JDBCSample oracle) throws SQLException {
		final int maxMediaLength = oracle.sqlQueryInt("SELECT MAX(media_length / 1000) FROM movie");
		final String mediaLengthIDtype = JDBCSample.unsignedTypeForMaxValue(maxMediaLength);

		// Column names must correspond to the Field names used to parse them!
		jdbc.dropAndCreateTable("movie",
				"(collection_id " + collectionIDtype + ", collection VARCHAR(255) NOT NULL," + " movie_id "
						+ movieIDtype + ", name VARCHAR(255) NOT NULL," + " length " + mediaLengthIDtype
						+ ", resolution VARCHAR(255) NOT NULL," + " fps VARCHAR(255) NOT NULL"

						+ ", date VARCHAR(255)" + ", neighborhood VARCHAR(255)" + ", battalion VARCHAR(255) "
						+ ", brigade VARCHAR(255)" + ", spec_brigade VARCHAR(255)" + ", individual VARCHAR(255)"
						+ ", council VARCHAR(255)" + ", title VARCHAR(255) "

						+ ", PRIMARY KEY (movie_id, collection)" + ") ENGINE=MyISAM");

		try (final PreparedStatement ps = jdbc
				.lookupPS("INSERT INTO movie VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

				final ResultSet rs = oracle
						.sqlQuery("SELECT collection_id, NICK_NAME AS collection, movie_id, movie_name,"
								+ " (media_length / 1000) AS media_length,"
								+ " CONCAT(CONCAT(image_width, 'x'), image_height) AS resolution, image_fps "
								+ ", \"date\" dat_e, \"neighborhood\" neighborhood, \"battalion\" battalion "
								+ ", \"brigade\" brigade, \"spec_brigade\" spec_brigade, \"individual\" individual "
								+ ", \"council\" council, \"title\" title " + "FROM movie "
								+ "LEFT JOIN  \"HRC_Metadata\" metadata on metadata.\"movie_id\" = movie.movie_id "
								+ "LEFT JOIN collectionmovie USING (movie_id) "
								+ "LEFT JOIN collection USING (collection_id)" + " WHERE "
								+ getCollectionsPredicate());) {
			UtilString.printDateNmessage("Elamp.copyMovieTable maxMediaLength=" + maxMediaLength + " mediaLengthIDtype="
					+ mediaLengthIDtype + ":\n" + MyResultSet.valueOfDeep(rs, 10));

			// jdbc.sqlUpdate("ALTER TABLE movie DISABLE KEYS");
			while (rs.next()) {
				ps.setString(1, rs.getString("collection_id"));
				ps.setString(2, rs.getString("collection"));
				ps.setString(3, rs.getString("movie_id"));
				ps.setString(4, rs.getString("movie_name"));
				ps.setString(5, rs.getString("media_length"));
				ps.setString(6, rs.getString("resolution"));
				ps.setString(7, rs.getString("image_fps"));

				ps.setString(8, rs.getString("dat_e"));
				ps.setString(9, rs.getString("neighborhood"));
				ps.setString(10, rs.getString("battalion"));
				ps.setString(11, rs.getString("brigade"));
				ps.setString(12, rs.getString("spec_brigade"));
				ps.setString(13, rs.getString("individual"));
				ps.setString(14, rs.getString("council"));
				ps.setString(15, rs.getString("title"));
				jdbc.sqlUpdate(ps);
			}
			// jdbc.sqlUpdate("ALTER TABLE movie ENABLE KEYS");
		}
	}

	// Column names must correspond to the Field names used to parse them!
	private void copyShotTable(final JDBCSample oracle) throws SQLException {
		final int maxMediaLength = oracle.sqlQueryInt("SELECT MAX((endoffset - startoffset) / 1000) FROM shotbreak");
		final String mediaLengthIDtype = JDBCSample.unsignedTypeForMaxValue(maxMediaLength);

		jdbc.dropAndCreateTable("shot",
				"(movie_id " + movieIDtype + ", shot_id " + shotIDtype + ", name VARCHAR(255) NOT NULL," + " length "
						+ mediaLengthIDtype + ", PRIMARY KEY (shot_id)" + ",  KEY movie_id(movie_id)"
						+ ") ENGINE=MyISAM");

		final String sql = "SELECT "
				// + "DISTINCT "
				+ "movie_id, shotbreak_id, name," + " ((endoffset - startoffset) / 1000) AS media_length "
				+ "FROM shotbreak "
				// +
				// "INNER JOIN collectionmovie USING (movie_id) "
				+ " WHERE " + getCollectionsPredicate();

		try (final PreparedStatement ps = jdbc.lookupPS("INSERT INTO shot VALUES(?, ?, ?, ?)");

				final ResultSet rs = oracle.sqlQuery(sql);) {
			UtilString.printDateNmessage("Elamp.copyShotTable maxMediaLength=" + maxMediaLength + " mediaLengthIDtype="
					+ mediaLengthIDtype + ":\n" + MyResultSet.valueOfDeep(rs, 10));

			// jdbc.sqlUpdate("ALTER TABLE shot DISABLE KEYS");
			jdbc.copyOracleTableToMySQL(ps, rs);
			while (rs.next()) {
				ps.setString(1, rs.getString("movie_id"));
				ps.setString(2, rs.getString("shotbreak_id"));
				ps.setString(3, rs.getString("name"));
				ps.setString(4, rs.getString("media_length"));
				jdbc.sqlUpdate(ps);
			}
			// jdbc.sqlUpdate("ALTER TABLE shot ENABLE KEYS");
		}
	}

	// private void createWordNetHierarchyTable() throws SQLException {
	// jdbc.sqlUpdate("DROP TABLE IF EXISTS word_hierarchy");
	//
	// jdbc.sqlUpdate("CREATE TABLE word_hierarchy ("
	// + " type VARCHAR(255) NOT NULL,"
	// + " collection VARCHAR(255) NOT NULL,"
	// + " word VARCHAR(255) NOT NULL," + " ancestors VARCHAR(255)"
	// // + ", PRIMARY KEY (feature_id) "
	// + ") ENGINE=MyISAM");
	// final String[] collections = { "KINDRED", "MEDTEST", "PSTRAIN",
	// "RESEARCH" };
	// final String[] types = { "OCR", "ASR" };
	//
	// System.out.println("Elamp.createWordNetHierarchyTable: "
	// + UtilString.valueOfDeep(collections));
	//
	// try (final PreparedStatement ps = jdbc
	// .lookupPS("INSERT INTO word_hierarchy VALUES(?, ?,?,?)");) {
	// for (final String collection : collections) {
	// ps.setString(2, collection);
	// for (final String type : types) {
	// ps.setString(1, type);
	// final String[] lines = readLines(
	// "C:\\Users\\mad\\Documents\\IDriveSync\\IDriveSync\\workspace\\bungeeDBscripts\\InstallBungee\\Elamp\\wordnet\\",
	// type + "-" + collection + "-FATHER.txt");
	// for (int line = 0; line < lines.length; line++) {
	// final List<String> wordAncestors = wordAncestors(line,
	// lines, 12);
	// final String word = wordAncestors.get(wordAncestors
	// .size() - 1);
	// ps.setString(3, word);
	// final String ancestorString = UtilString.join(
	// wordAncestors, " -- ");
	// ps.setString(4, ancestorString);
	// ps.executeUpdate();
	// }
	// }
	// }
	// }
	// }
	//
	// private List<String> wordAncestors(final int wordIndex,
	// final String[] lines, final int level) {
	// final String line = lines[wordIndex];
	// final String[] cols = line.split("\t");
	// assert cols.length == 2;
	// final String word = cols[0];
	// final int parent = Integer.parseInt(cols[1]);
	// final List<String> result = parent >= 0 && level > 0 ? wordAncestors(
	// parent, lines, level - 1) : new LinkedList<String>();
	// result.add(word);
	// return result;
	// }

	/**
	 * Insert "Subject" relations from Semantic_Features =>
	 * semantic_feature_hierarchy into raw_item_facet.
	 *
	 * Semantic_Features: [shot_id | movie_id, feature_id]
	 * semantic_feature_hierarchy: [feature_id, facet_id]
	 */
	private void readSemanticFeatures() throws SQLException {
		UtilString.printDateNmessage("Elamp.readSemanticFeatures...");
		createSemanticFeatureHierarchyTable();

		final Facet subject = Facet.getPreparsedFacet("Subject");
		populateHandler.fields.add(subject);

		UtilString.printDateNmessage("Elamp.readSemanticFeatures updating raw_item_facet...");

		final String sql = "INSERT INTO raw_item_facet (" + "SELECT DISTINCT " + itemTableIDcolumn + ", facet_id "
				+ "FROM Semantic_Features INNER JOIN semantic_feature_hierarchy USING (feature_id) )";

		final int nRows = jdbc.sqlUpdate(sql);
		UtilString.printDateNmessage("...updated " + nRows + " rows.");
		jdbc.dropTable("semantic_feature_hierarchy");
	}

	// Only depends on files in
	// workspace\bungeeDBscripts\InstallBungee\Elamp
	// semantic_feature_hierarchy: [feature_id, facet_id]
	private void createSemanticFeatureHierarchyTable() throws SQLException {
		db.startReadingData();
		createMyFeatureToAncestorsFromCSV();

		jdbc.dropAndCreateTable("semantic_feature_hierarchy",
				"(feature_id " + semanticFeatureIDtype + ", facet_id " + itemIDtype
				// + ", KEY (feature_id)"
						+ ") ENGINE=Memory");

		try (ResultSet rs = jdbc.sqlQuery("SELECT feature_id, ancestors FROM my_feature_to_ancestors "
				+ "INNER JOIN my_feature USING (my_feature_id)");

				PreparedStatement ps = jdbc.lookupPS("INSERT INTO semantic_feature_hierarchy VALUES(?, ?)");) {
			final String prefix = "Subject" + Compile.FACET_HIERARCHY_SEPARATOR;
			while (rs.next()) {
				ps.setString(1, rs.getString("feature_id"));
				final int[] facets = db.getFacets(prefix + rs.getString("ancestors"));
				if (INSERT_ANCESTORS_INITIALLY) {
					for (final int facet : facets) {
						ps.setInt(2, facet);
						jdbc.sqlUpdate(ps);
					}
				} else {
					final int facet = facets[facets.length - 1];
					ps.setInt(2, facet);
					jdbc.sqlUpdate(ps);
				}
			}
		}
		jdbc.dropTable("my_feature_to_ancestors");
	}

	private void createMyFeatureToAncestorsFromCSV() throws SQLException {
		jdbc.dropAndCreateTable("my_feature_to_ancestors",
				"(my_feature_id VARCHAR(255) NOT NULL" + ", ancestors VARCHAR(255)"
				// Increasing varcharsize above 255 slows things waaaay
				// down
						+ ", KEY (my_feature_id)" + ") ENGINE=Memory");

		try (final PreparedStatement ps = jdbc.lookupPS("INSERT INTO my_feature_to_ancestors VALUES(?, ?)");) {
			final String[] renames = UtilFiles.readLines(
					"C:\\Users\\mad\\Documents\\IDriveSync\\IDriveSync\\workspace"
							+ "\\bungeeDBscripts\\InstallBungee\\Elamp\\Semantic Features\\",
					"prototype_concept_categories.csv");
			for (final String rename : renames) {
				// System.out.println("Elamp.insertIntoHierarchyFromCSV " +
				// rename);

				final String[] columns = rename.split(",");
				final String my_feature_id = columns[2];
				final String name = columns[1];

				final StringBuilder ancestors = new StringBuilder();
				for (int column = 3; column < columns.length; column++) {
					final String intermediate = columns[column];
					if (UtilString.isNonEmptyString(intermediate) && !intermediate.equalsIgnoreCase(name)) {
						if (ancestors.length() > 0) {
							ancestors.append(Compile.FACET_HIERARCHY_SEPARATOR);
						}
						ancestors.append(intermediate);
					}
				}
				if (ancestors.length() > 0) {
					ancestors.append(Compile.FACET_HIERARCHY_SEPARATOR);
				}
				ancestors.append(name);
				jdbc.sqlUpdateWithStringArgs(ps, my_feature_id, ancestors.toString());
			}
		}
	}

	/**
	 * Inserts attributes and attribute-like relations from movie/shot table(s)
	 * into items.
	 *
	 * Try writing a PopulateHandler method (fields, sql) that gathers all the
	 * inserts into one sql update.
	 */
	@SuppressWarnings("null")
	private void readAttributes() throws SQLException {
		db.startReadingData();
		// final Attribute name = FunctionalAttribute.getAttribute("name",
		// false);
		// final Attribute movie_id =
		// FunctionalAttribute.getAttribute("movie_id",
		// false);

		// List of columns that are in the movie table, even though the
		// itemTable = "shot"
		final String[] movieTableAttributes = {};
		final String[] semanticTableAttributes = { "COMMENTS" };
		final List<QualifiedField> attributes = new ArrayList<>();
		final @NonNull String itemDescCols[] = UtilArray
				.subArray(
						db.ensureItemColumns(
								jdbc.sqlQueryInt("SELECT MAX(" + itemTableIDcolumn + ") FROM " + itemTable)).split(","),
						2);
		for (final @NonNull String descColumn : itemDescCols) {
			final Attribute attribute = FunctionalAttribute.getAttribute(descColumn, false);
			final String table = ArrayUtils.contains(movieTableAttributes, descColumn) ? "movie"
					: ArrayUtils.contains(semanticTableAttributes, descColumn) ? "semantic_features" : itemTable;
			attributes.add(new QualifiedField(attribute, table));
		}

		createParsedHierarchyTable(attributes, getFacets());
		Facet.getNumericFacet(CONNECT_STRING);
	}

	@SuppressWarnings("null")
	private List<QualifiedField> getFacets() {
		final List<QualifiedField> result = new LinkedList<>();
		result.add(new QualifiedField(Facet.getGenericFacet("Collection"), "movie"));
		result.add(new QualifiedField(Facet.getTimeFacet("Length"), itemTable));

		final int[][] commonResolutions = { { 640, 480 }, { 320, 240 }, { 1280, 720 }, { 480, 360 }, { 640, 360 } };
		result.add(new QualifiedField(Facet.getRoundToCommon2DValuesFacet("Resolution", commonResolutions, true),
				"movie"));

		final int[] commonFPS = { 15, 25, 29, 30 };
		result.add(new QualifiedField(Facet.getRoundToCommonValuesFacet("FPS", commonFPS, true), "movie"));

		if (ArrayUtils.contains(sm_main.getStringValue("collectionIDs").split(","), "5")) {
			result.add(new QualifiedField(Facet.getDateFacet("Date"), "movie"));
			result.add(new QualifiedField(Facet.getGenericFacet("Neighborhood"), "movie"));
			result.add(new QualifiedField(Facet.getGenericFacet("Battalion"), "movie"));
			result.add(new QualifiedField(Facet.getGenericFacet("Brigade"), "movie"));
			result.add(new QualifiedField(Facet.getGenericFacet("spec_brigade"), "movie"));
			result.add(new QualifiedField(Facet.getGenericFacet("Individual"), "movie"));
			result.add(new QualifiedField(Facet.getGenericFacet("Council"), "movie"));
		} else if (ArrayUtils.contains(sm_main.getStringValue("collectionIDs").split(","), "6")) {
			result.add(new QualifiedField(Facet.getDateFacet("DATE"), "semantic_features"));
			final String[] genericFacets = { "BRIGADE_FSA_NAME", "BATTALION_FSA_NAME", "COMPANY_FSA_NAME",
					"SPECIAL_BRIGADE_FSA_NAME", // "COMMENTS",
					"CONFIRMED_COUNTRY_OF_ORIGIN", "POSSIBLE_COUNTRY_OF_ORIGIN", "SUBCOUNCIL_DIVISION_NAME",
					"COUNCIL_DIVISION_NAME", "CITY_COUNCIL_NAME", "REGIONAL_COUNCIL_NAME", "NATIONAL_COUNCIL_NAME",
					"INDIVIDUALS_SEEN_IN_VIDEO", "GEO_MOHAFAZA", "GEO_MANTAQA", "GEO_NAHIA", "GEO_CITY_TOWN",
					"GEO_NEIGHBORHOOD", "GEO_GENERAL_AREA", "ARMS_SEEN_IN_VIDEO", "ARMS_TYPE_SEEN_IN_VIDEO",
					"SERIAL_NUMBER" };
			for (final @NonNull String genericFacet : genericFacets) {
				result.add(new QualifiedField(Facet.getGenericFacet(genericFacet), "semantic_features"));
			}
		}
		return result;
	}

	/**
	 * Populate item based on attributes, which must be ordered according to
	 * globals.itemDescriptionFields.
	 *
	 * Create a table with the parsed facet_id of each UNIQUE raw value and its
	 * ancestors, rather than parsing the raw value for each item:
	 *
	 * [rawValue001, facet_id] [rawValue001, parent_facet_id] [rawValue001,
	 * grandparent_facet_id] [rawValue001, ...]
	 *
	 * @param attributes
	 *            Must be ordered according to item columns
	 * @param facets
	 * @throws SQLException
	 */
	private void createParsedHierarchyTable(final List<QualifiedField> attributes, final List<QualifiedField> facets)
			throws SQLException {
		UtilString.printDateNmessage("Elamp.createParsedHierarchyTable...");

		db.startReadingData();
		parseAttributes(attributes);

		jdbc.dropAndCreateTable("parsed_hierarchy",
				"(raw_value VARCHAR(255) NOT NULL,"
						// Increasing varcharsize above 255 slows things waaaay
						// down
						+ " facet_id " + semanticFeatureIDtype + ", PRIMARY KEY (raw_value)" + ") ENGINE=MyISAM");
		for (final QualifiedField qField : facets) {
			final String qtable = qField.table;
			String et = qtable.equals("shot") ? "shot" : enhancedItemTable;
			if (!et.contains(qtable)) {
				et += " INNER JOIN " + qtable + " USING (movie_id)";
			}
			parseFacet((Facet) qField.field, qtable, et);
		}
		// jdbc.dropTable("parsed_hierarchy");
	}

	private void parseAttributes(final List<QualifiedField> attributes) throws SQLException {
		assert jdbc.nRows("raw_item_facet") == 0;

		boolean isOnlyShots = true;
		String et = enhancedItemTable;
		final StringBuilder itemSQL = new StringBuilder();
		itemSQL.append("INSERT INTO item SELECT DISTINCT ").append(itemTableIDcolumn).append(", NULL");
		for (final QualifiedField qField : attributes) {
			final String qtable = qField.table;
			if (!qtable.equals("shot")) {
				isOnlyShots = false;
				if (!et.contains(qtable)) {
					et += " INNER JOIN " + qtable + " USING (movie_id)";
				}
			}
			final Field field = qField.field;
			populateHandler.fields.add(field);
			itemSQL.append(", ").append(qtable).append(".").append(field.name);
		}
		itemSQL.append(" FROM ").append((isOnlyShots ? "shot" : et));
		@SuppressWarnings("null")
		final @NonNull String sql = itemSQL.toString();
		jdbc.sqlUpdate(sql);
	}

	@SuppressWarnings("null")
	private void parseFacet(final Facet facet, final String unenhancedItemTable, final String _enhancedItemTable)
			throws SQLException {
		UtilString.printDateNmessage("   Elamp.parseFacet " + facet);
		populateHandler.fields.add(facet);
		final String facetName = facet.name;
		final String qualifiedFacetName = unenhancedItemTable + "." + facetName;
		jdbc.sqlUpdate("TRUNCATE TABLE parsed_hierarchy");

		@SuppressWarnings("resource")
		final PreparedStatement ps = jdbc.lookupPS("INSERT INTO parsed_hierarchy VALUES(?, ?)");
		try (ResultSet rs = jdbc.sqlQuery("SELECT DISTINCT " + qualifiedFacetName + " FROM " + _enhancedItemTable
				+ " WHERE " + qualifiedFacetName + " IS NOT NULL");) {
			while (rs.next()) {
				final String rawValue = rs.getString(1);
				ps.setString(1, rawValue);
				final String[][] parseds = facet.parse(rawValue, populateHandler);
				for (final @NonNull String[] parsed : parseds) {
					final List<String> ancestors = facet.path(rawValue, parsed);
					final int[] ancestorIDs = db.getFacets(ancestors);

					if (INSERT_ANCESTORS_INITIALLY) {
						for (final int ancestorID : ancestorIDs) {
							ps.setInt(2, ancestorID);
							jdbc.sqlUpdateWithIntArgs(ps);
						}
					} else {
						final int ancestorID = ancestorIDs[ancestorIDs.length - 1];
						ps.setInt(2, ancestorID);
						jdbc.sqlUpdateWithIntArgs(ps);
					}
				}
			}

			UtilString.printDateNmessage("   Elamp.parseFacet inserting into raw_item_facet...");
			final int nRows = jdbc.sqlUpdate("INSERT INTO raw_item_facet SELECT DISTINCT " + itemTableIDcolumn
					+ ", parsed_hierarchy.facet_id" + " FROM " + _enhancedItemTable + " INNER JOIN parsed_hierarchy ON "
					+ qualifiedFacetName + " = parsed_hierarchy.raw_value");
			UtilString.printDateNmessage("... inserted " + nRows + " rows");
		}
	}

	private static class QualifiedField {
		Field field;
		String table;

		QualifiedField(final Field _field, final String _table) {
			field = _field;
			table = _table;
		}
	}

	/**
	 * Insert ASR/OCR relations into raw_item_facet.
	 *
	 * @param facetTypeName
	 *            "ASR" or "OCR"
	 */
	private void readWordFeature(final @NonNull String facetTypeName) throws SQLException {
		db.startReadingData();
		final Facet facet = Facet.getGenericFacet(facetTypeName);
		final String wordTable = facetTypeName + "raw";

		final String _enhancedItemTable = wordTable + " INNER JOIN " + itemTable + " ON " + wordTable + ".record_num = "
				+ itemTable + "." + itemTableIDcolumn;

		parseFacet(facet, wordTable, _enhancedItemTable);

		// final String sQL = "SELECT record_num, stem_word " + "FROM "
		// + wordTable;
		//
		// try (ResultSet rs = jdbc.sqlQuery(sQL);) {
		// UtilString.printDateNmessage("Elamp.readWordFeature " + wordTable
		// + ":\n" + MyResultSet.valueOfDeep(rs, 10));
		//
		// while (rs.next()) {
		// db.recordNum = rs.getInt("record_num");
		// facet.insert(rs.getString("stem_word"), populateHandler);
		// }
		// }
	}
}