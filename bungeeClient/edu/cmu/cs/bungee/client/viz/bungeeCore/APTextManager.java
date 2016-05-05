package edu.cmu.cs.bungee.client.viz.bungeeCore;

import java.awt.Font;
import java.awt.font.TextMeasurer;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil;

/**
 * Deals with formatting MarkupElements as Strings, which is especially
 * complicated for Perspectives.
 */
class APTextManager implements Serializable {
	protected static final long serialVersionUID = 1L;

	// Shows chars available in your Java environment:
	// http://www.pccl.demon.co.uk/java/unicode.html
	// delete button is F078; checkbox is F0FE; delete is FoFD

	private static final @NonNull String FONT_FAMILY = "SansSerif";
	private static final int FONT_FACE = Font.BOLD;

	/**
	 * A very thin space, so alignment can be precise.
	 */
	private static final @NonNull String THIN_SPACE = "\u200A";

	private static final @NonNull String CHILD_INDICATOR_SUFFIX = "↴"; // ↪⇘↯▶▼👶⋏⋀^⍾△☻⛄⍗🚸↓⇓⤦⤸⤵⬎↴

	/**
	 * Enough space characters to equal the width of the boxes we'll draw.
	 */
	private static final @NonNull String CHECKBOX_SPACES = "      ";

	/**
	 * Enough space characters to equal buttonMargin.
	 */
	private static final @NonNull String BUTTON_MARGIN_SPACES = "  ";

	private final @NonNull Bungee art;

	/**
	 * Minimum size for legible text. Can be lowered by TooSmallWindow.
	 */
	int minLegibleFontSize = 8;

	private int fontSize;
	private @NonNull Font font = new Font(FONT_FAMILY, FONT_FACE, fontSize);
	/**
	 * Always equal to an int
	 */
	private double digitWidth;
	/**
	 * Always equal to an int
	 */
	private double commaWidth;
	/**
	 * Always equal to an int
	 */
	private double hyphenWidth;
	/**
	 * Always equal to an int
	 */
	private double thinSpaceWidth;
	/**
	 * Always equal to an int
	 */
	double lineH;
	/**
	 * Always equal to an int
	 */
	double childIndicatorWidth;
	/**
	 * Always equal to an int
	 */
	double checkBoxWidth;
	/**
	 * Always equal to an int
	 */
	double parentIndicatorWidth;
	/**
	 * Always equal to an int
	 */
	double scrollbarWidth;

	/**
	 * Space to the right and left of buttons. Set in setFontSize to the width
	 * of BUTTON_MARGIN_SPACES (always equals an integer).
	 */
	double buttonMargin;

	private transient @NonNull SoftReference<Hashtable<String, Double>> stringWidthsTable = initStringWidthsTable();

	APTextManager(final @NonNull Bungee _art) {
		art = _art;
		// call art.setFontSize() rather than setFontSize() so that preferences
		// are updated and all frames are informed.
		// art.setFontSize(BungeeConstants.DEFAULT_TEXT_SIZE);
		// setFontSize(BungeeConstants.DEFAULT_TEXT_SIZE, Integer.MAX_VALUE);
		// font = Util.nonNull(font);
	}

	/**
	 * @return Math.ceil() of the width
	 */
	double getStringWidth(final @NonNull String name) throws NullPointerException {
		assert UtilString.isNonEmptyString(name);

		double stringWidth;
		final Map<String, Double> table = getStringWidthsTable();
		final Double stringWidthD = table.get(name);
		if (stringWidthD != null) {
			stringWidth = stringWidthD;
		} else {
			// stringWidth =
			// Math.ceil(getTextMeasurer(name).getAdvanceBetween(0,
			// name.length()));
			stringWidth = UtilString.getStringWidth(name, font);
			table.put(name, stringWidth);
		}
		return stringWidth;
	}

	private @NonNull TextMeasurer getTextMeasurer(final @NonNull String name) throws NullPointerException {
		return APText.getTextmeasurer(name, getFont());
	}

	private synchronized @NonNull Map<String, Double> getStringWidthsTable() {
		Hashtable<String, Double> table = stringWidthsTable.get();
		if (table == null) {
			table = new Hashtable<>();
			stringWidthsTable = new SoftReference<>(table);
		}
		return table;
	}

	private synchronized @NonNull static SoftReference<Hashtable<String, Double>> initStringWidthsTable() {
		final Hashtable<String, Double> table = new Hashtable<>();
		return new SoftReference<>(table);
	}

	void checkboxesUpdated() {
		clearTextCaches();
	}

	void clearTextCaches() {
		// System.out.println("APTextManager.clearTextCaches");
		synchronized (stringWidthsTable) {
			stringWidthsTable.clear();
		}
	}

	/**
	 * @return whether the new _fontSize, constrained to [minFontSize,
	 *         maxFontSize], represents a change.
	 *
	 *         If maxFontSize < minFontSize, use minFontSize and let setTooSmall
	 *         deal with it.
	 */
	boolean setFontSize(int _fontSize, final int maxFontSize) {
		_fontSize = UtilMath.constrain(_fontSize, minLegibleFontSize, Math.max(minLegibleFontSize, maxFontSize));
		final boolean result = (_fontSize != font.getSize());
		if (result) {
			// System.out.println("APTextManager.setFontSize " + fontSize + " =>
			// " + _fontSize);
			fontSize = _fontSize;
			clearTextCaches();
			font = new Font(FONT_FAMILY, FONT_FACE, fontSize);
			assert fontSize == font.getSize() : fontSize + " " + getFont().getSize();
			assert font != null;
			lineH = APText.fontLineH(font);
			digitWidth = averageCharWidth("0123456789");
			commaWidth = averageCharWidth(",");
			hyphenWidth = averageCharWidth("-");
			thinSpaceWidth = averageCharWidth(THIN_SPACE);

			childIndicatorWidth = getStringWidth(CHILD_INDICATOR_SUFFIX);
			parentIndicatorWidth = getStringWidth(BungeeConstants.PARENT_INDICATOR_PREFIX);
			checkBoxWidth = getStringWidth(CHECKBOX_SPACES);
			buttonMargin = getStringWidth(BUTTON_MARGIN_SPACES);
			scrollbarWidth = Math.round(BungeeConstants.SCROLLWIDTH_TO_LINEH_RATIO * lineH);
		}
		return result;
	}

	/**
	 * @return Always equal to an int
	 */
	private double averageCharWidth(final @NonNull String s) {
		final int sLength = s.length();
		final int repeats = 1000 / sLength;
		final StringBuilder repeated = new StringBuilder();
		for (int i = 0; i < repeats; i++) {
			repeated.append(s);
		}
		return getStringWidth(Util.nonNull(repeated.toString())) / (sLength * repeats);
	}

	@NonNull
	Font getFont(final int style) {
		final String family = getFont().getFamily();
		final int size = getFont().getSize();
		return new Font(family, style, size);
	}

	/**
	 * constrainWidth/Height default to true; isWrap to false.
	 */
	@NonNull
	APText oneLineLabel() {
		final APText result = APText.oneLineLabel(getFont());
		return result;
	}

	/**
	 * constrainWidth/Height and isWrap default to true.
	 */
	public @NonNull APText getAPText() {
		return new APText(getFont());
	}

	int getFontSize() {
		return fontSize;
	}

	@NonNull
	Font getFont() {
		return font;
	}

	@NonNull
	String truncateText(final @NonNull String name, final double availableWidth) {
		assert UtilString.isNonEmptyString(name);
		String result = name;
		try {
			result = PiccoloUtil.truncateText(name, (float) availableWidth, getTextMeasurer(name));
		} catch (final NullPointerException e) {
			System.err.println("APTextManager.truncateText ignoring getTextMeasurer NullPointerXception and returning '"
					+ name + "' unchanged.");
		}
		assert result != null;
		return result;
	}

	/**
	 * Caller must account for padToNameW
	 *
	 * @return Always equal to an int
	 */
	double getFacetStringWidth(final @NonNull PerspectiveMarkupElement treeObject, final boolean showChildIndicator,
			final boolean showCheckBox) {
		assert !showCheckBox || treeObject.getParentPME() != null;
		return getStringWidth(treeObject.getName(), showChildIndicator && treeObject.isEffectiveChildren(),
				showCheckBox);
	}

	/**
	 * @return Always equal to an int
	 */
	double getStringWidth(final @NonNull String s, final boolean showChildIndicator, final boolean showCheckBox) {
		double result = 0.0;
		assert s != null;
		result = getStringWidth(s);
		if (showChildIndicator) {
			result += childIndicatorWidth;
		}
		if (showCheckBox) {
			result += checkBoxWidth;
		}
		return result;
	}

	/**
	 * Only called by Bungee.computeText()
	 *
	 * @return text, or NULL and call callback if element's perspective's name
	 *         isn't cached.
	 */
	@Nullable
	String computeText(final @NonNull PerspectiveMarkupElement element, final double numW, final double nameW,
			final boolean numFirst, final boolean maybeShowChildIndicator, final boolean maybeShowCheckBox,
			final boolean padToNameW, final @NonNull RedrawCallback redraw) {
		return computeText(element, numW, nameW, numFirst, maybeShowChildIndicator, maybeShowCheckBox, padToNameW,
				redraw, element.getOnCount());
	}

	/**
	 * Only called by MarkupViz
	 *
	 * @return text, or NULL and call callback if element is a
	 *         PerspectiveMarkupElement whose perspective's name isn't cached.
	 */
	@Nullable
	String computeText(final @NonNull MarkupElement element, final @NonNull RedrawCallback callback) {
		return computeText(element, -1.0, Double.POSITIVE_INFINITY, false, false, false, false, callback, 1);
	}

	/**
	 * All computeText calls go through here
	 *
	 * @param numW
	 *            if < 0, don't show count; if > 0 pad onCount to exactly this
	 *            width. If numFirst, add two space characters between onCount
	 *            and name; if !numFirst assume numW takes care of separating
	 *            them.
	 * @param nameW
	 *            if > 0, trucate name to this width.
	 * @param numFirst
	 *            put count before name. True for PerspectiveList, False for
	 *            PerspectiveViz
	 * @param maybeShowChildIndicator
	 *            checks for p.isEffectiveChildren()
	 * @param maybeShowCheckBox
	 *            checks for parent != null && Bungee.getShowCheckboxes()
	 * @param padToNameW
	 *            pad name to exactly match nameW?
	 * @param onCount
	 *            count to draw
	 *
	 * @return text, or NULL and call callback if element is a
	 *         PerspectiveMarkupElement whose perspective's name isn't cached.
	 *
	 *         Don't trim -- need initial spaces to position past checkboxes
	 */
	@Nullable
	String computeText(final @NonNull MarkupElement element, final double numW, final double nameW,
			final boolean numFirst, final boolean maybeShowChildIndicator, final boolean maybeShowCheckBox,
			final boolean padToNameW, final @Nullable RedrawCallback callback, final int onCount) {
		String text;
		assert nameW >= art.lineH() : nameW;
		if (element instanceof PerspectiveMarkupElement) {
			text = computeFacetText((PerspectiveMarkupElement) element, numW, nameW, numFirst, maybeShowChildIndicator,
					maybeShowCheckBox, padToNameW, callback, onCount);
		} else {
			text = truncateText(element.getName(callback), nameW);
		}
		return text;
	}

	/**
	 * Only called by computeText
	 *
	 * @return facet text, or NULL and call callback if name isn't cached.
	 */
	private @Nullable String computeFacetText(final @NonNull PerspectiveMarkupElement pme, final double numW,
			double nameW, final boolean numFirst, final boolean maybeShowChildIndicator,
			final boolean maybeShowCheckBox, final boolean padToNameW, final @Nullable RedrawCallback callback,
			final int onCount) {
		if (callback != null && pme.perspective.getNameOrDefaultAndCallback(null, callback) == null) {
			return null;
		}
		assert nameW == (int) nameW || (Double.isInfinite(nameW) && !padToNameW) : nameW;
		final StringBuilder labelText = new StringBuilder(100);
		nameW = maybeAppendCheckBox(pme, nameW, maybeShowCheckBox, labelText);
		final boolean showChildIndicator = maybeShowChildIndicator && pme.isEffectiveChildren();
		if (showChildIndicator) {
			assert nameW > childIndicatorWidth : pme + " labelText=" + labelText + " nameW=" + nameW
					+ " childIndicatorWidth=" + childIndicatorWidth;
			nameW -= childIndicatorWidth;
		}
		final String numPart = getOnCountString(pme, numW, onCount);
		if (numFirst) {
			labelText.append(numPart).append(BUTTON_MARGIN_SPACES);
		}
		final String namePart = truncateText(pme.getName(callback), nameW);
		assert namePart.length() > 0 : pme + " " + nameW;
		// can't append before truncation or the suffix would be truncated.
		labelText.append(namePart);
		if (showChildIndicator) {
			labelText.append(CHILD_INDICATOR_SUFFIX);
		}
		if (padToNameW) {
			labelText.append(stringForWidth(nameW - getStringWidth(namePart)));
		}
		if (!numFirst) {
			labelText.append(numPart);
		}
		final String result = labelText.toString();
		assert result.length() > 0 && !result.startsWith("<") : result + " element=" + pme;
		return result;
	}

	/**
	 * @param pme
	 *            only used in assert statement
	 * @param numW
	 *            negative means don't include onCount
	 * @param onCount
	 * @return
	 */
	private @NonNull String getOnCountString(final @NonNull PerspectiveMarkupElement pme, final double numW,
			final int onCount) {
		String numPart = "";
		if (numW >= 0.0) {
			final double excessNumWidth = numW - numWidth(onCount);
			assert excessNumWidth >= 0.0 : numW + " excessNumWidth=" + excessNumWidth + " " + pme.perspective
					+ " onCount=" + onCount;
			numPart = stringForWidth(excessNumWidth) + UtilString.addCommas(onCount);
		}
		return numPart;
	}

	/**
	 * @return always equal to an int.
	 */
	double numWidth(final int n) {
		final int absN = Math.abs(n);
		final int nDigits = 1 + (absN == 0 ? 0 : (int) Math.log10(absN));
		final int nCommas = nDigits < 4 ? 0 : (nDigits - 1) / 3;
		final double result = Math.ceil((n >= 0 ? 0.0 : hyphenWidth) + nDigits * digitWidth + nCommas * commaWidth);
		return result;
	}

	/**
	 * @return a thinSpacesString of width width. "" if width <= 0
	 */
	private @NonNull String stringForWidth(final double width) {
		final int nChars = Math.max(0, (int) (width / thinSpaceWidth + 0.5));
		return thinSpacesString(nChars);
	}

	private static @NonNull String[] thinSpaceStrings = UtilString.repeatStrings(THIN_SPACE, 200);

	/**
	 * Only called by stringForWidth()
	 */
	private static @NonNull String thinSpacesString(final int nChars) {
		if (thinSpaceStrings.length <= nChars) {
			thinSpaceStrings = UtilString.repeatStrings(THIN_SPACE, nChars + 50);
		}
		final String result = thinSpaceStrings[nChars];
		assert result != null;
		return result;
	}

	/**
	 * "maybe" means:
	 *
	 * maybeShowCheckBox
	 *
	 * && pme.getParent() != null
	 *
	 * && art.getShowCheckboxes()
	 *
	 * Barfs if it won't fit in nameW.
	 *
	 * @return is equal to an int if nameW is.
	 */
	private double maybeAppendCheckBox(final @NonNull PerspectiveMarkupElement pme, double nameW,
			final boolean maybeShowCheckBox, final @NonNull StringBuilder labelText) {
		final boolean showCheckBox = maybeShowCheckBox && pme.getParentPME() != null && art.getShowCheckboxes();
		if (showCheckBox) {
			assert nameW > checkBoxWidth : "Checkbox won't even fit, little less name: " + pme + " labelText="
					+ labelText + " nameW=" + nameW + " checkBoxWidth=" + checkBoxWidth + "\n path="
					+ pme.perspective.path();
			labelText.append(CHECKBOX_SPACES);
			nameW -= checkBoxWidth;
		}
		return nameW;
	}

}
