package edu.cmu.cs.bungee.client.query.markup;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;

/**
 * A little language for tagged sequences of query components, for generating
 * natural language descriptions. Each element is rendered as an APText (or is a
 * modifier affecting how subsequent elements are rendered). The rendering is
 * done using MarkupViz.
 */
public interface Markup extends List<MarkupElement> {

	/**
	 * Consecutive ItemPredicates in this Markup should be sorted so that result
	 * is concise.
	 *
	 * @param genericObjectLabel
	 *            what you call items, e.g. 'image' or 'work'
	 * @return a merged and generic-substituted Markup
	 */
	public @NonNull Markup compile(@NonNull String genericObjectLabel);

	/**
	 * Destructive. Inserts like this:
	 *
	 * {[element] [comma]}... [connector] [element]
	 *
	 * @param connector
	 *            " and ", " or ", etc
	 */
	@NonNull
	Markup addConnectors(@NonNull MarkupElement connector);

	public boolean add(final @NonNull String s);

	/**
	 * @param _redraw
	 *            callback object when any unknown facet names are read in
	 * @return this Markup rendered as a String
	 */
	public @NonNull String toText(@Nullable RedrawCallback _redraw);

	public void substituteForGenericObjectLabel(@NonNull String genericObjectLabel);

	/**
	 * Destructively underline elements following index satisfying
	 * shouldUnderline(). NO-OP if index<0.
	 */
	public void underline(final int index);

	public List<MarkupElement> subList(int i);

}
