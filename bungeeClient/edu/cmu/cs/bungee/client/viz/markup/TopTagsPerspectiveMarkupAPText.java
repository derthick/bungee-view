package edu.cmu.cs.bungee.client.viz.markup;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.markup.MarkupElement;
import edu.cmu.cs.bungee.client.query.markup.TopTagsPerspectiveMarkupElement;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Replayer.ReplayLocation;
import edu.cmu.cs.bungee.client.viz.bungeeCore.UserAction;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;

/**
 * A PerspectiveMarkupAPText that displays a score instead of onCount.
 */
public class TopTagsPerspectiveMarkupAPText extends PerspectiveMarkupAPText {

	private final @NonNull TopTagsPerspectiveMarkupElement treeObject;

	/**
	 * constrainWidth, constrainHeight, and isWrap all default to false.
	 * justification defaults to LEFT_ALIGNMENT.
	 */
	static @NonNull TopTagsPerspectiveMarkupAPText getInstance(final @NonNull Perspective perspective,
			@Nullable String s, final @NonNull Bungee art, final double _numW, final boolean _numFirst,
			final boolean _showChildIndicator, final boolean _showCheckBox, final boolean _underline, final int _score,
			final boolean padToNameW, final @Nullable RedrawCallback _redraw,
			final @NonNull ReplayLocation replayLocation) {
		TopTagsPerspectiveMarkupAPText result = art.getTopTagsViz().getFromZPool(perspective, _numW);
		if (result == null) {
			assert Util.assertMouseProcess();
//			final TopTagsPerspectiveMarkupElement _treeObject = new TopTagsPerspectiveMarkupElement(perspective,
//					_score);
//			if (s == null) {
//				// Compute s here, because Constructor will render _score==-1 as "?"
//				s = art.computeText(_treeObject, _numW, Double.POSITIVE_INFINITY, _numFirst, _showChildIndicator,
//						_showCheckBox, padToNameW, _redraw, _score);
//			}
			result = new TopTagsPerspectiveMarkupAPText(perspective, s, art, _numW, Double.POSITIVE_INFINITY, _numFirst,
					_showChildIndicator, _showCheckBox, _underline, _score, padToNameW, replayLocation);
			// incfGGG();
		} else {
			if (result.setScore(_score)) {
				result.maybeSetText(art.computeText(result.treeObject, // perspective.getMarkupElement(),
						_numW, Double.POSITIVE_INFINITY, _numFirst, _showChildIndicator, _showCheckBox, padToNameW,
						_redraw, _score));
				// incfGGG();
			}
			result.updateHighlighting();
		}
		assert !result.getText().contains("?") : result + " " + _score;
		result.setConstrainWidthToTextWidth(false);
		// result.setWidth(_numW + _nameW);
		return result;
	}

	/**
	 * constrainWidth defaults to true; constrainHeight and isWrap default to
	 * false. justification defaults to LEFT_ALIGNMENT.
	 */
	private TopTagsPerspectiveMarkupAPText(final @NonNull Perspective perspective, final @Nullable String s,
			final @NonNull Bungee _art, final double _numW, final double _nameW, final boolean _numFirst,
			final boolean _showChildIndicator, final boolean _showCheckBox, final boolean _underline, final int _score,
			final boolean padToNameW, final @NonNull ReplayLocation replayLocation) {
		super(perspective, s, _art, _numW, _nameW, _numFirst, _showChildIndicator, _showCheckBox, _underline, _score,
				padToNameW, replayLocation);
		treeObject = new TopTagsPerspectiveMarkupElement(perspective,
				_score);
	}

	/**
	 * @return whether score changed
	 */
	private boolean setScore(final int score) {
		final boolean result = score != treeObject.score;
		treeObject.score = score;
		return result;
	}

	@Override
	@NonNull
	MarkupElement treeObject() {
		return treeObject;
	}

	@Override
	public boolean maybeSetText(@Nullable final String _text) {
		final boolean result = super.maybeSetText(_text);
		if (result) {
			setUnderline(UserAction.isDefaultLocationUnderline(art, facet, this), YesNoMaybe.NO);
		}
		return result;
	}

	@SuppressWarnings("null")
	private static final @NonNull Pattern UNDERLINE_PATTERN = Pattern.compile("[\\?\\d]\\s+");

	@Override
	public void setUnderline(final boolean isUnderline, final @NonNull YesNoMaybe isRerender) {
		final String text = getText();
		if (isUnderline) {
			if (UtilString.isNonEmptyString(text)) {
				final Matcher matcher = UNDERLINE_PATTERN.matcher(text);
				final boolean isMatch = matcher.find();
				assert isMatch : "'" + text + "'";
				final int beginIndex = matcher.end();
				// System.out.println("TopTagsPerspectiveMarkupAPText.setUnderline
				// '" + text + "' " + beginIndex
				// + " isRerender=" + isRerender);
				setMyAttribute(UNDERLINE, UNDERLINE_ON, beginIndex, -1, isRerender);
			}
		} else {
			super.setUnderline(isUnderline, isRerender);
		}
	}

	@Override
	public boolean updateHighlighting(final int queryVersion,
			@SuppressWarnings("unused") final @NonNull YesNoMaybe isRerender) {
		final Color facetColor = facetColor(queryVersion);
		final boolean result = facetColor != getTextPaint();
		if (result) {
			setTextPaint(facetColor);
			// maybeIncfGGG();
		}
		return result;
	}

}
