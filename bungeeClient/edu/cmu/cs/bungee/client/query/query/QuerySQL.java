package edu.cmu.cs.bungee.client.query.query;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.InformediaQuery;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupPerspectiveRange;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class QuerySQL {

	/**
	 * Use BETWEEN instead of IN (...) if number of elements exceed this;
	 */
	private static final int MAX_INLIST_SIZE = 1000;
	private final @NonNull Query query;

	/**
	 * Only used as an argument to QuerySQL.onItemsQuery().
	 *
	 * E.g. " AND MATCH(it.facet_names, it.description, it.title,...) AGAINST ("
	 */
	private final @NonNull String matchFieldsPrefix;

	QuerySQL(final @NonNull Query _query) {
		query = _query;
		matchFieldsPrefix = getMatchFieldsPrefix(query.itemDescriptionFields());
	}

	/**
	 * @param ignorePred
	 *            ignore restrictions on this Perspective. Used by
	 *            PerspectiveList.
	 * @return SQL rendering of the current query, or null if there are no
	 *         restrictions.
	 */
	@Nullable
	String onItemsQuery(final @Nullable Perspective ignorePred) {
		final StringBuilder qJOIN = new StringBuilder(400);
		final List<String> wheres = new LinkedList<>();
		String mainOnItemsTable = handleSearches(qJOIN, wheres);

		boolean needDistinct = false;
		int joinIndex = 0;
		for (final SortedSet<Perspective> restrictions : computeInclude(ignorePred, true)) {
			needDistinct |= restrictions.size() > 1;
			mainOnItemsTable = adjoinTrue(qJOIN, restrictions, joinIndex++, mainOnItemsTable, wheres);
		}
		final @NonNull SortedSet<Perspective> excludeFacets = computeExclude(ignorePred);
		if (excludeFacets.size() > 0) {
			adjoinFalse(qJOIN, excludeFacets, joinIndex);
			wheres.add("itemFacetTable" + joinIndex + ".record_num IS NULL");
		}
		final @NonNull Set<InformediaQuery> informediaQueries = query.getInformediaQueries();
		if (informediaQueries.size() > 0) {
			wheres.add(" onItemsTable.record_num IN (" + ((InformediaQuery) informediaQueries.toArray()[0]).getItems()
					+ ")");
		}
		final boolean isAddNullColumn = ignorePred == null;
		final String result = onItemsQueryInternal(isAddNullColumn, qJOIN, wheres, mainOnItemsTable, needDistinct);
		// System.out.println("QuerySQL.onItemsQuery return " + result);
		return result;
	}

	/**
	 * @return "RESTRICTED", "item", "item_facetNtype_heap", or
	 *         "item_order_heap"
	 */
	private @Nullable String handleSearches(final @NonNull StringBuilder qJOIN, final @NonNull List<String> wheres) {
		String mainOnItemsTable = query.isRestrictedData() ? "RESTRICTED" : null;
		final @NonNull Collection<String> searches = query.getSearches();
		if (searches.size() > 0) {
			// Don't use USING because AND in matchFieldsPrefix will cause
			// syntax error
			String newMatchFieldsPrefix = matchFieldsPrefix;
			if (mainOnItemsTable == null) {
				mainOnItemsTable = "item";
				newMatchFieldsPrefix = matchFieldsPrefix.replace("item.", "onItemsTable.");
				assert newMatchFieldsPrefix != null;
				// matchFieldsPrefix = newMatchFieldsPrefix;
			} else {
				qJOIN.append(" INNER JOIN item ON onItemsTable.record_num = item.record_num");
			}
			for (final String search : searches) {
				wheres.add(newMatchFieldsPrefix + JDBCSample.quoteForSQL(search) + " IN BOOLEAN MODE)");
			}
		}
		return mainOnItemsTable;
	}

	private @Nullable String onItemsQueryInternal(final boolean isAddNullColumn, final @NonNull StringBuilder qJOIN,
			final @NonNull List<String> wheres, @Nullable String mainOnItemsTable, final boolean needDistinct) {
		String result = null;
		if (qJOIN.length() > 0 || wheres.size() > 0) {
			String sortColumn;
			if (mainOnItemsTable == null) {
				mainOnItemsTable = "item_order_heap onItemsTable";
				sortColumn = "onItemsTable." + query.sortColumnName();
			} else if (mainOnItemsTable == "RESTRICTED" || !isAddNullColumn) {
				mainOnItemsTable = mainOnItemsTable + " onItemsTable";
				sortColumn = "onItemsTable.sort";
			} else {
				mainOnItemsTable = mainOnItemsTable + " onItemsTable INNER JOIN item_order_heap USING (record_num)";
				sortColumn = "item_order_heap." + query.sortColumnName();
			}
			final String wheresString = wheres.isEmpty() ? "" : " WHERE " + UtilString.join(wheres, " AND ");
			result = (needDistinct ? "SELECT DISTINCT" : "SELECT") + " onItemsTable.record_num"
					+ (isAddNullColumn ? ", NULL" : "") + " FROM " + mainOnItemsTable + qJOIN + wheresString
					+ (isAddNullColumn ? " ORDER BY " + sortColumn : "");

			// This can happen if globals.itemDescriptionFields includes a
			// qualified column
			result = result.replace("onItemsTable.onItemsTable", "onItemsTable");
		}
		return result;
	}

	/**
	 * join in another copy of item_facetNtype_heap and require its facet_id to
	 * [not] be in sortedItemPredicates.
	 *
	 * @param mainOnItemsTable
	 */
	private static void adjoinFalse(final @NonNull StringBuilder qJOIN,
			final @NonNull SortedSet<Perspective> sortedItemPredicates, final int joinIndex) {
		final String itemFacetTable = "itemFacetTable" + joinIndex;
		qJOIN.append(getJoinString(joinIndex, false)).append(" AND ");
		itemPredsSQLexpr(qJOIN, sortedItemPredicates, itemFacetTable + ".facet_id");
		// itemPredsSQLexprWhere(wheres, sortedItemPredicates, itemFacetTable
		// + ".facet_id");
	}

	private static @Nullable String adjoinTrue(final @NonNull StringBuilder qJOIN,
			final @NonNull SortedSet<Perspective> sortedItemPredicates, final int joinIndex,
			@Nullable String mainOnItemsTable, final @NonNull List<String> wheres) {
		String alias;
		if (mainOnItemsTable == null) {
			mainOnItemsTable = "item_facetNtype_heap";
			alias = "onItemsTable";
		} else {
			alias = "itemFacetTable" + joinIndex;
			qJOIN.append(getJoinString(joinIndex, true));
			// itemPredsSQLexpr(qJOIN, sortedItemPredicates, alias +
			// ".facet_id");
		}
		itemPredsSQLexprWhere(wheres, sortedItemPredicates, alias + ".facet_id");
		return mainOnItemsTable;
	}

	/**
	 * Add a predicate requiring the SQL variable to [not] be in the Collection
	 * of ItemPredicates.
	 *
	 * For shorter command lines, uses BETWEEN to group sequential Perspectives.
	 * NO! MySQL doesn't use BETWEEN efficiently.
	 */
	static void itemPredsSQLexprWhere(final @NonNull List<String> wheres,
			final @NonNull SortedSet<Perspective> perspectives, final @NonNull String variable) {
		assert !perspectives.isEmpty();
		if (perspectives.size() > 1) {
			final SortedSet<Perspective> lonePerspectives = new TreeSet<>();
			final List<String> disjuncts = new LinkedList<>();
			final Collection<MarkupElement> merged = PerspectiveMarkupElement.mergePerspectives(perspectives);
			assert merged.size() < 5000 : merged;
			for (final MarkupElement markupElement : merged) {
				if (markupElement instanceof Perspective) {
					lonePerspectives.add((Perspective) markupElement);
				} else if (markupElement instanceof MarkupPerspectiveRange) {
					final MarkupPerspectiveRange markupPerspectiveRange = (MarkupPerspectiveRange) markupElement;
					final int nFacetsEffective = markupPerspectiveRange.nFacetsRaw();
					if (nFacetsEffective > MAX_INLIST_SIZE) {
						disjuncts.add(variable + " BETWEEN " + markupPerspectiveRange.fromPerspective().getID()
								+ " AND " + markupPerspectiveRange.toPerspective().getID());
					} else {
						lonePerspectives.addAll(markupPerspectiveRange.getFacets());
					}
				} else {
					assert false : "Unexpected type: " + markupElement;
				}
			}
			if (lonePerspectives.size() > 0) {
				assert lonePerspectives.size() < 2 * MAX_INLIST_SIZE : lonePerspectives.size();
				final String perspectiveIDs = Query.getItemPredicateIDs(lonePerspectives);
				disjuncts.add(variable + " IN (" + perspectiveIDs + ")");
			}
			assert disjuncts.size() > 0 : perspectives;
			final String disjunctsString = disjuncts.size() == 1 ? disjuncts.get(0)
					: "(" + UtilString.join(disjuncts, " OR ") + ")";
			wheres.add(disjunctsString);
		} else {
			wheres.add(variable + " = " + perspectives.first().getID());
		}
	}

	/**
	 * Add a predicate requiring the SQL variable to [not] be in the Collection
	 * of ItemPredicates.
	 *
	 * For shorter command lines, uses BETWEEN to group sequential Perspectives.
	 * NOT DOING THIS NOW (and so no need for perspectives to be sorted.)
	 */
	public static @NonNull StringBuilder itemPredsSQLexpr(@Nullable StringBuilder qJOIN,
			final @NonNull SortedSet<Perspective> perspectives, final @NonNull String variable) {
		assert !perspectives.isEmpty();
		if (qJOIN == null) {
			qJOIN = new StringBuilder();
		}
		// final List<String> predicateDescs = new LinkedList<>();
		// final SortedSet<ItemPredicate> itemPreds = PerspectiveMarkupElement
		// .coalesce(sortedItemPredicates);
		// final Collection<Perspective> perspectives = new LinkedList<>();

		// if statement below doesn't deal correctly with MexPerspectives, so
		// handle them here. Coalescing ensures use of BETWEEN for ranges.
		// for (final Iterator<ItemPredicate> it = itemPreds.iterator(); it
		// .hasNext();) {
		// final ItemPredicate itemPredicate = it.next();
		// if (itemPredicate instanceof MarkupPerspectiveRange) {
		// final MarkupPerspectiveRange markupPerspectiveRange =
		// (MarkupPerspectiveRange) itemPredicate;
		// final SortedSet<Perspective> facets = markupPerspectiveRange
		// .getFacets();
		// it.remove();
		// predicateDescs.add(variable + " BETWEEN "
		// + facets.first().getID() + " AND "
		// + facets.last().getID());
		// } else if (itemPredicate instanceof MexPerspectives) {
		// perspectives.addAll(itemPredicate.getFacets());
		// } else {
		// perspectives
		// .add(((PerspectiveMarkupElement) itemPredicate).perspective);
		// }
		// }
		final String perspectiveIDs = Query.getItemPredicateIDs(perspectives);
		if (perspectives.size() > 1) {
			assert perspectives.size() < 2 * MAX_INLIST_SIZE : perspectives.size();
			qJOIN.append(variable + " IN (" + perspectiveIDs + ")");
		} else {
			qJOIN.append(variable + " = " + perspectiveIDs);
		}
		// if (predicateDescs.size() > 1) {
		// qJOIN.append("(").append(UtilString.join(predicateDescs, " OR "))
		// .append(")");
		// } else {
		// qJOIN.append(predicateDescs.get(0));
		// }
		// UtilString.indent("QuerySQL.itemPredsSQLexpr return " + qJOIN);
		return qJOIN;
	}

	private static @NonNull String[][] joinStrings = updateJoinStrings(10);

	/**
	 * @param joinIndex
	 * @param polarity
	 *            whether to use INNER or LEFT
	 * @return INNER|LEFT JOIN item_facetNtype_heap itemFacetTable<joinIndex> ON
	 *         onItemsTable.record_num = itemFacetTable<joinIndex>.record_num
	 *         AND itemFacetTable<joinIndex>.facet_id
	 */
	private static @NonNull String getJoinString(final int joinIndex, final boolean polarity) {
		synchronized (joinStrings) {
			final String result = updateJoinStrings(joinIndex)[joinIndex][polarity ? 0 : 1];
			assert result != null;
			return result;
		}
	}

	@SuppressWarnings("null")
	private static synchronized @NonNull String[][] updateJoinStrings(final int joinIndex) {
		if (joinStrings == null || joinIndex >= joinStrings.length) {
			final String[][] _joinStrings = new String[joinIndex + 10][2];
			for (int i = 0; i < joinIndex + 10; i++) {
				final String s = " JOIN item_facetNtype_heap itemFacetTable" + i
				// + " USE INDEX (facet)"

				// + " ON onItemsTable.record_num = itemFacetTable" + i
				// + ".record_num AND "
						+ " ON onItemsTable.record_num = itemFacetTable" + i + ".record_num "

				// + i + ".facet_id "
				;
				_joinStrings[i][0] = " INNER" + s;
				_joinStrings[i][1] = " LEFT" + s;
			}
			joinStrings = _joinStrings;
		}
		return joinStrings;
	}

	private @NonNull SortedSet<Perspective> computeExclude(final @Nullable Perspective ignorePred) {
		final SortedSet<Perspective> result = new TreeSet<>();
		for (final SortedSet<Perspective> set : computeInclude(ignorePred, false)) {
			result.addAll(set);
		}
		return result;
	}

	/**
	 * For each facetType, compute polarity=true restrictions that are not
	 * descendents of ignorePred. Add the restrictions of each facetTypes, if
	 * any.
	 */
	private @NonNull List<SortedSet<Perspective>> computeInclude(final @Nullable Perspective ignorePred,
			final boolean polarity) {
		final List<SortedSet<Perspective>> include = new LinkedList<>();
		for (final Perspective facetType : query.getFacetTypes()) {
			// Do top-level with isLocalOnly = false in case newly
			// displayable facetType hasn't been displayed yet.
			final SortedSet<Perspective> restrictions = facetType.getRestrictionFacetInfos(polarity);
			if (ignorePred != null) {
				for (final Iterator<Perspective> it = restrictions.iterator(); it.hasNext();) {
					final Perspective restriction = it.next();
					if (restriction.hasAncestor(ignorePred)) {
						it.remove();
					}
				}
			}
			// UtilString.indent("Query.computeInclude facetType=" +
			// facetType
			// + " restrictions=" + restrictions);
			final int nRestrictions = restrictions.size();
			if (nRestrictions > 0) {
				include.add(restrictions);
			}
		}
		return include;
	}

	private static @NonNull String getMatchFieldsPrefix(final @NonNull String[] itemDescFields) {
		final StringBuilder sb = new StringBuilder();
		sb.append("MATCH(item.facet_names");
		for (final String itemDescField : itemDescFields) {
			sb.append(",item.").append(itemDescField);
		}
		sb.append(") AGAINST (");
		return Util.nonNull(sb.toString());
	}

}
