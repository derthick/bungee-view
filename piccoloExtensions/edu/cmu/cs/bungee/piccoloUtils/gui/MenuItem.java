package edu.cmu.cs.bungee.piccoloUtils.gui;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public interface MenuItem {

	/**
	 * @return the label that appears on the menu.
	 */
	@NonNull
	String getLabel();

	@NonNull
	String getMouseDoc();

	boolean isEnabled();

	/**
	 * @return the desired Menu.value
	 */
	@Nullable
	String doCommand();

}
