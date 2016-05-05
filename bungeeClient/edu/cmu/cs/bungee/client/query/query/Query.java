package edu.cmu.cs.bungee.client.query.query;

import static edu.cmu.cs.bungee.javaExtensions.Util.assertNotMouseProcess;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilString.now;

import java.awt.event.InputEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.InformediaQuery;
import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.ItemPredicate;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.PrefetchStatus;
import edu.cmu.cs.bungee.client.query.TopTags;
import edu.cmu.cs.bungee.client.query.explanation.Distribution;
import edu.cmu.cs.bungee.client.query.explanation.Explanation;
import edu.cmu.cs.bungee.client.query.explanation.FacetSelection;
import edu.cmu.cs.bungee.client.query.explanation.NonAlchemyExplanation;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.markup.QueryDescriptionMarkup;
import edu.cmu.cs.bungee.client.query.markup.Restrictions;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.header.SortLabelNMenu;
import edu.cmu.cs.bungee.client.viz.tagWall.PerspectiveList;
import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.servlet.FetchType;
import edu.cmu.cs.bungee.servlet.OnItemsTable;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Holds a set of filters that has been applied to a database, and all the
 * associated facet and item information
 */
public class Query implements ItemPredicate {
	protected static final long serialVersionUID = 1L;

	public enum DescriptionCategory implements MarkupElement {
		@NonNull META, @NonNull CONTENT, @NonNull OBJECT;

		@Override
		public boolean isEffectiveChildren() {
			return false;
		}

		@SuppressWarnings("null")
		@Override
		public @NonNull String getName() {
			return name();
		}

		@SuppressWarnings("null")
		@Override
		public @NonNull String getName(@SuppressWarnings("unused") final RedrawCallback _redraw) {
			return name();
		}

		@Override
		public @NonNull Markup description() {
			return DefaultMarkup.newMarkup(this);
		}

		@Override
		public @Nullable MarkupElement pluralize() {
			return null;
		}

		@Override
		public @Nullable MarkupElement merge(@SuppressWarnings("unused") final MarkupElement nextElement) {
			return null;
		}

		@Override
		public boolean shouldUnderline() {
			return false;
		}
	}

	/**
	 * Bungee.replayOp secretly knows this value
	 */
	public static final int ERROR = 24;

	/**
	 * Essentially @NonNull
	 */
	public final String dbName;

	/**
	 * Number of top-level facets
	 */
	public final int nAttributes;

	/**
	 * Mouse documentation for what happens if you click on the Selected Item
	 * thumbnail. The empty string disables clicking.
	 */
	public final @NonNull String itemURLdoc;

	public final @NonNull Object prefetchingLock = "Prefetching Lock";
	private final @NonNull Object queryValidLock = "Query Valid Lock";

	protected final @NonNull ServletInterface db;

	private final @NonNull QuerySQL querySQL;

	/**
	 * What you call the items, e.g. 'images', 'works', etc. (plural)
	 */
	private final @NonNull String genericObjectLabel;

	private final @NonNull SortedSet<Perspective> displayedPerspectives = new TreeSet<>();

	private final @Immutable @NonNull List<Perspective> facetTypes;

	private final boolean isCorrelations;

	private final @NonNull Set<String> searches = new LinkedHashSet<>();

	/**
	 * Only used by Informedia
	 */
	private final @NonNull Set<InformediaQuery> informediaQueries = new LinkedHashSet<>();

	/**
	 * Maps from facetID to Perspective.
	 */
	private final @NonNull Perspective[] allPerspectives;
	/**
	 * updateData() stores onCounts for ALL facet_id's, even if there is no
	 * Perspective for it yet.
	 */
	private final @NonNull int[] perspectiveOnCounts;
	private final @NonNull int[] perspectiveTotalCounts;
	private int perspectiveOnCountsVersion;

	private final @NonNull Object displayedPerspectivesLock = "displayed Perspectives Lock";

	/**
	 * The dbname, or if restrictData 'dbname/restriction_description'
	 */
	public @NonNull String baseName;

	/**
	 * Hack to suppress error messages
	 */
	public int insideLetterLabeledZoom;

	/**
	 * Sort Results by this.
	 */
	public int sortedByFacetTypeIdOrSpecial = SortLabelNMenu.ORDER_BY_RANDOM;

	long lastActiveTime = 0L;

	/**
	 * These Threads are essentially @NonNull
	 */
	private transient Redrawer redrawer;
	private transient LetterOffsetsCreator letterOffsetsCreator;
	private transient NameGetter nameGetter;
	private transient Prefetcher prefetcher;
	private transient OnCountsIgnoringFacetGetter onCountsIgnoringFacetGetter;

	/**
	 * the number of items that satisfy the current filters
	 *
	 * always >= 0
	 */
	private int onCount;

	/**
	 * the number of items in the [restricted] collection
	 *
	 * always >= 0
	 */
	private int totalCount;

	/**
	 * Used to determine whether cached values are up to date.
	 */
	private int version = 1;
	private int externalVersion = version;

	private boolean isRestrictedData = false;

	private boolean isIntensionallyRestricted = false;

	private @NonNull Set<String> prevSearches = new HashSet<>();
	private @Nullable Pattern textSearchPattern = null;

	/**
	 * Called in thread Bungee.queryUpdater
	 *
	 * @param server
	 *            the Bungee View server to connect to, e.g.
	 *            http://localhost/bungeeOLD/Bungee
	 * @param dbName
	 *            the database to connect to, e.g. movie
	 * @param isTraceServletCalls
	 */
	public static @NonNull Query getQuery(final @NonNull String server, final @NonNull String dbName,
			final boolean noTemporaryTables, final boolean isTraceServletCalls, final int slowQueryTime) {
		final ServletInterface servletInterface = new ServletInterface(server, dbName, noTemporaryTables,
				isTraceServletCalls, slowQueryTime);
		return servletInterface.isEditable ? new EditableQuery(servletInterface) : new Query(servletInterface);
	}

	Query(final @NonNull ServletInterface _db) {
		db = _db;
		querySQL = new QuerySQL(this);
		decacheDistributions();
		Item.decacheItems();

		if (db.errorMessage() == null) {
			dbName = db.getDBname();
			baseName = db.getDBdescription();
			isCorrelations = db.isCorrelations;
			totalCount = db.itemCount;
			onCount = totalCount;
			genericObjectLabel = UtilString.pluralize(db.genericObjectLabel);
			itemURLdoc = db.itemURLdoc;
			allPerspectives = new Perspective[db.nFacets + 1];

			redrawer = new Redrawer(this);
			new Thread(redrawer).start();

			prefetcher = new Prefetcher(this);
			new Thread(prefetcher).start();

			nameGetter = new NameGetter(this);
			new Thread(nameGetter).start();

			letterOffsetsCreator = new LetterOffsetsCreator(this);
			new Thread(letterOffsetsCreator).start();

			perspectiveOnCounts = new int[allPerspectives.length];
			perspectiveTotalCounts = new int[allPerspectives.length];
			updateFilteredCounts();
			System.arraycopy(perspectiveOnCounts, 0, perspectiveTotalCounts, 0, perspectiveOnCounts.length);
			nAttributes = initFacetTypes();
		} else {
			// Return gracefully so parent can deal with error
			nAttributes = -1;
			itemURLdoc = "";
			genericObjectLabel = "Unknown";
			baseName = "InvalidQuery";
			allPerspectives = new Perspective[0];
			perspectiveOnCounts = new int[allPerspectives.length];
			perspectiveTotalCounts = new int[allPerspectives.length];
			dbName = null;
			isCorrelations = false;
		}
		facetTypes = Util.nonNull(Collections.unmodifiableList(new ArrayList<>(displayedPerspectives)));
		System.out.println(dbName + ".isCorrelations=" + isCorrelations);
	}

	/**
	 * Revert to initial state.
	 */
	public void reset() {
		if (!isQueryValid()) {
			version++;
			setQueryValid(true);
			// Don't have things hanging around waiting; let them get an error.
			// (But increase version so they know they're stale.)
		}
		decacheDistributions();
		Item.decacheItems();
		redrawer.reset();
		prefetcher.reset();
		nameGetter.reset();
		letterOffsetsCreator.reset();
		clear();
		isRestrictedData = false;
		totalCount = db.itemCount;
		onCount = totalCount;
		updateFilteredCounts();
		System.arraycopy(perspectiveOnCounts, 0, perspectiveTotalCounts, 0, perspectiveOnCounts.length);

		baseName = db.getDBdescription();
		for (int i = allPerspectives.length - 1; i >= 0; i--) {
			final Perspective facet = allPerspectives[i];
			if (facet != null) {
				facet.restrictData();
			}
		}
	}

	/**
	 * @param facets
	 * @return the Query associated with the facets.
	 */
	public static @NonNull Query query(final @NonNull Collection<Perspective> facets) {
		assert facets.size() > 0;
		@SuppressWarnings("null")
		final Query query = UtilArray.some(facets).query();
		return query;
	}

	public long lastActiveTime() {
		return !isQueryValid() ? Long.MAX_VALUE : Math.max(lastActiveTime, db.lastActiveTime());
	}

	/**
	 * Never call in mouse process. Use queueRedraw() instead.
	 *
	 * waitForValidQuery and invokeLater in mouse process. Notes lastActiveTime.
	 */
	public void invokeWhenQueryValid(final @NonNull Runnable runnable) {
		if (waitForValidQuery()) {
			queryInvokeLater(runnable);
		}
	}

	/**
	 * Never call in mouse process. Use queueRedraw() instead.
	 *
	 * invokeLater in mouse process, and update lastActiveTime. Does NOT
	 * waitForValidQuery.
	 */
	public void queryInvokeLater(final @NonNull Runnable runnable) {
		assert assertNotMouseProcess();
		final Runnable runnable2 = new Runnable() {

			@Override
			public void run() {
				runnable.run();
				lastActiveTime = now();
			}
		};
		javax.swing.SwingUtilities.invokeLater(runnable2);
	}

	/**
	 * If isQueryValid() call callback right away; otherwise
	 * queueRedraw(callback)
	 */
	public void queueOrRedraw(final @NonNull RedrawCallback callback) {
		if (!isCallbackQueued(callback)) {
			if (isQueryValid()) {
				callback.redrawCallback();
			} else {
				assert callback != null;
				redrawer.add(callback);
			}
		}
	}

	/**
	 * Calls callback in mouse process when Query is valid.
	 */
	public void queueRedraw(final @NonNull RedrawCallback callback) {
		assert !isQueryValid() || assertNotMouseProcess() : "Don't queue when you can call.";
		if (!isCallbackQueued(callback)) {
			redrawer.add(callback);
		}
	}

	/**
	 * This doesn't seem to ever return true, so avoid the effort.
	 */
	@SuppressWarnings("static-method")
	public boolean isCallbackQueued(@SuppressWarnings("unused") final @NonNull RedrawCallback callback) {
		return false;
		// final boolean result = Redraw.isCallbackQueued(callback, this) ||
		// redrawer.isCallbackQueued(callback)
		// || prefetcher.isCallbackQueued(callback) ||
		// nameGetter.isCallbackQueued(callback)
		// || letterOffsetsCreator.isCallbackQueued(callback);
		// if (result) {
		// System.err.println("Query.isCallbackQueued SAVE! " + callback +
		// UtilString.getStackTrace());
		// }
		// return result;
	}

	public void setExitOnError(final boolean isExit) {
		redrawer.setExitOnError(isExit);
		prefetcher.setExitOnError(isExit);
		nameGetter.setExitOnError(isExit);
		letterOffsetsCreator.setExitOnError(isExit);
		if (onCountsIgnoringFacetGetter != null) {
			onCountsIgnoringFacetGetter.setExitOnError(isExit);
		}
	}

	private void decacheDistributions() {
		Distribution.decacheDistributions();
		NonAlchemyExplanation.decacheExplanations();
		topMutInfCache.clear();
	}

	@NonNull
	String[] itemDescriptionFields() {
		return UtilString.splitComma(db.itemDescriptionFields);
	}

	/**
	 * @return status of most recent servlet response
	 */
	public @Nullable String errorMessage() {
		return db.errorMessage();
	}

	/**
	 * @return the number of Informedia queries
	 */
	private int nInformediaQueries() {
		return informediaQueries.size();
	}

	public boolean isAlive() {
		return redrawer != null;
	}

	/**
	 * clean up when this query is no longer needed
	 */
	public void exit() {
		System.out.println(db.close());
		System.out.println("...exiting Query priority=" + Thread.currentThread().getPriority());
		if (redrawer != null) {
			redrawer.exit();
			redrawer = null;
		}
		if (prefetcher != null) {
			prefetcher.exit();
			prefetcher = null;
		}
		if (onCountsIgnoringFacetGetter != null) {
			onCountsIgnoringFacetGetter.exit();
			onCountsIgnoringFacetGetter = null;
		}
		if (nameGetter != null) {
			nameGetter.exit();
			nameGetter = null;
		}
		if (letterOffsetsCreator != null) {
			letterOffsetsCreator.exit();
			letterOffsetsCreator = null;
		}
		decacheDistributions();

		if (!isQueryValid()) {
			version++;
			setQueryValid(true);
			// Don't have things hanging around waiting; let them get an error.
			// (But increase version so they know they're stale.)
		}
	}

	@Override
	public String toString() {
		return (isQueryValid() ? "<Valid " : "<Invalid ") + UtilString.toString(this, getName(null)).substring(1);
	}

	@Override
	public @NonNull String getName() {
		return getName(null);
	}

	/**
	 * @return e.g. '11,111 images from 1980s from Personal/images from
	 *         Chattanooga'
	 */
	@Override
	public @NonNull String getName(final @Nullable RedrawCallback callback) {
		final StringBuilder buf = new StringBuilder();
		if (isQueryValid()) {
			buf.append(UtilString.addCommas(getOnCount())).append(" ");
		} else {
			buf.append("Invalid Query ");
		}
		buf.append(markupToText(description(), callback));
		if (isRestrictedData()) {
			buf.append(" from ").append(baseName);
		}
		final String result = buf.toString();
		assert result != null;
		return result;
	}

	public @NonNull String markupToText(final @NonNull Markup markup, final @Nullable RedrawCallback callback) {
		return markup.compile(getGenericObjectLabel(false)).toText(callback);
	}

	/**
	 * Only called by HeaderQueryDescription.setDescription()
	 *
	 * @return e.g. "Viewing 66%: the 11,111 " + description().
	 */
	public @NonNull Markup headerQueryDescription() {
		final Markup result = DefaultMarkup.emptyMarkup();
		if (isIntensionallyRestricted()) {
			result.add(DefaultMarkup.FILTER_CONSTANT_VIEWING);
			result.add(DefaultMarkup.FILTER_COLOR_WHITE);
			result.add(Util.nonNull(UtilString.formatPercent(onCount, totalCount, null, true).toString()));
			result.add(DefaultMarkup.DEFAULT_COLOR_TAG);
			result.add(DefaultMarkup.FILTER_CONSTANT_THE);
			result.add(DefaultMarkup.FILTER_COLOR_WHITE);
			result.add(UtilString.addCommas(onCount) + " ");
			result.add(DefaultMarkup.DEFAULT_COLOR_TAG);
			result.addAll(description());
		}
		return result;
	}

	/**
	 * @return e.g. 'works from 20th century.'
	 */
	@Override
	public @NonNull Markup description() {
		final List<Markup> phrases = new LinkedList<>();
		for (final Perspective facetType : facetTypes) {
			final Markup phrase = facetType.getPhrase();
			if (!phrase.isEmpty()) {
				// System.out.println("Query.description " + facetType + ": " +
				// phrases);
				phrases.add(phrase);
			}
		}
		// System.out.println("Query.description " + phrases);
		final Markup result = QueryDescriptionMarkup.getDescription(phrases, searches, informediaQueries,
				genericObjectLabel);
		return result;
	}

	/**
	 * @return ".  (There are now " + describeNfilters() + ")";
	 */
	public @NonNull String describeFilters() {
		return ".  (There are now " + describeNfilters() + ")";
	}

	/**
	 * @return description of this query's number and types of filters
	 */
	public @NonNull String describeNfilters() {
		final int nText = nFilters(true, false, false);
		final int nFacet = nFilters(false, true, false);
		final int nInformediaQueries = nFilters(false, false, true);
		final StringBuilder buf = new StringBuilder();
		if (nFacet + nText + nInformediaQueries == 0) {
			buf.append("no filters");
		} else {
			buf.append("filters on ");
			if (nFacet > 0) {
				buf.append(nFacet).append(" Tags");
				if (nText > 0 || nInformediaQueries > 0) {
					buf.append(" and ");
				}
			}
			if (nText > 0) {
				buf.append(nText).append(" keywords");
				if (nInformediaQueries > 0) {
					buf.append(" and ");
				}
			}
			if (nInformediaQueries > 0) {
				buf.append(nInformediaQueries).append(" Informedia queries");
			}
			buf.append(isRestrictedData() ? " on restricted collection." : ".");
		}
		return Util.nonNull(buf.toString());
	}

	/**
	 * @param isSearches
	 *            include text search filters?
	 * @param isFacet
	 *            include filters on facets?
	 * @return this query's number of filters
	 */
	public int nFilters(final boolean isSearches, final boolean isFacet, final boolean isInformediaQueries) {
		int result = isSearches ? nSearches() : 0;
		if (isFacet) {
			result += getNonImpliedRestrictions().nRestrictions();
		}
		if (isInformediaQueries) {
			result += nInformediaQueries();
		}
		return result;
	}

	public boolean isTopLevel(final int facetID) {
		return facetID <= nAttributes;
	}

	/**
	 * Called only when initializing or editing. There will be few calls, so try
	 * to avoid prefetching. 2016/03: Should have a new DB command, to avoid
	 * importing all siblings.
	 *
	 * @param name1
	 *            [case-insensitive] name of facet to find
	 * @param parent
	 *            parent of facet to find, or null for top-level Perspectives.
	 * @return the facet with this name and parent
	 */
	@Nullable
	Perspective findPerspective(final @NonNull String name1, final @Nullable Perspective parent) {
		Perspective result = null;
		if (parent == null || parent.prefetchStatus() == PrefetchStatus.PREFETCHED_YES || !parent.isAlphabetic()) {
			Collection<Perspective> siblings;
			if (parent == null) {
				siblings = facetTypes;
			} else {
				if (!parent.isPrefetched(PrefetchStatus.PREFETCHED_YES)) {
					lockAndPrefetch(parent, FetchType.PREFETCH_FACET_WITH_NAME);
					assert parent.isPrefetched(PrefetchStatus.PREFETCHED_YES);
				}
				siblings = parent.getChildrenRaw();
			}
			for (final Perspective child : siblings) {
				final String childName = child.getNameIfCached();
				if (childName.equalsIgnoreCase(name1)) {
					// ignore case for the benefit of LetterLabeled
					result = child;
					break;
				}
			}
		} else {
			// This doesn't require prefetching and hopefully is faster.
			// It does access the database (in the mouse process), and
			// only works if parent.isAlphabetic()
			final String lowerCaseName = name1.toLowerCase();
			assert lowerCaseName != null;
			result = parent.firstWithPrefix(lowerCaseName, null);
			assert result != null : parent + "'" + name1;
		}
		return result;
	}

	/**
	 * Note: ignores restrictData.
	 */
	public boolean computeIsIntensionallyRestricted() {
		boolean result = nSearches() > 0 || nInformediaQueries() > 0;

		for (final Perspective displayedPerspective : displayedPerspectives) {
			if (result |= displayedPerspective.isRestricted()) {
				break;
			}
		}
		isIntensionallyRestricted = result;
		return result;
	}

	/**
	 * @return allRestrictions() > 0
	 */
	public boolean isIntensionallyRestricted() {
		return isIntensionallyRestricted;
	}

	/**
	 * @return 0 < onCount < totalCount
	 */
	public boolean isPartiallyRestricted() {
		return getOnCount() > 0 && isExtensionallyRestricted();
	}

	/**
	 * @return onCount < totalCount
	 */
	public boolean isExtensionallyRestricted() {
		return getOnCount() < getTotalCount();
	}

	/**
	 * rs: [name, descriptionCategory, descriptionPreposition, nChildren,
	 * childrenOffset, flags]
	 *
	 * @return the number of FacetTypes
	 */
	private int initFacetTypes() {
		// UtilString.indentMore("Query.initFacetTypes: ");
		// final long start = Util.now();
		int facetTypeID = 0;
		try (ResultSet rs = db.initPerspectives();) {
			while (rs.next()) {
				final DescriptionCategory descriptionCategory = DescriptionCategory
						.valueOf(rs.getString(2).toUpperCase());
				assert descriptionCategory != null;
				final String descriptionPreposition = rs.getString(3);
				assert descriptionPreposition != null;
				final String _name = rs.getString(1);
				assert _name != null;
				final Perspective facetType = new Perspective(++facetTypeID, _name, rs.getInt(5), rs.getInt(4),
						descriptionCategory, descriptionPreposition, this, rs.getInt(6));
				displayedPerspectives.add(facetType);
				facetType.setIsDisplayed(true);
				// facetType.initChildren(childrenRS, FetchType.PREFETCH_FACET);
				// if (!facetType.isPrefetchedToDefaultLevel()) {
				// queuePrefetch(facetType, null);
				// }
			}
			prefetchFacets(displayedPerspectives);
			// perspectivesToAdd.addAll(displayedPerspectives);
		} catch (final Throwable se) {
			se.printStackTrace();
		}
		// UtilString.indentLess("Query.initFacetTypes return after "
		// + Util.elapsedTime(start));
		return facetTypeID;
	}

	/**
	 * Add a Perspective and/or RedrawCallback to the prefetch queue.
	 * (RedrawCallbacks should be queued after the Perspective they should run
	 * on. If RedrawCallback is already on the queue, the unique property will
	 * remove and re-add it, preserving this property.)
	 *
	 * @return whether queue changed, that is, p isn't prefetched and isn't
	 *         already queued.
	 */
	public boolean queuePrefetch(final @NonNull Perspective facet, final @NonNull FetchType fetchType,
			final @Nullable RedrawCallback redrawCallback) {
		assert insideLetterLabeledZoom > 0 || facet.isDisplayed() : facet.queuePrefetchErrMsg(fetchType);
		final boolean result = !facet.isPrefetched(Perspective.prefetchedStatusFromFetchType(fetchType))
				&& prefetcher.add(facet, fetchType, redrawCallback);

		// System.out.println("Query.queuePrefetch " + facet + " FetchType=" +
		// fetchType + " current status="
		// + facet.prefetchStatus() + " redrawCallback=" + redrawCallback + "
		// isQueryValid=" + isQueryValid()
		// + "; result=" + result);

		return result;
	}

	void prefetchFacets(final @NonNull Collection<Perspective> facets) {
		final Collection<PrefetchSpec> prefetchSpecs = new ArrayList<>(facets.size());
		for (final Perspective facet : facets) {
			final FetchType fetchType = facet.getFetchType();
			if (!facet.isPrefetched(Perspective.prefetchedStatusFromFetchType(fetchType))) {
				prefetchSpecs.add(new PrefetchSpec(facet, fetchType));
			}
		}
		prefetchFacetsFromPrefetchSpecs(prefetchSpecs);
	}

	// static @NonNull Collection<PrefetchSpec> getPrefetchSpecs(final @NonNull
	// Collection<Perspective> facets) {
	// final Collection<PrefetchSpec> prefetchSpecs = new
	// ArrayList<>(facets.size());
	// for (final Perspective facet : facets) {
	// final FetchType fetchType = facet.getFetchType();
	// if
	// (!facet.isPrefetched(Perspective.prefetchedStatusFromFetchType(fetchType)))
	// {
	// prefetchSpecs.add(new PrefetchSpec(facet, fetchType));
	// }
	// }
	// return prefetchSpecs;
	// }

	void prefetchFacetsFromPrefetchSpecs(final @NonNull Collection<PrefetchSpec> prefetchSpecs) {
		if (prefetchSpecs.size() > 0) {
			final Map<FetchType, SortedSet<Perspective>> facetsByFetchType = new IdentityHashMap<>();
			for (final PrefetchSpec spec : prefetchSpecs) {
				final Perspective facet = spec.p;
				final FetchType fetchType = spec.fetchType;
				if (!facet.isPrefetched(Perspective.prefetchedStatusFromFetchType(fetchType))) {
					final SortedSet<Perspective> perspectives = UtilArray.ensureMapSortedSetValue(facetsByFetchType,
							fetchType);
					final boolean added = perspectives.add(facet);
					assert added;
				}
			}
			if (facetsByFetchType.size() > 0) {
				synchronized (prefetchingLock) {
					for (final Map.Entry<FetchType, SortedSet<Perspective>> entry : facetsByFetchType.entrySet()) {
						final FetchType fetchType = entry.getKey();
						assert fetchType != null;
						final SortedSet<Perspective> toPrefetch = entry.getValue();
						assert toPrefetch != null;
						// Need to synchronize prefetches so no one else
						// prefetches after we check prefetch status.
						final PrefetchStatus prefetchedStatusFromFetchType = Perspective
								.prefetchedStatusFromFetchType(fetchType);
						// in case it was queued with multiple fetchTypes
						for (final Iterator<Perspective> it = toPrefetch.iterator(); it.hasNext();) {
							final Perspective facet = it.next();
							if (facet.isPrefetched(prefetchedStatusFromFetchType)) {
								it.remove();
							}
						}
						if (toPrefetch.size() > 0) {
							prefetch(toPrefetch, fetchType, false);
						}
					}
				}
			}
		}
	}

	public void lockAndPrefetch(final @NonNull Perspective p, final @NonNull FetchType fetchType) {
		synchronized (prefetchingLock) {
			if (!p.isPrefetched(Perspective.prefetchedStatusFromFetchType(fetchType))) {
				final SortedSet<Perspective> facets = new TreeSet<>();
				facets.add(p);
				// assert facets != null;
				prefetch(facets, fetchType, false);
			}
		}
	}

	void prefetch(final @NonNull SortedSet<Perspective> perspectives, final @NonNull FetchType fetchType,
			final boolean forEditing) {
		assert perspectives.size() > 0;
		assert forEditing || assertIsPrefetchable(perspectives, fetchType);
		try {
			final ResultSet[] rss = db.prefetch(getItemPredicateIDs(perspectives), fetchType);
			try (ResultSet parentRS = rss[0]; ResultSet childRS = rss[1];) {
				assert parentRS != null && childRS != null;
				final FetchType perspectiveFetchType = Perspective.ensureFetchTypeForUnrestrictedData(fetchType);
				for (final Perspective perspective : perspectives) {
					synchronized (perspective) {
						perspective.initAfterPrefetch(parentRS, childRS, perspectiveFetchType);
						perspective.notifyAll();
					}
				}
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	private static boolean assertIsPrefetchable(final @NonNull Collection<Perspective> perspectives,
			final @NonNull FetchType fetchType) {
		final PrefetchStatus desiredStatus = Perspective.prefetchedStatusFromFetchType(fetchType);
		for (final Perspective p : perspectives) {
			assert !p.isPrefetched(desiredStatus) : p.queuePrefetchErrMsg(fetchType);
		}
		return true;
	}

	public static boolean isPrefetching() {
		return Thread.currentThread().getName().equals("Prefetcher");
	}

	protected @NonNull OnItemsTable onItemsTable() {
		// can't call isExtensionallyRestricted, because query is invalid
		return isIntensionallyRestricted() ? OnItemsTable.ONITEMS
				: isRestrictedData() ? OnItemsTable.RESTRICTED : OnItemsTable.ITEM_ORDER_HEAP;
	}

	/**
	 * Only called in thread QueryUpdater.
	 *
	 * Update onCounts for all Perspectives and set query valid.
	 *
	 * @param selectedItem
	 *            Tell database what item we want to keep track of
	 * @param nThumbs
	 *            Tell database how many neighbors of selectedItem we want to
	 *            keep track of. If > 0, database will return a table of items
	 *            within nNeighbors [inclusive; db will add 1 since Servlet is
	 *            exclusive] on both sides, and return a new selected item if
	 *            the old one no longer satisfies the query.
	 * @return Argument for Bungee.setSelectedItem: [offset for correctedItem,
	 *         correctedItem] If item is no longer in onItems, corrected item
	 *         will be different from item.
	 */
	public @NonNull int[] updateOnItems(final @Nullable Item selectedItem, final int nThumbs) {
		final int selectedItemID = selectedItem != null ? selectedItem.getID() : -1;
		final int[] offsetNitem = db.updateOnItems(querySQL.onItemsQuery(null), selectedItemID, onItemsTable(), nThumbs,
				version);
		onCount = offsetNitem[2];
		assert onCount == 0 || (onCount < 0 && selectedItem == null) || offsetNitem[0] >= 0 : UtilString
				.valueOfDeep(offsetNitem);
		if (onCount < 0) {
			onCount = totalCount;
		}
		prefetchFacets(displayedPerspectives);
		if (onCount == 0) {
			resetForNewData();
		} else if (onCount == totalCount) {
			System.arraycopy(perspectiveTotalCounts, 0, perspectiveOnCounts, 0, perspectiveTotalCounts.length);
		} else {
			updateFilteredCounts();
		}
		setQueryValid(true);
		return offsetNitem;
	}

	private void updateFilteredCounts() {
		resetForNewData();
		try (final ResultSet rs = db.filteredCounts(onItemsTable());) {
			while (rs.next()) {
				final int facetID = rs.getInt(1);
				final int _onCount = rs.getInt(2);
				perspectiveOnCounts[facetID] = _onCount;
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set onCount=0 for children of displayedPerspectives prior to setting
	 * counts for non-zero onCounts
	 */
	private void resetForNewData() {
		// Update onCounts while query is still invalid. Will become
		// valid immediately afterward.
		Arrays.fill(perspectiveOnCounts, 0);
		perspectiveOnCountsVersion = version;
	}

	/**
	 * Uses perspectiveOnCounts. Never calls db.
	 *
	 * Make sure query is valid before calling this.
	 *
	 * @return always >= 0
	 */
	public int getFacetOnCount(final @NonNull Perspective facet) {
		return getFacetOnCount(facet.getID());
	}

	/**
	 * Uses perspectiveOnCounts. Never calls db.
	 *
	 * Make sure query is valid before calling this.
	 *
	 * @return always >= 0
	 */
	public int getFacetOnCount(final int facetID) {
		assert isQueryValid();
		int result = -1;
		if (perspectiveOnCountsVersion == version) {
			result = perspectiveOnCounts[facetID];
		} else if (onCount == 0) {
			result = 0;
		} else {
			assert !isExtensionallyRestricted() : perspectiveOnCountsVersion + " " + version + " " + this;
			result = getFacetTotalCount(facetID);
		}
		assert result >= 0;
		return result;
	}

	/**
	 * @return always >= 0
	 */
	public int getFacetTotalCount(final @NonNull Perspective facet) {
		return getFacetTotalCount(facet.getID());
	}

	/**
	 * @return always >= 0
	 */
	public int getFacetTotalCount(final int facetID) {
		final int result = perspectiveTotalCounts[facetID];
		assert result >= 0;
		return result;
	}

	public void setFacetOnCount(final @NonNull Perspective facet, final int _onCount) {
		assert assertInRange(_onCount, 0, onCount);
		final int facetID = facet.getID();
		perspectiveOnCounts[facetID] = _onCount;
	}

	public void setFacetTotalCount(final @NonNull Perspective facet, final int _totalCount) {
		assert facet.getParent() == null
				|| _totalCount <= Util.nonNull(facet.getParent()).getTotalCount() : "new totalCount=" + _totalCount
						+ " query.getTotalCount()=" + totalCount + " isRestrictedData=" + isRestrictedData() + "\n"
						+ facet.path(true, true);
		setFacetTotalCount(facet.getID(), _totalCount);
	}

	public void setFacetTotalCount(final int facetID, final int _totalCount) {
		assert assertInRange(_totalCount, 0, totalCount);
		assert assertInRange(facetID, 1, nPerspectivesRaw());
		perspectiveTotalCounts[facetID] = _totalCount;
	}

	private final @NonNull Set<Perspective> queuedCountsIgnoringFacets = new HashSet<>();

	/**
	 * @return children's onCounts as if there were no filters on parent:
	 *
	 *         rs: [child, onCount]
	 *
	 *         Returned value is null iff callback == DUMMY_REDRAW_CALLBACK or
	 *         callback has been queued.
	 */
	public @Nullable MyResultSet getOnCountsIgnoringFacet(final @NonNull Perspective parentToIgnore,
			final @NonNull PerspectiveList perspectiveList, final @Nullable RedrawCallback callback) {
		MyResultSet rs = null;
		if (callback == null) {
			final String subQuery = querySQL.onItemsQuery(parentToIgnore);
			assert subQuery != null : "PerspectiveList.getSelectedOnCounts should have detected this case";
			rs = db.onCountsIgnoringFacet(subQuery, parentToIgnore.getID());
			assert rs != null;
		} else {
			// assert callback != DummyRedrawCallback.DUMMY_REDRAW_CALLBACK;
			// It's not worth the time. (usually from UserAction.isZeroHits().)
			// System.out.println("Query.getOnCountsIgnoringFacet " +
			// parentToIgnore + " callback=" + callback);

			if (onCountsIgnoringFacetGetter == null) {
				onCountsIgnoringFacetGetter = new OnCountsIgnoringFacetGetter(this, perspectiveList);
				new Thread(onCountsIgnoringFacetGetter).start();
			}
			onCountsIgnoringFacetGetter.add(queuedCountsIgnoringFacets.contains(parentToIgnore) ? null : parentToIgnore,
					callback);
			queuedCountsIgnoringFacets.add(parentToIgnore);
		}
		return rs;
	}

	@NonNull
	String sortColumnName() {
		return Compile.sortColumnName(sortedByFacetTypeIdOrSpecial);
	}

	public @Nullable Perspective sortedBy() {
		return sortedByFacetTypeIdOrSpecial > 0 ? findPerspectiveOrError(sortedByFacetTypeIdOrSpecial) : null;
	}

	/**
	 * Remove all filters. Does not updateQuery()
	 */
	public void clear() {
		for (final Perspective facetType : facetTypes) {
			facetType.clearPerspective();
		}
		searches.clear();
		informediaQueries.clear();
		isIntensionallyRestricted = false;
		assert !computeIsIntensionallyRestricted();
	}

	/**
	 * @param searchText
	 *            add a filter on searchText
	 */
	public void addTextSearch(final @NonNull String searchText) {
		// assert UtilString.isNonEmptyString(s);
		assert isIllegalSearch(searchText) == null : searchText + "\n" + isIllegalSearch(searchText);
		searches.add(searchText);
		// System.out.println("Query.addTextSearch " + searchText + " ⇒ " +
		// searches);
	}

	/**
	 * @param s
	 *            filter to remove
	 * @return was anything removed?
	 */
	public boolean removeTextSearch(final @NonNull String s) {
		final boolean result = searches.remove(s);
		return result;
	}

	/**
	 * @return the set of Strings being filtered on
	 */
	public @NonNull Set<String> getSearches() {
		// System.out.println("Query.getSearches ⇒ " + searches);
		return searches;
	}

	/**
	 * @return the number of text search filters
	 */
	public int nSearches() {
		return searches.size();
	}

	/**
	 * @return MyResultSet[keyString, facetID] where keyString is a lower-case
	 *         String of length 1, and facetID is the last child of facet whose
	 *         lower-cased name begins with prefix+keyString.
	 */
	public @NonNull MyResultSet getLetterOffsets(final @NonNull Perspective facet, final @NonNull String prefix) {
		assert Thread.currentThread().getName().equals("LetterOffsetsCreator") || java.awt.EventQueue.isDispatchThread()
				&& insideLetterLabeledZoom > 0 : "Query.getLetterOffsets calling DB in "
						+ Thread.currentThread().getName() + ": facet=" + facet + " prefix='" + prefix + "'";

		// System.out.println("Query.getLetterOffsets " + facet + " '" + prefix
		// + "'\n" + UtilString.getStackTrace());
		return db.letterOffsets(facet.getID(), prefix);
	}

	/**
	 * Add p to displayedPerspectives.
	 */
	public void display(final @NonNull Perspective p) {
		// Might be displayed even if not part of query
		if (!p.isDisplayed()) {
			synchronized (displayedPerspectivesLock) {
				assert p.isEffectiveChildren() : p;
				displayedPerspectives.add(p);
				p.setIsDisplayed(true);
				p.ensurePrefetched(PrefetchStatus.PREFETCHED_YES, null);
			}
		}
	}

	public void undisplay(final @NonNull Perspective p) {
		synchronized (displayedPerspectivesLock) {
			displayedPerspectives.remove(p);
			p.setIsDisplayed(false);
			assert !displayedPerspectives.isEmpty() : this;
		}
	}

	public @NonNull SortedSet<Perspective> displayedPerspectives() {
		return displayedPerspectives;
	}

	public @NonNull SortedSet<Perspective> nonTopLevelDisplayedPerspectives() {
		final SortedSet<Perspective> result = new TreeSet<>(displayedPerspectives);
		result.removeAll(facetTypes);
		return result;
	}

	/**
	 * Called by RangeEnsurer and Informedia
	 *
	 * offsets are zero-based, but db is one-based.
	 */
	public @NonNull ResultSet offsetItems(final int minOffsetInclusive, final int maxOffsetExclusive) {
		// System.out.println("offsetItems [" + minOffsetInclusive + "-"
		// + maxOffsetExclusive + "> " + onItemsTable());
		return db.offsetItems(minOffsetInclusive, maxOffsetExclusive, onItemsTable(), version);
	}

	public void reorderItems(final int facetTypeOrSpecial) {
		db.reorderItems(facetTypeOrSpecial);
	}

	public @NonNull ResultSet[] getThumbs(final @NonNull Collection<Item> items, final int imageW, final int imageH) {
		return db.thumbs(getItemIDs(items), imageW, imageH, BungeeConstants.THUMB_QUALITY);
	}

	public @NonNull ResultSet getDescAndImage(final @NonNull Item item, final int imageW, final int imageH,
			final int quality) throws SQLException {
		final ResultSet rs = db.descAndImage(item.getID(), imageW, imageH, quality);
		assert rs != null;
		assert ((MyResultSet) rs).nRows() == 1 : MyResultSet.valueOfDeep(rs, 10);
		return rs;
	}

	private final @NonNull Map<String, List<Perspective>> topMutInfCache = new HashMap<>();

	/**
	 * @return best candidates, sorted from best to worst. Can be empty.
	 */
	public @NonNull List<Perspective> topCandidates(final @NonNull String itemPredsExpr) {
		assert !isRestrictedData;
		List<Perspective> result = topMutInfCache.get(itemPredsExpr);
		if (result == null) {
			final ResultSet[] rss = db.topCandidates(itemPredsExpr, Explanation.MAX_CANDIDATES);
			try (
					// SMINT_PINT_STRING_INT_INT
					final ResultSet rs1 = rss[1];

					// PINT
					final ResultSet rs0 = rss[0];) {
				assert rs1 != null;
				importFacets(rs1);

				final int nRows = MyResultSet.nRows(rs0);
				result = new ArrayList<>(nRows);
				while (rs0.next()) {
					result.add(findPerspectiveOrError(rs0.getInt(1)));
				}
				assert result.size() == nRows;
				// result = importFacets(rs0);
				topMutInfCache.put(itemPredsExpr, result);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
		return new ArrayList<>(result);
	}

	public @Nullable Perspective importFacet(final int facetID) {
		if (isZombie(facetID)) {
			return null;
		}
		Perspective result = allPerspectives[facetID];

		// if getTotalCount()==0, restrictData has made it a zombie; ignore.
		if (result == null || result.needsChildrenOffset()) {
			final String facetIDstring = Integer.toString(facetID);
			assert facetIDstring != null;
			try (final ResultSet rs = db.importFacets(facetIDstring, isRestrictedData());) {
				final List<Perspective> importedFacets = importFacets(rs);
				if (importedFacets.size() > 0) {
					result = importedFacets.get(0);
				} else {
					System.err.println("Query.importFacet " + facetID + " failed. isRestrictedData()="
							+ isRestrictedData() + " nRows(rs)=" + MyResultSet.nRows(rs));
				}
			} catch (final SQLException e) {
				System.err.println("While Query.importFacet " + facetID + " result=" + result
						+ (result != null ? " result.getTotalCount=" + result.getTotalCount() : ""));
				e.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * @param rs
	 *            [facet_id, parent_facet_id, name, n_child_facets,
	 *            first_child_offset]
	 *
	 *            or if caller knows that everything is already imported and
	 *            just needs to convert IDs to Perspectives, just
	 *
	 *            [facet_id]
	 *
	 * @return Perspectives in the order returned by Database
	 */
	private @NonNull List<Perspective> importFacets(final @NonNull ResultSet rs) throws SQLException {
		final List<Perspective> result = new ArrayList<>(MyResultSet.nRows(rs));
		while (rs.next()) {
			final int childID = rs.getInt(1);
			Perspective child = findPerspectiveIfCached(childID);
			if (child == null || child.needsChildrenOffset() || child.getNameIfCached() == null) {
				final int parentID = rs.getInt(2);
				assert parentID > 0 : child + " " + childID;
				Perspective parent = findPerspectiveIfCached(parentID);
				if (parent == null || parent.needsChildrenOffset() || parent.getTotalCount() < 0) {
					parent = importFacet(parentID);
					assert parent != null && !parent.needsChildrenOffset() : parent + " " + parentID;
				}
				child = parent.ensureChild(childID, rs.getString(3), rs.getInt(5), rs.getInt(4));
				assert rs.getInt(4) == 0 || !child.needsChildrenOffset() : child + " first_child_offset=" + rs.getInt(5)
						+ " n_child_facets=" + rs.getInt(4);

				// final int childTotalCount = rs.getInt(6);
				// assert childTotalCount > 0 : child.path(false, true);
				// assert childTotalCount <= totalCount : child + " totalCount
				// (" + childTotalCount
				// + ") is greater than queryTotalCount (" + totalCount + ")";
				// child.setTotalCount(childTotalCount);
			}
			result.add(child);
		}
		// UtilString.indent("Query.importFacets imported " + result.size() + "
		// facets.");
		return result;
	}

	/**
	 * Only called by Item.getOffset.
	 *
	 * Calls Database.
	 *
	 * @return offset or -1 if it doesn't satisfy query.
	 */
	public int itemOffset(final @NonNull Item item, final int nNeighbors) {
		assert nNeighbors >= 0;
		int offset = -1;
		if (getOnCount() > 0) {
			final int[] offsetNitem = db.itemOffset(item.getID(), onItemsTable(), nNeighbors, version);
			offset = item.getID() == offsetNitem[1] ? offsetNitem[0] : -1;
		}
		return offset;
	}

	public @NonNull int[] itemIndexFromURL(final @NonNull String urlString) {
		return db.itemIndexFromURL(urlString, onItemsTable(), version);
	}

	/**
	 * Only called by FacetTree.<init>
	 *
	 * @return rows are [facet_id, parent_facet_id, name, n_child_facets,
	 *         first_child_offset, total_count]
	 */
	public @NonNull ResultSet getItemInfo(final @NonNull Item item) {
		return db.itemInfo(item.getID());
	}

	public @Nullable String getItemURL(final @NonNull Item item) {
		return isShowDocuments() ? db.itemURL(item.getID()) : null;
	}

	public boolean isShowDocuments() {
		return UtilString.isNonEmptyString(itemURLdoc);
	}

	private static final @NonNull Pattern TERM_PATTERN = termPattern();

	private static @NonNull Pattern termPattern() {
		final String plusMinusPrefix = "\\G(-|\\+)?";
		final String suffix = "(\\s++|\\z)";
		final String nonCaptureSuffix = "(?:\\s++|\\z)";

		// (?:X) X, as a non-capturing group
		// X++, X*+ Possessive quantifiers
		// X+? Reluctant quantifiers
		// \S A non-whitespace character
		// \G The end of the previous match
		// \z The end of the input

		final String quotedTerm = plusMinusPrefix + "(\".+?\")" + suffix;
		final String parenthesizedTerm = plusMinusPrefix + "(\\(.+?\\))" + suffix;
		// alphaNumeric or ' or _, optionally followed by '*'
		// final String alphaNumeric = plusMinusPrefix +
		// "([\\p{IsAlphabetic}\\p{IsDigit}'_]++)(\\*)?" + nonCaptureSuffix;
		final String alphaNumeric = plusMinusPrefix + "(['\\w]+)(\\*)?" + nonCaptureSuffix;
		final String error = "\\G(\\S++)(\\s*+)(\\s*+)";
		final Pattern result = Pattern.compile(quotedTerm + "|" + parenthesizedTerm + "|" + alphaNumeric + "|" + error);
		assert result != null;
		return result;
	}

	/**
	 * Use only letters, digits, space, tab, _, ', +, -, *, (, ), and \".
	 */
	private static final @NonNull Pattern ILLEGAL_CHAR_PATTERN = Util
			.nonNull(Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit} \t'_+\\-*()\"]"));

	/**
	 * + and - must immediately precede a word, parenthesis, or quotation mark
	 */
	private static final @NonNull Pattern PREFIX_POSITION_PATTERN = Util
			.nonNull(Pattern.compile("(?:-|\\+)[^\\p{IsAlphabetic}\\p{IsDigit}'_()\"]"));

	// private static final Pattern ILLEGAL_ASTERISK_PATTERN = Pattern
	// .compile("[^\\p{IsAlphabetic}\\p{IsDigit}'_]\\*");

	/**
	 * "*" must immediately follow a word
	 */
	private static final @NonNull Pattern ILLEGAL_ASTERISK_PATTERN = getIllegalAsteriskPattern();

	private static @NonNull Pattern getIllegalAsteriskPattern() {
		final String illegalAsteriskPrefix = "(?:\\W|\\A)\\*";
		final String illegalAsteriskSuffix = "\\*\\w";
		final Pattern result = Pattern.compile("(?:" + illegalAsteriskPrefix + ")|(?:" + illegalAsteriskSuffix + ")");
		assert result != null;
		return result;
	}

	/**
	 * @return error message if searchText is an illegal search, or null if it
	 *         is legal.
	 */
	public static @Nullable String isIllegalSearch(final @Nullable String searchText) {
		String result = null;
		if (!UtilString.isNonEmptyString(searchText)) {
			result = "Search words must have at least 4 characters";
		} else if (ILLEGAL_CHAR_PATTERN.matcher(searchText).find()) {
			result = "Use only letters, digits, spaces, tabs, _, ', +, -, *, (, ), and \".";
		} else if (PREFIX_POSITION_PATTERN.matcher(searchText).find()) {
			result = "+ and - must immediately precede a word, parenthesis, or quotation mark";
		} else if (ILLEGAL_ASTERISK_PATTERN.matcher(searchText).find()) {
			result = "* must immediately follow a word";
		} else {
			boolean positive = false;
			final Matcher m = TERM_PATTERN.matcher(searchText);
			while (m.find() && result == null) {
				int groupOffset = 0;
				while (m.group(2 + groupOffset) == null && groupOffset <= 9) {
					// Each of the 4 disjuncts in TERM_PATTERN have 3 capturing
					// groups, and we have to check all 4 (groupOffset=0,3,6,9)
					// to see which matched.
					groupOffset += 3;
				}
				final String plusMinusPrefix = m.group(1 + groupOffset); // optional
				final String term = m.group(2 + groupOffset); // required
				// optional, only applicable for alphaNumeric disjunct
				final boolean isAsteriskSuffix = "*".equals(m.group(3 + groupOffset));
				if (groupOffset == 9 || !UtilString.isNonEmptyString(term)) {
					result = "Illegal construct: " + plusMinusPrefix;
				} else if (term.length() < 4 && !isAsteriskSuffix) {
					result = "Search words must have at least 4 characters: " + term;
					// } else if (term.matches("\".*[+\\-*()].*")) {
				} else if (term.startsWith("\"") && UtilString.containsAny(term, "+-*()")) {
					result = "Use only letters, digits, spaces, _, and ' inside quotation marks.";
				} else {
					positive |= !"-".equals(plusMinusPrefix);
				}
			}
			if (result == null && !positive) {
				result = "You have to have at least one term that doesn't have a '-' in front of it.";
			}
		}
		return result;
	}

	/**
	 * @return whether restrictions and searches are consistent with onCounts.
	 *         set to invalid by Bungee.updateAllData and to valid by
	 *         Query.updateData
	 */
	public boolean isQueryValid() {
		return externalVersion > 0;
	}

	public boolean isQueryVersionCurrent(final int onCountQueryVersion) {
		return (onCountQueryVersion == externalVersion) && onCountQueryVersion > 0;
	}

	public int version() {
		return externalVersion;
	}

	public void setQueryValid(final boolean isValid) {
		// pick any *private* final object to synchronize on
		synchronized (queryValidLock) {
			assert isValid == !isQueryValid() : "isQueryValid() is already " + isValid; // mad
																						// 10/15/13
			if (isValid) {
				externalVersion = version;
				queryValidLock.notifyAll();
			} else {
				externalVersion = -1;
				version++;
				queuedCountsIgnoringFacets.clear();
			}
			System.out.println("Query.setQueryValid externalVersion=" + externalVersion + " onCount/totalCount="
					+ onCount + "/" + totalCount);
		}
	}

	/**
	 * @return isAlive() (and query is valid)
	 */
	public boolean waitForValidQuery() {
		// System.out.println("Query.waitForValidQuery");
		if (!isQueryValid()) {
			assert Util.assertNotMouseProcess() : "Don't block mouse process";
			synchronized (queryValidLock) {
				while (!isQueryValid()) {
					try {
						queryValidLock.wait();
					} catch (final InterruptedException e) {
						// Our wait is over
					}
				}
			}
			assert isQueryValid();
			// System.out.println("....waitForValidQuery return");
		}
		return isAlive();
	}

	/**
	 * @param minFacetID
	 *            inclusive
	 * @param maxFacetID
	 *            inclusive
	 * @param direction
	 *            +1 or -1
	 * @return return first Perspective with totalCount > 0, searching in
	 *         direction.
	 */
	public @Nullable Perspective findFirstEffectivePerspective(final int minFacetID, final int maxFacetID,
			final int direction) {
		assert Math.abs(direction) == 1 : direction;
		Perspective result = null;
		final int startFacetID = direction > 0 ? minFacetID : maxFacetID;
		final int endFacetID = direction > 0 ? maxFacetID : minFacetID;
		for (int facetID = startFacetID; facetID - direction != endFacetID; facetID += direction) {
			result = findPerspectiveNow(facetID);
			if (result != null) {
				assert result.getTotalCount() > 0 : result;
				break;
			}
		}
		return result;
	}

	private enum IfNotFoundAction {
		@SuppressWarnings("hiding") @NonNull ERROR, @NonNull NOTHING, @NonNull GET_NOW
	}

	public @NonNull Perspective findPerspectiveOrError(final int facetID) {
		final Perspective result = findPerspective(facetID, IfNotFoundAction.ERROR);
		assert result != null;
		return result;
	}

	public @Nullable Perspective findPerspectiveIfCached(final int facetID) {
		return findPerspective(facetID, IfNotFoundAction.NOTHING);
	}

	public @Nullable Perspective findPerspectiveNow(final int facetID) {
		return findPerspective(facetID, IfNotFoundAction.GET_NOW);
	}

	private @Nullable Perspective findPerspective(final int facetID, final @NonNull IfNotFoundAction ifNotFoundAction) {
		assert assertInRange(facetID, 1, nPerspectivesRaw()) : facetID;
		Perspective result = allPerspectives[facetID];
		// if (isZombie(result)) {
		// // System.err.println("Query.findPerspective: " + result + " is a
		// // zombie.");
		// result = null;
		// }
		if (result == null || isZombie(facetID)) {
			switch (ifNotFoundAction) {
			case ERROR:
				assert false : result == null ? "Can't find perspective " + facetID : result + " has totalCount 0";
				break;
			case GET_NOW:
				result = importFacet(facetID);
				break;
			case NOTHING:
				result = null;
				break;
			default:
				assert false : ifNotFoundAction;
				break;
			}
		}
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// boolean isZombie(final int facetID) {
	// return getFacetTotalCount(facetID) == 0;
	// }

	boolean isZombie(final int facetID) {
		return getFacetTotalCount(facetID) == 0;
	}

	/**
	 * @Includes zombies.
	 */
	public int nPerspectivesRaw() {
		return allPerspectives.length - 1;
	}

	/**
	 * @param perspective
	 *            only null if editing
	 */
	public void cachePerspective(final int facet_id, final @Nullable Perspective perspective) {
		allPerspectives[facet_id] = perspective;
	}

	public void restrictData() {
		baseName += " / " + markupToText(description(), null);
		// System.out.println("Query.restrictData " + name);
		System.arraycopy(perspectiveOnCounts, 0, perspectiveTotalCounts, 0, perspectiveOnCounts.length);
		for (int i = allPerspectives.length - 1; i >= 0; i--) {
			final Perspective facet = allPerspectives[i];
			if (facet != null) {
				facet.restrictData();
			}
		}

		setQueryValid(false);
		isRestrictedData = true;

		totalCount = onCount;
		clear();
		decacheDistributions();
		db.restrict();
		isIntensionallyRestricted = false;
		assert !computeIsIntensionallyRestricted();
		setQueryValid(true);
	}

	// Only called by Informedia
	public void addInformediaQuery(final @NonNull InformediaQuery informediaQuery) {
		informediaQueries.add(informediaQuery);
	}

	public void setItemDescription(final @NonNull Item item, final @NonNull String description) {
		db.setItemDescription(item.getID(), description);
	}

	public @NonNull String[][] getDatabases() {
		return db.databaseDescs;
	}

	public @NonNull String[] opsSpec(final int session) {
		return db.opsSpec(session);
	}

	// public static class RandomOpsSpec {
	// public final String session;
	// public final String[] ops;
	//
	// RandomOpsSpec(final String _session, final String[] _ops) {
	// session = _session;
	// ops = _ops;
	// }
	// }

	public @NonNull String randomSession() {
		return db.randomOpsSpec();
	}

	public @NonNull String getSession() {
		return db.getSession();
	}

	public void loseSession(@SuppressWarnings("unused") final @NonNull String session) {
		// db.loseSession(session);
	}

	public @NonNull String aboutCollection() {
		final String result = db.aboutCollection();
		assert result != null;
		return result;
	}

	@Override
	public int getTotalCount() {
		return totalCount;
	}

	/**
	 * @return always >= 0
	 */
	public int getOnCount() {
		assert isQueryValid();
		return onCount;
	}

	private static boolean maybeInsertSemicolon(final @NonNull StringBuilder buf, final boolean firstTime) {
		if (!firstTime) {
			buf.append(";");
		}
		return false;
	}

	private static boolean insertFacets(final @NonNull StringBuilder buf, boolean firstTime,
			final @NonNull SortedSet<Perspective> restrictions, final boolean polarity) {
		if (restrictions.size() > 0) {
			firstTime = maybeInsertSemicolon(buf, firstTime);
			buf.append(polarity ? "+:" : "-:");
			for (final Perspective p : restrictions) {
				if (p != restrictions.first()) {
					buf.append("|");
				}
				buf.append(p.path(false, false, true));
			}
		}
		return firstTime;
	}

	/**
	 * @return non-implied positive and negative restrictions.
	 */
	public @Immutable @NonNull SortedSet<Perspective> allNonImpliedRestrictions() {
		return getNonImpliedRestrictions().allRestrictions();
	}

	/**
	 * @return highest required and lowest excluded Restrictions. Do not modify.
	 */
	public @NonNull Restrictions getNonImpliedRestrictions() {
		getRestrictions();
		return cachedCanonicalizedRestrictions;
	}

	private final @NonNull Restrictions cachedCanonicalizedRestrictions = new Restrictions();
	private final @NonNull Restrictions cachedRestrictions = new Restrictions();
	private int cachedRestrictionsVersion = -2;

	/**
	 * @return implied and non-implied Restrictions for current query, whether
	 *         valid or not. Do not modify.
	 */
	public @NonNull Restrictions getRestrictions() {
		// Use version rather than externalVersion here.
		if (cachedRestrictionsVersion != version) {
			cachedRestrictions.clear();
			cachedCanonicalizedRestrictions.clear();
			for (final Perspective child : displayedPerspectives()) {
				final Restrictions restrictionsOrNull = child.restrictionsOrNull();
				cachedRestrictions.add(restrictionsOrNull);
				cachedCanonicalizedRestrictions.add(restrictionsOrNull);
			}
			cachedCanonicalizedRestrictions.canonicalize();
			cachedRestrictionsVersion = version();
		}
		return cachedRestrictions;
	}

	// public void traceServletCalls(final boolean isTrace) {
	// db.isTraceServletCalls = isTrace;
	// }

	/**
	 * Ignores restrictData.
	 *
	 * @return The argument for "&query=": a representation of the current
	 *         filters from which Bungee.setInitialState can recreate them.
	 */
	public @NonNull String bookmark() {
		boolean isQueryEmpty = true;
		final StringBuilder buf = new StringBuilder();
		for (final String search : searches) {
			isQueryEmpty = maybeInsertSemicolon(buf, isQueryEmpty);
			buf.append("TextSearch:").append(search);
		}
		for (final Perspective facetType : facetTypes) {
			for (final boolean polarity : Util.BOOLEAN_VALUES) {
				final SortedSet<Perspective> restrictions = facetType.getRestrictionFacetInfos(polarity);
				isQueryEmpty = insertFacets(buf, isQueryEmpty, restrictions, polarity);
			}
		}
		final String result = buf.toString();
		assert result != null;
		return result;
	}

	public void setInitialState(final @NonNull String stateSpec) {
		UtilString.indentMore("Query.setInitialState: " + stateSpec);
		final String[] terms = UtilString.splitSemicolon(stateSpec);
		for (final String term : terms) {
			// UtilString.indent("Query.setInitialState: " + term);
			final String[] x = term.split(":");
			assert x.length == 2;
			final String type = x[0];
			final String searchText = x[1];
			assert searchText != null;
			if (type.equals("TextSearch")) {
				addTextSearch(searchText);
			} else {
				assert type.length() == 1 && "+-".indexOf(type) >= 0 : type;
				final boolean polarity = ("+".equals(type));
				final Set<Perspective> facets = parsePerspectives(searchText);
				for (final Perspective child : facets) {
					if (!child.isRestriction(polarity)) {
						final Perspective parent = child.getParent();
						assert parent != null;
						parent.toggleFacet(null, child, polarity ? InputEvent.CTRL_DOWN_MASK : Util.EXCLUDE_ACTION);
					}
				}
			}
		}
		UtilString.indentLess("Query.setInitialState return");
	}

	/**
	 * Only called while setInitialStatee()
	 *
	 * @param string
	 *            "|"-delimited list of " -- "-delimited Perspective paths
	 */
	public @NonNull Set<Perspective> parsePerspectives(final @NonNull String string) {
		UtilString.indentMore("parsePerspectives: '" + string + "'");
		insideLetterLabeledZoom++;
		final Set<Perspective> result = new HashSet<>();
		final String[] disjuncts = string.split("\\|");
		for (final String disjunct : disjuncts) {
			assert disjunct != null;
			final String[] descendents = Compile.ancestors(disjunct);
			Perspective parent = null;
			for (final String descendentName : descendents) {
				assert descendentName != null;
				final Perspective child = findPerspective(descendentName, parent);
				assert child != null : "'" + parent + "' '" + descendentName + "'";
				parent = child;
			}
			assert parent != null;
			result.add(parent);
		}
		insideLetterLabeledZoom--;
		UtilString.indentLess("parsePerspectives return " + result);
		return result;
	}

	private static final double UNCORRECTED_SIGNIFICANCE_THRESHOLD = 0.01;

	private double bonferroniThreshold = 0.0;
	private double bonferroniMedianThreshold = 0.0;
	private int bonferroniThresholdVersion = -1;

	public double getBonferroniThreshold() {
		if (isQueryValid() && bonferroniThresholdVersion != version()) {
			bonferroniThreshold = UNCORRECTED_SIGNIFICANCE_THRESHOLD / nVisiblePerspectives();
			bonferroniMedianThreshold = UNCORRECTED_SIGNIFICANCE_THRESHOLD / nOrderedAttributes();
			bonferroniThresholdVersion = version();
		}
		return bonferroniThreshold;
	}

	public double correctedPvalue(final double uncorrectedPvalue) {
		return Math.min(1.0, uncorrectedPvalue * UNCORRECTED_SIGNIFICANCE_THRESHOLD / getBonferroniThreshold());
	}

	public double getBonferroniMedianThreshold() {
		getBonferroniThreshold();
		return bonferroniMedianThreshold;
	}

	/**
	 * @return the total number of children of all displayedPerspectives. This
	 *         is an upper bound on the number of visible bars, and a lot
	 *         cheaper to compute.
	 */
	private int nVisiblePerspectives() {
		int result = 0;
		for (final Perspective displayedPerspective : displayedPerspectives) {
			result += displayedPerspective.nChildrenRaw();
		}
		return result;
	}

	/**
	 * @return the number of rows in raw_facet_type whose ordered column is
	 *         true.
	 */
	private double nOrderedAttributes() {
		double n = 0.0;
		for (final Perspective facetType : facetTypes) {
			if (facetType.isOrdered()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Calls Database
	 */
	public @NonNull Item[] getItems(final int minOffset, final int maxOffsetExclusive) {
		Item[] result = null;
		try (final ResultSet rs = offsetItems(minOffset, maxOffsetExclusive);) {
			final int n = maxOffsetExclusive - minOffset;
			result = new Item[n];
			int i = 0;
			// rs can have extra rows if it was cached
			while (rs.next() && i < n) {
				final Item item = Item.ensureItem(rs.getInt(1));
				item.setOffset(minOffset + i, version());
				result[i++] = item;
			}
			assert !UtilArray.hasDuplicates(result) : "startIndex=" + minOffset + " maxOffsetExclusive="
					+ maxOffsetExclusive + " result=" + UtilString.valueOfDeep(result) + "\n"
					+ MyResultSet.valueOfDeep(rs, n);
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	@NonNull
	Runnable getDoPrintUserAction(final int location, final @NonNull String object, final int modifiers) {
		return new Runnable() {

			@Override
			public void run() {
				printUserAction(location, object, modifiers);
			}
		};
	}

	/**
	 * MySQL timestamp granularity is one second, and users can perform more
	 * than one action in that time, so we need to remember the order
	 * explicitly.
	 */
	private int userActionIndex = 0;

	public void printUserAction(final int location, final @NonNull String object, final int modifiers) {
		assert Util.assertMouseProcess() : "Should call this from one thread to ensure order is correct";
		if (userActionIndex > 0 || (onCount == totalCount && !isRestrictedData)) {
			// If Replayer or Bungee.setInitialState leave us in a state where
			// userActionIndex==0 but Replayer won't work, don't record ops.
			final StringBuilder buf = new StringBuilder();
			buf.append(userActionIndex++);
			buf.append(",").append(location);
			buf.append(",").append(object.replace(',', ';'));
			buf.append(",").append(modifiers);
			buf.append(",").append(onCount);
			final String desc = buf.toString();
			// if (object.equals(Replayer.ELLIPSIS)) {
			// System.out.println("Query.printUserAction " + desc + "\n" +
			// UtilString.getStackTrace());
			// }
			assert desc != null;
			db.printUserAction(desc);
		}
	}

	private @NonNull TopTags cachedTopTags = new TopTags(0);
	private int cachedTopTagsVersion = -1;

	/**
	 * @param nLines
	 *            the number of positive (==negative) lines TopTagsViz wants to
	 *            draw.
	 *
	 * @return TopTags structure with positive- and negative-associated
	 *         Perspectives. It will be empty if onCount==totalCount or if there
	 *         will be a callback.
	 */
	public @NonNull TopTags topTags(final int nLines, final @NonNull RedrawCallback callback) {
		assert isQueryValid();
		final TopTags topTags = cachedTopTagsVersion == externalVersion && cachedTopTags.getnLines() >= nLines
				? cachedTopTags : new TopTags(nLines);
		if (onCount < totalCount) {
			boolean areCountsCached = true;
			for (final Perspective facetType : facetTypes) {
				if (facetType.getTotalCount() < 0) {
					areCountsCached = false;
				}
			}
			if (areCountsCached) {
				for (final Perspective facetType : facetTypes) {
					if (!facetType.updateTopTags(topTags, externalVersion, true, callback)) {
						System.err.println("Warning: Query.topTags: updateTopTags(" + facetType
								+ ") not up to date. version=" + externalVersion);
						areCountsCached = false;
					}
				}
			}
			if (!areCountsCached) {
				topTags.clear();
			} else {
				cachedTopTags = topTags;
				cachedTopTagsVersion = externalVersion;
			}
		}
		return topTags;
	}

	public static @NonNull String getItemIDs(final @NonNull Collection<Item> items) {
		final StringBuilder buf = new StringBuilder();
		for (final Item item : items) {
			if (buf.length() > 0) {
				buf.append(",");
			}
			buf.append(item.getID());
		}
		return Util.nonNull(buf.toString());
	}

	static @NonNull String getItemPredicateIDs(final @NonNull Collection<Perspective> facets) {
		final StringBuilder buf = new StringBuilder();
		assert !UtilArray.hasDuplicates(facets);
		for (final Perspective p : facets) {
			if (buf.length() > 0) {
				buf.append(",");
			}
			buf.append(p.getServerID());
		}
		return Util.nonNull(buf.toString());
	}

	/**
	 * @return [BaseCounts, CandidateCounts]
	 *
	 *         BaseCounts: [[state, count], ...]
	 *
	 *         CandidateCounts: [[facet_id, state, count], ...]
	 */
	public @NonNull MyResultSet[] onCountMatrix(final @NonNull Collection<Perspective> facetsOfInterest,
			final @NonNull Collection<Perspective> candidates, final boolean needBaseCounts) {
		assert candidates.size() > 0;
		assert facetsOfInterest.size() <= FacetSelection.MAX_FACET_SELECTION_FACETS : "More than "
				+ FacetSelection.MAX_FACET_SELECTION_FACETS + " facets: " + facetsOfInterest;

		// System.out.println("Query.onCountMatrix " + facetsOfInterest + " " +
		// candidates);
		return db.onCountMatrix(getItemPredicateIDs(facetsOfInterest), getItemPredicateIDs(candidates),
				isRestrictedData(), needBaseCounts);
	}

	/**
	 * Can be called from thread prefetcher
	 */
	public boolean isRestrictedData() {
		return isRestrictedData;
	}

	/**
	 * Allow adding, deleting, reparenting, renaming facets and changing the
	 * items they apply to?
	 *
	 * (Can't be static because it must be overridable. EditableQuery returns
	 * true.)
	 */
	@SuppressWarnings("static-method")
	public boolean isEditable() {
		return false;
	}

	public @NonNull MarkupElement getGenericObjectMarkup(final boolean isPlural) {
		return MarkupStringElement.getElement(getGenericObjectLabel(isPlural));
	}

	public @NonNull String getGenericObjectLabel(final boolean isPlural) {
		return isPlural ? genericObjectLabel : db.genericObjectLabel;
	}

	/**
	 * Only called by NameGetter, Perspective.getNameNow(), and
	 * LetterLabeled.prefix()
	 *
	 * setName() for each perspectives.
	 */
	public void getNamesNow(final @NonNull Collection<Perspective> perspectives) {
		final SortedSet<Perspective> sortedPerspectives = new TreeSet<>();
		// Keep only still-unnamed perspectives
		for (final Perspective p : perspectives) {
			// Might have been named while we weren't looking
			if (p.getNameIfCached() == null) {
				sortedPerspectives.add(p);
			}
		}
		if (sortedPerspectives.size() > 0) {
			try (final ResultSet rs = db.facetNames(getItemPredicateIDs(sortedPerspectives));) {
				assert rs != null : sortedPerspectives;
				assert MyResultSet.nRows(rs) == sortedPerspectives.size() : sortedPerspectives + "\n"
						+ MyResultSet.valueOfDeep(rs);
				for (final Perspective facet : sortedPerspectives) {
					rs.next();
					final String facetName = rs.getString(1);
					assert facetName != null;
					facet.setName(facetName);
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Only called by TopTagsViz and PerspectiveList
	 */
	public void queueGetNames(final @NonNull Collection<Perspective> uncached, final @NonNull RedrawCallback callback) {
		if (uncached.size() > 0) {
			nameGetter.addAll(uncached, callback);
		}
	}

	/**
	 * Only called by Perspective.getNameOrDefaultAndCallback().
	 */
	public void queueGetName(final @NonNull Perspective perspective, final @NonNull RedrawCallback callback) {
		assert assertParentPrefetched(perspective);
		nameGetter.add(perspective, callback);
	}

	private boolean assertParentPrefetched(final @NonNull Perspective perspective) {
		final Perspective parent = perspective.getParent();
		try {
			assert parent != null;
			assert !queuePrefetch(parent, parent.getFetchType(), null) : parent;
		} catch (final AssertionError e) {
			System.err.println("While queueGetName " + perspective);
			throw (e);
		}
		return true;
	}

	public void queueGetLetterOffsets(final @NonNull Perspective perspective, final @NonNull String prefix,
			final @NonNull RedrawCallback callback) {
		letterOffsetsCreator.add(perspective, prefix, callback);
	}

	public boolean isCorrelations() {
		return isCorrelations;
	}

	public @Nullable Pattern textSearchPattern() {
		if (!searches.equals(prevSearches)) {
			prevSearches = new HashSet<>(searches);
			textSearchPattern = null;
			if (searches.size() > 0) {
				final List<String> disjuncts = new LinkedList<>();
				for (final String search : searches) {
					final String[] quoteds = search.replace("*", "\\w*").replace("+", "").split("\"");
					final int nQuoteds = quoteds.length;
					// assert nQuoteds % 2 == 1 : nQuoteds + " search=" + search
					// + " quoteds=" + UtilString.valueOfDeep(quoteds);
					for (int i = 0; i < nQuoteds; i++) {
						final String s = quoteds[i];
						if (i % 2 != 0) {
							disjuncts.add(s);
						} else {
							final List<String> words = Arrays.asList(s.split("\\s+"));
							for (final String word : words) {
								if (word.length() > 0 && !word.startsWith("-")) {
									disjuncts.add(word);
								}
							}
						}
					}
				}
				final StringBuilder buf = new StringBuilder();
				buf.append("\\b");
				boolean isFirstSearch = true;
				for (final String disjunct : disjuncts) {
					if (isFirstSearch) {
						isFirstSearch = false;
					} else {
						buf.append("|");
					}
					buf.append("(").append(disjunct).append(")");
				}
				buf.append("\\b");
				textSearchPattern = Pattern.compile(buf.toString(), Pattern.CASE_INSENSITIVE);
				// System.out.println("Query.textSearchPattern return "
				// + textSearchPattern);
			}
		}
		return textSearchPattern;
	}

	/**
	 * In case TagWall hasn't been initialized
	 */
	public int maxBarTotalCount() {
		int result = 0;
		for (final Perspective facetType : facetTypes) {
			result = Math.max(result, facetType.getMaxChildTotalCount());
		}
		return result;
	}

	@Override
	public int compareTo(final ItemPredicate arg0) {
		assert false;
		return arg0 == this ? 0 : -1;
	}

	@Override
	public @NonNull Collection<Perspective> getFacets() {
		assert false;
		return UtilArray.EMPTY_LIST;
	}

	@Override
	public @Nullable Perspective compareBy() {
		assert false;
		return null;
	}

	@Override
	public @Nullable Perspective fromPerspective() {
		assert false;
		return null;
	}

	@Override
	public @Nullable Perspective toPerspective() {
		assert false;
		return null;
	}

	@Override
	public @NonNull YesNoMaybe isPlural() {
		return YesNoMaybe.NO;
	}

	@Override
	public @NonNull YesNoMaybe isNegated() {
		return YesNoMaybe.NO;
	}

	@Override
	public boolean isEffectiveChildren() {
		return false;
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		return null;
	}

	@Override
	public @Nullable MarkupElement merge(@SuppressWarnings("unused") final MarkupElement nextElement) {
		assert false;
		return null;
	}

	@Override
	public boolean shouldUnderline() {
		assert false;
		return false;
	}

	@Override
	public @Nullable ItemPredicate negate() {
		assert false;
		return null;
	}

	public @NonNull List<Perspective> getFacetTypes() {
		return facetTypes;
	}

	public @NonNull Set<InformediaQuery> getInformediaQueries() {
		return informediaQueries;
	}

}
