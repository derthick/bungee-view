package edu.cmu.cs.bungee.piccoloUtils.gui;

import org.eclipse.jdt.annotation.NonNull;

/**
 * SortButtons are placed atop columns in a table to sort by that column.
 * setOrder generally iterates over the buttons for a table like this:
 *
 * for (final SortButton<?> button : table.buttons) {
 *
 * ....((SortButton<SortBy>) button).setDirection(sortField, sortDirection);}
 *
 * setDirection makes the SortButton corresponding to sortField sort by
 * sortDirection, and makes all the other buttons NO-OPs.
 */
public interface SortButtons<E> {

	void setOrder(@NonNull E sortField, @NonNull SortDirection direction);

	// SortButton<E>[] getSortButtons();
	//
	// void updateButtons(final E sortField2, final int direction);

}