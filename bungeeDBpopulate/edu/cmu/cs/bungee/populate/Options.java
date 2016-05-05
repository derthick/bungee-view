package edu.cmu.cs.bungee.populate;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.jdt.annotation.NonNull;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.sun.image.codec.jpeg.ImageFormatException;

import edu.cmu.cs.bungee.compile.Database;
import edu.cmu.cs.bungee.compile.PopulateImageTable;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilFiles;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.MyApplicationSettings;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.Token;

/**
 * This controls populating the raw tables of a Bungee View database. The
 * default is to use a parser, in which case subclasses should override
 * startElement, etc. Otherwise they should override readData.
 */
public class Options extends DefaultHandler {

	public static final int DEFAULT_THUMB_QUALITY = 80;

	public static final int DEFAULT_THUMB_MAX_DIMENSION = 400;

	protected final MyApplicationSettings sm_main = new MyApplicationSettings();

	protected JDBCSample jdbc;
	protected PopulateHandler populateHandler;

	protected PopulateHandler getPopulateHandler() throws SQLException {
		return new PopulateHandler(getJDBC(), sm_main.getBooleanValue("verbose"));
	}

	/**
	 * Process args and populate().
	 */
	protected void init(final boolean isParser, final String[] args) throws Throwable {
		addOptions(isParser);
		if (args != null && sm_main.parseArgs(args)) {
			// System.out.println("PopulateHandler.init imageUrlGetter="
			// + getStringValue("imageUrlGetter"));
			assert isParser == UtilString.isNonEmptyString(sm_main
					.getStringValue("files")) : "You must specify at least one 'files' iff you're using a parser.";
			try {
				jdbc = getJDBC();
				// if (!jdbc.columnExists("globals", "build_args")) {
				// jdbc.sqlUpdate("ALTER TABLE globals ADD COLUMN build_args
				// TEXT AFTER isEditable");
				// }
				try (final PreparedStatement ps = jdbc.lookupPS("UPDATE globals SET build_args = ?");) {
					jdbc.sqlUpdateWithStringArgs(ps, UtilString.valueOfDeep(args));
				}
				removeBackupDirectory();
				populate();
			} finally {
				if (jdbc != null) {
					jdbc.close();
				}
			}
		}
	}

	// private void logRestore() throws SQLException {
	// final File dataDir = new File(jdbc.sqlQueryString("SELECT @@datadir"));
	// final File bkpDir = new File(new File(dataDir.getParentFile(),
	// "dataBKP"), jdbc.dbName);
	// if (bkpDir.exists()) {
	// // otherwise it will be created the first time the db is edited.
	// logRestore(bkpDir);
	// }
	// }

	private void removeBackupDirectory() {
		// e.g. C:\ProgramData\MySQL\MySQL Server 5.6\data\
		final File dataDir = new File(jdbc.sqlQueryString("SELECT @@datadir"));
		final File bkpDir = new File(new File(dataDir.getParentFile(), "dataBKP"), jdbc.dbName);
		if (bkpDir.exists()) {
			UtilFiles.delete(bkpDir.toPath());
		}
	}

	protected void addOptions(final boolean isParser) {
		// System.out.println("Options.addOptions isParser=" + isParser);
		sm_main.addStringToken("db", "database to write to", Token.optRequired | Token.optSwitch, "");
		sm_main.addStringToken("copyTablesFromDB",
				"create any missing tables by copying schema (images, raw_facet, user_actions, raw_item_facet, item)"
						+ " or schema and data (raw_facet_type, globals) from this database",
				Token.optSwitch, null);
		sm_main.addStringToken("server", "MySQL server", Token.optSwitch, "jdbc:mysql://localhost/");
		sm_main.addStringToken("user", "MySQL user", Token.optSwitch, "bungee");
		sm_main.addStringToken("pass", "MySQL user password", Token.optRequired, "");
		sm_main.addStringToken("toMerge", "Merge items with the same URI that differ only in these tag categories",
				Token.optSwitch, null);

		sm_main.addStringToken("copyThumbsFrom", "Database to copy thumbnails from, based on the item.URI column",
				Token.optSwitch, null);
		sm_main.addStringToken("thumbUrlGetter", "SQL expression for thumbnail location. Defaults to globals.itemURL",
				Token.optSwitch, null);
		sm_main.addStringToken("thumbRegexp", "Java regexp to apply to url", Token.optSwitch, null);
		sm_main.addIntegerToken("thumbMaxDimension", "Maximum thumbnail size", Token.optSwitch,
				DEFAULT_THUMB_MAX_DIMENSION);
		sm_main.addIntegerToken("thumbQuality", "Thumbnail quality (1-100)", Token.optSwitch, DEFAULT_THUMB_QUALITY);
		if (isParser) {
			// Currently only used by OAI
			sm_main.addStringToken("files", "Files to parse", Token.optSwitch, null);
		}
		sm_main.addStringToken("cities", "List of place names, just to note they are places", Token.optSwitch, null);
		sm_main.addStringToken("renames",
				"Filename containing tag-name pairs, where the first is replaced with the second", Token.optSwitch,
				null);
		// Could renames be subsumed by moves?
		sm_main.addStringToken("moves",
				"Filename containing tag-hierarchy pairs, where the first hierarchy is moved to the second",
				Token.optSwitch, null);
		sm_main.addStringToken("directory", "default directory for file arguments", Token.optSwitch,
				"C:\\Users\\mad\\Documents\\IDriveSync\\IDriveSync\\documents\\Work\\Projects\\ArtMuseum\\PopulateDataFiles");
		sm_main.addBooleanToken("reset", "clear raw_facet, raw_item_facet, and item?", Token.optSwitch);
		sm_main.addIntegerToken("maxItems", "Ensure item table can store a record_num as large as this",
				Token.optSwitch, 1_000_000);
		sm_main.addBooleanToken("renumber", "renumber record_num to use sequential IDs starting at 1", Token.optSwitch);
		sm_main.addBooleanToken("dontComputeCorrelations",
				// Will never compute correlations if item_facet has more than
				// Compile.MAX_CORRELATION_QUERY_ROWS
				"This enables Influence Diagrams, but can be prohibitively expensive if item_facet is large (100,000 or more)",
				Token.optSwitch);
		sm_main.addBooleanToken("singularizeFacetNames",
				"Change plural tag names to singular. (Not ready for prime time.)", Token.optSwitch);
		sm_main.addBooleanToken("verbose", "print tag hierarchy manipulations?", Token.optSwitch);
		sm_main.addBooleanToken("dontReadData",
				"Don't change items or facets; just load thumbs.  If -verbose, will print would-be DB updates",
				Token.optSwitch);
		sm_main.addBooleanToken("dontLoadThumbs", "Don't load thumbs.", Token.optSwitch);
	}

	final @NonNull String[] tablesToCopy = { "raw_facet_type", "globals" };
	final @NonNull String[] tablesToCreate = { "images", "raw_facet", "user_actions", "raw_item_facet", "item" };

	protected @NonNull String[] tablesToCopy() {
		return tablesToCopy;
	}

	protected @NonNull JDBCSample getJDBC() throws SQLException {
		if (jdbc == null) {
			final String dbName = Util.nonNull(sm_main.getStringValue("db"));
			final String copyTablesFromDB = Util.nonNull(sm_main.getStringValue("copyTablesFromDB"));
			final String server = Util.nonNull(sm_main.getStringValue("server"));
			final String user = Util.nonNull(sm_main.getStringValue("user"));
			final String pass = Util.nonNull(sm_main.getStringValue("pass"));
			if (UtilString.isNonEmptyString(copyTablesFromDB)) {
				try (final JDBCSample _jdbc = JDBCSample.getMySqlJDBCSample(server, copyTablesFromDB, user, pass);) {
					_jdbc.ensureDatabase(dbName, tablesToCreate, tablesToCopy());
				}
			}
			jdbc = JDBCSample.getMySqlJDBCSample(server, dbName, user, pass);
		}
		assert jdbc != null;
		return jdbc;
	}

	/**
	 * readAndProcessData() and loadThumbs().
	 */
	private void populate() throws Throwable {
		populateHandler = getPopulateHandler();

		final boolean dontReadData = sm_main.getBooleanValue("dontReadData");
		if (!dontReadData || sm_main.getBooleanValue("verbose")) {
			populateHandler.dontUpdate = dontReadData;
			readAndProcessData();
			populateHandler.dontUpdate = false;
		}

		loadThumbs();
		// populateHandler.updateImageSizes();
	}

	private void loadThumbs() throws SQLException, ImageFormatException {
		if (jdbc.nRows("item") < Database.MAX_THUMBS && !sm_main.getBooleanValue("dontLoadThumbs")) {
			final int nMissingImages = nMissingImages();
			if (nMissingImages == 0) {
				System.out.println("No images to load.");
			} else {
				System.out.println(nMissingImages + " images to load.");
				final String copyThumbsFrom = sm_main.getStringValue("copyThumbsFrom");
				if (copyThumbsFrom != null) {
					populateHandler.copyThumbsNoURI(copyThumbsFrom);
				}
				final String urlGetter = getURLgetter();
				if (urlGetter != null) {
					final Pattern regex = getThumbRegex();
					final int maxDimension = sm_main.getIntegerValue("thumbMaxDimension");
					// PopulateHandler.loadImages(urlGetter, maxDimension,
					// maxDimension, UtilMath.constrain(
					// sm_main.getIntegerValue("thumbQuality"), 0,
					// 100), jdbc, false, null);
					PopulateImageTable.populateImageTable(urlGetter, regex, maxDimension, maxDimension,
							UtilMath.constrain(sm_main.getIntegerValue("thumbQuality"), 0, 100),
							populateHandler.getJDBC(), populateHandler.dontUpdate, null);
				}
				final int nRemainingMissingImages = nMissingImages();
				System.out.println("Loaded " + (nMissingImages - nRemainingMissingImages) + " images. Unable to load "
						+ nRemainingMissingImages + " images.");
			}
		}
	}

	private int nMissingImages() throws SQLException {
		final int nMissingImages = jdbc.sqlQueryInt("SELECT COUNT(item.record_num) " + "FROM item LEFT JOIN images "
				+ "USING (record_num) WHERE images.image IS NULL");
		return nMissingImages;
	}

	private Pattern getThumbRegex() throws SQLException {
		String regex = sm_main.getStringValue("thumbRegexp");
		if (regex == null) {
			regex = populateHandler.getJDBC().sqlQueryString("SELECT thumb_regexp FROM globals");
		} else {
			populateHandler.getJDBC().sqlUpdate("UPDATE globals SET thumb_regexp = '" + regex + "'");
		}
		final Pattern cPattern = UtilString.isNonEmptyString(regex) ? Pattern.compile(regex) : null;
		return cPattern;
	}

	private String getURLgetter() throws SQLException {
		String urlGetter = sm_main.getStringValue("thumbUrlGetter");
		if (urlGetter == null) {
			urlGetter = populateHandler.getJDBC().sqlQueryString("SELECT thumb_URL FROM globals", true);
		} else {
			populateHandler.getJDBC().sqlUpdate("UPDATE globals SET thumb_URL = " + JDBCSample.quoteForSQL(urlGetter));
		}
		if (urlGetter == null) {
			urlGetter = populateHandler.getJDBC().sqlQueryString("SELECT itemURL FROM globals", true);
		}
		if (urlGetter == null) {
			System.err.println("Can't find urlGetter");
		}
		return urlGetter;
	}

	private void readAndProcessData() throws Throwable {
		// Allows NULLS until compile sets a value
		// jdbc.SQLupdate("ALTER TABLE item MODIFY facet_names TEXT");

		if (sm_main.getBooleanValue("reset")) {
			populateHandler.clearTables(sm_main.getIntegerValue("maxItems"));
		}
		// populateHandler.reddUpTables(PopulateHandler.INPUT_TABLES);

		final String directory = sm_main.getStringValue("directory");
		final String renamesFile = sm_main.getStringValue("renames");
		if (renamesFile != null) {
			populateHandler.setRenames(parsePairFiles(directory, renamesFile));
		}
		final String movesFile = sm_main.getStringValue("moves");
		if (movesFile != null) {
			populateHandler.setMoves(parsePairFiles(directory, movesFile));
		}
		final String citiesFile = sm_main.getStringValue("cities");
		if (citiesFile != null) {
			populateHandler.setPlaces(parseSingletonFiles(directory, citiesFile));
		}

		// override this if not using a parser
		readData();
		populateHandler.cleanUp();
		fixImageRecordNumbers();

		if (sm_main.getBooleanValue("renumber")) {
			populateHandler.renumber();
		}
		populateHandler.renameNmove();
		if (sm_main.getBooleanValue("singularizeFacetNames")) {
			populateHandler.singularizeFacetNames();
		}

		final String facetTypesToMerge = sm_main.getStringValue("toMerge");
		if (facetTypesToMerge != null) {
			populateHandler.mergeDuplicateItems(facetTypesToMerge);
		}
		// populateHandler.promoteNonrestrictingFacets();
		populateHandler.compile(sm_main.getBooleanValue("dontComputeCorrelations"));
	}

	/**
	 * If reset has cleared item, but images was kept and has a URI column, use
	 * that to fix record_num's.
	 *
	 * @throws SQLException
	 */
	private void fixImageRecordNumbers() throws SQLException {
		if (jdbc.columnExists("images", "URI")) {
			// try (ResultSet rs = jdbc.sqlQuery("SELECT images.record_num "
			// + "FROM images LEFT JOIN item "
			// + "USING (record_num) WHERE item.URI IS NULL");) {
			final int max = jdbc.sqlQueryInt("SELECT MAX(record_num) FROM item");
			final int nRows = jdbc.sqlUpdate("CREATE TEMPORARY TABLE unmatched_images AS" + " SELECT images.record_num "
					+ "FROM images LEFT JOIN item " + "USING (record_num) WHERE item.URI IS NULL");
			if (nRows > 0) {
				jdbc.sqlUpdate("UPDATE images INNER JOIN unmatched_images USING (record_num)"
						+ " SET images.record_num = images.record_num + " + max);
				jdbc.sqlUpdate(
						"UPDATE IGNORE images INNER JOIN item USING (uri) SET images.record_num = item.record_num"
								+ " WHERE images.record_num > " + max);
			}
		}
	}

	/**
	 * @throws SQLException
	 */
	protected void readData() throws SAXException, IOException, ParserConfigurationException, SQLException {
		final String filenameList = sm_main.getStringValue("files");
		// non-NULL iff using parser
		if (filenameList != null) {
			final String directory = sm_main.getStringValue("directory");
			final SAXParser parser = getParser();
			final String[] filenames = filenameList.split(",");
			for (final String filename : filenames) {
				final File[] matches = UtilFiles.getFiles(directory, filename);
				for (final File file : matches) {
					System.out.println("\nParsing " + file);
					parser.parse(file.toString(), this);
				}
			}
			System.out.println("... done parsing ");
		}
	}

	private static SAXParser getParser() throws ParserConfigurationException, SAXException {
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		final SAXParser parser = factory.newSAXParser();
		return parser;
	}

	protected static List<String[]> parsePairFiles(final String directory, final String filenames) {
		List<String[]> result = null;
		if (filenames != null) {
			result = new LinkedList<>();
			final String[] fnames = filenames.split(",");
			for (final String fname : fnames) {
				final String[] lines = UtilFiles.readLines(directory, fname);
				for (int i = 0; i < lines.length; i++) {
					final String[] pair = lines[i].split("\t");
					assert pair.length == 2 : "On line " + (i + 1) + " of file " + fname
							+ ":\n Should have exactly one tab character: " + lines[i];
					pair[0] = pair[0].toLowerCase();
					result.add(pair);
				}
			}
		}
		return result;
	}

	private static Set<String> parseSingletonFiles(final String directory, final String filenames) {
		Set<String> result = null;
		if (filenames != null) {
			result = new HashSet<>();
			final String[] fnames = filenames.split(",");
			for (final String fname : fnames) {
				final String[] lines = UtilFiles.readLines(directory, fname);
				for (final String line : lines) {
					result.add(line);
				}
			}
		}
		return result;
	}

	// protected static String[] readLines(final String directory,
	// final String filename) {
	// System.out.println("Parsing " + filename);
	// final File f = new File(directory, filename);
	// return UtilFiles.readFile(f).split("\n");
	// }
}
