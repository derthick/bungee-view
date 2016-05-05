package edu.cmu.cs.bungee.client.viz.tagWall;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.piccoloUtils.gui.SortDirection;

/**
 * Possible criteria for sorting Perspectives
 */
public enum SortBy {
	SORT_BY_SELECTION(SortDirection.Z_A, "Sort by selection status"),

	SORT_BY_ON_COUNT(SortDirection.Z_A, "Sort by number of items satisfying all filters except those on parent"),

	// SORT_BY_TOTAL_COUNT,

	SORT_BY_ID(SortDirection.A_Z, "Sort by name");

	private final @NonNull SortDirection defaultSortDirection;

	private final @NonNull String mouseDoc;

	private SortBy(final @NonNull SortDirection _defaultSortDirection, final @NonNull String _mouseDoc) {
		defaultSortDirection = _defaultSortDirection;
		mouseDoc = _mouseDoc;
	}

	@NonNull
	public SortDirection defaultSortDirection() {
		return defaultSortDirection;
	}

	public @NonNull String mouseDoc() {
		return mouseDoc;
	}
}