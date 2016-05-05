package edu.cmu.cs.bungee.populate.scripts;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.sparql.SPARQLRepository;

import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.populate.Facet;
import edu.cmu.cs.bungee.populate.Options;

/**
 * Run it like this:
 *
 * -renames Rename.txt -moves MyTGM.txt,places_hierarchy.txt,Moves.txt
 *
 * -db saam
 *
 * Test SPARQL queries at http://dbpedia.org/sparql
 *
 * SPARQL documentation at http://www.w3.org/TR/sparql11-query/
 */
public class SAAM extends Options { // NO_UCD (unused code)

	static final String BASE_URI = "http://edan.si.edu/saam/sparql";

	private static final String PREFIXES = "PREFIX cidoc: <http://www.cidoc-crm.org/cidoc-crm/> \n"
			+ "PREFIX   xsd: <http://www.w3.org/2001/XMLSchema#> \n"
			+ "PREFIX  skos: <http://www.w3.org/2008/05/skos#> \n"
			+ "PREFIX  saam: <http://edan.si.edu/saam/id/ontologies/> \n"
			+ "PREFIX    w3: <http://www.w3.org/2000/01/rdf-schema#> \n\n";

	static final String[] QUERIES = {

			"SELECT DISTINCT ?ArtistURL ?ArtistName \n"

			+ "WHERE  {  \n"

			+ "		   ?ArtistURL cidoc:P1_is_identified_by ?x . \n"

			+ "		     OPTIONAL { ?x saam:PE_has_type_name ?NameType } . \n"

			+ "		     ?x w3:label ?Artist  \n"

			+ "		    FILTER (!strends(LCASE(?Artist), \".jpg\")) \n"

			+ "		   BIND(CONCAT(STR(?Artist), \";NAMETYPE=\", IF(bound(?NameType), STR(?NameType), \"\")) AS ?ArtistName) \n"

			+ "			}",

	};

	// static {
	// System.out.println(UtilString.valueOfDeep(PREFIXES));
	// System.out.println(UtilString.valueOfDeep(QUERIES));
	// }

	static final String[] ATTRIBUTES = {

			"lodURL↪?lodURL cidoc:P102_has_title $Title↩/cidoc:P1_is_identified_by/w3:label=ID",

			"lodURL/^cidoc:P30_transferred_custody_of/cidoc:P3_has_note=Acquisition",

			"lodURL/(saam:PE_has_note_gallerylabel|saam:PE_has_note_newacquisitionlabel)=GalleryLabel",

			"lodURL/cidoc:P43_has_dimension=w"
					+ "↪?w cidoc:P2_has_type <http://edan.si.edu/saam/id/thesauri/dimension/width> . \n"
					+ " ?w cidoc:P91_has_unit <http://qudt.org/vocab/unit#Centimeter>↩/cidoc:P90_has_value=Width",

			"lodURL/cidoc:P43_has_dimension=h"
					+ "↪?h cidoc:P2_has_type <http://edan.si.edu/saam/id/thesauri/dimension/height> . \n"
					+ " ?h cidoc:P91_has_unit <http://qudt.org/vocab/unit#Centimeter>↩/cidoc:P90_has_value=Height",

			"lodURL/cidoc:P43_has_dimension=d"
					+ "↪?d cidoc:P2_has_type <http://edan.si.edu/saam/id/thesauri/dimension/depth> . \n"
					+ " ?d cidoc:P91_has_unit <http://qudt.org/vocab/unit#Centimeter>↩/cidoc:P90_has_value=Depth",

			"lodURL/cidoc:P43_has_dimension=diam"
					+ "↪?diam cidoc:P2_has_type <http://edan.si.edu/saam/id/thesauri/dimension/diam> . \n"
					+ " ?diam cidoc:P91_has_unit <http://qudt.org/vocab/unit#Centimeter>↩/cidoc:P90_has_value=Diameter",

			"lodURL/cidoc:P55_has_current_location/w3:label=Location",
			"lodURL/cidoc:P55_has_current_location/cidoc:P3_has_note=LocationNote",
			"lodURL/cidoc:P102_has_title/w3:label=Title",
			"lodURL/^cidoc:P108_has_produced/cidoc:P4_has_time-span/(w3:label|cidoc:P82_at_some_time_within)=Date",

			"lodURL/^cidoc:P108_has_produced/cidoc:P14_carried_out_by=ArtistURL",

			"ArtistURL/cidoc:P98i_was_born/cidoc:P7_took_place_at=ArtistBirthplace",
			"ArtistURL/cidoc:P98i_was_born/cidoc:P4_has_time-span/(w3:label|cidoc:P82_at_some_time_within)=BirthDate",
			"ArtistURL/cidoc:P100i_died_in/cidoc:P4_has_time-span/(w3:label|cidoc:P82_at_some_time_within)=DeathDate",

			"lodURL/cidoc:P128_carries/cidoc:P129_is_about=subj"
					+ "↪FILTER NOT EXISTS { ?subj skos:topConceptOf <http://edan.si.edu/saam/id/thesauri/term/Mark_Type> }↩"
					+ "/skos:prefLabel=Subject",

			"lodURL↪?lodURL cidoc:P102_has_title $Title↩/cidoc:P138i_has_representation=ImageURL",

			"lodURL/saam:PE_object_mainclass/skos:prefLabel=MainClass",

			"lodURL/saam:PE_object_subsclass=SubClass", "lodURL/saam:PE_medium_description=MediumDescription",

	};

	public static void main(final String[] args) {
		try {
			new SAAM().init(false, args);
		} catch (final Throwable e) {
			e.printStackTrace(System.out);
		}
	}

	// @Override
	// protected void init(final boolean isParser, final String[] args) throws
	// Throwable {
	// addOptions(isParser);
	// if (args != null && sm_main.parseArgs(args)) {
	// try (JDBCSample _jdbc = getJdbc();) {
	// jdbc = _jdbc;
	// populateHandler = getPopulateHandler();
	// final String movesFile = sm_main.getStringValue("moves");
	// if (movesFile != null) {
	// final String directory = sm_main.getStringValue("directory");
	// populateHandler.setMoves(parsePairFiles(directory, movesFile));
	// }
	// // final Facet artistBirthplace = Facet.getPlaceFacet("Artist
	// // Birthplace");
	// // insertArtistBirthplace(artistBirthplace,
	// //
	// "http://edan.si.edu/saam/id/thesauri/place/Tashkent_none_none_Uzbekistan");
	//
	// final Facet facet = Facet.getDateFacet("Date");
	// populateRawFacetInternal(facet, "early 1920s");
	// }
	// }
	// }

	@Override
	protected void readData() throws SQLException {
		jdbc.sqlUpdate("SET SESSION group_concat_max_len = 12000");
		// populateObjectTables();
		populateItem();
		populateRawFacet();
	}

	final List<String> mediumColumns = UtilArray.getUnmodifiableList("MainClass", "SubClass", "MediumDescription");

	private void populateRawFacet() throws SQLException {
		UtilString.printDateNmessage("Enter SAAM.populateRawFacet");
		jdbc.sqlUpdate("TRUNCATE TABLE raw_facet");
		jdbc.sqlUpdate("TRUNCATE TABLE raw_item_facet");

		final Facet date = Facet.getDateFacet("Date");
		final Facet artistBirthplace = Facet.getPlaceFacet("Artist Birthplace");
		final Facet artist = Facet.getGenericFacet("Artist");
		final Facet location = Facet.getPreparsedFacet("Current Location");
		final Facet acquired = Facet.getGenericFacet("Acquired");
		final Facet size = Facet.getRoundToSigFigsFacet("Size (cm²)");
		final Facet medium = Facet.getPreparsedFacet("Medium");
		final Facet subject = Facet.getPreparsedFacet("Keyword");

		try (final ResultSet rs = jdbc.sqlQuery("SELECT ID AS record_num, ArtistURL,"

		+ "         GROUP_CONCAT(DISTINCT Width             SEPARATOR '|') AS Width,"
				+ " GROUP_CONCAT(DISTINCT Height            SEPARATOR '|') AS Height,"
				+ " GROUP_CONCAT(DISTINCT Depth             SEPARATOR '|') AS Depth,"
				+ " GROUP_CONCAT(DISTINCT Diameter          SEPARATOR '|') AS Diameter,"
				+ " GROUP_CONCAT(DISTINCT Date              SEPARATOR '|') AS Date,"
				+ " GROUP_CONCAT(DISTINCT Acquisition       SEPARATOR '|') AS Acquisition,"
				+ " GROUP_CONCAT(DISTINCT Location          SEPARATOR '|') AS Location,"
				+ " GROUP_CONCAT(DISTINCT LocationNote      SEPARATOR '|') AS LocationNote,"
				+ " GROUP_CONCAT(DISTINCT MainClass         SEPARATOR '|') AS MainClass,"
				+ " GROUP_CONCAT(DISTINCT SubClass          SEPARATOR '|') AS SubClass,"
				+ " GROUP_CONCAT(DISTINCT MediumDescription SEPARATOR '|') AS MediumDescription,"
				+ " GROUP_CONCAT(DISTINCT Subject           SEPARATOR '|') AS Subject,"
				+ " GROUP_CONCAT(DISTINCT ArtistBirthplace  SEPARATOR '|') AS ArtistBirthplace,"
				+ " GROUP_CONCAT(DISTINCT Birthdate         SEPARATOR '|') AS Birthdate,"
				+ " GROUP_CONCAT(DISTINCT Deathdate         SEPARATOR '|') AS Deathdate,"
				+ " GROUP_CONCAT(DISTINCT ArtistName        SEPARATOR '|') AS ArtistName "

		+ "FROM lodID " + "LEFT JOIN lodDate              USING (lodURL) "
				+ "LEFT JOIN lodSubject           USING (lodURL) " + "LEFT JOIN lodAcquisition       USING (lodURL) "
				+ "LEFT JOIN lodLocation          USING (lodURL) " + "LEFT JOIN lodLocationNote      USING (lodURL) "
				+ "LEFT JOIN lodWidth             USING (lodURL) " + "LEFT JOIN lodHeight            USING (lodURL) "
				+ "LEFT JOIN lodDepth             USING (lodURL) " + "LEFT JOIN lodDiameter          USING (lodURL) "
				+ "LEFT JOIN lodMainClass         USING (lodURL) " + "LEFT JOIN lodSubClass          USING (lodURL) "
				+ "LEFT JOIN lodMediumDescription USING (lodURL) " + "LEFT JOIN lodArtistURL         USING (lodURL) "
				+ "LEFT JOIN lodArtistBirthplace  USING (ArtistURL) "
				+ "LEFT JOIN lodBirthdate         USING (ArtistURL) "
				+ "LEFT JOIN lodDeathdate         USING (ArtistURL) "
				+ "LEFT JOIN lodArtistName        USING (ArtistURL) "

		+ "GROUP BY record_num, ArtistURL");) {
			UtilString.printDateNmessage(
					"SAAM.populateRawFacet start processing ResultSet with " + MyResultSet.nRows(rs) + " rows.");
			while (rs.next()) {
				populateHandler.setRecordNum(rs.getInt("record_num"));

				populateRawFacetInternal(date, rs.getString("Date"));
				populateRawFacetInternal(acquired, rs.getString("Acquisition"));

				// NOTE SubClass may have cidoc:P127_has_broader_term
				insertConcatenatedValues(medium, "", mediumColumns, rs);

				insertArtist(artist, rs);
				insertSize(size, rs);
				insertArtistBirthplace(artistBirthplace, rs.getString("ArtistBirthplace"));
				insertSubject(subject, rs);
				insertLocation(location, rs);
			}
		}
	}

	private static String[] values(final String colName, final ResultSet rs) throws SQLException {
		return values(rs.getString(colName));
	}

	private static String[] values(final String concatenatedValues) {
		String[] result;
		if (concatenatedValues != null) {
			result = concatenatedValues.split("\\|");
		} else {
			result = new String[0];
		}
		return result;
	}

	private void insertArtistBirthplace(final Facet facet, final String concatenatedValues) throws SQLException {
		final String[] values = values(concatenatedValues);
		for (final String url : values) {
			assert url != null;
			String value = UtilString.getLastSplit(url, "/");
			value = value.replace("_none", "");
			value = value.replace("-", " ");
			final String hierValue = UtilString.join(value.split("_"));
			assert hierValue != null;

			// System.out.println("SAAM.insertArtistBirthplace " + facet + "
			// concatenatedValues=" + concatenatedValues
			// + " hierValue=" + hierValue.toString());
			populateRawFacetInternal(facet, hierValue);
		}
	}

	private void insertLocation(final Facet facet, final ResultSet rs) throws SQLException {
		final String[] values = values("Location", rs);
		// not always true
		// assert values.length <= 1 : UtilString.valueOfDeep(values);

		final String[] notes = values("LocationNote", rs);
		if (notes.length == values.length) {
			insertLocationFromNotes(facet, notes);
		} else {
			insertLocationFromValues(facet, values);
		}
	}

	private static final Pattern NOTES_PATTERN = Pattern.compile(",\\s*");

	private void insertLocationFromNotes(final Facet facet, final String[] values) throws SQLException {
		for (final String value : values) {

			final String processedValue = isDisplayedText(true)
					+ NOTES_PATTERN.matcher(value).replaceAll(Compile.FACET_HIERARCHY_SEPARATOR);

			// final String processedValue = isDisplayedText(true)
			// + value.replaceAll(",\\s*", Compile.FACET_HIERARCHY_SEPARATOR);
			// System.out.println("SAAM.insertLocation " + processedValue);
			populateRawFacetInternal(facet, processedValue);
		}
	}

	private static final Pattern isDisplayedPattern = Pattern.compile("AA\\d@");

	private static final Pattern locationPattern = Pattern.compile("([^:]+):([^;]+)(?:;(.+))?");

	public void insertLocationFromValues(final Facet facet, final String[] values) throws SQLException {
		for (final String value : values) {
			final Matcher m = locationPattern.matcher(value);
			final boolean found = m.find();
			assert found : value;
			final String isDisplayedText = isDisplayedText(isDisplayedPattern.matcher(value).find());
			String processedValue = isDisplayedText + m.group(1) + Compile.FACET_HIERARCHY_SEPARATOR + m.group(2)
					+ Compile.FACET_HIERARCHY_SEPARATOR + m.group(3);
			processedValue = processedValue.replace("/", Compile.FACET_HIERARCHY_SEPARATOR);
			// System.out.println("SAAM.insertLocation " + processedValue);
			populateRawFacetInternal(facet, processedValue);
		}
	}

	public static String isDisplayedText(final boolean isDisplayed) {
		return (isDisplayed ? "Currently on view" : "Not currently on view") + Compile.FACET_HIERARCHY_SEPARATOR;
	}

	private static final List<String> hyphenatedSubjects = UtilArray.getUnmodifiableList("self-portrait");
	private static final List<List<String>> hyphenatedSubjectReplacements = computeHyphenatedSubjects();

	private static List<List<String>> computeHyphenatedSubjects() {
		final List<List<String>> result = new ArrayList<>(hyphenatedSubjects.size());
		for (final String hyphenated : hyphenatedSubjects) {
			result.add(UtilArray.getUnmodifiableList(hyphenated.replace("-", Compile.FACET_HIERARCHY_SEPARATOR),
					hyphenated));
		}
		return result;
	}

	private void insertSubject(final Facet facet, final ResultSet rs) throws SQLException {
		final String[] values = values("Subject", rs);
		for (final String value : values) {
			String processedValue = value.replace("-", Compile.FACET_HIERARCHY_SEPARATOR);
			for (final List<String> hyphenatedSubject : hyphenatedSubjectReplacements) {
				processedValue = processedValue.replace(hyphenatedSubject.get(0), hyphenatedSubject.get(1));
			}
			populateRawFacetInternal(facet, processedValue);
		}
	}

	private final List<String> preferredNameTypes = UtilArray.getUnmodifiableList("preferred-w-o-accents", "later-name",
			"nea-application-name", "conferred-name", "married-name", "birth-or-maiden-name", "former-name",
			"pseudonym", "variant-name", /* In case no NAMETYPE was found */ "nametype=");

	private void insertArtist(final Facet facet, final ResultSet rs) throws SQLException {
		final String[] names = values("ArtistName", rs);
		String bestName = null;
		int bestNameRank = Integer.MAX_VALUE;
		for (final String name : names) {
			final String[] components = name.trim().split(";");
			assert components.length == 2 : name + " " + rs.getString("lodURL");
			final String components1 = components[1];
			assert components1.startsWith("NAMETYPE=") : rs.getString("lodURL");
			final String nameType = UtilString.getLastSplit(components1, "/").toLowerCase();
			final int rank = preferredNameTypes.indexOf(nameType);
			assert rank >= 0 : "name='" + name + "' nameType='" + nameType + "' components[1]='" + components1
					+ "' record_num=" + rs.getString("record_num");
			if (rank < bestNameRank) {
				bestName = components[0];
				bestNameRank = rank;
			}
		}
		if (bestName != null) {
			final String[] nameParts = bestName.split("\\s+");
			final String lastFirst = nameParts[nameParts.length - 1] + ", "
					+ UtilString.join(ArrayUtils.subarray(nameParts, 0, nameParts.length - 1), " ");
			String artistDates = artistDates(rs);
			if (artistDates == null) {
				artistDates = "";
			}
			populateRawFacetInternal(facet, lastFirst + artistDates);
		}
	}

	private static String artistDates(final ResultSet rs) throws SQLException {
		String result = null;
		String birth = artistDate("Birthdate", rs);
		String death = artistDate("Deathdate", rs);
		if (birth != null || death != null) {
			if (birth == null) {
				birth = "";
			}
			if (death == null) {
				death = "";
			}
			result = " (" + birth + " - " + death + ")";
		}
		return result;
	}

	private static final Pattern yearPattern = Pattern.compile("(\\d{4})");

	private static String artistDate(final String colName, final ResultSet rs) throws SQLException {
		String result = null;
		final String[] values = values(colName, rs);
		if (values.length > 1) {
			System.err.println("Multiple " + colName + "s: " + UtilString.valueOfDeep(values) + ". Using " + values[0]);
		}
		if (values.length > 0) {
			final Matcher m = yearPattern.matcher(values[0]);
			final boolean isMatch = m.find();
			if (isMatch) {
				result = m.group(1);
			} else {
				System.err.println("Warning: No year found in " + values[0]);
			}
		}
		return result;
	}

	private void insertSize(final Facet size, final ResultSet rs) throws SQLException {
		final double[] dimensions = getSize(rs);
		if (dimensions != null) {
			final String value = Integer.toString(UtilMath.roundToInt(dimensions[0] * dimensions[1]));
			populateRawFacetInternal(size, value);
		}
	}

	private static String getDimensions(final ResultSet rs) throws SQLException {
		String result = null;
		final double[] dimensions = getSize(rs);
		if (dimensions != null) {
			result = UtilMath.roundToInt(dimensions[1]) + "cm × " + UtilMath.roundToInt(dimensions[0]) + "cm";
			if (!Double.isNaN(dimensions[2])) {
				result += " × " + UtilMath.roundToInt(dimensions[2]) + "cm";
			}
		}
		return result;
	}

	private static double[] getSize(final ResultSet rs) throws SQLException {
		double[] result = null;
		double width = getDimension(rs, "Width");
		if (Double.isNaN(width)) {
			width = getDimension(rs, "Diameter");
		}
		final double height = getDimension(rs, "Height");
		final double depth = getDimension(rs, "Depth");
		if (Double.isNaN(width) != Double.isNaN(height)) {
			// System.err.println("Warning: Inconsistent dimensions for "
			// + rs.getString("lodURL") + ": " + width + "×" + height);
		} else if (!Double.isNaN(width)) {
			final double[] result1 = { width, height, depth };
			result = result1;
		}
		return result;
	}

	private static double getDimension(final ResultSet rs, final String type) throws SQLException {
		final String[] values = values(type, rs);
		// if (values.length > 1) {
		// System.err.println("Warning: Non-Unique dimension for " +
		// rs.getString("record_num") + "." + type + ": "
		// + rs.getString(type) + ". Using " + values[0]);
		// }
		final double result = values.length > 0 ? Double.parseDouble(values[0]) : Double.NaN;
		return result;
	}

	private void insertConcatenatedValues(final Facet facet, String prevValues, final List<String> colNames,
			final ResultSet rs) throws SQLException {
		if (colNames.isEmpty()) {
			if (prevValues.length() > 0) {
				populateRawFacetInternal(facet, prevValues);
			}
		} else {
			final String colName = colNames.get(0);
			final String[] values = values(colName, rs);
			final List<String> restColNames = colNames.subList(1, colNames.size());
			if (values.length == 0) {
				insertConcatenatedValues(facet, prevValues, restColNames, rs);
			} else {
				if (prevValues.length() > 0) {
					prevValues += Compile.FACET_HIERARCHY_SEPARATOR;
				}
				for (String value : values) {
					assert value != null;
					if (colName.equals("SubClass")) {
						value = UtilString.getLastSplit(value, "/");
					}
					insertConcatenatedValues(facet, prevValues + value, restColNames, rs);
				}
			}
		}
	}

	@SuppressWarnings("null")
	private void populateRawFacetInternal(final Facet facet, final String concatenatedValues) throws SQLException {
		final String[] values = values(concatenatedValues);
		for (final @NonNull String value : values) {
			final String trimValue = value.trim();
			assert!trimValue.endsWith("null") : trimValue + "\n" + concatenatedValues;
			facet.insert(trimValue, populateHandler);
		}
	}

	private void populateItem() throws SQLException {
		UtilString.printDateNmessage("Enter SAAM.populateItem");
		jdbc.sqlUpdate("TRUNCATE TABLE item");
		// jdbc.sqlUpdate("TRUNCATE TABLE images");
		@SuppressWarnings("resource")
		final PreparedStatement ps = jdbc.lookupPS("INSERT INTO item VALUES (?, NULL, ?, ?, ?, ?, DEFAULT, ?)");
		try (ResultSet rs = jdbc.sqlQuery(
				"SELECT ID AS record_num, lodURL, MIN(ImageURL) AS ImageURL, Title, MIN(GalleryLabel) AS GalleryLabel,"
						+ " MIN(Width) AS Width, MIN(Height) AS Height, MIN(Depth) AS Depth, MIN(Diameter) AS Diameter "
						+ "FROM lodID " + "LEFT JOIN lodImageURL USING (lodURL) "
						+ "LEFT JOIN lodTitle    USING (lodURL) " + "LEFT JOIN lodGalleryLabel    USING (lodURL) "
						+ "LEFT JOIN lodWidth    USING (lodURL) " + "LEFT JOIN lodHeight   USING (lodURL) "
						+ "LEFT JOIN lodDepth   USING (lodURL) " + "LEFT JOIN lodDiameter   USING (lodURL) "
						+ "GROUP BY record_num " + "ORDER BY record_num;");) {
			UtilString.printDateNmessage(
					"SAAM.populateItem start processing ResultSet with " + MyResultSet.nRows(rs) + " rows.");
			while (rs.next()) {
				ps.setInt(1, rs.getInt("record_num"));
				ps.setString(2, rs.getString("ImageURL"));
				ps.setString(3, rs.getString("Title"));
				ps.setString(4, rs.getString("GalleryLabel"));
				ps.setString(5, getDimensions(rs));
				ps.setString(6, rs.getString("lodURL"));
				jdbc.sqlUpdateWithIntArgs(ps);
			}
		}
	}

	void populateObjectTables() throws SQLException {
		UtilString.printDateNmessage("Enter SAAM.populateObjectTables");
		final SPARQLRepository sPARQLRepository = new SPARQLRepository(BASE_URI);
		sPARQLRepository.initialize();
		try (final RepositoryConnection connection = sPARQLRepository.getConnection();) {
			populateObjectTablesInternal(connection);
		}
	}

	final Pattern queryPattern = Pattern.compile("SELECT DISTINCT \\?(\\w+)\\s+\\?(\\w+)");

	private void populateObjectTablesInternal(final RepositoryConnection connection) throws SQLException {
		for (final String attribute : ATTRIBUTES) {
			assert attribute != null;
			final String queryString = computeAttributeQuery(attribute);
			final String colName = colName(attribute);
			populateObjectTableInternal("lod" + colName, colName, keyName(attribute), connection, queryString);
		}
		for (final String query : QUERIES) {
			final Matcher m = queryPattern.matcher(query);
			final boolean found = m.find();
			assert found : query;
			final String keyName = m.group(1);
			final String colName = m.group(2);

			final String queryString = PREFIXES + query;
			populateObjectTableInternal("lod" + colName, colName, keyName, connection, queryString);
		}
	}

	private static String colName(final @NonNull String attribute) {
		final String varDesc = UtilString.getLastSplit(attribute, "=");
		return varDesc.split("↪")[0];
	}

	private static String keyName(final String attribute) {
		return attribute.split("↪|/")[0];
	}

	static final Pattern varPattern = Pattern.compile("(\\w+)(?::.+)?(?:↪([^↩]*)↩)?");

	private static String computeAttributeQuery(final String pathExpr) {
		final StringBuilder whereBuf = new StringBuilder();
		final StringBuilder queryBuf = new StringBuilder();
		queryBuf.append(PREFIXES + "SELECT DISTINCT ");

		whereBuf.append("WHERE { \n");

		final String[] varSeparateds = (pathExpr).split("=");
		final List<String> filters = new LinkedList<>();
		String firstVar = null;
		String recentVar = null;
		for (final String varSeparated : varSeparateds) {
			final Matcher m = varPattern.matcher(varSeparated);
			final boolean found = m.find();
			assert found : varSeparated;
			final String filter = m.group(2);
			if (filter != null) {
				filters.add(filter);
			}
			recentVar = "?" + m.group(1);
			if (firstVar == null) {
				firstVar = recentVar;
				queryBuf.append(recentVar).append(" ");
			}
			whereBuf.append(" ").append(recentVar).append(" ");
			final int matchLength = m.group().length();
			if (varSeparated.length() > matchLength) {
				assert varSeparated.charAt(matchLength) == '/' : recentVar + " " + varSeparated;
				if (!recentVar.equals(firstVar)) {
					whereBuf.append(". ").append(recentVar).append(" ");
				}
				whereBuf.append(varSeparated.substring(matchLength + 1));
			}
		}
		assert recentVar != null : pathExpr;
		queryBuf.append(recentVar).append(" ");
		for (final String filter : filters) {
			whereBuf.append(". \n ").append(filter);
		}
		// queryBuf.append(whereBuf).append("} LIMIT 1000 \n");
		queryBuf.append(whereBuf).append("} \n");
		return queryBuf.toString();
	}

	void populateObjectTableInternal(final String tableName, final String colName, final String keyName,
			final RepositoryConnection connection, final String queryString) throws SQLException {
		System.out.println(queryString);
		System.out.println();
		createObjectTable(keyName, colName, tableName);
		final TupleQuery query = connection.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
		try (final TupleQueryResult tuples = query.evaluate();) {
			final List<String> bindingNames = tuples.getBindingNames();
			@SuppressWarnings("resource")
			final java.sql.PreparedStatement insertPS = jdbc.lookupPS("INSERT INTO " + tableName + " VALUES(?, ?)");
			while (tuples.hasNext()) {
				try {
					final BindingSet tuple = tuples.next();
					for (int column = 1; column <= 2; column++) {
						final String variable = bindingNames.get(column - 1);
						final Value value = tuple.getValue(variable);
						final String s = value == null ? null : value.stringValue();
						insertPS.setString(column, s);
					}
					jdbc.sqlUpdateWithIntArgs(insertPS);
				} catch (final Throwable e) {
					System.err.println("While SAAM.populateObjectTableInternal " + insertPS + ":\n" + e);
				}
			}
		}
	}

	private void createObjectTable(final String keyName, final String colName, final String tableName)
			throws SQLException {
		final String colType = colName.equals("ID") ? " MEDIUMINT(6)" : " TEXT";
		final StringBuilder buf = new StringBuilder();
		buf.append("(").append(keyName).append(" VARCHAR(255) NOT NULL, ").append(colName).append(colType);
		buf.append(",  KEY ").append(keyName).append(" (").append(keyName).append(")");
		buf.append(") ENGINE=MyISAM");
		jdbc.dropAndCreateTable(tableName, buf.toString());
	}

	// static final int MAX_COLUMN_WIDTH = 20;
	//
	// static String formatTuples(final TupleQueryResult tuples) {
	// final FormattedTableBuilder align = new FormattedTableBuilder();
	// final List<String> columnNames = tuples.getBindingNames();
	// final List<Object> columns = new ArrayList<>(columnNames.size());
	// for (final String colName : columnNames) {
	// columns.add(colName);
	// }
	// align.addLine(columns);
	// align.addLine();
	// while (tuples.hasNext()) {
	// columns.clear();
	// final BindingSet tuple = tuples.next();
	// final Iterator<Binding> iterator = tuple.iterator();
	// int column = 0;
	// while (iterator.hasNext()) {
	// final Binding binding = iterator.next();
	// while (!columnNames.get(column++).equals(binding.getName())) {
	// columns.add("<null>");
	// }
	// String value = binding.getValue().stringValue();
	// final int excessW = value.length() - MAX_COLUMN_WIDTH;
	// if (excessW > 0) {
	// value = value.substring(excessW);
	// }
	// columns.add(value);
	// }
	// while (column++ < columnNames.size()) {
	// columns.add("<null>");
	// }
	// align.addLine(columns);
	// }
	// return align.format();
	// }

}