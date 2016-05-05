package edu.cmu.cs.bungee.client.viz.popup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

final class Step implements Comparable<Step>, Serializable {

	private static final long serialVersionUID = 1L;

	private static final @NonNull List<Step> ELEMENTS = new ArrayList<>();

	// As each step is created, its ordinal will be fixed at the next
	// non-negative integer
	private final int ordinal = ELEMENTS.size();

	private Step next = null; // NO_UCD (use final)

	private final @NonNull String name;

	private Step(final @NonNull String _name) {
		name = _name;
		ELEMENTS.add(this);
		if (ordinal > 0) {
			ELEMENTS.get(ordinal - 1).next = this;
		}
	}

	@Nullable
	Step next() {
		return next;
	}

	@Override
	public int compareTo(final Step arg0) {
		return ordinal - arg0.ordinal;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, name);
	}

	/**
	 * No popup is being displayed
	 */
	static final @NonNull Step NO_POPUP = new Step("NO_POPUP");
	/**
	 * Mimimal popup adjacent to Bar
	 */
	static final @NonNull Step NO_FRAME = new Step("NO_FRAME");
	/**
	 * Begin animating move to top-right and adding more information. Start
	 * computing InfluenceDiagram. Make spacebarDesc say
	 * "Press the spacebar for more explanation"
	 */
	static final @NonNull Step START_FRAME = new Step("START_FRAME");
	/**
	 * waitForUserInput()
	 */
	static final @NonNull Step NO_HELP = new Step("NO_HELP");

	// /**
	// *********************** START ANIMATION *******************************
	// *
	// * Make spacebarDesc say "Press the spacebar to skip this animation"
	// */
	// static final Step START_ANIMATION = new Step("START_ANIMATION");
	// /**
	// * Connect to Rank()
	// */
	// static final Step SCALE_FRONT = new Step("SCALE_FRONT");
	// // static final Step TRANSLATE_POPUP = new Step("TRANSLATE_POPUP");
	// /**
	// * Fade out most info; fade in lines;
	// */
	// static final Step FADE_IN_TOTAL = new Step("FADE_FOR_TOTAL");
	// /**
	// * Emphasize lines even more, and add ", as shown by the bar's width" to
	// * totalDesc
	// */
	// static final Step HELP_TOTAL = new Step("HELP_TOTAL");
	// /**
	// * Fade out lines, totalCount, totalDesc
	// */
	// static final Step FADE_OUT_TOTAL = new Step("FADE_TOTAL");
	// /**
	// * Fade in most everything
	// */
	// static final Step FADE_IN_RATIO = new Step("HELP_RATIO");
	// /**
	// * Emphasize ratioLines
	// */
	// static final Step HELP_RATIO = new Step("HIGHLIGHT_RATIO");
	// // static final Step FADE_OUT_RATIO = new Step("FADE_RATIO");
	// // static final Step FADE_IN_PARENT = new Step("HELP_PARENT");
	// // static final Step HELP_PARENT = new Step("HIGHLIGHT_PARENT");
	// // static final Step FADE_OUT_PARENT = new Step("HELP_PARENT");
	// /**
	// * Make everything opaque. Make spacebarDesc say
	// * "Press the spacebar again for a slower animation"
	// */
	// static final Step UNFADE = new Step("UNFADE");
	// /**
	// *********************** END ANIMATION *******************************
	// *
	// * Update gold border. Make spacebarDesc say
	// * "Press the spacebar again for a slower animation" Update
	// * significanceHeader
	// *
	// * waitForUserInput()
	// */
	// static final Step END_ANIMATION = new Step("END_ANIMATION");
}