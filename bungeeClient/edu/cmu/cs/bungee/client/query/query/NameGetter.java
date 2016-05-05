package edu.cmu.cs.bungee.client.query.query;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

class NameGetter extends CallbackQueueThread<Perspective> {

	NameGetter(final @NonNull Query _query) {
		super("NameGetter", -1, _query, false);
	}

	@Override
	protected void processClassSpecificInfos(final Collection<Perspective> unnamedFacets) {
		query.getNamesNow(unnamedFacets);
	}

	@Override
	public void reportError(final Throwable e, final CallbackSpec<Perspective> rc) {
		// queryInvokeLater so all printUserActions are serialized in the mouse
		// process
		query.queryInvokeLater(query.getDoPrintUserAction(Query.ERROR, UtilString.getStackTrace(e), 0));
		super.reportError(e, rc);
	}
}