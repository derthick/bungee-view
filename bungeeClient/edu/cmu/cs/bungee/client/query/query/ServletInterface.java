package edu.cmu.cs.bungee.client.query.query;

import static edu.cmu.cs.bungee.javaExtensions.UtilString.now;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.zip.InflaterInputStream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.FormattedTableBuilder;
import edu.cmu.cs.bungee.javaExtensions.MyLogger;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.UtilFiles;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.servlet.Command;
import edu.cmu.cs.bungee.servlet.FetchType;
import edu.cmu.cs.bungee.servlet.OnItemsTable;

/**
 * No bungeeClient class besides Query should know about ServletInterface.
 * ServletInterface should not know about any other bungeeeClient class.
 * ServletInterface and bungeeServlet.Encoder both know about the communication
 * protocol, but no other classes should. Perhaps all the logic should be in
 * Encoder.
 *
 * These functions can be called from multiple threads!
 */
final class ServletInterface implements Serializable {
	private static final int GETSTREAM_TIMEOUT = 1_000_000;

	private static final long serialVersionUID = 1L;

	private static final @NonNull Level SERVLET_LOG_LEVEL = /*
															 * slowQueryTime >=
															 * 0 ? MyLogger.INFO
															 * :
															 */ MyLogger.WARNING;

	/**
	 * [[<name>,<description>], ...]
	 */
	final @NonNull String[][] databaseDescs;
	final @NonNull String[] dbNameNdesc;
	/**
	 * Total item count for current query
	 */
	final int itemCount;
	final @NonNull String itemDescriptionFields;
	final @NonNull String genericObjectLabel;
	final @NonNull String itemURLdoc;
	final boolean isEditable;
	/**
	 * Number of distinct facets (including facet types) in database.
	 */
	final int nFacets;

	private final boolean isTraceServletCalls;
	/**
	 * Only relevant if isTraceServletCalls. Print servlet calls as well as
	 * accumulate their elapsed times.
	 */
	private final boolean isPrintServletCalls = true;
	/**
	 * <command name> -> [-<call time 0>, ...]
	 *
	 * Uses -<call time> to sort from highest to lowest.
	 */
	private final @NonNull Map<CommandNThread, int[]> commandTimes = new HashMap<>();

	private final @NonNull String host;
	private final @NonNull String sessionID;
	private final @NonNull MyResultSet initPerspectives;

	/**
	 * status of most recent servlet response
	 */
	private @Nullable String status;

	/**
	 * This caches answers for two functions:
	 *
	 * itemIndex: what is the itemOffset of an item?
	 *
	 * offsetItems: what are the items for a range of offsets?
	 *
	 * The answers are cached when calling itemIndex, itemIndexFromURL, and
	 * updateOnItems.
	 */
	private final @NonNull ItemInfo itemInfo = new ItemInfo();

	private long lastActiveTime = 0L;
	private int nActiveThreads = 0;

	ServletInterface(final @NonNull String codeBase, final @NonNull String dbName, final boolean noTemporaryTables,
			final boolean _isTraceServletCalls, final int slowQueryTime) {
		System.out.println(
				"ServletInterface: codeBase=" + codeBase + " dbName=" + dbName + " date=" + (new Date().toString()));
		assert UtilString.isNonEmptyString(codeBase);
		host = codeBase;
		isTraceServletCalls = _isTraceServletCalls;
		final String[] args = { dbName, Integer.toString(slowQueryTime), Boolean.toString(noTemporaryTables),
				SERVLET_LOG_LEVEL.toString() };
		String _sessionID = null;
		String _genericObjectLabel = null;
		String _itemDescriptionFields = null;
		int _itemCount = -1;
		boolean _isEditable = false;
		MyResultSet _initPerspectives = null;
		int _nFacets = -1;
		String _itemURLdoc = "";
		String[][] _databaseDescs = null;
		boolean _isCorrelations = true;
		try (final DataInputStream in = getStream(Command.CONNECT, args);) {
			if (in != null) {
				_sessionID = MyResultSet.readString(in);
				System.out.println(" session = " + _sessionID);

				_databaseDescs = getDatabases(MyResultSet.readString(in));
				_nFacets = MyResultSet.readInt(in);
				_itemCount = MyResultSet.readInt(in);
				_isCorrelations = MyResultSet.readBoolean(in);

				// These are the getGlobals in Servlet.doPostInternal
				_itemDescriptionFields = MyResultSet.readString(in);
				_genericObjectLabel = MyResultSet.readString(in);
				_itemURLdoc = MyResultSet.readString(in);
				_isEditable = Boolean.valueOf(MyResultSet.readString(in));

				_initPerspectives = new MyResultSet(in);
			} else if (status == null) {
				status = "Could not connect to " + UtilString.join(args);
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
		isCorrelations = _isCorrelations;
		assert _sessionID != null && _sessionID.length() > 0;
		sessionID = _sessionID;
		genericObjectLabel = _genericObjectLabel == null ? "unknown" : _genericObjectLabel;
		itemDescriptionFields = _itemDescriptionFields == null ? "" : _itemDescriptionFields;
		itemCount = _itemCount;
		isEditable = _isEditable;
		assert _initPerspectives != null;
		initPerspectives = _initPerspectives;
		nFacets = _nFacets;
		itemURLdoc = _itemURLdoc;
		assert _databaseDescs != null;
		databaseDescs = _databaseDescs;
		dbNameNdesc = getDBnameNdesc(dbName);
	}

	/**
	 * Defaults to databaseDescs[0]
	 */
	private @NonNull String[] getDBnameNdesc(final @NonNull String _dbName) {
		String[] result = null;
		for (int i = 0; i < databaseDescs.length && result == null; i++) {
			if (!UtilString.isNonEmptyString(_dbName) || databaseDescs[i][0].equalsIgnoreCase(_dbName)) {
				result = databaseDescs[i];
			}
		}
		assert result != null;
		return result;
	}

	@NonNull
	String getDBdescription() {
		final String result = dbNameNdesc[1];
		assert result != null;
		return result;
	}

	@NonNull
	String getDBname() {
		final String result = dbNameNdesc[0];
		assert result != null;
		return result;
	}

	@NonNull
	String close() {
		// System.out.println("ServletInterface.close" +
		// UtilString.getStackTrace());
		String result = null;
		try (final DataInputStream in = doGetStream(Command.CLOSE, null);) {
			result = MyResultSet.readString(in);
		} catch (final IOException e) {
			e.printStackTrace();
		}

		if (isTraceServletCalls) {
			printCommandTimes();
		}
		assert result != null;
		return result;
	}

	private void printCommandTimes() {
		final FormattedTableBuilder align = new FormattedTableBuilder();
		align.addLine("Thread", "Command", "Invocations", "Average Time (ms)", "Elapsed Time (ms)",
				"% Total Elapsed Time");
		align.addLine();

		int allCommandsTime = 0;
		final SortedSet<Integer> totalTimes = new TreeSet<>();
		for (final int[] value : commandTimes.values()) {
			final int time = value[1];
			allCommandsTime -= time;
			totalTimes.add(time);
		}
		final double totalD = allCommandsTime / 100.0;

		int entriesTotalTime = 0;
		for (final int minusTotalTime : totalTimes) {
			for (final Entry<CommandNThread, int[]> entry : commandTimes.entrySet()) {
				final int[] value = entry.getValue();
				if (minusTotalTime == value[1]) {
					final int totalTime = -minusTotalTime;
					entriesTotalTime += totalTime;
					final int invocations = value[0];
					final CommandNThread commandNThread = entry.getKey();
					align.addLine(commandNThread.thread().getName(), commandNThread.command(), invocations,
							UtilMath.roundToInt(totalTime / (double) invocations), totalTime,
							UtilMath.roundToInt(totalTime / totalD));
				}
			}
		}
		align.addLine();
		align.addLine("", "Total", "", "", allCommandsTime, UtilMath.roundToInt(entriesTotalTime / totalD));
		System.out.println("\n" + align.format() + "\n");
	}

	@Nullable
	String errorMessage() {
		return status;
	}

	/**
	 * Ignore returned stream. For commands that need no return value.
	 */
	private void dontGetStream(final @NonNull Command command, final @Nullable String[] args) {
		try (final DataInputStream stream = getStream(command, args);) {
			// Make sure stream gets closed.
		} catch (final IOException e) {
			System.err.println(
					"While ServletInterface.doGetStream command=" + command + " args=" + Arrays.toString(args));
			e.printStackTrace();
		}
	}

	private @NonNull DataInputStream doGetStream(final @NonNull Command command, @Nullable final String[] args) {
		final DataInputStream stream = getStream(command, args);
		assert stream != null : "While ServletInterface.doGetStream command=" + command + " args="
				+ Arrays.toString(args);
		return stream;
	}

	private @Nullable DataInputStream getStream(final @NonNull Command command, final @Nullable String[] args) {
		active(true);
		final long start = isTraceServletCalls ? UtilString.now() : 0L;
		DataInputStream in = null;
		final String urlString = getUrlString(command, args);
		try {
			traceSendServletCall(urlString);
			final HttpURLConnection conn = (HttpURLConnection) (new URL(host + urlString)).openConnection();
			conn.setConnectTimeout(GETSTREAM_TIMEOUT);
			conn.setReadTimeout(GETSTREAM_TIMEOUT);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-length", "0");
			final int code = conn.getResponseCode();
			final int codeType = code / 100;
			if (codeType == 2) {
				in = new DataInputStream(new InflaterInputStream(new BufferedInputStream(conn.getInputStream())));
			} else {
				handleResponseError(conn);
			}
		} catch (final Throwable e) {
			System.err.println("While ServletInterface.getStream " + urlString + ":\n");
			if (status == null) {
				status = e.toString();
			}
			e.printStackTrace();
		} finally {
			active(false);
		}
		if (isTraceServletCalls /* && !command.startsWith("onCountMatrix") */) {
			@SuppressWarnings("null")
			final CommandNThread commandNThread = new CommandNThread(command, Thread.currentThread());
			int[] commandTime = commandTimes.get(commandNThread);
			if (commandTime == null) {
				commandTime = new int[2];
				commandTimes.put(commandNThread, commandTime);
			}
			commandTime[0]++;
			commandTime[1] -= UtilString.now() - start;
			traceReceiveServletCall(command, start);
		}
		return in;
	}

	private synchronized void active(final boolean isActive) {
		nActiveThreads += isActive ? 1 : -1;
		if (nActiveThreads == 0) {
			lastActiveTime = now();
		}
	}

	public long lastActiveTime() {
		return nActiveThreads == 0 ? lastActiveTime : Long.MAX_VALUE;
	}

	class CommandNThread extends ArrayList<Object> {
		CommandNThread(final @NonNull Command command, final @NonNull Thread thread) {
			add(command);
			add(thread);
		}

		@NonNull
		Command command() {
			final Command command = (Command) get(0);
			assert command != null;
			return command;
		}

		@NonNull
		Thread thread() {
			final Thread thread = (Thread) get(1);
			assert thread != null;
			return thread;
		}

		@Override
		public String toString() {
			return UtilString.toString(this, command() + " " + thread().getName());
		}
	}

	private @NonNull String getUrlString(final @NonNull Command command, final @Nullable String[] args) {
		final StringBuilder buf = new StringBuilder();
		buf.append("?command=").append(command);
		try {
			if (args != null) {
				for (int i = 0; i < args.length; i++) {
					assert args[i] != null : UtilString.valueOfDeep(args);
					buf.append("&arg").append(i + 1).append("=").append(URLEncoder.encode(args[i], "UTF-8"));
				}
			}
			final String encodedActions = flushUserActions();
			if (encodedActions != null) {
				buf.append("&userActions=").append(encodedActions);
			}
			buf.append("&session=").append(sessionID);
		} catch (final UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		final String result = buf.toString();
		assert result != null;
		return result;
	}

	private boolean traceSendServletCall(final @NonNull String urlString) throws UnsupportedEncodingException {
		if (isTraceServletCalls && isPrintServletCalls) {
			String decodedURLstring = URLDecoder.decode(urlString, "UTF-8");
			decodedURLstring = decodedURLstring.replace("arg4=WARNING", "arg4=WRNING");
			System.out.println(UtilString.getSpaces((nActiveThreads - 1) * 2) + decodedURLstring);
		}
		return true;
	}

	private boolean traceReceiveServletCall(final @NonNull Command command, final long start) {
		if (isTraceServletCalls && isPrintServletCalls) {
			System.out.println(UtilString.getSpaces(nActiveThreads * 2) + "?  " + command + " took "
					+ UtilString.elapsedTimeString(start, 8));
		}
		return true;
	}

	private void handleResponseError(final @NonNull HttpURLConnection conn) throws IOException {
		status = conn.getResponseMessage();
		System.err.println(" Warning: ServletInterface.getStream got status " + conn.getResponseCode() + ": " + status);
		try (final InputStream errorStream = conn.getErrorStream();) {
			if (errorStream != null) {
				final String html = UtilFiles.inputStreamToString(errorStream);
				assert html != null;
				System.err.println("ServletInterface.getStream error stream says:\n" + UtilString.html2text(html));
			}
		}
	}

	private @NonNull String getString(final @NonNull Command command, final @Nullable String[] args) {
		String result = null;
		try (final DataInputStream in = getStream(command, args);) {
			result = MyResultSet.readString(in);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	private @NonNull MyResultSet getResultSet(final @NonNull Command command, final @Nullable String[] args) {
		try (DataInputStream in = doGetStream(command, args);) {
			return new MyResultSet(in);
		} catch (final IOException e) {
			System.err.println(" Warning: While ServletInterface.getResultSet command=" + command + " args="
					+ Arrays.toString(args));
			throw (new AssertionError(e));
		}
	}

	@NonNull
	String aboutCollection() {
		return getString(Command.ABOUT_COLLECTION, null);
	}

	@NonNull
	String itemURL(final int item) {
		final String[] args = { Integer.toString(item) };
		return getString(Command.ITEM_URL, args);
	}

	@NonNull
	MyResultSet onCountsIgnoringFacet(final @NonNull String subQuery, final int ignoreParent) {
		final String[] args = { subQuery, Integer.toString(ignoreParent) };
		return getResultSet(Command.ONCOUNTS_IGNORING_FACET, args);
	}

	/**
	 * @return all non-zero onCounts
	 */
	@NonNull
	ResultSet filteredCounts(final @NonNull OnItemsTable table) {
		final String[] args = { table.toString() };
		return getResultSet(Command.FILTERED_COUNTS, args);
	}

	@NonNull
	ResultSet initPerspectives() {
		return initPerspectives;
	}

	/**
	 * Externally synchronized (Query.prefetchingLock), so Servlet doesn't have
	 * to.
	 */
	@NonNull
	ResultSet[] prefetch(final @NonNull String facetIDs, final @NonNull FetchType fetchType) {
		final String[] args = { facetIDs, Integer.toString(fetchType.ordinal()) };
		final ResultSet[] rss = new ResultSet[2];
		try (final DataInputStream in = doGetStream(Command.PREFETCH, args);) {
			rss[0] = new MyResultSet(in);
			rss[1] = new MyResultSet(in);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return rss;
	}

	@NonNull
	MyResultSet letterOffsets(final int facetID, final @NonNull String prefix) {
		final String[] args = { Integer.toString(facetID), prefix };
		return getResultSet(Command.LETTER_OFFSETS, args);
	}

	/**
	 * @return a one-column rs of items in [minOffsetInclusive,
	 *         maxOffsetExclusive>
	 *
	 *         Warning: result's current row may not be zero!
	 */
	@SuppressWarnings("resource")
	@NonNull
	ResultSet offsetItems(final int minOffsetInclusive, final int maxOffsetExclusive, final @NonNull OnItemsTable table,
			final int _queryVersion) {
		if (itemInfo.queryVersion == _queryVersion && minOffsetInclusive >= itemInfo.minIndex
				&& maxOffsetExclusive <= itemInfo.maxIndex()) {
			try {
				final ResultSet itemOffsetsRS = itemInfo.itemOffsetsRS;
				assert itemOffsetsRS != null;
				itemOffsetsRS.absolute(minOffsetInclusive - itemInfo.minIndex);
			} catch (final SQLException e) {
				System.err.println(" Warning: Caching is messed up: " + itemInfo + " " + minOffsetInclusive);
				e.printStackTrace();
			}
			// if (printOps)
			// System.out.println("Using cached rs for offsetItems: " +
			// itemInfo);
		} else {
			// if (itemInfo != null && isTraceServletCalls) {
			// System.err
			// .println("Warning: NOT using cached rs for offsetItems ["
			// + minOffsetInclusive
			// + "-"
			// + maxOffsetExclusive
			// + ">. table="
			// + table
			// + " itemInfo.queryVersion="
			// + itemInfo.queryVersion
			// + " _queryVersion="
			// + _queryVersion + " itemInfo:\n " + itemInfo);
			// // System.err.println(UtilString.getStackTrace());
			// }

			final String[] args = { Integer.toString(minOffsetInclusive), Integer.toString(maxOffsetExclusive),
					table.toString() };
			final ResultSet result = getResultSet(Command.OFFSET_ITEMS, args);
			try {
				result.next();
				itemInfo.update(result.getInt(1), minOffsetInclusive, minOffsetInclusive, result, _queryVersion);
				result.beforeFirst();
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
		// System.out.println("ServletInterface.offsetItems return: itemInfo="
		// + itemInfo);
		return itemInfo.itemOffsetsRS;
	}

	// /**
	// * itemInfo. itemID == item AND:
	// *
	// *
	// * resultRow - 1 .......: x ... minIndex ... x
	// *
	// * resultRow ...........: x ... ... x
	// *
	// * OR
	// *
	// * resultRow ...........: minIndex ... x
	// *
	// * // selectedItemOffset is somewhere between minIndex - maxIndexInclusive
	// *
	// * ... .................: ... selectedItemOffset ...
	// *
	// *
	// * resultRow + nRows - 1: x ... ... maxIndexInclusive
	// *
	// * OR
	// *
	// * resultRow + nRows - 1: x ... ... x
	// *
	// * resultRow + nRows ...: x ... maxIndexInclusive ... x
	// *
	// * @return visRowOffset constrained so that item is visible (i.e. in a
	// range
	// * that includes itemRow) and where offsets are cached. If no such
	// * cached range, return -1;
	// */
	// public int cachedVisOffset(final int itemRow, final int visRowOffset,
	// final int nVisibleRows, final int nCols, final int item) {
	// assert item >= 0;
	// int result = -1;
	// if (itemInfo.itemID == item) {
	// final double dnCols = nCols;
	// final int minRow = Math.max(itemRow - nVisibleRows + 1,
	// UtilMath.intCeil(itemInfo.minIndex / dnCols));
	// final int maxRow = Math.min(itemRow,
	// (int) (itemInfo.maxIndex() / dnCols) - 1);
	// final boolean isCached = minRow <= itemRow && itemRow <= maxRow
	// && maxRow - minRow >= nVisibleRows;
	// if (isCached) {
	// result = UtilMath.constrain(visRowOffset, minRow, maxRow);
	// }
	// // System.out.println("ServletInterface.cachedVisOffset visRowOffset="
	// // + visRowOffset + " nRows=" + nRows + " nCols=" + nCols
	// // + " itemRow=" + itemRow + " result=" + result);
	// }
	// return result;
	// }

	@NonNull
	ResultSet[] thumbs(final @NonNull String items, final int imageW, final int imageH, final int quality) {
		final String[] args = { items, Integer.toString(imageW), Integer.toString(imageH), Integer.toString(quality) };
		final ResultSet[] result = new ResultSet[2];
		try (final DataInputStream in = doGetStream(Command.THUMBS, args);) {
			result[0] = new MyResultSet(in);
			result[1] = new MyResultSet(in);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	@NonNull
	ResultSet descAndImage(final int item, final int imageW, final int imageH, final int quality) {
		final String[] args = { Integer.toString(item), Integer.toString(imageW), Integer.toString(imageH),
				Integer.toString(quality) };
		return getResultSet(Command.DESC_AND_IMAGE, args);
	}

	/**
	 * Always called right after getDescAndImage
	 */
	@NonNull
	ResultSet itemInfo(final int item) {
		final String[] args = { Integer.toString(item) };
		return getResultSet(Command.ITEM_INFO, args);
	}

	/**
	 * @return parent_facet_id, facet_id, name, n_child_facets,
	 *         first_child_offset
	 */
	@NonNull
	ResultSet importFacets(final @NonNull String facetIDs, final boolean isRestrictedData) {
		final String[] args = { facetIDs, asBoolean(isRestrictedData) };
		return getResultSet(Command.IMPORT_FACET, args);
	}

	/**
	 * @return [offset for correctedItem, correctedItem, onCount], where onCount
	 *         == -1 means no restrictions (i.e. totalCount)
	 */
	@NonNull
	int[] updateOnItems(final @Nullable String subQuery, final int selectedItem,
			final @NonNull OnItemsTable onItemsTable, final int nThumbs, final int _queryVersion) {
		assert nThumbs >= 0 : nThumbs;
		final int[] result = { -1, -1, -1 };
		// final int maxThumbsExclusive = nThumbs + 1;
		final String[] args = { (subQuery == null ? "" : subQuery), Integer.toString(selectedItem),
				onItemsTable.toString(), Integer.toString(nThumbs) };
		try (final DataInputStream in = doGetStream(Command.UPDATE_ON_ITEMS, args);) {
			final int onCount = MyResultSet.readInt(in);
			result[2] = subQuery == null ? -1 : onCount;
			if (onCount > 0) {
				final int[] offsetNitem = correctNcacheOffsets(in, selectedItem, nThumbs, _queryVersion);
				result[0] = offsetNitem[0];
				result[1] = offsetNitem[1];
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Caches itemOffsetsRS.
	 *
	 * @return [offset for correctedItem, correctedItem]
	 *
	 *         If item is no longer in onItems, corrected item will be different
	 *         from item. (If nNeighbors == 0, item won't be corrected, but
	 *         offset will be -1.)
	 */
	@NonNull
	int[] itemOffset(final int item, final @NonNull OnItemsTable table, final int nNeighbors, final int _queryVersion) {
		int[] result = null;
		if (itemInfo.queryVersion == _queryVersion && itemInfo.item == item) {
			// if (printOps)
			// System.out.println("Using cached itemIndex for " + item + ", "
			// + itemInfo.itemIndex);

			// Doesn't check for invalid item like correctNcacheOffsets does
			final int[] result1 = { itemInfo.itemOffset, item };
			result = result1;
		} else {
			// if (itemInfo != null && isTraceServletCalls) {
			// System.err.println("Warning: NOT using cached itemIndex for "
			// + item + " itemInfo.queryVersion="
			// + itemInfo.queryVersion + " _queryVersion=" + _queryVersion
			// + " itemInfo:\n " + itemInfo
			// + MyResultSet.valueOfDeep(itemInfo.itemOffsetsRS));
			// // System.err.println(UtilString.getStackTrace());
			// }
			assert nNeighbors >= 0;
			// nNeighbors += 1; // Servlet nNeighbors is exclusive
			final String[] args = { Integer.toString(item), table.toString(), Integer.toString(nNeighbors) };
			try (final DataInputStream in = doGetStream(Command.ITEM_OFFSET, args);) {
				result = correctNcacheOffsets(in, item, nNeighbors, _queryVersion);
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
		assert result != null;
		return result;
	}

	/**
	 * @return [offset for correctedItem, correctedItem] If item is no longer in
	 *         onItems, corrected item will be different from item. (If
	 *         nNeighbors == 0, item won't be corrected, but offset will be -1.)
	 *
	 *         Only called by Bungee.setInitialState
	 */
	@NonNull
	int[] itemIndexFromURL(final @NonNull String urlString, final @NonNull OnItemsTable table,
			final int _queryVersion) {
		final int nNeighbors = 0;
		final String[] args = { urlString, table.toString(), Integer.toString(nNeighbors) };
		int[] result = null;
		try (final DataInputStream in = doGetStream(Command.ITEM_INDEX_FROM_URL, args);) {
			final int itemID = MyResultSet.readInt(in) - 1;
			result = correctNcacheOffsets(in, itemID, nNeighbors, _queryVersion);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	/**
	 * Caches itemOffsetsRS.
	 *
	 * @param _queryVersion
	 *
	 * @return [offset for correctedItem, correctedItem] If item is no longer in
	 *         onItems, corrected item will be different from item. (If
	 *         nNeighbors == 0, item won't be corrected, but offset will be -1.)
	 */
	@SuppressWarnings("resource")
	private @NonNull int[] correctNcacheOffsets(final @NonNull DataInputStream in, final int itemID,
			final int nNeighbors, final int _queryVersion) {
		int itemOffset = MyResultSet.readInt(in) - 1;
		int newItemID = itemID;

		// nNeighbors<=0 only from Art setInitialSelectedItem & clickThumb
		if (itemOffset < 0 || nNeighbors > 0) {
			final int minIndex = MyResultSet.readInt(in);
			final ResultSet itemOffsetsRS = new MyResultSet(in);

			newItemID = ensureItem(itemOffset, itemOffsetsRS, itemID);
			assert (itemOffset < 0) == (newItemID != itemID) : "itemID=" + itemID + " newItemID=" + newItemID
					+ " itemOffset=" + itemOffset;
			if (newItemID != itemID) {
				// For new items, Servlet only returns a forward range
				itemOffset = minIndex;
			}
			itemInfo.update(newItemID, itemOffset, minIndex, itemOffsetsRS, _queryVersion);
		}
		final int[] result = { itemOffset, newItemID };

		// System.out.println("ServletInterface.itemIndexInternal return "
		// + UtilString.valueOfDeep(result) + "\n cached itemInfo="
		// + itemInfo);

		return result;
	}

	private static int ensureItem(final int itemIndex, final @NonNull ResultSet itemOffsetsRS, final int itemID) {
		int result = itemID;
		if (itemIndex < 0) {
			try {
				itemOffsetsRS.next();
				result = itemOffsetsRS.getInt(1);
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	@SuppressWarnings("null")
	private static @NonNull String[][] getDatabases(final @NonNull String _databaseDescs) {
		// String[][] databases = null;
		// if (_databaseDescs != null) {
		final String[] s = UtilString.splitSemicolon(_databaseDescs);
		final String[][] databases = new String[s.length][];
		for (int i = 0; i < s.length; i++) {
			databases[i] = UtilString.splitComma(s[i]);
			// System.out.println(s[i]);
		}
		// }
		return databases;
	}

	private @NonNull StringBuilder processedQueue = new StringBuilder();

	public final boolean isCorrelations;

	synchronized void printUserAction(final @NonNull String x) {
		assert x.length() > 0;
		if (processedQueue.length() > 0) {
			processedQueue.append(";");
		}
		processedQueue.append(x);
	}

	private synchronized @Nullable String flushUserActions() throws UnsupportedEncodingException {
		String result = null;
		if (processedQueue.length() > 0) {
			final String s = processedQueue.toString();
			assert s.length() > 0 : "'" + processedQueue + "' length=" + processedQueue.length();
			result = URLEncoder.encode(s, "UTF-8");
			assert result.length() > 0 : s;
			processedQueue = new StringBuilder();
		}
		return result;
	}

	void reorderItems(final int facetTypeOrSpecial) {
		final String[] args = { Integer.toString(facetTypeOrSpecial) };
		dontGetStream(Command.REORDER_ITEMS, args);
		// decacheOffsets();
	}

	void restrict() {
		dontGetStream(Command.RESTRICT, null);
	}

	@NonNull
	ResultSet addItemFacet(final int facet, final int item) {
		final String[] args = { Integer.toString(facet), Integer.toString(item) };
		return getResultSet(Command.ADD_ITEM_FACET, args);
	}

	@NonNull
	ResultSet addItemsFacet(final int facet, final @NonNull OnItemsTable table) {
		final String[] args = { Integer.toString(facet), table.toString() };
		return getResultSet(Command.ADD_ITEMS_FACET, args);
	}

	@NonNull
	ResultSet removeItemsFacet(final int facet, final @NonNull OnItemsTable table) {
		final String[] args = { Integer.toString(facet), table.toString() };
		return getResultSet(Command.REMOVE_ITEMS_FACET, args);
	}

	@NonNull
	ResultSet addChildFacet(final int facet, final @NonNull String name) {
		final String[] args = { Integer.toString(facet), name };
		return getResultSet(Command.ADD_CHILD_FACET, args);
	}

	@NonNull
	ResultSet removeItemFacet(final int facet, final int item) {
		final String[] args = { Integer.toString(facet), Integer.toString(item) };
		return getResultSet(Command.REMOVE_ITEM_FACET, args);
	}

	@NonNull
	ResultSet reparent(final int parent, final int child) {
		final String[] args = { Integer.toString(parent), Integer.toString(child) };
		return getResultSet(Command.REPARENT, args);
	}

	void writeback() {
		final String[] args = {};
		dontGetStream(Command.WRITEBACK, args);
	}

	void revert(final @NonNull String date) {
		final String[] args = { date };
		dontGetStream(Command.REVERT, args);
	}

	void rotate(final int item, final int degrees) {
		final String[] args = { Integer.toString(item), Integer.toString(degrees) };
		dontGetStream(Command.ROTATE, args);
	}

	void rename(final int facetID, final @NonNull String newName) {
		final String[] args = { Integer.toString(facetID), newName };
		dontGetStream(Command.RENAME, args);
	}

	/**
	 * @param facets
	 *            A comma-delimited list of facet IDs
	 * @return corrsponding names
	 */
	@NonNull
	ResultSet facetNames(final @NonNull String facets) {
		final String[] args = { facets };
		return getResultSet(Command.FACET_NAMES, args);
	}

	void setItemDescription(final int currentItem, final @NonNull String description) {
		final String[] args = { Integer.toString(currentItem), description };
		dontGetStream(Command.SET_ITEM_DESCRIPTION, args);
	}

	@NonNull
	String[] opsSpec(final int session) {
		final String[] args = { Integer.toString(session) };
		try (final MyResultSet rs = getResultSet(Command.OPS_SPEC, args);) {
			return (String[]) rs.getValues(1);
		}
	}

	@NonNull
	String randomOpsSpec() {
		String result = null;
		try (final DataInputStream in = doGetStream(Command.RANDOM_OPS_SPEC, null);) {
			result = MyResultSet.readString(in);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	@NonNull
	String getSession() {
		return sessionID;
	}

	public void loseSession(final @NonNull String session) {
		final String[] args = { session };
		dontGetStream(Command.LOSE_SESSION, args);
	}

	/**
	 * Either result component can be null: first if !needBaseCounts, second if
	 * no candidates.
	 *
	 * Callers are responsible for closing ResultSets!
	 *
	 * @return [BaseCounts, CandidateCounts]
	 */
	@SuppressWarnings("resource")
	@NonNull
	public MyResultSet[] onCountMatrix(final @NonNull String facetsOfInterest, final @NonNull String candidates,
			final boolean isRestrictedData, final boolean needBaseCounts) {
		// System.out.println("onCountMatrix " + isRestrictedData);
		final String[] args = { facetsOfInterest, candidates, asBoolean(isRestrictedData), asBoolean(needBaseCounts) };
		MyResultSet[] result = null;
		try (final DataInputStream in = doGetStream(Command.ON_COUNT_MATRIX, args);) {
			final boolean needCandidateCounts = candidates.length() > 0;
			assert needBaseCounts || needCandidateCounts;

			final MyResultSet[] result1 = { needBaseCounts ? new MyResultSet(in) : null,
					needCandidateCounts ? new MyResultSet(in) : null, };
			result = result1;
		} catch (final IOException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	private static @NonNull String asBoolean(final boolean value) {
		return value ? "1" : "0";
	}

	@NonNull
	ResultSet[] topCandidates(final @NonNull String perspectiveIDexpr, final int maxCandidates) {
		final String[] args = { perspectiveIDexpr, Integer.toString(maxCandidates) };
		final ResultSet[] result = new ResultSet[2];
		try (final DataInputStream in = doGetStream(Command.TOP_CANDIDATES, args);) {
			result[0] = new MyResultSet(in);
			result[1] = new MyResultSet(in);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	private static final class ItemInfo implements Serializable {

		int item;
		int minIndex;
		@NonNull
		ResultSet itemOffsetsRS = MyResultSet.DUMMY_RS;
		int itemOffset;
		int queryVersion;

		ItemInfo() {
		}

		/**
		 * @param _minIndex
		 *            offset of first item in _itemOffsets
		 * @param _itemOffsetsRS
		 *            single column of items at successive offsets
		 * @param _queryVersion
		 */
		void update(final int _itemID, final int _itemOffset, final int _minIndex,
				final @NonNull ResultSet _itemOffsetsRS, final int _queryVersion) {
			try {
				itemOffsetsRS.close();
			} catch (final SQLException e) {
				e.printStackTrace();
			}
			item = _itemID;
			itemOffset = _itemOffset;
			minIndex = _minIndex;
			itemOffsetsRS = _itemOffsetsRS;
			queryVersion = _queryVersion;
		}

		/**
		 * @return exclusive max
		 */
		int maxIndex() {
			return minIndex + MyResultSet.nRowsNoThrow(itemOffsetsRS);
		}

		@Override
		public String toString() {
			final StringBuilder buf = new StringBuilder();
			buf.append("onItems[").append(itemOffset).append("] = ").append(item);
			final String records = MyResultSet.valueOfDeep(itemOffsetsRS, 50);
			buf.append("; range [").append(minIndex).append("-").append(maxIndex()).append(">\n").append(records);
			return UtilString.toString(this, buf);
		}
	}
}
