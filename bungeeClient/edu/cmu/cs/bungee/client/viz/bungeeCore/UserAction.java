package edu.cmu.cs.bungee.client.viz.bungeeCore;

import static edu.cmu.cs.bungee.javaExtensions.Util.EXCLUDE_ACTION;
import static edu.cmu.cs.bungee.javaExtensions.Util.LEFT_BUTTON_MASK;
import static edu.cmu.cs.bungee.javaExtensions.Util.MIDDLE_BUTTON_MASK;
import static edu.cmu.cs.bungee.javaExtensions.Util.RIGHT_BUTTON_MASK;
import static edu.cmu.cs.bungee.javaExtensions.Util.ensureButton;
import static edu.cmu.cs.bungee.javaExtensions.Util.nButtons;
import static edu.cmu.cs.bungee.javaExtensions.Util.printModifiersEx;
import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.awt.event.InputEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.Perspective.ToggleFacetResult;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.grid.GridElement;
import edu.cmu.cs.bungee.client.viz.markup.BungeeAPText;
import edu.cmu.cs.bungee.client.viz.markup.FacetNode;
import edu.cmu.cs.bungee.client.viz.markup.LetterLabeledAPText;
import edu.cmu.cs.bungee.client.viz.markup.MarkupSearchText;
import edu.cmu.cs.bungee.client.viz.markup.PerspectiveMarkupAPText;
import edu.cmu.cs.bungee.client.viz.popup.PopupSummary;
import edu.cmu.cs.bungee.client.viz.selectedItem.SelectedItemColumn;
import edu.cmu.cs.bungee.client.viz.tagWall.Bar;
import edu.cmu.cs.bungee.client.viz.tagWall.LetterLabeled;
import edu.cmu.cs.bungee.client.viz.tagWall.Letters;
import edu.cmu.cs.bungee.client.viz.tagWall.PerspectiveViz;
import edu.cmu.cs.bungee.client.viz.tagWall.Rank;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * Provides mouseDoc() and perform() for mouse enter and click gestures.
 * (HandleKey provides perform for keyboard gestures.)
 */
public class UserAction implements RedrawCallback {

	public enum PMELocation {
		RANK_LABEL, BAR, DEFAULT, LETTERS, ZOOM_LETTER, GRID_ITEM, SELECTED_ITEM, SEARCH_TEXT, POPUP
	}

	/**
	 * REQUIRED, PROHIBITED, or UNSELECTED
	 */
	enum PerspectiveSelection {
		REQUIRED, PROHIBITED, UNSELECTED;

		static PerspectiveSelection toPerspectiveSelection(final boolean isRequired, final boolean isProhibited) {
			assert !(isRequired && isProhibited);
			return isRequired ? REQUIRED : isProhibited ? PROHIBITED : UNSELECTED;
		}
	}

	private enum Command {
		SelectRank, TogglePerspectiveList("tagLists"), DisplayPerspective(
				"shortcuts"), Unselect, SelectPerspective, SelectPerspectiveRangeShift("shortcuts"),

		SelectPerspectiveRangeControl("shortcuts"), StartZoom("zoom"), SelectItem, MayHideTransients, Zoom(
				"zoom"), TogglePopups, ProhibitPerspective("shortcuts"),

		PerspectiveEditMenu("editing"), ItemEditMenu("editing"), SetSelectedForEdit("editing"), AddTag(
				"editing"), ShowItemInBrowser, RemoveTextSearch;

		private final @Nullable String expertFeature;

		Command() {
			expertFeature = null;
		}

		Command(final String _expertFeature) {
			expertFeature = _expertFeature;
		}

		/**
		 * @return name of required expert feature, or null if none required.
		 *         (No Command requires more than one.)
		 */
		@Nullable
		String getExpertFeature() {
			return expertFeature;
		}
	}

	private static final @NonNull Map<MouseInput, Command> COMMAND_MAP = new HashMap<>();

	private final @NonNull MouseInput mouseInput;
	private final @NonNull Bungee art;
	private final @NonNull Command command;

	private final @Nullable Perspective facet;
	private final @Nullable Rank rank;
	private final @Nullable String text;
	private final @Nullable Item item;

	public int getItemID() {
		return item != null ? item.getID() : -1;
	}

	private UserAction(final @NonNull Command _command, final @NonNull MouseInput _input,
			final @Nullable Perspective _facet, final @Nullable Item _item, final @Nullable String _text,
			final @Nullable Rank _rank, final @NonNull Bungee _art) {
		super();
		assert _input.getLocation() != PMELocation.BAR || _rank != null : _input;

		command = _command;
		mouseInput = _input;
		facet = _facet;
		item = _item;
		text = _text;
		rank = _rank;
		art = _art;
	}

	/**
	 * This is conservative in that it returns false if the query is invalid.
	 *
	 * @param callback
	 *            if non-null and selectedOnCount isn't cached, use total count
	 *            and callback when it is.
	 *
	 * @return Will invoking command on facet with modifiers result in zero
	 *         hits?
	 */
	private boolean isZeroHits(final @Nullable RedrawCallback callback) {
		final Query query = query();
		boolean result = facet != null && !facet.isTopLevel() && command != Command.Unselect
				&& command != Command.TogglePerspectiveList && command != Command.PerspectiveEditMenu
				&& (rank == null || Util.nonNull(rank).isConnected()) && query.isQueryValid();
		if (result) {
			assert facet != null;
			final @NonNull Perspective nonNullFacet = facet;
			final int modifiers = mouseInput.getModifiers();
			final boolean polarity = !Util.isExcludeAction(modifiers);
			if (nonNullFacet.isRestriction(polarity)) {
				// Deselecting an exclude can never decrease query onCount.
				//
				// Deselecting an include can only decrease query onCount if
				// there is another include on the same parent that won't be
				// deselected. It can only decrease to zero if all the other
				// includes have onCount==0.

				final Perspective parent = nonNullFacet.getParent();
				assert parent != null;
				if (polarity && !Perspective.isDeselectOthers(modifiers) && parent.nRestrictions(polarity) > 1) {
					for (final Perspective sibling : parent.restrictions(polarity)) {
						if (sibling != nonNullFacet && sibling.getOnCount() > 0) {
							result = false;
							break;
						}
					}
				} else {
					result = false;
				}
			} else if (nonNullFacet.isRestriction(!polarity)) {
				result = false;
			} else {
				final int problemCount = polarity ? 0 : query.getOnCount();
				final int onCount = nonNullFacet.getOnCount();
				final int totalCount = nonNullFacet.getTotalCount();
				result = isInRange(problemCount, onCount, totalCount);
				if (result && onCount != totalCount) {
					result = false;
					// too slow...
					// final int selectedOnCount =
					// art.getTagWall().perspectiveList.selectedOnCount(nonNullFacet,
					// callback);
					// result = selectedOnCount == problemCount;
				}
			}
		}
		return result;
	}

	// /**
	// * If there are sibling restrictions, predicting isZeroHits is tricky. For
	// * required, another required or excluded can make onCount go to zero, but
	// * only requireds will be obviated. For excluded,
	// *
	// * @param polarity
	// * @return
	// */
	// private boolean isSiblingRestrictions(final boolean polarity) {
	// assert facet != null;
	// final Perspective parent = facet.getParent();
	// int parentNrestrictions = parent.nRestrictions();
	// assert facet != null;
	// if (facet.isRestriction(polarity)) {
	// parentNrestrictions--;
	// }
	// final boolean result = parentNrestrictions > 0;
	// return result;
	// }

	private boolean hasRequiredExpertFeatures() {
		final String expertFeature = command.getExpertFeature();
		return art.isReplaying() || !UtilString.isNonEmptyString(expertFeature)
				|| ArrayUtils.contains(art.getPreferences().features2string().split(","), expertFeature);
	}

	private boolean isShiftSelectOK(final @Nullable Perspective parent) {
		return command != Command.SelectPerspectiveRangeShift || art.getArrowFocus() == null
				|| art.getArrowFocusParent() == parent;
	}

	public static @Nullable UserAction getAction(final @NonNull PInputEvent _pInputEvent, final @NonNull Bungee art,
			final int modifiers) {
		final PNode pickedNode = _pInputEvent.getPickedNode();
		assert pickedNode != null;
		return getAction(pickedNode, art, modifiers);
	}

	private static final int SHIFT_RIGHT = InputEvent.SHIFT_DOWN_MASK | RIGHT_BUTTON_MASK;

	/**
	 * The findNodeType() calls make this slow.
	 */
	static @Nullable UserAction getAction(final @NonNull PNode pickedNode, final @NonNull Bungee art, int modifiers) {
		if ((modifiers & SHIFT_RIGHT) == SHIFT_RIGHT) {
			modifiers = (modifiers & ~SHIFT_RIGHT) | MIDDLE_BUTTON_MASK;
		}
		final Bar bar = (Bar) findNodeType(pickedNode, Bar.class);

		final FacetNode facetNode = bar != null ? bar
				: (FacetNode) findNodeType(pickedNode, PerspectiveMarkupAPText.class);
		Perspective perspective = facetNode == null ? null : facetNode.getFacet();

		final GridElement gridElement = (GridElement) findNodeType(pickedNode, GridElement.class);
		final Item item = gridElement == null ? null : gridElement.getWrapper().getItem();

		final String nodeText = getText(pickedNode);
		final Rank rank = (Rank) findNodeType(pickedNode, Rank.class);
		if (perspective == null && nodeText != null && rank != null) {
			final LetterLabeledAPText letterText = (LetterLabeledAPText) findNodeType(pickedNode,
					LetterLabeledAPText.class);
			if (letterText != null) {
				perspective = letterText.perspectiveViz.p;
			}
		}
		final PMELocation location = getLocation(pickedNode, bar != null, perspective, rank, item != null,
				nodeText != null);
		assert location != PMELocation.BAR || rank != null : PiccoloUtil.ancestorString(pickedNode);

		return getAction(art, modifiers, perspective, rank, location, nodeText, item);
	}

	static @Nullable UserAction getAction(final @NonNull Bungee art, final int modifiers,
			final @Nullable Perspective perspective, final @Nullable Rank rank, final @NonNull PMELocation location,
			final @Nullable String _text, final @Nullable Item item) {
		final PerspectiveSelection facetSelectedValue = perspective == null ? null
				: PerspectiveSelection.toPerspectiveSelection(perspective.isRestriction(true),
						perspective.isRestriction(false));
		final MouseInput mouseInput = new MouseInput(location, modifiers, facetSelectedValue,
				perspective != null && perspective.isTopLevel(), rank != null && rank.isConnected());
		final Command command = COMMAND_MAP.get(mouseInput);
		final UserAction result = command == null ? null
				: new UserAction(command, mouseInput, perspective, item, _text, rank, art);
		return result;
	}

	/**
	 * @return the getText() of a LetterLabeledAPText or MarkupSearchText.
	 *
	 *         Does NOT return text of FacetNodes!
	 */
	private static @Nullable String getText(final @Nullable PNode pickedNode) {
		BungeeAPText bungeeAPText = (BungeeAPText) findNodeType(pickedNode, LetterLabeledAPText.class);
		if (bungeeAPText == null) {
			bungeeAPText = (BungeeAPText) findNodeType(pickedNode, MarkupSearchText.class);
		}
		final String result = bungeeAPText == null ? null : bungeeAPText.getText();
		return result;
	}

	private static @NonNull PMELocation getLocation(final @Nullable PNode pickedNode, final boolean isBar,
			final @Nullable Perspective perspective, final @Nullable Rank rank, final boolean isItem,
			final boolean isNodeText) {
		PMELocation location = null;
		if (findNodeType(pickedNode, PopupSummary.class) != null
				|| findNodeType(pickedNode, MouseDocLine.class) != null) {
			location = PMELocation.POPUP;
		} else if (isNodeText && findNodeType(pickedNode, MarkupSearchText.class) != null) {
			location = PMELocation.SEARCH_TEXT;
		} else if (isNodeText) {
			location = PMELocation.ZOOM_LETTER;
		} else if (findNodeType(pickedNode, Letters.class) != null) {
			location = PMELocation.LETTERS;
		} else if (isBar) {
			location = PMELocation.BAR;
		} else if (rank != null && rank.hasPVp(perspective)) {
			location = PMELocation.RANK_LABEL;
		} else if (perspective != null) {
			location = PMELocation.DEFAULT;
		} else if (isItem && findNodeType(pickedNode, SelectedItemColumn.class) != null) {
			location = PMELocation.SELECTED_ITEM;
		} else if (isItem) {
			location = PMELocation.GRID_ITEM;
		}
		assert location != null : "\n pickedNode=" + PiccoloUtil.ancestorString(pickedNode) + "\n isBar=" + isBar
				+ "\n perspective=" + perspective + "\n rank=" + rank + "\n isItem=" + isItem + "\n isNodeText="
				+ isNodeText;
		return location;
	}

	private static @Nullable Object findNodeType(final @Nullable PNode pickedNode, final @NonNull Class<?> clasz) {
		return PiccoloUtil.findAncestorNodeType(pickedNode, clasz);
	}

	private static final @NonNull int[] BUTTON_MASKS = { Util.LEFT_BUTTON_MASK, Util.MIDDLE_BUTTON_MASK,
			Util.RIGHT_BUTTON_MASK };

	private static final @NonNull String[] BUTTON_NAMES = { "Left", "Middle", "Right" };

	public static boolean setClickDesc(final @NonNull PInputEvent e, final int modifiersEx, final @NonNull Bungee art) {
		Markup clickDesc = null;
		UserAction userAction = null;
		if (art.getIsEditing()) {
			final Markup desc = DefaultMarkup.emptyMarkup();
			boolean isLeftOnly = true;
			for (int i = 0; i < 3; i++) {
				userAction = UserAction.getAction(e, art, modifiersEx | BUTTON_MASKS[i]);
				if (userAction != null) {
					final Markup mouseDoc = userAction.mouseDoc(true, null);
					if (mouseDoc != null) {
						mouseDoc.add(0, MarkupStringElement.getElement("(" + BUTTON_NAMES[i] + ") "));
						if (desc.size() > 0) {
							desc.add("; ");
						}
						desc.addAll(mouseDoc);
						if (i > 0) {
							isLeftOnly = false;
						}
					}
				}
			}
			if (isLeftOnly && desc.size() > 0) {
				desc.remove(0);
			}
			if (desc.size() > 0) {
				clickDesc = desc;
			}
		} else {
			userAction = UserAction.getAction(e, art, ensureButton(modifiersEx));
			if (userAction != null) {
				clickDesc = userAction.mouseDoc(true, null);
			}
		}
		assert clickDesc == null || UtilString.isNonEmptyString(clickDesc.toText(art.getMouseDoc())) : userAction;
		art.setClickDesc(clickDesc);
		return clickDesc != null;
	}

	/**
	 * @param wantResult
	 *            Many callers just want to know if returned value is null. If
	 *            !wantResult, don't bother to actually populate it.
	 *
	 * @return Markup, or null if zeroHits, lacks expert feature, inappropriate
	 *         SelectPerspectiveRangeControl/Shift, or unknown ShowItemInBrowser
	 *         URL.
	 */
	@Nullable
	Markup mouseDoc(final boolean wantResult, final @Nullable RedrawCallback callback) {
		return (hasRequiredExpertFeatures() && !isZeroHits(callback)) ? maybeZeroHitsMouseDoc(wantResult) : null;
	}

	private static final Markup DUMMY_ZERO_HITS_RESULT = DefaultMarkup.emptyMarkup();

	protected @Nullable Markup maybeZeroHitsMouseDoc(final boolean wantResult) {
		return maybeZeroHitsMouseDoc(wantResult ? DefaultMarkup.emptyMarkup() : DUMMY_ZERO_HITS_RESULT);
	}

	/**
	 * @param result
	 *            Many callers just want to know if returned value is null. If
	 *            result==DUMMY_ZERO_HITS_RESULT, don't bother to actually
	 *            populate it.
	 *
	 * @return mouse doc even for events that are illegal because of
	 *         isZeroHits().
	 */
	protected @Nullable Markup maybeZeroHitsMouseDoc(Markup result) {
		final Perspective parent = facet != null ? facet.getParent() : null;
		switch (command) {
		case SelectRank:
			assert rank != null;
			addMouseDoc(result, OPEN_CATEGORY, rank.firstPerspective());
			break;
		case TogglePerspectiveList:
			if (art.getTagWall().perspectiveList.isHidden()) {
				addMouseDoc(result, LIST_ALL, facet, TAGS);
			} else {
				addMouseDoc(result, HIDE_TAGS);
			}
			break;
		case DisplayPerspective:
			addMouseDoc(result, ENSURE_THAT, facet, IS_DISPLAYED);
			break;
		case Unselect:
			assert facet != null;
			addMouseDoc(result, REMOVE_RESTRICTION, facet.getFacetType(),
					facet.isRestriction(true) ? EQUALS : NOT_EQUALS_FILTER_TYPE, facet);
			break;
		case SelectPerspective:
		case ProhibitPerspective:
			assert facet != null;
			addMouseDoc(result, ADD_FILTER, facet.getFacetType(),
					command == Command.SelectPerspective ? EQUALS : NOT_EQUALS_FILTER_TYPE, facet);
			break;
		case SelectPerspectiveRangeControl:
		case SelectPerspectiveRangeShift:
			assert parent != null;
			addMouseDoc(result, ADD);
			final boolean isShift = command == Command.SelectPerspectiveRangeShift;
			if (isShift) {
				if (art.getArrowFocusParent() == parent) {
					addMouseDoc(result, art.getArrowFocus(), DASH);
				} else {
					result = null;
					break;
				}
			}
			addMouseDoc(result, facet, TO_FILTER_ON, parent);
			break;
		case StartZoom:
			addMouseDoc(result, ZOOM_PAN);
			break;
		case SelectItem:
			addMouseDoc(result, "Select this " + art.getQuery().getGenericObjectLabel(false));
			break;
		case ShowItemInBrowser:
			final String desc = query().itemURLdoc;
			if (UtilString.isNonEmptyString(desc)) {
				addMouseDoc(result, desc);
			} else {
				result = null;
			}
			break;
		case SetSelectedForEdit:
			addMouseDoc(result, SET_DEFAULT_TAG, facet);
			break;
		case Zoom:
			final String nonNullText = Util.nonNull(text);
			final int prefixLength = nonNullText.length() - 1;
			final StringBuilder msg = new StringBuilder("zoom into tags starting with '");
			msg.append(nonNullText).append("', as will typing '").append(nonNullText.charAt(prefixLength)).append("'");
			if (prefixLength > 0) {
				msg.append(";  backspace zooms out to tags starting with ");
				if (prefixLength == 1) {
					msg.append("any letter.");
				} else {
					msg.append("'").append(nonNullText.substring(0, prefixLength - 1)).append("'.");
				}
			}
			final String msgString = msg.toString();
			assert msgString != null;
			addMouseDoc(result, msgString);
			break;
		case RemoveTextSearch:
			addMouseDoc(result, "Remove text search on '" + text + "'");
			break;
		case PerspectiveEditMenu:
			addMouseDoc(result, SHOW_EDIT_TAG_MENU, facet);
			break;
		case ItemEditMenu:
			addMouseDoc(result, SHOW_EDIT_MENU, item);
			break;
		case AddTag:
			if (facet != null || item != null) {
				Item _item = item;
				Perspective _facet = facet;
				if (facet != null) {
					_item = art.getSelectedItem();
				} else {
					_facet = getEditing().defaultArgumentFacet;
				}
				addMouseDoc(result, ADD_TAG, _facet, TO, _item);
			}
			break;

		default:
			assert false : command;
			break;
		}
		return result;
	}

	private static void addMouseDoc(final Markup result, final @NonNull String s) {
		if (result != DUMMY_ZERO_HITS_RESULT) {
			result.add(s);
		}
	}

	private static void addMouseDoc(final Markup result, final MarkupElement... elements) {
		if (result != DUMMY_ZERO_HITS_RESULT) {
			for (final MarkupElement element : elements) {
				result.add(element);
			}
		}
	}

	private @NonNull Editing getEditing() {
		final Editing result = art.editing;
		assert result != null;
		return result;
	}

	protected final @NonNull Deque<UserAction> queue = new LinkedList<>();

	@Override
	public void redrawCallback() {
		final UserAction userAction = queue.poll();
		if (userAction != null) {
			userAction.perform();
		}
	}

	/**
	 * @return false means action failed; true means action was queued or it
	 *         succeeded.
	 */
	public boolean performWhenQueryValid() {
		if (query().isQueryValid() && queue.isEmpty()) {
			return perform();
		} else {
			queue.offer(this);
			query().queueRedraw(this);
			return true;
		}
	}

	/**
	 * @return true iff no error.
	 */
	public boolean perform() {
		final boolean isShiftSelectOK = facet == null || isShiftSelectOK(Util.nonNull(facet).getParent());
		final String expertFeature = command.getExpertFeature();
		Markup msg = null;
		if (!hasRequiredExpertFeatures()) {
			assert expertFeature != null;
			msg = DefaultMarkup.newMarkup(MarkupStringElement.getElement(expertFeature), IS_REQUIRED);
		} else if ("editing".equals(expertFeature) && !art.ensureEditing()) {
			// ensureEditing has called setTip(), so don't set msg
		} else if (!isShiftSelectOK) {
			msg = DefaultMarkup.newMarkup(
					MarkupStringElement.getElement("You can't SHIFT+SELECT until there is already a sibling of "),
					Util.nonNull(facet).getMarkupElement(), MarkupStringElement.getElement(" selected"));
		} else if (isZeroHits(null)) {
			msg = maybeZeroHitsMouseDoc(true);
			if (msg == null) {
				msg = DefaultMarkup.newMarkup();
			}
			msg.add(0, NO_MATCHES);
			msg.add(query().describeFilters());
		} else {
			try {
				performInternal();
			} catch (final Throwable e) {
				System.err.println("While UserAction.perform " + this + ":\n");
				// e.printStackTrace();
				throw (e);
			}
		}
		final boolean result = msg == null;
		if (!result) {
			art.setTip(msg);
		}
		if (art.isPrintActions) {
			System.out.println("UserAction.perform " + command + " ⇒ " + result + "\n (" + this + ")");
		}
		return result;
	}

	void performInternal() {
		final ToggleFacetResult toggleFacetResult = isToggleFacetCommand();
		final Perspective parent = facet != null ? facet.getParent() : null;

		switch (command) {
		case SelectItem:
			assert item != null && art.getItemOffset(item) >= 0 : item + " " + query();
			art.setSelectedItem(item, (mouseInput.getModifiers() & Util.CLICK_THUMB_INTENTIONALLY_MODIFIER) != 0, 0,
					ReplayLocation.THUMBNAIL);
			break;
		case ShowItemInBrowser:
			assert UtilString.isNonEmptyString(query().itemURLdoc);
			assert item != null;
			art.showItemInNewWindow(item);
			break;
		case Zoom:
			assert text != null;
			art.getTagWall().zoomTo(LetterLabeled.convertSuffix(text.charAt(text.length() - 1)), facet);
			break;
		case SelectRank:
			assert rank != null;
			art.getTagWall().connectToRank(rank);
			assert rank != null;
			art.setArrowFocus(art.getDefaultChild(rank.firstPerspective()));
			break;
		case TogglePerspectiveList:
			assert facet != null;
			art.getTagWall().togglePerspectiveList(facet);
			break;
		case DisplayPerspective:
			assert facet != null;
			displayAncestors(facet, art);
			break;

		// These change query
		case Unselect:
			assert facet != null;
			facet.deselectNundisplay();
			break;
		case ProhibitPerspective:
			selectFacet(false);
			break;
		case SelectPerspective:
		case SelectPerspectiveRangeControl:
			selectFacet(true);
			break;
		case SelectPerspectiveRangeShift:
			assert facet != null && parent != null && toggleFacetResult != null;
			final Collection<Perspective> toDisplay = parent.selectInterveningFacets(art.getArrowFocus(), facet, true,
					toggleFacetResult);
			assert toDisplay.size() > 0 : this + " " + art.getArrowFocus() + " " + facet + " " + toggleFacetResult;
			Perspective.displayAncestors(toDisplay);
			break;
		case RemoveTextSearch:
			assert text != null;
			final boolean isRemoved = art.removeTextSearch(text);
			assert isRemoved : "UserAction.performInternal RemoveTextSearch " + text + " getSearches="
					+ query().getSearches();
			break;

		// Editing
		case SetSelectedForEdit:
			getEditing().setSelectedForEdit(facet);
			break;
		case PerspectiveEditMenu:
			assert facet != null;
			getEditing().showFacetMiddleMenu(facet);
			break;
		case AddTag:
			if (facet != null) {
				getEditing().addTag(facet);
			} else if (item != null) {
				getEditing().addTag(item);
			} else {
				assert false : "No facet or item";
			}
			break;
		case ItemEditMenu:
			getEditing().showItemMiddleMenu(item);
			break;
		default:
			assert false : command;
			break;
		}

		if (toggleFacetResult != null) {
			toggleFacet(toggleFacetResult);
		}
	}

	public static void displayAncestors(final @NonNull Perspective facet, final @NonNull Bungee art) {
		if (facet.displayAncestors()) {
			final boolean isChanged = art.getTagWall().synchronizeWithQuery();
			assert isChanged;
		}
		art.getTagWall().connectToPerspective(facet.lowestAncestorPVp());
	}

	private void selectFacet(final boolean polarity) {
		assert facet != null;
		final Perspective parent = facet.getParent();
		assert parent != null;
		parent.deselect(Util.nonNull(facet), mouseInput.getModifiers(), false);
		assert facet != null;
		parent.selectFacet(facet, polarity);
		assert facet != null;
		facet.displayAncestors();
	}

	/**
	 * @return new ToggleFacetResult(true) if command is a toggle facet; null
	 *         otherwise.
	 */
	@SuppressWarnings("incomplete-switch")
	private @Nullable ToggleFacetResult isToggleFacetCommand() {
		ToggleFacetResult result = null;
		switch (command) {
		case SelectPerspective:
		case ProhibitPerspective:
		case Unselect:
		case SelectPerspectiveRangeControl:
		case SelectPerspectiveRangeShift:
			result = new ToggleFacetResult(true);
		}
		return result;
	}

	/**
	 * 1. updateQuery() and connectToPerspective(facet.lowestAncestorPVp());
	 *
	 * 2. setTip(errorMsg); or
	 *
	 * 3. print("NO-OP")
	 */
	void toggleFacet(final @NonNull ToggleFacetResult toggleFacetResult) {
		if (toggleFacetResult.result) {
			art.updateQuery();
			assert facet != null;
			final Perspective lowestAncestorPVp = facet.lowestAncestorPVp();
			art.getTagWall().connectToPerspective(lowestAncestorPVp);
			assert facet != null;
			final Perspective parent = facet.getParent();
			assert facet != null;
			if (facet.isRestriction(true) && parent == lowestAncestorPVp) {
				art.setArrowFocus(facet);
			} else {
				art.setArrowFocus(art.getDefaultChild(lowestAncestorPVp));
			}
		} else if (toggleFacetResult.errorMsg != null) {
			art.setTip(toggleFacetResult.errorMsg);
		} else if (!art.getTagWall().synchronizeWithQuery()) {
			System.out.println("Art.toggleFacet NO-OP: " + facet + command);
		}
	}

	private static class MouseInput {
		private final @NonNull PMELocation location;
		private final int modifiers;
		private final @Nullable PerspectiveSelection facetSelected;
		private final boolean isFacetType;
		/**
		 * Not used
		 */
		private final boolean isRankSelected;

		public MouseInput(final @NonNull PMELocation _location, final int _modifiers,
				final @Nullable PerspectiveSelection facetSelectionValue, final boolean _isFacetType,
				final boolean _isRankSelected) {
			assert nButtons(_modifiers) == 1 : "modifiers must include exactly one Mouse Button:"
					+ printModifiersEx(_modifiers);
			location = _location;
			modifiers = _modifiers;
			facetSelected = facetSelectionValue;
			isFacetType = _isFacetType;
			isRankSelected = _isRankSelected;
		}

		@NonNull
		PMELocation getLocation() {
			return location;
		}

		int getModifiers() {
			return modifiers;
		}

		@Override
		public String toString() {
			return UtilString.toString(this,
					"location=" + location + "," + Util.printModifiersEx(modifiers) + ", facetSelected=" + facetSelected
							+ ", isFacetType=" + isFacetType + ", isRankSelected=" + isRankSelected);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (facetSelected == PerspectiveSelection.REQUIRED ? 1231 : 1237);
			result = prime * result + (isFacetType ? 1231 : 1237);
			result = prime * result + location.hashCode();
			result = prime * result + modifiers;
			result = prime * result + (isRankSelected ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final MouseInput other = (MouseInput) obj;
			if (facetSelected != other.facetSelected) {
				return false;
			}
			if (isFacetType != other.isFacetType) {
				return false;
			}
			if (location != other.location) {
				return false;
			}
			if (modifiers != other.modifiers) {
				return false;
			}
			if (isRankSelected != other.isRankSelected) {
				return false;
			}
			return true;
		}

	}

	static {
		final PerspectiveSelection[] notApplicableFacetSelection = { null };
		final PerspectiveSelection[] anyFacetSelection = { PerspectiveSelection.REQUIRED,
				PerspectiveSelection.UNSELECTED, PerspectiveSelection.PROHIBITED };
		final PerspectiveSelection[] yesNoFacetSelection = { PerspectiveSelection.REQUIRED,
				PerspectiveSelection.PROHIBITED };
		final PerspectiveSelection[] maybeOrNoSelectedFacetSelection = { PerspectiveSelection.UNSELECTED,
				PerspectiveSelection.PROHIBITED };
		final PerspectiveSelection[] maybeOrYesSelectedFacetSelection = { PerspectiveSelection.UNSELECTED,
				PerspectiveSelection.REQUIRED };
		final PerspectiveSelection[] unselectedFacetSelection = { PerspectiveSelection.UNSELECTED };

		/**************** OpenBarNDefault *************/
		addOpenBarNDefaultCommand(LEFT_BUTTON_MASK | InputEvent.ALT_DOWN_MASK, YesNoMaybe.MAYBE,
				maybeOrNoSelectedFacetSelection, Command.DisplayPerspective);
		addOpenBarNDefaultCommand(LEFT_BUTTON_MASK, YesNoMaybe.NO, yesNoFacetSelection, Command.Unselect);
		addOpenBarNDefaultCommand(LEFT_BUTTON_MASK, YesNoMaybe.NO, unselectedFacetSelection, Command.SelectPerspective);
		addOpenBarNDefaultCommand(LEFT_BUTTON_MASK | EXCLUDE_ACTION, YesNoMaybe.NO, maybeOrYesSelectedFacetSelection,
				Command.ProhibitPerspective);
		addOpenBarNDefaultCommand(LEFT_BUTTON_MASK | InputEvent.SHIFT_DOWN_MASK, YesNoMaybe.NO,
				unselectedFacetSelection, Command.SelectPerspectiveRangeShift);
		addOpenBarNDefaultCommand(LEFT_BUTTON_MASK | InputEvent.CTRL_DOWN_MASK, YesNoMaybe.NO,
				maybeOrNoSelectedFacetSelection, Command.SelectPerspectiveRangeControl);
		addOpenBarNDefaultCommand(MIDDLE_BUTTON_MASK, YesNoMaybe.MAYBE, anyFacetSelection, Command.PerspectiveEditMenu);
		addCommand(PMELocation.RANK_LABEL, MIDDLE_BUTTON_MASK, YesNoMaybe.MAYBE, anyFacetSelection, YesNoMaybe.MAYBE,
				Command.PerspectiveEditMenu);
		addOpenBarNDefaultCommand(RIGHT_BUTTON_MASK, YesNoMaybe.MAYBE, anyFacetSelection, Command.SetSelectedForEdit);
		addOpenBarNDefaultCommand(RIGHT_BUTTON_MASK | InputEvent.CTRL_DOWN_MASK, YesNoMaybe.MAYBE, anyFacetSelection,
				Command.AddTag);

		// addCommand(location[s], modifiers, isFacetType, facetSelectionValues,
		// rankSelected, Command)
		/**************** TAG WALL *************/
		addCommand(PMELocation.RANK_LABEL, LEFT_BUTTON_MASK, YesNoMaybe.MAYBE, anyFacetSelection, YesNoMaybe.YES,
				Command.TogglePerspectiveList);
		addCommand(PMELocation.RANK_LABEL, LEFT_BUTTON_MASK, YesNoMaybe.MAYBE, anyFacetSelection, YesNoMaybe.NO,
				Command.SelectRank);
		addCommand(PMELocation.BAR, LEFT_BUTTON_MASK, YesNoMaybe.NO, anyFacetSelection, YesNoMaybe.NO,
				Command.SelectRank);

		/**************** LETTERS/SEARCH *************/
		addCommand(PMELocation.LETTERS, LEFT_BUTTON_MASK, YesNoMaybe.NO, notApplicableFacetSelection, YesNoMaybe.YES,
				Command.StartZoom);
		addCommand(PMELocation.ZOOM_LETTER, LEFT_BUTTON_MASK, YesNoMaybe.MAYBE, anyFacetSelection, YesNoMaybe.YES,
				Command.Zoom);
		addCommand(PMELocation.SEARCH_TEXT, LEFT_BUTTON_MASK, YesNoMaybe.NO, notApplicableFacetSelection, YesNoMaybe.NO,
				Command.RemoveTextSearch);

		/**************** ITEMS *************/
		addCommand(PMELocation.GRID_ITEM, LEFT_BUTTON_MASK, YesNoMaybe.NO, notApplicableFacetSelection, YesNoMaybe.NO,
				Command.SelectItem);
		addCommand(PMELocation.GRID_ITEM, LEFT_BUTTON_MASK | Util.CLICK_THUMB_INTENTIONALLY_MODIFIER, YesNoMaybe.NO,
				notApplicableFacetSelection, YesNoMaybe.NO, Command.SelectItem);
		addCommand(PMELocation.SELECTED_ITEM, LEFT_BUTTON_MASK, YesNoMaybe.NO, notApplicableFacetSelection,
				YesNoMaybe.NO, Command.ShowItemInBrowser);
		final @NonNull PMELocation[] itemLocations = { PMELocation.GRID_ITEM, PMELocation.SELECTED_ITEM };
		@SuppressWarnings("null")
		final @NonNull List<PMELocation> itemLocationsList = Arrays.asList(itemLocations);
		addCommand(itemLocationsList, MIDDLE_BUTTON_MASK, YesNoMaybe.NO, notApplicableFacetSelection, YesNoMaybe.NO,
				Command.ItemEditMenu);
		addCommand(itemLocationsList, RIGHT_BUTTON_MASK | InputEvent.CTRL_DOWN_MASK, YesNoMaybe.NO,
				notApplicableFacetSelection, YesNoMaybe.NO, Command.AddTag);

		// for (final Entry<Input, Command> entry : commandMap.entrySet()) {
		// System.out
		// .println(entry.getKey() + "\n " + entry.getValue() + "\n");
		// }
	}

	private static void addOpenBarNDefaultCommand(final int modifiers, final @NonNull YesNoMaybe isFacetType,
			final @NonNull PerspectiveSelection[] facetSelectionValues, final @NonNull Command command) {
		addCommand(PMELocation.BAR, modifiers, isFacetType, facetSelectionValues, YesNoMaybe.YES, command);
		addCommand(PMELocation.DEFAULT, modifiers, isFacetType, facetSelectionValues, YesNoMaybe.MAYBE, command);
	}

	private static void addCommand(final @NonNull Collection<PMELocation> locations, final int modifiers,
			final @NonNull YesNoMaybe isFacetType, final @NonNull PerspectiveSelection[] facetSelectionValues,
			final @NonNull YesNoMaybe rankSelected, final @NonNull Command command) {
		for (final PMELocation location : locations) {
			assert location != null;
			addCommand(location, modifiers, isFacetType, facetSelectionValues, rankSelected, command);
		}
	}

	private static void addCommand(final @NonNull PMELocation location, final int modifiers,
			final @NonNull YesNoMaybe isFacetType, final @NonNull PerspectiveSelection[] facetSelectionValues,
			final @NonNull YesNoMaybe rankSelected, final @NonNull Command command) {
		for (final boolean facetTypeValue : YesNoMaybe.compatibleBooleanValues(isFacetType)) {
			for (final PerspectiveSelection facetSelectionValue : facetSelectionValues) {
				for (final boolean rankSelectedValue : YesNoMaybe.compatibleBooleanValues(rankSelected)) {
					final MouseInput input = new MouseInput(location, modifiers, facetSelectionValue, facetTypeValue,
							rankSelectedValue);
					UtilArray.putNew(COMMAND_MAP, input, command, "");
				}
			}
		}
	}

	public static final @NonNull MarkupElement NO_MATCHES = MarkupStringElement
			.getElement("There would be no matches if you ");

	private static final @NonNull MarkupStringElement TO = MarkupStringElement.getElement(" to ");
	private static final @NonNull MarkupStringElement ADD_TAG = MarkupStringElement.getElement("Add Tag ");
	private static final @NonNull MarkupStringElement SHOW_EDIT_MENU = MarkupStringElement
			.getElement("Show Edit Menu for ");
	private static final @NonNull MarkupStringElement SHOW_EDIT_TAG_MENU = MarkupStringElement
			.getElement("Show Edit Tag Menu for ");
	private static final @NonNull MarkupStringElement SET_DEFAULT_TAG = MarkupStringElement
			.getElement("Set default tag to ");
	private static final @NonNull MarkupStringElement ZOOM_PAN = MarkupStringElement
			.getElement("Drag mouse up/down to zoom; right/left to pan");
	private static final @NonNull MarkupStringElement EQUALS = MarkupStringElement.getElement(" = ");
	private static final @NonNull MarkupStringElement NOT_EQUALS_FILTER_TYPE = MarkupStringElement.getElement(" ≠ ");
	private static final @NonNull MarkupStringElement DASH = MarkupStringElement.getElement(" - ");
	private static final @NonNull MarkupStringElement TO_FILTER_ON = MarkupStringElement.getElement(" to filter on ");
	private static final @NonNull MarkupStringElement ADD = MarkupStringElement.getElement("Add ");
	private static final @NonNull MarkupStringElement ADD_FILTER = MarkupStringElement.getElement("Add Filter ");
	private static final @NonNull MarkupStringElement REMOVE_RESTRICTION = MarkupStringElement
			.getElement("Remove restriction ");
	private static final @NonNull MarkupStringElement IS_DISPLAYED = MarkupStringElement
			.getElement(" is displayed on the Tag Wall");
	private static final @NonNull MarkupStringElement ENSURE_THAT = MarkupStringElement.getElement("Ensure that ");
	private static final @NonNull MarkupStringElement HIDE_TAGS = MarkupStringElement
			.getElement("Hide the list of tags");
	private static final @NonNull MarkupStringElement TAGS = MarkupStringElement.getElement(" tags");
	private static final @NonNull MarkupStringElement LIST_ALL = MarkupStringElement.getElement("List all ");
	private static final @NonNull MarkupStringElement OPEN_CATEGORY = MarkupStringElement.getElement("Open category ");
	private static final @NonNull MarkupStringElement IS_REQUIRED = MarkupStringElement.getElement(" is required.");

	@Override
	public String toString() {
		return UtilString.toString(this, mouseInput + "\n command=" + command + "\n rank=" + rank + "\n facet="
				+ (facet != null ? facet.path() : facet));
	}

	private @NonNull Query query() {
		return art.getQuery();
	}

	/**
	 * Called by BungeeAPText
	 */
	public static boolean isUnderline(final @NonNull Bungee art, final @NonNull BungeeAPText apText,
			final @NonNull PMELocation location) {
		Perspective perspective = null;
		RedrawCallback callback = null;
		if (apText instanceof PerspectiveMarkupAPText) {
			final PerspectiveMarkupAPText perspectiveMarkupAPText = (PerspectiveMarkupAPText) apText;
			perspective = perspectiveMarkupAPText.getFacet();
			callback = perspectiveMarkupAPText;
		}
		final Rank rank = (Rank) findNodeType(apText, Rank.class);
		return isUnderline(art, rank, perspective, location, apText.getText(), callback);

		// final UserAction action = getAction(art, LEFT_BUTTON_MASK,
		// perspective, rank, location, apText.getText(), null);
		//
		// // too slow
		// // final UserAction action = getAction(apText, art,
		// LEFT_BUTTON_MASK);
		// return (action == null) ? false : action.mouseDoc(false, callback) !=
		// null;
	}

	/**
	 * Called by PerspectiveMarkupAPText()
	 */
	public static boolean isLabeledLabelUnderline(final @NonNull PerspectiveViz _perspectiveViz,
			final @NonNull Perspective _facet, final @Nullable RedrawCallback callback) {
		return isUnderline(_perspectiveViz.art(), _perspectiveViz.getRank(), _facet, PMELocation.DEFAULT, null,
				callback);
	}

	/**
	 * Called by TopTagsViz, TopTagsPerspectiveMarkupAPText, FacetTreeViz, and
	 * PerspectiveList.
	 */
	public static boolean isDefaultLocationUnderline(final @NonNull Bungee art, final @Nullable Perspective perspective,
			final @Nullable RedrawCallback callback) {
		return isUnderline(art, null, perspective, PMELocation.DEFAULT, null, callback);
	}

	/**
	 * ISN'T THIS BOGUS? action.mouseDoc sometimes returns non-null even for
	 * invalid actions!!!!
	 */
	public static boolean isUnderline(final @NonNull Bungee art, @Nullable final Rank rank,
			final @Nullable Perspective perspective, final @NonNull PMELocation location, @Nullable final String text,
			final @Nullable RedrawCallback callback) {
		final UserAction action = getAction(art, LEFT_BUTTON_MASK, perspective, rank, location, text, null);
		return (action == null) ? false : action.mouseDoc(false, callback) != null;
	}

}
