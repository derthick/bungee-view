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

package edu.cmu.cs.bungee.client.viz.selectedItem;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.constrain;

import java.awt.Font;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.FacetTree;
import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.BungeeFrame;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.grid.GridElementWrapper;
import edu.cmu.cs.bungee.client.viz.grid.GridImage;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.Boundary;
import edu.cmu.cs.bungee.piccoloUtils.gui.TextBox;

/**
 * Represents the rightmost frame
 */
public final class SelectedItemColumn extends BungeeFrame implements RedrawCallback {

	/**
	 * set to @NonNull in init()
	 */
	transient public ItemSetter setter;
	/**
	 * set to @NonNull in init()
	 */
	FacetTreeViz facetTreeViz;
	/**
	 * set to @NonNull in init()
	 */
	TextBox selectedItemTextBox = null;

	/**
	 * Only set by updateBoundary(). Never depends on text. No one else should
	 * refer to it except through minTextBoxH().
	 */
	private double preferredTextBoxHpercentage = 0.5;

	/**
	 * The displayed image for selected item.
	 */
	private @Nullable GridImage gridImage;

	// /**
	// * Set to art.selectedItem AFTER image, description, and facetTree have
	// been
	// * displayed. null while these are being retrieved.
	// */
	// private @Nullable Item currentItem;

	public SelectedItemColumn(final @NonNull Bungee a) {
		super(a, BungeeConstants.SELECTED_ITEM_TEXT_COLOR, "Selected " + a.getQuery().getGenericObjectLabel(false),
				true);
		setVisible(false);
	}

	public void init() {
		setter = new ItemSetter(this);
		new Thread(setter).start();

		final double usableWifNoScrollbar = usableWifNoScrollbar();
		assert usableWifNoScrollbar > getFontSize() : usableWifNoScrollbar;
		facetTreeViz = new FacetTreeViz(art, usableWifNoScrollbar);
		addChild(facetTreeViz);

		selectedItemTextBox = new TextBox(usableWifNoScrollbar, 50.0, null,
				BungeeConstants.SELECTED_ITEM_TEXTBOX_SCROLL_BG_COLOR,
				BungeeConstants.SELECTED_ITEM_TEXTBOX_SCROLL_FG_COLOR,
				UtilColor.brighten(BungeeConstants.SELECTED_ITEM_TEXT_COLOR, 1.3f), null, art.getCurrentFont(),
				art.scrollbarWidth(), art.internalColumnMargin);
		selectedItemTextBox.setEditable(art.getIsEditing(), art.getCanvas(), edit);
		addChild(selectedItemTextBox);
	}

	@Override
	public void setExitOnError(final boolean isExit) {
		if (setter != null) {
			setter.setExitOnError(isExit);
		}
	}

	@Override
	public void maybeSetVisible(final boolean isVisible) {
		setVisible(isVisible && art.getSelectedItem() != null);
	}

	/**
	 * Only called by Bungee.queryValidRedraw()
	 */
	public void queryValidRedraw() {
		query.queueOrRedraw(this);
	}

	@Override
	public void redrawCallback() {
		maybeSetVisible(true);
		if (getVisible()) {
			initted();
			if (facetTreeViz.getItem() != art.getSelectedItem()) {
				draw();
			}
		}
	}

	/**
	 * Called by boundaryDragged(), setFont(), and BungeeFrame.forceValidate().
	 */
	@Override
	public void validateInternal() {
		super.validateInternal();
		maybeSetVisible(true);
		if (getVisible()) {
			draw();
		}
	}

	/**
	 * Make sure facetTree and gridImage are cached, then
	 * updateSelectedItemTextBox() and draw facetTree.
	 *
	 * Y Margins: [button=art.buttonMargin(); internal=internalYmargin()]
	 *
	 * <top> 0 <label> button <image> internal <desc> internal <tree> 0 <bottom>
	 *
	 *
	 * X Margins: [internal=internalXmargin()]
	 *
	 * <left> internal <textBox> internal <right>
	 *
	 * or
	 *
	 * <left> internal <facetTree> internal <scrollbar> internal <right>
	 */
	private void draw() {
		assert getVisible();
		final Item selectedItem = art.getSelectedItem();
		assert selectedItem != null; // True if getVisible()
		final FacetTree facetTree = getFacetTree(selectedItem);
		if (facetTree != null && maybeAddImage(selectedItem)) {
			// if maybeAddImage fails, we'll be called again
			updateSelectedItemTextBox();

			// System.out.println("SelectedItemColumn.draw
			// selectedItemTextBox.getHeight()="
			// + selectedItemTextBox.getHeight() + " (" +
			// (selectedItemTextBox.getHeight() / art.lineH())
			// + " lines) selectedItemTextBox.getText()='" +
			// selectedItemTextBox.getText() + "'");
			final double treeY = selectedItemTextBox.getMaxY() + 2.0 * internalYmargin();
			final double treeH = getHeight() - getBottomMargin() - treeY;
			assert treeH >= minFacetTreeH() : "No room for facetTreeViz: treeY=" + treeY + " internalYMargin="
					+ internalYmargin() + " virtualH=" + getHeight() + "\n  selectedItemTextBox.getBounds="
					+ selectedItemTextBox.getBounds() + "\n selectedItemTextBox.getYOffset="
					+ selectedItemTextBox.getYOffset() + "\n   selectedItemTextBox.getScale="
					+ selectedItemTextBox.getScale();
			facetTreeViz.setOffset(internalXmargin(), treeY);
			facetTreeViz.validate(usableWifNoScrollbar(), treeH);
			if (boundary != null && !boundary.isDragging()) {
				setBoundaryOffset(treeY - internalYmargin());
			}
			// currentItem = selectedItem;
			art.removeInitialHelp();
		}
		art.getGrid().validateYellowSIcolumnOutline();
	}

	/**
	 * Only called by draw()
	 *
	 * @return FacetTree, or null and call setter.set(selectedItem), which calls
	 *         redrawCallback()
	 */
	@Nullable
	FacetTree getFacetTree(final Item selectedItem) {
		FacetTree result = null;
		if (selectedItem != null) {
			result = FacetTree.lookupFacetTree(selectedItem, query);
			facetTreeViz.setTree(result);
			if (result == null) {
				setter.set(selectedItem);
			}
		}
		return result;
	}

	/**
	 * Only called by draw() and boundaryDragged()
	 */
	private void setBoundaryOffset(final double y) {
		if (boundary != null) {
			if (facetTreeViz.isScrollbarVisible() || selectedItemTextBox.isScrollbar()) {
				addChild(boundary);
				assert boundary != null;
				boundary.setLogicalDragPosition(y);
			} else {
				assert boundary != null;
				boundary.removeFromParent();
			}
		} else {
			assert !art.getShowBoundaries() : "descBoundary is null.";
		}
	}

	/**
	 * Only called by draw()
	 *
	 * Set textbox text to facetTree.description(), highlight searches, and set
	 * textBox height based on text and facetTree.
	 */
	private void updateSelectedItemTextBox() {
		selectedItemTextBox.setText(facetTreeViz.getTree().description(), query.textSearchPattern());
		assert gridImage != null;
		final double textBoxY = Math.ceil(gridImage.getMaxY() + internalYmargin());
		selectedItemTextBox.setOffset(internalXmargin(), textBoxY);

		final double maxTextBoxH = Math.max(preferredTextBoxH(),
				availableBoxPlusTreeH() - facetTreeViz.ensureTotalLines() * art.lineH());
		selectedItemTextBox.validate(usableWifNoScrollbar(), maxTextBoxH);

		assert selectedItemTextBox.getHeight() <= maxTextBoxH : selectedItemTextBox.getHeight() + " " + maxTextBoxH;
		assert selectedItemTextBox.getMaxY() == textBoxY
				+ selectedItemTextBox.getHeight() : "selectedItemTextBox.getMaxY=" + selectedItemTextBox.getMaxY()
						+ " textBoxY=" + textBoxY + " selectedItemTextBox.getHeight()="
						+ selectedItemTextBox.getHeight() + " (textBoxY+selectedItemTextBox.getHeight())="
						+ (textBoxY + selectedItemTextBox.getHeight()) + " selectedItemTextBox.getText():\n"
						+ selectedItemTextBox.getText();
	}

	/**
	 * Only called by updateSelectedItemTextBox()
	 *
	 * Never less than TextBox.minHeight() [scrollW * 3.0]. Can be made larger
	 * by dragging boundary. Never depends on text. Always equal to an int.
	 */
	private double preferredTextBoxH() {
		final double availableBoxPlusTreeH = availableBoxPlusTreeH();
		return constrain(Math.rint(availableBoxPlusTreeH * preferredTextBoxHpercentage),
				selectedItemTextBox.minHeight(), availableBoxPlusTreeH - minFacetTreeH());
	}

	/**
	 * Always equal to an int
	 */
	private double minFacetTreeH() {
		return Math.min(3.0 * art.lineH(), maxFacetTreeH());
	}

	/**
	 * Always equal to an int
	 */
	private double maxFacetTreeH() {
		return facetTreeViz.ensureTotalLines() * art.lineH();
	}

	/**
	 * @return Space available for selectedItemTextBox and facetTreeViz, not
	 *         including margins.
	 */
	private double availableBoxPlusTreeH() {
		return getHeight() - selectedItemTextBox.getYOffset() - 2.0 * internalYmargin() - getBottomMargin();
	}

	/**
	 * @return gridImage != null
	 */
	private boolean maybeAddImage(final @NonNull Item item) {
		if (gridImage != null) {
			@NonNull
			final GridImage _gridImage = gridImage;
			if (_gridImage.getItem() == item) {
				assert _gridImage.getParent() == this;
				_gridImage.setSelectedItemMode(getTopMargin(), maxImageW(), maxImageH(), getWidth());
			} else {
				removeChild(gridImage);
				gridImage = null;
			}
		}
		if (gridImage == null) {
			gridImage = (GridImage) GridElementWrapper.lookupGridElement(item, false);
			if (gridImage != null) {
				gridImage.setSelectedItemMode(getTopMargin(), maxImageW(), maxImageH(), getWidth());
				addChild(gridImage);
			} else {
				setter.set(item);
			}
		}
		return gridImage != null;
	}

	int maxImageW() {
		final int result = (int) (getWidth() - 2.0 * internalXmargin());
		assert result > 0;
		return result;
	}

	int maxImageH() {
		final int result = (int) (getHeight() / 3.0);
		assert result > 0;
		return result;
	}

	@Override
	public void setFeatures() {
		super.setFeatures();
		if (selectedItemTextBox != null) {
			selectedItemTextBox.setEditable(art.getIsEditing(), art.getCanvas(), edit);
		}
	}

	@Override
	protected boolean setFont(final @NonNull Font font) {
		final boolean result = super.setFont(font);
		if (result && facetTreeViz != null) {
			facetTreeViz.setFont(font);
			selectedItemTextBox.setFontNScrollbarW(font, art.scrollbarWidth());
			validateInternal();
		}
		return result;
	}

	@Override
	public double minHeight() {
		double minH = internalYmargin() * 3.0;
		if (selectedItemTextBox != null) {
			minH += selectedItemTextBox.getYOffset();
		}
		return minH;
	}

	public <V extends Perspective> boolean updateBrushing(final @NonNull Set<V> changedFacets, final int queryVersion) {
		return isInitted() && facetTreeViz.updateHighlighting(changedFacets, queryVersion, YesNoMaybe.MAYBE);
	}

	public void stop() {
		if (setter != null) {
			setter.exit();
			setter = null;
		}
	}

	@Override
	public void doHideTransients() {
		selectedItemTextBox.revert();
	}

	@Override
	public void boundaryDragged(final @NonNull Boundary boundary1) {
		if (selectedItemTextBox != null) {
			final double dragW = boundary1.constrainedLogicalDragPosition();
			setBoundaryOffset(dragW);
			final double textBoxH = dragW - internalYmargin() - selectedItemTextBox.getYOffset();
			preferredTextBoxHpercentage = textBoxH / availableBoxPlusTreeH();
			validateInternal();
		}
	}

	@Override
	public double minDragLimit(@SuppressWarnings("unused") final @NonNull Boundary boundary1) {
		final double maxFacetTreeH = facetTreeViz.getItem() == null ? 0.0 : maxFacetTreeH();
		return Math.max(selectedItemTextBox.getYOffset() + selectedItemTextBox.minHeight() + internalYmargin(),
				getHeight() - maxFacetTreeH - internalYmargin() - getBottomMargin());
	}

	@Override
	public double maxDragLimit(@SuppressWarnings("unused") final @NonNull Boundary boundary1) {
		final double minFacetTreeH = facetTreeViz.getItem() == null ? 0.0 : minFacetTreeH();
		return Math.min(selectedItemTextBox.getYOffset() + selectedItemTextBox.maxHeight() + internalYmargin(),
				getHeight() - minFacetTreeH - internalYmargin() - getBottomMargin());
	}

	@Override
	protected @NonNull Boundary getBoundary() {
		final Boundary result = new Boundary(this, true);
		result.setVisualOffset(0.0);
		return result;
	}

	// ///////////////////////////// Editing ///////////////////////////

	private transient @NonNull Runnable edit = new Runnable() {

		@Override
		public void run() {
			try {
				final String description = selectedItemTextBox.getText();
				assert description != null;
				art.setItemDescription(description);
			} catch (final Throwable e) {
				art.stopReplayer();
				throw (e);
			}
		}
	};

	// ///////////////////////////// End Editing //////////////////////

}
