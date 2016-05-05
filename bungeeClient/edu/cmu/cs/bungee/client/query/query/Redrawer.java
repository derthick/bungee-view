package edu.cmu.cs.bungee.client.query.query;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;

class Redrawer extends CallbackQueueThread<Comparable<?>> {

	Redrawer(final @NonNull Query _query) {
		super("Redrawer", -1, _query, /* waitForValidQuery */true);
	}

	/**
	 * Called only by Query.queueRedraw()
	 */
	synchronized boolean add(final @Nullable RedrawCallback callback) {
		assert Thread.currentThread() != myThread : myThread;
		return add(new CallbackSpec<>(null, callback));
	}

	@Override
	protected synchronized void processClassSpecificInfos(
			@SuppressWarnings("unused") final Collection<Comparable<?>> ignore) {
		assert false : "Redrawer is only used for Query.queueRedraw().  ClassSpecificInfos should always be empty.";
	}

}