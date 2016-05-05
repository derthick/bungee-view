package edu.cmu.cs.bungee.client.viz.grid;

import static edu.cmu.cs.bungee.javaExtensions.Util.CLICK_THUMB_INTENTIONALLY_MODIFIER;

import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.markup.BungeeClickHandler;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.SolidBorder;

/**
 * A substitute for a common superclass of GridImage and GridText (which both
 * implement GridElement). It represents an Item, and knows its description, all
 * it facets, and its offset in onCounts.
 */
public class GridElementWrapper implements Serializable {

	protected static final long serialVersionUID = 1L;

	private transient static SoftReference<Map<Item, GridElement>> GRID_IMAGES_USE_DESC;

	private transient static SoftReference<Map<Item, GridElement>> GRID_IMAGES_NO_DESC;

	protected final @NonNull Bungee art;

	/**
	 * true in Grid; false in SelectedItem
	 */
	final boolean useDescIfNoImage;

	private final @NonNull Item item;

	/**
	 * brushing makes this visible.
	 *
	 * Lazy initializion, always in mouse process, so essentially @NonNull
	 */
	private SolidBorder border;

	/**
	 * Lazy initializion, so essentially @NonNull
	 */
	private final GridElement gridElement;

	/**
	 * Creates the wrapper
	 */
	public static synchronized void ensureGridElementWrapper(final @NonNull Bungee bungee, final @NonNull Item item,
			final @Nullable BufferedImage bufferedImage, final int _rawW, final int _rawH,
			final boolean _useDescIfNoImage) {
		if (lookupGridElement(item, _useDescIfNoImage) == null) {
			Util.ignore(new GridElementWrapper(bungee, item, _useDescIfNoImage, bufferedImage, _rawW, _rawH));
		}
	}

	private GridElementWrapper(final @NonNull Bungee _art, final @NonNull Item _item, final boolean _useDescIfNoImage,
			@Nullable final BufferedImage bufferedImage, final int _rawW, final int _rawH) {
		art = _art;
		useDescIfNoImage = _useDescIfNoImage;
		item = _item;
		gridElement = (bufferedImage == null && useDescIfNoImage) ? new GridText(this)
				: new GridImage(this, bufferedImage, _rawW, _rawH);

		cacheGridElement();
		gridElement.addInputEventListener(BungeeClickHandler.getBungeeClickHandler());
	}

	void brush(final boolean state) {
		final @NonNull Set<Perspective> facets = state ? getItem().getFacets() : UtilArray.EMPTY_SET;
		art().brushFacets(facets);
	}

	boolean updateBrushing(final Set<Perspective> changedFacets) {
		final boolean isBorderVisible = UtilArray.intersects(getItem().getFacets(), changedFacets);
		final boolean result = isBorderVisible != (border != null && border.getVisible());

		if (result) {
			if (border != null) {
				border.setVisible(isBorderVisible);
			} else {
				border = new SolidBorder(BungeeConstants.GRID_ELEMENT_WRAPPER_BORDER_COLOR, 2);
				gridElement.addChild(border);
			}
		}
		if (isBorderVisible) {
			border.moveToFront();
		}
		return result;
	}

	public double getWidth() {
		return getGridElement().getWidth();
	}

	public double getHeight() {
		return getGridElement().getHeight();
	}

	@NonNull
	Bungee art() {
		return art;
	}

	@NonNull
	Query query() {
		return art.getQuery();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, gridElement);
	}

	/**
	 * Set w and h as large as possible while maintaining aspect ratio and
	 * without exceeding maxImageW/maxImageH or cached image w/h.
	 */
	void setDisplaySize(final int maxImageW, final int maxImageH) {
		getGridElement().setDisplaySize(maxImageW, maxImageH);
	}

	public @NonNull GridElement getGridElement() {
		assert gridElement != null;
		return gridElement;
	}

	public boolean isGridImage() {
		return getGridElement() instanceof GridImage;
	}

	void setOffset(final double x, final double y) {
		getGridElement().setOffset(x, y);
	}

	public double getXOffset() {
		return getGridElement().getXOffset();
	}

	public double getYOffset() {
		return getGridElement().getYOffset();
	}

	public double getScale() {
		final double scale = getGridElement().getScale();
		assert scale == 1.0 : scale;
		return scale;
	}

	public String getItemName() {
		return getItem().getDescription(query());
	}

	void printUserAction() {
		art().printUserAction(useDescIfNoImage ? ReplayLocation.THUMBNAIL : ReplayLocation.IMAGE, getItem().getID(),
				CLICK_THUMB_INTENTIONALLY_MODIFIER);
	}

	/////////////////// Caching /////////////////////////

	public static @Nullable GridElementWrapper lookupGridElementWrapper(final @NonNull Item item,
			final boolean _useDescIfNoImage) {
		final GridElement gridElement = lookupGridElement(item, _useDescIfNoImage);
		return gridElement == null ? null : gridElement.getWrapper();
	}

	public static @Nullable GridElement lookupGridElement(final @NonNull Item item, final boolean _useDescIfNoImage) {
		return getGridElementsTable(_useDescIfNoImage).get(item);
	}

	private void cacheGridElement() {
		getGridElementsTable(useDescIfNoImage).put(getItem(), getGridElement());
	}

	/**
	 * Only called when editing
	 */
	public static void decacheGridElement(final @Nullable Item item) {
		if (item != null) {
			getGridElementsTable(true).remove(item);
			getGridElementsTable(false).remove(item);
		}
	}

	/**
	 * @param _useDescIfNoImage
	 *            true for ResultsGrid; false for SelectedItem
	 */
	private static @NonNull Map<Item, GridElement> getGridElementsTable(final boolean _useDescIfNoImage) {
		Map<Item, GridElement> table = (_useDescIfNoImage
				? (GRID_IMAGES_USE_DESC == null ? null : GRID_IMAGES_USE_DESC.get())
				: (GRID_IMAGES_NO_DESC == null ? null : GRID_IMAGES_NO_DESC.get()));
		if (table == null) {
			table = new Hashtable<>();
			final SoftReference<Map<Item, GridElement>> tableReference = new SoftReference<>(table);
			if (_useDescIfNoImage) {
				GRID_IMAGES_USE_DESC = tableReference;
			} else {
				GRID_IMAGES_NO_DESC = tableReference;
			}
		}
		return table;
	}

	public Bungee getArt() {
		return art;
	}

	public @NonNull Item getItem() {
		return item;
	}

}