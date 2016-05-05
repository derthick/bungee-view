package edu.cmu.cs.bungee.client.viz.bungeeCore;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;

/**
 * A bunch of tweakable parameters
 */
public class BungeeConstants {
	static final boolean IS_PRINT_USER_ACTIONS = true;

	public static final long RANK_ANIMATION_MS = 1000L;
	public static final long DATA_ANIMATION_MS = 1000L;
	public static final long RANK_ANIMATION_STEP = 100L;
	public static final long DATA_ANIMATION_STEP = 100L;

	static final int DEFAULT_TEXT_SIZE = 14;

	public static final int THUMB_QUALITY = 50;
	public static final int IMAGE_QUALITY = 100;
	/**
	 * Choose a number of thumbnail columns so that the grid is at least this
	 * tall and wide. (Thumbnails will be at least MIN_THUMB_SIZE - 2 *
	 * THUMB_BORDER)
	 */
	public static final double MIN_THUMB_SIZE = 20.0;
	static final double SCROLLWIDTH_TO_LINEH_RATIO = 0.9;
	public static final int YELLOW_SELECTED_THUMB_OUTLINE_THICKNESS = 3;
	public static final int YELLOW_SI_COLUMN_OUTLINE_THICKNESS = 2;
	public static final int PERSPECTIVE_LIST_OUTLINE_THICKNESS = 3;
	public static final int EULER_DIAGRAM_OUTLINE_THICKNESS = 1;

	public static final int MIN_INFLUENCE_DIAGRAM_EDGES = 1;

	static final double BEGINNER_MARGIN_SIZE_RATIO = 20.0 / DEFAULT_TEXT_SIZE;
	static final double EXPERT_MARGIN_SIZE_RATIO = 10.0 / DEFAULT_TEXT_SIZE;

	public static final float DEFAULT_LIGHTBEAM_TRANSPARENCY = 0.25f;// 0.15f;
	public static final float HIGHLIGHTED_LIGHTBEAM_TRANSPARENCY = 0.4f;// 0.25f;

	public static float getLightbeamTransparency(final boolean isHighlight) {
		return isHighlight ? HIGHLIGHTED_LIGHTBEAM_TRANSPARENCY : DEFAULT_LIGHTBEAM_TRANSPARENCY;
	}

	/**
	 * CTRL | SHIFT | EXCLUDE
	 */
	public static final int DONT_DESELECT_OTHERS_MASK = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
			| Util.EXCLUDE_ACTION;

	/**
	 * The → symbol that goes in front of rank labels
	 */
	public static final @NonNull String PARENT_INDICATOR_PREFIX = "→";

	/**
	 * Don't automatically prefetch names for facets with more children than
	 * this. 1000 is empirically the point where prefetching is the same speed
	 * with or without using index parent. With more children than this,
	 * prefetch ignores this index.
	 */
	public static final int PREFETCH_NAMES_MAX_CHILDREN = 1000;

	public enum Significance {
		EXCLUDED, NEGATIVE, UNASSOCIATED, POSITIVE, INCLUDED
	}

	// public enum Fade {
	// NORMAL, HIGHLIGHTED
	// }

	public static final @NonNull Color GOLD_BORDER_COLOR = UtilColor.getHSBColor(0.17f, 0.5f, 0.4f);

	public static final @NonNull Color INCLUDED_COLOR = new Color(0x00ff00);
	private static final @NonNull Color POSITIVE_ASSOCIATION_COLOR = new Color(0x509950);
	public static final @NonNull Color EXCLUDED_COLOR = new Color(0xac9200);
	private static final @NonNull Color NEGATIVE_ASSOCIATION_COLOR = new Color(0x8e784f);
	private static final @NonNull Color UNASSOCIATED_COLOR = new Color(0x707070);

	private static final @NonNull Color UNASSOCIATED_COLOR_BRIGHTER = Util.nonNull(UNASSOCIATED_COLOR.brighter());
	private static final @NonNull Color UNASSOCIATED_COLOR_DARKER = Util.nonNull(UNASSOCIATED_COLOR.darker());

	/**
	 * Colors used for facets significantly positively associated with the
	 * current filters
	 */
	public static final @NonNull List<Color> POSITIVE_ASSOCIATION_COLORS = UtilArray
			.getUnmodifiableList(POSITIVE_ASSOCIATION_COLOR, POSITIVE_ASSOCIATION_COLOR.brighter());
	/**
	 * Colors used for facets significantly negatively associated with the
	 * current filters
	 */
	public static final @NonNull List<Color> NEGATIVE_ASSOCIATION_COLORS = UtilArray
			.getUnmodifiableList(NEGATIVE_ASSOCIATION_COLOR, NEGATIVE_ASSOCIATION_COLOR.brighter());

	/**
	 * Colors used for facets in positive filters
	 */
	public static final @NonNull List<Color> INCLUDED_COLORS = UtilArray.getUnmodifiableList(INCLUDED_COLOR,
			new Color(0xc4ffc4));

	/**
	 * Colors used for facets in negative filters
	 */
	public static final @NonNull List<Color> EXCLUDED_COLORS = UtilArray.getUnmodifiableList(EXCLUDED_COLOR,
			EXCLUDED_COLOR.brighter());

	/**
	 * Colors used for facets not significantly associated with the current
	 * filters
	 */
	public static final @NonNull List<Color> UNASSOCIATED_COLORS = UtilArray.getUnmodifiableList(UNASSOCIATED_COLOR,
			UNASSOCIATED_COLOR_BRIGHTER);

	public static final @NonNull List<List<Color>> COLORS_BY_SIGNIFICANCE = UtilArray.getUnmodifiableList(
			BungeeConstants.EXCLUDED_COLORS, BungeeConstants.NEGATIVE_ASSOCIATION_COLORS,
			BungeeConstants.UNASSOCIATED_COLORS, BungeeConstants.POSITIVE_ASSOCIATION_COLORS,
			BungeeConstants.INCLUDED_COLORS);

	public static List<Color> significanceColorFamily(final Significance significance) {
		return COLORS_BY_SIGNIFICANCE.get(significance.ordinal());
	}

	/**
	 * Used in percent labels and hotzone
	 */
	public static final @NonNull Color PV_BG_COLOR = new Color(0x1f2333);

	/**
	 * Yellow outlines for thumbnails, SelectedItem, and EulerDiagrams.
	 */
	public static final @NonNull Color OUTLINE_COLOR = UtilColor.YELLOW;
	public static final @NonNull Color HELP_COLOR = new Color(0xe78d00);

	public static final @NonNull Color HEADER_BG_COLOR = new Color(0x001a66);
	public static final @NonNull Color HEADER_FG_COLOR = new Color(0x699999);

	public static final @NonNull Color TEXT_FG_COLOR = UtilColor.WHITE;
	static final @NonNull Color IS_TOO_SMALL_BG_COLOR = UtilColor.WHITE;

	public static final @NonNull Color BVBG = UtilColor.BLACK;
	static final @NonNull Color EDITING_MENU_BG_COLOR = UtilColor.BLACK;

	public static final @NonNull Color PERSPECTIVE_LIST_BG_COLOR = BVBG;
	public static final @NonNull Color PERSPECTIVE_LIST_TEXT_COLOR = Util.nonNull(UNASSOCIATED_COLOR.brighter());
	public static final @NonNull Color PERSPECTIVE_LIST_SORT_BUTTON_BG_COLOR = UNASSOCIATED_COLOR_DARKER;
	public static final @NonNull Color PERSPECTIVE_LIST_SCROLL_FG_COLOR = UNASSOCIATED_COLOR;
	public static final @NonNull Color PERSPECTIVE_LIST_SCROLL_BG_COLOR = UNASSOCIATED_COLOR_DARKER;

	public static final @NonNull Color SELECTED_ITEM_TEXT_COLOR = new Color(0x523636);
	public static final @NonNull Color SELECTED_ITEM_TEXTBOX_SCROLL_FG_COLOR = SELECTED_ITEM_TEXT_COLOR;
	public static final @NonNull Color SELECTED_ITEM_TEXTBOX_SCROLL_BG_COLOR = Util
			.nonNull(SELECTED_ITEM_TEXTBOX_SCROLL_FG_COLOR.darker());
	public static final @NonNull Color GRID_FG_COLOR = SELECTED_ITEM_TEXT_COLOR;
	public static final @NonNull Color GRID_SCROLL_FG_COLOR = GRID_FG_COLOR;
	public static final @NonNull Color GRID_SCROLL_BG_COLOR = Util.nonNull(GRID_SCROLL_FG_COLOR.darker());
	public static final @NonNull Color GRID_ELEMENT_WRAPPER_BORDER_COLOR = Util
			.nonNull(GRID_FG_COLOR.brighter().brighter().brighter());

	public static final @NonNull Color PERCENT_LABEL_COLOR = UNASSOCIATED_COLOR_BRIGHTER;
	public static final @NonNull Color TAGWALL_FG_COLOR = UNASSOCIATED_COLOR;
	public static final @NonNull Color TAGWALL_FG_COLOR_DARKER = Util.nonNull(TAGWALL_FG_COLOR.darker());
	public static final @NonNull Color FACET_TREE_SCROLL_FG_COLOR = UNASSOCIATED_COLOR;
	public static final @NonNull Color FACET_TREE_SCROLL_BG_COLOR = UNASSOCIATED_COLOR_DARKER;
	public static final @NonNull Color CHECKMARK_COLOR = Util.nonNull(INCLUDED_COLORS.get(1));
	public static final @NonNull Color CHECKBOX_COLOR = Util
			.nonNull(UtilColor.desaturate(Util.nonNull(POSITIVE_ASSOCIATION_COLORS.get(0)), -1).darker());
	public static final @NonNull Color X_COLOR = Util.nonNull(NEGATIVE_ASSOCIATION_COLORS.get(1));
	public static final @NonNull Color XBOX_COLOR = Util
			.nonNull(UtilColor.desaturate(Util.nonNull(EXCLUDED_COLORS.get(0)), -1).darker());

}
