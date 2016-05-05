package edu.cmu.cs.bungee.client.viz.bungeeCore;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.grid.ResultsGrid;
import edu.cmu.cs.bungee.client.viz.header.Header;
import edu.cmu.cs.bungee.client.viz.selectedItem.SelectedItemColumn;
import edu.cmu.cs.bungee.client.viz.tagWall.TagWall;

interface BungeeInterface {

	void waitForIdle();

	void mayHideTransients();

	void toggleFacet(Perspective facet, int modifiers);

	Query getQuery();

	TagWall getTagWall();

	ResultsGrid getGrid();

	SelectedItemColumn getSelectedItemColumn();

	Header getHeader();

	void stopReplayer();

	void resetForReplayer();

	MouseDocLine getMouseDoc();

}