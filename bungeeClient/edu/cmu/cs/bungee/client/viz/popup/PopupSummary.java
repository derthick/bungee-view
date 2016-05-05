package edu.cmu.cs.bungee.client.viz.popup;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.TopTags;
import edu.cmu.cs.bungee.client.query.TopTags.TagRelevance;
import edu.cmu.cs.bungee.client.query.explanation.Distribution;
import edu.cmu.cs.bungee.client.query.explanation.Explanation;
import edu.cmu.cs.bungee.client.query.explanation.ExplanationTask;
import edu.cmu.cs.bungee.client.query.explanation.FacetSelection;
import edu.cmu.cs.bungee.client.query.markup.DefaultMarkup;
import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.MarkupStringElement;
import edu.cmu.cs.bungee.client.query.markup.PerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.query.query.Query.DescriptionCategory;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.markup.BungeeAPText;
import edu.cmu.cs.bungee.client.viz.markup.MarkupViz;
import edu.cmu.cs.bungee.client.viz.tagWall.TagWall;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph;
import edu.cmu.cs.bungee.javaExtensions.graph.Node;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.Alignment;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyGraph;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.activities.PActivity;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * see <a href="C:\Projects\ArtMuseum\DesignSketches\popupColors.xcf">color key
 * diagram</a>
 */
public final class PopupSummary extends LazyPNode implements RedrawCallback {

	private static final MarkupStringElement HAVING_A_KNOWN = MarkupStringElement.getElement(" having a known");
	/**
	 * How long to keep minimal popup
	 */
	private static final long START_FRAME_DELAY = 2000L;
	/**
	 * How long the move should take
	 */
	private static final long START_FRAME_ANIMATION_DURATION = 1000L;
	/**
	 * How long after the move starts before things start fading in.
	 */
	private static final long START_FRAME_ANIMATION_DELAY = 0L;

	private static final double BIG_TEXT_SCALE = 1.4;
	private static final float TRANSPARENT = 0.0f;
	private static final float OPAQUE = 1.0f;

	/**
	 * Non-text colors
	 */

	/**
	 * Gold background that appears as a border in the no-help case, and a PNode
	 * text paint in help mode.
	 */
	private static final @NonNull Color GOLD_BORDER_COLOR = BungeeConstants.GOLD_BORDER_COLOR;
	@SuppressWarnings("null")
	private static final @NonNull Color NO_HELP_BG_COLOR = Color.getHSBColor(0f, 0f, 0.1f);

	/**
	 * Text colors
	 */
	private static final @NonNull Color COUNT_TEXT_COLOR = BungeeConstants.TEXT_FG_COLOR;
	private static final @NonNull Color UNIMPORTANT_TEXT_COLOR = Util.nonNull(COUNT_TEXT_COLOR.darker());
	@SuppressWarnings("null")
	private static final @NonNull Color ON_PERCENT_COLOR = Color.getHSBColor(0.15f, 0.4f, 0.9f);

	public Perspective facet;
	public transient InfluenceDiagramCreator influenceDiagramCreator;

	final @NonNull Bungee art;

	/**
	 * E.g. "Items having". w = textElementW().
	 */
	private final @NonNull MarkupViz namePrefix;
	/**
	 * E.g. "20th Century". w = textElementW().
	 */
	private final @NonNull MarkupViz name;
	/**
	 * E.g. "1,001". Only visible from START_FRAME.
	 *
	 * BOTTOM_LEFT aligns with PopupSummary's TOP_LEFT
	 */
	private final @NonNull APText totalCount;
	/**
	 * NO_FRAME E.g. "53 / 83 = 60%"
	 *
	 * after that, e.g. " in restricted set"
	 */
	private final @NonNull MarkupViz totalDesc;
	/**
	 * E.g. "25%"
	 */
	private final @NonNull APText onPercent;
	/**
	 * E.g. "499"
	 */
	private final @NonNull APText onCount;
	/**
	 * E.g. " of them satisfy all 1 filters, compared with"
	 */
	private final @NonNull MarkupViz onDesc;
	/**
	 * E.g. "25%"
	 */
	private final @NonNull APText siblingPercent;
	/**
	 * E.g. "for the other 500 Europes". w = textElementW().
	 */
	private final @NonNull APText siblingDesc;
	/**
	 * E.g. "1 / 23"
	 */

	/**
	 * transparency varies, but at least partially opaque for Step.NO_FRAME
	 */
	private final @NonNull LazyPNode totalBG = new LazyPNode();
	/**
	 * The gold border. Visible after Step.START_FRAME
	 */
	private final @NonNull LazyPNode goldBorder = new LazyPNode();
	private final @NonNull APText[] textNodes;
	private final @NonNull PNode[] textElementWnodes;

	/**
	 * Maps primary facets to an Explanation, which may also contain extra
	 * explanatory facets.
	 */
	private final @NonNull Map<SortedSet<Perspective>, Explanation> explanations = new HashMap<>();
	private final @NonNull Alignment alignment;

	// private int facetTotalCount;

	// These are non-null iff we're showing median
	private @Nullable Perspective conditionalMedian;
	private @Nullable Perspective unconditionalMedian;

	/**
	 * Scales the explanation speed; the larger it is, the slower the
	 * explanation animation; that is, the steps from START_ANIMATION to
	 * END_ANIMATION
	 */
	private long animationSpeed = 1L;

	private @NonNull Step currentStep = Step.NO_POPUP;

	private @Nullable PNode anchor;
	private LazyGraph<Perspective> influenceDiagram;
	private EulerDiagram observedEulerDiagram;
	private EulerDiagram predictedEulerDiagram;

	/**
	 * Query version when setFacet was last called. If version changes, hide()
	 */
	private int queryVersion = -1;
	private boolean showMedian;

	/**
	 * UtilMath.roundToInt(UtilMath.constrain(w, art.lineH() * 20.0, art.w *
	 * 0.8))
	 *
	 * Width of totalBG and influenceDiagram. Gold border is wider; Text
	 * elements are narrower. Should not change with Steps.
	 */
	private double bgW;

	public PopupSummary(final @NonNull Bungee _art) {
		alignment = new Alignment();
		art = _art;

		name = getMarkupViz(Color.DARK_GRAY);
		name.setScale(BIG_TEXT_SCALE);

		namePrefix = getMarkupViz(UNIMPORTANT_TEXT_COLOR.darker());

		totalCount = newAPText();
		totalCount.setTextPaint(COUNT_TEXT_COLOR);

		totalDesc = getMarkupViz(UNIMPORTANT_TEXT_COLOR);

		onPercent = newAPText();
		onPercent.setTextPaint(ON_PERCENT_COLOR);

		onCount = newAPText();
		onCount.setTextPaint(COUNT_TEXT_COLOR);

		onDesc = getMarkupViz(UNIMPORTANT_TEXT_COLOR);

		siblingPercent = newAPText();
		siblingPercent.setTextPaint(ON_PERCENT_COLOR);

		siblingDesc = newAPText();
		siblingDesc.setTextPaint(UNIMPORTANT_TEXT_COLOR);

		goldBorder.setPaint(GOLD_BORDER_COLOR);
		totalBG.setPaint(NO_HELP_BG_COLOR);

		addPermanentChildren();

		textNodes = textNodes();
		textElementWnodes = textElementWnodes();

		setPickableMode(PickableMode.PICKABLE_MODE_NEVER);
		setVisible(false);
	}

	private @NonNull APText[] textNodes() {
		final APText[] _texts = { totalCount, onPercent, onCount, siblingPercent, siblingDesc };
		return _texts;
	}

	private @NonNull PNode[] textElementWnodes() {
		final PNode[] _texts = { name, namePrefix, siblingDesc };
		return _texts;
	}

	public void queryValidRedraw(final @Nullable Pattern textSearchPattern) {
		for (int i = 0; i < getChildrenCount(); i++) {
			final PNode child = getChild(i);
			if (child instanceof BungeeAPText) {
				((BungeeAPText) child).queryValidRedraw(textSearchPattern);
			}
		}
	}

	/**
	 * Summary.showPopup is always called before this, so we know everthing is
	 * reset
	 *
	 * @param _anchor
	 */
	public void setFacet(final @NonNull Perspective _facet, final boolean _showMedian, final @NonNull PNode _anchor) {
		showMedian = _showMedian;
		facet = _facet;
		if (showMedian) {
			conditionalMedian = facet.getMedianPerspective(true);
			unconditionalMedian = facet.getMedianPerspective(false);
		}
		anchor = _anchor;
		currentStep = Step.NO_POPUP;
		setVisible(true);
		init();
	}

	@Override
	public void redrawCallback() {
		if (facet != null) {
			init();
		}
	}

	// Only used when replaying
	public void computeInfluenceDiagramNow(final @NonNull Perspective _facet) {
		showMedian = false;
		facet = _facet;
		anchor = null;
		initNoPopup();
		currentStep = Step.NO_HELP;
		init();
	}

	/**
	 * If isUpToDate(), replay to currentStep. Otherwise, maybeRedrawFacetDesc
	 * will be called when isUpToDate.
	 *
	 * Called by setFacet and maybeRedrawFacetDesc.
	 */
	private void init() {
		try {
			if (isUpToDate()) {
				if (currentStep == Step.NO_POPUP) {
					initNoPopup();
					performNextStep();
				} else {
					final Step restoreTo = currentStep;
					final long currentAnimationSpeed = getAnimationSpeed();
					setAnimationSpeed(0L);
					for (currentStep = Step.NO_POPUP; currentStep.compareTo(restoreTo) < 0; currentStep = Util
							.nonNull(currentStep.next())) {
						actions();
					}
					setAnimationSpeed(currentAnimationSpeed);
				}
				moveToFront();
			}
		} catch (final Exception e) {
			System.err.println("Warning: In Popup.init facet=" + facet);
			throw (e);
		}
	}

	private void initNoPopup() {
		assert getParent() == tagWall();
		setBounds(0.0, 0.0, 1.0, 1.0);
		setVisible(true);
		double _bgW = art.getStringWidth(facet.getName()) * BIG_TEXT_SCALE;
		if (_bgW > art.w * 0.5) {
			// break name into at most 2 lines
			_bgW /= 2.0;
		}
		setBGw(_bgW + art.lineH() * 3.0);
	}

	/**
	 * @return whether query is valid and facetName is cached; queues redraw or
	 *         getName if not.
	 */
	boolean isUpToDate() {
		boolean result = false;
		if (!isHidden()) {
			final Query query = query();
			final int _queryVersion = query.version();
			if (_queryVersion < 0) {
				query.queueRedraw(this);
			} else {
				queryVersion = _queryVersion;
				final String facetName = facet.getNameOrDefaultAndCallback(null, this);
				result = facetName != null;

				// Defer anchorForPopup() to here, when query and PVs must be
				// valid.
				if (result && anchor == null) {
					anchor = tagWall().anchorForPopup(Util.nonNull(facet));
					// if (anchor == null) {
					// result = false;
					// query.queueOrRedraw(this);
					// }
				}
			}
		}
		return result;
	}

	/**
	 * 1. terminateAnimationJobs
	 *
	 * 2. constrain(_bgW, art.lineH() * 20.0, art.w * 0.8)
	 *
	 * 3. Set totalBG bounds from {name, namePrefix, totalDesc, siblingDesc,
	 * onDesc};
	 *
	 * 4. Set Popup.this bounds from (totalBG, influenceDiagram)
	 *
	 * 5. align this with top right of header
	 */
	private void setBGw(double _bgW) {
		alignment.terminateAnimationJobs();
		_bgW = Math.rint(UtilMath.constrain(_bgW, art.lineH() * 20.0, art.w * 0.8));
		if (_bgW != bgW) {
			bgW = _bgW;
			setWidth(textElementWnodes, textElementW());
			setTotalBGbounds();

			if (currentStep.compareTo(Step.START_FRAME) >= 0) {
				alignment.animateToAlignment(this, Alignment.TOP_RIGHT, art.getHeader(), Alignment.TOP_RIGHT,
						-buttonMargin(), buttonMargin(),
						(isAnchorable() && currentStep != Step.NO_HELP) ? START_FRAME_ANIMATION_DURATION : 0L);
			}
		}
	}

	private @NonNull APText newAPText() {
		final APText result = art.oneLineLabel();
		// initialize so globalBoundsCheck will succeed.
		result.maybeSetText("<uninitialized>");
		return result;
	}

	/**
	 * @return Wrapping MarkupViz with constrainHeightToContent
	 */
	private @NonNull MarkupViz getMarkupViz(final @Nullable Paint textColor) {
		final MarkupViz markupViz = new MarkupViz(art, textColor);
		markupViz.setIsWrapText(true);
		markupViz.constrainHeightToContent = true;
		markupViz.setRedrawer(this);
		return markupViz;
	}

	private void addPermanentChildren() {
		addChild(goldBorder);
		addChild(totalBG);
		addChild(name);
		addChild(namePrefix);
		addChild(totalCount);
		addChild(totalDesc);

		addChild(onPercent);
		addChild(onCount);
		addChild(onDesc);
		addChild(siblingPercent);
		addChild(siblingDesc);
	}

	/**
	 * fade with no delay and default animation duration.
	 */
	private void fadeNoDelay(final @NonNull PNode[] nodes, final float transparency) {
		fade(nodes, 0L, fadeDuration(), transparency);
	}

	private void fade(final @NonNull PNode[] nodes, final long delay, final long duration, final float transparency) {
		for (final PNode node : nodes) {
			assert node != null : UtilString.valueOfDeep(nodes);
			if (transparency != node.getTransparency()) {
				if (transparency > 0.0f && node.getTransparency() == 0.0f) {
					// Gross hack to tell fitToNodes that this node should be
					// considered, even before animateToTransparency gets
					// started.
					node.setTransparency(Float.MIN_VALUE);
				}
				alignment.trackAnimationJob(edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil.animateToTransparency(node,
						transparency, delay, duration - delay));
			}
		}
	}

	private static void setWidth(final @NonNull PNode[] nodes, final double w) {
		for (final PNode node : nodes) {
			node.setWidth((int) (w / node.getScale()));
		}
	}

	private void visibility(final @NonNull PNode[] nodes, final boolean isVisible) {
		fade(nodes, 0L, 0L, isVisible ? OPAQUE : TRANSPARENT);
	}

	private void delay(final long duration) {
		assert duration >= 0L;
		if (duration != 0L) {
			final PActivity job = new PActivity(duration);
			addActivity(job);
			alignment.trackAnimationJob(job);
		}
	}

	private static @NonNull PNode[] nodes(final @NonNull PNode... nodes) {
		assert nodes != null;
		return nodes;
	}

	private void actions() {
		// System.out.println("PopupSummary.actions currentStep=" + currentStep
		// + " really?=" + !maybeHide());
		if (!maybeHide()) {

			noFrameActions();

			namePrefixActions();
			nameActions();

			totalCountActions();
			totalDescActions();

			if (query().isExtensionallyRestricted()) {
				onDescActions();
				onCountActions();
				onPercentActions();

				onNsiblingFadeActions();
				siblingInitActions();
			}

			totalBGActions();
			goldBorderActions();
			globalActions();
		}
	}

	// Compartmentalize these that are 'obvious'
	// Many popup PNodes have empty text or contents when they shouldn't be
	// visible, instead of making them transparent.
	/**
	 * Set fonts, visibility, and a few widths
	 */
	private void noFrameActions() {
		if (currentStep == Step.NO_FRAME) {

			final PNode[] maybeVisibles = { goldBorder, totalCount, onCount, onPercent, onDesc, siblingPercent,
					siblingDesc };
			visibility(maybeVisibles, !isAnchorable());

			// Turn most everything off initially
			PNode[] invisibles = { namePrefix };
			if (!query().isExtensionallyRestricted()) {
				final PNode[] moreInvisibles = { onCount, onPercent, onDesc, siblingPercent, siblingDesc };
				invisibles = UtilArray.append(invisibles, moreInvisibles);
			}
			assert invisibles != null;
			visibility(invisibles, false);

			// Turn 2 things on
			visibility(nodes(name, totalDesc), true);

			// These widths won't change during popup
			setWidth(textElementWnodes, textElementW());

			assert name.getXmargin() == 0.0;
			assert name.getYmargin() == 0.0;
		}
	}

	private void globalActions() {
		final double buttonMargin = buttonMargin();
		if (currentStep == Step.NO_FRAME) {
			setTransform(null);
			Alignment.setBoundsFromNodes(this, nodes(name, totalDesc), buttonMargin, buttonMargin);
			assert anchor != null;
			Alignment.align(this, Alignment.CENTER_LEFT, anchor, Alignment.CENTER_RIGHT);
			delay(START_FRAME_DELAY);
		} else if (currentStep == Step.START_FRAME) {
			computeAndShowInfluenceDiagram();

			// Popup's parent (tagWall) must be in front of header during popups
			tagWall().moveToFront();
			alignment.animateToAlignment(this, Alignment.TOP_RIGHT, art.getHeader(), Alignment.TOP_RIGHT, -buttonMargin,
					buttonMargin, isAnchorable() ? START_FRAME_ANIMATION_DURATION : 0L);
		}
	}

	private void totalCountActions() {
		if (currentStep == Step.START_FRAME) {
			totalCount.maybeSetText(totalCountString());
			onPercent.maybeSetText(percentOn(null).toString());

			// Have to interleave facet- and total- alignment

			// vertical
			Alignment.align(totalCount, Alignment.TOP, name, Alignment.BOTTOM, 0.0, buttonMargin());
			Alignment.align(onPercent, Alignment.TOP, totalDesc, Alignment.BOTTOM, 0.0, buttonMargin());

			// horizontal
			// Add extra x-margin so if siblingPercent is longer, it will still
			// have some margin.
			Alignment.align(onPercent, Alignment.LEFT, name, Alignment.LEFT, 2.0 * buttonMargin(), 0.0);
			Alignment.align(totalCount, Alignment.LEFT, onPercent, Alignment.RIGHT, buttonMargin(), 0.0);

			fade(nodes(totalCount), 0L, 0L, OPAQUE);
		}
	}

	/**
	 * @return !node.isEmpty()
	 */
	private static boolean setContentAndLayout(final @NonNull MarkupViz markupViz,
			final @NonNull Markup uncompiledMarkup) {
		markupViz.setContent(uncompiledMarkup);
		final boolean result = !markupViz.isEmpty();
		if (result) {
			markupViz.layout();
		}
		return result;
	}

	private void totalDescActions() {
		if (currentStep == Step.NO_FRAME) {
			// need to set text first, or global bounds will be empty
			setWidth(nodes(totalDesc), textElementW());
			setContentAndLayout(totalDesc, noFrameDesc());
			Alignment.align(totalDesc, Alignment.TOP_LEFT, name, Alignment.BOTTOM_LEFT, 0.0, buttonMargin());
		} else if (currentStep == Step.START_FRAME /* && totalCount() >= 0 */) {
			assert totalCount() >= 0 : facet.path(true, true);

			// Our width is smaller than the others
			setWidth(nodes(totalDesc), remainingTextElementW(totalCount));

			setContentAndLayout(totalDesc, totalDescTotalCountDescription());

			// We use a space in the text (via collectionDescription) for
			// separation rather than margins
			Alignment.align(totalDesc, Alignment.TOP_LEFT, totalCount, Alignment.TOP_RIGHT);

			Alignment.align(onPercent, Alignment.TOP, totalDesc, Alignment.BOTTOM, 0.0, buttonMargin());
		}
	}

	private void namePrefixActions() {
		final PNode[] nodes = nodes(namePrefix);
		if (currentStep == Step.START_FRAME) {
			if (setContentAndLayout(namePrefix, namePrefixContents())) {
				Alignment.align(namePrefix, Alignment.BOTTOM_LEFT, name, Alignment.TOP_LEFT, 0.0, -buttonMargin());
				fade(nodes, START_FRAME_ANIMATION_DELAY, START_FRAME_ANIMATION_DURATION, OPAQUE);
			}
		}
	}

	private void nameActions() {
		if (currentStep == Step.NO_FRAME) {
			setContentAndLayout(name, nameContents());

			Alignment.align(name, Alignment.TOP_LEFT, this, Alignment.TOP_LEFT, buttonMargin(), 0.0);
		}
	}

	private final @NonNull Step[] totalBGsteps = { Step.NO_FRAME, Step.START_FRAME };

	private void totalBGActions() {
		if (ArrayUtils.contains(totalBGsteps, currentStep)) {
			if (currentStep == Step.NO_FRAME) {
				totalBG.setTransparency(0.5f);
			} else {
				fade(nodes(totalBG), START_FRAME_ANIMATION_DELAY, START_FRAME_ANIMATION_DURATION, OPAQUE);
			}
			setTotalBGbounds();
		}
	}

	/**
	 * Set totalBG bounds from {name, namePrefix, totalDesc, siblingDesc,
	 * onDesc};
	 *
	 * Then set Popup.this bounds from (totalBG, influenceDiagram)
	 */
	private void setTotalBGbounds() {
		Alignment.setBoundsFromNodes(totalBG, nodes(name, namePrefix, totalDesc, siblingDesc, onDesc), buttonMargin(),
				buttonMargin());
		Alignment.setBoundsFromNodes(this, nodes(totalBG, influenceDiagram), 0.0, 0.0);
	}

	/**
	 * Always equal to an int
	 */
	private double buttonMargin() {
		return art.buttonMargin();
	}

	private void goldBorderActions() {
		if (currentStep == Step.START_FRAME) {
			fade(nodes(goldBorder), START_FRAME_ANIMATION_DELAY, START_FRAME_ANIMATION_DURATION, OPAQUE);

			Alignment.setBoundsFromNodes(goldBorder, nodes(totalBG), buttonMargin(), buttonMargin());
		}
	}

	private void onNsiblingFadeActions() {
		final PNode[] nodes = nodes(onPercent, onCount, onDesc, siblingPercent, siblingDesc);
		if (currentStep == Step.START_FRAME) {
			fade(nodes, START_FRAME_ANIMATION_DELAY, START_FRAME_ANIMATION_DURATION, OPAQUE);
		}
	}

	private void onPercentActions() {
		Alignment.align(siblingPercent, Alignment.TOP_RIGHT, onCount, Alignment.TOP_LEFT, -buttonMargin(), 0.0);
	}

	private boolean isShowMedian() {
		return unconditionalMedian != null;
	}

	private void onCountActions() {
		onCount.maybeSetText(onCountString());

		Alignment.align(onCount, Alignment.TOP_RIGHT, onDesc, Alignment.TOP_LEFT);
	}

	private @NonNull String onCountString() {
		return UtilString.addCommas(onCount());
	}

	private void onDescActions() {
		final double remainingTextElementW = remainingTextElementW(onCount);
		assert remainingTextElementW > 0.0 : onCount.getGlobalBounds() + " " + bgW;
		setWidth(nodes(onDesc), remainingTextElementW);

		if (setContentAndLayout(onDesc, onDescription())) {
			Alignment.align(onDesc, Alignment.TOP_LEFT, totalDesc, Alignment.BOTTOM_LEFT, 0.0, buttonMargin());
		}
	}

	/**
	 * Only called by onDescActions.
	 *
	 * @return " of them satisfy all n filters, [compared with] \n"
	 *         <significanceColor> " median: " <perspective> ["p<0.01"]
	 */
	private @NonNull Markup onDescription() {
		final StringBuilder buf = new StringBuilder();
		buf.append(" of them satisfy all ").append(query().nFilters(true, true, true)).append(" filters");
		if (siblingTotalCount() > 0) {
			buf.append(", compared with");
		}
		final Markup result = addMedianContent(Util.nonNull(buf.toString()), true);
		return result;
	}

	private void siblingInitActions() {
		if (currentStep == Step.START_FRAME) {
			// Text never changes, plus we're using attributes to have the
			// percentage in a different color
			String siblingPercentText = "";
			String siblingDescText = "";
			final int siblingTotalCount = siblingTotalCount();
			if (siblingTotalCount > 0) {
				final StringBuffer siblingPercentBuf = new StringBuffer();
				siblingPercentText = siblingPercentOn(siblingPercentBuf).toString();
				siblingDescText = siblingDescText(siblingTotalCount);

				siblingPercent.clearMostAttributes();
			}
			siblingPercent.maybeSetText(siblingPercentText);
			siblingDesc.maybeSetText(siblingDescText);
		}

		Alignment.align(siblingPercent, Alignment.TOP, onDesc, Alignment.BOTTOM, 0.0, buttonMargin());
		Alignment.align(siblingPercent, Alignment.RIGHT, onPercent, Alignment.RIGHT);
		Alignment.align(siblingDesc, Alignment.TOP_LEFT, siblingPercent, Alignment.TOP_RIGHT, buttonMargin(), 0.0);
		setWidth(nodes(siblingDesc), remainingTextElementW(siblingPercent));
	}

	private @NonNull String siblingDescText(final int siblingTotalCount) {
		String siblingDescText;
		final StringBuffer siblingDescBuf = new StringBuffer();
		siblingDescBuf.append("for the other [").append(UtilString.addCommas(facet.parentTotalCount())).append("−")
				.append(UtilString.addCommas(totalCount())).append("=] ")
				.append(UtilString.addCommas(siblingTotalCount)).append(" ").append(parentDescString());
		assert facet != null;
		art.appendPvalue(facet, siblingDescBuf);
		siblingDescText = siblingDescBuf.toString();
		assert siblingDescText != null;
		return siblingDescText;
	}

	void performNextStep() {
		if (currentStep.next() != null && !alignment.isTerminatingAnimations() && art.isReady()) {
			alignment.terminateAnimationJobs();
			if (/* getRoot() != null && */!maybeHide()) {
				setCurrentStep(Util.nonNull(currentStep.next()));

				// perform
				actions();

				// handle next step
				final long delay = alignment.maxFinishTime() - System.currentTimeMillis();
				if (delay > 0L) {
					final PActivity ta = new PActivity(delay) {
						@Override
						protected void activityFinished() {
							super.activityFinished();
							if (isUpToDate()) {
								performNextStep();
							}
						}
					};
					addActivity(ta);
					alignment.trackAnimationJob(ta);
				} else {
					if (animationSpeed > 0L) {
						System.err.println("Popup.performNextStep immediately??? " + currentStep);
					}
					performNextStep();
				}
			}
		}
	}

	private void setCurrentStep(final @NonNull Step _currentStep) {
		currentStep = _currentStep;
	}

	private @NonNull String totalCountString() {
		final String result = totalCount() >= 0 ? UtilString.addCommas(totalCount()) : " ";
		return result;
	}

	/**
	 * @return "items having a known {facet type}" or, e.g. " that are 5xx cm² "
	 *         .
	 */
	private @NonNull Markup namePrefixContents() {
		final Markup description = DefaultMarkup.emptyMarkup();

		if (facet.getParent() == null) {
			description.add(query().getGenericObjectMarkup(true));
			description.add(HAVING_A_KNOWN);
		} else {
			final MarkupStringElement pattern = facet.getDescriptionPreposition().getPattern(true);
			if (pattern != null) {
				final String content = pattern.getName().replace(DefaultMarkup.FILTER_TYPE_SUBSTITUTE, '…');
				assert content != null;
				description.add(content);
			}
		}
		// PerspectiveMarkupElement.getPhraseFromRestrictions(facet.getDescriptionPreposition(),
		// facet.getDescriptionCategory(), new
		// Restrictions(Collections.singleton(facet), Collections.EMPTY_SET))

		// return deletePMEs(description);
		return description;
	}

	// // Only called by namePrefixContents
	// private @NonNull Markup deletePMEs(final @NonNull Markup facetDescList) {
	// for (final ListIterator<MarkupElement> it = facetDescList.listIterator();
	// it.hasNext();) {
	// final MarkupElement element = it.next();
	// if (element instanceof PerspectiveMarkupElement || element ==
	// DescriptionCategory.OBJECT) {
	// it.remove();
	// } else if (element == DescriptionCategory.CONTENT || element ==
	// DescriptionCategory.META) {
	// it.set(query().getGenericObjectMarkup(true));
	// }
	// }
	// return facetDescList;
	// }

	private @NonNull Markup nameContents() {
		final boolean isPlural = facet.getParent() != null
				&& facet.getDescriptionCategory() == DescriptionCategory.OBJECT;
		final Markup v = losePrefixes(facet.getMarkupElement(isPlural, false).description());
		return v;
	}

	/**
	 * This discards anything before a PerspectiveMarkupElement (e.g. COLOR and
	 * PLURAL)
	 */
	private static @NonNull Markup losePrefixes(final @NonNull Markup markup) {
		final Markup result = DefaultMarkup.emptyMarkup();
		boolean startRecording = false;
		for (final MarkupElement element : markup) {
			if (element instanceof PerspectiveMarkupElement) {
				startRecording = true;
			}
			if (startRecording) {
				result.add(element);
			}
		}
		return result;
	}

	/**
	 * @return always >= 0
	 */
	private int onCount() {
		return facet.getOnCount();
	}

	private int totalCount() {
		return facet.getTotalCount();
	}

	private @NonNull StringBuffer percentOn(final @Nullable StringBuffer buf) {
		return UtilString.formatPercent(onCount(), totalCount(), buf, totalCount() == 0);
	}

	private int siblingOnCount() {
		final int siblingOnCount = facet.parentOnCount() - onCount();
		assert siblingOnCount >= 0;
		// // recompute parentOnCount and facetOnCount
		// query().queueRedraw(this);
		return siblingOnCount;
	}

	private int siblingTotalCount() {
		return facet.parentTotalCount() - totalCount();
	}

	private @NonNull StringBuffer siblingPercentOn(final @Nullable StringBuffer buf) {
		return UtilString.formatPercent(siblingOnCount(), siblingTotalCount(), buf, true);
	}

	/**
	 * Only called by totalDescActions
	 *
	 * @return E.g. " in collection \n" <significanceColor> " median: "
	 *         <perspective> ["p<0.01"]
	 */
	private @NonNull Markup totalDescTotalCountDescription() {
		final StringBuilder buf = new StringBuilder();
		if (query().isRestrictedData()) {
			buf.append(" in restricted set");
		} else {
			buf.append(" in collection");
		}
		// if (isHelping()) {
		// buf.append(", as shown by the bar's width");
		// }
		final Markup description = addMedianContent(Util.nonNull(buf.toString()), false);
		return description;
	}

	/**
	 * @return if (unconditionalMedian == null): emptyMarkup
	 *
	 *         else "<it>\n median: " <perspective> ["(p=0.01)"]</it>
	 */
	private @NonNull Markup addMedianContent(final @NonNull String msg, final boolean isConditional) {
		final Markup content = DefaultMarkup.newMarkup(MarkupStringElement.getElement(msg));
		final Perspective medianPerspective = isConditional ? conditionalMedian : unconditionalMedian;
		if (isShowMedian() && medianPerspective != null) {
			content.add(DefaultMarkup.NEWLINE_TAG);
			content.add(DefaultMarkup.ITALIC_STRING_TAG);
			content.add(DefaultMarkup.FILTER_CONSTANT_MEDIAN);
			content.add(medianPerspective);
			if (isConditional) {
				content.addAll(medianPvalueDesc());
			}
			content.add(DefaultMarkup.DEFAULT_STYLE_TAG);
		}
		return content;
	}

	// only called by medianContent
	/**
	 * @return [<medianTestSignificanceColor>, "(p=0.5)", DEFAULT_COLOR_TAG]
	 */
	private @NonNull Markup medianPvalueDesc() {
		final Markup result = DefaultMarkup.emptyMarkup();
		// result.add(new MarkupPaintElement(art.facetColor(facet,
		// facet.medianTestSignificanceSign(this))));

		final StringBuffer buf = new StringBuffer();
		art.appendMedianPvalue(Util.nonNull(facet), buf);
		result.add(Util.nonNull(buf.toString()));

		// result.add(DefaultMarkup.DEFAULT_COLOR_TAG);
		return result;
	}

	private @NonNull String parentDescString() {
		final Perspective parent = facet.getParent();
		if (facet != null && parent != null) {
			final Markup markup = parent.getMarkupElement(true, false).description();
			return query().markupToText(markup, this);
		} else {
			return query().getGenericObjectLabel(true);
		}
	}

	/**
	 * @return E.g. "53 / 83 = 60%"
	 *
	 *         or " median: " <Perspective> " → " <Perspective>
	 */
	private @NonNull Markup noFrameDesc() {
		final Markup result = DefaultMarkup.newMarkup(DefaultMarkup.FILTER_COLOR_WHITE);
		if (isShowMedian()) {
			result.add(DefaultMarkup.FILTER_CONSTANT_MEDIAN);
			result.add(medianName(false));
			if (unconditionalMedian != conditionalMedian) {
				result.add(DefaultMarkup.FILTER_CONSTANT_RIGHT_ARROW);
				result.add(medianName(true));
				// result.add(medianPvalueString());
			}
		} else {
			final StringBuffer buf = new StringBuffer();
			buf.append(onCountString());
			buf.append(" / ").append(totalCountString());
			buf.append(" = ");
			percentOn(buf);
			result.add(Util.nonNull(buf.toString()));
		}
		return result;
	}

	// only called by noFrameDesc
	private MarkupElement medianName(final boolean isConditional) {
		final Perspective median = isConditional ? conditionalMedian : unconditionalMedian;
		if (median != null) {
			return median.getMarkupElement();
		} else {
			// if no items are selected, conditional median will be null
			return DefaultMarkup.FILTER_CONSTANT_UNDEFINED;
		}
	}

	/**
	 * @return bgW - 2 * art.buttonMargin()
	 */
	private double textElementW() {
		return bgW - 2 * buttonMargin();
	}

	private double remainingTextElementW(final APText leftTextElement) {
		final double width = bgW - leftTextElement.getWidth() - buttonMargin();
		assert width > 0.0 : bgW + " " + leftTextElement.getWidth() + " " + buttonMargin();
		assert (int) width == width;
		return width;
	}

	// private boolean isHelping() {
	// return currentStep.compareTo(Step.NO_HELP) > 0;
	// }

	private boolean isAnchorable() {
		// If bars are redrawn, anchor might have been dropped
		// assert anchor == null || anchor.getRoot() != null : anchor;
		return anchor != null && anchor.getRoot() != null;
	}

	public boolean isQueryVersionCurrent() {
		return query().isQueryVersionCurrent(queryVersion);
	}

	/**
	 * Checks whether query changed, and hide()s if so.
	 *
	 * @return whether we're hiding
	 */
	private boolean maybeHide() {
		// This sometimes fails even if we descend from PRoot
		// assert getRoot() != null : Util.ancestorString(this);

		final boolean result = !isQueryVersionCurrent();
		if (result) {
			hide();
		}
		return result;
	}

	public void hide() {
		setVisible(false);
		if (currentStep != Step.NO_POPUP) {
			// final boolean isHelp = isHelping();
			setCurrentStep(Step.NO_POPUP);
			setAnimationSpeed(0L);
			alignment.terminateAnimationJobs();
			// stop(false);
			hideInfluenceDiagram();
			removeEulerDiagrams();
			// if (isHelp) {
			// tagWall().animateRankComponentHeights(0L);
			// totalCount.setScale(1.0);
			// onCount.setScale(1.0);
			// if (pv() != null) {
			// Util.nonNull(pv()).updateHotZoneForMouseEventOrHide(0.5, false);
			// }
			// siblingPercent.clearMostAttributes();
			// }
			anchor = null;
			facet = null;
			conditionalMedian = null;
			unconditionalMedian = null;
			// explanations.clear();
			tagWall().moveToBack();
		}
		setTransform(null);
	}

	public boolean isHidden() {
		return !getVisible();
	}

	/**
	 * Scales the explanation speed; the larger it is, the slower the
	 * explanation animation; that is, the steps from START_ANIMATION to
	 * END_ANIMATION
	 */
	private long getAnimationSpeed() {
		return animationSpeed;
	}

	private void setAnimationSpeed(final long _animationSpeed) {
		animationSpeed = _animationSpeed;
	}

	private long fadeDuration() {
		return getAnimationSpeed() * 1000L;
	}

	@NonNull
	Query query() {
		return art.getQuery();
	}

	// private @Nullable PerspectiveViz pv() {
	// PerspectiveViz result = null;
	// if (anchor != null) {
	// result = (PerspectiveViz) anchor.getParent().getParent();
	// }
	// return result;
	// }

	private @NonNull TagWall tagWall() {
		return art.getTagWall();
	}

	@Override
	public String toString() {
		return UtilString.toString(this, facet == null ? "no facet" : facet.toString());
	}

	public void setFeatures() {
		// if (!art.getIsGraph()) {
		// stop(false);
		// }

		final Font font = art.getCurrentFont();
		for (final APText node : textNodes) {
			node.setFont(font);
		}
	}

	public void stop() {
		if (influenceDiagramCreator != null) {
			influenceDiagramCreator.exit();
			influenceDiagramCreator = null;
		}
		// hide();
	}

	public void setExitOnError(final boolean isExit) {
		if (influenceDiagramCreator != null) {
			influenceDiagramCreator.setExitOnError(isExit);
		}
	}

	/**
	 * One of the global actions. Start influenceDiagramCreator generating an
	 * explanation in the background.
	 */
	private void computeAndShowInfluenceDiagram() {
		if (facet != null && art.getIsGraph()
		// Database can't handle topCandidates for isRestrictedData
				&& !query().isRestrictedData()) {
			final SortedSet<Perspective> primaryFacets = computePrimaryFacets();
			final Explanation explanation = lookupExplanation(primaryFacets);
			if (explanation != null) {
				showInfluenceDiagram(explanation);
			} else {
				assert facet != null;
				art.printUserAction(ReplayLocation.INFLUENCE_DIAGRAM, facet, 0);
				ensureInfluenceDiagramCreator();
				assert influenceDiagramCreator != null;
				assert facet != null;
				final ExplanationTask explanationTask = new PopupExplanationTask(this, influenceDiagramCreator, facet,
						primaryFacets);
				influenceDiagramCreator.add(explanationTask);
			}
		}
	}

	private void ensureInfluenceDiagramCreator() {
		assert art.getIsGraph();
		if (influenceDiagramCreator == null || influenceDiagramCreator.getExited()) {
			influenceDiagramCreator = new InfluenceDiagramCreator(this);
			new Thread(influenceDiagramCreator).start();
		}
	}

	/**
	 * @return leaf facets of query().allRestrictions(), plus popupFacet. (or a
	 *         different algorithm if > MAX_FACETS)
	 */
	public @Immutable @NonNull SortedSet<Perspective> computePrimaryFacets() {
		assert facet != null;
		SortedSet<Perspective> primary = computePrimaryFacetsInternal(facet);
		if (primary.size() > FacetSelection.MAX_FACET_SELECTION_FACETS) {
			primary = computePrimaryFacetsAlternateAlgorithm(primary);
		}
		assert UtilMath.assertInRange(primary.size(), 1, FacetSelection.MAX_FACET_SELECTION_FACETS) : " popupFacet="
				+ facet + " " + primary;
		final SortedSet<Perspective> result = Collections.unmodifiableSortedSet(primary);
		assert result != null;
		return result;
	}

	private @NonNull SortedSet<Perspective> computePrimaryFacetsAlternateAlgorithm(
			final @NonNull SortedSet<Perspective> primary) {
		final TopTags topTags = new TopTags(FacetSelection.MAX_FACET_SELECTION_FACETS);
		final Query query = query();
		if (query.isExtensionallyRestricted()) {
			for (final Perspective p : primary) {
				final boolean areCountsCached = p.updateTopTags(topTags, query.version(), false, null);
				assert areCountsCached;
			}
		}
		if (topTags.top.size() > 0) {
			primary.clear();
			for (final TagRelevance tagRelevance : topTags.top) {
				primary.add(tagRelevance.getFacet());
				if (primary.size() >= FacetSelection.MAX_FACET_SELECTION_FACETS) {
					break;
				}
			}
		} else {
			UtilArray.retainSubset(primary, 0, FacetSelection.MAX_FACET_SELECTION_FACETS);
		}
		return primary;
	}

	/**
	 * Called only by computePrimaryFacets
	 *
	 * @return a new set with leaf facets of query().allRestrictions(), plus
	 *         popupFacet.
	 */
	private static @NonNull SortedSet<Perspective> computePrimaryFacetsInternal(final @NonNull Perspective popupFacet) {
		final SortedSet<Perspective> primaryFacets = new TreeSet<>(popupFacet.query().allNonImpliedRestrictions());
		primaryFacets.add(popupFacet);
		final Collection<Perspective> ancestors = new HashSet<>();
		for (final Perspective facet : primaryFacets) {
			ancestors.addAll(facet.ancestors());
		}
		ancestors.remove(popupFacet);
		primaryFacets.removeAll(ancestors);
		assert primaryFacets.size() > 0;
		return primaryFacets;
	}

	@Nullable
	Explanation lookupExplanation(final @NonNull SortedSet<Perspective> primaryFacets) {
		Explanation result = null;
		if (!maybeHide()) {
			assert facet != null;
			result = explanations.get(primaryFacets);
		}
		assert result == null || primaryFacets.equals(result.primaryFacets());
		// System.out.println("PopupSummary.lookupExplanation " + primaryFacets
		// + " => " + result);
		return result;
	}

	// Can be called in process influenceDiagramCreator.
	void showInfluenceDiagram(final @NonNull Explanation explanation) {
		hideInfluenceDiagram();
		if (facet != null && explanation.nEdges() >= BungeeConstants.MIN_INFLUENCE_DIAGRAM_EDGES) {
			influenceDiagram = showInfluenceDiagramInternal(explanation);
			addChild(influenceDiagram);
			final double buttonMargin = buttonMargin();
			double influenceDiagramW = influenceDiagram.getWidth();
			final double totalBGwidth = totalBG.getWidth();
			if (influenceDiagramW < totalBGwidth) {
				influenceDiagram.setMinWidth(totalBGwidth);
				influenceDiagramW = influenceDiagram.getWidth();
			}
			// Have to align influenceDiagram before setBGw so fullBounds is
			// right
			Alignment.align(Util.nonNull(influenceDiagram), Alignment.LEFT, name, Alignment.LEFT, -buttonMargin, 0.0);
			Alignment.align(Util.nonNull(influenceDiagram), Alignment.TOP, siblingDesc, Alignment.BOTTOM, 0.0,
					2.0 * buttonMargin);
			if (totalBGwidth < influenceDiagramW) {
				// setBGw will align with top-right corner, if appropriate
				setBGw(influenceDiagramW);
			}
			Alignment.setBoundsFromNodes(goldBorder, nodes(totalBG, influenceDiagram), buttonMargin, buttonMargin);

			final float totalDescTransparency = totalDesc.getTransparency();
			if (totalDescTransparency < 1.0f) {
				fadeNoDelay(nodes(influenceDiagram), totalDescTransparency);
				final long duration = alignment.maxFinishTime() - System.currentTimeMillis();
				fade(nodes(influenceDiagram), 0L, duration, OPAQUE);
			}
			showEuler(explanation);
		}
	}

	private void hideInfluenceDiagram() {
		if (influenceDiagram != null) {
			// If we generate a new InfluenceDiagram, the facetsOfInterest
			// might be different, and we don't want the influenceDiagram to
			// change on redraws.
			removeChild(influenceDiagram);
			influenceDiagram = null;
		}
	}

	/**
	 * Only called by showInfluenceDiagram
	 */
	private @NonNull LazyGraph<Perspective> showInfluenceDiagramInternal(final @NonNull Explanation explanation) {
		final Graph<Perspective> graph = explanation.buildGraph(art.getIsDebugGraph());
		graph.removeZeroWeightEdges();
		graph.removeUnconnectedNodes();

		final SortedSet<Perspective> primaryFacets = explanation.primaryFacets();
		final MarkupViz title = computeInfluenceDiagramTitle(explanation, primaryFacets);

		final LazyGraph<Perspective> lazyGraph = new LazyGraph<Perspective>(graph, title) {

			@Override
			protected Paint getLabelColor(final @NonNull Node<Perspective> node) {
				return art.facetColor(node.object);
			}

			@Override
			protected Font getLabelFont(final Node<Perspective> node) {
				return getFont(node);
			}

			@Override
			protected Font getLabelFont(final Edge<Perspective> edge) {
				return getFont(edge.getLeftNode(), edge.getRightNode());
			}

			@SafeVarargs
			private final @NonNull Font getFont(final Node<Perspective>... nodes) {
				return allInPrimary(nodes) ? art.getCurrentFont() : art.getFont(Font.ITALIC);
			}

			@Override
			protected Stroke getStroke(final Edge<Perspective> edge) {
				return allInPrimary(edge.getLeftNode(), edge.getRightNode()) ? super.getStroke(edge) : DASH_STROKE;
			}

			@SafeVarargs
			private final boolean allInPrimary(final Node<Perspective>... nodes) {
				boolean isContained = true;
				for (final Node<Perspective> node : nodes) {
					if (!primaryFacets.contains(node.object)) {
						isContained = false;
						break;
					}
				}
				return isContained;
			}

		};
		return lazyGraph;
	}

	private static final @NonNull float[] DASH_PATTERN = { 10f, 10f };
	static final @NonNull BasicStroke DASH_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
			10f, DASH_PATTERN, 0f);

	private @NonNull MarkupViz computeInfluenceDiagramTitle(final @NonNull Explanation explanation,
			final @NonNull SortedSet<Perspective> primaryFacets) {
		final int nPrimaryFacets = primaryFacets.size();
		assert nPrimaryFacets > 1 : primaryFacets;
		final Markup titleContent = DefaultMarkup.emptyMarkup();
		titleContent.add("Influences [-100 to +100] " + (nPrimaryFacets == 2 ? "between" : "among") + " tags ");

		titleContent.addAll(DefaultMarkup.newMarkup(primaryFacets).addConnectors(DefaultMarkup.CONNECTOR_AND));
		final int nAddedFacets = explanation.facets().size() - nPrimaryFacets;
		if (nAddedFacets > 0) {
			titleContent
					.add(". Adding " + nAddedFacets + " more explanatory tag" + (nAddedFacets > 1 ? "s" : "") + " (");
			titleContent.add(DefaultMarkup.ITALIC_STRING_TAG);
			final SortedSet<Perspective> nonprimaryFacets = new TreeSet<>(explanation.facets());
			nonprimaryFacets.removeAll(primaryFacets);
			titleContent.addAll(DefaultMarkup.newMarkup(nonprimaryFacets).addConnectors(DefaultMarkup.CONNECTOR_AND));
			titleContent.add(DefaultMarkup.DEFAULT_STYLE_TAG);
			final int nPrimaryEdges = explanation.getPredictedDistribution().getEdgesAmong(primaryFacets, false).size();
			final int nPossiblePrimaryEdgess = (nPrimaryFacets * (nPrimaryFacets - 1)) / 2;
			if (nPrimaryEdges < nPossiblePrimaryEdgess) {
				if (nPrimaryEdges == 0) {
					titleContent.add(") eliminates the direct influence" + (nPossiblePrimaryEdgess > 1 ? "s." : "."));
				} else {
					titleContent.add(") eliminates " + (nPossiblePrimaryEdgess - nPrimaryEdges) + " of the "
							+ nPossiblePrimaryEdgess + " direct influences.");
					titleContent.add(" The remaining direct influences change as shown following the "
							+ (nPrimaryEdges > 1 ? nPrimaryEdges + " '>'s." : "'>'."));
				}
			} else {
				titleContent.add(") changes the direct influence" + (nPrimaryEdges > 1 ? "s" : "")
						+ " as shown following the " + (nPrimaryEdges > 1 ? nPrimaryEdges + " '>'s." : "'>'."));
			}
		}

		final MarkupViz title = new MarkupViz(art, UtilColor.WHITE);
		title.setIsWrapText(true);
		title.constrainHeightToContent = true;
		title.setContent(titleContent);
		return title;
	}

	/**
	 * NO-OP unless art.getIsDebugGraph()
	 */
	private void showEuler(final @NonNull Explanation explanation) {
		removeEulerDiagrams();
		final @NonNull SortedSet<Perspective> primaryFacets = explanation.primaryFacets();
		if (art.getIsDebugGraph() && EulerDiagram.checkEulerFacets(primaryFacets)) {
			final Distribution observedDestribution = explanation.getObservedDistribution();
			final SortedSet<Perspective> nonPrimaryFacets = explanation.nonPrimaryFacets();
			try {
				final Distribution predictedDestribution = explanation.getPredictedDistribution();
				final boolean predictPerfect = Arrays.equals(observedDestribution.getCounts(),
						predictedDestribution.getMarginalCounts(primaryFacets));
				final String label = predictPerfect ? "Predicted (= Observed)" : "Observed";
				observedEulerDiagram = new EulerDiagram(observedDestribution, primaryFacets, nonPrimaryFacets, art,
						label);
				observedEulerDiagram.setStrokePaint(GOLD_BORDER_COLOR);
				addChild(observedEulerDiagram);
				assert observedEulerDiagram != null;
				Alignment.align(observedEulerDiagram, Alignment.TOP_RIGHT, goldBorder, Alignment.TOP_LEFT);
				if (!predictPerfect) {
					predictedEulerDiagram = new EulerDiagram(predictedDestribution, primaryFacets, nonPrimaryFacets,
							art, "Predicted");
					predictedEulerDiagram.setStrokePaint(GOLD_BORDER_COLOR);
					addChild(predictedEulerDiagram);
					assert observedEulerDiagram != null && predictedEulerDiagram != null;
					Alignment.align(predictedEulerDiagram, Alignment.TOP_RIGHT, observedEulerDiagram,
							Alignment.TOP_LEFT);
				}
				final double eulerWidth = observedEulerDiagram.getWidth()
						+ (predictPerfect ? 0.0 : predictedEulerDiagram.getWidth());
				final double popupLeft = art.getWidth() - goldBorder.getGlobalBounds().getWidth();
				final double xScale = popupLeft / eulerWidth;
				final double yScale = art.getHeight() / observedEulerDiagram.getHeight();
				observedEulerDiagram.setScale(xScale, yScale);
				assert observedEulerDiagram != null;
				Alignment.align(observedEulerDiagram, Alignment.TOP_RIGHT, goldBorder, Alignment.TOP_LEFT);
				if (!predictPerfect) {
					predictedEulerDiagram.setScale(xScale, yScale);
					assert observedEulerDiagram != null && predictedEulerDiagram != null;
					Alignment.align(predictedEulerDiagram, Alignment.TOP_RIGHT, observedEulerDiagram,
							Alignment.TOP_LEFT);
				}
			} catch (final AssertionError e) {
				e.printStackTrace();
			}
		}
	}

	private void removeEulerDiagrams() {
		if (observedEulerDiagram != null) {
			observedEulerDiagram.removeFromParent();
			observedEulerDiagram = null;
		}
		if (predictedEulerDiagram != null) {
			predictedEulerDiagram.removeFromParent();
			predictedEulerDiagram = null;
		}
	}

	/**
	 * need explanationTask.primaryFacets() explicitly, because
	 * explanation.facets() might have more facets than were asked for.
	 *
	 * Caching is more effective if relevantFacets are the same, no matter which
	 * of them is used to calculate the others. Not sure if this is true.
	 *
	 * @param explanation
	 */
	void addExplanation(final @NonNull ExplanationTask explanationTask, final @NonNull Explanation explanation) {
		assert explanationTask.primaryFacets().equals(explanation.primaryFacets());
		// if true, drop explanationTask arg

		UtilArray.putNew(explanations, explanationTask.primaryFacets(), explanation, this);
	}

}
