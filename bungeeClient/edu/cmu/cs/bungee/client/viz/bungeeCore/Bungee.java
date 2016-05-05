package edu.cmu.cs.bungee.client.viz.bungeeCore;

import static edu.cmu.cs.bungee.javaExtensions.Util.assertNotMouseProcess;
import static edu.cmu.cs.bungee.javaExtensions.Util.nButtons;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.constrain;
import static edu.cmu.cs.bungee.javaExtensions.UtilString.elapsedTime;

/*

 Bungee View lets you search, browse, and data-mine an image collection.
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at
 mad@cs.cmu.edu

 */

/**
 * ToDo:
 *
 * Partial unrestrict
 *
 * Copy text in text boxes.
 *
 * Deeply nested facets will never show up in TopTags.
 *
 * Text search is ignored in Explanations.
 *
 * Fancy thumb scrolling: when sorted, show facet labels to the right of the
 * scroll bar when scrolling. Label the first thumb with each facet value (on
 * top). havea a button to lock each column.
 *
 * Brush grid scrollbar if ordered by an attribute. scrollbar intervals
 * correspond to tags. Intervals can be computed from perspective cumOnCounts.
 *
 * History mechanism; Tabs for history (query, selected images), top tags, favorites,
 * Recent/Saved Items galleries.
 *
 * in expert mode, could have tabs like informedia. A "Save" button would save
 * the current query in a new tab. Then you could have a tab menu to AND or OR
 * with the current query. You could also do "compare" where the current query
 * becomes the OR and you restrict to either one.
 *
 * Shot collector
 *
 * XML query rep [parent query, searches, required, excluded] & list rep.
 * Bookmark with the former; send the latter to the server, and construct the
 * query there. Markup should have been HTML.
 *
 * PerspectiveLists show tags that have zero totalCount in restricted set.
 *
 * Query for percentage on even for deeply nested facets. Pass more arguments
 * analogous to relevantFacets, to get counts for nested facets of the selected
 * item along with all the other counts.
 *
 * When you switch modes, bar label counts don't always line up. Maybe the
 * problem is when they've previsouly been displayed in selected item frame?
 *
 * HP database: lose centuries and only group dates by decade. Days should be '1
 * June 1944' not '1'. Music database: many Labels' names start with blank
 * space; Artists not alphabetized right. HistoryMakers - why are there
 * singletons, e.g. Date of Interview > 1993 > January?
 *
 * Document BV ontology. eg are tags and categories the same kind of object?
 * They use the same color scheme, so why can't you click on Oscar to select
 * Oscar-winning movies?
 *
 * Limit inf search names to width of frame, with rollover expansion like
 * summary description.
 *
 * Tweedie-style bars showing expected, current, current except for us, current
 * except for 1 other.
 *
 * Sort by relevance as one menu option. Cluster by similarity another option.
 * SELECT *, match(facet_names, title) against ('eastwood') relevance FROM item
 * order by relevance desc
 *
 * new attribute for number of clicks (facets and items). Doesn't make sense for
 * facets. Instead color background of bar to show popularity.
 *
 * This file has way too much crap in it, which should be moved to
 * javaExtentions, FacetText, or it's own file.
 *
 * Rationalize startup: print start/end time for creating each frame, and its
 * "delayed init", and see whether we're really saving anything with the extra
 * complication. Also nest instantiatedPerspective inside Perspective.
 *
 * Widget to limit cluster size (especially to 1)
 *
 * Search against facets, and display matches nested as usual, but without
 * adding ancestors to query. cf
 * http://www.cs.cmu.edu/~quixote/DynamicCategorySets.pdf Or list these nested
 * under the search term with checkboxes
 *
 * do search like regular facets. Add bars for search terms to make them look
 * like other categories. Use numerical relevance.
 *
 * Is itunes data good for bungee view? If so,put identical images together.
 * Draw grid over 1 or 2 copies to represent tracks on 1 album. AlsoARTstor
 *
 * Call even item_url & getCountsIgnoringFacet in other threads, in case server
 * connection goes down.
 *
 * add hard-to-see menu of categories to show
 *
 * Editing option: editable description. drag to add properties (right click to
 * delete?). define new properties (right click?). (right?) drag to re-order
 * properties/categories.
 *
 * Endeca automatically drills down when only one tag has non-zero count
 *
 * Tnf.layout - don't create new aptexts unless content changes. Keep two
 * layouts - wrapped & not? Tnf.Layout should check content & children's bounds
 * to see if it really has to do anything. Each tnf can cache its aptexts.
 *
 * Shift-select should work across multiple selections.
 *
 * JOHNS LIST:
 *
 * Reduce false positives.
 *
 * Task-specific interface customization. i.e. browsing interface could be
 * different from EDA interface.
 *
 * Counts don't add up.
 *
 * Store findings (patterns & individual documents). Highlight saved works in
 * new results.
 *
 * LOW PRIORITY:
 *
 * Help: keyboard events(shift, arrow), clippy.
 *
 * Support aggregation operator other than COUNT. For instance, have area depend
 * on sales volume rather than number of companies.
 */

/**
 * To run from command line: C:\Documents and
 * Settings\mad\Desktop\eclipse\workspace\art>java -cp ".;C:/Documents and
 * Settings/mad/Desktop/applet_test/piccoloNEW.jar;C:/Documents and
 * Settings/mad/Desktop/applet_test/piccolox.jar;C:/Documents and
 * Settings/mad/Desktop/mysql-connector-java-3.1.7/mysql-connector-java-3.1.7/mysql-connector-java-3.1.7-bin.jar"
 * -Xrunhprof:cpu=times,doe=n viz.Art
 *
 * or -Xrunhprof:cpu=samples,depth=10,thread=y then
 *
 * java -jar JPerfAnal.jar java.hprof.txt
 *
 * in C:\eclipse\workspace\art
 *
 * For HPJmeter, use -Xrunhprof:cpu=samples,thread=y,depth=20,cutoff=0 and
 * possibly heap=all
 */

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.WindowConstants;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.InformediaQuery;
import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupPaintElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.BungeeFrame;
import edu.cmu.cs.bungee.client.viz.TopTagsViz;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants.Significance;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.grid.GridElementWrapper;
import edu.cmu.cs.bungee.client.viz.grid.ResultsGrid;
import edu.cmu.cs.bungee.client.viz.header.Header;
import edu.cmu.cs.bungee.client.viz.informedia.InformediaClient;
import edu.cmu.cs.bungee.client.viz.selectedItem.SelectedItemColumn;
import edu.cmu.cs.bungee.client.viz.tagWall.Rank;
import edu.cmu.cs.bungee.client.viz.tagWall.TagWall;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.URLQuery;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.threads.QueueThread;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Boundary;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode.PickableMode;
import edu.cmu.cs.bungee.piccoloUtils.gui.Menu;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;
import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.PInputManager;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PActivityScheduler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolox.PFrame;
import jdk.nashorn.internal.ir.annotations.Immutable;

public class Bungee extends PFrame // PApplet
		implements // RedrawCallback,
		WindowListener {

	protected static final long serialVersionUID = 1L;

	static final long MIN_IDLE_TIME = 400L;
	static final long IDLE_POLL_INTERVAL = 200L;

	@NonNull
	Preferences preferences;

	/**
	 * Height, Width of the whole application window
	 */
	private int h;
	public int w;

	/**
	 * Lets anyone (like printUserAction) know we're replaying.
	 */
	private @Nullable Replayer replayer;

	/**
	 * Space on each side of scroll bars. Set in setFontSize to a fraction of
	 * the text size. Always equal to an int.
	 */
	public double internalColumnMargin;
	public double twiceInternalColumnMargin;
	public int intTwiceInternalColumnMargin;
	/**
	 * Essentially @NonNull
	 */
	Query query;
	/**
	 * Essentially @NonNull
	 */
	public String dbName;
	boolean noTemporaryTables;
	boolean isTraceServletCalls;
	public boolean isPrintActions;

	/**
	 * Governs what happens on typing the arrow keys. Null means navigate
	 * through Results list; A Perspective means navigate from this Bar,
	 * (de)selecting the specified adjacent Bar(s).
	 */
	private @Nullable Perspective arrowFocus;

	private @Nullable Item selectedItem;

	transient private @Nullable DocumentShower documentShower;
	private final @NonNull APTextManager apTextManager;

	/****************************************************
	 * Children are essentially @NonNull
	 ****************************************************/

	private Header header;
	private MouseDocLine mouseDoc;
	private InitialHelp help;
	private TagWall tagWall;
	private ResultsGrid grid;
	private SelectedItemColumn selectedItemColumn;
	private TopTagsViz extremeTags;
	/**
	 * TagWall, ExtremeTags, ResultsGrid, SelectedItem
	 */
	public @Immutable List<BungeeFrame> columns;
	/**
	 * HEADER, TagWall, ExtremeTags, ResultsGrid, SelectedItem
	 */
	private @Immutable @NonNull List<BungeeFrame> frames = UtilArray.EMPTY_LIST;

	private final @NonNull KeyEventHandler keyHandler = new KeyEventHandler(this);

	/**
	 * Desired Thread priorities: VizSynchronizer art-1 ThumbLoader art-2
	 * ImageLoader art-2 ItemSetter art-2 GetPerspectiveNames art-3 Highlighter
	 * art-3
	 *
	 * However art is at 4, and the only lower choice is 2. Therefore we settle
	 * for: VizSynchronizer art ThumbLoader art-2 ImageLoader art-2 ItemSetter
	 * art-2 GetPerspectiveNames art-2 Highlighter art-2
	 *
	 */

	/**
	 * Whether the query and frames have been created (though not necessarily
	 * validated). Will be false during an unrestrict.
	 */
	private boolean isReady = false;

	// /**
	// * Used by codeBase() and DocumentShower, but not necessary to either.
	// */
	// final transient @Nullable BasicService basicJNLPservice =
	// Util.maybeGetBasicService();
	//
	// /**
	// * Used only by copyBookmark()
	// */
	// private transient final @Nullable ClipboardService jnlpClipboardService =
	// Util.maybeGetClipboardService();

	/**
	 * initializer creates this from the query portion of the URL sent from the
	 * Browser. It stores it for later use by setInitialState.
	 */
	private @NonNull URLQuery argURLQuery;

	public int slowQueryTime = -1;

	@NonNull
	String server;

	private final @NonNull Set<Perspective> brushedFacets = new HashSet<>();

	@SuppressWarnings("null")
	private final @Immutable @NonNull Set<Perspective> brushedFacetsUnmodifiable = Collections
			.unmodifiableSet(brushedFacets);

	transient public @Nullable InformediaClient informediaClient;

	/**
	 * Essentially @NonNull
	 */
	transient private QueryUpdater queryUpdater;

	private @Nullable File logFile;

	public static void main(final @NonNull String[] args) {
		Util.ignore(new Bungee(args));
	}

	private Bungee(final @NonNull String[] args) {
		// for PFrame
		super("Bungee View Image Collection Browser.  See the forest AND the trees.", false, null);
		assert args.length > 0;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(this);
		argURLQuery = new URLQuery(Util.nonNull(args[0].replace('!', '&')));
		apTextManager = new APTextManager(this);
		preferences = defaultPreferences();
		setFontSize(BungeeConstants.DEFAULT_TEXT_SIZE);
		server = setServer();
		myInitialize();
	}

	private Bungee() {
		assert false : " Warning: Bungee() shouldn't be called.";
		// Applet seems to need this
		argURLQuery = new URLQuery("");
		apTextManager = new APTextManager(this);
		preferences = defaultPreferences();
		setFontSize(BungeeConstants.DEFAULT_TEXT_SIZE);
		server = setServer();
	}

	/**
	 * Startup process:
	 *
	 * 1. {@link #setServer} (via {@link #setDatabase}) stops everything from
	 * any previous database, resets {@link #isReady()}, shows the
	 * "Waiting for <server>" message, starts {@link QueryUpdater}, and returns
	 * so that the message is displayed.
	 *
	 * 2. {@link QueryUpdater} initializes {@link Query} and its background
	 * processes in the background and then schedules {@link #createFrames} to
	 * run in the mouse process.
	 *
	 * 3. {@link #createFrames} removes the "Waiting" message and shows any
	 * error creating query. Otherwise, it adds all the frames, sets isReady,
	 * and calls {@link #validateIfReady}.
	 *
	 * 4. If isReady and not too-small window, {@link #validateIfReady} computes
	 * sizes for and validates all frames. (resize and small-window-OK button
	 * each retry validateIfReady.)
	 *
	 * 5. {@link #setTooSmall}, via {@link #setFramesVisibility}, handles
	 * {@link TagWall} visibility. The first time {@link TagWall#paint} is
	 * called, it schedules the initialization of tagWall, selectedItem, grid,
	 * and {@link #setInitialState}. setInitialState sets query state, selected
	 * item, displayed PVs, and {@link Replayer} any specified session.
	 *
	 * @see edu.umd.cs.piccolox.PFrame#initialize()
	 */
	@Override
	public void initialize() {
		super.initialize();
		assert !isReady();
		final PCanvas canvas = getCanvas();
		canvas.setPanEventHandler(null);
		canvas.setZoomEventHandler(null);
		grabFocus();
	}

	/**
	 * initialize() is called during Bungee.<init>. This is called by Bungee.
	 * <init> after everything else is done.
	 */
	private void myInitialize() {
		// must redirect System.out (to log file) before using it.
		maybeInitLogFile();
		System.out.println("Bungee.myInitialize argURLQuery=\n" + argURLQuery.format() + "\nenableAssertions="
				+ Util.areAssertionsEnabled() + "\nMIN_IDLE_TIME=" + MIN_IDLE_TIME);

		// This makes PLayer smaller than Bungee, but it at least makes it have
		// Integer dimensions.
		final PCamera cam = getCanvas().getCamera();
		int _w = argURLQuery.getIntArgument("width");
		if (_w <= 0) {
			_w = (int) cam.getWidth();
		}
		int _h = argURLQuery.getIntArgument("height");
		if (_h <= 0) {
			_h = (int) cam.getHeight();
		}
		setSize(_w, _h);
		cam.addPropertyChangeListener(PNode.PROPERTY_BOUNDS, new PropertyChangeListener() {
			@Override
			public void propertyChange(final PropertyChangeEvent propertyChangeEvent) {
				final PBounds bounds = (PBounds) propertyChangeEvent.getNewValue();
				setSize((int) bounds.getWidth(), (int) bounds.getHeight());
			}
		});

		noTemporaryTables = Boolean.parseBoolean(argURLQuery.getArgument("noTemporaryTables", "false"));
		final String _slowQueryTime = argURLQuery.getArgument("slowQueryTime");
		if (_slowQueryTime.length() > 0) {
			slowQueryTime = Integer.parseInt(_slowQueryTime);
		}

		isTraceServletCalls = Boolean.parseBoolean(argURLQuery.getArgument("isTraceServletCalls", "false"));
		isPrintActions = Boolean.parseBoolean(argURLQuery.getArgument("isPrintActions", "false"));

		initPreferences();

		final String _dbName = argURLQuery.getArgument("db");
		if (_dbName.length() > 0) {
			maybeSetDatabase(_dbName);
		} else {
			startQueryUpdater();
		}
	}

	private static final @NonNull SimpleDateFormat MY_DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd_HH.mm");

	private void maybeInitLogFile() {
		final URLQuery _argURLQuery = argURLQuery;
		assert _argURLQuery != null;
		final String logsDirectoryName = _argURLQuery.getArgument("logsDirectory");
		if (logsDirectoryName.length() > 0) {
			final File logsDirectory = new File(logsDirectoryName);
			assert logsDirectory.isDirectory() : logsDirectory;
			final String _dbName = _argURLQuery.getArgument("db");
			assert _dbName
					.length() > 0 : "You must specify a Database on the command line in order to log output to a file.";
			final String logFileName = _dbName + "_" + MY_DATE_FORMAT.format(new Date());
			final File logDirectory = new File(logsDirectory, logFileName);
			logDirectory.mkdir();
			logFile = new File(logDirectory, logFileName + ".html");
			try {
				final boolean createNewFile = logFile.createNewFile();
				assert createNewFile : logFile;
				@SuppressWarnings("resource")
				final PrintStream logStream = new PrintStream(logFile) {

					@Override
					public void println(String x) {
						x = x.replace("<", "&lt;").replace(">", "&gt;");
						final Matcher m = ESCAPE_HTML_PATTERN.matcher(x);
						x = m.replaceAll("<$1>");
						super.println(x);
					}

				};
				System.setOut(logStream);

				// Preserve whitespace and word-wrap.
				System.out.println(
						"<style type=\"text/css\"> body {white-space: pre-wrap;font-family: monospace, sans-serif;}</style>");
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}

	static final Pattern ESCAPE_HTML_PATTERN = Pattern.compile("&lt;(/?(?:img|style).*?)&gt;");

	public void grabFocus() {
		final PInputManager pInputManager = getCanvas().getRoot().getDefaultInputManager();
		pInputManager.setKeyboardFocus(keyHandler);

	}

	private void initPreferences() {
		final URLQuery _argURLQuery = argURLQuery;
		assert _argURLQuery != null;
		final int fontSize = _argURLQuery.getIntArgument("fontSize");
		// Must setFontSize to initialize font
		setFontSize(fontSize == Integer.MIN_VALUE ? getFontSize() : fontSize);

		final String mode = _argURLQuery.getArgument("mode");
		if ("expert".equalsIgnoreCase(mode)) {
			expertMode();
		}

		final String onFeatures = _argURLQuery.getArgument("onFeatures");
		if (onFeatures.length() > 0) {
			setPreferences(preferences, onFeatures, true);
		}

		final String offFeatures = _argURLQuery.getArgument("offFeatures");
		if (offFeatures.length() > 0) {
			setPreferences(preferences, offFeatures, false);
		}
	}

	private void initInformedia() {
		final int informediaPort = argURLQuery.getIntArgument("informediaPort");
		if (informediaPort != Integer.MIN_VALUE) {
			try {
				informediaClient = new InformediaClient(this, informediaPort,
						argURLQuery.getBooleanArgument("informediaTesting"));
				informediaClient.start();
			} catch (final NumberFormatException e) {
				e.printStackTrace();
			}
		}
	}

	public void saveVideoSet() {
		assert informediaClient != null;
		informediaClient.newVideoSet();
	}

	void testInformediaImport() {
		assert informediaClient != null;
		informediaClient.testImport();
	}

	void testInformediaExport() {
		final Item selected = getSelectedItem();
		if (selected != null) {
			final List<Item> items = Arrays.asList(query.getItems(0, Math.min(10, query.getOnCount())));
			if (!items.contains(selected)) {
				items.add(selected);
			}
			assert informediaClient != null;
			informediaClient.testExport(query.getName(null), selected.getID(), Query.getItemIDs(items));
		}
	}

	public boolean setPreferences(final @Nullable Preferences base, final @Nullable String optionsToChangeString,
			final boolean newState) {
		return setPreferences(getPreferences(base, optionsToChangeString, newState));
	}

	public boolean setPreferences(@NonNull Preferences newPreferences) {
		newPreferences = applyPreferencesRules(newPreferences);

		// System.out.println("Bungee.setPreferences changes=\n" +
		// preferences.differenceOrQuoteNone(newPreferences)
		// + "\ncurrent=" + preferences + UtilString.getStackTrace());
		final boolean result = !newPreferences.equals(preferences);
		if (result) {
			final boolean oldCheckboxes = preferences.checkboxes;
			final int oldFontSize = preferences.fontSize;
			assert oldFontSize == getCurrentFont().getSize() : "[old] preferences.fontSize=" + oldFontSize
					+ " getCurrentFont=" + getCurrentFont();
			preferences = newPreferences;
			final int newFontSize = preferences.fontSize;

			if (preferences.checkboxes != oldCheckboxes) {
				checkboxesUpdated();
			}
			final boolean fontSizeChanged = newFontSize != oldFontSize;
			if (fontSizeChanged) {
				final boolean isFontSizeChanged = apTextManager.setFontSize(newFontSize, maxFontSizeThatFitsInWindow());
				assert isFontSizeChanged : "oldFontSize=" + oldFontSize + " newFontSize=" + newFontSize
						+ " maxFontSize()=" + maxFontSizeThatFitsInWindow();
				internalColumnMargin = Math.rint(0.17 * lineH());
				twiceInternalColumnMargin = 2.0 * internalColumnMargin;
				intTwiceInternalColumnMargin = (int) twiceInternalColumnMargin;
				if (help != null) {
					help.setFont(getCurrentFont());
				}
			}
			if (isReady) {
				if (fontSizeChanged) {
					validateIfReady();
				}
				for (final BungeeFrame frame : frames) {
					frame.setFeatures();
				}
			}

			final String s = preferences.features2string().replace(',', '_');
			assert s != null;
			printUserAction(ReplayLocation.MODE, s, 0);
		}
		return result;
	}

	/**
	 * Only called by setPreferences
	 */
	private @NonNull Preferences applyPreferencesRules(@NonNull Preferences _preferences) {
		if (_preferences.tagLists && !_preferences.checkboxes) {
			_preferences = getPreferences(_preferences, "checkboxes", true);
		}
		if (_preferences.editing && query != null && !query.isEditable()) {
			_preferences = getPreferences(_preferences, "editing", false);
		}
		final int fontSize = _preferences.fontSize;
		final int constrainedFontSize = constrain(fontSize, minLegibleFontSize(),
				Math.max(minLegibleFontSize(), maxFontSizeThatFitsInWindow()));
		if (fontSize != constrainedFontSize) {
			_preferences = getPreferences(_preferences, "fontSize=" + constrainedFontSize, false);
		}
		return _preferences;
	}

	/**
	 * @return whether there was a change in size. Doesn't tell children to
	 *         setFont.
	 */
	public boolean setFontSize(final int size) {
		return setPreferences(preferences, "fontSize=" + size, true);
	}

	/**
	 * @return Given the current, valid, layout based on current text size, how
	 *         much bigger can it be?
	 *
	 *         Assume minWidth/minHeight is proportional to font size, with a
	 *         fudge factor.
	 */
	public int maxFontSizeThatFitsInWindow() {
		int size = Integer.MAX_VALUE;
		if (selectedItemColumn != null) {
			final double wRatio = w / (double) minWidth();
			final double hRatio = h / minHeight();
			final double fudgeFactor = 0.99;
			double minRatio = Math.min(wRatio, hRatio) * fudgeFactor;
			if (minRatio < 1.0 && !isTooSmall()) {
				minRatio = 1.0;
			}
			size = (int) (getFontSize() * minRatio);
		}
		// System.out.println("Bungee.maxFontSizeThatFitsInWindow => " + size);
		return size;
	}

	/**
	 * This must not depend on frames being validated, so use getStringWidth
	 * instead of getWidth, for instance. It DOES depend on textH.
	 *
	 * @return minimum width of window required for rendering
	 */
	private int minWidth() {
		int result = interFrameMarginSize() * 3;
		assert getSelectedItemColumn() != null;
		assert columns.size() == 4 : columns;
		for (final BungeeFrame column : columns) {
			result += (int) Math.ceil(column.minWidth());
		}
		return result;
	}

	/**
	 * The margins between Tag Wall/Top Tags/Results/SelectedItem.
	 */
	public int interFrameMarginSize() {
		return UtilMath.roundToInt(getFontSize() * (isWideMargins() ? BungeeConstants.BEGINNER_MARGIN_SIZE_RATIO
				: BungeeConstants.EXPERT_MARGIN_SIZE_RATIO));
	}

	private double minHeight() {
		double result = 1.0;
		for (final BungeeFrame column : columns) {
			result = Math.max(result, column.minHeight());
		}
		result += header.minHeight() + getMouseDoc().getHeight();
		return result;
	}

	private @NonNull String setServer() {
		if (!UtilString.isNonEmptyString(server)) {
			assert argURLQuery != null;
			server = argURLQuery.getArgument("servre");
			if (server.length() == 0) {
				server = codeBase();
			}
		}
		assert UtilString.isNonEmptyString(server) : "You must specify a server on the command line.";
		return server;
	}

	@NonNull
	String codeBase() {
		String result;
		// if (basicJNLPservice != null) {
		// result = basicJNLPservice.getCodeBase().toString();
		// } else {
		result = server.replace("Bungee", "");
		// }
		assert result != null;
		return result;
	}

	/**
	 * Called by invokeLater in mouse process from Replayer.replayNextSession(),
	 * and by Header.ClearButton.doPick()
	 *
	 * Calls setDatabase. If isReplaying(), temporarily set replayer to null.
	 */
	public void setDatabaseWhileReplaying(final boolean reset) {
		// hide this process from stop, which is called by setDatabase
		final Replayer oldReplayer = replayer;
		replayer = null;
		assert dbName != null;
		setDatabase(dbName, reset);
		replayer = oldReplayer;
	}

	public void maybeSetDatabase(final @Nullable String _dbName) {
		if (_dbName != null && !_dbName.equalsIgnoreCase(dbName)) {
			setDatabase(_dbName, false);
			initInformedia();
		}
	}

	/**
	 * @param forceReset
	 *            Restart Bungee even if dbName is unchanged and query isn't
	 *            isRestrictedData(). (Only used by Replayer.)
	 */
	void setDatabase(final @Nullable String _dbName, final boolean forceReset) {
		final boolean isEmptyDBname = !UtilString.isNonEmptyString(_dbName);
		if (isEmptyDBname || !Util.nonNull(_dbName).equals(dbName) || forceReset || query.isRestrictedData()) {
			System.out.println("Bungee.setDatabase " + dbName + " => " + _dbName + "\n");
			if (isReady) {
				stopBungee();
			}
			dbName = _dbName;

			final APText waitingMessage = oneLineLabel();
			waitingMessage.maybeSetText(isEmptyDBname ? "You must specify a database on the command line."
					: "Waiting for " + server + "   ...");
			waitingMessage.setScale(1.5);
			waitingMessage.setOffset(lineH(), lineH());
			addChild(waitingMessage);

			if (!isEmptyDBname && queryUpdater == null) {
				startQueryUpdater();
			}
		}
	}

	private void startQueryUpdater() {
		queryUpdater = new QueryUpdater(this);
		new Thread(queryUpdater).start();
	}

	/**
	 * Stop all threads and close DB connection. Called by setDatabase and
	 * windowClosed.
	 */
	private void stopBungee() {
		isReady = false;

		// prevent repaints
		removeAllChildren();

		// Do this first, to give time for db call to execute
		if (query != null) {
			query.exit();
			query = null;
		}
		if (queryUpdater != null) {
			queryUpdater.exit();
			queryUpdater = null;
		} else {
			stopReplayer();
		}
		if (selectedItemColumn != null) {
			selectedItemColumn.stop();
			selectedItemColumn = null;
		}
		if (grid != null) {
			grid.stop();
			grid = null;
		}
		if (tagWall != null) {
			tagWall.stop();
			tagWall = null;
		}
		if (documentShower != null) {
			documentShower.exit();
			documentShower = null;
		}
		header = null;
		mouseDoc = null;
		extremeTags = null;
		help = null;

		// Need to lose all references to stale facets, or hash tables can have
		// collisions in new database.
		brushedFacets.clear();
		apTextManager.clearTextCaches();
		selectedItem = null;
	}

	private void createFramesNcolumnsLists() {
		frames = UtilArray.getUnmodifiableList(header, tagWall, extremeTags, grid, selectedItemColumn);
		assert UtilArray.assertNoNulls(frames);
		columns = frames.subList(1, frames.size());
	}

	/**
	 * Scheduled by QueryUpdater.init after query is set.
	 *
	 * Show query.errorMessage or create sub-frames, show InitialHelp, set
	 * isReady, and call validateIfReady.
	 */
	void createFrames() {
		assert !isReady;
		removeAllChildren();
		final String errorMessage = query != null ? query.errorMessage() : null;
		if (errorMessage != null) {
			showError("Could not connect to " + server + " because\n" + errorMessage);
		} else if (query != null) {
			help = new InitialHelp(this);
			header = new Header(this, query.getDatabases(), Util.nonNull(dbName));
			mouseDoc = new MouseDocLine(this);
			selectedItemColumn = new SelectedItemColumn(this);
			grid = new ResultsGrid(this);
			tagWall = new TagWall(this);
			extremeTags = new TopTagsViz(this);

			createFramesNcolumnsLists();
			for (final BungeeFrame column : columns) {
				assert column != null;
				addChild(column);
			}
			assert help != null;
			addChild(help);
			addChild(getMouseDoc());
			// Last, to let menu overlap other panes.
			addChild(getHeader());
			isReady = true;
			validateIfReady();
		}
	}

	private void removeAllChildren() {
		final PLayer layer = getLayer();
		layer.removeAllChildren();
	}

	private void addChild(final @NonNull PNode node) {
		final PLayer layer = getLayer();
		layer.addChild(node);
	}

	/**
	 * @param power
	 *            the growth rate of the frame as extra space becomes available.
	 * @return the width to make a frame as a fraction of its minimum width.
	 *         Always >= 1.0
	 */
	private double scaleRatio(final double power) {
		// gridW >= grid.minWidth() as long as w >= minWidth()
		final int minWidth = minWidth();
		assert w >= minWidth : "text size=" + getFontSize() + " w=" + w + " minWidth=" + minWidth + ", " + minWidth()
				+ " SelectedItem:" + getSelectedItemColumn().minWidth() + " extremeTags:" + extremeTags.minWidth()
				+ " tagWall:" + tagWall.minWidth() + " Grid:" + grid.minWidth();
		final double result = Math.pow(w / (double) minWidth, power);
		assert result >= 1.0;
		return result;
	}

	@Override
	public int getHeight() {
		return h;
	}

	@Override
	public int getWidth() {
		return w;
	}

	void replaySetSize(final int width, final int height) {
		assert isReplaying();
		setSize(width, height);
	}

	@Override
	public void setSize(final int width, final int height) {
		final Insets insets = getInsets();
		super.setSize(width + insets.left + insets.right, height + insets.top + insets.bottom);
		if (width != w || height != h) {
			// Need to remember these for when we are ready to validate
			w = width;
			h = height;
			validateIfReady();
		}
	}

	/**
	 * If frames have been created (isReady) and the text size is OK, compute
	 * sizes and validate all sub-frames.
	 *
	 * Called on initialization, by setSize, SmallWindowButton.doPick, and
	 * setPreferences.
	 *
	 * @return whether isReady && !setTooSmall().
	 */
	boolean validateIfReady() {
		// setTooSmall will reduce text size if necessary to fit in the window.
		// If so, it will call validateIfReady again, via setPreferences.
		getLayer().setBounds(0, 0, w, h);
		final boolean result = isReady && !setTooSmall();
		if (result) {
			printUserAction(ReplayLocation.SETSIZE, w, h);

			setBackgroundColor(BungeeConstants.BVBG);

			final double selectedItemW = (int) (selectedItemColumn.minWidth() * scaleRatio(0.5));
			final double summaryW = (int) (tagWall.minWidth() * scaleRatio(1.0));
			final double extremeW = (int) (extremeTags.minWidth() * scaleRatio(0.5));
			final double gridW = (w - summaryW - selectedItemW - extremeW - 3 * interFrameMarginSize());

			assert gridW >= grid.minWidth() : "\nsummaryMinW=" + tagWall.minWidth() + " extremeTagsMinW="
					+ extremeTags.minWidth() + " gridMinW=" + grid.minWidth() + " selectedItemMinW="
					+ selectedItemColumn.minWidth() + "\nsummaryW=" + summaryW + " extremeW=" + extremeW + " gridW="
					+ gridW + " selectedItemW=" + selectedItemW + " getFontSize=" + getFontSize() + " w=" + w;

			final double mouseDocH = lineH();
			final double headerH = header.minHeight();
			final double columnsYoffset = headerH + mouseDocH;
			final double columnsH = h - columnsYoffset;

			// Do mouseDoc before header, so header sets its boundary correctly
			getMouseDoc().setOffset(0.0, headerH);
			getMouseDoc().validate(w, mouseDocH);
			header.validate(w, headerH);

			final double colWidths[] = { summaryW, extremeW, gridW, selectedItemW };
			double xOffset = 0.0;
			for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
				final BungeeFrame column = columns.get(colIndex);
				final double colWidth = colWidths[colIndex];
				column.setOffset(xOffset, columnsYoffset);
				column.validate(colWidth, columnsH);
				xOffset += colWidth + interFrameMarginSize();
			}

			positionHelp();
			maybeRevert();
			if (!tagWall.isInitted()) {
				setInitialState();
			}
		}
		return result;
	}

	private void positionHelp() {
		if (isShowingInitialHelp()) {
			final double xOffset = tagWall.getMaxX() + lineH();
			final double yOffset = header.getHeight() + 4.0 * lineH();
			help.setOffset(xOffset, yOffset);
			help.setWidth(w - xOffset - lineH());
		}
	}

	private boolean setTooSmall() {
		removeTooSmall();
		boolean isTooSmall = isTooSmall();
		if (isTooSmall) {
			final int minLegibleFontSize = minLegibleFontSize();
			final int maxFontSizeThatFitsInWindow = maxFontSizeThatFitsInWindow();
			if (maxFontSizeThatFitsInWindow >= minLegibleFontSize) {
				// Can reduce the text size so it's legible and everything fits.
				setFontSize(maxFontSizeThatFitsInWindow);
				isTooSmall = false;
			} else {
				// Set minimum legible text size before computing minW/H.
				setFontSize(minLegibleFontSize);

				// end with blank line, which makes room for goAhead
				final String msg = "To have room for legible text,\nBungee View requires at least " + minWidth()
						+ " wide x " + ((int) minHeight()) + " high pixels," + "\nbut your window is only " + w
						+ " wide x " + h + " high.\n\n  1. Make sure your window is maximized, or"
						+ "\n  2. Increase your screen resolution, or\n ";
				final APText tooSmall = showError(msg);
				final double scale = tooSmall.getScale();
				assert tooSmall.getFont().getSize()
						* scale > minLegibleFontSize : (tooSmall.getFont().getSize() * scale) + " "
								+ minLegibleFontSize;
				final SmallWindowButton goAhead = new SmallWindowButton(this);
				goAhead.setOffset(tooSmall.getXOffset(), tooSmall.getMaxY() - goAhead.getHeight() * scale);
				goAhead.setScale(scale);
				addChild(goAhead);
				if (isReplaying()) {
					goAhead.doPick();
					isTooSmall = false;
				}
			}
		}
		setFramesVisibility(!isTooSmall);
		if (!isTooSmall) {
			setBackgroundColor(BungeeConstants.BVBG);
		}
		assert isTooSmall == isTooSmall() : isTooSmall;
		return isTooSmall;
	}

	private boolean isTooSmall() {
		return w < minWidth() || h < minHeight();
	}

	private @NonNull APText showError(final @NonNull String msg) {
		removeTooSmall();
		setFramesVisibility(false);
		setBackgroundColor(BungeeConstants.IS_TOO_SMALL_BG_COLOR);

		final APText errorMsg = getAPText();
		errorMsg.setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
		errorMsg.maybeSetText(msg);
		System.err.println("Warning: Bungee.showErrer displaying: " + msg);
		final double wScale = w / errorMsg.getWidth();
		final double hScale = h / errorMsg.getHeight();
		final double minScale = Math.min(wScale, hScale);
		errorMsg.setScale(minScale);
		addChild(errorMsg);
		errorMsg.moveToFront();
		return errorMsg;
	}

	public void setBackgroundColor(final @Nullable Color color) {
		getCanvas().setBackground(color);
	}

	private void removeTooSmall() {
		final PLayer layer = getLayer();
		final int count = layer.getChildrenCount();
		for (int i = count - 1; i >= 0; i--) {
			final PNode node = layer.getChild(i);
			if (node != help && (node instanceof APText || node instanceof SmallWindowButton)) {
				layer.removeChild(i);
			}
		}
	}

	private @NonNull PLayer getLayer() {
		final PLayer result = getCanvas().getLayer();
		assert result != null;
		return result;
	}

	private void setFramesVisibility(final boolean isVisible) {
		for (final BungeeFrame frame : frames) {
			frame.maybeSetVisible(isVisible);
		}
		if (mouseDoc != null) {
			mouseDoc.setVisible(isVisible);
		}
		if (help != null) {
			assert tagWall.connectedRank() == null : "TagWall.connectToRank should have nulled help";
			help.setVisible(isVisible);
		}
	}

	public void removeInitialHelp() {
		if (help != null) {
			help.removeFromParent();
			help = null;
		}
	}

	public boolean isShowingInitialHelp() {
		return help != null && help.getParent() != null;
	}

	public void updateBoundary(final @NonNull Boundary boundary) {
		// Propagate changes, working out from boundary column
		assert getShowBoundaries();
		final BungeeFrame column = (BungeeFrame) boundary.getParent();
		final double deltaX = boundary.constrainedLogicalDragPosition() - column.getWidth();
		if (deltaX != 0.0) {
			assert deltaX == (int) deltaX : deltaX;
			final BungeeFrame nextColumn = UtilArray.next(columns, column);
			assert nextColumn != null;

			if (deltaX < 0.0) {
				nextColumn.validate(nextColumn.getWidth() - deltaX, nextColumn.getHeight());
				scrunchColumns(column, -1, -deltaX);
			} else {
				column.validate(column.getWidth() + deltaX, column.getHeight());
				scrunchColumns(nextColumn, +1, deltaX);
			}
		}
	}

	/**
	 * @param columnToScrunch
	 *            move columnToScrunch in direction to free amount pixels.
	 *
	 * @param direction
	 *            +1 move to the right; -1 move to the left
	 */
	private void scrunchColumns(final @NonNull BungeeFrame columnToScrunch, final int direction, double amount) {
		assert columnToScrunch != null : amount;
		final BungeeFrame columnToMove = direction < 0.0 ? UtilArray.next(columns, columnToScrunch) : columnToScrunch;
		assert columnToMove != null : columnToScrunch;
		columnToMove.setXoffset(direction, amount);

		final double newW = Math.max(columnToScrunch.minWidth(), columnToScrunch.getWidth() - amount);
		final double relief = columnToScrunch.getWidth() - newW;
		columnToScrunch.validate(newW, columnToScrunch.getHeight());
		assert relief >= 0.0;
		amount -= relief;
		assert amount >= 0.0;
		if (amount > UtilMath.ABSOLUTE_SLOP) {
			// recursive call will do the above
			final BungeeFrame followingColumn = followingColumn(columnToScrunch, direction);
			assert followingColumn != null : direction + " " + columnToScrunch + " amount=" + amount;
			scrunchColumns(followingColumn, direction, amount);
		}
	}

	public @Nullable BungeeFrame followingColumn(final @NonNull BungeeFrame column, final int direction) {
		return direction == 1 ? UtilArray.next(columns, column) : UtilArray.previous(columns, column);
	}

	void togglePopups() {
		showPopup(null);
		final boolean state = !isPopups();
		setPreferences(preferences, "popups", state);
		setTip(state ? "Will now show tag information with popups"
				: "Will now show tag information here in the header");
	}

	/**
	 * Surrogate for isExpertMode
	 */
	boolean isWideMargins() {
		return !getShowCheckboxes();
	}

	private boolean isPopups() {
		return preferences.popups;
	}

	public boolean isOpenClose() {
		return preferences.openClose;
	}

	public boolean getShowCheckboxes() {
		return preferences.checkboxes;
	}

	public boolean getIsShortcuts() {
		return preferences.shortcuts || isReplaying();
	}

	private boolean getIsBrushing() {
		return preferences.brushing;
	}

	public boolean getShowPvalues() {
		return preferences.pvalues;
	}

	public boolean getShowMedian() {
		return preferences.medians;
	}

	public boolean getShowSortMenu() {
		return preferences.sortMenus;
	}

	private boolean getUseArrowKeys() {
		return preferences.arrows;
	}

	public boolean getShowBoundaries() {
		return preferences.boundaries;
	}

	public boolean getShowTagLists() {
		return preferences.tagLists;
	}

	public boolean getShowZoomLetters() {
		return preferences.zoom;
	}

	/**
	 * @return isReady() && query.isEditable() && preferences.editing
	 */
	public boolean getIsEditing() {
		boolean result = false;
		if (isReady()) {
			assert query != null;
			assert preferences != null;
			result = query.isEditable() && preferences.editing;
		}
		return result;
	}

	/**
	 * @return preferences.graph && query.isCorrelations()
	 */
	public boolean getIsGraph() {
		return preferences.graph && query.isCorrelations();
	}

	public boolean getIsDebugGraph() {
		return preferences.debugGraph;
	}

	/**
	 * @return non-positive means "Bungee's Choice"
	 */
	public int getDesiredNumResultsColumns() {
		return preferences.nColumns;
	}

	/**
	 * Only called by Thumbnails.boundaryDragged()
	 */
	public boolean setDesiredNumResultsColumns(final int nCols) {
		final boolean result = nCols != getDesiredNumResultsColumns();
		if (result) {
			preferences = getPreferences(preferences, "nColumns=" + nCols, true);
			getGrid().setDesiredNumCols(nCols);
		}
		return result;
	}

	private boolean showDocument(final @NonNull Item item) {
		final boolean result = ensureDocumentShower();
		if (result) {
			assert documentShower != null;
			documentShower.addItem(item);
		}
		return result;
	}

	public boolean showDocument(final @NonNull String urlString) {
		final boolean result = ensureDocumentShower();
		if (result) {
			assert documentShower != null;
			documentShower.addURLstring(urlString);
		} else {
			System.err.println("Warning: Bungee.showDocument: no documentShower");
		}
		return result;
	}

	private boolean ensureDocumentShower() {
		final boolean result = query.isShowDocuments();
		if (result && documentShower == null) {
			documentShower = new DocumentShower();
		}
		return result;
	}

	public void copyBookmark() {
		try {
			final String bookmark = bookmark();
			System.out.println("bookmark:\n" + bookmark + "\n");
			// if (jnlpClipboardService != null) {
			// jnlpClipboardService.setContents(new StringSelection(bookmark));
			// setTip("Bookmark copied to system clipboard");
			// } else
			if (pasteToClipboard(bookmark)) {
				setTip("Bookmark copied to system clipboard");
			} else {
				setTip("Can't copy to clipboard, but you can copy the URL printed on the console. (No jnlpClipboardService.)");
			}
		} catch (final UnsupportedEncodingException e) {
			setTip("Error creating bookmark: " + e);
		}
	}

	private @NonNull String bookmark() throws UnsupportedEncodingException {
		assert getSelectedItem() != null;
		assert query != null;
		String result = null;
		final StringBuilder buf = new StringBuilder(codeBase());
		buf.append("bungee.jsp?");
		buf.append("db=").append(URLEncoder.encode(dbName, "UTF-8"));
		buf.append("&servre=").append(URLEncoder.encode(server, "UTF-8"));
		buf.append("&onFeatures=").append(URLEncoder.encode(preferences.features2string(), "UTF-8"));

		if (selectedItem != null) {
			final String urlString = query.getItemURL(selectedItem);
			if (urlString != null) {
				buf.append("&SelectedItem=").append(URLEncoder.encode(urlString, "UTF-8"));
			} else {
				buf.append("&SelectedItemID=").append(Util.nonNull(selectedItem).getID());
			}
		}
		final Perspective connectedPerspective = tagWall.connectedPerspective();
		if (connectedPerspective != null) {
			buf.append("&SelectedFacet=")
					.append(URLEncoder.encode(connectedPerspective.path(false, false, true), "UTF-8"));
		}
		if (query.isIntensionallyRestricted()) {
			addQueryDescription(buf);
		}

		result = buf.toString();
		assert result != null;
		return result;
	}

	public static boolean pasteToClipboard(final String s) {
		boolean result = false;
		try {
			final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (clipboard != null) {
				clipboard.setContents(new StringSelection(s), null);
				result = true;
			}
		} catch (final IllegalStateException ise) {
			// clipboard was unavailable
			// no need to provide error feedback to user since updating
			// the system selection is not a user invoked action
		}
		return result;
	}

	@NonNull
	StringBuilder addQueryDescription(@Nullable StringBuilder buf) throws UnsupportedEncodingException {
		if (buf == null) {
			buf = new StringBuilder();
		}
		buf.append("&query=");
		final String bookmark = query.bookmark();
		buf.append(URLEncoder.encode(bookmark, "UTF-8"));
		return buf;
	}

	public void setInitialState() {
		getHeader().init();
		getSelectedItemColumn().init();
		getGrid().init();
		getTagWall().init();
		setInitialQuery(argURLQuery);
		assert argURLQuery != null;
		setInitialSelectedItem(argURLQuery);
		assert argURLQuery != null;
		setInitialPVs(argURLQuery);
		assert argURLQuery != null;
		replay(argURLQuery);
		argURLQuery = new URLQuery("");
	}

	public boolean isReplaying() {
		return replayer != null;
	}

	private void replay(final @NonNull URLQuery _argURLQuery) {
		final String replayArg = _argURLQuery.getArgument("sessions");
		if (replayArg.length() > 0) {
			assert replayer == null;
			replayer = new Replayer(this, replayArg, logFile);
			new Thread(replayer).start();
			setIsReplaying(true);
		}
	}

	public void stopReplayer() {
		if (replayer != null) {
			replayer.exit();
			setIsReplaying(false);
			replayer = null;
		}
	}

	public boolean getExitOnError() {
		return isReplaying();
	}

	/**
	 * @param isReplaying
	 *            true when starting Replayer; false when stopping Replayer.
	 */
	public void setIsReplaying(final boolean isReplaying) {
		if (query != null) {
			query.setExitOnError(isReplaying);
		}
		assert replayer != null;
		setIsReplayingInternal(replayer, isReplaying);
		queryUpdater.setExitOnError(isReplaying);
		setIsReplayingInternal(documentShower, isReplaying);
		setIsReplayingInternal(grid, isReplaying);
		setIsReplayingInternal(tagWall, isReplaying);
		setIsReplayingInternal(selectedItemColumn, isReplaying);
	}

	private static void setIsReplayingInternal(final @Nullable BungeeFrame bungeeFrame, final boolean isExit) {
		if (bungeeFrame != null) {
			bungeeFrame.setExitOnError(isExit);
		}
	}

	private static void setIsReplayingInternal(final @Nullable QueueThread<?> queueThread, final boolean isExit) {
		if (queueThread != null) {
			queueThread.setExitOnError(isExit);
		}
	}

	/**
	 * @return Whether the query and frames have been created (though not
	 *         necessarily validated). Will be false during an unrestrict.
	 *
	 *         Only called by Replayer.replaySessions
	 */
	public boolean isReady() {
		return isReady;
	}

	private void setInitialPVs(final @NonNull URLQuery _argURLQuery) {
		final String facetName = _argURLQuery.getArgument("SelectedFacet");
		if (facetName.length() > 0) {
			final Set<Perspective> facets = query.parsePerspectives(facetName);
			for (final Perspective facet : facets) {
				assert facet != null;
				UserAction.displayAncestors(facet, this);
			}
		}
	}

	private void setInitialQuery(final @NonNull URLQuery _argURLQuery) {
		final String stateSpec = _argURLQuery.getArgument("query");
		if (stateSpec.length() > 0) {
			assert UtilString.isNonEmptyString(dbName);
			query.setInitialState(stateSpec);
			updateQuery();
		}
	}

	private void setInitialSelectedItem(final @NonNull URLQuery _argURLQuery) {
		final String urlString = _argURLQuery.getArgument("SelectedItem");
		if (urlString.length() > 0) {
			selectedItemColumn.setter.set(urlString);
		} else {
			final int itemID = _argURLQuery.getIntArgument("SelectedItemID");
			if (itemID > 0) {
				selectedItemColumn.setter.set(Item.ensureItem(itemID));
			}
		}
	}

	public void showMedianArrowDesc(final @Nullable Perspective parent) {
		brushFacet(parent);
		tagWall.showMedianArrowPopup(parent);
	}

	/**
	 * @return a good arrowFocus when connecting to rank with a pv whose
	 *         pvP==parent.
	 *
	 *         <br>
	 *         <br>
	 *         Currently written for setArrowFocus assertion that
	 *         arrowFocus.isRestricted(true)
	 */
	@SuppressWarnings("null")
	public @Nullable Perspective getDefaultChild(final @NonNull Perspective parent) {
		assert parent.isEffectiveChildren() : parent.path(true, true);
		Perspective child = null;
		if (getArrowFocusParent() == parent && getArrowFocus().isRestricted(true)) {
			child = getArrowFocus();
		} else if (parent.isRestricted(true)) {
			child = UtilArray.some(parent.restrictions(true));
		}
		return child;
	}

	public void setArrowFocus(final @Nullable Perspective facet) {
		if (facet != arrowFocus) {
			assert assertIsValidArrowFocus(facet);
			arrowFocus = facet;
		}
	}

	/**
	 * Only allow arrow navigation from positive filters. See comment in
	 * PerspectiveList.handleArrow.
	 */
	private boolean assertIsValidArrowFocus(final @Nullable Perspective facet) {
		if (facet != null) {
			assert facet.isRestriction(true) : facet.path(true, true);
			final Perspective facetParent = facet.getParent();
			assert facetParent != null : facet.path(true, true);
			final Rank rank = tagWall.lookupRank(facetParent);
			assert rank != null : facet.path(true, true);
			assert rank.isConnected() : "facetParent rank=" + rank + " connectedRank=" + tagWall.connectedRank();
		}
		return true;
	}

	public @Nullable Rank arrowFocusRank() {
		Rank result = null;
		if (arrowFocus != null) {
			final Perspective arrowFocusParent = getArrowFocusParent();
			assert arrowFocusParent != null : arrowFocus;
			result = tagWall.lookupRank(arrowFocusParent);
			assert result != null : arrowFocusParent;
		}
		return result;
	}

	public @Nullable Perspective getArrowFocusParent() {
		return arrowFocus != null ? arrowFocus.getParent() : null;
	}

	public @Nullable Perspective getArrowFocus() {
		return arrowFocus;
	}

	/**
	 * This is for brushing TO thumbnails FROM other frames.
	 *
	 * @param facet
	 *            what to highlight (brighten on mouse over). null means
	 *            unhighlight whatever was highlighted before.
	 */
	public void brushFacet(@Nullable final Perspective facet) {
		final Set<Perspective> facets = facet == null ? UtilArray.EMPTY_SET : Collections.singleton(facet);
		assert facets != null;
		setBrushedFacets(facets, getIsBrushing());
		showPopup();
	}

	/**
	 * This is for brushing FROM thumbnails TO other frames.
	 *
	 * Tell all frames to update highlighting for any facets whose highlighting
	 * changed.
	 */
	public void brushFacets(final @NonNull Set<Perspective> facets) {
		if (getIsBrushing()) {
			setBrushedFacets(facets, false);
		}
	}

	private void setBrushedFacets(final @NonNull Set<Perspective> _brushedFacets, final boolean brushGrid) {
		if (!brushedFacets.equals(_brushedFacets)) {
			assert Util.assertMouseProcess();
			final Set<Perspective> changedFacets = UtilArray.symmetricDifference(brushedFacets, _brushedFacets);
			brushedFacets.clear();
			brushedFacets.addAll(_brushedFacets);

			final int queryVersion = query.version();
			if (queryVersion > 0) {

				if (mouseDoc != null && mouseDoc.updateBrushing(changedFacets, queryVersion)) {
					paintImmediately();
				}
				if (selectedItemColumn.updateBrushing(changedFacets, queryVersion)) {
					paintImmediately();
				}
				if (brushGrid && grid.updateBrushing(brushedFacetsUnmodifiable)) {
					paintImmediately();
				}
				if (tagWall.updateHighlighting(changedFacets, queryVersion)) {
					tagWall.repaintNow();
					paintImmediately();
				}

				header.updateBrushing(changedFacets, queryVersion);
				extremeTags.updateBrushing(changedFacets, queryVersion);
			}
		}
	}

	private void paintImmediately() {
		getCanvas().paintImmediately();
	}

	/**
	 * may on the way up; do on the way down
	 *
	 * Called by
	 *
	 * 1. MyInputHandler keyPress, mouseClick, mousePress
	 *
	 * 2. QueryViz when you type return in the search box, or use a delete
	 * button
	 */
	public void mayHideTransients() {
		// Header:
		// databaseMenu.doHideTransients();
		// helpMenu.doHideTransients();
		// modeMenu.doHideTransients();
		// textSearch.doHideTransients();
		//
		// SelectedItem
		// selectedItemSummaryTextBox.revert();
		//
		// ResultsGrid
		// sortMenu.doHideTransients()
		//
		//
		for (final BungeeFrame bungeeFrame : frames) {
			bungeeFrame.doHideTransients();
		}

		if (mouseDoc != null) {
			mouseDoc.doHideTransients();
		}
		if (editing != null) {
			editing.doHideTransients();
		}
	}

	public void setTip(final @Nullable String s) {
		if (mouseDoc != null) {
			mouseDoc.setTip(s);
		}
	}

	public void setTip(final @Nullable Markup markup) {
		if (mouseDoc != null) {
			mouseDoc.setTip(markup);
		}
	}

	/**
	 * @return if !getIsShortcuts(), remove non-button modifiers
	 */
	public int getModifiersEx(final @NonNull PInputEvent e) {
		return e.getModifiersEx() & (getIsShortcuts() ? -1 : edu.cmu.cs.bungee.javaExtensions.Util.BUTTON_MASK);
	}

	public void setClickDesc(final @Nullable Markup markup) {
		getMouseDoc().setClickDesc(markup, false);
	}

	/**
	 * setClickDesc(null)
	 */
	public void resetClickDesc() {
		setClickDesc((Markup) null);
	}

	public void setNoClickDesc(final @Nullable Markup markup) {
		if (mouseDoc != null) {
			mouseDoc.setClickDesc(markup, true);
		}
	}

	public void setClickDesc(final @Nullable String s) {
		if (mouseDoc != null) {
			mouseDoc.setClickDesc(s, false);
		}
	}

	public void setNoClickDesc(final @Nullable String s) {
		if (mouseDoc != null) {
			mouseDoc.setClickDesc(s, true);
		}
	}

	public void setNonClickMouseDoc(final @NonNull String s, final @NonNull Color color) {
		if (mouseDoc != null) {
			mouseDoc.setClickDescInternal(
					DefaultMarkup.newMarkup(new MarkupPaintElement(color), MarkupStringElement.getElement(s)));
		}
	}

	/**
	 * The mouse doc to display when the mouse isn't over something you can
	 * click on.
	 */
	@Nullable
	Markup defaultClickDesc() {
		Markup result = null;
		if (!isShowingInitialHelp() && getUseArrowKeys()) {
			if (arrowFocus != null) {
				result = arrowFocus.defaultClickDesc();
			} else {
				result = grid.defaultClickDesc();
			}
		}
		return result;
	}

	public @NonNull Color facetColor(final @NonNull Perspective facet) {
		return facetColor(facet, query.version());
	}

	public @NonNull Color facetColor(final @NonNull Perspective facet, final int queryVersion) {
		return significanceColor(significance(facet, queryVersion), isHighlighted(facet));
	}

	/**
	 * @return INCLUDED, UNASSOCIATED, or EXCLUDED, or if queryVersion >= 0,
	 *         POSITIVE or NEGATIVE.
	 */
	public @NonNull static Significance significance(final Perspective facet, final int queryVersion) {
		Significance significance = Significance.UNASSOCIATED;
		if (facet.isRestriction(true)) {
			significance = Significance.INCLUDED;
		} else if (facet.isRestriction(false)) {
			significance = Significance.EXCLUDED;
		} else if (queryVersion >= 0) {
			significance = facet.pValueSignificance(queryVersion);
		}
		return significance;
	}

	public static @NonNull Color significanceColor(final @NonNull Significance significance,
			final boolean isHighlighted) {
		final List<Color> significanceColorFamily = BungeeConstants.significanceColorFamily(significance);
		final Color result = significanceColorFamily.get(isHighlighted ? 1 : 0);
		assert result != null;
		return result;
	}

	public int nColors() {
		return getIsShortcuts() || getShowCheckboxes() ? 5 : 4;
	}

	public void clearQuery() {
		query.clear();

		// Calling connectToRank before updateQuery sets query invalid uses
		// stale counts and barfs in facetColor->pValueSignificanceSign.
		// Could probably connect to null before query.clear(), though.
		updateQuery();
		tagWall.connectToRank(null);
		setArrowFocus(null);

		final boolean isMultipleFilters = query.nFilters(false, true, false) > 1;
		if (isMultipleFilters) {
			setTip("To clear a single filter, click on the tag again.");
		}
	}

	boolean handleArrow(final int keyCode, final int modifiers) {
		assert MyInputEventHandler.isArrowKeyOrCtrlA(keyCode, modifiers) : "keyCode=" + keyCode
				+ Util.printModifiersEx(modifiers) + " keyCodeString=" + KeyEvent.getKeyText(keyCode);
		boolean result = getUseArrowKeys() || isReplaying();
		if (!result) {
			setTip("Arrow keys are disabled in beginner mode");
		} else if (arrowFocus == null) {
			result = grid.handleArrow(keyCode, modifiers);
		} else {
			assert nButtons(modifiers) == 0 : Util.printModifiersEx(modifiers);
			printUserAction(ReplayLocation.ARROW, keyCode, modifiers);
			final Rank arrowFocusRank = arrowFocusRank();
			assert arrowFocusRank != null : arrowFocus;
			final Perspective newFocus = arrowFocusRank.handleArrowForAllPVs(keyCode, modifiers);
			if (newFocus != null) {
				assert newFocus.validateArrowFocus(arrowFocus);
				resetClickDesc();
				setArrowFocus(newFocus);
			} else {
				result = false;
				final Perspective arrowFocusParent = getArrowFocusParent();
				assert arrowFocusParent != null;
				setTip("No " + arrowFocusParent.getName()
						+ " tags with non-zero count found in response to navigation keypress.");
			}
		}
		return result;
	}

	public @NonNull String aboutCollection() {
		return query.aboutCollection();
	}

	public @Nullable Item getSelectedItem() {
		return selectedItem;
	}

	public boolean setSelectedItemExplicitly(final @NonNull Item item) {
		return setSelectedItem(item, true, getItemOffset(item), ReplayLocation.THUMBNAIL);
	}

	/**
	 * Calls Database unless cached (via Item.getOffset).
	 *
	 * @return query.itemOffset(this), or -1 if query invalid or item doesn't
	 *         satisfy query.
	 */
	public int getItemOffset(final @NonNull Item item) {
		final Query query2 = query;
		assert query2 != null;
		return item.getOffset(query2, grid.maxPossibleThumbs());
	}

	/**
	 * Sets selectedItem (and its offset) and calls grid.setSelectedItem().
	 *
	 * Called by ResultsGrid, UserAction, QueryUpdater, and elsewhere in Bungee.
	 *
	 * Calls Database (via ensureItemSatisfiesQuery).
	 *
	 * @param isExplicitly
	 *            whether user chose new selected item. (As opposed to
	 *            scrolling, or updated query not including previously selected
	 *            item.) If true, setArrowFocus(null).
	 *
	 * @param replayLocation
	 *            Only printUserAction() if non-null.
	 *
	 * @param newSelectedItemOffset
	 *            if item is non-null and doesn't satisfy the query, use item at
	 *            this offset.
	 *
	 * @return whether selectedItem changed
	 */
	public boolean setSelectedItem(@Nullable final Item item, final boolean isExplicitly,
			final int newSelectedItemOffset, final @Nullable ReplayLocation replayLocation) {
		assert Util.assertMouseProcess();
		// assert !query.isQueryValid() || newSelectedItemOffset <
		// query.getOnCount() : newSelectedItemOffset + " "
		// + query.getOnCount();
		final Item ensuredItem = ensureItemSatisfiesQuery(item, newSelectedItemOffset);
		final boolean result = ensuredItem != selectedItem;
		if (result) {
			if (isExplicitly) {
				assert item == ensuredItem;
				setArrowFocus(null);
			}
			if (replayLocation != null) {
				assert replayLocation == ReplayLocation.THUMBNAIL : replayLocation;
				printUserAction(replayLocation, ensuredItem == null ? 0 : ensuredItem.getID(),
						isExplicitly ? Util.CLICK_THUMB_INTENTIONALLY_MODIFIER : 0);
			}
			selectedItem = ensuredItem;
		}
		grid.setSelectedItem(selectedItem);
		return result;
	}

	/**
	 * Only called by Informedia
	 */
	public @NonNull Item[] getItems(final int startIndex, final int maxOffsetExclusive) {
		return query.getItems(startIndex, maxOffsetExclusive);
	}

	/**
	 * Only called by Informedia
	 */
	public void addInformediaQuery(final @NonNull String name, final @NonNull Item[] items) {
		if (items.length > 0) {
			query.addInformediaQuery(new InformediaQuery(name, items));
			mayHideTransients();
			updateQuery();
		} else {
			setTip("There are no items satisfying Informedia query '" + name + "'");
		}
	}

	boolean showItemInNewWindow(final @NonNull Item item) {
		final boolean result = showDocument(item);
		if (result) {
			printUserAction(ReplayLocation.IMAGE, item.getID(), 0);
		}
		return result;
	}

	private void showPopup() {
		showPopup(getBrushedFacets().size() == 1 ? UtilArray.some(getBrushedFacets()) : null);
	}

	void showPopup(@Nullable Perspective facet) {
		if (!isShowingInitialHelp()) {
			if (isPopups()) {
				tagWall.showPopup(facet);
				facet = null;
			}
			if (mouseDoc != null) {
				mouseDoc.showPopup(facet);
			}
		}
	}

	public void computeInfluenceDiagramNow(final @NonNull Perspective _facet) {
		tagWall.computeInfluenceDiagramNow(_facet);
	}

	// static void showMoreHelp() {
	// System.out.println("Animated Help is commented out of this version.");
	// }

	public @NonNull String getBugInfo() {
		return "db=" + dbName + "&session=" + query.getSession();
	}

	public void printUserAction(final @NonNull ReplayLocation replayLocation, final @NonNull Perspective facet,
			final int modifiers) {
		printUserAction(replayLocation, Util.nonNull(Integer.toString(facet.getID())), modifiers);
	}

	public void printUserAction(final @NonNull ReplayLocation replayLocation, final int arg, final int modifiers) {
		printUserAction(replayLocation, Util.nonNull(Integer.toString(arg)), modifiers);
	}

	public void printUserAction(final @NonNull ReplayLocation replayLocation, final @NonNull String s,
			final int modifiers) {
		if (BungeeConstants.IS_PRINT_USER_ACTIONS && !isReplaying()) {
			if (query != null) {
				query.printUserAction(replayLocation.index(), s, modifiers);
				echoUserAction(replayLocation, s, modifiers);
			}
		}
	}

	/**
	 * For those actions not printed by UserAction.perform
	 */
	private void echoUserAction(final @NonNull ReplayLocation replayLocation, final @NonNull String object,
			final int modifiers) {
		if (isPrintActions) {
			System.out.println("Bungee.echoUserAction location=" + replayLocation + " object=" + object + " modifiers="
					+ modifiers);
			if (replayLocation == ReplayLocation.RESTRICT) {
				System.out.println("USER ACTION:  Restrict");
			}
		}
	}

	public @NonNull Preferences getPreferences() {
		return preferences;
	}

	public boolean isExpertMode() {
		return getExpertPreferences().equals(preferences);
	}

	public void expertMode() {
		setPreferences(getExpertPreferences());
	}

	public boolean isBeginnerMode() {
		return getBeginnerPreferences().equals(preferences);
	}

	public void beginnerMode() {
		setPreferences(getBeginnerPreferences());
	}

	/**
	 * Keeps existing numeric features.
	 */
	private @NonNull Preferences getBeginnerPreferences() {
		return getPreferences(preferences, Preferences.EXPERT_FEATURE_NAMES, false);
	}

	/**
	 * Keeps existing numeric features.
	 */
	private @NonNull Preferences getExpertPreferences() {
		return getPreferences(preferences, Preferences.EXPERT_FEATURE_NAMES, true);
	}

	/****************************************************
	 * Delegate methods for APTextManager
	 ****************************************************/

	public int minLegibleFontSize() {
		return apTextManager.minLegibleFontSize;
	}

	public void setMinLegibleFontSize(final int size) {
		assert size > 0 : size;
		apTextManager.minLegibleFontSize = size;
	}

	public int getFontSize() {
		return apTextManager.getFontSize();
	}

	public @NonNull Font getFont(final int style) {
		return apTextManager.getFont(style);
	}

	public @NonNull Font getCurrentFont() {
		return apTextManager.getFont();
	}

	/**
	 * Always equal to an int.
	 */
	public double lineH() {
		return apTextManager.lineH;
	}

	public @NonNull String truncateText(final @NonNull String string, final double _nameW) {
		return apTextManager.truncateText(string, _nameW);
	}

	/**
	 * constrainWidth/Height and isWrap default to true.
	 */
	public @NonNull APText getAPText() {
		return apTextManager.getAPText();
	}

	/**
	 * constrainWidth/Height default to true; isWrap to false.
	 */
	public @NonNull APText oneLineLabel() {
		return apTextManager.oneLineLabel();
	}

	/**
	 * Always equal to an int
	 */
	public double scrollbarWidth() {
		return apTextManager.scrollbarWidth;
	}

	/**
	 * Always equal to an int
	 */
	public double scrollbarWidthNmargin() {
		return apTextManager.scrollbarWidth + internalColumnMargin;
	}

	/**
	 * @return always equal to an int.
	 */
	public double numWidth(final int i) {
		return apTextManager.numWidth(i);
	}

	/**
	 * Always equal to an int
	 */
	public double checkBoxWidth() {
		return apTextManager.checkBoxWidth;
	}

	/**
	 * Space to the right and left of buttons. Equal to the width of two space
	 * characters. Always equal to an int.
	 */
	public double buttonMargin() {
		return apTextManager.buttonMargin;
	}

	void checkboxesUpdated() {
		apTextManager.checkboxesUpdated();
	}

	/**
	 * Always equal to an int
	 */
	public double parentIndicatorWidth() {
		return apTextManager.parentIndicatorWidth;
	}

	/**
	 * Always equal to an int
	 */
	public double childIndicatorWidth() {
		return apTextManager.childIndicatorWidth;
	}

	void clearTextCaches() {
		apTextManager.clearTextCaches();
	}

	/**
	 * Caller must account for padToNameW
	 *
	 * @return Always equal to an int
	 */
	public double getFacetStringWidth(final @NonNull PerspectiveMarkupElement treeObject,
			final boolean showChildIndicator, final boolean showCheckBox) {
		return apTextManager.getFacetStringWidth(treeObject, showChildIndicator, showCheckBox);
	}

	/**
	 * Always equal to an int
	 */
	public double getStringWidth(final @NonNull String s, final boolean showChildIndicator,
			final boolean showCheckBox) {
		return apTextManager.getStringWidth(s, showChildIndicator, showCheckBox);
	}

	/**
	 * @return a double that equals an integer.
	 */
	public double getStringWidth(final @NonNull String text) {
		return apTextManager.getStringWidth(text);
	}

	public @Nullable String computeText(final @NonNull PerspectiveMarkupElement pme, final double _numW,
			final double _nameW, final boolean _numFirst, final boolean maybeShowChildIndicator,
			final boolean maybeShowCheckBox, final boolean padToNameW, final @NonNull RedrawCallback _redraw) {
		return apTextManager.computeText(pme, _numW, _nameW, _numFirst, maybeShowChildIndicator, maybeShowCheckBox,
				padToNameW, _redraw);
	}

	/**
	 * @return text, or NULL and call callback if treeObject is a
	 *         PerspectiveMarkupElement whose perspective's name isn't cached.
	 */
	public @Nullable String computeText(final @NonNull MarkupElement treeObject, final double _numW,
			final double _nameW, final boolean _numFirst, final boolean maybeShowChildIndicator,
			final boolean maybeShowCheckBox, final boolean padToNameW, final @Nullable RedrawCallback redrawCallback,
			final int onCount) {
		return apTextManager.computeText(treeObject, _numW, _nameW, _numFirst, maybeShowChildIndicator,
				maybeShowCheckBox, padToNameW, redrawCallback, onCount);
	}

	/**
	 * @return minimum stringWidth for any perspective name
	 */
	public double minNameWidth() {
		return 5.0 * lineH();
	}

	/**
	 * @return text, or NULL and call callback if element is NULL or is a
	 *         PerspectiveMarkupElement whose perspective's name isn't cached.
	 */
	public @Nullable String computeText(final @NonNull MarkupElement element, final @NonNull RedrawCallback redrawer) {
		return apTextManager.computeText(element, redrawer);
	}

	/*****************************************************
	 * End of delegate methods for APTextManager
	 *****************************************************/

	private final class DocumentShower extends QueueThread<URL> {

		DocumentShower() {
			super("DocumentShower", 0);
			new Thread(this).start();
			setExitOnError(Bungee.this.getExitOnError());
		}

		boolean addItem(final @NonNull Item item) {
			boolean result = false;
			String urlString = getQuery().getItemURL(item);
			if (isInformediaDB()) {
				try {
					urlString += addQueryDescription(null);
				} catch (final UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			}
			assert UtilString.isNonEmptyString(urlString) : "DocumentShower.addItem can't getItemURL for " + item;
			assert urlString != null;
			result = addURLstring(urlString);
			return result;
		}

		private boolean isInformediaDB() {
			return dbName.length() >= 5 && dbName.substring(0, 5).equalsIgnoreCase("Elamp");
		}

		boolean addURLstring(final @NonNull String urlString) {
			boolean result = false;
			try {
				result = add(new URL(new URL(codeBase()), urlString));
			} catch (final MalformedURLException e1) {
				System.err.println("Warning: " + codeBase() + " " + urlString);
				e1.printStackTrace();
			}
			return result;
		}

		// Handle both Items and Strings in one class to minimize the number of
		// Threads. For items, look up the URL and recurse.
		@Override
		public void process(final @NonNull URL url) {
			// if (basicJNLPservice != null) {
			// basicJNLPservice.showDocument(url);
			// } else {
			try {
				Desktop.getDesktop().browse(new URI(url.toString()));
			} catch (final IOException | URISyntaxException e) {
				e.printStackTrace();
			}
			// }
		}

		@Override
		public synchronized void exit() {
			stopReplayer();
			super.exit();
		}

		// end DocumentShower
	}

	private transient static @Nullable BufferedImage missingImage;

	public @NonNull BufferedImage getMissingImage() {
		if (missingImage == null) {
			final String where = codeBase() + "missing.gif";
			try {
				missingImage = ImageIO.read(new URL(where));
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
		assert missingImage != null;
		return missingImage;
	}

	public @NonNull Query getQuery() {
		assert query != null;
		return query;
	}

	/**
	 * set query, then invokeLater will setPreferences, setDatabase, and
	 * createFrames (which sets isReady).
	 *
	 * Called only in thread queryUpdater, by QueryUpdater.init()
	 */
	public void setQuery(final @NonNull Query _query) {
		query = _query;

		final Runnable doCreateFrames = new Runnable() {
			@Override
			public void run() {

				// Force printUserAction of preferences (so Replayer always
				// starts in the same state as the original session) by
				// resetting it to a minimal value and then back to default.
				// (But keep text size, or setPreferences() will barf.)
				//
				// Also, now that query is set, preference rules will remove
				// "editing" if the query isn't editable.

				final Preferences oldPreferences = preferences;
				setPreferences(null, "fontSize=" + getFontSize(), false);
				// setPreferences(defaultPreferences());
				setPreferences(oldPreferences);

				// If bungee.dbName wasn't supplied, query will now have the
				// default.
				maybeSetDatabase(getQuery().dbName);
				createFrames();
			}
		};

		query.queryInvokeLater(doCreateFrames);
	}

	@NonNull
	Preferences defaultPreferences() {
		return getPreferences(null, "fontSize=" + getFontSize() + Preferences.DEFAULT_FEATURE_NAMES, true);
	}

	@NonNull
	Preferences getPreferences(final @Nullable Preferences base, final @Nullable String optionsToChangeString,
			final boolean newState) {
		return new Preferences(base, optionsToChangeString, newState, isReplaying());
	}

	public @NonNull TagWall getTagWall() {
		assert tagWall != null;
		return tagWall;
	}

	public @NonNull TopTagsViz getTopTagsViz() {
		assert extremeTags != null;
		return extremeTags;
	}

	public @NonNull ResultsGrid getGrid() {
		assert grid != null;
		return grid;
	}

	public @NonNull SelectedItemColumn getSelectedItemColumn() {
		assert selectedItemColumn != null;
		return selectedItemColumn;
	}

	public @NonNull Header getHeader() {
		assert header != null;
		return header;
	}

	public @NonNull MouseDocLine getMouseDoc() {
		assert mouseDoc != null;
		return mouseDoc;
	}

	public boolean isHighlighted(final Perspective facet) {
		return isBrushed(facet) || query.getRestrictions().allRestrictions().contains(facet);
	}

	public int highlightPoints(final Perspective facet) {
		int result = 0;
		if (isBrushed(facet)) {
			result++;
		}
		if (query.getRestrictions().allRestrictions().contains(facet)) {
			result++;
		}
		return result;
	}

	// public @NonNull Set<Perspective> getHighlightedFacets() {
	// final Set<Perspective> result = new
	// TreeSet<>(query.getRestrictions().allRestrictions());
	// result.addAll(brushedFacetsUnmodifiable);
	// return result;
	// }

	public boolean isBrushed(final Perspective facet) {
		return brushedFacetsUnmodifiable.contains(facet);
	}

	public @Immutable @NonNull Set<Perspective> getBrushedFacets() {
		return brushedFacetsUnmodifiable;
	}

	/**
	 * The Query is newly valid.
	 */
	void queryValidRedraw() {
		final int queryVersion = query.version();
		assert queryVersion > 0;
		final Pattern textSearchPattern = query.textSearchPattern();
		// tagWall.queryValidRedraw(queryVersion, textSearchPattern);
		// Must do this after PVs are synchronized. But the extra
		// tagWall.queryValidRedraw is slow, and what's the worst that can
		// happen?
		// computeBonferroniThreshold();

		if (query.getOnCount() == 0) {
			setTip("No results. You need to remove some filters" + query.describeFilters());
			// setSelectedItem(null, false, -1, null);
		}

		showPopup();
		tagWall.queryValidRedraw(queryVersion, textSearchPattern);
		header.queryValidRedraw(queryVersion, textSearchPattern);
		grid.queryValidRedraw();
		selectedItemColumn.queryValidRedraw();
		extremeTags.queryValidRedraw(textSearchPattern);

		// in case selectedItem no longer satisfies query.
		setSelectedItem(selectedItem, false, 0, null);
		// ensureItemSatisfiesQuery(getSelectedItem(), -1);
	}

	/**
	 * Calls Database and waitForValidQuery().
	 *
	 * @return Item at newSelectedItemOffset if item is not null and doesn't
	 *         satisfy the query. Else return item.
	 */
	public @Nullable Item ensureItemSatisfiesQuery(@Nullable Item item, final int newSelectedItemOffset) {
		if (item != null && query.waitForValidQuery() && getItemOffset(item) < 0) {
			assert isReplaying() : "The selected item, " + item + ", doesn't satisfy the query: " + query.getName(null);
			final int onCount = query.getOnCount();
			if (onCount == 0) {
				item = null;
				// setSelectedItem(null, false, -1, null);
			} else {
				assert assertInRange(newSelectedItemOffset, 0, onCount);
				item = query.getItems(newSelectedItemOffset, newSelectedItemOffset + 1)[0];
				// setSelectedItem(item, false, 0, null);
			}
		}
		return item;
	}

	boolean removeTextSearch(final @NonNull String searchText) {
		final boolean result = query.removeTextSearch(searchText);
		if (result) {
			updateQuery();
		}
		return result;
	}

	/**
	 * All query changes go through this method EXCEPT restrictData. Does not
	 * require isQueryValid.
	 */
	public void updateQuery() {
		query.computeIsIntensionallyRestricted();
		queryUpdater.updateQuery();
	}

	public void restrictData() {
		if (query.getOnCount() == 0) {
			setTip("Can't restrict when there are no results.");
		} else if (!query.isExtensionallyRestricted()) {
			if (query.isIntensionallyRestricted()) {
				setTip("Can't restrict when no filters actually filter anything out.");
			} else {
				setTip("Can't restrict when there are no filters.");
			}
		} else {
			printUserAction(ReplayLocation.RESTRICT, 0, 0);
			setArrowFocus(null);
			query.restrictData();
			header.restrictData();
			tagWall.restrictData();
			queryValidRedraw();
		}
	}

	/**
	 * @return amount of truly idle time.
	 */
	long waitForIdle() {
		assert assertNotMouseProcess();
		long idleTime;
		while ((idleTime = elapsedTime(lastActiveTime())) < MIN_IDLE_TIME) {
			Util.sleep(IDLE_POLL_INTERVAL);
		}
		return idleTime;
	}

	private PActivityScheduler activityScheduler;

	private @NonNull PActivityScheduler getActivityScheduler() {
		if (activityScheduler == null) {
			activityScheduler = getCanvas().getRoot().getActivityScheduler();
		}
		assert activityScheduler != null;
		return activityScheduler;
	}

	private long lastActiveTime() {
		final boolean maybeIdle = isReady() && query != null && queryUpdater != null && selectedItemColumn != null
				&& selectedItemColumn.setter != null && grid != null && grid.rangeEnsurer != null && tagWall != null
				// &&
				// !getCanvas().getRoot().getActivityScheduler().getAnimating()
				&& !edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil.getAnimating(getActivityScheduler())
		// && tagWall.popup.influenceDiagramCreator != null
		;

		return !maybeIdle ? Long.MAX_VALUE
				: UtilMath.max((replayer != null ? replayer.lastActiveTime() : 0L), query.lastActiveTime(),
						queryUpdater.lastActiveTime(), selectedItemColumn.setter.lastActiveTime(),
						grid.rangeEnsurer.lastActiveTime(), (tagWall.popup.influenceDiagramCreator == null ? 0L
								: tagWall.popup.influenceDiagramCreator.lastActiveTime()));
	}

	/**
	 * Stub for PApplet. Delete for PFrame.
	 */
	// public void dispose() {
	// // stub
	// }

	/**
	 * Stub for PFrame. Delete for PApplet.
	 */
	@Override
	public @NonNull Rectangle getDefaultFrameBounds() {
		return new Rectangle(200, 0, 1000, 800);
	}

	/**
	 * Was in Header. Moved here so all PApplet/PFrame issues are in this file.
	 *
	 * Create the GUI and show it. For thread safety, this method should be
	 * invoked from the event dispatch thread.
	 */
	public void createAndShowGUI() {
		// Comment out body for PApplet.
		assert Util.assertMouseProcess();
		final JDialog frame = new JDialog(this, "Choose Bungee View options");
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		frame.getContentPane().add(new PreferencesDialog(this, frame));

		// Display the window.
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	public void windowClosing(final WindowEvent e) {
		windowClosed(e);
	}

	@Override
	public void windowClosed(@SuppressWarnings("unused") final WindowEvent e) {
		stopBungee();
		System.exit(NORMAL);
	}

	@Override
	public void windowDeactivated(@SuppressWarnings("unused") final WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowOpened(@SuppressWarnings("unused") final WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowIconified(@SuppressWarnings("unused") final WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowDeiconified(@SuppressWarnings("unused") final WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowActivated(@SuppressWarnings("unused") final WindowEvent e) {
		// TODO Auto-generated method stub
	}

	// ///////////////////////////// Editing ///////////////////////////

	@Nullable
	Editing editing;

	private @NonNull Editing getEditing() {
		final Editing result = editing;
		assert result != null;
		return result;
	}

	@Nullable
	Menu editMenu() {
		return ensureEditing(false) ? getEditing().editMenu : null;
	}

	public void setItemDescription(final @NonNull String description) {
		if (ensureEditing()) {
			getEditing().setItemDescription(Util.nonNull(selectedItem), description);
		}
	}

	/**
	 * Make sure editing is instantiated, if appropriate
	 */
	boolean ensureEditing() {
		return ensureEditing(true);
	}

	/**
	 * This is supposed to be called only on mouse-middle-button events, but
	 * Alt-Left is treated as middle by PInputEvent.isMiddleMouseButton, so
	 * sometimes this prints Warnings erroneously.
	 */
	private boolean ensureEditing(final boolean isWarn) {
		if (!getIsEditing()) {
			// In case user has turned off editing using the Custom menu
			editing = null;
		}
		if (editing == null) {
			if (getIsEditing()) {
				editing = new Editing(this);
			} else if (!isWarn) {
				// don't warn
			} else if (query.isEditable()) {
				setTip("Updating this Database is disabled. Enable it using the Custom Mode menu");
			}
			// else if (e != null && e.isMouseEvent()) {
			// System.err.println(" Warning: Bungee.ensureEditing " +
			// e.getButton() + " "
			// + InputEvent.getModifiersExText(e.getModifiersEx()));
			// // This is more likely to confuse a user who has no idea about
			// // editing than to inform a user who wants to edit.
			// // setTip("Updating this Database is disabled. Enable it by
			// // setting globals.isEditable = 'Y'");

			else {
				setTip("Updating this Database is disabled.");
			}
		}
		return editing != null;
	}

	/**
	 * Only called when editing
	 */
	void decacheItems() {
		final Item previousSelectedItem = getSelectedItem();
		GridElementWrapper.decacheGridElement(previousSelectedItem);
		setSelectedItem((Item) null, false, -1, ReplayLocation.THUMBNAIL);
		setSelectedItem(previousSelectedItem, false, -1, ReplayLocation.THUMBNAIL);
	}

	public void appendPvalue(final @NonNull Perspective facet, final @NonNull StringBuffer buf) {
		if (getShowPvalues()) {
			appendPvalueInternal(buf, facet.correctedPvalue());
		}
	}

	public void appendMedianPvalue(final @NonNull Perspective facet, final @NonNull StringBuffer buf) {
		if (getShowPvalues()) {
			appendPvalueInternal(buf, facet.medianPvalue());
		}
	}

	/**
	 * Add, e.g. "(p=0.1)"
	 */
	private static void appendPvalueInternal(final @NonNull StringBuffer buf, final double pValue) {
		if (pValue >= 0.0) {
			buf.append(" (");
			UtilString.appendPvalue(pValue, buf);
			buf.append(")");
		}
	}

	public void reorder(final int facetTypeIdOrSpecial) {
		// Must pass an int, because -1 and 0 have special meaning
		if (facetTypeIdOrSpecial != query.sortedByFacetTypeIdOrSpecial) {
			printUserAction(ReplayLocation.REORDER, facetTypeIdOrSpecial, 0);
			query.sortedByFacetTypeIdOrSpecial = facetTypeIdOrSpecial;
			grid.reorder();
			getQuery().reorderItems(facetTypeIdOrSpecial);
			updateQuery();
		}
	}

	/**
	 * Only called when Editing
	 */
	public void handleCursor(final boolean wait) {
		if (queryUpdater != null) {
			// in case we're shutting down.
			queryUpdater.handleCursor(wait);
		}
	}

	private void maybeRevert() {
		final String revertDate = argURLQuery.getArgument("revert");
		if (UtilString.isNonEmptyString(revertDate)) {
			assert ensureEditing() : "You can't revert because editing is disabled.";
			assert revertDate != null;
			getEditing().revert(revertDate);
		}
	}

	// ///////////////////////////// End Editing //////////////////////
}
