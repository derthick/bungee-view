package edu.cmu.cs.bungee.client.query;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class InformediaQuery implements Serializable {
	protected static final long serialVersionUID = 1L;
	private final @NonNull String string;
	private final @NonNull String name;

	// Only called by Informedia
	public InformediaQuery(final @NonNull String _name, final @NonNull Item[] items) {
		final List<Item> asList = Arrays.asList(items);
		assert asList != null;
		string = Query.getItemIDs(asList);
		name = _name;
	}

	public String getItems() {
		return string;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, name);
	}
}