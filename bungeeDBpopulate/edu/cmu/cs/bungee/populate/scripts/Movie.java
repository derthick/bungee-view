package edu.cmu.cs.bungee.populate.scripts;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.jdt.annotation.NonNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.populate.Options;
import edu.cmu.cs.bungee.populate.PopulateHandler;

class Movie extends Options { // NO_UCD (unused code)

	public static void main(final String[] args) {
		try {
			final Movie movie = new Movie();
			movie.init(false, args);
			// movie.loadImages();
		} catch (final Throwable ex) {
			ex.printStackTrace(System.out);
		}
	}

	/**
	 */
	@Override
	protected PopulateHandler getPopulateHandler() {
		SaxMovieHandler handler = null;
		try {
			handler = new SaxMovieHandler(getJDBC(), false);
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		return handler;
	}

	public Movie() {
		try {
			final String directory = "C:\\Projects\\ArtMuseum\\InfoVisMovieContest";
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			final SAXParser parser = factory.newSAXParser();
			parser.parse(directory + "\\moviedb.xml", populateHandler);
			((SaxMovieHandler) populateHandler).checkList(directory + "\\mpaa-ratings-reasons-reformatted.list");
		} catch (SAXException | IOException | ParserConfigurationException e) {
			e.printStackTrace();
		}
	}
}

final class SaxMovieHandler extends PopulateHandler {
	private static final Pattern TITLE_PATTERN = Pattern
			.compile("\"?(.*?\"?\\s*\\([0-9/VXI\\?]*\\)(?: \\(.*?\\))?(?: \\{.*\\})?)");

	private int recordNum;
	private int facetID;
	private String subfield = null;
	private StringBuilder data;
	private final PreparedStatement lookupRecordNumFromTitle;
	private final PreparedStatement newItem;
	private final PreparedStatement addTriple;
	private final PreparedStatement lookupFacet;
	private final PreparedStatement addFacet;
	private final PreparedStatement lookupFacetType;
	private final PreparedStatement lookupMonthOrder;
	private final PreparedStatement setMonthOrder;

	SaxMovieHandler(final @NonNull JDBCSample _jdbc, final boolean clearTables) throws SQLException {
		super(_jdbc, true);

		// try {
		if (clearTables) {
			// db.createTablesIfNotExists("hp2");
			clearTables(100_000);
			// parseTGM();
			// parse043codes();
			// checkMultipleParents("places_hierarchy");
			// checkMultipleParents("TGM");
		}

		recordNum = _jdbc.sqlQueryInt("SELECT MAX(record_num) FROM item");
		facetID = Math.max(_jdbc.sqlQueryInt("SELECT MAX(facet_id) FROM raw_facet"),
				_jdbc.sqlQueryInt("SELECT MAX(facet_type_id) FROM raw_facet_type") + 100);
		lookupRecordNumFromTitle = _jdbc.lookupPS("SELECT record_num FROM item WHERE keyTitle = ?");
		newItem = _jdbc.lookupPS("INSERT INTO item VALUES(null, null, ?, ?, ?)");
		addTriple = _jdbc.lookupPS("REPLACE INTO raw_item_facet VALUES(?, ?)");
		lookupFacet = _jdbc.lookupPS("SELECT f.facet_id FROM raw_facet f WHERE f.name = ? AND f.parent_facet_id = ?");
		addFacet = _jdbc.lookupPS("INSERT INTO raw_facet VALUES(?, ?, null, ?, ?)");
		lookupFacetType = _jdbc.lookupPS("SELECT f.facet_type_id FROM raw_facet_type f WHERE f.name = ?");
		lookupMonthOrder = _jdbc
				.lookupPS("SELECT ordered_child_names FROM raw_facet WHERE name = ? AND parent_facet_id = ?");
		setMonthOrder = _jdbc
				.lookupPS("UPDATE raw_facet SET ordered_child_names = ? WHERE name = ? AND parent_facet_id = ?");
		// } catch (final Exception e) {
		// e.printStackTrace();
		// }
	}

	public void checkList(final String filename) {
		final String facetTypeName = "MPAA Rating";
		final String pattern = "MV: " + TITLE_PATTERN + " Rated (\\S*) for (.*)";
		final Pattern listPattern = Pattern.compile(pattern);
		try (BufferedReader r = new BufferedReader(new FileReader(filename));) {
			String line;
			while ((line = r.readLine()) != null) {
				final Matcher m = listPattern.matcher(line);
				if (m.matches()) {
					final String key = m.group(1);
					recordNum = getJDBC().sqlQueryInt(Util.nonNull(lookupRecordNumFromTitle), key);
					if (recordNum >= 0) {
						String value = m.group(2);
						if (facetTypeName.equals("Length")) {
							// int hours = (value != null) ? Integer
							// .parseInt(value) : 0;
							// int minutes = Integer.parseInt(m.group(3));
							// value = Integer.toString(hours * 60 + minutes);
							addLength(value);
						} else if (facetTypeName.equals("Location")) {
							final String[] values = value.split(", ");
							if (locationType < 0) {
								locationType = ensureFacetType("Location");
							}
							int parent = locationType;
							for (int i = values.length - 1; i >= 0; i--) {
								parent = addToRawItemFacet(values[i], locationType, parent);
							}
						} else if (facetTypeName.equals("Subject")) {
							final String[] values = value.split("-");
							for (int i = values.length - 1; i >= 0; i--) {
								final String valuesi = values[i];
								assert valuesi != null;
								values[i] = UtilString.capitalize(valuesi);
							}
							value = UtilString.join(values, " ");
							if (subjectType < 0) {
								subjectType = ensureFacetType("Subject");
							}
							addToRawItemFacet(value, subjectType, subjectType);
						} else if (facetTypeName.equals("MPAA Rating")) {
							if (mpaaType < 0) {
								mpaaType = ensureFacetType("MPAA Rating");
							}
							final int rating = addToRawItemFacet(value, mpaaType, mpaaType);
							final String[] values = m.group(3).split(",|\\.|( and )");
							for (int i = values.length - 1; i >= 0; i--) {
								// reason might be "for mild violence"
								String reason = values[i].trim();
								if (reason.startsWith("for ")) {
									reason = reason.substring(3).trim();
								}
								if (reason.length() > 0) {
									reason = UtilString.capitalize(reason);
									addToRawItemFacet(reason, mpaaType, rating);
								}
							}
						} else {
							assert false : "Unknown type: " + facetTypeName + " " + key + " " + value;
						}
					}
				} else {
					System.out.println("Don't recognize " + line);
				}
			}
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void startElement(@SuppressWarnings("unused") final String uri,
			@SuppressWarnings("unused") final String localName, final String qName, final Attributes attrs) {
		if (qName.equals("movie")) {
			newItem(attrs.getValue("title"));
			final String nr = attrs.getValue("numratings");
			if (nr != null) {
				final int nRatings = Integer.parseInt(nr);
				if (nRatings > 0) {
					final String rating = attrs.getValue("rating");
					addRating(rating);
				}
			}
			final String boxOffice = attrs.getValue("boxoffice");
			if (boxOffice != null) {
				addBoxOffice(boxOffice);
			}
			final String year = attrs.getValue("year");
			if (yearType < 0) {
				yearType = ensureFacetType("Year");
			}
			addToRawItemFacet(year, yearType, -1);
			final String release = attrs.getValue("releasedate");
			if (release != null) {
				addReleaseDate(release);
			}
		} else {
			if (qName.equals("actor")) {
				subfield = attrs.getValue("sex");
			} else if (qName.equals("oscar")) {
				subfield = attrs.getValue("type");
			}
			data = new StringBuilder();
		}
	}

	private void newItem(final String title) {
		try {
			final Matcher m = TITLE_PATTERN.matcher(title);
			if (!m.find()) {
				assert false : "Bad title: " + title;
			}
			newItem.setInt(1, ++recordNum);
			newItem.setString(2, m.group(1));
			newItem.setString(3, title);
			assert newItem != null;
			sqlUpdate(newItem);
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void characters(final char[] ch, final int start, final int length) {
		if (data != null) {
			data.append(ch, start, length);
		}
	}

	@Override
	public void endElement(@SuppressWarnings("unused") final String uri,
			@SuppressWarnings("unused") final String localName, final String qName) {
		String value = null;
		if (data != null && data.length() > 0) {
			value = trim(data.toString());
		}
		if ("director,cinematographer".indexOf(qName) >= 0) {
			assert value != null : "No " + qName + " " + recordNum;
			if (directorType < 0) {
				directorType = ensureFacetType("Director");
			}
			if (cinematographerType < 0) {
				cinematographerType = ensureFacetType("Cinematographer");
			}
			final int type = qName.equals("director") ? directorType : cinematographerType;
			addToRawItemFacet(value, type, -1);
		} else if (qName.equals("genre")) {
			assert value != null : "No " + qName + " " + recordNum;
			if (genreType < 0) {
				genreType = ensureFacetType("Genre");
			}
			addToRawItemFacet(UtilString.capitalize(value), genreType, -1);
		} else if (qName.equals("actor")) {
			assert value != null : "No " + qName + " " + recordNum;
			if (actressType < 0) {
				actressType = ensureFacetType("Actress");
			}
			if (actorType < 0) {
				actorType = ensureFacetType("Actor");
			}
			final int type = subfield.equals("male") ? actorType : actressType;
			addToRawItemFacet(value, type, -1);
		} else if (qName.equals("oscar")) {
			if (oscarType < 0) {
				oscarType = ensureFacetType("Oscar");
			}
			final int parent = addToRawItemFacet(getOscarSubtype(subfield), oscarType, -1);
			if (value != null) {
				addToRawItemFacet(value, oscarType, parent);
			}
		}
		data = null;
	}

	static String trim(final String value) {
		String result = value.replaceAll("\\A\\W*", "");
		result = result.replaceAll("[\\W&&[^\\)]]*\\z", "");
		// System.out.println(value + " => " + result);
		return result;
	}

	private Hashtable<String, String> oscarTypes;

	private String getOscarSubtype(final String name) {
		if (oscarTypes == null) {
			oscarTypes = new Hashtable<>();
			oscarTypes.put("bestpicture", "Best Picture");
			oscarTypes.put("cinematography", "Cinematography");
			oscarTypes.put("directing", "Directing");
			oscarTypes.put("leadingactor", "Leading Actor");
			oscarTypes.put("leadingactress", "Leading Actress");
			oscarTypes.put("supportingactor", "Supporting Actor");
			oscarTypes.put("supportingactress", "Supporting Actress");
		}
		return oscarTypes.get(name);
	}

	private int addToRawItemFacet(final String value, final int type, int parent) {
		if (parent < 0) {
			parent = type;
		}
		final int facet = ensureFacet(value, parent, type);
		try {
			addTriple.setInt(1, recordNum);
			addTriple.setInt(2, facet);
			assert addTriple != null;
			sqlUpdate(addTriple);
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		return facet;
	}

	private int ensureFacet(final String name, final int parent, final int type) {
		// System.out.println("ensureFacet " + name + " " + type + " " +
		// parentName);
		int result = -1;
		try {
			lookupFacet.setString(1, name);
			lookupFacet.setInt(2, parent);
			result = getJDBC().sqlQueryInt(Util.nonNull(lookupFacet));
			if (result < 0) {
				addFacet.setInt(1, ++facetID);
				addFacet.setString(2, name);
				addFacet.setInt(3, parent);
				addFacet.setInt(4, type);
				assert addFacet != null;
				sqlUpdate(addFacet);
				result = facetID;
			}
		} catch (final SQLException e) {
			System.err.println("Warning: " + name + " " + parent + " " + type);
			e.printStackTrace();
		}
		return result;
	}

	private int ensureFacetType(final String name) {
		int result = -1;
		try {
			result = getJDBC().sqlQueryInt(Util.nonNull(lookupFacetType), name);
			if (result < 0) {
				sqlUpdate("INSERT INTO raw_facet_type VALUES(null, " + JDBCSample.quoteForSQL(name)
						+ ", null, null, null, null)");
				result = ensureFacetType(name);
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	private static String formatIntStringToWidth(final String intString, final int nChars) {
		return String.format("%," + nChars + "d", Integer.parseInt(intString));
	}

	// cached facet type IDs
	private int boxOfficeType = -1;
	private int lengthType = -1;
	private int ratingType = -1;
	private int releaseType = -1;
	private int oscarType = -1;
	private int yearType = -1;
	private int locationType = -1;
	private int subjectType = -1;
	private int mpaaType = -1;
	private int actressType = -1;
	private int genreType = -1;
	private int actorType = -1;
	private int directorType = -1;
	private int cinematographerType = -1;

	private void addRating(final String rating) {
		// System.out.println(rating);
		// System.out.println(rating.split("\\.").length);
		String parentName = rating.split("\\.")[0];
		while (parentName.length() < 2) {
			parentName = " " + parentName;
		}
		parentName += ".x";
		if (ratingType < 0) {
			ratingType = ensureFacetType("Average Rating");
		}
		final int parent = addToRawItemFacet(parentName, ratingType, -1);
		addToRawItemFacet(rating, ratingType, parent);
	}

	private void addBoxOffice(final String boxOffice) {
		if (boxOffice.length() > 1) {
			final StringBuilder parentNameBuf = new StringBuilder(boxOffice.substring(0, 1));
			while (parentNameBuf.length() < boxOffice.length()) {
				parentNameBuf.append("0");
			}
			String parentName = formatIntStringToWidth(parentNameBuf.toString(), 11);
			parentName = parentName.replace('0', 'x');
			if (boxOfficeType < 0) {
				boxOfficeType = ensureFacetType("Box Office");
			}
			final int parent = ensureFacet(parentName, boxOfficeType, boxOfficeType);
			addToRawItemFacet(formatIntStringToWidth(boxOffice, 11), boxOfficeType, parent);
		}
	}

	private void addLength(String minutes) {
		if (minutes.length() > 1) {
			final StringBuilder parentNameBuf = new StringBuilder(minutes.substring(0, 1));
			while (parentNameBuf.length() < minutes.length()) {
				parentNameBuf.append("0");
			}
			String parentName = formatIntStringToWidth(parentNameBuf.toString(), 3);
			parentName = parentName.replace('0', 'x');
			minutes = formatIntStringToWidth(minutes, 3);
			parentName = parentName.replace('0', 'x');
			if (lengthType < 0) {
				lengthType = ensureFacetType("Length");
			}
			final int parent = ensureFacet(parentName, lengthType, lengthType);
			addToRawItemFacet(minutes, lengthType, parent);
		}
	}

	private final Pattern releaseDatePattern = Pattern.compile("(\\d{1,2}) (\\w{3}) (\\d{4})");

	private void addReleaseDate(final String release) {
		final Matcher m = releaseDatePattern.matcher(release);
		if (!m.matches()) {
			assert false : "Bad release: " + release;
		} else {
			final String year = m.group(3);
			final String month = month(m.group(2)) + " " + year;
			if (releaseType < 0) {
				releaseType = ensureFacetType("Release Date");
			}
			final int yearID = addToRawItemFacet(year, releaseType, -1);
			final int monthID = addToRawItemFacet(month, releaseType, yearID);
			addToRawItemFacet(release, releaseType, monthID);
			try {
				lookupMonthOrder.setString(1, year);
				lookupMonthOrder.setInt(2, releaseType);
				if (getJDBC().sqlQueryString(Util.nonNull(lookupMonthOrder), true) == null) {
					setMonthOrder.setString(1, "Jan ?,Feb ?,Mar ?,Apr ?,May ?,Jun ?,Jul ?,Aug ?,Sep ?,Oct ?,Nov ?,Dec ?"
							.replaceAll("\\?", year));
					setMonthOrder.setString(2, year);
					setMonthOrder.setInt(3, releaseType);
					assert setMonthOrder != null;
					sqlUpdate(setMonthOrder);
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private Hashtable<String, String> months;

	private String month(final String abbr) {
		if (months == null) {
			months = new Hashtable<>();
			final String[] names = "January,February,March,April,May,June,July,August,September,October,November,December"
					.split(",");
			for (final String name : names) {
				final String abb = name.substring(0, 3);
				months.put(abb, name);
			}
		}
		final String result = months.get(abbr);
		assert result != null;
		return result;
	}
}