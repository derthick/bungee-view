package edu.cmu.cs.bungee.populate.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilFiles;
import edu.cmu.cs.bungee.populate.Attribute;
import edu.cmu.cs.bungee.populate.Facet;
import edu.cmu.cs.bungee.populate.Field;
import edu.cmu.cs.bungee.populate.Options;

class CSbib extends Options { // NO_UCD (unused code)

	// private static String baseURL =
	// "http://liinwww.ira.uka.de/bibliography/";

	private static final @NonNull String BROWSE_URL = "http://ftp.fi.muni.cz/pub/bibliography/";

	private static final String DOWNLOAD_DIR = "C:\\Documents and Settings\\mad\\My Documents\\Projects\\ArtMuseum\\CSbib";

	public static void main(final String[] args) {
		// if (false) // Just do this once
		// download();
		try {
			final CSbib csbib = new CSbib();
			csbib.init(true, args);
		} catch (final Throwable ex) {
			ex.printStackTrace(System.out);
		}
	}

	@SuppressWarnings("unused")
	private static void download() {
		try {
			final String topLevel = UtilFiles.readURL(BROWSE_URL);
			final Pattern p = Pattern.compile("href=\"(\\w*)/\"");
			final Matcher m = p.matcher(topLevel);
			while (m.find()) {
				final String sub = m.group(1);

				final String subdir = BROWSE_URL + "/" + sub;
				final String secondLevel = UtilFiles.readURL(subdir);
				final Pattern p2 = Pattern.compile("href=\"(\\w*\\.bib\\.gz)\"");
				final Matcher m2 = p2.matcher(secondLevel);
				while (m2.find()) {
					final String filename = m2.group(1);
					final String path = subdir + "/" + filename;
					System.out.println("copying " + path);
					UtilFiles.copyURI(new URI(path), new File(DOWNLOAD_DIR, filename));
				}
			}
		} catch (final MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// private String computeQuery() {
	// String[] levelOne = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
	// "K" };
	// String[] levelTwoThree = { "0", "1", "2", "3", "4", "5", "6", "7", "8",
	// "9" };
	// StringBuilder buf = new StringBuilder();
	// for (int i = 0; i < levelOne.length; i++) {
	// for (int j = 0; j < levelTwoThree.length; j++) {
	// for (int j2 = 0; j2 < levelTwoThree.length; j2++) {
	// if (buf.length() > 0)
	// buf.append(" ");
	// buf.append(levelOne[i]).append(".")
	// .append(levelTwoThree[j]).append(".").append(
	// levelTwoThree[j2]);
	// }
	// }
	// }
	// return buf.toString();
	// }

	@Override
	protected void readData() {
		final String filenameList = sm_main.getStringValue("files");
		if (filenameList != null) {
			final String directory = sm_main.getStringValue("directory");
			final String[] filenames = filenameList.split(",");
			for (final String filename : filenames) {
				final File[] matches = UtilFiles.getFiles(directory, filename);
				for (final File matche : matches) {
					System.out.println("\nParsing " + matche);
					try {
						parse(matche.toString());
					} catch (final SQLException e) {
						e.printStackTrace();
					}
					// if (j > -1)
					// break;
				}
			}
			System.out.println("... done parsing ");
			System.out.println("Fields ignored: " + FIELDS_TO_IGNORE);
		}
	}

	private static final Set<String> FIELDS_TO_IGNORE = new HashSet<>();

	private void parse(final String bibfile) throws SQLException {
		final Pattern cPattern = Pattern.compile(".*\\\\(.*)\\.bib\\.gz");
		final Matcher cm = cPattern.matcher(bibfile);
		cm.find();
		final String col = cm.group(1);
		final String bibentries = UtilFiles.readFile(bibfile);
		final String bibPattern = "(?s)@(\\w+)\\{(.*?)\\s*,(.*?,)\\s*\\}\\s*";
		final Pattern p = Pattern.compile(bibPattern);
		final String propertyPattern = "(?s)\\s*(\\S+)\\s*=\\s*(\"?)(.+?)\\2\\s*(,\\s*)|\\z";
		final Pattern p2 = Pattern.compile(propertyPattern);

		// True in theory (modulo @String, etc), but don't barf on bad files.
		// Pattern p3 = Pattern.compile("(?:" + propertyPattern + ")+");
		// Pattern p4 = Pattern.compile("(" + bibPattern + ")+");

		final Matcher m = p.matcher(bibentries);
		int start = 0;
		// assert p4.matcher(bibentries).matches();
		while (m.find(start) && !m.hitEnd()) {
			final String entryType = m.group(1);
			if (!entryType.equalsIgnoreCase("String") && !entryType.equalsIgnoreCase("Preamble")) {
				populateHandler.newItem();
				// System.out.println(m.group(1));
				type.insert(entryType, populateHandler);
				final String group2 = m.group(2);
				assert group2 != null;
				id.insert(group2, populateHandler);
				assert col != null;
				collection.insert(col, populateHandler);
				final Matcher m2 = p2.matcher(m.group(3));
				// System.out.println(m.group(3));
				// assert p3.matcher(m.group(3)).matches() : m.group(3);
				while (!m2.hitEnd() && m2.find()) {
					final String fieldName = m2.group(1);
					if (fieldName == null) {
						System.err.println("Syntax error in \n" + m.group(3));
					} else {
						// System.out.println("insert " + fieldName + " " +
						// m2.group(2));
						// System.out.println(m2.start()+"-"+m2.end());
						final Field field = getField(fieldName);
						String value = m2.group(3);
						if (field != null) {
							value = value.replaceAll("\\\\", "");
							value = value.replaceAll("\\{", "");
							value = value.replaceAll("\\}", "");
							value = value.replaceAll("\\s+", " ");
							assert value != null;
							field.insert(value, populateHandler);
						} else if (!FIELDS_TO_IGNORE.contains(fieldName)) {
							System.out.println("Can't find field " + fieldName + "\t\twhile parsing " + group2
									+ ": value = " + value);
							FIELDS_TO_IGNORE.add(fieldName);
						}
					}
				}
			}
			start = m.end(3);
		}
	}

	private final Attribute id = Attribute.getAttribute("ID");
	private final Facet type = Facet.getGenericFacet("Type");
	private final Facet collection = Facet.getGenericFacet("Collection");

	private static final String[] ATTRIBUTES = { "Title", "Note", "URL", // "ISBN",
			"Abstract" };

	private static final String[] GENERIC_FACETS = { "Affiliation", "Booktitle", "Journal", "Language" };

	private static final String[] ANDED_VALUE_FACETS = { "Author", "Publisher", "Editor" };

	private static final String[][] SYNONYMS = { { "Address", "Location" }, { "Country", "Location" }, // {
																										// "ISSN",
																										// "ISBN"
																										// },
			{ "key", "Subject" }, { "keywords", "Subject" }, { "descriptors", "Subject" }, { "keyword", "Subject" },
			{ "kwds", "Subject" }, { "doi", "URL" }, { "Editors", "Editor" }, { "Institution", "Affiliation" },
			{ "school", "Affiliation" } };

	private static final Map<String, Field> FIELD_NAMES = fieldNames();

	private static Map<String, Field> fieldNames() {
		final Map<String, Field> result = new HashMap<>();
		for (final String attribute : ATTRIBUTES) {
			result.put(attribute.toLowerCase(), Attribute.getAttribute(attribute));
		}
		for (final String facet : GENERIC_FACETS) {
			result.put(facet.toLowerCase(), Facet.getGenericFacet(facet));
		}
		for (final String facet : ANDED_VALUE_FACETS) {
			result.put(facet.toLowerCase(), Facet.getANDedValuesFacet(facet));
		}

		result.put("year", Facet.getDateFacet("Year"));
		result.put("location", Facet.getPlaceFacet("Location"));
		result.put("subject", Facet.getSubjectFacet("Subject"));

		Facet.getParsingFacet(BROWSE_URL, null);

		for (final String[] pair : SYNONYMS) {
			final String key = pair[0].toLowerCase();
			final String value = pair[1].toLowerCase();
			result.put(key, result.get(value));
		}
		return result;
	}

	private static Field getField(final @NonNull String fieldName) {
		assert FIELD_NAMES != null;
		assert fieldName != null;
		// System.out.println(fieldName+" "+(fieldName.length()));
		return FIELD_NAMES.get(fieldName.toLowerCase());
	}
}
