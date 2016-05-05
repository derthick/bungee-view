package edu.cmu.cs.bungee.client.query;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInRange;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import JSci.maths.statistics.ChiSq2x2;
import JSci.maths.statistics.ChiSqParams;
import edu.cmu.cs.bungee.client.query.markup.DescriptionPreposition;
import edu.cmu.cs.bungee.client.query.markup.Restrictions;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.query.query.Query.DescriptionCategory;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;
import uk.org.bobulous.java.intervals.Interval;

class InstantiatedPerspective implements Serializable {

	protected static final long serialVersionUID = 1L;

	/**
	 * object, meta, or content. Used to generate query description.
	 *
	 * i.e. MarkupStringElement.PHRASE_TYPE_META
	 */
	final @NonNull DescriptionCategory descriptionCategory;

	final @NonNull DescriptionPreposition descriptionPreposition;

	private final @NonNull Perspective myPerspective;

	@NonNull
	PrefetchStatus prefetched = PrefetchStatus.PREFETCHED_NO;
	/**
	 * The children will have consecutive facet_id's, starting one after
	 * children_offset. Should only change if {@link Query#isEditable}
	 */
	int children_offset = Integer.MIN_VALUE;
	boolean isDisplayed = false;
	boolean isOrdered = false;
	/**
	 * Whether children's names are ordered by facet_id. The interface will only
	 * zoom by prefixes if so.
	 */
	boolean isAlphabetic = false;

	/**
	 * The sum of the totalCounts for our children. Can be less than totalCount
	 * if some items aren't further categorized, or greater if some items are in
	 * multiple child categories.
	 */
	private int totalChildTotalCount = Integer.MIN_VALUE;
	private int maxChildTotalCount = Integer.MIN_VALUE;

	private int medianQueryVersion = -1;

	private @NonNull ChiSq2x2 medianChiSq2x2 = ChiSq2x2.UNINFORMATIVE_CHI_SQ_2X2;

	/**
	 * Maps from a prefix string to a map from the next letter to the [first,
	 * last] child with that letter.
	 */
	private Map<String, Map<String, Interval<Perspective>>> lettersOffsetsMap;

	/**
	 * Query is filtered to return only results with one of these tags.
	 */
	private Restrictions localRestrictions;

	/**
	 * All children ordered by {@link Perspective#facet_id}. May contain null's
	 * for uninitialized children.
	 */
	private @NonNull Perspective[] dataIndex = new Perspective[0];
	/**
	 * Unmodifiable view of dataIndex, for public consumption. May contain
	 * null's for uninitialized children.
	 */
	private @NonNull List<Perspective> immutableDataIndex = Util.nonNull(Collections.EMPTY_LIST);

	// /**
	// * Only called by Perspective.ensureInstantiatedPerspective()
	// */
	// InstantiatedPerspective(final @NonNull Perspective perspective, @NonNull
	// final Perspective parent) {
	// this(perspective, parent, -1);
	// }

	/**
	 * Called by Perspective.ensureChild()
	 */
	InstantiatedPerspective(final @NonNull Perspective perspective, final @NonNull Perspective parent,
			final int _children_offset) {
		this(perspective, _children_offset, parent.getDescriptionCategory(), parent.getDescriptionPreposition());
	}

	/**
	 * Only called by {@link Query#initFacetTypes}
	 */
	InstantiatedPerspective(final @NonNull Perspective perspective, final int _children_offset,
			final @NonNull DescriptionCategory _descriptionCategory, final @NonNull String _descriptionPreposition) {
		this(perspective, _children_offset, _descriptionCategory, new DescriptionPreposition(_descriptionPreposition));
	}

	private InstantiatedPerspective(final @NonNull Perspective perspective, final int _children_offset,
			final @NonNull DescriptionCategory _descriptionCategory,
			final @NonNull DescriptionPreposition _descriptionPreposition) {
		assert _children_offset > 0 && perspective.nChildrenRaw() > 0;
		myPerspective = perspective;
		descriptionCategory = _descriptionCategory;
		descriptionPreposition = _descriptionPreposition;
		setNchildrenRaw(myPerspective.nChildrenRaw);
		setChildrenOffset(_children_offset);
		final Perspective parent = myPerspective.parent;
		if (parent != null) {
			isOrdered = parent.isOrdered();
		}
	}

	/**
	 * @param minChild
	 *            is inclusive
	 * @param maxChild
	 *            is also inclusive
	 */
	public @NonNull List<Perspective> getChildrenRaw(final int minChild, final int maxChild) {
		final List<Perspective> result = immutableDataIndex.subList(minChild, maxChild + 1);
		assert result != null;
		return result;
	}

	public @NonNull @Immutable List<Perspective> getChildrenRaw() {
		return immutableDataIndex;
	}

	// void incfChildren(final int delta) {
	// assert query().isEditable();
	// setNchildrenRaw(nChildrenRaw + delta);
	// }

	synchronized void setNchildrenRaw(final int nChildrenRaw) {
		// System.out.println("InstantiatedPerspective.setNchildrenRaw " + this
		// + " " + nChildrenRaw);
		// if (n != nChildrenRaw) {
		// nChildrenRaw = n;
		if (nChildrenRaw != dataIndex.length) {
			dataIndex = new Perspective[nChildrenRaw];
			immutableDataIndex = UtilArray.unmodifiableList(Arrays.asList(dataIndex));
		}
	}

	void setChildrenOffset(final int _children_offset) {
		if (_children_offset > 0) {
			assert children_offset < 0 || children_offset == _children_offset : myPerspective.path(true, true) + " "
					+ children_offset + " " + _children_offset;
			children_offset = _children_offset;
		}
	}

	private int whichChild2facetID(final int whichChild) {
		return getChildrenOffset() + 1 + whichChild;
	}

	private int facetID2whichChild(final int facetID) {
		return facetID - getChildrenOffset() - 1;
	}

	/**
	 * @param isConditional
	 *            Want median according to onCount or totalCount?
	 *
	 * @return The median child, or null if query is invalid or no items satisfy
	 *         this predicate.
	 */
	public @Nullable Perspective getMedianPerspective(final boolean isConditional) {
		final int medianIndexRaw = (int) medianWhichChild(isConditional);
		return medianIndexRaw >= 0 ? getRawNthChild(medianIndexRaw) : null;
	}

	/**
	 * @see Perspective#medianWhichChild
	 */
	double medianWhichChild(final boolean isConditional) {
		double result = -1.0;
		if (query().isQueryValid()) {
			final int count = isConditional ? getTotalChildOnCount() : getTotalChildTotalCount();
			// Return -1.0 if count==0 (but <whichChild>.0 if count==1).
			if (count > 0) {
				final int medianCount = count / 2;
				int cumCount = 0;
				for (int childID = firstChildID(); childID <= lastChildID(); childID++) {
					final int childCount = isConditional ? getFacetOnCount(childID) : getFacetTotalCount(childID);
					cumCount += childCount;
					if (cumCount > medianCount) {
						final double childFraction = 1.0 - (cumCount - medianCount) / (double) childCount;
						assert !Double.isNaN(childFraction) && childFraction < 1.0 && childFraction >= 0.0 : "child="
								+ dataIndex[facetID2whichChild(childID)] + " whichChild=" + facetID2whichChild(childID)
								+ " / nChildren=" + myPerspective.nChildrenRaw + " cumCount=" + cumCount
								+ " medianCount=" + medianCount + " (cumCount - medianCount)="
								+ (cumCount - medianCount) + " childCount=" + childCount + " isConditional="
								+ isConditional + " childFraction=" + childFraction;
						result = facetID2whichChild(childID) + childFraction;
						assert result >= 0.0 && result < myPerspective.nChildrenRaw() : this + " isConditional="
								+ isConditional + " medianCount=" + medianCount + " cumCount=" + cumCount + " result="
								+ result + " nEffectiveChildren=" + myPerspective.nEffectiveChildren();
						break;
					}
				}
			}
		}
		return result;
	}

	/**
	 * Sets field medianChiSq2x2.
	 *
	 * The two distributions to be compared are the on and off items. They are
	 * divided into above and below the unconditional median, allocating items
	 * with the median value in the same proportion they occur in the
	 * unconditional case.
	 *
	 * ChiSq2x2#row0() is the number on, and ChiSq2x2#row1() is the number off.
	 * ChiSq2x2#col0() is the total greater than the median, and ChiSq2x2#col1()
	 * is the total less than the median.
	 */
	@NonNull
	ChiSq2x2 getMedianChiSq2x2() {
		final int queryVersion = query().version();
		if (medianQueryVersion != queryVersion) {
			medianChiSq2x2 = ChiSq2x2.UNINFORMATIVE_CHI_SQ_2X2;
			final int totalChildOnCount = getTotalChildOnCount();
			if (queryVersion > 0 && totalChildOnCount < getTotalChildTotalCount() && totalChildOnCount > 0) {
				final double median = medianWhichChild(false);
				final int medianIndexRaw = (int) median;
				int greaterThanMedianChildOnCount = 0;
				int greaterThanMedianChildTotalCount = 0;
				for (int i = medianIndexRaw + 1; i < myPerspective.nChildrenRaw; i++) {
					final int childID = whichChild2facetID(i);
					greaterThanMedianChildOnCount += getFacetOnCount(childID);
					greaterThanMedianChildTotalCount += getFacetTotalCount(childID);
				}
				final int medianChildID = whichChild2facetID(medianIndexRaw);
				final double medianValueFractionAboveMedian = 1.0 - (median - medianIndexRaw);
				greaterThanMedianChildOnCount += UtilMath
						.roundToInt(getFacetOnCount(medianChildID) * medianValueFractionAboveMedian);

				greaterThanMedianChildTotalCount += UtilMath
						.roundToInt(getFacetTotalCount(medianChildID) * medianValueFractionAboveMedian);

				final ChiSqParams chiSqParams = ChiSqParams.getChiSqParams(getTotalChildTotalCount(), totalChildOnCount,
						greaterThanMedianChildTotalCount, greaterThanMedianChildOnCount);
				if (chiSqParams != null) {
					medianChiSq2x2 = chiSqParams.getChiSq();
				}
				medianQueryVersion = queryVersion;
			}
		}
		return medianChiSq2x2;
	}

	// private String path(final boolean b, final boolean c, final int childID)
	// {
	// String result = "";
	// final Perspective child = dataIndex[facetID2whichChild(childID)];
	// if (child != null) {
	// result = child.path(b, c);
	// }
	// return result;
	// }

	/**
	 * @return always >= 0
	 */
	int getTotalChildOnCount() {
		int result;
		if (query().isExtensionallyRestricted()) {
			result = 0;
			for (int childID = firstChildID(); childID <= lastChildID(); childID++) {
				result += getFacetOnCount(childID);
			}
		} else {
			result = getTotalChildTotalCount();
		}
		assert result >= 0;
		return result;
	}

	void addChildFacet(final @NonNull Perspective child) {
		final int whichChild = facetID2whichChild(child.getID());
		assert dataIndex.length > whichChild && whichChild >= 0 : " whichChild=" + whichChild + " children_offset="
				+ children_offset + "\n" + child.path(true, true);
		dataIndex[whichChild] = child;
	}

	int getChildrenOffset() {
		assert children_offset > 0 : myPerspective.path(true, true);
		return children_offset;

	}

	// TODO Remove unused code found by UCDetector
	// void removeNoCountChild(final Perspective facet) {
	// assert children_offset > 0 : this;
	// final int index = facet.getID() - children_offset - 1;
	// assert dataIndex.length > index && index >= 0 : this + " " + index + " "
	// + facet + " " + children_offset;
	// dataIndex[index] = null;
	// }

	/**
	 * @return nth child facet in alphabetical (or sort) order (starting with
	 *         zero)
	 */
	public @NonNull Perspective getRawNthChild(final int whichChild) {
		assert assertInRange(whichChild, 0, myPerspective.nChildrenRaw - 1);
		Perspective result = dataIndex[whichChild];
		if (result == null && myPerspective.prefetchStatus() == PrefetchStatus.PREFETCHED_NO) {
			System.out.println("InstantiatedPerspective.getNthChild prefetching " + myPerspective + " "
					+ myPerspective.getFetchType());
			query().lockAndPrefetch(myPerspective, myPerspective.getFetchType());
			result = dataIndex[whichChild];
		}
		assert result != null;
		return result;
	}

	/**
	 * Only called when editing.
	 */
	void resetChildrensOnCounts() {

		for (int childID = firstChildID(); childID <= lastChildID(); childID++) {
			setFacetTotalCount(childID, 0);
		}

		// assert prefetched != PrefetchStatus.PREFETCHED_NO : this;
		// for (final Perspective child : dataIndex) {
		// if (child != null) {
		// child.setOnCount(0);
		// }
		// }
	}

	/**
	 * Create Restrictions if needed and return.
	 */
	@NonNull
	Restrictions restrictions() {
		if (localRestrictions == null) {
			localRestrictions = new Restrictions();
		}
		assert localRestrictions != null;
		return localRestrictions;
	}

	@Nullable
	Restrictions restrictionsOrNull() {
		return localRestrictions;
	}

	/**
	 * Just clears restrictions().
	 */
	void clearRestrictions() {
		if (localRestrictions != null) {
			localRestrictions.clear();
		}
	}

	private @NonNull Query query() {
		return myPerspective.query();
	}

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append(myPerspective.name).append(" (").append(UtilString.addCommas(myPerspective.getID())).append(")");
		return UtilString.toString(this, buf);
	}

	int nRestrictions() {
		return localRestrictions == null ? 0 : localRestrictions.nRestrictions();
	}

	int nRestrictions(final boolean polarity) {
		return localRestrictions == null ? 0 : localRestrictions.nRestrictions(polarity);
	}

	boolean isRestricted() {
		return localRestrictions != null && localRestrictions.isRestricted();
	}

	boolean isRestricted(final boolean polarity) {
		return localRestrictions != null && localRestrictions.isRestricted(polarity);
	}

	boolean isRestriction(final @NonNull Perspective child, final boolean polarity) {
		return localRestrictions != null && localRestrictions.isRestriction(child, polarity);
	}

	boolean isRestriction(final @NonNull Perspective child) {
		return localRestrictions != null && localRestrictions.isRestriction(child);
	}

	Map<String, Interval<Perspective>> lookupLetterOffsets(final String prefix) {
		return lettersOffsetsMap == null ? null : lettersOffsetsMap.get(prefix);
	}

	void decacheLettersOffsets() {
		if (lettersOffsetsMap != null) {
			lettersOffsetsMap.clear();
		}
	}

	Map<String, Interval<Perspective>> putLettersOffsets(final String prefix,
			final Map<String, Interval<Perspective>> result) {
		if (lettersOffsetsMap == null) {
			lettersOffsetsMap = new HashMap<>();
		}
		return lettersOffsetsMap.put(prefix, result);
	}

	// public String setName(final @Nullable String _name) {
	// name = _name;
	// if (myPerspective.getID() == 36282) {
	// System.out.println("InstantiatedPerspective.setName " + this + " name=" +
	// name);
	// }
	// return name;
	// }

	/**
	 * @return >= 0 iff there are any children.
	 */
	public int getMaxChildTotalCount() {
		if (maxChildTotalCount == Integer.MIN_VALUE) {
			for (int childID = firstChildID(); childID <= lastChildID(); childID++) {
				maxChildTotalCount = Math.max(maxChildTotalCount, getFacetTotalCount(childID));
			}
		}
		return maxChildTotalCount;
	}

	/**
	 * @return always >= 0
	 */
	public int getTotalChildTotalCount() {
		if (totalChildTotalCount == Integer.MIN_VALUE) {
			totalChildTotalCount = 0;
			for (int childID = firstChildID(); childID <= lastChildID(); childID++) {
				totalChildTotalCount += getFacetTotalCount(childID);
			}
		}
		return totalChildTotalCount;
	}

	int firstChildID() {
		return children_offset + 1;
	}

	int lastChildID() {
		return children_offset + myPerspective.nChildrenRaw;
	}

	/**
	 * @return always >= 0
	 */
	private int getFacetTotalCount(final int childID) {
		return query().getFacetTotalCount(childID);
	}

	private void setFacetTotalCount(final int childID, final int totalCount) {
		query().setFacetTotalCount(childID, totalCount);
	}

	/**
	 * @return always >= 0
	 */
	private int getFacetOnCount(final int childID) {
		return query().getFacetOnCount(childID);
	}

	public void setTotalChildTotalCount(final int _totalChildTotalCount) {
		totalChildTotalCount = _totalChildTotalCount;
	}

	public void setMaxChildTotalCount(final int _maxChildTotalCount) {
		maxChildTotalCount = _maxChildTotalCount;
	}

}
