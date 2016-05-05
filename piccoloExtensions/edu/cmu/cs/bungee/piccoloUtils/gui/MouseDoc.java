/*

Created on Dec 20, 2005

The Bungee View applet lets you search, browse, and data-mine an image collection.
Copyright (C) 2006  Mark Derthick

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.  See gpl.html.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

You may also contact the author at
mad@cs.cmu.edu,
or at
Mark Derthick
Carnegie-Mellon University
Human-Computer Interaction Institute
Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.piccoloUtils.gui;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Support Genera-like Mouse Documentation Line at the bottom of the window. The
 * application sends these messages to its Mouse Documentation Line, and
 * descendents pass messages up. The messages can be either something that
 * implements MouseDoc and can describe themselves, or a String to display.
 */
public interface MouseDoc {

	/**
	 * @param doc
	 *            the String to display, or null to hide display.
	 */
	public void setMouseDoc(@Nullable String doc);

	// public @Nullable String getMouseDoc();

}
