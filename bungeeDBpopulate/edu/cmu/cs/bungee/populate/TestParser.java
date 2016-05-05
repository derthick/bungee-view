package edu.cmu.cs.bungee.populate;

import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.Token;

/**
 * Run it like this:
 *
 * -parserClass "DateFacetValueParser" -value "14 Aug 2015"
 *
 * -moves MyTGM.txt,places_hierarchy.txt,Moves.txt -pass <bungee-password>
 */
public class TestParser extends Options {

	public TestParser() {
	}

	public static void main(final String[] args) {
		try {
			new TestParser().init(true, args);
		} catch (final Throwable e) {
			e.printStackTrace(System.out);
		}
	}

	@SuppressWarnings("null")
	@Override
	protected void init(final boolean isParser, String[] args) throws Throwable {
		addOptions(isParser);
		sm_main.addStringToken("parserClass", "name of a subclass of FacetValueParser", Token.optSwitch,
				"DateFacetValueParser");
		sm_main.addStringToken("value", "value to parse", Token.optRequired, "");
		args = ArrayUtils.add(ArrayUtils.add(args, "-db"), "test_parser");
		if (args != null && sm_main.parseArgs(args)) {
			try (JDBCSample _jdbc = getJDBC();) {
				jdbc = _jdbc;
				populateHandler = getPopulateHandler();
				final String movesFile = sm_main.getStringValue("moves");
				if (movesFile != null) {
					final String directory = sm_main.getStringValue("directory");
					populateHandler.setMoves(parsePairFiles(directory, movesFile));
				}

				String valueToParse = sm_main.getStringValue("value");
				final Class<?> parserClass = Class.forName(sm_main.getStringValue("parserClass"));
				final FacetValueParser parser = (FacetValueParser) parserClass.newInstance();
				final Facet facet = new FacetWithInsert(parser);
				valueToParse = valueToParse.trim();
				System.out.println("Parsing '" + valueToParse + "'");
				facet.insert(valueToParse.trim(), populateHandler);
			}
		}
	}

	static class FacetWithInsert extends Facet {

		FacetWithInsert(final FacetValueParser _parser) {
			super("<FacetType>", _parser);
		}

		@Override
		public boolean insert(final String value, final PopulateHandler handler) {
			final String[][] facets = parse(value, handler);
			for (final String[] pathFacets : facets) {
				assert pathFacets != null;
				final List<String> path = path(value, pathFacets);
				System.out.println("Parse result: " + path);
			}
			final boolean result = facets.length > 0;
			if (!result) {
				System.out.println("No parse results");
			}
			return result;
		}

	}

}
