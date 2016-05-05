package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Paint;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;

public interface ExpandableText extends LazyNode {

	void setPaint(Paint aPaint);

	void setUnderline(final boolean isUnderline, final @NonNull YesNoMaybe isRerender);

	void setTextPaint(final @Nullable Paint aPaint);

	void setPickable(boolean isPickable);

	void setConstrainWidthToTextWidth(final boolean _constrainWidthToTextWidth);

	void setConstrainHeightToTextHeight(final boolean _constrainHeightToTextHeight);

	void setJustification(float just);

	String getText();

	/**
	 * @return whether text changed
	 */
	boolean maybeSetText(String text);

	boolean setWidth(double w);

	boolean setHeight(double height);

	boolean expand();

	boolean contract();

	void enableExpandableText(boolean b);

}
