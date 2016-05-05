package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class SortButton<E> extends TextButton {

	private static final long serialVersionUID = 1L;

	private final @NonNull E sortField;
	private final @NonNull SortDirection defaultDirection;
	private @NonNull SortDirection direction;
	private final @NonNull SortButtons<E> container;

	public SortButton(final @NonNull E field, final @NonNull SortDirection _defaultDirection, final @NonNull Font font,
			final @NonNull SortButtons<E> _container, final Color textColor, final @Nullable Color bgColor,
			@Nullable final String _mouseDoc) {
		super(SortDirection.NONE.getLabel(), font, 0.0, 0.0, -1.0, -1.0, null,
				_mouseDoc != null ? _mouseDoc : "Sort by this column", true, textColor, bgColor);
		setJustification(Component.CENTER_ALIGNMENT);
		sortField = field;
		defaultDirection = _defaultDirection;
		direction = _defaultDirection;
		container = _container;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, sortField);
	}

	public void setDirection(final @NonNull E field, final @NonNull SortDirection _direction) {
		assert _direction == SortDirection.A_Z || _direction == SortDirection.Z_A : _direction;
		direction = field == sortField ? _direction : SortDirection.NONE;
		try {
			setText(direction.getLabel());
		} catch (final AssertionError e) {
			System.err.println("While SortButton.setDirection " + this + ":\n");
			e.printStackTrace();
		}
	}

	private @NonNull SortDirection getDirection() {
		return direction;
	}

	@Override
	public void doPick() {
		final @NonNull SortDirection oldDirection = getDirection();
		final @NonNull SortDirection _direction = (oldDirection == SortDirection.NONE) ? defaultDirection
				: oldDirection.reverseDirection();
		container.setOrder(sortField, _direction);
	}

}
