package edu.cmu.cs.bungee.populate.scripts;

import static edu.cmu.cs.bungee.javaExtensions.UtilFiles.getFiles;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.compile.Database;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.Token;
import edu.cmu.cs.bungee.populate.Facet;
import edu.cmu.cs.bungee.populate.Options;
import edu.cmu.cs.bungee.populate.PopulateHandler;

/**
 * Run it like this:
 *
 * -user root -pass tartan01 -verbose -copyTablesFromDB personal
 *
 * -db personal2 -reset -renumber
 *
 * -photoDirectory C:\Users\mad\OneDrive\Pictures
 */
class PersonalPhotos extends Options { // NO_UCD (unused code)

	private static final String[] IGNORE_EXTENSIONS = { "7z", "avi", "css", "doc", "htm", "html", "js", "ppt", "skb",
			"skp", "xcf", "xml", "xls" };

	private static final String[] IMAGE_EXTENSIONS = { "bmp", "gif", "jpg", "pdf", "png" };

	private static final String PERSONAL_DB_IMAGE_DIRECTORY = "C:/Program Files/apache-tomcat-7.0.47/webapps/bungee/Images/Personal Photos/";

	private String photoDirectory;

	private String photoDirectoryUsingForwardSlashes;

	public static void main(final String[] args) {
		try {
			new PersonalPhotos().init(args);
		} catch (final Throwable e) {
			e.printStackTrace(System.out);
		}
	}

	private void init(final String[] args) throws Throwable {
		// System.out.println("DBpersonalPhotos.init");
		sm_main.addStringToken("photoDirectory", "root of directory tree containing photos.", Token.optRequired, "");
		super.init(false, args);
	}

	@Override
	protected void readData() throws SQLException, IOException {
		final String photoDirectoryValue = sm_main.getStringValue("photoDirectory");
		assert photoDirectoryValue != null;
		photoDirectory = UtilString.removeSuffix(photoDirectoryValue, "\\") + "\\";
		photoDirectoryUsingForwardSlashes = photoDirectory.replace('\\', '/');
		// System.out.println("DBpersonalPhotos.readData " + photoDirectory);
		jdbc.sqlUpdate("INSERT INTO " + jdbc.dbName + ".raw_facet SELECT * FROM personal.raw_facet");
		readDir(photoDirectory, new LinkedList<FacetValue>());
		copyItemFacetData();
	}

	@SuppressWarnings("resource")
	private void copyItemFacetData() throws SQLException {
		@SuppressWarnings("null")
		final JDBCSample jdbcExisting = JDBCSample.getMySqlJDBCSample(sm_main.getStringValue("server"), "personal",
				sm_main.getStringValue("user"), sm_main.getStringValue("pass"));
		int nMatches = 0;
		final java.sql.PreparedStatement psExisting = jdbcExisting
				.lookupPS("SELECT facet_id FROM raw_item_facet WHERE record_num = ?");
		final java.sql.PreparedStatement psNew = jdbc.lookupPS("REPLACE INTO raw_item_facet VALUES(?, ?)");
		try (ResultSet rsExisting = jdbcExisting.sqlQuery("SELECT URI, record_num FROM item");) {
			while (rsExisting.next()) {
				final String uriExisting = PERSONAL_DB_IMAGE_DIRECTORY + rsExisting.getString("URI");
				assert !uriExisting.contains("\\") : uriExisting;
				final int recordExisting = rsExisting.getInt("record_num");
				nMatches += findMatchingItem(jdbcExisting, psExisting, psNew, recordExisting, uriExisting);
			}
			System.out.println("\nDBpersonalPhotos.copyItemFacetData found " + nMatches + " matches out of "
					+ MyResultSet.nRows(rsExisting) + " rows.\n");
		}
	}

	private int findMatchingItem(final JDBCSample jdbcExisting, final java.sql.PreparedStatement psExisting,
			final @NonNull java.sql.PreparedStatement psNew, final int recordExisting,
			final @NonNull String uriExisting) throws SQLException {
		int result = 0;
		final String filenameExisting = UtilString.getLastSplit(uriExisting, "/");
		assert !filenameExisting.contains("\\") : filenameExisting;
		final String sql = "SELECT URI, record_num FROM item WHERE URI LIKE "
				+ JDBCSample.quoteForSQL("%/" + filenameExisting);
		try (ResultSet rsNew = jdbc.sqlQuery(sql);) {
			int recordNew = -1;
			final File imageFileExisting = new File(uriExisting);
			if (!imageFileExisting.isFile()) {
				System.err.println("File not found: " + uriExisting);
			}
			final long lengthExisting = imageFileExisting.length();
			while (rsNew.next()) {
				final String uriNew = photoDirectoryUsingForwardSlashes + rsNew.getString("URI");
				assert !uriNew.contains("\\") : uriNew;
				final File imageFileNew = new File(uriNew);
				final long lengthNew = imageFileNew.length();
				if (lengthNew == lengthExisting) {
					recordNew = rsNew.getInt("record_num");
				}
			}
			if (recordNew < 0) {
				// final int nRows = MyResultSet.nRows(rs2);
				// System.out.println(nRows + " file:///" + uri + " size="
				// + UtilString.addCommas(length) + "B:");

				// copyImageToOneDrive(uri, imageFile);
				// printNameMatches(rs2);
			} else {
				result = 1;
				psExisting.setInt(1, recordExisting);
				final int[] facets = jdbcExisting.sqlQueryIntArray(psExisting);
				// final java.sql.PreparedStatement psGetParent = jdbc
				// .lookupPS("select parent_facet_id from raw_facet where
				// facet_id = ?");
				for (final int facet : facets) {
					// psGetParent.setInt(1, facet);
					// System.out.println(jdbc.sqlQueryInt(psGetParent) + "."
					// + facet);
					// All personal facets should have the same IDs
					// as the new database
					jdbc.sqlUpdateWithIntArgs(psNew, recordNew, facet);
				}
			}
		}
		return result;
	}

	// @SuppressWarnings("unused")
	// private void copyImageToOneDrive(final String uri, final File
	// imageFile)
	// throws IOException {
	// assert uri.startsWith(PERSONAL_DB_IMAGE_DIRECTORY);
	// final String baseURI =
	// uri.substring(PERSONAL_DB_IMAGE_DIRECTORY.length());
	// final String uri2 = photoDirectoryUsingForwardSlashes + baseURI;
	// final File imageFile2 = new File(uri2);
	// final boolean success = imageFile2.getParentFile().mkdirs();
	// assert success;
	// Files.copy(imageFile.toPath(), imageFile2.toPath());
	// }

	private void readDir(final String directory, Collection<FacetValue> facetNames) throws SQLException, IOException {
		// System.out.println("DBpersonalPhotos.readDir " + directory + " "
		// + facetNames);
		final Collection<FacetValue> _facetNames = getFacetNames(directory);
		_facetNames.addAll(facetNames);
		facetNames = _facetNames;
		final File[] files = getFiles(directory);

		// @SuppressWarnings("unused")
		// private void printNameMatches(final ResultSet rs2) throws
		// SQLException {
		// rs2.beforeFirst();
		// while (rs2.next()) {
		// final String uri2 = photoDirectoryUsingForwardSlashes +
		// rs2.getString("URI");
		// final File imageFile2 = new File(uri2);
		// final long length2 = imageFile2.length();
		// System.out.println(" file:///" + uri2 + " size=" +
		// UtilString.addCommas(length2) + "B");
		// }
		// }

		for (final File file : files) {
			if (file.isDirectory()) {
				final String dirname = file.getCanonicalPath();
				readDir(dirname, facetNames);
			} else {
				readImage(file, facetNames);
			}
		}
	}

	private void readImage(final File file, final Collection<FacetValue> facetNames) throws SQLException, IOException {
		final String filename = file.getName();
		assert filename != null;
		final String lowerCaseExtension = UtilString.getLastSplit(filename, "\\.").toLowerCase();
		if (!ArrayUtils.contains(IGNORE_EXTENSIONS, lowerCaseExtension) && !filename.equals("Thumbs.db")
				&& !filename.contains("_unrotated.")) {
			assert ArrayUtils.contains(IMAGE_EXTENSIONS, lowerCaseExtension) : filename;
			final Database db = populateHandler.getDB();
			db.newItem();
			db.insertAttribute("URI", canonicalize(file.getCanonicalPath()));
			for (final FacetValue facetValue : facetNames) {
				facetValue.insert(populateHandler);
			}
		}
	}

	private final Pattern decadePattern = Pattern.compile("(?:19|20)\\d0s");
	private final Pattern yearPattern = Pattern.compile("(?:19|20)\\d{2}");

	private Collection<FacetValue> getFacetNames(final String dirname) {
		final Collection<FacetValue> result = new LinkedList<>();

		final String canonicalDirName = canonicalize(dirname);
		if (UtilString.isNonEmptyString(canonicalDirName)) {
			final edu.cmu.cs.bungee.populate.Facet folder = Facet.getGenericFacet("Folder");
			result.add(new FacetValue(folder, canonicalDirName));
		}

		final Facet date = Facet.getDateFacet("Date");
		Matcher matcher = decadePattern.matcher(dirname);
		if (matcher.find()) {
			final String group = matcher.group();
			assert group != null;
			result.add(new FacetValue(date, group));
		} else {
			matcher = yearPattern.matcher(dirname);
			if (matcher.find()) {
				final String group = matcher.group();
				assert group != null;
				result.add(new FacetValue(date, group));
			}
		}
		return result;
	}

	private @NonNull String canonicalize(final String pathname) {
		assert pathname.startsWith(photoDirectory) : "\n" + pathname + "\n" + photoDirectory;
		String pathname2 = pathname.substring(photoDirectory.length());
		// System.out.println("canonicalize " + pathname + " => " + pathname2
		// + " " + photoDirectory);
		pathname2 = pathname2.replace('\\', '/');
		assert pathname2 != null;
		return pathname2;
	}

	private static class FacetValue {
		final @NonNull Facet facet;
		final @NonNull String value;

		FacetValue(final @NonNull Facet _facet, final @NonNull String _value) {
			super();
			assert UtilString.isNonEmptyString(_value);
			facet = _facet;
			value = _value;
		}

		@Override
		public String toString() {
			return UtilString.toString(this, facet + " " + value);
		}

		boolean insert(final PopulateHandler populateHandler) throws SQLException {
			return facet.insert(value, populateHandler);
		}
	}

}
