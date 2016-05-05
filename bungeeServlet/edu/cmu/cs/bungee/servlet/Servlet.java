package edu.cmu.cs.bungee.servlet;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyLogger;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * This class is only referred to in web.xml
 */
public class Servlet extends HttpServlet {

	protected static final long serialVersionUID = 8922913873736902656L;

	private static final Map<Integer, Database> SESSIONS = new HashMap<>();

	private static final Map<Database, DatabaseEditing> DATABASE_EDITORS = new HashMap<>();

	private static final Random SESSION_GENERATOR = new Random();

	private final MyLogger myLogger = MyLogger.getMyLogger(Servlet.class);

	private void logp(final String msg, final Level level, final Throwable e, final String sourceMethod) {
		MyLogger.logp(myLogger, msg, level, e, "Servlet", sourceMethod);
	}

	private void logp(final String msg, final Level level, final String sourceMethod) {
		MyLogger.logp(myLogger, msg, level, "Servlet", sourceMethod);
	}

	/**
	 * Using
	 *
	 * if () {errorp();}
	 *
	 * instead of myAssertp() prevents unnecessary evaluation of msg.
	 */
	private void errorp(final String msg, final String sourceMethod) {
		logp(msg, MyLogger.SEVERE, sourceMethod);
		throw new AssertionError(msg);
	}

	@Override
	// Initialize DBs specified by config parameter "initDBs"
	public void init(final ServletConfig config) throws ServletException {
		super.init(config);
		final String dbNamesString = config.getInitParameter("initDBs");
		if (UtilString.isNonEmptyString(dbNamesString)) {
			final String server = config.getInitParameter("server");
			final String user = config.getInitParameter("user");
			final String pass = config.getInitParameter("pwd");
			if (!UtilString.isNonEmptyString(server) || !UtilString.isNonEmptyString(user)
					|| !UtilString.isNonEmptyString(pass)) {
				errorp("Missing init arg(s): " + server + ", " + user + ", " + pass, "ensureDBsInitted");
			}
			assert server != null && user != null && pass != null;
			final String[] dbNames = dbNamesString.split(",");
			logp("Ensuring all " + dbNames.length + " DBs initted: " + dbNamesString, MyLogger.WARNING,
					"ensureDBsInitted");
			for (final String dbName : dbNames) {
				logp("Initializing " + dbName, MyLogger.INFO, "ensureDBsInitted");
				assert dbName != null;
				try (final Database db = new Database(server, dbName, user, pass, false);) {
					// Each session gets it's own db, so no reason to keep them.
					db.close();
				} catch (final Throwable e) {
					logp("Skipping ensureDBInitted of " + dbName + " because: ", MyLogger.SEVERE, e,
							"ensureDBsInitted");
				}
			}
			logp("All " + dbNames.length + " DBs initted.", MyLogger.WARNING, "ensureDBsInitted");
		}
	}

	@Override
	synchronized public void destroy() {
		final Collection<Database> dbs = SESSIONS.values();
		logp("Closing " + dbs.size() + " DBs: " + dbs, MyLogger.WARNING, "destroy");
		for (final Database db : dbs) {
			try {
				closeDB(null, db);
			} catch (final SQLException e) {
				logp("Can't close database " + db + " because: ", MyLogger.SEVERE, e, "destroy");
			}
		}
		SESSIONS.clear();
		logp("All " + dbs.size() + " DBs closed.", MyLogger.WARNING, "destroy");
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
		// Called when you go to a bookmark in a browser
		doPost(request, response);
	}

	/**
	 * Just calls logp(MyLogger.FINE)
	 */
	void logRequest(final HttpServletRequest request) {
		final StringBuilder buf = new StringBuilder();
		buf.append("\ngetRequestURL=").append(request.getRequestURL().toString()).append("\ngetQueryString=")
				.append(request.getQueryString()).append("\ngetRemoteHost=").append(request.getRemoteHost());
		buf.append("\ngetCharacterEncoding=").append(request.getCharacterEncoding());
		buf.append("\nHeaders:");
		final Enumeration<String> headers = request.getHeaderNames();
		while (headers.hasMoreElements()) {
			final String header = headers.nextElement();
			buf.append("\n").append(header).append(": ").append(request.getHeader(header));
		}
		buf.append("\nAttributes:");
		final Enumeration<String> attributes = request.getAttributeNames();
		while (attributes.hasMoreElements()) {
			final String attribute = attributes.nextElement();
			buf.append("\n").append(attribute).append(": ").append(request.getAttribute(attribute));
		}
		logp(buf.toString(), MyLogger.FINE, "logRequest");
	}

	@Override
	// Make sure the Tomcat Connector is configured for
	// UTF_8, e.g. <Connector port="80" URIEncoding="UTF-8"/>. Otherwise
	// getParameter will decode wrong.
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
		logRequest(request);
		assert false : "Should not enableAssertions on Servlet";
		final Command command = Command.valueOf(request.getParameter("command"));
		response.setContentType("application/octet-stream");
		response.setHeader("pragma", "no-cache");
		try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(
				new BufferedOutputStream(response.getOutputStream()), new Deflater(Deflater.BEST_COMPRESSION)));) {
			// Not sure whether this is needed. Must make sure out isn't closed
			// until after reportDoPostError.
			try {
				final Integer session = command == Command.CONNECT ? connectCommand(request) : lookupSession(request);
				logp("...doPost " + command + " to db", MyLogger.FINE, "doPost");
				doPostInternal(request, command, out, session);
			} catch (final Throwable e) {
				reportDoPostError(response, request, e);
			}
		}
		logp("doPost " + command + " done", MyLogger.FINE, "doPost");
	}

	private void reportDoPostError(final HttpServletResponse response, final HttpServletRequest request,
			final Throwable e) throws IOException {
		String message = null;
		try (final Database db = SESSIONS.get(lookupSession(request));) {
			message = "In Database=" + db + ", could not " + request.getQueryString() + " because " + e
					+ (e.getCause() != null ? "\nbecause\n" + e.getCause() : "")
					+ (db == null ? "\n sessions: " + SESSIONS : "") + "\n"
					+ UtilString.join(e.getStackTrace(), "\nat ");

			logp(message, MyLogger.SEVERE, "reportDoPostError");
			if (response.isCommitted()) {
				logp("Can't send errMsg to client because response is already committed.\n" + message, MyLogger.SEVERE,
						"reportDoPostError");
			} else {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
			}
		} catch (final SQLException e1) {
			throw new IOException(message, e1);
		}
	}

	private synchronized static Integer lookupSession(final HttpServletRequest request) {
		final String session = request.getParameter("session");
		return session == null ? null : Integer.valueOf(session);
	}

	/**
	 * Only called by doPost
	 *
	 * Create session and a database for it.
	 *
	 * @return new session
	 * @throws SQLException
	 */
	@SuppressWarnings("resource")
	// "resource" is for db.
	private Integer connectCommand(final HttpServletRequest request) throws ServletException, SQLException {
		final ServletConfig config = getServletConfig();
		final String[] dbNames = config.getInitParameter("dbs").toLowerCase().split(",");
		String dbName = request.getParameter("arg1");
		if (!UtilString.isNonEmptyString(dbName)) {
			dbName = dbNames[0];
		}
		dbName = dbName.toLowerCase();
		assert dbName != null;
		Integer session = null;
		final String server = Util.nonNull(config.getInitParameter("server"));
		final String pass = Util.nonNull(config.getInitParameter("pwd"));
		final String user = Util.nonNull(config.getInitParameter("user"));
		if (isAuthorized(dbName + request.getRemoteHost(), config, dbNames, dbName, user)) {
			final boolean noTemporaryTables = Boolean.parseBoolean(request.getParameter("arg3"));
			final Level level = Util.nonNull(Level.parse(request.getParameter("arg4")));
			myLogger.setLevel(level);
			final String slowQueryTimeString = request.getParameter("arg2");
			final Database db = new Database(server, dbName, user, pass, noTemporaryTables);
			db.setLogLevel(level);
			session = generateSession(db);
			if (slowQueryTimeString != null) {
				db.jdbc.slowQueryTime = Integer.parseInt(slowQueryTimeString);
			}
		} else {
			final boolean databaseExists = JDBCSample.getMySqlJDBCSample(server, "sys", user, pass)
					.databaseExists(dbName);
			throw new ServletException(databaseExists
					? "Your IP address, " + request.getRemoteHost() + ", is not authorized to use database " + dbName
					: "Database " + dbName + " does not exist.");
		}
		return session;
	}

	/**
	 * Only called by connectCommand
	 */
	private synchronized static Integer generateSession(final Database db) {
		final Integer session = Integer.valueOf(SESSION_GENERATOR.nextInt());
		SESSIONS.put(session, db);
		return session;
	}

	/**
	 * Only called by connectCommand
	 */
	private static boolean isAuthorized(final String requestIP, final ServletConfig config, final String[] dbNames,
			final String dbName, final String user) {
		boolean isAuthorized = user.equals("root") || ArrayUtils.contains(dbNames, dbName);
		if (!isAuthorized) {
			final String[] authorizedIPs = config.getInitParameter("IPpermissions").split(",");
			for (int i = 0; i < authorizedIPs.length && !isAuthorized; i++) {
				isAuthorized = requestIP.startsWith(authorizedIPs[i]);
			}
		}
		return isAuthorized;
	}

	/**
	 * Only called by doPost
	 */
	@SuppressWarnings({ "null", "resource" })
	// "resource" is for db. "null" is for db, arg1string, etc.
	private void doPostInternal(final HttpServletRequest request, final Command command,
			final @NonNull DataOutputStream out, final Integer session)
			throws IOException, ServletException, SQLException, ParseException, InterruptedException {
		final Database db = SESSIONS.get(session);
		if (db == null && command != Command.CLOSE) {
			errorp("db is null!: session=" + session + " command=" + command, "doPostInternal");
		}
		handleUserActions(session, request);
		final String arg1string = request.getParameter("arg1");
		final String arg2string = request.getParameter("arg2");
		final String arg3string = request.getParameter("arg3");
		final ServletConfig config = getServletConfig();
		switch (command) {
		case CONNECT:
			Encoder.writeString(session.toString(), out);
			Encoder.writeString(db.dbDescs(config.getInitParameter("dbs")), out);
			Encoder.writeInt(db.facetCount(), out);
			Encoder.writeInt(db.itemCount(), out);
			Encoder.writeBoolean(db.isCorrelations(), out);
			final String[] globals = db.getGlobals();
			if (Boolean.valueOf(globals[3])) {
				final DatabaseEditing databaseEditing = new DatabaseEditing(db);
				databaseEditing.setLogLevel(myLogger.getLevel());
				DATABASE_EDITORS.put(db, databaseEditing);
				myLogger.logp("databaseEditing =" + databaseEditing, MyLogger.INFO, "doPostInternal");
			}
			for (final String global : globals) {
				Encoder.writeString(global, out);
			}
			db.initFacetTypes(out);
			break;
		case ABOUT_COLLECTION:
			Encoder.writeString(db.aboutCollection(), out);
			break;
		case ONCOUNTS_IGNORING_FACET:
			final int ignoreParent = getIntParameter(request, "arg2");
			db.getCountsIgnoringFacet(arg1string, ignoreParent, out);
			break;
		case ITEM_URL:
			int item = getIntParameter(request, "arg1");
			Encoder.writeString(db.getItemURL(item), out);
			break;
		case FILTERED_COUNTS:
			db.getFilteredCounts(OnItemsTable.valueOf(arg1string), out);
			break;
		case PREFETCH:
			final FetchType fetchType = FetchType.values()[Integer.parseInt(arg2string)];
			db.prefetch(arg1string, fetchType, out);
			break;
		case LETTER_OFFSETS:
			final int parentFacetID = getIntParameter(request, "arg1");
			db.getLetterOffsets(parentFacetID, arg2string, out);
			break;
		case FACET_NAMES:
			db.getNames(arg1string, out);
			break;
		case OFFSET_ITEMS:
			db.offsetItems(getIntParameter(request, "arg1"), getIntParameter(request, "arg2"),
					OnItemsTable.valueOf(arg3string), out);
			break;
		case REORDER_ITEMS:
			db.reorderOffsetQueries(getIntParameter(request, "arg1"));
			break;
		case THUMBS:
			db.getThumbs(arg1string, getIntParameter(request, "arg2"), getIntParameter(request, "arg3"),
					getIntParameter(request, "arg4"), out);
			break;
		case DESC_AND_IMAGE:
			db.getDescAndImage(getIntParameter(request, "arg1"), getIntParameter(request, "arg2"),
					getIntParameter(request, "arg3"), getIntParameter(request, "arg4"), out);
			break;
		case ITEM_INFO:
			item = getIntParameter(request, "arg1");
			db.getItemInfo(item, out);
			break;
		case IMPORT_FACET:
			boolean isRestrictedData = getBooleanParameter(request, "arg2");
			db.getFacetInfo(arg1string, isRestrictedData, out);
			break;
		case UPDATE_ON_ITEMS:
			final int onCount = db.updateOnItems(arg1string /* subQuery */);
			Encoder.writeInt(onCount, out);
			if (onCount > 0) {
				final OnItemsTable table = OnItemsTable.valueOf(arg3string);
				item = getIntParameter(request, "arg2");
				final int nNeighbors = getIntParameter(request, "arg4");
				handleItemIndex(item, table, nNeighbors, db, out);
			}
			break;
		case ITEM_INDEX_FROM_URL:
			item = db.getItemFromURL(arg1string);
			// item==-1 if not found
			Encoder.writeInt(item + 1, out);
			OnItemsTable table = OnItemsTable.valueOf(arg2string);
			int nNeighbors = getIntParameter(request, "arg3");
			handleItemIndex(item, table, nNeighbors, db, out);
			break;
		case ITEM_OFFSET:
			item = getIntParameter(request, "arg1");
			table = OnItemsTable.valueOf(arg2string);
			nNeighbors = getIntParameter(request, "arg3");
			handleItemIndex(item, table, nNeighbors, db, out);
			break;
		case SET_ITEM_DESCRIPTION:
			item = getIntParameter(request, "arg1");
			db.setItemDescription(item, arg2string /* description */);
			break;
		case ON_COUNT_MATRIX:
			isRestrictedData = getBooleanParameter(request, "arg3");
			final boolean needBaseCounts = getBooleanParameter(request, "arg4");
			db.onCountMatrix(arg1string /* facets */, arg2string /* candidates */, isRestrictedData, needBaseCounts,
					out);
			break;
		case TOP_CANDIDATES:
			final int maxCandidates = getIntParameter(request, "arg2");
			db.topCandidates(arg1string, maxCandidates, out);
			break;
		case LOSE_SESSION:
			int opsSession = getIntParameter(request, "arg1");
			db.loseSession(opsSession);
			break;
		case OPS_SPEC:
			opsSession = getIntParameter(request, "arg1");
			db.opsSpec(opsSession, out);
			break;
		case RANDOM_OPS_SPEC:
			db.randomOpsSpec(out);
			break;
		case RESTRICT:
			db.restrict();
			break;
		case CLOSE:
			final String s = "CLOSE " + db;
			Encoder.writeString(s, out);
			closeDB(session, db);
			break;

		// Remainder are editing commands
		case ADD_ITEM_FACET:
			int facet = getIntParameter(request, "arg1");
			item = getIntParameter(request, "arg2");
			getDatabaseEditing(db).addItemFacet(facet, item, out);
			break;
		case ADD_ITEMS_FACET:
			facet = getIntParameter(request, "arg1");
			table = OnItemsTable.valueOf(arg2string);
			getDatabaseEditing(db).addItemsFacet(facet, table, out);
			break;
		case REMOVE_ITEM_FACET:
			facet = getIntParameter(request, "arg1");
			item = getIntParameter(request, "arg2");
			getDatabaseEditing(db).removeItemFacet(facet, item, out);
			break;
		case REMOVE_ITEMS_FACET:
			facet = getIntParameter(request, "arg1");
			table = OnItemsTable.valueOf(arg2string);
			getDatabaseEditing(db).removeItemsFacet(facet, table, out);
			break;
		case ADD_CHILD_FACET:
			facet = getIntParameter(request, "arg1");
			getDatabaseEditing(db).addChildFacet(facet, arg2string /* name */, out);
			break;
		case REPARENT:
			final int parent = getIntParameter(request, "arg1");
			final int child = getIntParameter(request, "arg2");
			getDatabaseEditing(db).reparent(parent, child, out);
			break;
		case WRITEBACK:
			getDatabaseEditing(db).writeBack();
			break;
		case REVERT:
			getDatabaseEditing(db).revert(arg1string /* date */, config.getInitParameter("user"),
					config.getInitParameter("pwd"));
			break;
		case ROTATE:
			item = getIntParameter(request, "arg1");
			final int clockwiseDegrees = getIntParameter(request, "arg2");
			getDatabaseEditing(db).rotate(item, clockwiseDegrees);
			break;
		case RENAME:
			facet = getIntParameter(request, "arg1");
			getDatabaseEditing(db).rename(facet, arg2string /* name */);
			break;

		default:
			throw (new ServletException("Unknown command: " + command));
		}
		logp("...doPost " + command + " writing", MyLogger.FINE, "doPostInternal");
	}

	private DatabaseEditing getDatabaseEditing(final Database db) {
		final DatabaseEditing result = DATABASE_EDITORS.get(db);
		if (result == null) {
			errorp("databaseEditing is null", "getDatabaseEditing");
		}
		return result;
	}

	// Called by doPostInternal and destroy
	private synchronized void closeDB(final Integer session, final Database db) throws SQLException {
		logp("Closing database " + db, MyLogger.INFO, "close");
		if (db != null) {
			db.close();
		}
		SESSIONS.remove(session);
	}

	// Only called by doPostInternal
	private void handleUserActions(final Integer session, final HttpServletRequest request) throws SQLException {
		final String actionsString = request.getParameter("userActions");
		if (actionsString != null) {
			@SuppressWarnings("resource")
			final Database db = SESSIONS.get(session);
			final String[] actions = UtilString.splitSemicolon(actionsString);
			for (final String action : actions) {
				final String[] actionString = UtilString.splitComma(Util.nonNull(action));
				if (actionString.length != 5) {
					errorp("Bad argString: '" + action + "' in '" + actionsString + "'", "handleUserActions");
				}
				final int actionIndex = Integer.parseInt(actionString[0]);
				final int location = Integer.parseInt(actionString[1]);
				final String object = actionString[2];
				final int modifiers = Integer.parseInt(actionString[3]);
				final int onCount = Integer.parseInt(actionString[4]);
				final String remoteHost = request.getRemoteHost();
				assert remoteHost != null;
				db.printUserAction(remoteHost, session.intValue(), actionIndex, location, object, modifiers, onCount);
			}
		}
	}

	/**
	 * Only called by doPostInternal
	 *
	 * Write selectedItem offset+1 (or 0 if selectedItem no longer satisfies the
	 * query) and, if nNeighbors>0 or selectedItem no longer satisfies the
	 * query, the minOffset for items in the rs written by offsetItems.
	 */
	private void handleItemIndex(final int selectedItem, final @NonNull OnItemsTable table, final int nNeighbors,
			final Database db, final @NonNull DataOutputStream out) throws ServletException, SQLException, IOException {
		if (nNeighbors < 0) {
			errorp("nNeighbors==" + nNeighbors, "handleItemIndex");
		}
		int itemOffset = selectedItem < 0 ? -1 : db.itemOffset(selectedItem, table);

		// servletInterface will subtract 1 from the result.
		Encoder.writeInt(itemOffset + 1, out);

		if (nNeighbors > 0 || itemOffset < 0) {
			if (itemOffset < 0) {
				itemOffset = 0;
			}
			final int minOffset = Math.max(0, itemOffset - nNeighbors);
			final int maxOffsetExclusive = itemOffset + nNeighbors + 1;
			Encoder.writeInt(minOffset, out);
			db.offsetItems(minOffset, maxOffsetExclusive, table, out);
		}
	}

	// Only called by doPostInternal
	private static boolean getBooleanParameter(final HttpServletRequest request, final String argSpec) {
		return getIntParameter(request, argSpec) > 0;
	}

	private static int getIntParameter(final HttpServletRequest request, final String argSpec) {
		final String arg = request.getParameter(argSpec);
		return Integer.parseInt(arg);
	}

}
