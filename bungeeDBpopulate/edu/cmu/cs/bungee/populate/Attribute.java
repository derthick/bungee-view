package edu.cmu.cs.bungee.populate;

import java.sql.SQLException;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

/**
 * An Attribute maps from a source string to a database update that sets the
 * Field.name column of the item table; item must have a column with this name.
 */
public class Attribute extends Field {

	// static Hashtable<String, Attribute> fieldCache = new Hashtable<String,
	// Attribute>();

	public static Attribute getAttribute(final @NonNull String name1) {
		// Attribute result = fieldCache.get(name1);
		// if (result == null) {
		// result = new Attribute(name1);
		// }
		return new Attribute(name1);
	}

	Attribute(final @NonNull String _column) {
		super(_column);
	}

	@Override
	public boolean insert(@NonNull String value, final PopulateHandler handler) throws SQLException {
		// boolean result = value != null;
		// assert value != null;
		// if (result) {
		final String oldValue = handler.getAttribute(name);
		if (oldValue != null) {
			System.err.println("Attribute.insert: " + oldValue + " += " + value);
			value = oldValue + "\n" + value;
		}
		final boolean result = handler.insertAttribute(this, value);
		// }
		return result;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, name);
	}

}