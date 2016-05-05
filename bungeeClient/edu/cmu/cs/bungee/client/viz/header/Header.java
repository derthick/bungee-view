/*

 Created on Mar 4, 2005

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
 mad@cs.cmu.edu,
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.client.viz.header;

import static edu.cmu.cs.bungee.javaExtensions.Util.assertMouseProcess;

import java.awt.Component;
import java.awt.Font;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.BungeeFrame;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.menusNbuttons.BungeeMenu;
import edu.cmu.cs.bungee.client.viz.menusNbuttons.BungeeTextButton;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.AbstractMenuItem;
import edu.cmu.cs.bungee.piccoloUtils.gui.Alignment;
import edu.cmu.cs.bungee.piccoloUtils.gui.Boundary;
import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryWithLabels;
import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryWithLabels.LabelNoffset;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.Menu;

public final class Header extends BungeeFrame {

	private final @NonNull ClearButton clear;
	private final @NonNull BookmarkButton bookmark;
	private final @NonNull RestrictButton restrict;

	/**
	 * databaseLabel is used instead of databaseMenu when there is only one
	 * database. Only one should be non-null;
	 */
	private final @Nullable Menu databaseMenu;
	private final @Nullable APText databaseLabel;
	private final @NonNull Menu helpMenu;
	private @Nullable SortLabelNMenu sortLabelNMenu;
	private final @NonNull Menu modeMenu;
	private final @NonNull APText countLabel;
	private @NonNull ColorKey colorKey;
	private final @NonNull HeaderQueryDescription summaryText;
	private final @NonNull TextSearch textSearch;

	/**
	 * Now that we're a BungeeFrame, we can't paint ourselves, so use
	 * background.
	 */
	private final @NonNull LazyPNode background = new LazyPNode();

	/**
	 * @param allDBdescriptions
	 *            [[database, prettyName], ...]
	 */
	public Header(final @NonNull Bungee _art, final @NonNull String[][] allDBdescriptions,
			final @NonNull String selectedDBname) {
		super(_art, BungeeConstants.HEADER_FG_COLOR, "Bungee View", true);
		background.setPaint(BungeeConstants.HEADER_BG_COLOR);

		helpMenu = getHelpMenu();
		modeMenu = getModeMenu();
		resetModeMenu();
		clear = new ClearButton();
		bookmark = new BookmarkButton();
		restrict = new RestrictButton();
		textSearch = new TextSearch(art);

		final String selectedDBdescription = allDBdescriptions[0][1];
		assert selectedDBdescription != null;
		if (allDBdescriptions.length > 1) {
			databaseLabel = null;
			databaseMenu = getDatabaseMenu(allDBdescriptions, selectedDBname, selectedDBdescription);
			addChild(databaseMenu);
		} else {
			databaseMenu = null;
			final APText databaseLabel2 = art.oneLineLabel();
			databaseLabel2.setTextPaint(BungeeConstants.HEADER_FG_COLOR);
			databaseLabel2.maybeSetText(selectedDBdescription);
			addChild(databaseLabel2);
			databaseLabel = databaseLabel2;
		}

		summaryText = new HeaderQueryDescription(art);
		countLabel = art.oneLineLabel();
		countLabel.setTextPaint(BungeeConstants.HEADER_FG_COLOR);
		// make sure these two have globalBounds, for aligning
		countLabel.maybeSetText(" ");
		summaryText.setBounds(0.0, 0.0, 1.0, 1.0);

		addChild(background);
		addChild(helpMenu);
		background.moveToBack();
		addChild(modeMenu);
		addChild(clear);
		addChild(bookmark);
		addChild(restrict);
		addChild(textSearch);
		addChild(countLabel);
		addChild(summaryText);
		colorKey = new ColorKey(_art);
		addChild(colorKey);
	}

	public void init() {
		initted();
		if (boundary != null) {
			boundary.mouseDoc = "Start dragging to change text size.";
		}
		queryValidRedraw();
	}

	private @NonNull BungeeMenu getDatabaseMenu(final @NonNull String[][] allDBdescriptions,
			final @NonNull String selectedDBname, @NonNull String selectedDBdescription) {
		final BungeeMenu _databaseMenu = new BungeeMenu(BungeeConstants.HEADER_BG_COLOR,
				BungeeConstants.HEADER_FG_COLOR, art);
		_databaseMenu.setLabelJustification(Component.LEFT_ALIGNMENT);
		_databaseMenu.mouseDoc = "Choose among Collections";
		for (final String[] allDBdescription : allDBdescriptions) {
			final String dbName = allDBdescription[0];
			final String dbDesc = allDBdescription[1];
			assert dbDesc != null && dbName != null;
			_databaseMenu.addButton(new SetDatabaseCommand(dbDesc, dbName));
			if (dbName.equalsIgnoreCase(selectedDBname)) {
				selectedDBdescription = dbDesc;
			}
		}
		_databaseMenu.setText(selectedDBdescription);
		return _databaseMenu;
	}

	private @NonNull BungeeMenu getModeMenu() {
		final BungeeMenu _modeMenu = new BungeeMenu(BungeeConstants.HEADER_BG_COLOR, BungeeConstants.HEADER_FG_COLOR,
				art);
		_modeMenu.mouseDoc = "Set Preferences";
		_modeMenu.addButton(new BeginnerModeCommand());
		_modeMenu.addButton(new ExpertModeCommand());
		_modeMenu.addButton(new CustomModeCommand());
		return _modeMenu;
	}

	protected void resetModeMenu() {
		if (art.isExpertMode()) {
			modeMenu.setText("Expert Mode");
		} else if (art.isBeginnerMode()) {
			modeMenu.setText("Beginner Mode");
		} else {
			modeMenu.setText("Custom");
		}
	}

	private @NonNull BungeeMenu getHelpMenu() {
		final BungeeMenu _helpMenu = new BungeeMenu(BungeeConstants.HEADER_BG_COLOR, BungeeConstants.HEADER_FG_COLOR,
				art);
		try {
			_helpMenu.mouseDoc = "Show Help Topics";
			_helpMenu.setLabelJustification(Component.RIGHT_ALIGNMENT);
			_helpMenu.addButton(
					new HelpMenuItem("Abo.html?" + URLEncoder.encode(art.getBugInfo(), "UTF-8"), "About Bungee View"));
			_helpMenu.addButton(new HelpMenuItem("<about collection>", "About This Collection"));
			_helpMenu.addButton(new HelpMenuItem("index.html#mining", "What do the colored bars mean?"));
			_helpMenu.addButton(new HelpMenuItem("Sea.html", "Search Syntax"));
			_helpMenu.addButton(new HelpMenuItem("Tip.html", "Tips and Tricks"));
			if (query.isEditable()) {
				_helpMenu.addButton(new HelpMenuItem("Edi.html", "Editing the database"));
			}
			_helpMenu.setText("Help");
		} catch (final UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		Alignment.ensureGlobalBounds(_helpMenu);
		return _helpMenu;
	}

	@Override
	public void setFeatures() {
		super.setFeatures(); // moved 3/19/16
		// System.out.println("Header.setFeatures isInitted()=" + isInitted());
		// if (isInitted()) {
		if (art.getShowSortMenu()) {
			if (sortLabelNMenu == null) {
				sortLabelNMenu = new SortLabelNMenu(art);
				setSortLabelNMenuVisible(art.getGrid().getVisible());
				addChild(sortLabelNMenu);
				validateInternal();
			}
		} else if (sortLabelNMenu != null) {
			sortLabelNMenu.removeFromParent();
			sortLabelNMenu = null;
			art.reorder(SortLabelNMenu.ORDER_BY_RANDOM);
		}
		resetModeMenu();
		// }
		// super.setFeatures();
	}

	@Override
	protected boolean setFont(final @NonNull Font font) {
		assertMouseProcess();
		final boolean result = super.setFont(font);
		if (result) {
			if (sortLabelNMenu != null) {
				sortLabelNMenu.setFont(font);
			}
			modeMenu.setFont(font);
			helpMenu.setFont(font);
			countLabel.setFont(font);
			clear.setFont(font);
			restrict.setFont(font);
			colorKey.setFont(font);
			summaryText.setFont(font);
			bookmark.setFont(font);
			if (databaseMenu != null) {
				databaseMenu.setFont(font);
			}
			if (databaseLabel != null) {
				databaseLabel.setFont(font);
			}
			textSearch.setFont(font);
			validateInternal();
		}
		return result;
	}

	@Override
	public void validateInternal() {
		super.validateInternal();
		final double buttonMargin = art.buttonMargin();

		background.setBounds(0.0, 0.0, getWidth(), getHeight());
		assert background != null;
		Alignment.align(helpMenu, Alignment.TOP_RIGHT, background, Alignment.TOP_RIGHT, -buttonMargin, buttonMargin);

		if (sortLabelNMenu != null) {
			Alignment.align(sortLabelNMenu, Alignment.TOP_RIGHT, helpMenu, Alignment.BOTTOM_RIGHT, 0.0, buttonMargin);
		}

		label.setOffset(buttonMargin, 0.0);
		Alignment.align(modeMenu, Alignment.TOP_LEFT, label, Alignment.BOTTOM_LEFT);
		Alignment.align(countLabel, Alignment.TOP_LEFT, label, Alignment.TOP_RIGHT, 3 * buttonMargin, buttonMargin);
		Alignment.align(summaryText, Alignment.TOP_LEFT, countLabel, Alignment.BOTTOM_LEFT);

		validateColorKey();
		// validateColorKey aligns colorKey horizontally
		summaryText.validate((sortLabelNMenu != null ? sortLabelNMenu : colorKey).getXOffset()
				- summaryText.getXOffset() - art.lineH());

		Alignment.align(clear, Alignment.TOP_LEFT, summaryText, Alignment.BOTTOM_LEFT, 0.0, buttonMargin);
		Alignment.align(restrict, Alignment.TOP_LEFT, clear, Alignment.TOP_RIGHT, buttonMargin, 0.0);
		Alignment.align(bookmark, Alignment.TOP_LEFT, restrict, Alignment.TOP_RIGHT, buttonMargin, 0.0);

		textSearch.setWidth(colorKey.getXOffset() - bookmark.getMaxX() - 4.0 * buttonMargin);
		textSearch.positionLabels();
		Alignment.align(textSearch, Alignment.TOP_LEFT, bookmark, Alignment.TOP_RIGHT, 2 * buttonMargin, 0.0);
		assert colorKey != null;
		Alignment.align(colorKey, Alignment.BOTTOM, bookmark, Alignment.BOTTOM);

		// This is always violated when text size increases; then it will be
		// called again with the new minHeight and all will be copasetic.
		assert colorKey.getMaxY() <= getHeight()
				|| getHeight() < minHeight() : "Header.validateInternal colorKey.getMaxY() > h: h=" + getHeight()
						+ " colorKey.getMaxY=" + colorKey.getMaxY() + " clear.getMaxY=" + clear.getMaxY()
						+ " minHeight=" + minHeight() + " lineH=" + art.lineH() + "\n getGlobalBounds="
						+ getGlobalBounds() + "\n colorKey.getGlobalBounds=" + colorKey.getGlobalBounds();

		placeDatabaseWidget();
		if (boundary != null) {
			boundary.setVisualOffset(art.getMouseDoc().getHeight());
		}
	}

	private void placeDatabaseWidget() {
		final double x = countLabel.getMaxX() + 10.0;
		final double y = countLabel.getYOffset();
		if (databaseLabel != null) {
			databaseLabel.setOffset(x, y);
		}
		if (databaseMenu != null) {
			final Menu databaseMenu2 = databaseMenu;
			databaseMenu2.setOffset(x, y);
			databaseMenu2.moveInBackOf(helpMenu);
		}
	}

	private void validateColorKey() {
		if (colorKey.nButtons() != art.nColors()) {
			colorKey.removeFromParent();
			colorKey = new ColorKey(art);
			addChild(colorKey);
		}
		colorKey.setVisible(query.isIntensionallyRestricted());
		Alignment.align(colorKey, Alignment.TOP_RIGHT, helpMenu, Alignment.BOTTOM_RIGHT);
	}

	@Override
	protected @NonNull Boundary getBoundary() {
		final Boundary result = new BoundaryWithLabels(this, true, getFont());
		result.setVisualOffset(art.getMouseDoc().getHeight());
		return result;
	}

	@Override
	public double minDragLimit(final @NonNull Boundary boundary1) {
		if (nLabels() <= 1) {
			return Integer.MAX_VALUE;
		}
		final double percentLower = (art.getFontSize() - art.minLegibleFontSize()) / (double) (nLabels() - 1);
		final double result = Math.rint(boundary1.constrainedLogicalDragPosition() - percentLower * dragRange());
		assert !Double.isNaN(result) : " percentLower=" + percentLower + " constrainedLogicalDragPosition="
				+ boundary1.constrainedLogicalDragPosition() + " dragRange=" + dragRange();
		return result;
	}

	@Override
	public double maxDragLimit(final @NonNull Boundary boundary1) {
		if (nLabels() <= 1) {
			return Integer.MIN_VALUE;
		}
		return minDragLimit(boundary1) + dragRange();
	}

	@Override
	public void boundaryDragged(final @NonNull Boundary boundary1) {
		final int newFontSize = art.minLegibleFontSize()
				+ UtilMath.roundToInt(boundary1.constrainedDragPercentage() * (nLabels() - 1));
		final int oldFontSize = art.getFontSize();
		if (art.setFontSize(newFontSize)) {
			final BoundaryWithLabels boundaryWithLabels = (BoundaryWithLabels) boundary1;
			final APText oldLabel = boundaryWithLabels.getLabel(Integer.toString(oldFontSize));
			assert oldLabel != null : oldFontSize;
			oldLabel.setTextPaint(UtilColor.WHITE);
			final APText newLabel = boundaryWithLabels.getLabel(Integer.toString(newFontSize));
			assert newLabel != null : newFontSize;
			newLabel.setTextPaint(UtilColor.YELLOW);
		}
	}

	private double dragRange() {
		// 1.3 gives just the right separation between labels that are 2 digits.
		return Math.rint(art.maxFontSizeThatFitsInWindow() * nLabels() * 1.3);
	}

	private int nLabels() {
		return art.maxFontSizeThatFitsInWindow() - art.minLegibleFontSize() + 1;
	}

	@Override
	public void enterBoundary(final @NonNull Boundary boundary1) {
		final APText label2 = ((BoundaryWithLabels) boundary1).getLabel(Integer.toString(art.getFontSize()));
		assert label2 != null;
		label2.setTextPaint(UtilColor.YELLOW);
	}

	@Override
	public @NonNull List<LabelNoffset> getLabels() {
		final int minLegibleFontSize = art.minLegibleFontSize();
		final double dx = nLabels() - 1;
		final List<LabelNoffset> result = new ArrayList<>(nLabels());
		for (int i = 0; i < nLabels(); i++) {
			result.add(new LabelNoffset(Integer.toString(i + minLegibleFontSize), i / dx));
		}
		return result;
	}

	@Override
	public double minWidth() {
		return requiredWidth();
	}

	@Override
	public double maxWidth() {
		return requiredWidth();
	}

	private double requiredWidth() {
		return art.w;
	}

	@Override
	public double minHeight() {
		return requiredHeight();
	}

	@Override
	public double maxHeight() {
		return requiredHeight();
	}

	// -----------------------------------
	// <button margin>
	// <count label>
	// <summary text> - includes <button margin> above and below
	// <button margin>
	// <clear button>
	// <button margin>
	// -----------------------------------
	//
	/**
	 * @return 3 * lineH + 5 * buttonMargin. Always equal to an int
	 */
	private double requiredHeight() {
		return 3.0 * art.lineH() + 5.0 * art.buttonMargin();
	}

	private void queryValidRedraw() {
		queryValidRedraw(query.version(), query.textSearchPattern());
	}

	public void queryValidRedraw(final int queryVersion, final @Nullable Pattern textSearchPattern) {
		summaryText.setDescription(queryVersion, textSearchPattern);
		countLabel.maybeSetText(
				UtilString.addCommas(query.getTotalCount()) + " " + query.getGenericObjectLabel(true) + " from");
		placeDatabaseWidget();

		final boolean buttonsVisible = query.isIntensionallyRestricted();
		clear.setNumFilters();
		clear.setVisibility(buttonsVisible || query.isRestrictedData());
		bookmark.setVisibility(buttonsVisible);
		restrict.setVisibility(buttonsVisible);
		colorKey.setVisible(buttonsVisible);
	}

	private class HelpMenuItem extends AbstractMenuItem {
		@NonNull
		String url;

		HelpMenuItem(final @NonNull String _url, final @NonNull String _label) {
			super(_label, "Show this help in your web browser");
			url = _url;
		}

		@Override
		public @Nullable String doCommand() {
			@NonNull
			String where = url;
			if ("<about collection>".equals(where)) {
				// Don't call this until needed, because it calls the database.
				where = art.aboutCollection();
			}
			art.showDocument(where);
			return null;
		}
	}

	private class BeginnerModeCommand extends AbstractMenuItem {
		BeginnerModeCommand() {
			super("Beginner Mode", "Disable advanced features");
		}

		@Override
		public @Nullable String doCommand() {
			art.beginnerMode();
			return "Beginner Mode";
		}
	}

	private class ExpertModeCommand extends AbstractMenuItem {
		ExpertModeCommand() {
			super("Expert Mode", "Enable advanced features");
		}

		@Override
		public @Nullable String doCommand() {
			art.expertMode();
			return "Expert Mode";
		}
	}

	private class CustomModeCommand extends AbstractMenuItem {
		CustomModeCommand() {
			super("Custom", "Choose features");
		}

		@Override
		public @Nullable String doCommand() {
			art.createAndShowGUI();
			return "Custom";
		}
	}

	private class SetDatabaseCommand extends AbstractMenuItem {
		private final @NonNull String dbName;

		SetDatabaseCommand(final @NonNull String dbDesc, final @NonNull String _dbName) {
			super(dbDesc, "Select this database");
			dbName = _dbName;
		}

		@Override
		public @Nullable String doCommand() {
			art.maybeSetDatabase(dbName);
			return getLabel();
		}
	}

	public <V extends Perspective> boolean updateBrushing(final @NonNull Set<V> changedFacets, final int queryVersion) {
		return summaryText.updateHighlighting(changedFacets, queryVersion);
	}

	/**
	 * Only called by replayOp
	 */
	public void clickClear() {
		clear.pick();
	}

	void clearQuery() {
		art.printUserAction(ReplayLocation.BUTTON, Replayer.CLEAR, 0);
		art.clearQuery();
	}

	// Only called by Replayer, which won't have set visible. To work around
	// replay problems, check whether it is a child, too.
	public void chooseReorder(final int facetTypeOrSpecial) {
		if (sortLabelNMenu != null) {
			sortLabelNMenu.chooseReorder(facetTypeOrSpecial);
		} else {
			art.reorder(facetTypeOrSpecial);
		}
	}

	@Override
	public void doHideTransients() {
		if (databaseMenu != null) {
			databaseMenu.doHideTransients();
		}
		helpMenu.doHideTransients();
		modeMenu.doHideTransients();
		textSearch.doHideTransients();
		if (sortLabelNMenu != null) {
			sortLabelNMenu.doHideTransients();
		}
	}

	public @NonNull String getTrimmedEditorText() {
		return textSearch.getTrimmedEditorText();
	}

	private abstract class HeaderButton extends BungeeTextButton {

		HeaderButton(final @NonNull String text, final @Nullable String _mouseDoc) {
			super(text, BungeeConstants.HEADER_BG_COLOR, UtilColor.brighten(BungeeConstants.HEADER_FG_COLOR, 0.8f),
					Header.this.art, null);
			setVisible(false);
			child.setConstrainWidthToTextWidth(true);
			mouseDoc = _mouseDoc;
		}

		// @Override
		// public void mayHideTransients(
		// @SuppressWarnings("unused") final PNode node) {
		// art.mayHideTransients();
		// }
	}

	private final class ClearButton extends HeaderButton {

		private static final @NonNull String CLEAR = " Clear  ";

		private boolean isUnrestrictMode() {
			return !query.isIntensionallyRestricted() && query.isRestrictedData();
		}

		ClearButton() {
			super(CLEAR, "Remove all text and category filters");
		}

		@Override
		public boolean isEnabled() {
			return query.isIntensionallyRestricted() || query.isRestrictedData();
		}

		@Override
		public void doPick() {
			if (isUnrestrictMode()) {
				art.setDatabaseWhileReplaying(false);
			} else {
				clearQuery();
			}
		}

		void setNumFilters() {
			if (isUnrestrictMode()) {
				mouseDoc = "Revert to exploring the entire database";
				setText(" Unrestrict  ");
			} else {
				mouseDoc = "Remove " + query.describeNfilters();
				setText(CLEAR);
			}
		}
	}

	private final class BookmarkButton extends HeaderButton {

		BookmarkButton() {
			super(" Bookmark  ", "Copy a URL for your current query to the system Clipboard");
			if (art.informediaClient != null) {
				setText("New Video Set");
				mouseDoc = "Save these matches as an Informedia video set";
				setDisabledMessage("There are no matches to save");
			} else {
				setDisabledMessage("There are no matches to bookmark");
			}
		}

		@Override
		public boolean isEnabled() {
			return !query.isQueryValid() || query.getOnCount() > 0;
		}

		@Override
		public void doPick() {
			if (art.informediaClient != null) {
				art.saveVideoSet();
			} else {
				art.copyBookmark();
			}
		}
	}

	private final class RestrictButton extends HeaderButton {

		RestrictButton() {
			super(" Restrict  ", "Explore within the current matches");
		}

		@Override
		public @Nullable String getDisabledMessage() {
			String msg;
			if (query.getOnCount() == 0) {
				msg = "Can't restrict when there are no results.";
			} else {
				assert !query.isExtensionallyRestricted();
				if (query.isIntensionallyRestricted()) {
					msg = "Can't restrict when no filters actually filter anything out.";
				} else {
					msg = "Can't restrict when there are no filters.";
				}
			}
			return msg;
		}

		@Override
		public boolean isEnabled() {
			return !query.isQueryValid() || query.isPartiallyRestricted();
		}

		@Override
		public void doPick() {
			art.restrictData();
		}
	}

	public void setSearchVisibility(final boolean isVisible) {
		textSearch.setSearchVisibility(isVisible);
	}

	public void restrictData() {
		setDBdescription(query.baseName);
		if (sortLabelNMenu != null) {
			sortLabelNMenu.updateButtons();
		}
	}

	public void setDBdescription(final @NonNull String desc) {
		if (databaseMenu != null) {
			final Menu databaseMenu2 = databaseMenu;
			databaseMenu2.setText(desc);
			databaseMenu2.label.contract();
		} else {
			assert databaseLabel != null;
			databaseLabel.maybeSetText(desc);
		}
	}

	public void setSortLabelNMenuVisible(final boolean state) {
		if (sortLabelNMenu != null) {
			sortLabelNMenu.setVisible(state);
		}
	}

}