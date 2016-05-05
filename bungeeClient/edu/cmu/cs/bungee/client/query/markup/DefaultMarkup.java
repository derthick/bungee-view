package edu.cmu.cs.bungee.client.query.markup;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

// Implementing the interface List<MarkupElement> requires all sorts of methods that the class LinkedList provides.
public class DefaultMarkup extends LinkedList<MarkupElement> implements Markup {

	static final @NonNull MarkupElement EXCLUDED_COLOR = new MarkupPaintElement(BungeeConstants.EXCLUDED_COLOR);

	public static final @NonNull MarkupElement FILTER_CONSTANT_MATCHES = MarkupStringElement.getElement("Matches");
	static final @NonNull MarkupElement FILTER_CONSTANT_FROM = MarkupStringElement.getElement(" from ");
	public static final @NonNull MarkupElement FILTER_CONSTANT_GRID_FG = new MarkupPaintElement(
			BungeeConstants.GRID_FG_COLOR);
	public static final @NonNull MarkupElement FILTER_CONSTANT_ARROW_KEYS = MarkupStringElement
			.getElement("Arrow keys will move through: ");
	public static final @NonNull MarkupElement FILTER_CONSTANT_RIGHT_ARROW = MarkupStringElement.getElement(" → ");

	public static final @NonNull MarkupElement FILTER_CONSTANT_UNDEFINED = MarkupStringElement
			.getElement("<undefined>");
	public static final @NonNull MarkupElement FILTER_CONSTANT_MEDIAN = MarkupStringElement.getElement(" median: ");
	static final @NonNull MarkupElement FILTER_TYPE_OR = MarkupStringElement.getElement(" or ");
	/**
	 * insert 'images' or 'works' or whatever, as specified in
	 * globals.genericObjectLabel
	 */
	static final @NonNull MarkupElement GENERIC_OBJECT_LABEL = MarkupStringElement.getElement("Generic Object Label");
	public static final @NonNull MarkupElement FILTER_COLOR_WHITE = new MarkupPaintElement(UtilColor.WHITE);
	public static final @NonNull MarkupElement FILTER_CONSTANT_THE = MarkupStringElement.getElement(": the ");
	public static final @NonNull MarkupElement FILTER_CONSTANT_VIEWING = MarkupStringElement.getElement("viewing ");

	public static final char FILTER_TYPE_SUBSTITUTE = '~';
	static final @NonNull MarkupElement FILTER_TYPE_BUT_NOT = MarkupStringElement.getElement(" (but not ");
	static final @NonNull MarkupElement FILTER_TYPE_CLOSE = MarkupStringElement.getElement(") ");
	static final @NonNull MarkupElement FILTER_CONSTANT_MENTIONS = MarkupStringElement
			.getElement(" whose description mentions '");

	private static final @NonNull MarkupElement CONNECTOR_COMMA = MarkupStringElement.getElement(", ");
	static final @NonNull public MarkupElement CONNECTOR_AND = MarkupStringElement.getElement(" and ");
	static final @NonNull public MarkupElement CONNECTOR_OR = MarkupStringElement.getElement(" or ");

	private static final List<MarkupElement> CONNECTORS = Arrays.asList(CONNECTOR_COMMA, CONNECTOR_AND, CONNECTOR_OR);

	static boolean isConnector(final MarkupElement markupElement) {
		return CONNECTORS.contains(markupElement);
	}

	static final @NonNull MarkupElement CONNECTOR_DASH = MarkupStringElement.getElement(" — ");
	static final @NonNull MarkupElement CONNECTOR_LESS_THAN = MarkupStringElement.getElement("at most ");
	static final @NonNull MarkupElement CONNECTOR_GREATER_THAN = MarkupStringElement.getElement("at least ");
	static final @NonNull MarkupElement CONNECTOR_ANY = MarkupStringElement.getElement("any ");

	/**
	 * Add an 's' to the next token
	 */
	public static final @NonNull MarkupElement PLURAL_TAG = new MarkupTag('s');

	/**
	 * insert a newline
	 */
	public static final @NonNull MarkupElement NEWLINE_TAG = new MarkupTag('\n');

	/**
	 * underline subsequent tokens
	 */
	public static final @NonNull MarkupElement UNDERLINE_TAG = new MarkupTag('u');

	/**
	 * Don't underline subsequent tokens
	 */
	public static final @NonNull MarkupElement NO_UNDERLINE_TAG = new MarkupTag('n');

	/**
	 * render subsequent tokens in the default color
	 */
	public static final @NonNull MarkupElement DEFAULT_COLOR_TAG = new MarkupTag('c');

	/**
	 * render subsequent tokens in the default text style
	 */
	public static final @NonNull MarkupElement DEFAULT_STYLE_TAG = new MarkupTag('b');

	/**
	 * render subsequent tokens in italics
	 */
	public static final @NonNull MarkupElement ITALIC_STRING_TAG = new MarkupTag('i');

	/**
	 * @return a Markup with no elements
	 */
	public static @NonNull Markup emptyMarkup() {
		return new DefaultMarkup();
	}

	public static @NonNull Markup newMarkup(final MarkupElement... elements) {
		final Markup result = emptyMarkup();
		result.addAll(Arrays.asList(elements));
		return result;
	}

	public static @NonNull Markup newMarkup(final @NonNull Collection<? extends MarkupElement> elements) {
		final Markup result = emptyMarkup();
		result.addAll(elements);
		// assert ((DefaultMarkup) result).ensureNoEmptyStrings();
		return result;
	}

	protected DefaultMarkup() {
		super();
		// force use of emptyMarkup
	}

	@Override
	public @NonNull Markup compile(final @NonNull String genericObjectLabel) {
		substituteForGenericObjectLabel(genericObjectLabel);
		final Markup markup = emptyMarkup();
		MarkupElement prev = null;
		for (MarkupElement markupElement : this) {
			if (markupElement instanceof Perspective) {
				markupElement = ((Perspective) markupElement).getMarkupElement();
			}
			assert markupElement != null : this;
			final MarkupElement merge = (prev != null ? prev.merge(markupElement) : null);
			if (merge != null) {
				markup.set(markup.size() - 1, merge);
				prev = merge;
			} else {
				markup.add(markupElement);
				prev = markupElement;
			}
		}
		return markup;
	}

	@Override
	public void substituteForGenericObjectLabel(final @NonNull String genericObjectLabel) {
		Collections.replaceAll(this, DefaultMarkup.GENERIC_OBJECT_LABEL,
				MarkupStringElement.getElement(genericObjectLabel));
	}

	@Override
	public @NonNull Markup addConnectors(final @NonNull MarkupElement connector) {
		assert !contains(connector) && !contains(UtilString.pluralize(connector.getName())) : connector + " already in "
				+ this;
		for (final ListIterator<MarkupElement> it = listIterator(); it.hasNext();) {
			if (!it.hasPrevious()) {
				// nothing to add
			} else if (it.nextIndex() + 1 == size()) {
				it.add(connector);
			} else {
				it.add(CONNECTOR_COMMA);
			}
			it.next();
		}
		return this;
	}

	/*
	 * Always call compile before this, to merge and interpret tags.
	 */
	@Override
	public @NonNull String toText(final @Nullable RedrawCallback _redraw) {
		final StringBuilder buf = new StringBuilder();
		for (final MarkupElement element : this) {
			final String name = element.getName(_redraw);
			buf.append(name);
		}
		return UtilString.bufToString(buf);
	}

	@Override
	public void underline(final int index) {
		if (index >= 0) {
			boolean isUnderline = false;
			for (final ListIterator<MarkupElement> it = listIterator(index); it.hasNext();) {
				final MarkupElement element = it.next();
				if (element.shouldUnderline() != isUnderline) {
					isUnderline = !isUnderline;
					it.previous();
					it.add(isUnderline ? UNDERLINE_TAG : NO_UNDERLINE_TAG);
					it.next();
				}
			}
		}
	}

	@Override
	public boolean add(final @NonNull String s) {
		return add(MarkupStringElement.getElement(s));
	}

	@Override
	public List<MarkupElement> subList(final int i) {
		return subList(i, size());
	}

}
