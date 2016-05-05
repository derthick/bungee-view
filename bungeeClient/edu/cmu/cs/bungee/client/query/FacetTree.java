/*

 Created on Mar 5, 2005

 Bungee View lets you search, browse, and data-mine an image collection.
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at
 mad@cs.cmu.edu,
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.client.query;

import java.lang.ref.SoftReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.javaExtensions.GenericTree;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;

/**
 * Top level node is an Item. Descendent nodes are the associated facets,
 * arranged in a hierarchy by their parent relationships.
 */
public final class FacetTree extends GenericTree<MarkupElement> {

	private transient static SoftReference<Map<Query, Map<Item, FacetTree>>> FACET_TREES;

	/**
	 * Only called by SelectedItem.itemSetter
	 *
	 * rs is [facet_id, parent_facet_id, name, n_child_facets,
	 * first_child_offset, total_count]
	 *
	 * @param item
	 *            create a FacetTree for item, containing its facets
	 */
	FacetTree(final @NonNull Item item, final @NonNull Query query) {
		super(item, item.getDescription(query));
		try (ResultSet rs = query.getItemInfo(item);) {
			if (query.waitForValidQuery()) {
				int lastFacetTypeID = -1;
				while (rs.next()) {
					final int parentFacetID = rs.getInt(2);
					if (parentFacetID != lastFacetTypeID && query.isTopLevel(parentFacetID)) {
						lastFacetTypeID = parentFacetID;
						final Perspective parentPerspective = query.findPerspectiveOrError(parentFacetID);
						final int row = rs.getRow();
						addChild(new FacetTree(parentPerspective, rs, query));
						rs.absolute(row);
					}
				}
				assert nChildren() > 0 : this + "\n" + MyResultSet.valueOfDeep(rs);
			}
		} catch (final Throwable e) {
			e.printStackTrace();
		}
		lookupFacetTreeTable(query).put(item, this);
	}

	/**
	 * @param rs
	 *            [facet_id, parent_facet_id, name, n_child_facets,
	 *            first_child_offset, total_count]
	 */
	private FacetTree(final Perspective parentPerspective, final ResultSet rs, final Query query) throws SQLException {
		super(parentPerspective.getMarkupElement(), null);
		final int parentID = parentPerspective.getID();
		rs.beforeFirst();
		while (rs.next()) {
			if (rs.getInt(2) == parentID) {
				final int row = rs.getRow();
				final Perspective childPerspective = parentPerspective.ensureChild(rs.getInt(1), rs.getString(3),
						rs.getInt(5), rs.getInt(4));
				// final int totalCount = rs.getInt(6);
				// assert totalCount > 0;
				// childPerspective.setTotalCount(totalCount);
				addChild(new FacetTree(childPerspective, rs, query));
				rs.absolute(row);
			}
		}
	}

	// public boolean isTotalCountCached() {
	// boolean result = true;
	// for (final MarkupElement element : this) {
	// if (element instanceof PerspectiveMarkupElement) {
	// final Perspective perspective = ((PerspectiveMarkupElement)
	// element).perspective;
	// if (!perspective.isTotalCountCached()) {
	// result = false;
	// break;
	// }
	// }
	// }
	// return result;
	// }

	public static @NonNull FacetTree ensureFacetTree(final @NonNull Item item, final @NonNull Query query) {
		FacetTree result = lookupFacetTree(item, query);
		if (result == null) {
			result = new FacetTree(item, query);
		}
		return result;
	}

	public static @Nullable FacetTree lookupFacetTree(final @NonNull Item item, final @NonNull Query query) {
		final Map<Item, FacetTree> facetTreeTable = lookupFacetTreeTable(query);
		final FacetTree facetTree = facetTreeTable.get(item);
		// if (facetTree != null && !facetTree.isTotalCountCached()) {
		// facetTreeTable.remove(item);
		// facetTree = null;
		// }
		return facetTree;
	}

	private static @NonNull Map<Item, FacetTree> lookupFacetTreeTable(final @NonNull Query query) {
		final Map<Query, Map<Item, FacetTree>> facetTreesTable = getFacetTreesTable();
		Map<Item, FacetTree> map = facetTreesTable.get(query);
		if (map == null) {
			map = new Hashtable<>();
			facetTreesTable.put(query, map);
		}
		return map;
	}

	private static @NonNull Map<Query, Map<Item, FacetTree>> getFacetTreesTable() {
		Map<Query, Map<Item, FacetTree>> table = FACET_TREES == null ? null : FACET_TREES.get();
		if (table == null) {
			table = new Hashtable<>();
			FACET_TREES = new SoftReference<>(table);
		}
		return table;
	}

}
