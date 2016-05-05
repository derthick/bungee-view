package edu.cmu.cs.bungee.client.viz.header;

import java.awt.Font;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyContainer;

public class SortLabelNMenu extends LazyContainer {

	public static final int ORDER_BY_RANDOM = -1;
	public static final int ORDER_BY_ID = 0;

	private final @NonNull Bungee art;
	private final @NonNull SortMenu sortMenu;
	private final @NonNull APText sortLabel;

	public SortLabelNMenu(final @NonNull Bungee _art) {
		art = _art;
		sortLabel = art.oneLineLabel();
		sortLabel.setTextPaint(BungeeConstants.HEADER_FG_COLOR /* .darker() */);
		sortLabel.maybeSetText("sorted by");
		addChild(sortLabel);

		sortMenu = new SortMenu(art);
		addChild(sortMenu);

		validate();
	}

	public void updateButtons() {
		sortMenu.updateButtons();
	}

	void validate() {
		sortMenu.setOffset(sortLabel.getMaxX()
				+ art.internalColumnMargin/* + art.lineH() */, 0.0);
		setBoundsFromChildrenBounds();
	}

	protected void setFont(final @NonNull Font font) {
		sortLabel.setFont(font);
		sortMenu.setFont(font);
		validate();
	}

	public void doHideTransients() {
		sortMenu.doHideTransients();
	}

	/**
	 * Only called by Replayer, which won't have set visible.
	 */
	public void chooseReorder(final int facetType) {
		if (!sortMenu.visible) {
			sortMenu.pick();
		}
		sortMenu.choose(facetType + 1);
	}

	/**
	 * Only called by Replayer.
	 */
	public static @Nullable String getName(final int facetTypeOrSpecial, final @NonNull Query query) {
		String result;
		switch (facetTypeOrSpecial) {
		case ORDER_BY_RANDOM:
			result = "ORDER_BY_RANDOM";
			break;
		case ORDER_BY_ID:
			result = "ORDER_BY_ID";
			break;
		default:
			final Perspective perspective = query.findPerspectiveNow(facetTypeOrSpecial);
			result = perspective != null ? perspective.toString() : null;
			break;
		}
		return result;
	}

}
