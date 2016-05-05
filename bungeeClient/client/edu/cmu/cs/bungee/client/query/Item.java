package edu.cmu.cs.bungee.client.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.markup.DefaultMarkupElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public final class Item extends DefaultMarkupElement implements Comparable<Item> {

	private static final @NonNull Map<Integer, Item> ITEM_CACHE = new HashMap<>();

	private final int id;

	/**
	 * concatenated values for all the description fields for this item:
	 *
	 * "\n<field #>\n<field value>..."
	 */
	private String description;

	private final @NonNull Set<Perspective> facets = new HashSet<>();

	private int offset = -1;
	private int offsetQueryVersion = -1;

	public static @NonNull Item ensureItem(final int id) {
		assert id > 0 : id;
		final Integer iD = id;
		Item result = ITEM_CACHE.get(iD);
		if (result == null) {
			result = new Item(id);
			ITEM_CACHE.put(iD, result);
		}
		return result;
	}

	private Item(final int _id) {
		super(false);
		id = _id;
	}

	/**
	 * Can call Database
	 */
	@SuppressWarnings("null")
	public @NonNull String getDescription(final @NonNull Query query) {
		if (description == null) {
			try (final ResultSet rs = query.getDescAndImage(this, -1, -1, -1);) {
				rs.next();
				setDescription(rs.getString(1));
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
		return description;
	}

	public void setDescription(final @NonNull String desc) {
		description = desc.length() == 0 ? toString() : desc;
	}

	public void addFacet(final @NonNull Perspective facet) {
		facets.add(facet);
	}

	public boolean hasFacet(final @NonNull Perspective facet) {
		return getFacets().contains(facet);
	}

	public @NonNull Set<Perspective> getFacets() {
		return facets;
	}

	public int getID() {
		return id;
	}

	@Override
	public String toString() {
		String _offset = "-1";
		try {
			_offset = UtilString.addCommas(facets.size() > 0 ? getOffset(Query.query(facets), 0) : offset);
		} catch (final AssertionError e) {
			// e.printStackTrace();
		}
		final String desc = description == null ? ""
				: " " + (description.length() < 50 ? description : description.substring(0, 47) + " ...").replace('\n',
						'↳');
		return UtilString.toString(this, "ID=" + UtilString.addCommas(id) + " offset=" + _offset + desc);
	}

	@Override
	public int compareTo(final Item arg0) {
		return getID() - arg0.getID();
	}

	@Override
	public @NonNull String getName() {
		return description != null ? description : Util.nonNull(toString());
	}

	/**
	 * Calls Database unless cached.
	 *
	 * @param nNeighbors
	 *            if we need to query DB, might as well cache this many
	 *            neighbors.
	 *
	 * @return query.itemOffset(this), or -1 if query invalid or item doesn't
	 *         satisfy query.
	 */
	public int getOffset(final @NonNull Query query, final int nNeighbors) {
		final int queryVersion = query.version();
		if (offsetQueryVersion != queryVersion) {
			setOffset(queryVersion >= 0 ? query.itemOffset(this, nNeighbors) : -1, queryVersion);
		}
		return offset;
	}

	public void setOffset(final int _offset, final int queryVersion) {
		offset = _offset;
		offsetQueryVersion = queryVersion;
	}

	public static void decacheItems() {
		ITEM_CACHE.clear();
	}

}
