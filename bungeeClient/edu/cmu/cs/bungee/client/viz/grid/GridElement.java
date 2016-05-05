package edu.cmu.cs.bungee.client.viz.grid;

import java.io.Serializable;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.viz.markup.KnowsBungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyNode;

/**
 * Known implementations: GridImage, GridText. Each has a GridElementWrapper,
 * which is a substitute for a common superclass, which PImage and APText can't
 * have.
 */
public interface GridElement extends Serializable, KnowsBungee, LazyNode {

	static final long serialVersionUID = 1739584606946774381L;

	/**
	 * Set w and h as large as possible while maintaining aspect ratio and
	 * without exceeding maxImageW/maxImageH or cached image w/h.
	 */
	void setDisplaySize(int maxImageW, int maxImageH);

	/**
	 * @return w/h of cached image
	 */
	double getImageWidth();

	double getImageHeight();

	@NonNull
	GridElementWrapper getWrapper();

	public @NonNull Item getItem();

	boolean isBigEnough(int edgeW, int edgeH, int quality);

}
