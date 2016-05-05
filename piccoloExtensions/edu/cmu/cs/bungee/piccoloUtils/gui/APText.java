package edu.cmu.cs.bungee.piccoloUtils.gui;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.assertInteger;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.font.TextMeasurer;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.event.EventListenerList;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode.PickableMode;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PInterpolatingActivity;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.event.PInputEventListener;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PPaintContext;
import edu.umd.cs.piccolo.util.PUtil;
import uk.org.bobulous.java.intervals.GenericInterval;
import uk.org.bobulous.java.intervals.Interval;
import uk.org.bobulous.java.intervals.Intervals;

/*
 * Created on Mar 5, 2005 @author mad Supports attributes like UNDERLINE. Also
 * checks whether there's really been a change before updating stuff.
 */
public class APText extends PText implements ExpandableText {

	protected static final long serialVersionUID = 1L;

	private static final FontRenderContext FONT_RENDER_CONTEXT = PPaintContext.RENDER_QUALITY_HIGH_FRC;

	private static final @NonNull Map<Font, Float> FONT_HEIGHTS = new Hashtable<>();

	private static final @NonNull Map<Collection<AttributeSpec>, HashMap<String, TextMeasurer>> TEXT_MEASURERS = new HashMap<>();

	@SuppressWarnings("null")
	protected static final @NonNull TextAttribute UNDERLINE = TextAttribute.UNDERLINE;

	@SuppressWarnings("null")
	protected static final @NonNull Integer UNDERLINE_ON = TextAttribute.UNDERLINE_ON;

	@SuppressWarnings("null")
	protected static final @NonNull TextAttribute FONT = TextAttribute.FONT;

	@SuppressWarnings("null")
	protected static final @NonNull TextAttribute BACKGROUND = TextAttribute.BACKGROUND;

	// private static final @NonNull Set<AttributedCharacterIterator.Attribute>
	// TM_IRRELEVANT_ATTRIBUTES = tmIrrelevantAttributes();
	//
	// private static final @NonNull Set<AttributedCharacterIterator.Attribute>
	// tmIrrelevantAttributes() {
	// final AttributedCharacterIterator.Attribute[] attrs = {
	// UNDERLINE, BACKGROUND };
	// return new HashSet<>(Arrays.asList(attrs));
	// }

	final @NonNull Set<AttributeSpec> txtAttributes = new HashSet<>();

	/**
	 * Ignore calls to recomputeLayout inside
	 * recomputeLayoutConstrainHeightWidth (because we calculate and set
	 * width/height explicitly), and when unconstraining width or height
	 * (because nothing will change).
	 */
	private boolean dontRecomputeLayout;

	private @NonNull PickableMode pickableMode = PickableMode.PICKABLE_MODE_AUTOMATIC;

	/**
	 * Must default to false, because constrainWidthToTextWidth defaults to
	 * true. NO! true is sensible if text contains newlines.
	 */
	private boolean isWrap = true;

	/**
	 * A float that is equal to an integer.
	 */
	private float lineH;

	private transient @Nullable AttributedCharacterIterator charIter;
	private transient @Nullable LineBreakMeasurer lbMeasurer;
	private transient @Nullable TextMeasurer tMeasurer;

	// Piccolo 1.2 made lines private, so have to copy all the code that uses
	// it.
	private transient TextLayout[] _lines;
	private transient TextLayout _line;

	private boolean isIncomplete;

	// private static int fff = 0;

	// private static int ggg = 0;

	private static final String gggShortClassName = "APText";

	public static final String gggName = "Color Key";

	public boolean isGGG() {
		return gggShortClassName != null && (gggName == null || (getText() != null && gggName.contains(getText())));
	}

	// TODO Remove unused code found by UCDetector
	// public boolean maybeIncfGGG() {
	// final boolean result = isGGG();
	// if (result) {
	// ggg++;
	// if (gggName != null || UtilMath.isPowerOfTwo(ggg)) {
	// UtilString.indent(gggShortClassName + " n text changes=" + ggg + " n
	// layouts=" + fff);
	// }
	// }
	// return result;
	// }

	/**
	 * constrainWidth/Height and isWrap default to true. justification defaults
	 * to LEFT_ALIGNMENT.
	 */
	public APText() {
		this(getDefaultFont());
	}

	private @NonNull static Font getDefaultFont() {
		assert DEFAULT_FONT != null;
		return DEFAULT_FONT;
	}

	// We override setFont to do extra stuff; it must always be called.
	/**
	 * constrainWidth/Height and isWrap default to true. justification defaults
	 * to LEFT_ALIGNMENT.
	 */
	@SuppressWarnings("null")
	public APText(final @NonNull Font font) {
		super();
		// More efficient to do nothing here and setText after all other
		// attributes are set.

		// In case there's mixed directions, want primary direction to be
		// left-to-right.
		setMyAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_LTR, YesNoMaybe.NO);
		setFontInternal(font, 0);
	}

	@Override
	public void enableExpandableText(final boolean enable) {
		if (enable) {
			addInputEventListener(ExpandableTextHover.EXPANDABLE_TEXT_HOVER);
		} else {
			removeInputEventListener(ExpandableTextHover.EXPANDABLE_TEXT_HOVER);
		}
	}

	/**
	 * constrainWidth/Height default to true; isWrap to false. justification
	 * defaults to LEFT_ALIGNMENT.
	 */
	public static @NonNull APText oneLineLabel(final @NonNull Font font) {
		final APText result = new APText(font);
		result.setWrap(false);
		return result;
	}

	@Override
	public boolean expand() {
		final boolean canExpand = isIncomplete;
		if (canExpand) {
			setWrap(true);
			setConstrainHeightToTextHeight(true);
			rerender();
			moveAncestorsToFront();
		}
		return canExpand;
	}

	@Override
	public boolean contract() {
		setWrap(false);
		final boolean canContract = getHeight() > lineH;
		if (canContract) {
			rerender();
			setConstrainHeightToTextHeight(false);
			setHeight(lineH);
		}
		return canContract;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "'" + getText() + "'");
	}

	static @NonNull Font fontForLineH(final @NonNull String name, final int style, final double lineH) {
		final int size = (int) (lineH * 0.75);
		final Font font = new Font(name, style, size);
		assert APText.fontLineH(font) <= lineH : " lineH=" + lineH + " font=" + font + " fontLineH(font)="
				+ APText.fontLineH(font);
		return font;
	}

	@Override
	public void setPickableMode(final @NonNull PickableMode _pickableMode) {
		pickableMode = _pickableMode;
		final boolean pickableState = (pickableMode == PickableMode.PICKABLE_MODE_AUTOMATIC) ? getVisible()
				: (pickableMode == PickableMode.PICKABLE_MODE_NEVER) ? false : true;
		setPickable(pickableState);
		setChildrenPickable(pickableState);
	}

	@Override
	public void setVisible(final boolean state) {
		if (getVisible() != state) {
			super.setVisible(state);
			setPickableMode(pickableMode);
		}
	}

	/**
	 * Whether to wrap (at word boundaries) to multiple lines.
	 */
	public void setWrap(final boolean _wrapText) {
		isWrap = _wrapText;
		if (isWrap) {
			setConstrainWidthToTextWidth(false);
		}
	}

	/**
	 * Whether to wrap (at word boundaries) to multiple lines.
	 */
	public boolean getWrap() {
		return isWrap;
	}

	@Override
	public void setXoffset(final double x) {
		setOffset(x, getYOffset());
	}

	@Override
	public void setYoffset(final double y) {
		setOffset(getXOffset(), y);
	}

	@Override
	public boolean setWidthHeight(final double w, final double h) {
		return setBounds(getX(), getY(), w, h);
	}

	@Override
	public boolean setBounds(final double x, final double y, final double w, final double h) {
		assert assertInteger(x) && assertInteger(y) && assertInteger(w) && assertInteger(h);
		assert w >= 0.0 && h >= 0.0 : w + " x " + h;
		final boolean result = super.setBounds(x, y, w, h);
		// super.setBounds() calls recomputeLayout, which can modify w/h.
		assert getX() == x && getY() == y : x + " " + y + " " + w + " " + h + " " + getBoundsReference();
		return result;
	}

	@Override
	public PInterpolatingActivity animateToBounds(final Rectangle2D bounds, final long duration) {
		return animateToBounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), duration);
	}

	@Override
	public void setOffset(final double x, final double y) {
		assert assertInteger(x);
		if (x != getXOffset() || y != getYOffset()) {
			super.setOffset(x, y);
		}
	}

	@Override
	public void setCenterX(final double x) {
		setXoffset(Math.rint(x - getWidth() * getScale() / 2.0));
	}

	@Override
	public void setCenterY(final double y) {
		setYoffset(Math.rint(y - getHeight() * getScale() / 2.0));
	}

	@Override
	public void setCenter(final Point2D.Double point) {
		setCenterX(point.getX());
		setCenterY(point.getY());
	}

	@Override
	public @NonNull Point2D.Double getCenter() {
		return new Point2D.Double(getCenterX(), getCenterY());
	}

	@Override
	public double getCenterX() {
		return getXOffset() + getWidth() * getScale() / 2.0;
	}

	@Override
	public double getCenterY() {
		return getYOffset() + getHeight() * getScale() / 2.0;
	}

	@Override
	public double getMaxX() {
		assert getX() == 0.0;
		return getXOffset() + getWidth() * getScale();
	}

	@Override
	public double getGlobalMaxX() {
		final PBounds globalBounds = getGlobalBounds();
		return globalBounds.getX() + globalBounds.getWidth();
	}

	@Override
	public double getIntMaxX() {
		return Math.ceil(getMaxX());
	}

	@Override
	public double getMaxY() {
		assert getY() == 0.0;
		return getYOffset() + getHeight() * getScale();
	}

	@Override
	public double getIntMaxY() {
		return Math.ceil(getMaxY());
	}

	@Override
	public void setScale(final double xScale, final double yScale) {
		if (xScale != getXscale() || yScale != getYscale()) {
			getTransformReference(true).scale(xScale, yScale);
			invalidatePaint();
			invalidateFullBounds();
			firePropertyChange(PROPERTY_CODE_TRANSFORM, PROPERTY_TRANSFORM, null, getTransformReference(true));
		}
	}

	@Override
	public double getXscale() {
		return getTransformReference(true).getScaleX();
	}

	@Override
	public double getYscale() {
		return getTransformReference(true).getScaleY();
	}

	@Override
	public void setScale(final double scale) {
		if (scale != getScale()) {
			scale(scale / getScale());
		}
	}

	@Override
	public void removeChildren(final Collection childrenNodes) {
		if (!childrenNodes.isEmpty()) {
			final Iterator<LazyNode> i = childrenNodes.iterator();
			while (i.hasNext()) {
				final PNode each = (PNode) i.next();
				each.setParent(null);
			}
			final List<LazyNode> _children = getChildrenReference();
			_children.removeAll(childrenNodes);
			invalidatePaint();
			invalidateFullBounds();

			firePropertyChange(PROPERTY_CODE_CHILDREN, PROPERTY_CHILDREN, null, _children);
		}
	}

	@Override
	public void addChildren(final Collection childrenNodes) {
		if (!childrenNodes.isEmpty()) {
			final List<PNode> _children = getChildrenReference();
			final Iterator<PNode> it = childrenNodes.iterator();
			while (it.hasNext()) {
				final PNode each = it.next();
				assert each.getParent() == null : each;
				each.setParent(this);
			}
			_children.addAll(childrenNodes);
			invalidatePaint();
			invalidateFullBounds();

			firePropertyChange(PROPERTY_CODE_CHILDREN, PROPERTY_CHILDREN, null, _children);
		}
	}

	/**
	 * For rotated text, work around Piccolo selection bugs.
	 */
	public boolean isUnderMouse(final double w, final boolean state, final PInputEvent e) {
		boolean result = true;
		if (state) {
			final Point2D mouseCoords = e.getPositionRelativeTo(this);
			final double x = mouseCoords.getX();
			final double _y = mouseCoords.getY();
			final double slop = 5.0;
			result = (x >= -slop && x <= w + slop && _y >= -slop && _y <= getHeight() + slop);
		}
		return result;
	}

	@Override
	public void moveAncestorsToFront() {
		edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil.moveAncestorsToFront(this);
	}

	@Override
	public void setTextPaint(final @Nullable Paint aPaint) {
		if (!Objects.deepEquals(aPaint, getTextPaint())) {
			super.setTextPaint(aPaint);
		}
	}

	@Override
	public void setText(@Nullable final String _text) {
		maybeSetText(_text, YesNoMaybe.MAYBE);
	}

	@Override
	public boolean maybeSetText(@Nullable final String _text) {
		return maybeSetText(_text, YesNoMaybe.MAYBE);
	}

	/**
	 * replaceAll("\n\n", "\n \n") and setText if changed.
	 *
	 * @return whether text changed
	 */
	public boolean maybeSetText(@Nullable String text, final @NonNull YesNoMaybe isRerender) {
		text = fixNewlines(text);
		final boolean result = !Objects.deepEquals(text, getText());
		if (result) {
			decacheCharIter();
			dontRecomputeLayout = !isRerender(result, isRerender);
			super.setText(text);
			dontRecomputeLayout = false;
		} else if (isRerender == YesNoMaybe.YES) {
			rerender();
		}
		return result;
	}

	private static final Pattern FIX_NEWLINES_PATTERN = Pattern.compile("\n\\s+\n\\s+\n");

	/**
	 * @param text
	 *            can be null
	 */
	protected static String fixNewlines(String text) {
		if (text != null) {
			while (text.indexOf("\n\n") >= 0) {
				// recomputeLayout doesn't deal well with empty lines
				text = text.replace("\n\n", "\n \n");
			}
			while (text.indexOf("\n \n \n") >= 0) {
				// lose repeated empty lines
				text = FIX_NEWLINES_PATTERN.matcher(text).replaceAll("\n \n");
			}
		}
		return text;
	}

	/**
	 * Only called by TextBox.setText()
	 *
	 * Interpret HTML, replaceAll("\n\n", "\n \n") and setText if changed.
	 *
	 * @return whether text changed
	 */
	boolean maybeSetHTML(@Nullable String html, final @NonNull YesNoMaybe isRerender) {
		boolean result = !Objects.deepEquals(html, getText());
		if (result) {
			html = interpretHTML(html);
			result = !Objects.deepEquals(html, getText());
		}
		if (result) {
			decacheCharIter();
			dontRecomputeLayout = !isRerender(result, isRerender);
			super.setText(html);
			dontRecomputeLayout = false;
		} else if (isRerender == YesNoMaybe.YES) {
			rerender();
		}
		return result;
	}

	private static final Pattern IS_HTML_PATTERN = Pattern.compile("</([biu]|em|strong)>", Pattern.CASE_INSENSITIVE);

	/**
	 * group1=<anything>
	 *
	 * < group2=b|i|u|em|strong <anything> >
	 *
	 * group3=<anything>
	 *
	 * </<group2>>
	 *
	 * group4=<anything>
	 *
	 * return group1 + group3 + group4, and add attribute (UNDERLINE or POSTURE)
	 * on group3.
	 */
	private static final Pattern HTML_PATTERN = Pattern.compile("(.*)<([biu]|em|strong)(?:\\W[^>]*)?>(.*)</\\2>(.*)",
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	/**
	 * Replace
	 * <p>
	 * and <br>
	 * with "\n \n".
	 */
	private static final Pattern HTML_PATTERN1 = Pattern.compile("<p[^>]*>|<br>", Pattern.CASE_INSENSITIVE);

	/**
	 * Remove end markers for
	 * <p>
	 * , <br>
	 * , and <font>
	 */
	private static final Pattern HTML_PATTERN2 = Pattern.compile("</(?:p|br|font)>", Pattern.CASE_INSENSITIVE);

	/**
	 * Remove opening markers for <font> and <?xml>
	 */
	private static final Pattern HTML_PATTERN3 = Pattern.compile("(?:font|\\?xml)(?:\\W[^>]*)?>",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Side effect: adds AttributeSpecs (like UNDERLINE) to this APText.
	 */
	@Nullable
	String interpretHTML(@Nullable String html) {
		if (html != null) {
			html = html.replace("&nbsp;", " ");
			if (IS_HTML_PATTERN.matcher(html).find()) {
				html = HTML_PATTERN1.matcher(html).replaceAll("\n \n");
				html = HTML_PATTERN2.matcher(html).replaceAll("");
				html = HTML_PATTERN3.matcher(html).replaceAll("");

				html = fixNewlines(html);
				while (true) {
					final Matcher m = HTML_PATTERN.matcher(html);
					if (m.find()) {
						final String group1 = m.group(1);
						// final String group3 = fixNewlines(m.group(3));
						final String group3 = m.group(3);
						html = group1 + group3 + m.group(4);
						Attribute attribute = null;
						Object value = null;
						final String tag = m.group(2).toLowerCase(Locale.getDefault());
						switch (tag) {

						case "b":
						case "strong":
						case "u":
							attribute = UNDERLINE;
							value = UNDERLINE_ON;
							break;

						case "em":
						case "i":
							attribute = TextAttribute.POSTURE;
							value = TextAttribute.POSTURE_OBLIQUE;
							break;

						default:
							assert false : tag + " " + html;
							break;
						}
						assert attribute != null && value != null;
						addMyAttribute(attribute, value, group1.length(), group1.length() + group3.length(),
								YesNoMaybe.NO);
					} else {
						break;
					}
				}
			} else {
				html = fixNewlines(html);
			}
		}
		return html;
	}

	private static final TextAttribute[] PRIMARY_TEXT_ATTRIBUTES = { TextAttribute.FAMILY, TextAttribute.WEIGHT,
			TextAttribute.WIDTH, TextAttribute.POSTURE, TextAttribute.SIZE, TextAttribute.TRANSFORM,
			TextAttribute.SUPERSCRIPT, TextAttribute.TRACKING };

	private void maybeReplaceFont(final Attribute attribute) {
		if (ArrayUtils.contains(PRIMARY_TEXT_ATTRIBUTES, attribute)) {
			replaceFont();
		}
	}

	protected void replaceFont() {
		final AttributeSpec prev = clearAttribute(FONT, YesNoMaybe.NO);
		if (prev != null) {
			final Font _font = (Font) prev.value;
			// clearAttribute(FONT, YesNoMaybe.NO);
			final Map<TextAttribute, ?> fontAttributes = _font.getAttributes();
			for (final TextAttribute primaryAttribute : PRIMARY_TEXT_ATTRIBUTES) {
				assert primaryAttribute != null;
				final Object fontsValue = fontAttributes.get(primaryAttribute);
				if (fontsValue != null) {
					setMyAttribute(primaryAttribute, fontsValue, YesNoMaybe.NO);
				}
			}
		}
	}

	@Override
	public void setFont(final Font aFont) {
		if (aFont != getFont()) {
			assert aFont != null;
			setFontInternal(aFont, getNlines());
		}
	}

	private void setFontInternal(final @NonNull Font aFont, final int nLines) {
		lineH = fontLineH(aFont);
		setHeight(lineH * nLines);
		if (nLines > 0) {
			final double sizeRatio = aFont.getSize2D() / getFont().getSize2D();
			if (sizeRatio > 1.0) {
				setWidth(Math.ceil(getWidth() * sizeRatio));
			}
		}
		addMyAttribute(FONT, aFont, 0, -1, YesNoMaybe.NO);
		super.setFont(aFont);
	}

	/**
	 * @return a float that is equal to an integer.
	 */
	public static float fontLineH(final @NonNull Font font) {
		Float h = FONT_HEIGHTS.get(font);
		if (h == null) {
			h = new Float(Math.ceil(font.getLineMetrics("Wwy`^_{", FONT_RENDER_CONTEXT).getHeight()));
			FONT_HEIGHTS.put(font, h);
		}
		return h.floatValue();
	}

	/**
	 * @return a float that is equal to an integer.
	 */
	public float getLineH() {
		return lineH;
	}

	@Override
	// default is true
	public void setConstrainWidthToTextWidth(final boolean _constrainWidthToTextWidth) {
		if (_constrainWidthToTextWidth != isConstrainWidthToTextWidth()) {
			if (_constrainWidthToTextWidth) {
				setWrap(false);
			} else {
				dontRecomputeLayout = !_constrainWidthToTextWidth;
				// No need to recomputeLayout when setting to false
			}
			super.setConstrainWidthToTextWidth(_constrainWidthToTextWidth);
			dontRecomputeLayout = false;
		}
	}

	@Override
	// default is true
	public void setConstrainHeightToTextHeight(final boolean _constrainHeightToTextHeight) {
		if (_constrainHeightToTextHeight != isConstrainHeightToTextHeight()) {
			dontRecomputeLayout = !_constrainHeightToTextHeight;
			// No need to recomputeLayout when setting to false
			super.setConstrainHeightToTextHeight(_constrainHeightToTextHeight);
			dontRecomputeLayout = false;
		}
	}

	/*
	 * Default is LEFT_ALIGNMENT
	 */
	@Override
	public void setJustification(final float just) {
		if (just != getJustification()) {
			super.setJustification(just);
		}
	}

	@Override
	public @NonNull PInterpolatingActivity animateToBounds(final double x, final double y, final double width,
			final double height, final long duration) {
		final @NonNull PBounds dst = new PBounds(x, y, width, height);

		final PInterpolatingActivity ta = new PInterpolatingActivity(duration, PUtil.DEFAULT_ACTIVITY_STEP_RATE) {

			@SuppressWarnings("null")
			private final @NonNull PBounds src = getBounds();

			@Override
			public void setRelativeTargetValue(final float zeroToOne) {
				APText.this.setBounds(Math.ceil(lerp(zeroToOne, src.x, dst.x)),
						Math.ceil(lerp(zeroToOne, src.y, dst.y)), Math.ceil(lerp(zeroToOne, src.width, dst.width)),
						Math.ceil(lerp(zeroToOne, src.height, dst.height)));
				assert getWidth() >= 0f : zeroToOne;
			}
		};

		addActivity(ta);
		return ta;

	}

	private boolean isNonEmptyText() {
		return UtilString.isNonEmptyString(getText());
	}

	/**
	 * rerender iff (isRerender == YesNoMaybe.MAYBE ? isChange : isRerender ==
	 * YesNoMaybe.YES)
	 */
	void maybeRerender(final boolean isChange, final @NonNull YesNoMaybe isRerender) {
		if (isRerender(isChange, isRerender)) {
			rerender();
		} else if (isChange) {
			decacheCharIter();
		}
	}

	/**
	 * @return isRerender == YesNoMaybe.MAYBE ? isChange : isRerender ==
	 *         YesNoMaybe.YES
	 */
	static boolean isRerender(final boolean isChange, final @NonNull YesNoMaybe isRerender) {
		return isRerender == YesNoMaybe.MAYBE ? isChange : isRerender == YesNoMaybe.YES;
	}

	private void rerender() {
		if (isNonEmptyText()) {
			decacheCharIter();
			invalidatePaint();
			recomputeLayout();
		}
	}

	private void decacheCharIter() {
		charIter = null;
		lbMeasurer = null;
		tMeasurer = null;
		_lines = null;
		_line = null;
	}

	/**
	 * Update BACKGROUND color
	 */
	public boolean updateSearchHighlighting(final @Nullable Pattern pattern, final @NonNull YesNoMaybe isRerender) {
		boolean isChange = clearAttribute(BACKGROUND, YesNoMaybe.NO) != null;
		if (pattern != null && UtilString.isNonEmptyString(getText())) {
			// twice darker so legible even if highlighting changes
			final Color color = ((Color) getTextPaint()).darker().darker();
			assert color != null;
			final Matcher matcher = pattern.matcher(getText());
			while (matcher.find()) {
				if (highlight(color, matcher.start(), matcher.end(), YesNoMaybe.NO)) {
					isChange = true;
				}
			}
		}
		maybeRerender(isChange, isRerender);
		return isChange;
	}

	/**
	 * @return a previous AttributeSpec for attribute. If non-null,
	 *         txtAttributes changed.
	 */
	AttributeSpec clearAttribute(final @NonNull Attribute attribute, final @NonNull YesNoMaybe isRerender) {
		AttributeSpec result = null;

		// I have no idea why foo is needed. If you iterate directly on
		// txtAttributes, the attribute is not removed. mad 2016/04
		final HashSet<AttributeSpec> foo = new HashSet<>(txtAttributes);
		for (final Iterator<AttributeSpec> it = foo.iterator(); it.hasNext();) {
			final AttributeSpec spec = it.next();
			if (spec.attribute.equals(attribute)) {
				result = spec;
				it.remove();
			}
		}
		if (result != null) {
			txtAttributes.clear();
			txtAttributes.addAll(foo);
			assert assertAttributeCleared(attribute);
		}
		maybeRerender(result != null, isRerender);
		return result;
	}

	@SuppressWarnings("null")
	private static final @NonNull Collection<TextAttribute> RUN_DIRECTION_OR_FONT = Arrays
			.asList(TextAttribute.RUN_DIRECTION, FONT);

	/**
	 * Remove everything from txtAttributes except RUN_DIRECTION and FONT, and
	 * return whether it changed.
	 */
	public boolean clearMostAttributes() {
		boolean result = false;
		for (final Iterator<AttributeSpec> it = txtAttributes.iterator(); it.hasNext();) {
			final AttributeSpec spec = it.next();
			if (!RUN_DIRECTION_OR_FONT.contains(spec.attribute)) {
				result = true;
				it.remove();
			}
		}
		if (result) {
			assert assertAttributesCleared(RUN_DIRECTION_OR_FONT);
			rerender();
		}
		return result;
	}

	private boolean assertAttributeCleared(final @NonNull Attribute attribute) {
		for (final AttributeSpec spec : txtAttributes) {
			assert spec.attribute != attribute : spec;
		}
		return true;
	}

	private boolean assertAttributesCleared(final @NonNull Collection<TextAttribute> attributes) {
		for (final AttributeSpec spec : txtAttributes) {
			assert attributes.contains(spec.attribute) : spec;
		}
		return true;
	}

	/**
	 * Set BACKGROUND color
	 *
	 * @return whether attribute changed
	 */
	public boolean highlight(final @NonNull Color color, final int beginIndex, final int endIndex,
			final @NonNull YesNoMaybe isRerender) {
		return addMyAttribute(BACKGROUND, color,
				GenericInterval.getClosedGenericInterval(beginIndex, getEndIndex(endIndex)), isRerender);
	}

	/**
	 * @param endIndex
	 *            <0 means no limit
	 */
	static @Nullable Integer getEndIndex(final int endIndex) {
		return endIndex < 0 ? null : endIndex;
	}

	@SuppressWarnings("null")
	public void setBoldFont(final int beginIndex, final int endIndex, final @NonNull YesNoMaybe isRerender) {
		addMyAttribute(FONT, getFont().deriveFont(Font.BOLD),
				GenericInterval.getClosedGenericInterval(beginIndex, getEndIndex(endIndex)), isRerender);
	}

	/**
	 * @return whether attribute changed
	 */
	public boolean addMyAttribute(final @NonNull AttributedCharacterIterator.Attribute attribute,
			final @NonNull Object value, final int beginIndex, final int endIndex,
			final @NonNull YesNoMaybe isRerender) {
		return addMyAttribute(attribute, value,
				GenericInterval.getClosedGenericInterval(beginIndex, getEndIndex(endIndex)), isRerender);
	}

	/**
	 * @return whether attribute changed
	 */
	boolean addMyAttribute(final @NonNull AttributedCharacterIterator.Attribute attributeToAdd,
			final @NonNull Object valueToAdd, final @NonNull Interval<Integer> intervalToAdd,
			final @NonNull YesNoMaybe isRerender) {
		final Intervals<Integer> intervalsToAdd = new Intervals<>(intervalToAdd);
		boolean isChanged = false;
		boolean isAdded = false;
		for (final Iterator<AttributeSpec> it = txtAttributes.iterator(); it.hasNext();) {
			final AttributeSpec existingAttributeSpec = it.next();
			if (existingAttributeSpec.attribute.equals(attributeToAdd)) {
				// If values are equal, combine; else remove old value from
				// intervalToAdd
				if (existingAttributeSpec.value.equals(valueToAdd)) {
					isAdded = true;
					final Intervals<Integer> newIntervals = existingAttributeSpec.intervals.add(intervalToAdd);
					if (newIntervals != existingAttributeSpec.intervals) {
						existingAttributeSpec.intervals = newIntervals;
						decacheCharIter();
						isChanged = true;
					}
				} else if (intervalsToAdd.includes(existingAttributeSpec.intervals)) {
					it.remove();
					isChanged = true;
				} else {
					final Intervals<Integer> newIntervals = existingAttributeSpec.intervals.subtract(intervalToAdd);
					if (newIntervals != existingAttributeSpec.intervals) {
						existingAttributeSpec.intervals = newIntervals;
						isChanged = true;
					}
				}
			}
		}
		return changeAttributeInternal(attributeToAdd, valueToAdd, intervalsToAdd, isRerender, isChanged, isAdded);
	}

	/**
	 * Replaces previous value[s]
	 *
	 * @param attribute
	 * @param value
	 *            Depending on the attribute, value could be a Color, a Font, an
	 *            Integer, ...
	 * @return whether attribute changed
	 */
	boolean setMyAttribute(final @NonNull AttributedCharacterIterator.Attribute attribute, final @NonNull Object value,
			final @NonNull Intervals<Integer> intervals, final @NonNull YesNoMaybe isRerender) {
		maybeReplaceFont(attribute);
		boolean isChanged = false;
		boolean isSet = false;
		for (final Iterator<AttributeSpec> it = txtAttributes.iterator(); it.hasNext();) {
			final AttributeSpec existingAttributeSpec = it.next();
			if (existingAttributeSpec.attribute.equals(attribute)) {
				// If values are equal, combine; else remove old value from
				// intervalToAdd
				if (existingAttributeSpec.value.equals(value)) {
					isSet = true;
					if (!existingAttributeSpec.intervals.equals(intervals)) {
						existingAttributeSpec.intervals = intervals;
						decacheCharIter();
						isChanged = true;
					}
				} else {
					isChanged = true;
					it.remove();
				}
			}
		}
		return changeAttributeInternal(attribute, value, intervals, isRerender, isChanged, isSet);
	}

	private boolean changeAttributeInternal(final @NonNull AttributedCharacterIterator.Attribute attribute,
			final @NonNull Object value, final @NonNull Intervals<Integer> intervals,
			final @NonNull YesNoMaybe isRerender, boolean isChanged, final boolean isSet) {
		assert assertConsistentAttributeIntervals();
		if (!isSet) {
			txtAttributes.add(new AttributeSpec(attribute, value, intervals));
			isChanged = true;
		}
		if (isChanged) {
			decacheCharIter();
		}
		maybeRerender(isChanged, isRerender);
		return isChanged;
	}

	private boolean assertConsistentAttributeIntervals() {
		for (final AttributeSpec spec1 : txtAttributes) {
			boolean isPastIt1 = false;
			for (final AttributeSpec spec2 : txtAttributes) {
				if (!isPastIt1) {
					isPastIt1 = spec2 == spec1;
				} else if (spec1.attribute == spec2.attribute) {
					assert !spec1.value.equals(spec2.value);
					assert !spec1.intervals.overlaps(spec2.intervals);
				}
			}
		}
		return true;
	}

	void setMyAttribute(final @NonNull AttributedCharacterIterator.Attribute attribute, final @NonNull Object value,
			final @NonNull YesNoMaybe isRerender) {
		setMyAttribute(attribute, value, universalIntervals, isRerender);
	}

	public void setMyAttribute(final @NonNull AttributedCharacterIterator.Attribute attribute,
			final @NonNull Object value, final int beginIndex, final int endIndex,
			final @NonNull YesNoMaybe isRerender) {
		setMyAttribute(attribute, value, getIntervals(beginIndex, endIndex), isRerender);
	}

	static final @NonNull Intervals<Integer> universalIntervals = new Intervals<>(
			GenericInterval.getClosedGenericInterval(0, null));

	/**
	 * @param beginIndex
	 *            >=0
	 * @param endIndex
	 *            <0 means no limit
	 */
	static @NonNull Intervals<Integer> getIntervals(final int beginIndex, final int endIndex) {
		if (beginIndex == 0 && endIndex < 0) {
			return universalIntervals;
		} else {
			return new Intervals<>(GenericInterval.getClosedGenericInterval(beginIndex, getEndIndex(endIndex)));
		}
	}

	@Override
	public void setUnderline(final boolean isUnderline, final @NonNull YesNoMaybe isRerender) {
		if (isUnderline) {
			setMyAttribute(UNDERLINE, UNDERLINE_ON, isRerender);
		} else {
			clearAttribute(UNDERLINE, isRerender);
		}
	}

	private AttributedCharacterIterator getCharIter() {
		final String _text = getText();
		if (UtilString.isNonEmptyString(_text) && charIter == null) {
			assert _text != null;
			charIter = getCharIter(_text, txtAttributes);
		}
		return charIter;
	}

	private static AttributedCharacterIterator getCharIter(final @NonNull String _text,
			final @Nullable Collection<AttributeSpec> txtAttributes2) {
		final AttributedString atString = new AttributedString(_text);
		if (txtAttributes2 != null) {
			for (final AttributeSpec spec : txtAttributes2) {
				// for (final AttributeSpec spec : specs) {
				// System.out.println(attrSpecOffset + " " +
				// Util.valueOfDeep(spec));
				if (spec.intervalsIncludes(_text)) {
					atString.addAttribute(spec.attribute, spec.value);
				} else {
					assert _text != null;
					for (final Interval<Integer> interval : spec.intervals) {
						assert interval != null;
						final Interval<Integer> intervalExplicit = intervalExplicit(interval, _text);
						final int start = Util.nonNull(intervalExplicit.getLowerEndpoint());
						final int end = Util.nonNull(intervalExplicit.getUpperEndpoint());
						if (start < _text.length() && end > 0) {
							atString.addAttribute(spec.attribute, spec.value, Math.max(0, start),
									Math.min(_text.length(), end));
						}
					}
				}
			}
		}
		return atString.getIterator();
	}

	static @NonNull Interval<Integer> intervalExplicit(final @NonNull Interval<Integer> interval,
			final @NonNull String _text) {
		return interval.intersection(getTextInterval(_text));
	}

	static @NonNull GenericInterval<Integer> getTextInterval(final @NonNull String _text) {
		return GenericInterval.getClosedGenericInterval(0, _text.length());
	}

	private @NonNull LineBreakMeasurer getLBmeasurer() {
		if (lbMeasurer != null) {
			lbMeasurer.setPosition(0);
		} else {
			final AttributedCharacterIterator attributedCharacterIterator = getCharIter();
			if (attributedCharacterIterator != null) {
				lbMeasurer = new LineBreakMeasurer(attributedCharacterIterator, FONT_RENDER_CONTEXT);
			}
		}
		assert lbMeasurer != null;
		return lbMeasurer;
	}

	private @NonNull TextMeasurer getTextmeasurer() throws NullPointerException {
		if (tMeasurer == null) {
			final String _text = getText();
			assert _text != null;
			tMeasurer = getTextmeasurer(_text, txtAttributes);
		}
		assert tMeasurer != null;
		return tMeasurer;
	}

	public static @NonNull TextMeasurer getTextmeasurer(final @NonNull String text, final @NonNull Font font)
			throws NullPointerException {
		return getTextmeasurer(text, getFontAttributeSpec(font));
	}

	private static final Map<Font, Collection<AttributeSpec>> FONT_ATTRIBUTE_SPECS = new HashMap<>();

	private static Collection<AttributeSpec> getFontAttributeSpec(final @NonNull Font font) {
		Collection<AttributeSpec> result = FONT_ATTRIBUTE_SPECS.get(font);
		if (result == null) {
			result = Collections.singletonList(new AttributeSpec(FONT, font));
			FONT_ATTRIBUTE_SPECS.put(font, result);
		}
		return result;
	}

	private static @NonNull TextMeasurer getTextmeasurer(final @NonNull String text,
			final Collection<AttributeSpec> _txtAttributes) throws NullPointerException {
		assert text.length() > 0;
		// Collection<AttributeSpec>
		// relevantTxtAttributes=relevantTxtAttributes(_txtAttributes);
		HashMap<String, TextMeasurer> map = TEXT_MEASURERS.get(_txtAttributes);
		if (map == null) {
			map = new HashMap<>();
			TEXT_MEASURERS.put(_txtAttributes, map);
		}
		TextMeasurer tMeasurer = map.get(text);
		if (tMeasurer == null) {
			try {
				tMeasurer = new TextMeasurer(getCharIter(text, _txtAttributes), FONT_RENDER_CONTEXT);
				// testForDuplicateTMs(tMeasurer, text, _txtAttributes, map);
				map.put(text, tMeasurer);
			} catch (final NullPointerException e) {
				System.err.println("While APText.getTextmeasurer('" + text + "', " + _txtAttributes + "):\n");
				throw (e);
			}
		}
		assert tMeasurer != null;
		return tMeasurer;
	}

	// private static Collection<AttributeSpec>
	// relevantTxtAttributes(Collection<AttributeSpec> attributeSpecs){
	// Collection<AttributeSpec>result=attributeSpecs;
	// for(AttributeSpec attributeSpec:attributeSpecs) {
	// if(TM_IRRELEVANT_ATTRIBUTES.contains(attributeSpec.attribute)) {
	// if(result==attributeSpecs) {
	// result=new LinkedList<>(attributeSpecs);
	// }
	// result.remove(attributeSpec);
	// }
	// }
	// return result;
	// }
	//
	// private static void testForDuplicateTMs(final TextMeasurer tMeasurer,
	// final String text,
	// final Collection<AttributeSpec> _txtAttributes, final HashMap<String,
	// TextMeasurer> map) {
	// final float[] tmBaselineOffsets = getBaselineOffsets(tMeasurer);
	// for (final Entry<Collection<AttributeSpec>, HashMap<String,
	// TextMeasurer>> foo : TEXT_MEASURERS.entrySet()) {
	// for (final Entry<String, TextMeasurer> bar : foo.getValue().entrySet()) {
	// final TextMeasurer value = bar.getValue();
	// final float[] baselineOffsets = getBaselineOffsets(value);
	// if (Arrays.equals(baselineOffsets, tmBaselineOffsets) &&
	// bar.getKey().equals(text)) {
	// assert _txtAttributes.equals(foo.getKey()) :
	// UtilArray.symmetricDifferences(_txtAttributes,
	// foo.getKey());
	// System.err.println(/* baselineOffsets + " " + */ foo.getKey() + " \n" +
	// bar.getKey()
	// + "\n duplicates " + text + "\n identical maps=" + bar.equals(map) +
	// "\n");
	// }
	// }
	// }
	// }
	//
	// private static float[] getBaselineOffsets(final TextMeasurer tm) {
	// return tm.getLayout(0, tm.getLineBreakIndex(0,
	// 9999)).getBaselineOffsets();
	// }

	/**
	 * @return the number of lines required to fully display this APText.
	 */
	public int getNlines() {
		if (isNonEmptyText() && ensureLayout()) {
			if (_line != null) {
				return 1;
			} else {
				return _lines.length;
			}
		} else {
			return 0;
		}
	}

	protected boolean assertLineXORlines(final String msg) {
		assert _lines != null ^ _line != null : " isWrap=" + isWrap + " availableWidth=" + getAvailableWidth() + msg
				+ " getText='" + getText() + "'";
		return true;
	}

	/**
	 * Compute the bounds of the text wrapped by this node. The text layout is
	 * wrapped based on the bounds of this node.
	 */
	@Override
	public void recomputeLayout() {
		final String text = getText();
		if (UtilString.isNonEmptyString(text)) {
			// assert !(isWrap && isConstrainWidthToTextWidth()) : isWrap + " "
			// + isConstrainWidthToTextWidth();
			final float availableWidth = getAvailableWidth();
			// System.err.println("APText.recomputeLayout " + this
			// + " isConstrainWidthToTextWidth()="
			// + isConstrainWidthToTextWidth() + " availableWidth="
			// + availableWidth + " dontRecomputeLayout="
			// + dontRecomputeLayout + " getCharIter()=" + getCharIter()
			// + " getText()=" + getText());

			if (!dontRecomputeLayout && availableWidth > 0f && getCharIter() != null) {
				// if (gggShortClassName != null && gggName != null &&
				// getText().contains(gggName)
				// && UtilString.shortClassName(this).equals(gggShortClassName))
				// {
				// fff++;
				// UtilString.indent("recomputeLayout '" + getText() + "'
				// availableWidth=" + availableWidth + " lineH="
				// + lineH + " " + getFont() + "\n" + txtAttributes + "\n" +
				// charIter.getAttributes()
				//
				// // +
				// //
				// edu.cmu.cs.bungee.piccoloUtils.gui.Util.ancestorString(this)
				// // + "\n" + UtilString.getStackTrace()
				//
				// );
				// }
				final int nextLineBreakOffset = nextLineBreakOffset(0);
				_lines = null;
				_line = null;
				try {
					if (isWrap) {
						recomputeLayoutWrap(availableWidth);
					} else {
						recomputeLayoutNoWrap(availableWidth, nextLineBreakOffset);
					}
				} catch (final NullPointerException e) {
					System.err.println(
							"APText.recomputeLayout ignoring getTextMeasurer NullPointerXception for '" + text + "'.");
					// _line=new TextLayout(text, getFont(),
					// PPaintContext.RENDER_QUALITY_HIGH_FRC);
					_line = new TextLayout(getCharIter(), FONT_RENDER_CONTEXT);
				}
				assert assertLineXORlines(" nextLineBreakOffset=" + nextLineBreakOffset);
				recomputeLayoutConstrainHeightWidth(/* textWidth, textHeight */);
			}
			// No! Sometimes dontRecomputeLayout because we'll redraw later.
			// assert _lines != null || _line != null;
		} else {
			_lines = null;
			_line = null;
		}
	}

	public float getAvailableWidth() {
		return isConstrainWidthToTextWidth() ? Float.MAX_VALUE : (float) getWidth();
	}

	private void recomputeLayoutNoWrap(final float availableWidth, final int nextLineBreakOffset) {
		final TextMeasurer textMeasurer = getTextmeasurer();
		final int firstCharThatWontFit = Math.min(nextLineBreakOffset,
				textMeasurer.getLineBreakIndex(0, availableWidth));
		isIncomplete = firstCharThatWontFit <= getText().length();
		// System.out.println("APText.recomputeLayoutNoWrap " + this + "\n
		// firstCharThatWontFit=" + firstCharThatWontFit
		// + " getText().length()=" + getText().length() + " isIncomplete=" +
		// isIncomplete);
		assert firstCharThatWontFit > 0 && firstCharThatWontFit <= getText().length() : this
				+ "\n firstCharThatWontFit=" + firstCharThatWontFit + " availableWidth=" + availableWidth + " '"
				+ getText() + "' nextLineBreakOffset=" + nextLineBreakOffset
				+ " firstCharThatWontFit in Float.MAX_VALUE=" + textMeasurer.getLineBreakIndex(0, Float.MAX_VALUE);
		_line = textMeasurer.getLayout(0, firstCharThatWontFit);
		assert _line != null;
		assert assertLineXORlines("");
	}

	private void recomputeLayoutWrap(final float availableWidth) {
		final List<TextLayout> linesList = new LinkedList<>();
		final LineBreakMeasurer lineBreakMeasurer = getLBmeasurer();
		assert charIter != null;
		final int endIndex = charIter.getEndIndex();
		while (lineBreakMeasurer.getPosition() < endIndex) {
			final int nextLineBreakOffset = nextLineBreakOffset(lineBreakMeasurer.getPosition());

			final TextLayout aTextLayout = computeNextLayout(lineBreakMeasurer, availableWidth, nextLineBreakOffset);
			if (lineBreakMeasurer.getPosition() == nextLineBreakOffset && nextLineBreakOffset < endIndex) {
				lineBreakMeasurer.setPosition(nextLineBreakOffset + 1);
			}
			linesList.add(aTextLayout);
		}
		isIncomplete = false;
		// System.out.println("APText.recomputeLayoutWrap " + this + "\n
		// isIncomplete=" + isIncomplete
		// + " linesList.size()=" + linesList.size() + "
		// isConstrainHeightToTextHeight()="
		// + isConstrainHeightToTextHeight() + " isConstrainWidthToTextWidth()="
		// + isConstrainWidthToTextWidth()
		// + " availableWidth=" + availableWidth);
		assert linesList.size() > 0 : availableWidth + " " + getText();
		_lines = linesList.toArray(new TextLayout[linesList.size()]);
	}

	/**
	 * @return if text[measurerPosition]=='\n' return measurerPosition+1
	 *
	 *         else index of first '\n' following measurerPosition, if any
	 *
	 *         else Integer.MAX_VALUE
	 */
	private int nextLineBreakOffset(final int measurerPosition) {
		int nextLineBreakOffset = getText().indexOf('\n', measurerPosition);
		if (nextLineBreakOffset == -1) {
			nextLineBreakOffset = Integer.MAX_VALUE;
		} else if (nextLineBreakOffset == measurerPosition) {
			nextLineBreakOffset++;
		}
		return nextLineBreakOffset;
	}

	private void recomputeLayoutConstrainHeightWidth() {
		dontRecomputeLayout = true;
		if (isConstrainWidthToTextWidth()) {
			double textWidth = 0.0;
			assert assertLineXORlines("");
			if (_line != null) {
				textWidth = _line.getAdvance();
				assert textWidth > 0f : _line;
			} else {
				for (final TextLayout aTextLayout : _lines) {
					assert aTextLayout.getAdvance() > 0f : aTextLayout;
					textWidth = Math.max(textWidth, aTextLayout.getAdvance());
				}
			}
			super.setWidth(Math.ceil(textWidth));
		}
		if (isConstrainHeightToTextHeight()) {
			super.setHeight(lineH * getNlines());
		}
		dontRecomputeLayout = false;
	}

	@Override
	protected void paint(final PPaintContext paintContext) {
		// we want to call super.super.paint() = PNode
		// Since this is hidden by PText, copy PNode.paint code:

		Graphics2D g2 = null;
		if (getPaint() != null) {
			g2 = paintContext.getGraphics();
			g2.setPaint(getPaint());
			g2.fill(getBoundsReference());
		}

		if (getTextPaint() != null // && isNonEmptyText() this may slow it down
				&& getFont().getSize() * paintContext.getScale() > greekThreshold && ensureLayout()) {
			if (g2 == null) {
				g2 = paintContext.getGraphics();
			}
			g2.setPaint(getTextPaint());

			float y = getTextTop();
			final float bottomY = getTextBottom();
			assert assertLineXORlines("");
			if (_line != null) {
				paintOneLine(g2, y, bottomY, _line);
			} else {
				for (final TextLayout textLayout : _lines) {
					assert textLayout != null;
					y = paintOneLine(g2, y, bottomY, textLayout);
					if (y > bottomY) {
						break;
					}
				}
			}
		}
	}

	/**
	 * @return whether layout is possible (e.g. not if availableW==0)
	 */
	private boolean ensureLayout() {
		if (_line == null && _lines == null && getAvailableWidth() > 0f) {
			recomputeLayout();
		}
		return _lines != null ^ _line != null;
	}

	/**
	 * @return y-coordinate of top line (which will be negative if scrolled up
	 *         above the displayed text.)
	 */
	protected float getTextTop() {
		return (float) getY();
	}

	protected float getTextBottom() {
		return (float) (getY() + getHeight()) - getLineH();
	}

	protected float paintOneLine(final @NonNull Graphics2D g2, final float y, final float bottomY,
			final @NonNull TextLayout textLayout) {
		if (shouldPaintLine(bottomY, y)) {
			final float offset = (((float) getWidth()) - textLayout.getAdvance()) * getJustification();
			textLayout.draw(g2, ((float) getX()) + offset, y + textLayout.getAscent());
		}
		return y + getLineH();
	}

	/**
	 * @return whether to paint line, considering that it might be scrolled
	 *         above displayed text.
	 */
	@SuppressWarnings("static-method")
	protected boolean shouldPaintLine(final float bottomY, final float y) {
		return bottomY >= y;
	}

	@Override
	public boolean setBoundsFromFullBounds() {
		return false;
	}

	/**
	 * Don't wait for normal damage control
	 */
	@Override
	public void repaintNow() {
		validateFullBounds();
		validateFullPaint();
	}

	@Override
	public void removeInputListeners() {
		final EventListenerList listenerList = getListenerList();
		final PInputEventListener[] listeners = listenerList.getListeners(PInputEventListener.class);
		for (final PInputEventListener listener : listeners) {
			removeInputEventListener(listener);
		}
	}

	@Override
	public @NonNull PNode pNode() {
		return this;
	}

	@Override
	public void setMouseDoc(final String doc) {
		if (getParent() instanceof MouseDoc) {
			((MouseDoc) getParent()).setMouseDoc(doc);
		}
	}

	@Override
	public @NonNull String nodeDesc() {
		return " text='" + getText() + "' getTextPaint=" + getTextPaint();
	}

}

class AttributeSpec implements Serializable {
	final @NonNull AttributedCharacterIterator.Attribute attribute;
	final @NonNull Object value;
	@NonNull
	Intervals<Integer> intervals;

	AttributeSpec(final @NonNull AttributedCharacterIterator.Attribute _attribute, final @NonNull Object _value) {
		this(_attribute, _value, APText.universalIntervals);
	}

	AttributeSpec(final @NonNull AttributedCharacterIterator.Attribute _attribute, final @NonNull Object _value,
			final @NonNull Intervals<Integer> _intervals) {
		attribute = _attribute;
		value = _value;
		intervals = _intervals;
	}

	public boolean intervalsIncludes(final @Nullable String _text) {
		if (_text == null) {
			return true;
		} else {
			return intervals.includes(APText.getTextInterval(_text));
		}
	}

	@Override
	public String toString() {
		return UtilString.toString(this, attribute + "=" + value + " " + intervals);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + attribute.hashCode();
		result = prime * result + intervals.hashCode();
		result = prime * result + value.hashCode();
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final AttributeSpec other = (AttributeSpec) obj;
		// if (attribute == null) {
		// if (other.attribute != null) {
		// return false;
		// }
		// } else
		if (!attribute.equals(other.attribute)) {
			return false;
		}
		// if (intervals == null) {
		// if (other.intervals != null) {
		// return false;
		// }
		// } else
		if (!intervals.equals(other.intervals)) {
			return false;
		}
		// if (value == null) {
		// if (other.value != null) {
		// return false;
		// }
		// } else
		if (!value.equals(other.value)) {
			return false;
		}
		return true;
	}

}
