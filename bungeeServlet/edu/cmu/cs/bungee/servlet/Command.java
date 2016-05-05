package edu.cmu.cs.bungee.servlet;

import org.eclipse.jdt.annotation.NonNull;

public enum Command {
	CONNECT, @NonNull CLOSE, @NonNull ONCOUNTS_IGNORING_FACET, @NonNull ABOUT_COLLECTION, @NonNull FILTERED_COUNTS,

	@NonNull UPDATE_ON_ITEMS, @NonNull PREFETCH, @NonNull OFFSET_ITEMS, @NonNull THUMBS,

	@NonNull DESC_AND_IMAGE, @NonNull ITEM_INFO, @NonNull ITEM_URL, @NonNull ITEM_OFFSET, @NonNull ITEM_INDEX_FROM_URL,

	@NonNull RESTRICT, @NonNull ADD_ITEMS_FACET, @NonNull ADD_CHILD_FACET, @NonNull REMOVE_ITEM_FACET, @NonNull REPARENT,

	@NonNull ADD_ITEM_FACET, @NonNull WRITEBACK, @NonNull REVERT, @NonNull ROTATE, @NonNull RENAME, @NonNull REMOVE_ITEMS_FACET,

	@NonNull FACET_NAMES, @NonNull REORDER_ITEMS, @NonNull SET_ITEM_DESCRIPTION, @NonNull OPS_SPEC, @NonNull RANDOM_OPS_SPEC,

	@NonNull LETTER_OFFSETS, @NonNull ON_COUNT_MATRIX, @NonNull TOP_CANDIDATES, @NonNull IMPORT_FACET, @NonNull LOSE_SESSION,
}