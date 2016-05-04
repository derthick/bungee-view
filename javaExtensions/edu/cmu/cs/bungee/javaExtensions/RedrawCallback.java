package edu.cmu.cs.bungee.javaExtensions;

/**
 * An object that needs a callback, for instance when the name of a Perspective
 * is available. Currently used only by
 * edu.cmu.cs.bungee.client.query.query.Redraw
 */
public interface RedrawCallback {

	/**
	 * Never call this directly -- always use Query.queueOrRedraw(), which
	 * checks for multiply-queued callbacks.
	 */
	public void redrawCallback();

}