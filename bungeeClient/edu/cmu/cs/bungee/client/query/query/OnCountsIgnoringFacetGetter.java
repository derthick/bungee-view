package edu.cmu.cs.bungee.client.query.query;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.viz.tagWall.PerspectiveList;
import edu.cmu.cs.bungee.javaExtensions.MyResultSet;

class OnCountsIgnoringFacetGetter extends CallbackQueueThread<Perspective> {

	private final @NonNull PerspectiveList perspectiveList;

	public OnCountsIgnoringFacetGetter(final @NonNull Query _query, final @NonNull PerspectiveList _perspectiveList) {
		super("OnCountsIgnoringFacetGetter", -2, _query, false);
		perspectiveList = _perspectiveList;
	}

	@Override
	protected void processClassSpecificInfos(final Collection<Perspective> facets) {
		for (final Perspective ignoreParent : facets) {
			if (ignoreParent != null) {
				try (final MyResultSet rs = query.getOnCountsIgnoringFacet(ignoreParent, perspectiveList, null);) {
					assert rs != null;
					perspectiveList.getSelectedOnCountsFromRS(null, rs, ignoreParent);
				}
			}
		}
	}

}
