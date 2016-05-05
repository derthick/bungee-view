package edu.cmu.cs.bungee.client.query.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.servlet.FetchType;

public class EditableQuery extends Query {

	EditableQuery(final @NonNull ServletInterface _db) {
		super(_db);
	}

	@Override
	public boolean isEditable() {
		// System.out.println("EditableQuery.isEditable ⇒ true");
		return true;
	}

	/**
	 * @param result
	 *            refresh facet counts after an *EDIT* operation
	 */
	private void refetchAfterEdit(final SortedSet<Perspective> result) {
		// System.out.println("refetch " + facet);
		// assert isEditable;
		// extendAllPerspectives(facet.childrenOffset() + facet.nChildren());
		if (result.size() > 0) {
			for (final Perspective p : result) {
				p.resetForNewData();
			}
			synchronized (prefetchingLock) {
				prefetch(result, FetchType.PREFETCH_FACET_WITH_NAME, true);
			}
		}
	}

	public Collection<Perspective> addItemFacet(final Perspective facet, final Item item) {
		return updateIDnCount(db.addItemFacet(facet.getID(), item.getID()), null);
	}

	public Collection<Perspective> addItemsFacet(final Perspective facet) {
		return updateIDnCount(db.addItemsFacet(facet.getID(), onItemsTable()), null);
	}

	public Collection<Perspective> removeItemsFacet(final Perspective facet) {
		return updateIDnCount(db.removeItemsFacet(facet.getID(), onItemsTable()), null);
	}

	public Collection<Perspective> addChildFacet(final Perspective parent, final @NonNull String facetName) {
		if (findPerspective(facetName, parent) != null) {
			throw new IllegalArgumentException(facetName + " is already a child of " + parent);
		}
		final int parent_id = parent == null ? 0 : parent.getID();
		if (parent != null) {
			parent.incfChildren(1);
		}
		return updateIDnCount(db.addChildFacet(parent_id, facetName), parent);
	}

	public Collection<Perspective> removeItemFacet(final Perspective facet, final Item item) {
		return updateIDnCount(db.removeItemFacet(facet.getID(), item.getID()), null);
	}

	public Collection<Perspective> reparent(final Perspective parent, final Perspective child) {
		if (findPerspective(child.getName(), parent) != null) {
			throw new IllegalArgumentException(parent + " already has a child named like " + child);
		}
		parent.reparent(child);
		return updateIDnCount(db.reparent(parent.getID(), child.getID()), null);
	}

	@Override
	public void exit() {
		writeback();
		super.exit();
	}

	public void writeback() {
		db.writeback();
	}

	public void revert(final @NonNull String date) {
		db.revert(date);
	}

	/**
	 * @param rs
	 *            [newID, oldID, count, offset, parent_facet_id] for all facets
	 *            whose values for any of these attributes might have changed
	 * @param parentOfCreatedFacet
	 *            (only non-null for addChildFacet) Only create new facets for
	 *            children of this parent (or if new parent_id == 0)
	 * @return displayed perspectives to redisplay
	 */
	private SortedSet<Perspective> updateIDnCount(final ResultSet rs, final Perspective parentOfCreatedFacet) {
		// assert isEditable();
		// printUpdateIDnCountArgs(nameOfCreatedFacet, parentOfCreatedFacet);

		final SortedSet<Perspective> result = new TreeSet<>();
		try {
			while (rs.next()) {
				final int newID = rs.getInt(1);
				final int oldID = rs.getInt(2);
				final int totalCount = rs.getInt(3);
				final int offset = rs.getInt(4);
				final int parent_facet_id = rs.getInt(5);

				// extendAllPerspectives(Math.max(oldID, newID));
				final Perspective p = findPerspectiveIfCached(oldID);
				final Perspective parent = parent_facet_id == 0 ? null : findPerspectiveIfCached(parent_facet_id);
				if (parent != null) {
					if (parent.isDisplayed()
							// if it's not used, maxTotalCount == -1 and this
							// will
							// blow
							&& totalCount > parent.getMaxChildTotalCount()) {

						// System.out.println("updateIDnCount setting max child
						// count to "
						// + cnt + " (" + p + ")");

						parent.setMaxChildTotalCount(totalCount);
					}
					parent.decacheLettersOffsets();
				}

				// printUpdateIDnCountRow(nameOfCreatedFacet,
				// parentOfCreatedFacet, newID, oldID, cnt, offset,
				// parent_facet_id, p, parent);

				if (p != null) {
					final Perspective forResult = updateIDnCountPrefetched(newID, oldID, totalCount, offset, p);
					if (forResult != null) {
						result.add(forResult);
					}
				}

				// Dead Code
				// else if (nameOfCreatedFacet != null
				// && parent == parentOfCreatedFacet) {
				// updateIDnCountNonPrefetched(nameOfCreatedFacet, newID, cnt,
				// offset, parent_facet_id, parent);
				// }

			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}
		// for (final Perspective p : result) {
		// Do this after all the renames
		refetchAfterEdit(result);
		// }
		return result;
	}

	private Perspective updateIDnCountPrefetched(final int newID, final int oldID, final int totalCount,
			final int offset, final Perspective p) {
		cachePerspective(oldID, null);
		cachePerspective(newID, p);
		return p.updateIDnCountPrefetched(newID, totalCount, offset);
	}

	public void rotate(final Item item, final int degrees) {
		db.rotate(item.getID(), degrees);
	}

	public void rename(final Perspective p, final @NonNull String newName) {
		db.rename(p.getID(), newName);
	}

}
