package edu.cmu.cs.bungee.piccoloUtils.gui;

import org.eclipse.jdt.annotation.NonNull;

public enum SortDirection {
	@NonNull Z_A("↓"),

	@NonNull A_Z("↑"),

	@NonNull NONE(" ");

	private final @NonNull String label;

	SortDirection(final @NonNull String _label) {
		label = _label;
	}

	public @NonNull String getLabel() {
		return label;
	}

	@NonNull
	SortDirection reverseDirection() {
		if (this == SortDirection.A_Z) {
			return SortDirection.Z_A;
		} else if (this == SortDirection.Z_A) {
			return SortDirection.A_Z;
		} else {
			assert false : this;
			return SortDirection.NONE;
		}
	}
}