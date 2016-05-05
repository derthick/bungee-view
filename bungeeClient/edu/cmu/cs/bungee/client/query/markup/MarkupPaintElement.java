package edu.cmu.cs.bungee.client.query.markup;

import java.awt.Paint;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class MarkupPaintElement extends DefaultMarkupElement {

	private final @NonNull Paint markupPaint;

	public MarkupPaintElement(final @NonNull Paint paint) {
		super();
		markupPaint = paint;
	}

	@Override
	public String getName() {
		return ""; // markupPaint.toString();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, markupPaint.toString());
	}

	@Override
	public int hashCode() {
		return markupPaint.hashCode();
	}

	@SuppressWarnings({ "null", "unused" })
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final MarkupPaintElement other = (MarkupPaintElement) obj;
		if (markupPaint == null) {
			if (other.markupPaint != null) {
				return false;
			}
		} else if (!markupPaint.equals(other.markupPaint)) {
			return false;
		}
		return true;
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		return this;
	}

	@Override
	public @Nullable MarkupElement merge(final MarkupElement nextElement) {
		MarkupElement result = null;
		if (nextElement instanceof MarkupPaintElement) {
			result = nextElement;
		}
		return result;
	}

	public @NonNull Paint getPaint() {
		return markupPaint;
	}

}
