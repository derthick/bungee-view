package edu.cmu.cs.bungee.client.viz.bungeeCore;

import java.io.Serializable;
import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

class Preferences implements Serializable {
	public final int fontSize;
	final int nColumns;
	final boolean arrows;
	final boolean boundaries;
	final boolean brushing;
	final boolean checkboxes;
	final boolean medians;
	final boolean openClose;
	final boolean popups;
	final boolean pvalues;
	/**
	 * Allow control-, shift-, and alt- modifiers
	 */
	final boolean shortcuts;
	final boolean sortMenus;
	final boolean tagLists;
	final boolean zoom;
	final boolean editing;
	final boolean graph;
	final boolean debugGraph;

	private static final @NonNull String[] FEATURE_NAMES = { "fontSize", "nColumns", "arrows", "boundaries", "brushing",
			"checkboxes", "medians", "openClose", "popups", "pvalues", "shortcuts", "sortMenus", "tagLists", "zoom",
			"editing", "graph", "debugGraph" };

	public static final @NonNull String EXPERT_FEATURE_NAMES = "arrows,boundaries,brushing,checkboxes,medians," // NO_UCD
			// (unused
			// code)
			+ "pvalues,shortcuts,sortMenus,tagLists,zoom,graph";

	static final @NonNull String DEFAULT_FEATURE_NAMES = ",popups";

	/**
	 * @param base
	 *            Default values. If null, everything defaults to false, except
	 *            fontSize=DEFAULT_TEXT_SIZE and nColumns=-1
	 * @param optionsToChangeString
	 *            Set these to newState (for boolean preferences), or to the
	 *            given state (for numeric)
	 */
	@SuppressWarnings("null")
	Preferences(final @Nullable Preferences base, final @Nullable String optionsToChangeString, final boolean newState,
			final boolean isUnknownFeaturesOK) {
		final boolean isBase = base != null;
		final String[] optionsToChange = optionsToChangeString != null ? optionsToChangeString.split(",")
				: new String[0];
		// Util.printDeep(optionsToChange);

		// Handle numeric preferences
		int _fontSize = isBase ? base.fontSize : BungeeConstants.DEFAULT_TEXT_SIZE;
		int nCols = isBase ? base.nColumns : -1;
		for (final String optionString : optionsToChange) {
			final String[] optionArray = optionString.split("=");
			final String optionName = optionArray[0];
			if (optionArray.length > 1) {
				final String optionValue = optionArray[1];
				if (optionName.equals("fontSize")) {
					_fontSize = Integer.parseInt(optionValue);
				} else if (optionName.equals("nColumns")) {
					nCols = Integer.parseInt(optionValue);
				} else {
					assert false : optionString;
				}
			} else if (!ArrayUtils.contains(FEATURE_NAMES, optionName)) {
				if (isUnknownFeaturesOK) {
					System.err.println(" Ignoring unknown feature name " + optionName);
				} else {
					assert false : optionString + " is not a valid feature: " + Arrays.toString(FEATURE_NAMES);
				}
			}
		}
		fontSize = _fontSize;
		nColumns = nCols;

		// Handle boolean preferences
		arrows = ArrayUtils.contains(optionsToChange, "arrows") ? newState : isBase && base.arrows;
		boundaries = ArrayUtils.contains(optionsToChange, "boundaries") ? newState : isBase && base.boundaries;
		brushing = ArrayUtils.contains(optionsToChange, "brushing") ? newState : isBase && base.brushing;
		checkboxes = ArrayUtils.contains(optionsToChange, "checkboxes") ? newState : isBase && base.checkboxes;
		medians = ArrayUtils.contains(optionsToChange, "medians") ? newState : isBase && base.medians;
		openClose = ArrayUtils.contains(optionsToChange, "openClose") ? newState : isBase && base.openClose;
		popups = ArrayUtils.contains(optionsToChange, "popups") ? newState : isBase && base.popups;
		pvalues = ArrayUtils.contains(optionsToChange, "pvalues") ? newState : isBase && base.pvalues;
		shortcuts = ArrayUtils.contains(optionsToChange, "shortcuts") ? newState : isBase && base.shortcuts;
		sortMenus = ArrayUtils.contains(optionsToChange, "sortMenus") ? newState : isBase && base.sortMenus;
		tagLists = ArrayUtils.contains(optionsToChange, "tagLists") ? newState : isBase && base.tagLists;
		zoom = ArrayUtils.contains(optionsToChange, "zoom") ? newState : isBase && base.zoom;
		editing = ArrayUtils.contains(optionsToChange, "editing") ? newState : isBase && base.editing;
		graph = ArrayUtils.contains(optionsToChange, "graph") ? newState : isBase && base.graph;
		debugGraph = ArrayUtils.contains(optionsToChange, "debugGraph") ? newState : isBase && base.debugGraph;
	}

	public @NonNull String differenceOrQuoteNone(final @NonNull Preferences newPreferences) {
		String result = difference(newPreferences);
		if (result.length() == 0) {
			result = " <none>";
		}
		result = result.substring(1);
		assert result != null;
		return result;
	}

	public @NonNull String difference(final @NonNull Preferences newPreferences) {
		final StringBuilder buf = new StringBuilder();
		if (fontSize != newPreferences.fontSize) {
			buf.append("\nfontSize: ").append(fontSize).append(" ⇒ ").append(newPreferences.fontSize);
		}
		if (nColumns != newPreferences.nColumns) {
			buf.append("\nnColumns: ").append(nColumns).append(" ⇒ ").append(newPreferences.nColumns);
		}
		if (arrows != newPreferences.arrows) {
			buf.append(differenceInternal(arrows)).append("arrows");
		}
		if (boundaries != newPreferences.boundaries) {
			buf.append(differenceInternal(boundaries)).append("boundaries");
		}
		if (brushing != newPreferences.brushing) {
			buf.append(differenceInternal(brushing)).append("brushing");
		}
		if (checkboxes != newPreferences.checkboxes) {
			buf.append(differenceInternal(checkboxes)).append("checkboxes");
		}
		if (medians != newPreferences.medians) {
			buf.append(differenceInternal(medians)).append("medians");
		}
		if (openClose != newPreferences.openClose) {
			buf.append(differenceInternal(openClose)).append("openClose");
		}
		if (popups != newPreferences.popups) {
			buf.append(differenceInternal(popups)).append("popups");
		}
		if (pvalues != newPreferences.pvalues) {
			buf.append(differenceInternal(pvalues)).append("pvalues");
		}
		if (shortcuts != newPreferences.shortcuts) {
			buf.append(differenceInternal(shortcuts)).append("shortcuts");
		}
		if (sortMenus != newPreferences.sortMenus) {
			buf.append(differenceInternal(sortMenus)).append("sortMenus");
		}
		if (tagLists != newPreferences.tagLists) {
			buf.append(differenceInternal(tagLists)).append("tagLists");
		}
		if (zoom != newPreferences.zoom) {
			buf.append(differenceInternal(zoom)).append("zoom");
		}
		if (editing != newPreferences.editing) {
			buf.append(differenceInternal(editing)).append("editing");
		}
		if (graph != newPreferences.graph) {
			buf.append(differenceInternal(graph)).append("graph");
		}
		if (debugGraph != newPreferences.debugGraph) {
			buf.append(differenceInternal(debugGraph)).append("debugGraph");
		}

		return Util.nonNull(buf.toString());
	}

	private static @NonNull String differenceInternal(final boolean feature) {
		return feature ? "\nRemove " : "\nAdd ";
	}

	public @NonNull String features2string() {
		final StringBuilder buf = new StringBuilder();
		buf.append("fontSize=").append(fontSize);
		if (nColumns > 0) {
			buf.append(",nColumns=").append(nColumns);
		}
		if (arrows) {
			buf.append(",arrows");
		}
		if (boundaries) {
			buf.append(",boundaries");
		}
		if (brushing) {
			buf.append(",brushing");
		}
		if (checkboxes) {
			buf.append(",checkboxes");
		}
		if (medians) {
			buf.append(",medians");
		}
		if (openClose) {
			buf.append(",openClose");
		}
		if (popups) {
			buf.append(",popups");
		}
		if (pvalues) {
			buf.append(",pvalues");
		}
		if (shortcuts) {
			buf.append(",shortcuts");
		}
		if (sortMenus) {
			buf.append(",sortMenus");
		}
		if (tagLists) {
			buf.append(",tagLists");
		}
		if (zoom) {
			buf.append(",zoom");
		}
		if (editing) {
			buf.append(",editing");
		}
		if (graph) {
			buf.append(",graph");
		}
		if (debugGraph) {
			buf.append(",debugGraph");
		}
		return Util.nonNull(buf.toString());
	}

	@Override
	public String toString() {
		return UtilString.toString(this, features2string());
	}

	@Override
	public boolean equals(final Object o) {
		if (!(o instanceof Preferences)) {
			return false;
		}
		final Preferences options = (Preferences) o;
		return options.fontSize == fontSize && options.nColumns == nColumns && options.arrows == arrows
				&& options.boundaries == boundaries && options.brushing == brushing && options.checkboxes == checkboxes
				&& options.medians == medians && options.openClose == openClose && options.popups == popups
				&& options.pvalues == pvalues && options.shortcuts == shortcuts && options.sortMenus == sortMenus
				&& options.tagLists == tagLists && options.zoom == zoom && options.editing == editing
				&& options.graph == graph && options.debugGraph == debugGraph;
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 37 * result + fontSize;
		result = 37 * result + nColumns;
		result = 37 * result + (arrows ? 0 : 1);
		result = 37 * result + (boundaries ? 0 : 1);
		result = 37 * result + (brushing ? 0 : 1);
		result = 37 * result + (checkboxes ? 0 : 1);
		result = 37 * result + (medians ? 0 : 1);
		result = 37 * result + (openClose ? 0 : 1);
		result = 37 * result + (pvalues ? 0 : 1);
		result = 37 * result + (shortcuts ? 0 : 1);
		result = 37 * result + (sortMenus ? 0 : 1);
		result = 37 * result + (tagLists ? 0 : 1);
		result = 37 * result + (zoom ? 0 : 1);
		result = 37 * result + (editing ? 0 : 1);
		result = 37 * result + (graph ? 0 : 1);
		result = 37 * result + (debugGraph ? 0 : 1);
		return result;
	}

}
