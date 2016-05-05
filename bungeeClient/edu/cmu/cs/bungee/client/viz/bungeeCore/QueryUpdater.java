package edu.cmu.cs.bungee.client.viz.bungeeCore;

import java.awt.Cursor;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.javaExtensions.threads.UpdateNoArgsThread;
import edu.umd.cs.piccolo.PCanvas;

final class QueryUpdater extends UpdateNoArgsThread {

	private static final Cursor WAIT_CURSOR = new Cursor(Cursor.WAIT_CURSOR);

	final Bungee bungee;

	/**
	 * The number of processes that are busy. Initially 1, as init() decreases
	 * it.
	 */
	private int waiting = 0;

	QueryUpdater(final Bungee _bungee) {
		super("QueryUpdater", 0);
		bungee = _bungee;
	}

	@Override
	public long lastActiveTime() {
		return waiting == 0 ? super.lastActiveTime() : Long.MAX_VALUE;
	}

	@Override
	// Create query in the background to save time.
	public void init() {
		handleCursor(true);
		super.init();
		String dbName = bungee.dbName;
		if (dbName == null) {
			dbName = "";
		}
		final Query query = Query.getQuery(bungee.server, dbName, bungee.noTemporaryTables, bungee.isTraceServletCalls,
				bungee.slowQueryTime);
		bungee.setQuery(query);
		// query.initCounts();
		handleCursor(false);
		// System.out.println("QueryUpdater.init " + bungee.isReady());
		// synchronized (myThread) {
		// myThread.notifyAll();
		// }
	}

	/**
	 * Only called by Bungee.updateQuery()
	 *
	 * All query changes go through this method EXCEPT restrictData. Does not
	 * require isQueryValid.
	 *
	 * This and process handle the update cycle:
	 *
	 * 1. Set query invalid and begin animating viz changes.
	 *
	 * 2. update synchronizes with the database, sets query valid, and
	 *
	 * 3. redraws.
	 */
	void updateQuery() {
		// System.out.println("QueryUpdater.updateQuery enter waiting=" +
		// waiting);
		if (getQuery().isQueryValid()) {
			getQuery().setQueryValid(false);
			bungee.getTagWall().synchronizeWithQuery();
		}
		if (update()) {
			handleCursor(true);
		}
		// System.out.println("QueryUpdater.updateQuery exit waiting=" +
		// waiting);
	}

	@Override
	public void process() {
		final Query query = getQuery();
		assert !query.isQueryValid() || !isQueueEmpty();

		if (isQueueEmptyNnotExited()) {
			final int[] offsetNitem = query.updateOnItems(bungee.getSelectedItem(),
					bungee.getGrid().maxPossibleThumbs());
			query.invokeWhenQueryValid(getRedrawer(offsetNitem));
		}
		if (!isQueueEmpty()) {
			assert waiting > 1;
			handleCursor(false);
		}
	}

	/**
	 * @return Runnable to call after Query becomes valid (i.e. counts have been
	 *         updated).
	 */
	private @NonNull Runnable getRedrawer(final int[] offsetNitem) {
		final int queryVersion = getQuery().version();

		// Don't barf, just let Runnable be a no-op.
		// assert queryVersion >= 0;

		return new Runnable() {
			@Override
			public void run() {
				try {
					// Check to make sure it hasn't become invalid again.
					// If we're unrestricting, isReady might be false.
					if (getQuery().isQueryVersionCurrent(queryVersion) && bungee.isReady()
							&& isQueueEmptyNnotExited()) {
						// printRedrawerReturnMessage();

						final int offset = offsetNitem[0];
						if (offset >= 0) {
							final Item correctedItem = Item.ensureItem(offsetNitem[1]);
							bungee.setSelectedItem(correctedItem, false, offset, ReplayLocation.THUMBNAIL);
						}

						bungee.queryValidRedraw();
					}
				} catch (final AssertionError e) {
					System.err.println(" Warning: In QueryUpdater.redrawer,\nnonTopLevelDisplayedPerspectives="
							+ getQuery().nonTopLevelDisplayedPerspectives() + "\n");
					e.printStackTrace();
					bungee.stopReplayer();
				} finally {
					handleCursor(false);
					// printRedrawerReturnMessage();
				}
			}
		};
	}

	// TODO Remove unused code found by UCDetector
	// void printRedrawerReturnMessage() {
	// System.out.println("QueryUpdater.getRedrawer return. queryValid="
	// + getQuery().isQueryValid() + " updateIndex="
	// + getQuery().version() + " queryUpdater.isUpToDat="
	// + isUpToDate());
	// }

	/**
	 * Cursor should be an hourglass iff waiting > 0.
	 *
	 * Also called in mouse process by Bungee during initialization.
	 */
	synchronized void handleCursor(final boolean wait) {
		// printDateNmessage("QueryUpdater.handleCursor wait=" + wait + " ⇒
		// waiting=" + (waiting + (wait ? 1 : -1)) + "\n"
		// + UtilString.getStackTrace());

		// synchronized (WAIT_CURSOR) {
		final boolean oldIsWaiting = waiting > 0;
		waiting += wait ? 1 : -1;
		final boolean isWaiting = waiting > 0;
		if (isWaiting != oldIsWaiting) {
			final PCanvas canvas = bungee.getCanvas();
			if (wait) {
				canvas.pushCursor(WAIT_CURSOR);
			} else {
				canvas.popCursor();
			}
		}
		// }
	}

	Query getQuery() {
		return bungee.getQuery();
	}

	@Override
	public synchronized void exit() {
		bungee.stopReplayer();
		super.exit();
	}
}