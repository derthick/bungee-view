/*

 Created on Mar 8, 2005

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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import JSci.maths.statistics.ChiSq2x2;
import JSci.maths.statistics.ChiSqParams;
import edu.cmu.cs.bungee.client.query.markup.DescriptionPreposition;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.query.markup.Restrictions;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.query.query.Query.DescriptionCategory;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants.Significance;
import edu.cmu.cs.bungee.compile.Compile;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.javaExtensions.comparator.IntValueComparator;
import edu.cmu.cs.bungee.servlet.FetchType;
import jdk.nashorn.internal.ir.annotations.Immutable;
import uk.org.bobulous.java.intervals.Enumerable;
import uk.org.bobulous.java.intervals.GenericInterval;
import uk.org.bobulous.java.intervals.Interval;

/**
 * aka Facet. a property that an Item can have.
 */
/**
 *
 */
public class Perspective implements ItemPredicate, Enumerable<Perspective> {

	public static final double ODDS_RANGE = 100.0;
	public static final double LOG_ODDS_RANGE = Math.log(ODDS_RANGE);
	public static final double INVERSE_ODDS_RANGE = 1.0 / ODDS_RANGE;

	private static final int FETCH_TYPE_INCREMENT_FOR_MANY_CHILDREN = +1;
	/**
	 * Only the database pays attention to this
	 */
	private static final int FETCH_TYPE_INCREMENT_FOR_RESTRICTED_DATA = +4;

	/**
	 * Perspective for the more general facet or facet_type, Only changes if
	 * isEditable
	 */
	@Nullable
	Perspective parent;

	/**
	 * Only changes if isEditable
	 */
	private int facet_id;
	protected String name;
	/**
	 * Should only change if is {@link Query#isEditable}
	 */
	int nChildrenRaw = 0;

	/**
	 * Used to determine whether {@link #pValueChiSq2x2} is up to date. Never
	 * negative.
	 */
	private int pValueQueryVersion = Integer.MAX_VALUE;

	private @NonNull ChiSq2x2 pValueChiSq2x2 = ChiSq2x2.UNINFORMATIVE_CHI_SQ_2X2;

	/**
	 * The SUM of the totalCounts for us and our previous siblings. Used only by
	 * LetterLabeled. MAYBE GET RID OF IT.
	 */
	private int cumTotalCount;
	private final InstantiatedPerspective instantiatedPerspective;
	private PerspectiveMarkupElement[] markups;
	private final @NonNull Query query;
	protected int whichChildRaw;

	/**
	 * Only called by DummyPerspective.<init>
	 */
	public Perspective(final @NonNull Perspective _parent) {
		parent = _parent;
		query = parent.query();
		instantiatedPerspective = null;
	}

	/**
	 * Only called by ensureChild()
	 */
	private Perspective(final int _facet_id, final @NonNull Perspective _parent, final @Nullable String name1,
			final int children_offset, final int n_children) {
		assert _facet_id > 0 : _facet_id;
		assert _parent.getChildrenOffset() > 0 : _parent;
		assert _parent.query().findPerspectiveIfCached(_facet_id) == null : _parent + " " + name1;

		facet_id = _facet_id;
		parent = _parent;
		query = parent.query();
		whichChildRaw = facet_id - _parent.getChildrenOffset() - 1;
		name = name1;
		nChildrenRaw = n_children;
		instantiatedPerspective = nChildrenRaw > 0 ? new InstantiatedPerspective(this, _parent, children_offset) : null;
		query().cachePerspective(facet_id, this);
	}

	/**
	 * Only called by initFacetTypes().
	 */
	public Perspective(final int _facet_id, final @NonNull String _name, final int _children_offset,
			final int n_children, final @NonNull DescriptionCategory _descriptionCategory,
			final @NonNull String _descriptionPreposition, final @NonNull Query _q, final int flags) {
		query = _q;
		parent = null;
		facet_id = _facet_id;
		whichChildRaw = facet_id - 1;
		name = _name;
		nChildrenRaw = n_children;
		instantiatedPerspective = nChildrenRaw > 0
				? new InstantiatedPerspective(this, _children_offset, _descriptionCategory, _descriptionPreposition)
				: null;
		setIsAlphabetic(UtilMath.isBit(flags, 0));
		instantiatedPerspective.isOrdered = UtilMath.isBit(flags, 1);
		query().cachePerspective(facet_id, this);
	}

	/*************************************************************************
	 * All Markup stuff is offloaded to PerspectiveMarkupElement
	 *************************************************************************/

	public @NonNull Markup defaultClickDesc() {
		return getMarkupElement().defaultClickDesc();
	}

	/**
	 * @return singular, positive, MarkupElement.
	 */
	public @NonNull PerspectiveMarkupElement getMarkupElement() {
		return getMarkupElement(false, false);
	}

	public @NonNull PerspectiveMarkupElement getMarkupElement(final boolean isPlural, final boolean negated) {
		int index = 0;
		if (isPlural) {
			index += 1;
		}
		if (negated) {
			index += 2;
		}
		if (markups == null) {
			markups = new PerspectiveMarkupElement[4];
		}
		PerspectiveMarkupElement result = markups[index];
		if (result == null) {
			result = new PerspectiveMarkupElement(this, isPlural, negated);
			markups[index] = result;
		}
		return result;
	}

	/**
	 * @return descriptionPreposition +/- patterns substituted with +/-
	 *         restrictions, rendered as "+, +, or + (but not -, -, or -)".
	 */
	public @NonNull Markup getPhrase() {
		return getMarkupElement().getPhraseFromRestrictions(getDescriptionPreposition(), getDescriptionCategory());
	}

	/**
	 * Pattern into which facet name is substituted for '~'. Default is
	 * implicitly [descriptionPreposition ~, NOT descriptionPreposition ~]. Used
	 * to generate query description.
	 */
	public @NonNull DescriptionPreposition getDescriptionPreposition() {
		return isInstantiated() ? instantiatedPerspective.descriptionPreposition
				: Util.nonNull(parent).instantiatedPerspective.descriptionPreposition;
	}

	/**
	 * OBJECT, META, or CONTENT. Used to generate query description.
	 */
	public @NonNull DescriptionCategory getDescriptionCategory() {
		return isInstantiated() ? instantiatedPerspective.descriptionCategory
				: Util.nonNull(parent).instantiatedPerspective.descriptionCategory;
	}

	public int nDescendentRestrictions() {
		return getMarkupElement().descendentRestrictions().nRestrictions();
	}

	/*************************************************************************
	 * End of Markup stuff
	 *************************************************************************/

	public void setTotalCount(final int count) {
		query.setFacetTotalCount(this, count);
	}

	public @NonNull @Immutable List<Perspective> getChildrenRaw() {
		return instantiatedPerspective.getChildrenRaw();
	}

	/**
	 * Only called by MexPerspectives.merge()
	 *
	 * @param minChild
	 * @param maxChild
	 * @return all children in this range, inclusive
	 */
	public @NonNull List<Perspective> getChildren(final @NonNull Perspective minChild,
			final @NonNull Perspective maxChild) {
		return instantiatedPerspective.getChildrenRaw(minChild.whichChildRaw(), maxChild.whichChildRaw());
	}

	public static final @NonNull TotalCountDescendingComparator TOTAL_COUNT_DESCENDING_COMPARATOR = new TotalCountDescendingComparator();

	static final class TotalCountDescendingComparator extends IntValueComparator<Perspective> {

		@Override
		public int value(final Perspective data) {
			return data.getTotalCount();
		}
	}

	/**
	 * @return number of ancestor Perspectives. 0 means no parent.
	 */
	int depth() {
		return parent != null ? parent.depth() + 1 : 0;
	}

	/**
	 * @return ancestors (not including this perspective)
	 */
	public @NonNull Set<Perspective> ancestors() {
		Set<Perspective> result;
		if (parent != null) {
			result = parent.ancestors();
			result.add(parent);
		} else {
			result = new HashSet<>();
		}
		return result;
	}

	/**
	 * @return the next more general Perspective, e.g. 2007 ⇒ 21st century. For
	 *         top level Perspectives, returns NULL rather than query!
	 */
	public @Nullable Perspective getParent() {
		return parent;
	}

	public @NonNull ItemPredicate getParentOrQuery() {
		return parent != null ? parent : query();
	}

	public @Nullable Perspective getGrandParent() {
		return parent != null ? Util.nonNull(parent).parent : null;
	}

	/**
	 * @return is ancestor this perspective or an ancestor of this perspective?
	 */
	public boolean hasAncestor(final @NonNull Perspective ancestor) {
		return (ancestor == this) || (parent != null && parent.hasAncestor(ancestor));
	}

	public boolean isUnderline() {
		return getParent() != null;
	}

	/**
	 * @return next sibling in sort order, or null if this is the last child.
	 *
	 *         There's no reason that next sibling can be findPerspective'd. Our
	 *         only refence to it is facet_id + 1.
	 */
	@Override
	public @Nullable Perspective next() {
		final int n = whichChildRaw() + 1;
		if (parent != null) {
			final @NonNull Perspective _parent = Util.nonNull(parent);
			return n < _parent.nChildrenRaw() ? _parent.getRawNthChild(n) : null;
		} else if (n < query.nPerspectivesRaw()) {
			return query.findPerspectiveOrError(n);
		} else {
			return null;
		}
	}

	@Override
	/**
	 * @return previous Perspective, or null if this is the first child
	 */
	public @Nullable Perspective previous() {
		final int n = whichChildRaw() - 1;
		if (n < 0) {
			return null;
		} else if (parent != null) {
			return Util.nonNull(parent).getRawNthChild(n);
		} else {
			return query.findPerspectiveOrError(n);
		}
	}

	public int getID() {
		return facet_id;
	}

	public @NonNull String getServerID() {
		final String result = Integer.toString(facet_id);
		assert result != null;
		return result;
	}

	/**
	 * @return number of child facets
	 */
	public int nChildrenRaw() {
		return nChildrenRaw;
	}

	/**
	 * @return do any of my child facets have non-zero total count?
	 */
	@Override
	public boolean isEffectiveChildren() {
		return nEffectiveChildren() > 0;
	}

	/**
	 * @return number of child facets with totalCount>0;
	 */
	int nEffectiveChildren() {
		if (!query.isRestrictedData()) {
			return nChildrenRaw();
		} else if (getTotalChildTotalCount() == 0) {
			return 0;
		} else {
			int result = 0;
			for (final Perspective child : getChildrenRaw()) {
				if (child != null && child.getTotalCount() > 0) {
					result++;
				}
			}
			return result;
		}
	}

	private int getChildrenOffset() {
		return instantiatedPerspective.children_offset;
	}

	/**
	 * Called from thread prefetcher.
	 */
	public @NonNull FetchType getFetchType() {
		assert nChildrenRaw() > 0 : this
				+ " doesn't have any tags. You should give it a negative value for sort in the raw_facet_types table.";

		FetchType fetchType = FetchType.PREFETCH_FACET_WITH_NAME;
		if (query().isRestrictedData()) {
			// The database treats this case specially, but
			// initAfterPrefetch() resets it via
			// ensureFetchTypeForUnrestrictedData().
			fetchType = fetchTypeForRestrictedData(fetchType);
		} else if (parent == null) {
			fetchType = FetchType.PREFETCH_FACET_TYPE_WITH_NAME;
		}
		if (nChildrenRaw() > BungeeConstants.PREFETCH_NAMES_MAX_CHILDREN) {
			fetchType = fetchTypeForManyChildren(fetchType);
		}
		return fetchType;
	}

	/**
	 * Add FETCH_TYPE_INCREMENT_FOR_MANY_CHILDREN
	 */
	private static @NonNull FetchType fetchTypeForManyChildren(final @NonNull FetchType fetchTypeForFewChildren) {
		assert fetchTypeForFewChildren.isName() : fetchTypeForFewChildren;
		final FetchType result = FetchType.values()[fetchTypeForFewChildren.ordinal()
				+ FETCH_TYPE_INCREMENT_FOR_MANY_CHILDREN];
		assert result != null;
		return result;
	}

	/**
	 * Subtract FETCH_TYPE_INCREMENT_FOR_MANY_CHILDREN
	 */
	private static @NonNull FetchType fetchTypeForFewChildren(final @NonNull FetchType fetchTypeForManyChildren) {
		assert !fetchTypeForManyChildren.isName() : fetchTypeForManyChildren;
		final FetchType result = FetchType.values()[fetchTypeForManyChildren.ordinal()
				- FETCH_TYPE_INCREMENT_FOR_MANY_CHILDREN];
		assert result != null;
		return result;
	}

	/**
	 * Add FETCH_TYPE_INCREMENT_FOR_RESTRICTED_DATA
	 */
	private static @NonNull FetchType fetchTypeForRestrictedData(
			final @NonNull FetchType fetchTypeForUnrestrictedData) {
		final int ordinal = fetchTypeForUnrestrictedData.ordinal();
		assert ordinal < FETCH_TYPE_INCREMENT_FOR_RESTRICTED_DATA : ordinal;
		final FetchType result = FetchType.values()[ordinal + FETCH_TYPE_INCREMENT_FOR_RESTRICTED_DATA];
		assert result != null;
		return result;
	}

	/**
	 * If query.isRestricted() subtract
	 * FETCH_TYPE_INCREMENT_FOR_RESTRICTED_DATA.
	 */
	static @NonNull public FetchType ensureFetchTypeForUnrestrictedData(final @NonNull FetchType fetchType) {
		FetchType result = fetchType;
		final int ordinal = fetchType.ordinal();
		if (ordinal >= FETCH_TYPE_INCREMENT_FOR_RESTRICTED_DATA) {
			result = FetchType.values()[ordinal - FETCH_TYPE_INCREMENT_FOR_RESTRICTED_DATA];
		}
		assert result != null;
		return result;
	}

	/**
	 * Only called by Query.prefetch()
	 */
	public void initAfterPrefetch(final @NonNull ResultSet parentRS, final @NonNull ResultSet childRS,
			final @NonNull FetchType fetchType) throws SQLException {
		parentRS.next();
		final int childrenOffset = parentRS.getInt("first_child_offset");

		// testing
		assert childrenOffset < 0 || getChildrenOffset() == childrenOffset : path(true, true) + " "
				+ getChildrenOffset() + " " + childrenOffset + "\n" + MyResultSet.valueOfDeep(parentRS);

		setChildrenOffset(childrenOffset);
		setIsAlphabetic(UtilMath.isBit(parentRS.getInt("is_alphabetic"), 0));

		final boolean isName = fetchType.isName();
		if (nChildrenRaw > 0) {
			int child_cumTotalCount = 0;
			int maxCount = -1;
			for (int childID = childrenOffset + 1; childID <= childrenOffset + nChildrenRaw; childID++) {
				childRS.next();
				final int childNChildren = childRS.getInt("n_child_facets");
				final int childChildrenOffset = childRS.getInt("first_child_offset");
				final String childName = isName ? childRS.getString("name") : null;
				final Perspective child = ensureChild(childID, childName, childChildrenOffset, childNChildren);
				final int childTotalCount = child.getTotalCount();
				child_cumTotalCount += childTotalCount;
				child.cumTotalCount = child_cumTotalCount;
				maxCount = Math.max(childTotalCount, maxCount);
			}
			assert !getChildrenRaw().contains(null) : this;
			setTotalChildTotalCount(child_cumTotalCount);
			setMaxChildTotalCount(maxCount);
		}
		assert isInstantiated() == nChildrenRaw > 0 : this + " " + nChildrenRaw;
		setPrefetchedStatus(isName ? PrefetchStatus.PREFETCHED_YES : PrefetchStatus.PREFETCHED_NO_NAMES);
	}

	public void setPrefetchedStatus(final @NonNull PrefetchStatus status) {
		if (isInstantiated()) {
			instantiatedPerspective.prefetched = status;
		} else {
			assert status == PrefetchStatus.PREFETCHED_NO;
		}
	}

	public @NonNull Perspective ensureChild(final int childID, final @Nullable String childName,
			final int child_children_offset, final int child_n_children) {
		assert childID > facet_id : childID;
		assert child_n_children >= 0 : child_n_children;
		assert child_children_offset >= 0 : child_children_offset;
		Perspective child = query().findPerspectiveIfCached(childID);

		if (child == null) {
			child = new Perspective(childID, this, childName, child_children_offset, child_n_children);
			addChildFacet(child);
		} else {
			if (childName != null) {
				child.setName(childName);
			}
			if (child_n_children >= 0) {
				child.setNchildren(child_n_children, child_children_offset);
			}
		}
		assert assertIsChild(child);
		return child;
	}

	public boolean isPrefetchedToDefaultLevel() {
		return isPrefetched(prefetchedStatusFromFetchType(getFetchType()));
	}

	/**
	 * @return PREFETCHED_NO_NAMES or PREFETCHED_YES: what prefetchStatus()
	 *         would return after prefetching with fetchType.
	 */
	public static @NonNull PrefetchStatus prefetchedStatusFromFetchType(final @NonNull FetchType fetchType) {
		return fetchType.isName() ? PrefetchStatus.PREFETCHED_YES : PrefetchStatus.PREFETCHED_NO_NAMES;
	}

	/**
	 * @return whether this is already prefetched to level. If not, but
	 *         isDisplayed(), calls queuePrefetch.
	 */
	public boolean ensurePrefetched(final @NonNull PrefetchStatus level, final @Nullable RedrawCallback callback) {
		// final @NonNull PrefetchStatus level = PrefetchStatus.PREFETCHED_YES;
		final boolean result = isPrefetched(level);
		if (!result && isDisplayed()) {
			query.queuePrefetch(this, getPrefetchTypeForLevel(level), callback);
		}
		return result;
	}

	/**
	 * @return whether prefetchStatus >= level
	 */
	public boolean isPrefetched(final @NonNull PrefetchStatus level) {
		assert level == PrefetchStatus.PREFETCHED_NO_NAMES || level == PrefetchStatus.PREFETCHED_YES;
		return prefetchStatus().compareTo(level) >= 0;
	}

	private @NonNull FetchType getPrefetchTypeForLevel(final @NonNull PrefetchStatus level) {
		FetchType fetchType = getFetchType();

		// If ensurePrefetched() is explicitly asked for
		// PrefetchStatus.PREFETCHED_YES, override default getFetchType()
		if (level == PrefetchStatus.PREFETCHED_YES && nChildrenRaw() > BungeeConstants.PREFETCH_NAMES_MAX_CHILDREN) {
			// Undo the fetchTypeForManyChildren() call in getFetchType()
			fetchType = fetchTypeForFewChildren(fetchType);
		}
		return fetchType;
	}

	public boolean isInstantiated() {
		return instantiatedPerspective != null;
	}

	public boolean needsChildrenOffset() {
		return nChildrenRaw() > 0 && (!isInstantiated() || getChildrenOffset() < 0);
	}

	/**
	 * Called by restrictData() and when editing.
	 *
	 * Children may be changing, so invalidate lettersOffsetsMap.
	 */
	public void decacheLettersOffsets() {
		if (isInstantiated()) {
			instantiatedPerspective.decacheLettersOffsets();
		}
	}

	/**
	 * @return whether there is a row of bars for this Perspective's children.
	 */
	public boolean isDisplayed() {
		return instantiatedPerspective != null && instantiatedPerspective.isDisplayed;
	}

	public void setIsDisplayed(final boolean isDisplayed) {
		instantiatedPerspective.isDisplayed = isDisplayed;
	}

	/**
	 * @return Whether children's names are ordered by facet_id. The interface
	 *         will only zoom by prefixes if so.
	 */
	public boolean isAlphabetic() {
		if (isInstantiated()) {
			return instantiatedPerspective.isAlphabetic;
		} else {
			assert parent != null;
			return parent.isAlphabetic();
		}
	}

	public void setIsAlphabetic(final boolean _isAlphabetic) {
		if (isInstantiated()) {
			instantiatedPerspective.isAlphabetic = _isAlphabetic;
		} else {
			assert parent != null && parent.isAlphabetic() == _isAlphabetic;
		}
	}

	/**
	 * @return does this Perspective have a natural ordering (like Date or
	 *         Rating)?
	 */
	public boolean isOrdered() {
		if (isInstantiated()) {
			return instantiatedPerspective.isOrdered;
		} else {
			assert parent != null;
			return parent.isOrdered();
		}
	}

	public @NonNull PrefetchStatus prefetchStatus() {
		return isInstantiated() ? instantiatedPerspective.prefetched : PrefetchStatus.PREFETCHED_NO;
	}

	/**
	 * Only called when editing.
	 */
	public void resetForNewData() {
		instantiatedPerspective.resetChildrensOnCounts();
		setOnCount(0);
	}

	/**
	 * @return always >= 0
	 */
	public int getOnCount() {
		return query.getFacetOnCount(this);
	}

	public void setOnCount(final int onCount) {
		query.setFacetOnCount(this, onCount);
	}

	/**
	 * @return onCount of parent, or if isTopLevel() query().getOnCount().
	 *         always >= 0
	 */
	public int parentOnCount() {
		return parent != null ? parent.getOnCount() : query().getOnCount();
	}

	/**
	 * @return totalCount of parent, or if isTopLevel() query().getTotalCount().
	 *         always >= 0
	 */
	public int parentTotalCount() {
		int result;
		if (parent == null) {
			result = query().getTotalCount();
		} else {
			assert parent != null;
			result = parent.getTotalCount();
		}
		return result;
	}

	/**
	 * @return always >= 0
	 */
	@Override
	public int getTotalCount() {
		return query.getFacetTotalCount(this);
	}

	/**
	 * @return The SUM of the totalCounts for us and our previous siblings. Used
	 *         for placing bars. always >= 0
	 */
	public int cumTotalCountInclusive() {
		return cumTotalCount;
	}

	/**
	 * @return The SUM of the totalCounts for our previous siblings. This
	 *         Perspective's first item, if any, would be 1 greater. Used for
	 *         placing bars. always >= 0
	 */
	public int cumTotalCountExclusive() {
		return cumTotalCount - getTotalCount();
	}

	/**
	 * @return index between 0 and nChildren-1
	 */
	public int whichChildRaw() {
		return whichChildRaw;
	}

	/**
	 * @param n
	 *            index into children perspepctives (between 0 and nChildren -
	 *            1)
	 * @return the nth child facet
	 */
	public @NonNull Perspective getRawNthChild(final int n) {
		return instantiatedPerspective.getRawNthChild(n);
	}

	/**
	 * @return the largest totalCount of any of our child facets, or -1 if there
	 *         are no children.
	 */
	public int getMaxChildTotalCount() {
		int result = -1;
		if (nChildrenRaw() > 0) {
			result = instantiatedPerspective.getMaxChildTotalCount();
		}
		return result;
	}

	public void setMaxChildTotalCount(final int maxCount) {
		instantiatedPerspective.setMaxChildTotalCount(maxCount);
	}

	/**
	 * Can be called from thread prefetcher
	 *
	 * @return the sum of the totalCount of our children. May differ from
	 *         totalCount because an item can have multiple sibling facets,
	 *         and/or may have this facet but not any of our children.
	 *
	 *         always >= 0
	 */
	public int getTotalChildTotalCount() {
		return (nChildrenRaw() > 0) ? instantiatedPerspective.getTotalChildTotalCount() : 0;
	}

	public int getTotalChildOnCount() {
		return instantiatedPerspective.getTotalChildOnCount();
	}

	private void setTotalChildTotalCount(final int cnt) {
		assert cnt >= 0 : this + " " + query() + " cnt=" + cnt;
		instantiatedPerspective.setTotalChildTotalCount(cnt);
	}

	/**
	 * @param isConditional
	 *            want median according to onCount or totalCount
	 *
	 * @return the whichChild (between 0 and nChildren-1] of the median + the
	 *         fraction of the median below the halfway point, when you lay out
	 *         count copies of all the child facets. Returns -1.0 if query is
	 *         invalid or no items satisfy this predicate. (Return
	 *         %whichChild%.0 if count==1).
	 */
	public double medianWhichChild(final boolean isConditional) {
		return instantiatedPerspective.medianWhichChild(isConditional);
	}

	/**
	 * @return spaces to indent according to this perspective's ancestor depth,
	 *         followed by a right arrow if this perspective has a parent.
	 */
	public @NonNull String namePrefix() {
		return namePrefix(depth());
	}

	/**
	 * @return spaces to indent according to this perspective's ancestor depth,
	 *         followed by a right arrow if this perspective has a parent.
	 */
	private static @NonNull String namePrefix(final int depth) {
		return depth > 0 ? UtilString.getSpaces(2 * (depth - 1)) + BungeeConstants.PARENT_INDICATOR_PREFIX : "";
	}

	/**
	 * @return cached name or "ID=67"
	 */
	@Override
	public @NonNull String getName() {
		return getName(null);
	}

	/**
	 * @return cached name or "ID=67" and call callback
	 */
	@Override
	public @NonNull String getName(final @Nullable RedrawCallback callback) {
		String pName = callback != null ? getNameOrDefaultAndCallback(null, callback) : getNameIfCached();
		if (pName == null) {
			// Default to null above, and only compute default if necessary
			pName = "ID=" + getID();
		}
		return pName;
	}

	public String nameNid() {
		return getName() + " (" + getID() + ")";
	}

	/**
	 * @param callback
	 *            If name hasn't been read from the database, call callback when
	 *            it is.
	 * @return the name of this facet, or defaultName if it hasn't been read
	 *         yet.
	 */
	public @Nullable String getNameOrDefaultAndCallback(final String defaultName,
			final @NonNull RedrawCallback callback) {
		String result = getNameIfCached();
		if (result == null) {
			query.queueGetName(this, callback);
			result = defaultName;
		}
		return result;
	}

	public @NonNull String getNameNow() {
		String result = getNameIfCached();
		if (result == null) {
			query().getNamesNow(UtilArray.getUnmodifiableList(this));
			result = getNameIfCached();
		}
		assert result != null;
		return result;
	}

	/**
	 * @return this facet's name, or null if it hasn't been read from the
	 *         database yet
	 */
	public String getNameIfCached() {
		return name;
	}

	/**
	 * @param _name
	 *            either we just read the name from the database, or we're
	 *            editing and changing the name for this facet
	 */
	public void setName(final @NonNull String _name) {
		assert name == null || name.equals(_name) : name + " " + _name;
		name = _name;
	}

	/**
	 * @param prefix
	 * @return Child with lowest facet_id whose name begins with prefix, or null
	 *         if none exist.
	 */
	public @Nullable Perspective firstWithPrefix(final @NonNull String prefix, final @Nullable RedrawCallback redraw) {
		final Interval<Perspective> rangeForPrefix = rangeForPrefix(prefix, redraw);
		final Perspective lowerEndpoint = rangeForPrefix != null ? rangeForPrefix.getLowerEndpoint() : null;
		return lowerEndpoint;
	}

	/**
	 * @return [first, last] Perspectives whose names begin with prefix. null
	 *         means redraw will be called when it is computed (only applicable
	 *         if redraw != null).
	 */
	public @Nullable Interval<Perspective> rangeForPrefix(final @NonNull String prefix,
			final @Nullable RedrawCallback redraw) {
		assert prefix.equals(prefix.toLowerCase()) : prefix;
		Interval<Perspective> result = null;
		if (prefix.length() == 0) {
			result = GenericInterval.getClosedGenericInterval(getRawNthChild(0), getRawNthChild(nChildrenRaw() - 1));
		} else {
			final int splitIndex = prefix.length() - 1;
			final String prefixButOne = Util.nonNull(prefix.substring(0, splitIndex));
			final Map<String, Interval<Perspective>> prefixButOneLetterOffsets = getLetterOffsets(prefixButOne, redraw);
			if (prefixButOneLetterOffsets != null) {
				final String suffix = prefix.substring(splitIndex);
				result = prefixButOneLetterOffsets.get(suffix);
				if (result == null) {
					result = GenericInterval.getEmptyInterval();
				}
				assert result != null : this + " prefix=" + prefix + " suffix=" + suffix + "\n"
						+ prefixButOneLetterOffsets;
			}
		}
		return result;
	}

	/**
	 * null means it is not yet computed.
	 */
	public @Nullable Map<String, Interval<Perspective>> lookupLetterOffsets(final @NonNull String prefix) {
		assert isAlphabetic() : this;
		assert prefix != null;
		assert prefix.equals(prefix.toLowerCase()) : prefix;
		final Map<String, Interval<Perspective>> letterOffsets = instantiatedPerspective.lookupLetterOffsets(prefix);
		return letterOffsets;
	}

	/**
	 * May be called in thread LetterOffsetsCreator
	 *
	 * @return Map from a <i>lower case</i> String <i>of length 0 or 1</i> to
	 *         the Perspective range of children starting with
	 *         <b><i> parent+string </i></b> . @NonNull if redraw==null.
	 */
	public @Nullable Map<String, Interval<Perspective>> getLetterOffsets(final @NonNull String prefix,
			final @Nullable RedrawCallback redraw) {
		assert prefix.equals(prefix.toLowerCase()) : prefix;
		Map<String, Interval<Perspective>> letterOffsets = lookupLetterOffsets(prefix);
		if (letterOffsets == null) {
			if (redraw != null && prefetchStatus() != PrefetchStatus.PREFETCHED_YES) {
				query().queueGetLetterOffsets(this, prefix, redraw);
			} else {
				letterOffsets = createLetterOffsets(prefix);
			}
		}
		return letterOffsets;
	}

	/**
	 * May be called in thread LetterOffsetsCreator
	 */
	private @NonNull Map<String, Interval<Perspective>> createLetterOffsets(final @NonNull String prefix) {
		assert prefix.equals(prefix.toLowerCase()) : prefix;
		assert lookupLetterOffsets(prefix) == null : this + " '" + prefix + "'";
		Map<String, Interval<Perspective>> result = null;
		if (prefetchStatus() == PrefetchStatus.PREFETCHED_YES) {
			result = createLetterOffsetsCached(prefix);
		} else {
			try (final MyResultSet rs = query().getLetterOffsets(this, prefix);) {
				result = new LinkedHashMap<>(MyResultSet.nRows(rs));
				while (rs.next()) {
					final int minFacetID = rs.getInt(2);
					final int maxFacetID = rs.getInt(3);
					final Perspective firstWithExtension = query().findFirstEffectivePerspective(minFacetID, maxFacetID,
							1);
					if (firstWithExtension == null) {
						continue;
					}
					final Perspective lastWithExtension = query().findFirstEffectivePerspective(minFacetID, maxFacetID,
							-1);
					final String key = rs.getString(1).toLowerCase();
					assert key != null && lastWithExtension != null;
					putLetterOffsetsValue(prefix, key, firstWithExtension, lastWithExtension, rs, false, result);
				}
			} catch (final Throwable e) {
				e.printStackTrace();
			}
		}
		assert result != null && !result.isEmpty() : this + " '" + prefix + "'";
		assert UtilArray.assertIsSorted(result.keySet(), UtilString.MY_US_COLLATOR);
		instantiatedPerspective.putLettersOffsets(prefix, result);
		return result;
	}

	private @NonNull Map<String, Interval<Perspective>> createLetterOffsetsCached(final @NonNull String prefix) {
		final Map<String, Interval<Perspective>> result = new LinkedHashMap<>();
		final int prefixLength = prefix.length();
		String suffix = null;
		Perspective firstWithExtension = null;
		Perspective lastWithExtension = null;
		for (int i = 0; i < nChildrenRaw(); i++) {
			final Perspective child = getRawNthChild(i);
			final String name1 = child.getName().toLowerCase();
			if (name1.startsWith(prefix)) {
				if (name1.length() == prefixLength) {
					suffix = "";
					firstWithExtension = child;
				} else {
					final String nextLetter = name1.substring(prefixLength, prefixLength + 1);
					assert nextLetter != null;
					if (!UtilString.stringEqualsUS(suffix, nextLetter)) {
						if (suffix != null) {
							if (UtilString.stringCompareUS(suffix, nextLetter) >= 0) {
								System.err.println(" Warning: Alphabetization error among children of " + this
										+ ": prefix='" + prefix + "'\n previous child=" + child.previous()
										+ "\n          child=" + child + "\n'" + suffix + "' should be before '"
										+ nextLetter + "'.");
							}
							assert firstWithExtension != null && lastWithExtension != null;
							putLetterOffsetsValue(prefix, suffix, firstWithExtension, lastWithExtension, null, false,
									result);
						}
						suffix = nextLetter;
						firstWithExtension = child;
					}
				}
				lastWithExtension = child;
			}
		}
		if (suffix != null) {
			assert firstWithExtension != null && lastWithExtension != null;
			putLetterOffsetsValue(prefix, suffix, firstWithExtension, lastWithExtension, null, false, result);
		}
		return result;
	}

	/**
	 * letterOffsets.put(suffix, getClosedGenericInterval(firstWithExtension,
	 * lastWithExtension)) iff that Interval's totalCount > 0.
	 *
	 * @param rs
	 *            is only used for error messages.
	 */
	public boolean putLetterOffsetsValue(final @NonNull String prefix, final @NonNull String suffix,
			final @NonNull Perspective firstWithExtension, final @NonNull Perspective lastWithExtension,
			final @Nullable MyResultSet rs, final boolean isBogusSuffix,
			final @NonNull Map<String, Interval<Perspective>> letterOffsets) {

		// suffix.length()==0 when name==prefix
		assert suffix.length() <= 1 && (isBogusSuffix || (lastWithExtension.couldStartWith(prefix + suffix)
				&& firstWithExtension.couldStartWith(prefix + suffix))) : "parent=" + this + ": prefix='" + prefix
						+ "' + suffix='" + suffix + "'  firstWithExtension=" + firstWithExtension
						+ " - lastWithExtension=" + lastWithExtension + (rs == null ? "" : "\n" + rs.valueOfDeep(20));

		final boolean result = lastWithExtension.cumTotalCountInclusive() > firstWithExtension.cumTotalCountExclusive();
		if (result) {
			letterOffsets.put(suffix, GenericInterval.getClosedGenericInterval(firstWithExtension, lastWithExtension));
		}
		return result;
	}

	/**
	 * Only called by getLetterOffsetsValue.
	 *
	 * @return whether name starts with prefix or is not cached.
	 */
	private boolean couldStartWith(final @NonNull String prefix) {
		boolean result = true;
		final int length = prefix.length();
		// Optimize common case
		if (length > 0) {
			if (name != null) {
				// stringEqualsUS ignores case
				result = UtilString.stringEqualsUS(prefix, name.substring(0, length));
			}
		}
		return result;
	}

	private void setNchildren(final int n_children, final int children_offset) {
		setNchildren(n_children);
		if (n_children > 0) {
			instantiatedPerspective.setChildrenOffset(children_offset);
		}
	}

	private void setNchildren(final int n_children) {
		nChildrenRaw = n_children;
		if (nChildrenRaw > 0) {
			instantiatedPerspective.setNchildrenRaw(n_children);
		}
	}

	private void setChildrenOffset(final int offset) {
		assert (offset > 0) == (nChildrenRaw() > 0) : this + " offset=" + offset + " nChildrenRaw=" + nChildrenRaw();
		if (offset > 0) {
			assert offset >= facet_id : facet_id + " " + offset;
			instantiatedPerspective.setChildrenOffset(offset);
		}
	}

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append(getNameIfCached()).append(" (").append(UtilString.addCommas(facet_id)).append(")");
		return UtilString.toString(this, buf);
	}

	/**
	 * @return this facet's most general ancestor
	 */
	public @NonNull Perspective getFacetType() {
		if (parent != null) {
			return parent.getFacetType();
		}
		return this;
	}

	public @NonNull Query query() {
		return query;
	}

	/**
	 * @return is this one of the facetTypes that get a top-level row of bars
	 */
	public boolean isTopLevel() {
		return parent == null;
	}

	public int nRestrictions() {
		return instantiatedPerspective != null ? instantiatedPerspective.nRestrictions() : 0;
	}

	public int nRestrictions(final boolean polarity) {
		return instantiatedPerspective != null ? instantiatedPerspective.nRestrictions(polarity) : 0;
	}

	/**
	 * @return does this facet have a filter on one of its children?
	 */
	public boolean isRestricted() {
		return instantiatedPerspective != null && instantiatedPerspective.isRestricted();
	}

	/**
	 * @return does this facet have a filter with polarity on one of its
	 *         children?
	 */
	public boolean isRestricted(final boolean polarity) { // NO_UCD (use
															// default)
		return instantiatedPerspective != null && instantiatedPerspective.isRestricted(polarity);
	}

	/**
	 * Will create Restrictions, so avoid calling if possible.
	 */
	@NonNull
	List<Perspective> allRestrictionsList() {
		final Restrictions restrictionsOrNull = restrictionsOrNull();
		if (restrictionsOrNull == null) {
			return UtilArray.EMPTY_LIST;
		} else {
			return restrictionsOrNull.allRestrictionsList();
		}
	}

	public @NonNull Set<Perspective> restrictions(final boolean polarity) {
		final Restrictions restrictionsOrNull = restrictionsOrNull();
		if (restrictionsOrNull == null) {
			return UtilArray.EMPTY_SET;
		} else {
			return restrictionsOrNull.restrictions(polarity);
		}
	}

	public @Nullable Restrictions restrictionsOrNull() {
		return instantiatedPerspective.restrictionsOrNull();
	}

	/**
	 * @return Is this one of parent's restrictions?
	 */
	public boolean isRestriction() {
		return parent != null && parent.isRestriction(this);
	}

	/**
	 * @return Is this one of parent's restrictions? or equivalently, Does this
	 *         restriction logically follow from the query?
	 *
	 *         Positive restrictions are propagated up, so it will always be
	 *         explicit on parent.
	 *
	 *         For negative restrictions [except in expert mode] there will
	 *         never be a PV for this Perspective, and parent will know that any
	 *         Bars are excluded.
	 */
	public boolean isRestriction(final boolean polarity) {
		return parent != null && parent.isRestriction(this, polarity);
	}

	/**
	 * @return Is child one of our restrictions?
	 */
	private boolean isRestriction(final @NonNull Perspective child) {
		assert assertIsChild(child);
		return instantiatedPerspective.isRestriction(child);
	}

	/**
	 * @return Is child one of our restrictions?
	 */
	boolean isRestriction(final @NonNull Perspective child, final boolean polarity) {
		assert assertIsChild(child);
		return instantiatedPerspective.isRestriction(child, polarity);
	}

	public @NonNull YesNoMaybe restrictionPolarity() {
		return isRestriction(true) ? YesNoMaybe.YES : isRestriction(false) ? YesNoMaybe.NO : YesNoMaybe.MAYBE;
	}

	@Override
	public @NonNull Collection<Perspective> getFacets() {
		final Set<Perspective> result = Collections.singleton(this);
		assert result != null;
		return result;
	}

	/**
	 * All restriction changes go through this function.
	 *
	 * Update selections, and ensure there's a [displayed] Perspective for newly
	 * selected facets' ancestors.
	 *
	 * @return whether the query changed (and if not, why not).
	 */
	public @NonNull ToggleFacetResult toggleFacet(final @Nullable Perspective fromChild,
			final @NonNull Perspective toChild, final int modifiers) {
		final boolean polarity = isRequireAction(toChild, modifiers);
		final ToggleFacetResult result = new ToggleFacetResult(!Util.isDisplayOnlyAction(modifiers));
		Collection<Perspective> toDisplay = new HashSet<>();
		if (!result.result) {
			toDisplay.add(toChild);
		} else if (Util.isShiftDown(modifiers)) {
			toDisplay = selectInterveningFacets(fromChild, toChild, !Util.isExcludeAction(modifiers), result);
		} else if (isRestriction(toChild, polarity) && !isDeselectOthers(modifiers)) {
			result.result = deselect(toChild, modifiers, false);
		} else {
			deselect(toChild, modifiers, false);
			toDisplay.add(toChild);
			selectFacet(toChild, polarity);
		}
		displayAncestors(toDisplay);
		return result;
	}

	/**
	 * @return Not an exclude and either child isn't excluded or a shift key is
	 *         pressed.
	 */
	boolean isRequireAction(final @NonNull Perspective child, final int modifiers) {
		final boolean polarity = !Util.isExcludeAction(modifiers)
				&& (Util.isAnyShiftKeyDown(modifiers) || !isRestriction(child, false));
		return polarity;
	}

	/**
	 * ~(CTRL | SHIFT | EXCLUDE)
	 */
	public static boolean isDeselectOthers(final int modifiers) {
		return (modifiers & BungeeConstants.DONT_DESELECT_OTHERS_MASK) == 0;
	}

	public static class ToggleFacetResult {
		public boolean result;
		public @Nullable String errorMsg = null;

		public ToggleFacetResult(final boolean _result) {
			result = _result;
		}

		@Override
		public String toString() {
			return UtilString.toString(this, "result=" + result + " errorMsg=" + errorMsg);
		}

	}

	/**
	 * @return whether anything was/would be deselected if child is selected
	 *         with the given modifiers.
	 *
	 *         If deselectOthers, deselect all children selected with either
	 *         polarity.
	 *
	 *         Otherwise, deselect child if it is selected with either polarity.
	 */
	public boolean deselect(final @NonNull Perspective child, final int modifiers, final boolean dontDeselect) {
		return isDeselectOthers(modifiers) ? deselectInternal(allRestrictionsList(), dontDeselect)
				: deselectInternal(child, dontDeselect);
	}

	static boolean deselectInternal(final @NonNull List<Perspective> children, final boolean dontDeselect) {
		boolean result = false;
		for (int i = children.size() - 1; i >= 0; i--) {
			final Perspective child = children.get(i);
			assert child != null;
			if (deselectInternal(child, dontDeselect)) {
				result = true;
				if (dontDeselect) {
					break;
				}
			}
		}
		return result;
	}

	static boolean deselectInternal(final @NonNull Perspective child, final boolean dontDeselect) {
		return dontDeselect ? child.isRestriction() : child.deselectNundisplay();
	}

	/**
	 * Remove parent's restriction on this (if any). In any case, undisplay
	 * this.
	 */
	public boolean deselectNundisplay() {
		undisplay();
		assert parent != null;
		return parent.deleteRestriction(this);
	}

	private boolean deleteRestriction(@NonNull final Perspective child) {
		assert assertIsChild(child);
		boolean result = false;
		final Restrictions restrictionsOrNull = restrictionsOrNull();
		if (restrictionsOrNull != null) {
			result = restrictionsOrNull.delete(child);
		}
		return result;
	}

	public void undisplay() {
		if (isDisplayed()) {
			query().undisplay(this);
			clearPerspective();
		}
	}

	/**
	 * Remove all filters, and recurse on isDisplayed() children.
	 */
	public void clearPerspective() {
		// recurse to clear descendents' restrictions (assumes only displayed
		// descendents are restricted).
		for (final Perspective child : instantiatedPerspective.getChildrenRaw()) {
			if (child != null) {
				child.undisplay();
			}
		}
		clearRestrictions();
	}

	/**
	 * Just clears restrictions().
	 */
	private void clearRestrictions() {
		instantiatedPerspective.clearRestrictions();
	}

	// /**
	// * Only called by UserAction
	// *
	// * @return whether anything new was displayed.
	// */
	// @SuppressWarnings("null")
	// public boolean displayMyAncestors() {
	// return displayAncestors(Collections.singletonList(this));
	// }

	/**
	 * @return whether anything new was displayed.
	 */
	public static boolean displayAncestors(final @NonNull Collection<Perspective> perspectives) {
		boolean result = false;
		for (final Perspective toDisplay : perspectives) {
			if (toDisplay.displayAncestors()) {
				result = true;
			}
		}
		return result;
	}

	/**
	 * If isEffectiveChildren, display this. Otherwise, just ancestors.
	 *
	 * @return whether anything was displayed, in which case
	 *         query().updateData() must be called to get onCounts and
	 *         totalCounts.
	 */
	public boolean displayAncestors() {
		boolean result = false;
		if (!isDisplayed()) {
			assert parent != null;
			result = parent.displayAncestors();
			if (isEffectiveChildren()) {
				query().display(this);
				result = true;
			}
		}
		return result;
	}

	/**
	 * @param toggleFacetResult
	 *            Used to return information: .result == return value non-empty
	 *            & .errorMsg
	 *
	 * @return children that were selected (polarity true only). If fromChild is
	 *         null, only select toChild.
	 */
	public @NonNull Collection<Perspective> selectInterveningFacets(final @Nullable Perspective fromChild,
			final @NonNull Perspective toChild, final boolean polarity,
			final @NonNull ToggleFacetResult toggleFacetResult) {
		assert polarity;
		final Collection<Perspective> selected = new HashSet<>();
		if (fromChild != null) {
			if (toChild.validateArrowFocus(fromChild)) {
				final int toWhichChild = toChild.whichChildRaw;
				final int fromWhichChild = fromChild.whichChildRaw;
				final int lowWhichChild = Math.min(toWhichChild, fromWhichChild);
				final int highWhichChild = Math.max(toWhichChild, fromWhichChild);
				try {
					for (int whichChild = lowWhichChild; whichChild <= highWhichChild; whichChild++) {
						final Perspective child = getRawNthChild(whichChild);
						assert child != null;
						if (!isRestriction(child, polarity)) {
							selected.add(child);
							selectFacet(child, polarity);
						}
					}
				} catch (final AssertionError e) {
					System.err.println("While Perspective.selectInterveningFacets " + this + " " + fromChild + " to "
							+ toChild + ":\n");
					throw (e);
				}
			} else {
				toggleFacetResult.result = false;
				toggleFacetResult.errorMsg = fromChild + " to " + toChild + " is not a sensible interval to select.";
			}
		} else {
			assert !isRestriction(toChild, polarity) : "Perspective.selectInterveningFacets problem";
			selected.add(toChild);
			selectFacet(toChild, polarity);
		}
		assert selected
				.isEmpty() == !toggleFacetResult.result : "Perspective.selectInterveningFacets nothing to select! "
						+ this + "\n from-toChild range:\n  " + fromChild + "\n - " + toChild + "\n polarity="
						+ polarity + " " + "\n isRestriction(toChild, polarity)=" + isRestriction(toChild, polarity)
						+ "\n toggleFacetResult=" + toggleFacetResult + "\n selected=" + selected;
		return selected;
	}

	/**
	 * @param potentialArrowFocus
	 *            not necessarily art.arrowFocus
	 * @return true.
	 *
	 *         Throws an AssertionError unless arrowFocus!=null and its parent
	 *         or grandparent is non-null and the same as ours.
	 */
	public boolean validateArrowFocus(final @Nullable Perspective potentialArrowFocus) {
		assert potentialArrowFocus != null && getParent() != null
				&& (potentialArrowFocus.getParent() == getParent() || (getGrandParent() != null
						&& potentialArrowFocus.getGrandParent() == getGrandParent())) : "arrowFocus="
								+ (potentialArrowFocus != null ? potentialArrowFocus.path() : "<null>") + "\n this="
								+ path();
		return true;
	}

	/**
	 * If polarity, select ancestors, too.
	 *
	 * @return Returns true if it changed the query, which it alway does.
	 */
	public boolean selectFacet(final @NonNull Perspective child, final boolean polarity) {
		if (polarity && parent != null && !isRestriction(polarity)) {
			assert parent != null;
			parent.selectFacet(this, polarity);
		}
		addRestriction(child, polarity);
		return true;
	}

	private void addRestriction(final @NonNull Perspective child, final boolean polarity) {
		assert assertIsChild(child);
		instantiatedPerspective.restrictions().add(child, polarity);
	}

	/**
	 * This is only called by Query.restrictData(). All children have already
	 * been restrictData()'ed.
	 */
	public void restrictData() {
		if (isInstantiated()) {
			int childCumOnCount = 0;
			int maxChildCount = -1;
			for (final Perspective child : instantiatedPerspective.getChildrenRaw()) {
				if (child != null) {
					final int childOnCount = child.getOnCount();
					if (childOnCount > maxChildCount) {
						maxChildCount = childOnCount;
					}
					childCumOnCount += childOnCount;
					child.cumTotalCount = childCumOnCount;
				}
			}
			setTotalChildTotalCount(childCumOnCount);
			setMaxChildTotalCount(maxChildCount);
			clearRestrictions();
			decacheLettersOffsets();
		}
	}

	private void addChildFacet(final @NonNull Perspective child) {
		if (needsChildrenOffset()) {
			query.importFacet(facet_id);
		}
		instantiatedPerspective.addChildFacet(child);
	}

	/**
	 * Only called by Rank, and parentTotal/OnCount are rank's total/onCount
	 *
	 * @return log(constrainOddsRatio()), or 0.0 if the resulting ChiSq2x2 is
	 *         known to be uninformative..
	 */
	public double constrainedLogOddsRatio(final int parentTotalCount, final int parentOnCount) {
		final double constrainedOddsRatio = constrainOddsRatio(parentTotalCount, parentOnCount);
		final double constrainedLogOddsRatio = Math.log(constrainedOddsRatio);
		assert Math.abs(constrainedLogOddsRatio / Perspective.LOG_ODDS_RANGE) <= 1.0 : constrainedLogOddsRatio + " "
				+ constrainedOddsRatio;
		assert query().isExtensionallyRestricted() || constrainedLogOddsRatio == 0.0;
		return constrainedLogOddsRatio;
	}

	/**
	 * Only called by logConstrainedOddsRatio()
	 *
	 * @return odds ratio constrained to [1.0 / ODDS_RANGE, ODDS_RANGE], or 1.0
	 *         if the resulting ChiSq2x2 is known to be uninformative.
	 */
	private double constrainOddsRatio(final int parentTotalCount, final int parentOnCount) {
		double result = 1.0;
		final ChiSqParams chiSqParams = ChiSqParams.getChiSqParams(parentTotalCount, parentOnCount, getTotalCount(),
				getOnCount());
		if (chiSqParams != null) {
			final double oddsGivenNotEvent = chiSqParams.b() * chiSqParams.c();
			result = (oddsGivenNotEvent == 0.0) ? Double.POSITIVE_INFINITY
					: chiSqParams.a() * chiSqParams.d() / oddsGivenNotEvent;
			result = UtilMath.constrain(result, INVERSE_ODDS_RANGE, ODDS_RANGE);
		}
		assert !query.isQueryValid() || query.isExtensionallyRestricted() || result == 1.0 : query.getName(null) + "\n "
				+ path(true, true) + " parentTotalCount=" + parentTotalCount + " parentOnCount=" + parentOnCount
				+ " getTotalCount=" + getTotalCount() + " getOnCount=" + getOnCount()
				+ " query.isExtensionallyRestricted()=" + query.isExtensionallyRestricted() + " result=" + result + "\n"
				+ chiSqParams;
		return result;
	}

	/**
	 * @return {@link BungeeConstants#SIGNIFICANCE_POSITIVE} or
	 *         {@link BungeeConstants#SIGNIFICANCE_NEGATIVE} or
	 *         {@link BungeeConstants#SIGNIFICANCE_UNASSOCIATED}
	 */
	@SuppressWarnings("javadoc")
	public @NonNull Significance pValueSignificance(final int queryVersion) {
		assert queryVersion > 0;
		final ChiSq2x2 chiSq2x2 = getpValueChiSq2x2(queryVersion, null);
		assert chiSq2x2 != null;
		final Significance result = significance(query.getBonferroniThreshold(), chiSq2x2);
		assert result == Significance.UNASSOCIATED || (getOnCount() >= 0 && parentOnCount() >= 0) : this
				+ " queryVersion=" + query().version() + "\n getOnCount(callback)=" + getOnCount()
				+ "\n parentOnCount(callback)=" + parentOnCount() + "\n result=" + result + "\n threshold="
				+ query.getBonferroniThreshold() + "\n pValue=" + pValue(queryVersion) + "\n getOnCount=" + getOnCount()
				+ "\n parentOnCount=" + parentOnCount() + "\n getpValueChiSq2x2:"
				+ Util.nonNull(getpValueChiSq2x2(queryVersion, null)).printTable();
		return result;
	}

	/**
	 * @return the p-value in [0, POSITIVE_INFINITY]
	 */
	public double correctedPvalue() {
		return query.correctedPvalue(pValue(query.version()));
	}

	/**
	 * @return the p-value in [0, POSITIVE_INFINITY]
	 */
	double pValue(final int queryVersion) {
		assert queryVersion > 0;
		final ChiSq2x2 chiSq2x2 = getpValueChiSq2x2(queryVersion, null);
		assert chiSq2x2 != null;
		return chiSq2x2.pvalue();
	}

	private static @NonNull Significance significance(final double threshold, final @NonNull ChiSq2x2 chiSq2x2) {
		Significance result = Significance.UNASSOCIATED;
		if (chiSq2x2.pvalue() <= threshold) {
			result = chiSq2x2.sign() > 0 ? Significance.POSITIVE : Significance.NEGATIVE;
		}
		return result;
	}

	/**
	 * Sets fields pValueChiSq2x2 and pValueQueryVersion.
	 *
	 * @return pValueChiSq2x2, or null (and call callback) iff query invalid.
	 */
	@Nullable
	ChiSq2x2 getpValueChiSq2x2(final int queryVersion, final @Nullable RedrawCallback callback) {
		ChiSq2x2 result = null;
		if (queryVersion < 0) {
			if (callback != null) {
				query().queueRedraw(callback);
			}
		} else {
			if (pValueQueryVersion != queryVersion) {
				final ChiSqParams chiSqParams = ChiSqParams.getChiSqParams(parentTotalCount(), parentOnCount(),
						getTotalCount(), getOnCount());
				pValueChiSq2x2 = chiSqParams != null ? chiSqParams.getChiSq() : ChiSq2x2.UNINFORMATIVE_CHI_SQ_2X2;
				pValueQueryVersion = queryVersion;
			}
			result = pValueChiSq2x2;
		}
		return result;
	}

	/**
	 * @param isConditional
	 *            Want median according to onCount or totalCount?
	 *
	 * @return The median child, or null if query is invalid or no items satisfy
	 *         this predicate.
	 */
	public @Nullable Perspective getMedianPerspective(final boolean isConditional) {
		return instantiatedPerspective.getMedianPerspective(isConditional);
	}

	/**
	 * @return the p-value that the conditional median is different from the
	 *         unconditional median.
	 */
	public double medianPvalue() {
		return getMedianChiSq2x2().pvalue();
	}

	// /**
	// * @param threshold
	// * @return {@link BungeeConstants#SIGNIFICANCE_POSITIVE} or
	// * {@link BungeeConstants#SIGNIFICANCE_NEGATIVE} or
	// * {@link BungeeConstants#SIGNIFICANCE_UNASSOCIATED}
	// */
	// @SuppressWarnings("javadoc")
	// @NonNull
	// Significance medianPvalueSignificance(final double threshold) {
	// return significance(threshold,
	// instantiatedPerspective.getMedianChiSq2x2());
	// }

	/**
	 * Is this facet's conditional median significantly different from its
	 * unconditional median?
	 *
	 * @return {@link BungeeConstants#SIGNIFICANCE_POSITIVE} or
	 *         {@link BungeeConstants#SIGNIFICANCE_NEGATIVE} or
	 *         {@link BungeeConstants#SIGNIFICANCE_UNASSOCIATED}
	 */
	@SuppressWarnings("javadoc")
	public @NonNull Significance medianTestSignificance() {
		return significance(query().getBonferroniMedianThreshold(), getMedianChiSq2x2());
	}

	private @NonNull ChiSq2x2 getMedianChiSq2x2() {
		return instantiatedPerspective.getMedianChiSq2x2();
	}

	/**
	 * Maybe add this Perspective to TopTags.
	 *
	 * @param isRecurseOnDisplayedChildren
	 *            whether to recurse on all children of displayed Perspectives
	 *            whose total count is less than its parent's. true for
	 *            TopTagsViz; false for Explanation.
	 *
	 * @return whether all totalCounts are cached.
	 */
	public boolean updateTopTags(final @NonNull TopTags topTags, final int queryVersion,
			final boolean isRecurseOnDisplayedChildren, final @Nullable RedrawCallback callback) {
		boolean areCountsCached = true;
		final int parentTotalCount = parentTotalCount();
		if (parentTotalCount > getTotalCount() && !(isRecurseOnDisplayedChildren && isRestriction())
				&& isInstantiated()) {
			final ChiSq2x2 chiSq = getpValueChiSq2x2(queryVersion, callback);
			if (chiSq == null) {
				areCountsCached = false;
			} else if (!Double.isNaN(chiSq.chiSq())) {
				final boolean siblingSelected = parent != null && parent.isRestricted();
				if ((chiSq.sign() > 0 || !siblingSelected)) {
					final double relativePhi = chiSq.myCramersPhi() / Math.sqrt(parentTotalCount);
					assert !Double.isNaN(relativePhi) : this + " myCramersPhi=" + chiSq.myCramersPhi()
							+ " parentTotalCount=" + parentTotalCount;
					topTags.maybeAdd(chiSq, relativePhi, this);
				}
			}
		}
		if (areCountsCached && isRecurseOnDisplayedChildren && prefetchStatus() != PrefetchStatus.PREFETCHED_NO
				&& isDisplayed()) {
			final int onCount = getOnCount();
			if (onCount < getTotalCount() && onCount > 0) {
				for (final Perspective child : getChildrenRaw()) {
					if (child.getTotalCount() > 0
							&& !child.updateTopTags(topTags, queryVersion, isRecurseOnDisplayedChildren, callback)) {
						areCountsCached = false;
					}
				}
			}
		}
		return areCountsCached;
	}

	/**
	 * @return the most recent ancestor (including this) that isDisplayed()
	 */
	public @NonNull Perspective lowestAncestorPVp() {
		if (isDisplayed()) {
			return this;
		} else {
			assert parent != null;
			return parent.lowestAncestorPVp();
		}
	}

	/**
	 * @return Set can be modified.
	 */
	public @NonNull SortedSet<Perspective> getRestrictionFacetInfos(final boolean polarity) {
		final SortedSet<Perspective> result = new TreeSet<>();
		if (nRestrictions() > 0) {
			for (final Perspective child : restrictions(polarity)) {
				assert child != null;
				assertIsChild(child);
				boolean found = false;
				if (polarity && child.isDisplayed()) {
					final SortedSet<Perspective> childResult = child.getRestrictionFacetInfos(true);
					result.addAll(childResult);
					found = childResult.size() > 0;
				}
				if (!found) {
					result.add(child);
				}
			}
		}
		if (!polarity) {
			// Excludes don't propagate up; have to check everywhere.
			for (final Perspective child : query().displayedPerspectives()) {
				if (child.getParent() == this) {
					result.addAll(child.getRestrictionFacetInfos(false));
				}
			}
		}
		return result;
	}

	private boolean assertIsChild(final @NonNull Perspective child) {
		assert child.getParent() == this : this + " " + child.path();
		return true;
	}

	public static @NonNull String path(final @NonNull Collection<Perspective> facets) {
		final StringBuilder buf = new StringBuilder("Collection<Perspective>:");
		for (final Perspective facet : facets) {
			buf.append("\n   ").append(facet.path());
		}
		buf.append("\n");
		return Util.nonNull(buf.toString());
	}

	/**
	 * @return path without showIsDisplayed or showCounts
	 */
	public @NonNull String path() {
		return path(false, false);
	}

	public @NonNull String path(final boolean showIsDisplayed, final boolean showCounts) {
		return path(showIsDisplayed, showCounts, false);
	}

	/**
	 * @return "<facet type> -- <ancestor> ... <ancestor> -- <this>"
	 *
	 *         Used for bookmarking.
	 */
	public @NonNull String path(final boolean showIsDisplayed, final boolean showCounts, final boolean isNameOnly) {
		String result;

		// avoid infinite recursion
		if (showCounts && !Query.isPrefetching()) {
			// This isn't the prefetch thread
			synchronized (query().prefetchingLock) {
				result = pathInternal(showIsDisplayed, showCounts, isNameOnly);
			}
		} else {
			// We'll use on/totalCount instead of getOn/getTotalCount.
			result = pathInternal(showIsDisplayed, showCounts, isNameOnly);
		}
		return result;
	}

	private @NonNull String pathInternal(final boolean showIsDisplayed, final boolean showCounts,
			final boolean isNameOnly) {
		String result = pathInternal2(showIsDisplayed, showCounts, isNameOnly);
		if (parent != null) {
			result = parent.path(showIsDisplayed, showCounts, isNameOnly) + Compile.FACET_HIERARCHY_SEPARATOR + result;
		}
		return result;
	}

	private @NonNull String pathInternal2(final boolean showIsDisplayed, final boolean showCounts,
			final boolean isNameOnly) {
		if (isNameOnly) {
			return getNameNow();
		}
		final StringBuilder buf = new StringBuilder();
		buf.append(getNameIfCached()).append(" (").append(getServerID());
		if (showIsDisplayed) {
			buf.append("; prefetchStatus=").append(prefetchStatus());
			buf.append("; isDisplayed=").append(isDisplayed());
		}
		if (showCounts) {
			buf.append(";");
			if (!query().isQueryValid()) {
				buf.append(" <query invalid>");
			} else {
				if (Query.isPrefetching()) {
					buf.append(" isPrefetching");
				}
				buf.append(" onCount/TotalCount=").append(getOnCount()).append("/").append(getTotalCount());
			}
		}
		buf.append(")");
		String result = UtilString.toString(this, buf);
		if (showIsDisplayed || showCounts) {
			result += "\n";
		}
		return result;
	}

	public @NonNull String queuePrefetchErrMsg(final @NonNull FetchType fetchType) {
		return "\n path=" + path(true, true) + "\n fetchType=" + fetchType + "\n prefetchedStatusFromFetchType="
				+ prefetchedStatusFromFetchType(fetchType) + "\n prefetchStatus=" + prefetchStatus() + "\n isDisplayed="
				+ isDisplayed() + "\n isRestrictedData=" + query().isRestrictedData();
	}

	@Override
	public int compareTo(final ItemPredicate o) {
		if (o instanceof Perspective) {
			return facet_id - ((Perspective) o).facet_id;
		} else {
			return compareBy().compareTo(o.compareBy());
		}
	}

	@Override
	public @NonNull Perspective compareBy() {
		return this;
	}

	@Override
	public @NonNull YesNoMaybe isPlural() {
		return YesNoMaybe.MAYBE;
	}

	@Override
	public @NonNull YesNoMaybe isNegated() {
		return YesNoMaybe.MAYBE;
	}

	@Override
	public @NonNull Perspective fromPerspective() {
		return this;
	}

	@Override
	public @NonNull Perspective toPerspective() {
		return this;
	}

	// ***********************************************************************
	// ************* Methods that are only called when editing ***************
	// ***********************************************************************

	public @Nullable Perspective updateIDnCountPrefetched(final int newID, final int totalCount, final int offset) {
		Perspective result = null;
		decacheLettersOffsets();
		setID(newID);
		setChildrenOffset(offset);
		assert totalCount >= 0;
		setTotalCount(totalCount);
		if (prefetchStatus() != PrefetchStatus.PREFETCHED_NO) {
			result = this;
		}
		if (parent != null) {
			parent.addChildFacet(this);
		}
		return result;
	}

	private void setID(final int _facet_id) {
		if (query().isEditable()) {
			facet_id = _facet_id;
		} else {
			throw (new UnsupportedOperationException("Can't change facet_id of " + this));
		}
	}

	public void reparent(final @NonNull Perspective child) {
		final Perspective parent2 = child.getParent();
		assert parent2 != null;
		parent2.incfChildren(-1);
		incfChildren(1);
		child.setParent(this);
	}

	public void incfChildren(final int delta) {
		assert query().isEditable();
		setNchildren(nChildrenRaw + delta);
	}

	private void setParent(final @Nullable Perspective _parent) {
		assert query().isEditable();
		parent = _parent;
	}

	// ************************************************************************
	// ********** End of Methods that are only called when editing ************
	// ************************************************************************

	@Override
	public int hashCode() {
		return getID();
	}

	@Override
	public @Nullable Markup description() {
		return null;
	}

	@Override
	public @Nullable MarkupElement pluralize() {
		return getMarkupElement(true, false);
	}

	@Override
	public @NonNull ItemPredicate negate() {
		return getMarkupElement(false, true);
	}

	@Override
	public @Nullable MarkupElement merge(final @NonNull MarkupElement nextElement) {
		return getMarkupElement().merge(nextElement);
	}

	@Override
	public boolean shouldUnderline() {
		return true;
	}

}
