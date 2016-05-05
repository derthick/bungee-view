package edu.cmu.cs.bungee.client.viz.selectedItem;

import java.awt.image.BufferedImage;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.FacetTree;
import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.grid.GridElement;
import edu.cmu.cs.bungee.client.viz.grid.GridElementWrapper;
import edu.cmu.cs.bungee.client.viz.grid.GridImage;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.threads.UpdateThread;

/**
 * Gets images and descriptions for SelectedItem column in the background.
 */
public final class ItemSetter extends UpdateThread<Object> { // NO_UCD (use
	// default)

	final @NonNull SelectedItemColumn siColumn;

	ItemSetter(final @NonNull SelectedItemColumn _selectedItem) {
		super("ItemSetter", -2);
		siColumn = _selectedItem;
	}

	/**
	 * Called from SelectedItem and Bungee.setInitialSelectedItem()
	 */
	public void set(final @NonNull Item item) {
		add(item);
	}

	/**
	 * Only called by Bungee.setInitialSelectedItem()
	 */
	public void set(final @NonNull String urlString) {
		assert urlString != null;
		add(urlString);
	}

	@Override
	/**
	 * Cache item's description, image, and FacetTree; then queue SI column
	 * redraw;
	 */
	public void process(final @NonNull Object itemURLStringOrItem) throws SQLException {
		if (isQueueEmpty()) {
			final Query query = query();
			final Item item = (itemURLStringOrItem instanceof String)
					? Item.ensureItem(query.itemIndexFromURL((String) itemURLStringOrItem)[1])
					: (Item) itemURLStringOrItem;

			ensureDescAndImage(item);
			if (art().getIsEditing() && !UtilString.isNonEmptyString(item.getDescription(query))) {
				item.setDescription("click to add a description");
			}
			FacetTree.ensureFacetTree(item, query);
			if (isQueueEmptyNnotExited()) {
				siColumn.facetTreeViz.prevScrollOffsetLines = 0;
				query.queueRedraw(siColumn);
			}
		}
	}

	/**
	 * Only called by process.
	 *
	 * ensures [SelectedItem] GridElementWrapper has DescAndImage.
	 */
	void ensureDescAndImage(final @NonNull Item item) throws SQLException {
		boolean isBigEnough = false;
		final GridElement gridElement = GridElementWrapper.lookupGridElement(item, false);
		if (gridElement != null) {
			assert gridElement instanceof GridImage : gridElement;
			isBigEnough = gridElement.isBigEnough(siColumn.maxImageW(), siColumn.maxImageH(),
					BungeeConstants.IMAGE_QUALITY);
		}
		if (!isBigEnough) {
			try (final ResultSet rs = query().getDescAndImage(item, siColumn.maxImageW(), siColumn.maxImageH(),
					BungeeConstants.IMAGE_QUALITY);) {
				rs.next();
				final String description = rs.getString(1);
				assert description != null;
				item.setDescription(description);
				final BufferedImage bufferedImage = ((MyResultSet) rs).getBufferedImage(2);
				GridElementWrapper.ensureGridElementWrapper(art(), item, bufferedImage, rs.getInt(3), rs.getInt(4),
						false);
			}
		}
	}

	@Override
	public synchronized void exit() {
		art().stopReplayer();
		super.exit();
	}

	@NonNull
	Query query() {
		return art().getQuery();
	}

	@NonNull
	Bungee art() {
		return siColumn.art;
	}

}