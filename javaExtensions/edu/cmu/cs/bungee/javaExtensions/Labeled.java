package edu.cmu.cs.bungee.javaExtensions;

import java.io.Serializable;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Keeps track of an ordered set of Vs, each associated with a count. Supports
 * cumCountRangeChildren(int minCount, int maxCount);
 */
interface Labeled<V> extends Serializable {

	static final long serialVersionUID = 1L;

	/**
	 * @return must not be empty, and must not contain nulls.
	 */
	@NonNull
	List<V> getChildren();

	/**
	 * @return must be >= 0.
	 *
	 *         Allow zero so DefaultLabeledForPerspective &
	 *         PerspectiveVScrollLabeled can use getChildrenRaw() for
	 *         getChildren() and whichChildRaw() for childIndex().
	 */
	int count(@NonNull V o);

	void drawLabel(@NonNull V o, int[] pixelRange);

}
