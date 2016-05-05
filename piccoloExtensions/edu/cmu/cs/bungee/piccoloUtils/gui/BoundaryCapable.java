package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.piccoloUtils.gui.BoundaryWithLabels.LabelNoffset;
import edu.umd.cs.piccolo.util.PBounds;

public interface BoundaryCapable {

	/**
	 * Called only by Boundary.drag().
	 *
	 * To move boundary with mouse, call:
	 *
	 * boundary.setDragBaseOffset(boundary.dragW())
	 */
	public void boundaryDragged(final @NonNull Boundary boundary);

	/**
	 * Only called by Boundary.dragMinMaxX().
	 *
	 * getXOffset() - effectiveMargin()
	 */
	public double minDragLimit(final @NonNull Boundary boundary);

	/**
	 * Only called by Boundary.dragMinMaxX().
	 */
	public double maxDragLimit(final @NonNull Boundary boundary);

	/**
	 * Ignorable
	 */
	public void enterBoundary(final @NonNull Boundary boundary);

	/**
	 * Ignorable
	 */
	public void exitBoundary(final @NonNull Boundary boundary);

	/**
	 * Only called by BoundaryWithScale
	 */
	public @Nullable List<LabelNoffset> getLabels();

	public double getHeight();

	public double getWidth();

	public PBounds getGlobalBounds();

}
