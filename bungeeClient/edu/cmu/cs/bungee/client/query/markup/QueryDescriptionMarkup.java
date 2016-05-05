package edu.cmu.cs.bungee.client.query.markup;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.InformediaQuery;
import edu.cmu.cs.bungee.client.query.query.Query.DescriptionCategory;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class QueryDescriptionMarkup extends DefaultMarkup {
	private static final @NonNull MarkupElement FILTER_CONSTANT_MENTIONS_ONE = MarkupStringElement
			.getElement(" whose description mentions one of the words '");
	private static final @NonNull MarkupElement FILTER_TYPE_AND = MarkupStringElement.getElement(" and");
	private static final @NonNull MarkupElement CONNECTOR_SPACE = MarkupStringElement.getElement(" ");
	private static final @NonNull MarkupElement CONNECTOR_SINGLE_BACKWARD_QUOTE = MarkupStringElement.getElement("'");
	private static final @NonNull MarkupElement INCLUDED_COLOR = new MarkupPaintElement(BungeeConstants.INCLUDED_COLOR);

	/**
	 * Only called by Query.description().
	 */
	public static @NonNull Markup getDescription(final @NonNull List<Markup> phrases,
			final @NonNull Set<String> searches, final @NonNull Set<InformediaQuery> informediaQueries,
			final @NonNull String genericObjectLabel) {
		// System.out.println("QueryDescriptionMarkup.getDescription\n phrases="
		// + phrases);
		final QueryDescriptionMarkup result = new QueryDescriptionMarkup();
		result.addDescriptionClauses(phrases, searches, informediaQueries);
		return result.compile(genericObjectLabel);
	}

	/******************************************************************
	 * Everything below is private.
	 ******************************************************************/

	private QueryDescriptionMarkup() {
		super();
	}

	private void addDescriptionClauses(final @NonNull List<Markup> phrases, final @NonNull Set<String> searches,
			final @NonNull Set<InformediaQuery> informediaQueries) {
		try {
			addDescriptionNounPhrasesOnly(phrases);
			boolean first = true;
			first = addDescriptionClausesFromPhrases(phrases, first);
			first = addDescriptionClausesFromSearches(searches, first);
			addDescriptionClausesForInformedia(informediaQueries, first);
		} catch (final Throwable e) {
			System.err.println("DefaultMarkup.descriptionClauses: While processing:\n phrases=" + phrases
					+ "\n searches=" + searches + "\n informediaQueries=" + informediaQueries);
			throw (e);
		}
	}

	/*
	 * Refer to the objects satisfying the phrases (using the plural), e.g.
	 * "Paintings", as identified by an OBJECT phrase, or by default, the
	 * GENERIC_OBJECT_LABEL.
	 */
	private void addDescriptionNounPhrasesOnly(final @NonNull List<Markup> phrases) {
		for (final Markup phrase : phrases) {
			if (phrase.get(0).equals(DescriptionCategory.OBJECT)) {
				for (int j = 1; j < phrase.size(); j++) {
					final MarkupElement element = phrase.get(j);
					assert element != null;
					if (DefaultMarkup.isConnector(element)) {
						add(element);
					} else {
						addPlural(element);
					}
				}
			}
		}
		if (size() == 0) {
			add(DefaultMarkup.PLURAL_TAG);
			add(DefaultMarkup.GENERIC_OBJECT_LABEL);
		}
	}

	private void addPlural(final @NonNull MarkupElement element) {
		final MarkupElement plural = element.pluralize();
		if (plural != null) {
			add(plural);
		} else {
			add(DefaultMarkup.PLURAL_TAG);
			add(element);
		}
	}

	private boolean addDescriptionClausesForInformedia(final @NonNull Set<InformediaQuery> informediaQueries,
			boolean first) {
		for (final InformediaQuery informediaQuery : informediaQueries) {
			final String s = "that match the Informedia query '" + informediaQuery + "'";
			if (first) {
				add(CONNECTOR_SPACE);
				first = false;
			} else {
				add(CONNECTOR_AND);
			}
			add(s);
		}
		return first;
	}

	private boolean addDescriptionClausesFromSearches(final @NonNull Set<String> searches, boolean first) {
		for (final String search : searches) {
			final boolean isQuoted = search.startsWith("\"") && search.endsWith("\"");
			final MarkupElement mentions = !isQuoted && UtilString.nOccurrences(search, ' ') > 0
					? FILTER_CONSTANT_MENTIONS_ONE : FILTER_CONSTANT_MENTIONS;
			if (first) {
				add(CONNECTOR_SPACE);
				first = false;
			} else {
				add(FILTER_TYPE_AND);
			}
			add(mentions);
			add(INCLUDED_COLOR);
			add(new MarkupSearchElement(search));
			add(DefaultMarkup.DEFAULT_COLOR_TAG);
			add(CONNECTOR_SINGLE_BACKWARD_QUOTE);
		}
		return first;
	}

	private static final DescriptionCategory[] DESCRIPTION_CATEGORIES_META_CONTENT = { DescriptionCategory.META,
			DescriptionCategory.CONTENT };

	private boolean addDescriptionClausesFromPhrases(final @NonNull List<Markup> phrases, boolean first) {
		for (final DescriptionCategory descriptionCategory : DESCRIPTION_CATEGORIES_META_CONTENT) {
			for (final Markup phrase : phrases) {
				if (descriptionCategory.equals(phrase.get(0)) && !topLevelFacetClause(phrase)) {
					if (descriptionCategory.equals(DescriptionCategory.CONTENT)) {
						if (first) {
							// result.add(" that");
							first = false;
						} else {
							add(FILTER_TYPE_AND);
						}
					}
					addAll(phrase.subList(1));
				}
			}
		}
		return first;
	}

	/**
	 * Destructively adds " having a[n] <facet type>" to this DefaultMarkup for
	 * every facet type PME in phrase.
	 *
	 * @return whether it added anything.
	 */
	private boolean topLevelFacetClause(final @NonNull Markup phrase) {
		boolean result = false;
		for (final MarkupElement element : phrase.subList(1)) {
			if (element instanceof PerspectiveMarkupElement) {
				final PerspectiveMarkupElement pme = (PerspectiveMarkupElement) element;
				if (pme.getParentPME() == null) {
					add(" having" + UtilString.indefiniteArticle(pme.getName()));
					add(pme);
					result = true;
				}
			}
		}
		return result;
	}

}
