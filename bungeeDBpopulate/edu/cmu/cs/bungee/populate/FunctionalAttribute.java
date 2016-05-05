package edu.cmu.cs.bungee.populate;

import java.sql.SQLException;

import org.eclipse.jdt.annotation.NonNull;

public class FunctionalAttribute extends Attribute {
	private final boolean noWarn;

	public static FunctionalAttribute getAttribute(final @NonNull String name, final boolean _noWarn) {
		// FunctionalAttribute result = (FunctionalAttribute)
		// fieldCache.get(name);
		// if (result == null) {
		// result = new FunctionalAttribute(name, _noWarn);
		// }
		return new FunctionalAttribute(name, _noWarn);
	}

	private FunctionalAttribute(final @NonNull String _column, final boolean _noWarn) {
		super(_column);
		noWarn = _noWarn;
	}

	@Override
	public boolean insert(final @NonNull String value, final PopulateHandler handler) throws SQLException {
		assert value != null;
		final String oldValue = handler.getAttribute(name);
		if (oldValue != null) {
			if (!noWarn && !value.equals(oldValue)) {
				System.err.println("Multiple values for " + name + ": " + oldValue + " " + value);
			}

			// If there's a clash, go with the first
			// value. This is the right thing for
			// multiple MARC 856 fields, at least,
			// according to Caroline Arms.
			return false;
		}
		return handler.insertAttribute(this, value);
	}
}