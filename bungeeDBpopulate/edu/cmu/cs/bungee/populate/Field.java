package edu.cmu.cs.bungee.populate;

import java.sql.SQLException;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.compile.Database;

/**
 * A field maps from a source string to a database insert call.
 */
public class Field {
	public final @NonNull String name;

	static final Field IGNORABLE_FIELD = new Field("Ignorable Field");

	Field(final @NonNull String _name) {
		name = _name;
	}

	/**
	 * @return whether anything changed
	 * @throws SQLException
	 */
	@SuppressWarnings({ "unused", "static-method" })
	public boolean insert(final @NonNull String value, final PopulateHandler handler) throws SQLException {
		assert false : value;
		return false;
	}

	/**
	 * After readData completes, this is called for each inserted field.
	 *
	 * @throws SQLException
	 */
	void cleanUp(@SuppressWarnings("unused") final Database db) throws SQLException {
		// override this
	}
}
