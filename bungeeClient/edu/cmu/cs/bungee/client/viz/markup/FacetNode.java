/*

Created on Mar 6, 2005

Bungee View lets you search, browse, and data-mine an image collection.
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

package edu.cmu.cs.bungee.client.viz.markup;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.YesNoMaybe;

/**
 * PNode representng a Perspective. The purpose is to have an InputHandler
 * appropriate for Perspectives. Constructors should
 * addInputEventListener(Art.facetClickHandler);
 */
public interface FacetNode extends KnowsBungee {

	/**
	 * @return the facet associated with this FacetNode
	 */
	@NonNull
	Perspective getFacet();

	/**
	 * @return getPaint() != art().facetColor(getFacet(), queryVersion)
	 */
	boolean updateHighlighting(int queryVersion, final @NonNull YesNoMaybe isRerender);
}
