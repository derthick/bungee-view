package edu.cmu.cs.bungee.populate.scripts;

import java.sql.SQLException;
import java.util.Hashtable;

import org.xml.sax.Attributes;

import edu.cmu.cs.bungee.populate.DC;
import edu.cmu.cs.bungee.populate.Facet;
import edu.cmu.cs.bungee.populate.Field;
import edu.cmu.cs.bungee.populate.GenericFacetValueParser;
import edu.cmu.cs.bungee.populate.MARC;
import edu.cmu.cs.bungee.populate.Options;

/**
 * Here is an example of parsing of XML data with help of document-handler.
 *
 * Warning: You have to update hard-coded db name in Database.createTables with
 * a db with appriopriate raw_facet_type, e.g. using dc or marc21.
 *
 *
 * Args for HP: -db hp4 -user root -pass tartan01 -reset -renumber -verbose
 * -directory
 * "C:\Users\mad\IDriveSync\documents\Work\Projects\ArtMuseum\Populate Data
 * Files" -files
 * "C:\Users\mad\IDriveSync\documents\Work\Projects\ArtMuseum\HistoricPittsburgh
 * \OAI-2010 - O c t \ hp-*.xml" -cities Places.txt -renames Rename.txt -moves
 * TGM.txt,places_hierarchy.txt,Moves.txt -image_url_getter item.URI
 * -image_regexp src=\"(/cgi-bin.*?)\"
 *
 * Args for LoC2: VM argument: -DentityExpansionLimit=200000 -db loc2 -user root
 * -pass tartan01 -reset -verbose -directory C:\Projects\ArtMuseum\LoC\2008\
 * -files loc-*.xml -cities Places.txt -renames Rename.txt -moves
 * TGM.txt,places_hierarchy.txt,Moves.txt -toMerge Collection -image_url_getter
 * item.URI
 *
 * Args for CiteSeer -db CiteSeer2 -user root -pass tartan01 -reset -renumber
 * -verbose -directory
 * "C:\Users\mad\IDriveSync\documents\Work\Projects\ArtMuseum\Populate Data
 * Files" -files
 * "C:\Users\mad\IDriveSync\documents\Work\Projects\ArtMuseum\CiteSeer\citeseerx
 * *.xml" -cities Places.txt -renames Rename.txt -moves
 * MyTGM.txt,places_hierarchy.txt,Moves.txt
 *
 * Args for InternetArchive -db InternetArchive -user root -pass tartan01 -reset
 * -renumber -verbose -directory
 * "C:\Users\mad\IDriveSync\documents\Work\Projects\ArtMuseum\Populate Data
 * Files" -files
 * "C:\Users\mad\IDriveSync\documents\Work\Projects\ArtMuseum\InternetArchive\
 * images-mediatype-I m a g e . x m l " -cities Places.txt -renames Rename.txt
 * -moves TGM.txt,places_hierarchy.txt,Moves.txt -dont_read_data
 * -image_url_getter item.URI -image_regexp
 * (?i)a\s+href=\\\"(/download/[^\\\"]*?\.(?:jpe?g|gif|png))\\\" (?i) makes it
 * case insensitive could maybe get images from pdf's with
 * http://pdfaid.com/ExtractImages.aspx and/or use insstalled tool to convert
 * bmp, etc.
 *
 *
 *
 * DO NOT END DIRECTORY NAME WITH '\"'. It will quote the quotation mark.
 *
 * MAKE SURE moves.txt is the last -moves files
 *
 * Can't get disjunction to work:
 *
 * -image_regexp SRC=\"(/gmd/.*?\.gif)\"
 *
 * -image_regexp SRC=\"(http://.*?\.gif)\"
 *
 */

public class OAI extends Options { // NO_UCD (unused code)

	/**
	 * Flag whether we're lexically inside a record
	 */
	private boolean isInRecord;
	private String tag = null;
	private String subfield = null;
	StringBuilder data;
	private String set;
	private String setSpec;
	private final Hashtable<String, String> setNames = new Hashtable<>();

	public static void main(final String[] args) {
		try {
			new OAI().init(true, args);
		} catch (final Throwable ex) {
			ex.printStackTrace(System.out);
		}
	}

	@Override
	public void startDocument() {
		// System.out.println("Document processing started");
		// db("ALTER TABLE raw_facet ADD INDEX name (name)");
		/**
		 * Holds successive subfield values for concatenating hierarchical
		 * lists. E.g. in
		 *
		 * <marc:subfield code="z">Virgina</marc:subfield>
		 *
		 * <marc:subfield code="z">Richmond</marc:subfield>
		 */

		populateHandler.parserState = new Hashtable<String, String[]>();
	}

	@Override
	public void endDocument() {
		// db("ALTER TABLE raw_facet DROP INDEX name");
		// System.out.println("Document processing finished");
		System.out.println("...done\n");
	}

	@Override
	public void startElement(@SuppressWarnings("unused") final String uri, final String localName, final String qName,
			final Attributes attrs) {
		// System.out.println("startElement: " + qName + " " + uri + " " +
		// localName);
		if (localName.equals("record")) {
			if (!isInRecord) {
				// loc-grabill.xml has nested <record><marc:record> tags, so
				// don't create two records
				isInRecord = true;
				try {
					populateHandler.newItem();
				} catch (final SQLException e1) {
					e1.printStackTrace();
				}
				if (set != null) {
					try {
						final Field field = Facet.getDateFacet("Collection");
						final boolean inserted = field.insert(set, populateHandler);
						assert inserted : field + " '" + set + "'";
					} catch (final SQLException e) {
						e.printStackTrace();
					}
				}
			}
		} else if (localName.equals("request")) {
			// Initial ListRecords request will specify the set. (Subsequent
			// requests with a ResumptionToken won't specify set, but it
			// will be
			// the same one.)
			final String setSpec1 = attrs.getValue("set");
			if (setSpec1 != null) {
				set = setNames.get(setSpec1);
				// System.out.println(setSpec + " => " + set);
				assert set != null;
			}
		} else if (localName.equals("setSpec") || localName.equals("setName")) {
			// These are only used as pairs to remember the association
			// between
			// the short name and the descriptive name. Only the request/set
			// above associates records with sets.
			data = new StringBuilder();
		} else if (qName.startsWith("marc:")) {
			if (localName.equals("datafield")) {
				clearSubfacetState();
				// ind1 = attrs.getValue("ind1");
				// ind2 = attrs.getValue("ind2");
				tag = attrs.getValue("tag");
			} else if (localName.equals("controlfield")) {
				clearSubfacetState();
				// ind1 = attrs.getValue("ind1");
				// ind2 = attrs.getValue("ind2");
				tag = attrs.getValue("tag");
				data = new StringBuilder();
			} else if (isInRecord && localName.equals("subfield")) {
				subfield = attrs.getValue("code");
				data = new StringBuilder();
			}
		} else if (qName.startsWith("dc:")) {
			if (isInRecord) {
				// tag = qName;
				data = new StringBuilder();
			}
		}
	}

	@SuppressWarnings("unchecked")
	void clearSubfacetState() {
		((Hashtable<String, String[]>) populateHandler.parserState).clear();
	}

	@Override
	public void characters(final char[] ch, final int start, final int length) {
		// StringBuilder temp = new StringBuilder();
		// temp.append(ch, start, length);
		// System.out.println("characters: " + temp);
		if (data != null) {
			data.append(ch, start, length);
		}
	}

	@Override
	public void endElement(final String uri, final String localName, final String qName) {
		if (localName.equals("record")) {
			isInRecord = false;
		}
		if (data != null && data.length() > 1) {
			final String value = GenericFacetValueParser.trim(data.toString());

			// System.out.println("endElement handling "
			// + (isMARC ? tag + " " + subfield + " " : "") + qName + " "
			// + value);
			try {
				if (localName.equals("setSpec")) {
					setSpec = value;
				} else if (localName.equals("setName")) {
					setNames.put(setSpec, value);
				} else {
					Field field = null;
					if (qName.startsWith("marc:")) {
						if (localName.equals("subfield")) {
							field = MARC.getInstance().decode(tag, subfield);
						}
						// field = Facet.getGenericFacet(localName, this);
						// assert field != null
						// || localName.equals("controlfield") :
						// "endElement not handling "
						// + tag
						// + " "
						// + subfield
						// + " "
						// + qName + " " + uri;
					} else if (qName.startsWith("dc:")) {
						field = DC.getInstance().decode(localName);
						assert field != null : "endElement not handling " + qName + " " + uri;
					}
					if (field != null) {
						// System.out.println("endElement " + recordNum + " " +
						// field +
						// " "
						// + value);
						try {
							field.insert(value, populateHandler);
						} catch (final SQLException e) {
							e.printStackTrace();
						}
						// System.out.println("...endElement ");
					}

				}
			} catch (final Throwable e) {
				throw new IllegalArgumentException("While parsing " + qName + " = " + value, e);
			}
		}
		data = null;
	}

	// private String whereAmI() {
	// return "tag=" + tag + "; subfield=" + subfield + "; data=" + data;
	// }

	// static void parseTGM(Map use, Map bt, String directory, String filename)
	// {
	// if (filename != null) {
	// File f = new File(directory, filename);
	// // BufferedReader in = new BufferedReader(f);
	// // while (true) {
	// // String[] s = in.readLine().split("\t");
	// // abbrevs.put(s[0], Util.subArray(s, 1, String.class));
	// // }
	// }
	//
	// }

	// private static final String namespaceMARC =
	// "http://www.loc.gov/MARC21/slim";
	// private static final String namespaceDC =
	// "http://purl.org/dc/elements/1.1/";
}
