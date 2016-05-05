package edu.cmu.cs.bungee.client.query.markup;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;

/**
 * A PerspectiveMarkupElement that displays a score instead of onCount.
 */
public class TopTagsPerspectiveMarkupElement extends PerspectiveMarkupElement {

	public int score;

	public TopTagsPerspectiveMarkupElement(final @NonNull Perspective _perspective, final int _score) {
		super(_perspective, false, false);
		score = _score;
	}

	@Override
	public int getOnCount() {
		return score;
	}

}
