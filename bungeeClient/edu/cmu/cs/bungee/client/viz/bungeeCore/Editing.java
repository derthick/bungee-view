package edu.cmu.cs.bungee.client.viz.bungeeCore;

import static edu.cmu.cs.bungee.javaExtensions.UtilString.isNotParsable;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.EditableQuery;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.grid.RangeEnsurer;
import edu.cmu.cs.bungee.client.viz.menusNbuttons.BungeeMenu;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.AbstractMenuItem;
import edu.cmu.cs.bungee.piccoloUtils.gui.Menu;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;
import uk.org.bobulous.java.intervals.GenericInterval;

/**
 * The entry points are itemMiddleMenu and facetMiddleMenu.
 *
 * Each command is a subclass of Edit
 */
class Editing implements Serializable {

	private static final String DATE_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss";
	static final DateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING);

	/**
	 * This is the "default" facet, in case the command needs more than the
	 * facet clicked on. Set only by setSelectedForEdit()
	 */
	Perspective defaultArgumentFacet;

	/**
	 * Can be either the item- or facet- MiddleMenu
	 */
	Menu editMenu;

	private final @NonNull Bungee art;

	Editing(final @NonNull Bungee _art) {
		super();
		art = _art;
	}

	@NonNull
	Bungee art() {
		return art;
	}

	@NonNull
	EditableQuery query() {
		return (EditableQuery) art().getQuery();
	}

	String getGenericObjectLabel(final boolean isPlural, final boolean capitalize) {
		String result = query().getGenericObjectLabel(isPlural);
		if (capitalize) {
			result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
		}
		return result;
	}

	void setTip(final String message) {
		art().setTip(message);
	}

	Item getSelectedItem() {
		return art().getSelectedItem();
	}

	private void addChild(final PNode node) {
		// System.out.println("Bungee.addChild " + node);
		assert node != null;
		final PLayer layer = art().getCanvas().getLayer();
		layer.addChild(node);
	}

	/**
	 * removeEditMenu()
	 */
	public void doHideTransients() {
		removeEditMenu();
	}

	void removeEditMenu() {
		if (editMenu != null) {
			editMenu.removeFromParent();
			editMenu = null;
		}
	}

	// Show user parsed date as verification
	static synchronized String prettyPrintDate(final String date1) {
		String result = date1;
		try {
			result = DATE_FORMAT.parse(date1).toString();
		} catch (final ParseException e) {
			// return date1 if error
		}
		return result;
	}

	void showItemMiddleMenu(final Item item) {
		assert item != null;
		editMenu = getEditMenu();
		addEditButton(new RotateCommand(item, 90));
		addEditButton(new RotateCommand(item, 180));
		addEditButton(new RotateCommand(item, 270));
		addEditButton(new AddTagCommand(item, defaultArgumentFacet,
				"Add tag " + defaultArgumentFacet + " (control+right click)"));
		editMenu.setText("Editing Commands");
		editMenu.pick();
	}

	void showFacetMiddleMenu(final @NonNull Perspective facet) {
		editMenu = getEditMenu();
		final String editorText = art().getHeader().getTrimmedEditorText();
		addEditButton(new SetSelectedForEditCommand(facet));
		addEditButton(new AddTagCommand(getSelectedItem(), facet,
				"Add " + facet + " to Selected " + getGenericObjectLabel(false, true) + " (control+right click)"));
		addEditButton(new AddToResultSetCommand(facet));
		addEditButton(new AddChildTagCommand(editorText, facet));
		addEditButton(new AddNewTagTypeCommand(editorText));
		// addEditButton("Delete this tag");
		addEditButton(new RemoveFromSelectedResultCommand(facet));
		addEditButton(new RemoveFromResultSetCommand(facet));
		addEditButton(new ReparentCommand(facet));
		addEditButton(new RenameTagCommand(editorText, facet));
		addEditButton(new WritebackCommand());
		addEditButton(new RevertCommand(editorText));
		editMenu.setText("Editing Commands");
		editMenu.pick();
	}

	private Menu getEditMenu() {
		removeEditMenu();
		final Menu result = new BungeeMenu(BungeeConstants.EDITING_MENU_BG_COLOR, BungeeConstants.TEXT_FG_COLOR, art());
		result.setOffset(0.0, art.getTagWall().getYOffset());
		addChild(result);
		return result;
	}

	private void addEditButton(final AbstractMenuItem command) {
		final int n = editMenu.nButtons() + 1;
		command.setLabel(Character.forDigit(n, n + 1) + ". " + command.getLabel());
		editMenu.addButton(command);
	}

	void setSelectedForEdit(final Perspective facet) {
		defaultArgumentFacet = facet;
		setTip(facet + " is now the default Tag.");
	}

	public boolean addTag(final Item item) {
		return addTag(defaultArgumentFacet, item, "Add tag " + defaultArgumentFacet);
	}

	public boolean addTag(final Perspective facet) {
		return addTag(facet, getSelectedItem(), "Add tag " + facet);
	}

	private boolean addTag(final Perspective facet, final Item item, final @NonNull String _label) {
		boolean result = false;
		assert item != null;
		final AddTagCommand addTagCommand = new AddTagCommand(item, facet, _label);
		if (addTagCommand.isEnabled()) {
			result = Boolean.valueOf(addTagCommand.doCommand());
			setTip(item + " is now tagged " + facet);
		} else {
			setTip("Command Unavailable: " + addTagCommand.getMouseDoc());
		}
		return result;
	}

	public void setItemDescription(final @NonNull Item currentItem, final @NonNull String description) {
		query().setItemDescription(currentItem, description);
		decacheCurrentItem();
	}

	void decacheCurrentItem() {
		art().decacheItems();
	}

	private abstract class EditCommand extends AbstractMenuItem {

		/**
		 * This is the facet we clicked on.
		 */
		protected final Perspective editMenuPerspective;

		EditCommand(final @NonNull String _label) {
			super(_label);
			editMenuPerspective = null;
		}

		EditCommand(final @NonNull String _label, final @NonNull String _disabledMouseDoc) {
			super(_label, _label, _disabledMouseDoc);
			editMenuPerspective = null;
		}

		EditCommand(final @Nullable Perspective _editMenuPerspective, final @NonNull String _label) {
			super(_label);
			editMenuPerspective = _editMenuPerspective;
		}

		EditCommand(final @Nullable Perspective _editMenuPerspective, final @NonNull String _label,
				final @NonNull String _disabledMouseDoc) {
			super(_label, _label, _disabledMouseDoc);
			editMenuPerspective = _editMenuPerspective;
		}

		/**
		 * @return displayed perspectives to redisplay
		 */
		abstract @Nullable Collection<Perspective> doEditCommand();

		@Override
		public String doCommand() {
			System.out.println("doCommand " + editMenuPerspective + " " + this);
			super.doCommand();

			boolean result = false;
			try {
				art().handleCursor(true);
				art().setTip("Executing " + getLabel());
				final Collection<Perspective> updated = doEditCommand();
				result = updated != null && updated.size() > 0;
				if (result) {
					// System.out.println("Reverting PerspectiveViz's for "
					// + UtilString.valueOfDeep(updated));
					final SortedSet<Perspective> unchanged = new TreeSet<>(query().displayedPerspectives());
					unchanged.removeAll(updated);

					// This will retain only the unchanged perspectives,
					// deleting the changed ones. Then updateAllData will add
					// them back again.
					art().getTagWall().synchronizePerspectives(unchanged);
					art().updateQuery();
				}
				removeEditMenu();
				art().setTip("Executed " + getLabel());
			} catch (final IllegalArgumentException e) {
				setTip(e.getMessage());
			} finally {
				art().handleCursor(false);
			}
			return Boolean.toString(result);
		}

	}

	private class RotateCommand extends EditCommand {
		final Item item;
		final int degrees;

		RotateCommand(final Item _item, final int _degrees) {
			super("Rotate " + _degrees + " degrees clockwise: ");
			item = _item;
			degrees = _degrees;
		}

		@Override
		public Collection<Perspective> doEditCommand() {
			query().rotate(item, degrees);
			decacheCurrentItem();
			return null;
		}

	}

	private class SetSelectedForEditCommand extends EditCommand {

		SetSelectedForEditCommand(final Perspective facet) {
			super(facet, "Set default tag to " + facet + " (right mouse button)");
		}

		@Override
		Collection<Perspective> doEditCommand() {
			setSelectedForEdit(editMenuPerspective);
			return null;
		}

	}

	String addOrRemoveTagCommandDisabledMessage(final Perspective facet, final Item item, final boolean isAdding) {
		String result = null;
		if (facet == null) {
			result = "You must set a default Tag before you can " + (isAdding ? "add it to" : "remove it from") + " an "
					+ getGenericObjectLabel(false, false);
		} else if (item == null) {
			result = "There is no selected " + getGenericObjectLabel(false, false) + " to tag";
		} else if (isAdding == item.hasFacet(facet)) {
			result = item.getDescription(query()).replace('\n', ' ')
					+ (isAdding ? " is already tagged " : " is not tagged ") + facet;
		}
		return result;
	}

	String addOrRemoveAllResultsCommandDisabledMessage(final Perspective facet, final boolean isAdding) {
		String result = null;
		if (facet == null) {
			result = "You must set a default Tag before you can " + (isAdding ? "add it to" : "remove it from") + " "
					+ getGenericObjectLabel(true, false);
		} else if (art().getQuery().getOnCount() == 0) {
			result = "There are no " + getGenericObjectLabel(true, false) + " to tag";
		} else {
			assert query().isQueryValid();
			final RangeEnsurer rangeEnsurer = art().getGrid().rangeEnsurer;
			rangeEnsurer.cacheOffsets(GenericInterval.getOpenUpperGenericInterval(0, query().getOnCount()));
			boolean isOK = false;
			for (int i = 0; i < query().getOnCount() && !isOK; i++) {
				final Item item = rangeEnsurer.getItem(i);
				assert item != null;
				final boolean isTagged = item.hasFacet(facet);
				isOK = isTagged != isAdding;
			}
			if (!isOK && isAdding) {
				result = "All results are already tagged " + facet;
			} else if (!isOK) {
				result = "No results are tagged " + facet;
			}
		}
		return result;
	}

	private class AddTagCommand extends EditCommand {

		private final Item item;

		AddTagCommand(final Item _item, final Perspective facet, final @NonNull String _label) {
			super(facet, _label);
			item = _item;
		}

		@Override
		public boolean isEnabled() {
			disabledMouseDoc = addOrRemoveTagCommandDisabledMessage(editMenuPerspective, getItem(), true);
			return disabledMouseDoc == null;
		}

		private Item getItem() {
			return item != null ? item : getSelectedItem();
		}

		@Override
		Collection<Perspective> doEditCommand() {
			final Collection<Perspective> updated = query().addItemFacet(editMenuPerspective, item);
			if (item == getSelectedItem()) {
				decacheCurrentItem();
			}
			return updated;
		}

	}

	private class RemoveFromSelectedResultCommand extends EditCommand {
		RemoveFromSelectedResultCommand(final Perspective facet) {
			super(facet, "Remove " + facet + " from Selected " + getGenericObjectLabel(false, true));
		}

		@Override
		public boolean isEnabled() {
			disabledMouseDoc = addOrRemoveTagCommandDisabledMessage(editMenuPerspective, getSelectedItem(), false);
			return disabledMouseDoc == null;
		}

		@Override
		Collection<Perspective> doEditCommand() {
			final Collection<Perspective> updated = query().removeItemFacet(editMenuPerspective, getSelectedItem());
			decacheCurrentItem();
			return updated;
		}

	}

	private class AddToResultSetCommand extends EditCommand {

		AddToResultSetCommand(final Perspective facet) {
			super(facet, "Add " + facet + " to all Matches");
		}

		@Override
		public boolean isEnabled() {
			disabledMouseDoc = addOrRemoveAllResultsCommandDisabledMessage(editMenuPerspective, true);
			return disabledMouseDoc == null;
		}

		@Override
		Collection<Perspective> doEditCommand() {
			final Collection<Perspective> updated = query().addItemsFacet(editMenuPerspective);
			art().decacheItems();
			return updated;
		}

	}

	private class RemoveFromResultSetCommand extends EditCommand {
		RemoveFromResultSetCommand(final Perspective facet) {
			super(facet, "Remove " + facet + " from all Matches");
		}

		@Override
		public boolean isEnabled() {
			disabledMouseDoc = addOrRemoveAllResultsCommandDisabledMessage(editMenuPerspective, false);
			return disabledMouseDoc == null;
		}

		@Override
		Collection<Perspective> doEditCommand() {
			final Collection<Perspective> updated = query().removeItemsFacet(editMenuPerspective);
			art().decacheItems();
			return updated;
		}

	}

	private class AddChildTagCommand extends EditCommand {

		private final @NonNull String name;

		AddChildTagCommand(final @NonNull String _name, final Perspective facet) {
			super(facet, "Add child tag " + _name + " to " + facet,
					"There is no name for the new Tag in the Text Search box");
			name = _name;
		}

		@Override
		public boolean isEnabled() {
			return UtilString.isNonEmptyString(name);
		}

		@Override
		Collection<Perspective> doEditCommand() {
			return query().addChildFacet(editMenuPerspective, name);
		}

	}

	private class ReparentCommand extends EditCommand {
		ReparentCommand(final Perspective facet) {
			super(facet, "Reparent " + (defaultArgumentFacet != null ? defaultArgumentFacet : "?") + " to " + facet);
		}

		@Override
		public boolean isEnabled() {
			boolean result = false;
			if (defaultArgumentFacet == null) {
				disabledMouseDoc = "You must set a default Tag before you can reparent it";
			} else if (defaultArgumentFacet == editMenuPerspective) {
				disabledMouseDoc = "You can't make a Tag its own parent";
			} else if (defaultArgumentFacet == editMenuPerspective.getParent()) {
				disabledMouseDoc = "The new parent is the same as the old parent";
			} else {
				result = true;
			}
			return result;
		}

		@Override
		Collection<Perspective> doEditCommand() {
			return query().reparent(editMenuPerspective, defaultArgumentFacet);
		}

	}

	private class RenameTagCommand extends EditCommand {
		private final @NonNull String name;

		RenameTagCommand(final @NonNull String _name, final Perspective facet) {
			super(facet, "Rename " + facet + " to " + _name);
			name = _name;
		}

		@Override
		public boolean isEnabled() {
			boolean result = false;
			if (!UtilString.isNonEmptyString(name)) {
				disabledMouseDoc = "There is no new name for the Tag in the Text Search box";
			} else if (name.equals(editMenuPerspective.getName())) {
				disabledMouseDoc = "The new name is the same as the old name";
			} else {
				result = true;
			}
			return result;
		}

		@Override
		Collection<Perspective> doEditCommand() {
			final String newName = name;
			query().rename(editMenuPerspective, newName);
			editMenuPerspective.setName(newName);
			art().clearTextCaches();
			return null;
		}

	}

	private class AddNewTagTypeCommand extends EditCommand {
		private final @NonNull String name;

		AddNewTagTypeCommand(final @NonNull String _name) {
			super("Add new tag category " + _name, "There is no name for the new Tag Category in the Text Search box");
			name = _name;
		}

		@Override
		public boolean isEnabled() {
			return UtilString.isNonEmptyString(name);
		}

		@Override
		Collection<Perspective> doEditCommand() {
			return query().addChildFacet(null, name);
		}

	}

	private class WritebackCommand extends EditCommand {
		WritebackCommand() {
			super("Save changes and exit");
		}

		@Override
		public Collection<Perspective> doEditCommand() {
			art().printUserAction(ReplayLocation.WRITEBACK, 0, 0);
			query().writeback();
			art().maybeSetDatabase(null);
			art().dispose();
			return null;
		}

	}

	public void revert(final @NonNull String revertDate) {
		new RevertCommand(revertDate).doCommand();
	}

	private class RevertCommand extends EditCommand {
		private final @NonNull String date;

		RevertCommand(final @NonNull String _date) {
			super("Revert database to " + prettyPrintDate(_date));
			date = _date;
		}

		@SuppressWarnings("null")
		@Override
		public boolean isEnabled() {
			final ParseException parseException = isNotParsable(date, DATE_FORMAT);
			final boolean result = parseException == null;
			if (!result) {
				disabledMouseDoc = date + " is not a date of the form " + DATE_FORMAT_STRING + ": "
						+ parseException.getMessage();
			}
			return result;
		}

		@Override
		public Collection<Perspective> doEditCommand() {
			query().revert(date);
			art().dispose();
			return null;
		}

	}

}
