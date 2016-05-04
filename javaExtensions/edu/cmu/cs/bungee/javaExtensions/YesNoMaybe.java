package edu.cmu.cs.bungee.javaExtensions;

import org.eclipse.jdt.annotation.NonNull;

public enum YesNoMaybe {
	YES, NO, MAYBE, CONFLICT;

	/**
	 * @param b
	 * @return b ? YES : NO
	 */
	public static @NonNull YesNoMaybe asYesNoMaybe(final boolean b) {
		return b ? YES : NO;
	}

	/**
	 * @param yesNoMaybe
	 * @return max(this, yesNoMaybe), where YES > MAYBE > NO > CONFLICT
	 */
	public @NonNull YesNoMaybe max(final YesNoMaybe yesNoMaybe) {
		if (this == YES || yesNoMaybe == YES) {
			return YES;
		} else if (this == MAYBE || yesNoMaybe == MAYBE) {
			return MAYBE;
		} else if (this == NO || yesNoMaybe == NO) {
			return NO;
		} else {
			return CONFLICT;
		}
	}

	/**
	 * @return if this and yesNoMaybe are the same or MAYBE, return the other;
	 *         else return CONFLICT
	 *
	 *         ......yesNoMaybe
	 *
	 *         this . Y ? N C
	 *
	 *         Y......Y Y C C
	 *
	 *         ?......Y ? N C
	 *
	 *         N......C N N C
	 *
	 *         C......C C C C
	 */
	public @NonNull YesNoMaybe intersect(final @NonNull YesNoMaybe yesNoMaybe) {
		if (this == yesNoMaybe || this == MAYBE) {
			return yesNoMaybe;
		} else if (yesNoMaybe == MAYBE) {
			return this;
		} else {
			return CONFLICT;
		}
	}

	/**
	 * @param b
	 * @return and(asYesNoMaybe(b)) != CONFLICT
	 */
	public boolean isCompatible(final boolean b) {
		return intersect(asYesNoMaybe(b)) != CONFLICT;
	}

	private static final boolean[] YES_COMPATBLE_BOOLEAN_VALUES = { true };
	private static final boolean[] NO_COMPATBLE_BOOLEAN_VALUES = { false };
	private static final boolean[] MAYBE_COMPATBLE_BOOLEAN_VALUES = { true, false };
	private static final boolean[] CONFLICT_COMPATBLE_BOOLEAN_VALUES = {};

	@SuppressWarnings("incomplete-switch")
	public @NonNull static boolean[] compatibleBooleanValues(final @NonNull YesNoMaybe ynm) {
		boolean[] result = null;
		switch (ynm) {
		case YES:
			result = YES_COMPATBLE_BOOLEAN_VALUES;
			break;
		case NO:
			result = NO_COMPATBLE_BOOLEAN_VALUES;
			break;
		case MAYBE:
			result = MAYBE_COMPATBLE_BOOLEAN_VALUES;
			break;
		case CONFLICT:
			result = CONFLICT_COMPATBLE_BOOLEAN_VALUES;
			break;
		}
		assert result != null;
		return result;
	}

}
