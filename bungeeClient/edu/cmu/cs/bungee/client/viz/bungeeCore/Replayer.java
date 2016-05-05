package edu.cmu.cs.bungee.client.viz.bungeeCore;

import static edu.cmu.cs.bungee.javaExtensions.Util.CLICK_THUMB_INTENTIONALLY_MODIFIER;
import static edu.cmu.cs.bungee.javaExtensions.Util.LEFT_BUTTON_MASK;
import static edu.cmu.cs.bungee.javaExtensions.Util.MIDDLE_BUTTON_MASK;
import static edu.cmu.cs.bungee.javaExtensions.Util.initThreadCPUtimes;
import static edu.cmu.cs.bungee.javaExtensions.Util.isControlOrShiftDown;
import static edu.cmu.cs.bungee.javaExtensions.Util.isExcludeAction;
import static edu.cmu.cs.bungee.javaExtensions.Util.isShiftDown;
import static edu.cmu.cs.bungee.javaExtensions.Util.nButtons;
import static edu.cmu.cs.bungee.javaExtensions.Util.printModifiersEx;
import static edu.cmu.cs.bungee.javaExtensions.Util.printModifiersHex;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.weight;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction.PMELocation;
import edu.cmu.cs.bungee.client.viz.header.SortLabelNMenu;
import edu.cmu.cs.bungee.client.viz.tagWall.Rank;
import edu.cmu.cs.bungee.client.viz.tagWall.TagWall;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.Util.MyThreadInfo;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilImage;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.threads.UpdateNoArgsThread;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;

/**
 * movie db session -1730120890 on playfair has the whole contest video sequence
 * as well as text search, restrict, and reorder.
 *
 * !!! Updated for Movie2: sessions=1643294883 !!!
 *
 *
 *
 * Unlike most UpdateNoArgsThread, no other thread calls update(). Instead, it
 * is called by init(), after every op, and after every session.
 */
public final class Replayer extends UpdateNoArgsThread implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final @NonNull String CLEAR = "Clear";
	public static final @NonNull String ELLIPSIS = "Ellipsis";

	public enum ReplayLocation {
		BAR(PMELocation.BAR, false, false),

		BAR_LABEL(PMELocation.BAR, false, false),

		/**
		 * For anything in the MarkupViz; not just the pvP.
		 */
		RANK_LABEL_REPLAY(PMELocation.RANK_LABEL, false, false),

		/**
		 * MISUSE_MODIFIERS as (modifiers == 0 ? "unintentionally" :
		 * "intentionally")
		 */
		THUMBNAIL(PMELocation.GRID_ITEM, false, true),

		IMAGE(PMELocation.SELECTED_ITEM, false, false),

		/**
		 * For MarkupViz [obsolete], FacetTreeViz, TopTagsViz, and
		 * PerspectiveList
		 */
		DEFAULT_REPLAY(PMELocation.DEFAULT, false, false),

		/**
		 * Obsolete
		 */
		SCROLL(null, true, false),

		/**
		 * Obsolete
		 */
		KEYPRESS(null, true, false),

		/**
		 * CLEAR, ELLIPSIS, MarkupSearchText, or [obsolete] a Perspective in a
		 * MarkupViz.
		 */
		BUTTON(PMELocation.DEFAULT, false, false),

		SEARCH(null, false, false),

		/**
		 * MISUSE_MODIFIERS as height
		 */
		SETSIZE(null, false, true),

		/**
		 * Obsolete
		 */
		CHECKBOX(null, true, false),

		/**
		 * restrictData via Restrict Button
		 */
		RESTRICT(null, false, false),

		/**
		 * Obsolete
		 */
		SHOW_CLUSTERS(null, true, false),

		/**
		 * Obsolete
		 */
		GRID_ARROW(null, true, false),

		/**
		 * Obsolete
		 */
		FACET_ARROW(null, true, false),

		REORDER(null, false, false),

		/**
		 * Obsolete
		 */
		TOGGLE_CLUSTER_EXCLUSION(null, true, false),

		/**
		 * Obsolete
		 */
		TOGGLE_CLUSTER(null, true, false),

		TOGGLE_POPUPS(null, false, false),

		/**
		 * Obsolete
		 */
		SHOW_MORE_HELP(null, true, false),

		/**
		 * MISUSE_MODIFIERS as keyChar
		 */
		ZOOM(null, false, true),

		MODE(null, false, false),

		ERROR(null, false, false),

		WRITEBACK(null, false, false),

		/**
		 * Now we're using keyCode as second argument, and assuming arrow Focus
		 * is the same as it was in the original session. No more need for
		 * distinguishing GRID_ARROW from FACET_ARROW. 7/2015
		 */
		ARROW(null, false, false),

		/**
		 * MISUSE_MODIFIERS as newVisRowOffset
		 */
		GRID_SCROLL(null, false, true),

		INFLUENCE_DIAGRAM(null, false, false);

		static {
			assert ERROR.index() == Query.ERROR : ERROR.index() + " " + Query.ERROR;
		}

		private final @Nullable PMELocation perspectiveMarkupElementLocation;
		private final boolean isObsolete;
		private final boolean isMisuseModifiers;

		ReplayLocation(final @Nullable PMELocation _perspectiveMarkupElementLocation, final boolean _isObsolete,
				final boolean _isMisuseModifiers) {
			perspectiveMarkupElementLocation = _perspectiveMarkupElementLocation;
			isObsolete = _isObsolete;
			isMisuseModifiers = _isMisuseModifiers;
		}

		public @Nullable PMELocation perspectiveMarkupElementLocation() {
			return perspectiveMarkupElementLocation;
		}

		boolean isObsolete() {
			return isObsolete;
		}

		/**
		 * @return whether to add LEFT_BUTTON when modifiers includes no
		 *         Buttons.
		 */
		boolean isMisuseModifiers() {
			return isMisuseModifiers;
		}

		static @NonNull ReplayLocation getReplayLocation(final int index) {
			final ReplayLocation result = values()[index - 1];
			assert result != null;
			return result;
		}

		@NonNull
		String replayLocationDesc() {
			return this + " (" + perspectiveMarkupElementLocation() + ")";
		}

		int index() {
			return ordinal() + 1;
		}

	}

	final @NonNull Bungee art;

	// Don't cached query, as it changes.
	// final @NonNull Query query;

	final @NonNull List<String> sessions;

	long sessionStartTime = -1L;
	long sessionIdleTime = -1L;
	@Nullable
	MyThreadInfo startThreadCPUtimes;
	int opNum = -1;

	/**
	 * Set in initSession()
	 */
	private String session;
	private String[] ops;
	/**
	 * <directory>/op
	 *
	 * UtilImage.captureComponent will add ".png"
	 */
	private final @Nullable String baseImageFilename;

	Replayer(final @NonNull Bungee _art, final @NonNull String sessionsArg, final @Nullable File _logFile) {
		super("Replayer", 0);
		art = _art;
		sessions = new ArrayList<>(Arrays.asList(sessionsArg.split(",")));
		baseImageFilename = _logFile == null ? null : _logFile.getParentFile() + File.separator + "op";
	}

	@Override
	protected void init() {
		super.init();
		// * There's a race condition I can't figure out setting the size of the
		// * frame. Delay the first session by 2000 and hope the size gets set
		// * in the mean time.
		Util.sleep(2_000L);
		update();
	}

	@Override
	// process the next op, if any; else next session, if any; else exit.
	public void process() {
		if (opNum >= 0) {
			if (opNum == 0) {
				initSession();
			} else {
				finishPreviousOp();
			}
			replayNextOp();
		} else {
			finishPreviousSession();
			replayNextSession();
		}
	}

	private void initSession() {
		waitForIdle();
		art.setArrowFocus(null);
		session = sessions.get(0).equalsIgnoreCase("Random") ? query().randomSession() : sessions.remove(0);
		ops = query().opsSpec(Integer.parseInt(session));
		System.out.println("\nReplaying " + ops.length + " ops from session " + session);
	}

	private void finishPreviousSession() {
		if (sessionStartTime > 0L) {
			System.out.println("\nReplaying session " + session + " took "
					+ UtilString.elapsedTimeString(sessionStartTime, 9) + " {"
					+ UtilString.elapsedTimeString(sessionStartTime + sessionIdleTime, 9) + " not idle)");
			if (startThreadCPUtimes != null) {
				System.out.println(Util.getThreadCPUtimes(startThreadCPUtimes));
			}
		}
		if (sessions.size() <= 0) {
			System.out.println("replayOps done.");
			System.err.println("replayOps done.");
			art.stopReplayer();
			exit();
		}
	}

	private void replayNextSession() {
		if (sessions.size() > 0) {
			waitForIdle();
			javax.swing.SwingUtilities.invokeLater(doStartNextSession);
		}
	}

	private final @NonNull Runnable doStartNextSession = new Runnable() {
		@Override
		public void run() {
			startThreadCPUtimes = initThreadCPUtimes();
			sessionStartTime = UtilString.now();
			sessionIdleTime = 0L;
			art.setDatabaseWhileReplaying(true);
			opNum = 0;
			update();
		}
	};

	long waitForIdle() {
		assert Thread.currentThread() == myThread : Thread.currentThread();
		final long idleTime = art.waitForIdle();
		sessionIdleTime += idleTime;
		return idleTime;
	}

	@Override
	public long lastActiveTime() {
		return opStart;
	}

	private static final @NonNull DecimalFormat OPNUM_FORMAT = new DecimalFormat("000");

	private void finishPreviousOp() {
		if (opStart > 0L) {
			assert opNum >= 1 : opNum;
			final long idleTime = waitForIdle();
			System.out.println(" op took " + UtilString.elapsedTimeString(opStart + idleTime, 7) + " non-idle time");
			opStart = -1L;
		}
	}

	/**
	 * Only called by process
	 *
	 * Ensure session initialized; invokeLater(doReplayNextOp) if more ops.
	 */
	private void replayNextOp() {
		opNum++;
		if (opNum > ops.length) {
			opNum = -1;
			update();
		} else if (!getExited()) {
			javax.swing.SwingUtilities.invokeLater(doReplayNextOp);
		}
	}

	private final @NonNull Runnable doReplayNextOp = new Runnable() {
		@Override
		public void run() {
			if (!getExited()) {
				try {
					replayOp();
				} catch (final Throwable e) {
					e.printStackTrace();
				}
				update();
			}
		}
	};

	long opStart = -1L;

	void replayOp() {
		// if (opNum > 10) {
		// return;
		// }
		assert Util.assertMouseProcess();
		art.mayHideTransients();

		@SuppressWarnings("null")
		final String[] args = UtilString.splitComma(ops[opNum - 1]);
		assert args.length > 3 : UtilString.valueOfDeep(args);
		final String arg2 = args[2];
		assert arg2 != null;
		final ReplayLocation replayLocation = ReplayLocation.getReplayLocation(Integer.parseInt(args[1]));
		if (replayLocation.isObsolete() || (replayLocation == ReplayLocation.BUTTON && arg2.equals(ELLIPSIS))
				|| (replayLocation == ReplayLocation.ZOOM && "65535".equals(args[3]))) {
			return;
		}
		if (baseImageFilename != null) {
			final String imageFilename = UtilImage.captureComponent(art, baseImageFilename + OPNUM_FORMAT.format(opNum))
					.getName();
			System.out.println("\n<img src=\"" + imageFilename + "\" alt=\"" + imageFilename + "\">\n");
		}
		System.out.println(
				"\nReplay op #" + opNum + " " + UtilString.valueOfDeep(UtilArray.subArray(args, 1)) + " " + new Date());
		final int arg2int = getIntArg(arg2);
		final String arg3 = args[3];
		assert arg3 != null;
		int modifiers = Integer.parseInt(arg3);
		int onCount = -1;
		if (args.length > 4) {
			try {
				onCount = Integer.parseInt(args[4]);
			} catch (final NumberFormatException e) {
				System.out.println(" Warning: While parseInt " + args[4] + ": " + e);
			}
		}

		final Rank connectedRank = art.getTagWall().connectedRank();
		final Perspective arrowFocus = art.getArrowFocus();
		final Item selectedItem = art.getSelectedItem();
		System.out.println("             [" + replayLocation.replayLocationDesc() + ", "
				+ possiblePerspective(arg2int, replayLocation) + possibleModifiers(modifiers, replayLocation)
				+ (onCount >= 0 ? ", onCount=" + UtilString.addCommas(onCount) : "") + "]\n query v" + query().version()
				+ ": " + query().getName() + "\n selected item: " + selectedItem + "\n selected rank: " + connectedRank
				+ "\n arrow focus: " + arrowFocus);
		if (connectedRank == null && arrowFocus != null) {
			System.err.println(" Warning: connectedRank==null, but arrowFocus=" + arrowFocus);
		}

		final int queryOnCount = query().getOnCount();
		if (onCount >= 0 && queryOnCount != onCount) {
			if (opNum == 1) {
				assert queryOnCount == query().getTotalCount() : " query().getOnCount()="
						+ UtilString.addCommas(queryOnCount) + " query().getTotalCount()="
						+ UtilString.addCommas(query().getTotalCount());
				// I don't understand why this happens -- later: because
				// setInitialState and replaying don't record updates.
				System.out.println(" Query.loseSession " + session + " because first op onCount != totalCount: onCount="
						+ UtilString.addCommas(onCount) + " query.getTotalCount()="
						+ UtilString.addCommas(query().getTotalCount()));
				query().loseSession(Util.nonNull(session));
				opNum = ops.length;
				return;
			} else {
				System.out.println("Warning: Replayer.replayOp expected onCount=" + UtilString.addCommas(onCount));
			}
		}
		if (args.length > 5) {
			System.out.println(" Warning: Replayer.replayOp ignoring replay op with too many fields.");
			return;
		}

		opStart = UtilString.now();
		switch (replayLocation) {
		case BAR:
		case BAR_LABEL:
			Perspective facet = replayPerspective(arg2int, true);
			if (facet != null) {
				final Perspective parent = facet.getParent();
				if (isExcludeAction(modifiers) && isShiftDown(modifiers)) {
					System.out
							.println("  Warning: Replayer.replayOp ignoring clickBar with negative polarity and Shift ("
									+ replayLocation.replayLocationDesc() + ") on " + facet
									+ printModifiersEx(modifiers));
				} else if (parent == null) {
					System.out.println("  Warning: Ignoring clickBar (" + replayLocation.replayLocationDesc()
							+ ") on top-level tag " + facet + printModifiersEx(modifiers));
				} else {
					System.out.println("  clickBar (" + replayLocation.replayLocationDesc() + ") " + parent + "."
							+ facet + printModifiersEx(modifiers));
					final Rank rank = lookupRank(parent, true);
					if (rank != null) {
						perform(modifiers, facet, rank, PMELocation.BAR, null, null);
					}
				}
			}
			break;
		case RANK_LABEL_REPLAY:
			final Perspective pvP = replayPerspective(arg2int, true);
			if (pvP != null) {
				PMELocation location = PMELocation.RANK_LABEL;
				Rank rank = lookupRank(pvP, false);
				if (rank == null) {
					final Perspective parent = pvP.getParent();
					assert parent != null : pvP;
					rank = lookupRank(parent, true);
					assert rank != null : parent;
					System.out.println("   Warning: Treating clickRank on childless tag " + pvP
							+ printModifiersEx(modifiers) + " as clickRank on " + rank);
					// tagWall().connectToRank(rank);
					location = PMELocation.DEFAULT;
				}
				System.out.println("  clickRank " + rank);
				perform(modifiers, pvP, rank, location, null, null);
			}
			break;
		case IMAGE:
			// Annoying in replayer.
			//
			// clickItem(arg2int, "clickImage",
			// PerspectiveMarkupElementLocation.SELECTED_ITEM);
			break;
		case THUMBNAIL:
			final String msg = "Click thumb " + ((modifiers & Util.CLICK_THUMB_INTENTIONALLY_MODIFIER) != 0
					? "intentionally" : "unintentionally");
			if (art.getGrid().getVisible()) {
				if (query().getOnCount() > 0) {
					if (arg2int > 0) {
						final Item item = Item.ensureItem(arg2int);
						if (art.getItemOffset(item) >= 0) {
							System.out.println("  " + msg + " " + item);
							perform(Util.LEFT_BUTTON_MASK | modifiers, null, null, PMELocation.GRID_ITEM, null, item);
						} else {
							item.getDescription(query());
							System.out.println("Warning: Can't " + msg + " because item doesn't satisfy the query: "
									+ item + " " + query());
						}
					} else {
						System.out.println("Warning: Can't " + msg + " because itemID<=0: " + arg2int);
					}
				} else {
					System.out.println("Warning: Can't " + msg + " because query().getOnCount() == 0");
				}
			} else {
				System.out.println("Warning: Can't " + msg + " because ResultsGrid isn't visible");
			}
			break;
		case DEFAULT_REPLAY:
			defaultPerspectiveReplayOp(arg2int, modifiers, replayLocation);
			break;
		case GRID_SCROLL:
			final int newSelectedItemOffset = arg2int;
			System.out.println("  grid scroll newSelectedItemOffset=" + newSelectedItemOffset);
			if (art.getGrid().getVisible()) {
				if (isInRange(newSelectedItemOffset, 0, queryOnCount - 1)) {
					art.getGrid().computeSelectedItemFromOffset(newSelectedItemOffset);
				} else {
					System.out.println("  Warning: GRID_SCROLL ignoring newSelectedItemOffset >= queryOnCount");
				}
			} else {
				System.out.println("  Warning: Ignoring GRID_SCROLL because ResultsGrid isn't visible");
			}
			break;
		case ARROW:
			final int keyCode = arg2int;
			final String keyText = KeyEvent.getKeyText(keyCode);
			if (MyInputEventHandler.isArrowKeyOrCtrlA(keyCode, modifiers)) {
				if (nButtons(modifiers) > 0) {
					System.out.println("  Arrow ignoring" + Util.printModifiersEx(modifiers & Util.BUTTON_MASK));
					modifiers &= ~Util.BUTTON_MASK;
				}
				if ("A".equals(keyText) && arrowFocus == null) {
					System.out.println("  Warning: CONTROL+A is not applicable for Matches");
				} else if (arrowFocus == null && !art.getGrid().getVisible()) {
					System.out.println("Wrning: Can't use arrow keys because ResultsGrid isn't visible");
				} else {
					System.out.println("  Arrow key='" + keyText + "'" + printModifiersEx(modifiers) + " from "
							+ (arrowFocus == null ? "Selected " + query().getGenericObjectLabel(false) : arrowFocus));
					art.handleArrow(keyCode, modifiers);
				}
			} else {
				System.out.println("  Arrow ignoring non-arrow keyCode '" + keyText + "' keyCode=" + keyCode
						+ printModifiersHex(keyCode) + printModifiersEx(modifiers) + "\n ARROW_KEYS are: "
						+ UtilString.valueOfDeep(MyInputEventHandler.getARROW_KEYS()));
			}
			break;
		case BUTTON:
			// ELLIPSIS is handled (ignored) at the top.
			if (arg2.equals(CLEAR)) {
				System.out.println("  button " + arg2);
				art.getHeader().clickClear();
				// } else if (arg2.equals(ELLIPSIS)) {
				// System.out.println(" ignoring button " + arg2);
				// // tagWall.clickEllipsis();
			} else if (arg2int > 0) {
				// BUTTON was used for anything in a MarkupViz [obsolete]
				defaultPerspectiveReplayOp(arg2int, modifiers, replayLocation);
			} else {
				System.out.println("  removeTextSearch " + arg2);
				final boolean removedTextSearch = art.removeTextSearch(arg2);
				if (!removedTextSearch) {
					System.err.println("Warning: Didn't removeTextSearch '" + arg2 + "'");
				}
			}
			break;
		case SEARCH:
			final String errorMsg = Query.isIllegalSearch(arg2);
			if (errorMsg != null) {
				System.out.println("  Warning: Ignoring illegal search '" + arg2 + "': " + errorMsg);
			} else {
				System.out.println("  search '" + arg2 + "'");
				query().addTextSearch(arg2);
				art.updateQuery();
			}
			break;
		case SETSIZE:
			final int width = arg2int;
			final int height = modifiers;
			System.out
					.println("  setSize " + art.getWidth() + " x " + art.getHeight() + " => " + width + " x " + height);
			art.replaySetSize(width, height);
			art.validate();
			break;
		case RESTRICT:
			System.out.println("  Restrict");
			art.restrictData();
			break;
		case REORDER:
			final int facetType = arg2int;
			final String facetTypeName = SortLabelNMenu.getName(facetType, query());
			if (facetTypeName != null) {
				System.out.println("  Reorder by " + facetTypeName);
				art.getHeader().chooseReorder(facetType);
			} else {
				System.out.println(" Can't reorder by unknown facet type " + facetType);
			}
			break;
		case TOGGLE_POPUPS:
			System.out.println("  Toggle Popups ");
			art.togglePopups();
			break;
		case INFLUENCE_DIAGRAM:
			facet = replayPerspective(arg2int, true);
			assert facet != null;
			art.computeInfluenceDiagramNow(facet);
			break;
		// case SHOW_MORE_HELP:
		// System.out.println(" Show More Help ");
		// Bungee.showMoreHelp();
		// break;
		case ZOOM:
			facet = replayPerspective(arg2int, false);
			final char keyChar = (char) modifiers;
			System.out.println("  Zoom to" + (facet == null ? "" : " " + facet) + " suffix='"
					+ (keyChar == '\b' ? "\\b" : keyChar) + "'");
			final char lowerCase = Character.toLowerCase(keyChar);
			if (lowerCase != keyChar) {
				System.out.println("wrning: ZOOM lower-casing " + keyChar);
			}
			tagWall().zoomTo(lowerCase, facet);
			break;
		case MODE:
			final Preferences oldPreferences = art.getPreferences();
			final String mode = arg2.replace('_', ',');
			assert mode != null;
			final Preferences intendedPreferences = art.getPreferences(null, mode, true);
			UtilString.indentMore("  Change mode: changes=");
			UtilString.indent(oldPreferences.differenceOrQuoteNone(intendedPreferences));
			UtilString.indentLess(null);
			art.setPreferences(intendedPreferences);
			final Preferences newPreferences = art.getPreferences();
			final String actualVsIntendedDifference = newPreferences.difference(intendedPreferences);
			if (actualVsIntendedDifference.length() > 0) {
				UtilString.indentMore("  Actual changes=");
				UtilString.indent(oldPreferences.differenceOrQuoteNone(newPreferences));
				UtilString.indentLess(null);
			}
			break;
		case ERROR:
			System.out.println("Warning: " + arg2);
			break;
		case WRITEBACK:
			System.out.println("Writeback");
			break;
		default:
			assert false : args[1];
		}

	}

	/**
	 * @param replayLocation
	 *
	 *            DEFAULT_REPLAY for MarkupViz [obsolete], FacetTreeViz,
	 *            TopTagsViz, and PerspectiveList.
	 *
	 *            BUTTON [when arg2int > 0] for anything in a MarkupViz
	 *            [obsolete].
	 */
	protected void defaultPerspectiveReplayOp(final int facetID, final int modifiers,
			final @NonNull ReplayLocation replayLocation) {
		final Perspective facet = replayPerspective(facetID, true);
		if (facet != null) {
			final String modifiersDesc = printModifiersEx(modifiers);
			if (facet.isRestriction(true) && isControlOrShiftDown(modifiers)) {
				System.out.println(" Warning: Ignoring click in " + replayLocation + " on required tag " + facet
						+ " with Control or Shift keys down:" + modifiersDesc);
			} else if (facet.isTopLevel()) {
				System.out.println(
						"  Ignoring click in " + replayLocation + " on top-level tag " + facet + modifiersDesc);
				// final Rank rank = lookupRank(facet, true);
				// assert rank != null;
				// System.out.println(" Treating click in " + replayLocation + "
				// on top-level tag " + facet
				// + modifiersDesc + " as ReplayLocation.RANK_LABEL_REPLAY");
				// perform(modifiers, facet, rank, PMELocation.RANK_LABEL, null,
				// null);
			} else {
				System.out.println("  Click in " + replayLocation + " on required=" + facet.restrictionPolarity()
						+ " tag " + facet + modifiersDesc);
				perform(modifiers, facet, null, PMELocation.DEFAULT, null, null);
			}
			// } else {
			// System.out.println(" Warning: Ignoring Click in " +
			// replayLocation + " on null tag " + modifiersDesc);
		}
	}

	private void perform(int modifiers, final @Nullable Perspective perspective, final @Nullable Rank rank,
			final @NonNull PMELocation location, final @Nullable String zoomText, final @Nullable Item item) {
		final int shiftKeys = modifiers & Util.ALL_SHIFT_KEYS_PLUS_EXCLUDE_PLUS_CLICK_THUMB_INTENTIONALLY;
		int buttons = modifiers & Util.BUTTON_MASK;
		if (weight(buttons) > 1 && (buttons & LEFT_BUTTON_MASK) != 0) {
			System.out.println("    Warning: Replayer.perform ignoring extra buttons"
					+ printModifiersEx(buttons & ~LEFT_BUTTON_MASK));
			buttons = LEFT_BUTTON_MASK;
		} else if (weight(buttons) > 1) {
			assert (buttons & MIDDLE_BUTTON_MASK) != 0 : buttons;
			System.out.println("    Warning: Replayer.perform ignoring extra buttons"
					+ printModifiersEx(buttons & ~MIDDLE_BUTTON_MASK));
			buttons = MIDDLE_BUTTON_MASK;
		} else if (weight(buttons) == 0) {
			System.out.println("    Wrning: Replayer.perform Adding LEFT_BUTTON to modifiers");
			buttons = LEFT_BUTTON_MASK;
		}
		if ((modifiers & ~shiftKeys & ~buttons) > 0) {
			System.out.println(
					"    Warning: Replayer.perform Ignoring" + printModifiersEx(modifiers & ~shiftKeys & ~buttons));
			modifiers &= shiftKeys & buttons;
		}
		if (performInternal(shiftKeys, buttons, perspective, rank, location, zoomText, item)) {
			// Yay!!! No shift keys problem!
		} else if ((shiftKeys & InputEvent.SHIFT_DOWN_MASK) != 0 && weight(shiftKeys) > 1
				&& performInternal(InputEvent.SHIFT_DOWN_MASK, buttons, perspective, rank, location, zoomText, item)) {
			System.out.println("    Warning: Replayer.perform Ignoring"
					+ printModifiersEx(shiftKeys & ~InputEvent.SHIFT_DOWN_MASK) + " of" + printModifiersEx(shiftKeys));
		} else if ((shiftKeys & CLICK_THUMB_INTENTIONALLY_MODIFIER) != 0 && weight(shiftKeys) > 1 && performInternal(
				CLICK_THUMB_INTENTIONALLY_MODIFIER, buttons, perspective, rank, location, zoomText, item)) {
			System.out.println("    Warning: Replayer.perform Ignoring"
					+ printModifiersEx(shiftKeys & ~CLICK_THUMB_INTENTIONALLY_MODIFIER) + " of"
					+ printModifiersEx(shiftKeys));
		} else if (performInternal(0, buttons, perspective, rank, location, zoomText, item)) {
			System.out.println(" Warning: Replayer.perform Ignoring" + printModifiersEx(shiftKeys));
		} else {
			System.out.println("    Warning: Can't find UserAction for" + printModifiersEx(modifiers) + " perspective="
					+ perspective + " rank=" + rank + " UserAction.location=" + location + " zoomText=" + zoomText
					+ " item=" + item
					+ (perspective == null ? ""
							: " isRequired=" + perspective.isRestriction(true) + " isProhibited="
									+ perspective.isRestriction(false)));
		}
	}

	/**
	 * Called only by perform
	 *
	 * @return whether a UserAction was found and performed.
	 *
	 *         If it fails with buttons, try LEFT_BUTTON_MASK instead.
	 */
	private boolean performInternal(final int shiftKeys, final int buttons, final @Nullable Perspective perspective,
			final @Nullable Rank rank, final @NonNull PMELocation location, final @Nullable String zoomText,
			final @Nullable Item item) {
		boolean result = maybePerform(shiftKeys | buttons, perspective, rank, location, zoomText, item);
		if (!result && buttons != Util.LEFT_BUTTON_MASK) {
			result = maybePerform(shiftKeys | Util.LEFT_BUTTON_MASK, perspective, rank, location, zoomText, item);
			if (result) {
				assert (buttons & ~Util.LEFT_BUTTON_MASK) != 0 && weight(buttons) == 1 : printModifiersEx(buttons);
				System.out.println("    Warning: Substituting" + printModifiersEx(Util.LEFT_BUTTON_MASK) + " for"
						+ printModifiersEx(buttons));
			}
		}
		return result;
	}

	/**
	 * Called only by performInternal
	 *
	 * @return whether a UserAction was found (whether it performed successfully
	 *         or not).
	 */
	private boolean maybePerform(final int modifiers, final @Nullable Perspective perspective,
			final @Nullable Rank rank, final @NonNull PMELocation location, final @Nullable String zoomText,
			final @Nullable Item item) {
		final UserAction userAction = UserAction.getAction(art, modifiers, perspective, rank, location, zoomText, item);
		if (userAction == null) {
			System.out.println(
					"    Warning: no userAction found for" + printModifiersEx(modifiers) + " perspective=" + perspective
							+ " rank=" + rank + " location=" + location + " zoomText=" + zoomText + " item=" + item);
		} else {
			assert query().isQueryValid();
			if (!userAction.performWhenQueryValid()) {
				// final Markup mouseDocMarkup =
				// art.getMouseDoc().getTipMarkup();
				// if (mouseDocMarkup == null ||
				// !mouseDocMarkup.contains(UserAction.NO_MATCHES)) {
				System.err.println("    Warning: userAction failed: " + userAction);
				// }
			}
		}
		return userAction != null;
	}

	/**
	 * @return pvP's rank, displaying it if necessary. Does not change
	 *         connectedRank. null means pvP has no child Tags.
	 */
	private @Nullable Rank lookupRank(final @NonNull Perspective pvP, final boolean isPrintError) {
		Rank rank = tagWall().lookupRank(pvP);
		if (rank == null) {
			if (pvP.isEffectiveChildren()) {
				System.out.println(
						"\nReplayer.lookupRank displaying unexpectedly non-displayed rank for " + pvP.path(true, true));
				final Rank oldRank = tagWall().connectedRank();
				UserAction.displayAncestors(pvP, art);
				rank = tagWall().lookupRank(pvP);
				assert rank != null : pvP.path();
				// Ensure rank is displayed, but connectToRank(oldRank). That
				// way, a click on a BAR on a deselected Rank doesn't turn into
				// SelectPerspective.
				tagWall().connectToRank(oldRank);
			} else if (isPrintError) {
				System.out.println(
						"Warning Replayer.lookupRank: ignoring operation because " + pvP + " has no child Tags");
			}
		}
		return rank;
	}

	private static int getIntArg(final @NonNull String arg) {
		try {
			return Integer.parseInt(arg);
		} catch (final NumberFormatException e) {
			return -1;
		}
	}

	private static @NonNull String possibleModifiers(final int modifiers,
			final @NonNull ReplayLocation replayLocation) {
		String result;
		switch (replayLocation) {
		case BAR:
		case BAR_LABEL:
		case RANK_LABEL_REPLAY:
		case IMAGE:
		case DEFAULT_REPLAY:
		case ARROW:
		case BUTTON:
			result = modifiers == 0 ? "" : "," + Util.printModifiersEx(modifiers);
			break;
		case THUMBNAIL:
			result = (modifiers & Util.CLICK_THUMB_INTENTIONALLY_MODIFIER) == 0 ? ", Unintentionally"
					: ", Intentionally";
			break;
		case ZOOM:
			final char keyChar = (char) modifiers;
			result = ", '" + (keyChar == '\b' ? "\\b" : keyChar) + "'";
			break;
		case GRID_SCROLL:
			result = " newVisRowOffset=" + UtilString.addCommas(modifiers);
			break;
		default:
			result = "," + Util.printModifiersEx(modifiers);
			break;
		}
		assert result != null;
		return result;
	}

	/**
	 * @return Perspective, String, Item, or Integer
	 */
	private @Nullable Object possiblePerspective(final int arg2int, final @NonNull ReplayLocation replayLocation) {
		Object result;
		switch (replayLocation) {
		case BAR:
		case BAR_LABEL:
		case RANK_LABEL_REPLAY:
		case DEFAULT_REPLAY:
		case ZOOM:
			result = replayPerspective(arg2int, false);
			break;
		case REORDER:
			result = SortLabelNMenu.getName(arg2int, query());
			break;
		case THUMBNAIL:
		case IMAGE:
			if (arg2int > 0) {
				result = Item.ensureItem(arg2int);
			} else {
				result = null;
				System.err.println(" Warning: item id=" + arg2int + " for replayLocation=IMAGE.");
			}
			break;
		case ARROW:
			result = KeyEvent.getKeyText(arg2int);
			break;
		case GRID_SCROLL:
			result = "newSelectedItemOffset=" + UtilString.addCommas(arg2int);
			break;
		default:
			result = arg2int;
			break;
		}
		return result;
	}

	/**
	 * @return null means no such Perspective (zombie?)
	 */
	private @Nullable Perspective replayPerspective(final int facetID, final boolean isPrintError) {
		Perspective perspective = null;
		final Query query = query();
		if (isInRange(facetID, 1, query.nPerspectivesRaw())) {
			try {
				perspective = query.findPerspectiveNow(facetID);
			} catch (final AssertionError e) {
				// Warning will be printed below.
			}
		}
		if (isPrintError && perspective == null) {
			System.out.println("  Warning: Replayer.replayPerspective Ignoring operation on unknown tag with ID="
					+ facetID + ". (There are " + query.nPerspectivesRaw() + " total tags.)");
		}
		return perspective;
	}

	private @NonNull TagWall tagWall() {
		return art.getTagWall();
	}

	@NonNull
	Query query() {
		return art.getQuery();
	}

}