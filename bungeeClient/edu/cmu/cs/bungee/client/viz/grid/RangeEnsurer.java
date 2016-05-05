package edu.cmu.cs.bungee.client.viz.grid;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.threads.UpdateNoArgsThread;
import uk.org.bobulous.java.intervals.GenericInterval;
import uk.org.bobulous.java.intervals.Interval;
import uk.org.bobulous.java.intervals.Intervals;

/**
 * Makes sure displayed items (& thumbnails via ensureImagesCached) are all
 * cached.
 */
public final class RangeEnsurer extends UpdateNoArgsThread { // NO_UCD (use
	// default)

	private final @NonNull ResultsGrid grid;

	private final ReadWriteLock itemsByOffsetCachedRangesLock = new ReentrantReadWriteLock();

	/**
	 * Length is query().totalCount. Stores all cached Items based on offset in
	 * onCount. There's no point in doing anything with offsets unless query is
	 * valid. itemsByOffsetCachedRanges is cleared when query is updated; stale
	 * items remain in the table, but no one should look.
	 */
	private Item[] itemsByOffset;

	/**
	 * Keeps track of which entries in itemsByOffset are cached. Each Interval
	 * is a begin/end [exclusive] range of cached indexes into itemsByOffset.
	 */
	private @NonNull Intervals<Integer> itemsByOffsetCachedRanges = new Intervals<>();

	RangeEnsurer(final @NonNull ResultsGrid _grid) {
		super("RangeEnsurer", -2);
		grid = _grid;
	}

	@Override
	public void process() {
		final Query query = query();
		if (query.waitForValidQuery()) {
			final Interval<Integer> displayedInterval = displayedInterval();
			if (displayedInterval != null) {
				cacheOffsets(displayedInterval);
			}
		}
	}

	/**
	 * @return whether displayedInterval items are all cached. If not, calls
	 *         update().
	 */
	public boolean isUpToDate() {
		final Interval<Integer> displayedInterval = displayedInterval();
		final boolean result = displayedInterval != null && ensureOffsetItemsTable()
				&& itemsByOffsetCachedRanges.includes(displayedInterval);
		if (!result) {
			update();
		}
		// System.out.println("RangeEnsurer.isUpToDate result=" + result + " " +
		// displayedInterval);
		return result;
	}

	private @Nullable Interval<Integer> displayedInterval() {
		final Query query = query();
		Interval<Integer> displayedInterval = null;
		if (isQueueEmptyNnotExited()) {
			if (query.isQueryValid() && query.getOnCount() == grid.onCount && grid.isVisRowOffsetValid()) {
				final int minDisplayedOffset = grid.visRowOffset * grid.nCols;
				final int nThumbsForVisRowOffset = grid.nThumbsForVisRowOffset();
				if (nThumbsForVisRowOffset > 0) {
					final int maxDisplayedOffsetExclusive = minDisplayedOffset + nThumbsForVisRowOffset;
					displayedInterval = getOpenUpperInterval(minDisplayedOffset, maxDisplayedOffsetExclusive);
					assert query.getOnCount() >= maxDisplayedOffsetExclusive : "onCount=" + query.getOnCount() + " "
							+ displayedInterval + " nThumbsForVisRowOffset=" + nThumbsForVisRowOffset
							+ " grid.visRowOffset=" + grid.visRowOffset + " grid.nVisibleRows=" + grid.nVisibleRows
							+ " grid.onCountRow=" + grid.onCountRow + " grid.nCols=" + grid.nCols;
					if (!displayedInterval.includes(grid.selectedItemOffset)) {
						// System.out.println("RangeEnsurer.displayedInterval
						// skipping " + displayedInterval
						// + "\n because it doesn't contain selectedItemOffset,
						// " +
						// grid.selectedItemOffset
						// + ". Stale request?");
						displayedInterval = null;
					}
				}
			}
		} else {
			// System.out.println("RangeEnsurer.displayedInterval exiting
			// because isQueueEmptyNnotExited=false.");
			// // query.queueRedraw(grid);
		}
		return displayedInterval;
	}

	/**
	 * Make sure displayed items (& thumbnails via ensureImagesCached) are all
	 * cached. If not, figure out the smallest uncached range, update the table
	 * of ranges on the assumption that they will be cached soon, and call
	 * updateOffsetItemTable to do the caching.
	 */
	public void cacheOffsets(final @NonNull Interval<Integer> displayedInterval) {
		// UtilString.indentMore("RangeEnsurer.cacheOffsets " +
		// displayedInterval);
		final Lock writeLock = itemsByOffsetCachedRangesLock.writeLock();
		try {
			writeLock.lock();
			if (ensureOffsetItemsTable() && !itemsByOffsetCachedRanges.includes(displayedInterval)) {
				final Intervals<Integer> intervals = new Intervals<>(displayedInterval)
						.subtract(itemsByOffsetCachedRanges);
				updateOffsetItemTable(intervals);
				itemsByOffsetCachedRanges = itemsByOffsetCachedRanges.add(displayedInterval);
				if (isQueryVersionCurrent()) {
					ensureImagesCached(displayedInterval);
					// query().queueRedraw(grid);
				} else {
					update();
				}
			} else {
				// System.out.println("RangeEnsurer.cacheOffsets quitting
				// because ensureOffsetItemsTable()="
				// + ensureOffsetItemsTable() + " and
				// itemsByOffsetCachedRanges.includes(displayedInterval)="
				// + itemsByOffsetCachedRanges.includes(displayedInterval));
			}
		} finally {
			writeLock.unlock();
		}
		// UtilString.indentLess("RangeEnsurer.cacheOffsets exit");
	}

	protected boolean isQueryVersionCurrent() {
		return query().isQueryVersionCurrent(offsetItemsQueryVersion);
	}

	private int offsetItemsQueryVersion = -2;

	/**
	 * MUST be called holding itemsByOffsetCachedRangesLock.writeLock
	 *
	 * @return whether query is valid and itemsByOffset is up to date.
	 */
	private boolean ensureOffsetItemsTable() {
		final int version = query().version();
		final boolean result = version > 0;
		if (version != offsetItemsQueryVersion && result) {
			itemsByOffsetCachedRanges = new Intervals<>();
			final int totalCount = query().getTotalCount();
			assert totalCount >= 0;
			if (itemsByOffset == null || itemsByOffset.length < totalCount) {
				itemsByOffset = new Item[totalCount];
			}
			offsetItemsQueryVersion = version;
		}
		return result;
	}

	/**
	 * Call query.offsetItems and ensureItem the items in the uncachedIntervals.
	 * Place them at the correct offset in itemsByOffset.
	 *
	 * Called only by cacheOffsets
	 */
	private void updateOffsetItemTable(final @NonNull Intervals<Integer> uncachedIntervals) {
		final Query query = query();
		for (final Interval<Integer> uncachedInterval : uncachedIntervals) {
			// each iteration queries database, so check isQueryVersionCurrent
			if (!isQueryVersionCurrent()) {
				break;
			}
			@SuppressWarnings("null")
			final int maxOffsetExclusive = uncachedInterval.getUpperEndpoint();
			assert maxOffsetExclusive <= query.getOnCount();
			@SuppressWarnings("null")
			final int minOffsetInclusive = uncachedInterval.getLowerEndpoint();

			// Warning: don't change rs row, as it may be purposefully set to
			// an intermediate row.
			try (final java.sql.ResultSet rs = query.offsetItems(minOffsetInclusive, maxOffsetExclusive);) {
				int offset = minOffsetInclusive;
				while (rs.next() && offset < maxOffsetExclusive) {
					final Item item = Item.ensureItem(rs.getInt(1));
					item.setOffset(offset, offsetItemsQueryVersion);
					itemsByOffset[offset++] = item;
				}
				assert offset == maxOffsetExclusive : "query().offsetItems returned " + MyResultSet.nRows(rs)
						+ " rows, but " + (maxOffsetExclusive - minOffsetInclusive) + " were expected.\n "
						+ uncachedInterval + " offset=" + offset + " rs.getRow()=" + rs.getRow() + " queryValid="
						+ query.isQueryValid() + " query.getOnCount()=" + query.getOnCount() + " grid.onCount="
						+ grid.onCount + "\n grid.visRowOffset=" + grid.visRowOffset + " grid.nVisibleRows="
						+ grid.nVisibleRows + " grid.onCountRow=" + grid.onCountRow + " grid.nCols=" + grid.nCols;
			} catch (final Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private static @NonNull Interval<Integer> getOpenUpperInterval(final int lower, final int upperExclusive) {
		return GenericInterval.getOpenUpperGenericInterval(lower, upperExclusive);
	}

	/**
	 * Called only by process() (via cacheOffsets())
	 *
	 * Checks whether items in displayedInterval need GridElements (which it
	 * calls loadItems to load from db).
	 */
	private void ensureImagesCached(final @NonNull Interval<Integer> displayedInterval) {
		final SortedSet<Item> toLoad = new TreeSet<>();
		@SuppressWarnings("null")
		final int minOffset = displayedInterval.getLowerEndpoint();
		@SuppressWarnings("null")
		final int maxOffsetExclusive = displayedInterval.getUpperEndpoint();
		assert !grid.isInitted() || maxOffsetExclusive <= grid.onCount : "grid.onCount=" + grid.onCount
				+ " query().getOnCount=" + query().getOnCount() + " offset range=" + displayedInterval
				+ " itemsByOffsetCachedRanges=" + itemsByOffsetCachedRanges;
		for (int offset = minOffset; offset < maxOffsetExclusive; offset++) {
			final Item item = getItem(offset);
			assert item != null : " offset=" + offset + " displayedInterval=" + displayedInterval
					+ " itemsByOffsetCachedRanges=" + itemsByOffsetCachedRanges + " offsetItemsQueryVersion="
					+ offsetItemsQueryVersion + " QueryVersion=" + query().version();
			final GridElement gridElement = GridElementWrapper.lookupGridElement(item, true);
			if (gridElement == null
					|| !gridElement.isBigEnough(grid.edgeW(), grid.edgeH(), BungeeConstants.THUMB_QUALITY)) {
				final boolean isNew = toLoad.add(item);
				assert isNew;
			}
		}
		if (toLoad.size() > 0) {
			loadItems(toLoad);
			query().queueRedraw(grid);
		}
	}

	/**
	 * @return item at offset, or null if it isn't cached
	 */
	public @Nullable Item getItem(final int offset) {
		assert assertInRange(offset, 0, grid.onCount - 1) : " offset=" + offset + " onCount=" + grid.onCount
				+ " visRowOffset=" + grid.visRowOffset;
		Item result = null;
		final Lock readLock = itemsByOffsetCachedRangesLock.readLock();
		try {
			readLock.lock();
			if (isOffsetCached(offset)) {
				result = itemsByOffset[offset];
				assert result != null : offset + " " + itemsByOffsetCachedRanges;
				// assert result.getOffset(query()) == offset : "we don't need
				// to set offset here, right?";
				// result.setOffset(offset);
			}
		} finally {
			readLock.unlock();
		}
		return result;
	}

	private boolean isOffsetCached(final int offset) {
		return isQueryVersionCurrent() && itemsByOffsetCachedRanges.includes(offset);
	}

	/**
	 * Called only by ensureImagesCached().
	 */
	private void loadItems(final @NonNull SortedSet<Item> items) {
		try {
			final ResultSet[] rss = query().getThumbs(items, grid.edgeW(), grid.edgeH());
			try (ResultSet itemDescRS = rss[0]; ResultSet itemFacetsRS = rss[1];) {
				createItem(Util.nonNull(itemDescRS));
				addItemFacets(Util.nonNull(itemFacetsRS));
			}
		} catch (final Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * Only called by loadItems()
	 *
	 * ensureItem(), setDescription(), and ensureGridElementWrapper()
	 *
	 * @param itemDescRS
	 *            [record_num, description, image, w, h]
	 */
	private void createItem(final @NonNull ResultSet itemDescRS) throws SQLException {
		while (itemDescRS.next()) {
			final Item item = Item.ensureItem(itemDescRS.getInt("record_num"));
			final String description = itemDescRS.getString("description");
			assert description != null;
			item.setDescription(description);
			GridElementWrapper.ensureGridElementWrapper(art(), item, ((MyResultSet) itemDescRS).getBufferedImage(3),
					itemDescRS.getInt("w"), itemDescRS.getInt("h"), true);
		}
	}

	/**
	 * Only called by loadItems()
	 *
	 * ensureItem(), addFacet()
	 *
	 * @param itemFacetsRS
	 *            [record_num, facet_id]
	 */
	private void addItemFacets(final @NonNull ResultSet itemFacetsRS) throws SQLException {
		while (itemFacetsRS.next()) {
			final Item item = Item.ensureItem(itemFacetsRS.getInt("record_num"));
			final int facetID = itemFacetsRS.getInt("facet_id");
			final Perspective facet = query().findPerspectiveIfCached(facetID);
			if (facet != null) {
				item.addFacet(facet);
			} else {
				grid.addPendingItemFacet(item, facetID);
			}
		}
	}

	@Override
	public synchronized void exit() {
		art().stopReplayer();
		super.exit();
	}

	private @NonNull Query query() {
		return art().getQuery();
	}

	private @NonNull Bungee art() {
		return grid.art;
	}

}
