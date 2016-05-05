package edu.cmu.cs.bungee.client.viz.markup;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;
import edu.umd.cs.piccolo.event.PInputEvent;

/**
 * Bar is the only subclass
 */
public abstract class DefaultFacetNode extends DefaultBungeeNode implements FacetNode, RedrawCallback {

	protected final @NonNull Perspective facet;

	public DefaultFacetNode(final @NonNull Bungee _art, final @NonNull Perspective _facet) {
		super(_art);
		facet = _facet;
		addInputEventListener(BungeeClickHandler.getBungeeClickHandler());
	}

	@SuppressWarnings("unused")
	@Override
	public boolean isUnderMouse(final boolean state, final PInputEvent e) {
		return true;
	}

	@Override
	public boolean brush(final boolean state, @SuppressWarnings("unused") final PInputEvent e) {
		art().brushFacet(state ? getFacet() : null);
		return false;
	}

	@Override
	public boolean updateHighlighting(final int queryVersion, @SuppressWarnings("unused") final YesNoMaybe isRerender) {
		return setMyPaint(art().facetColor(facet, queryVersion));
	}

	@Override
	public Perspective getFacet() {
		return facet;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, facet);
	}

}
