package edu.cmu.cs.bungee.client.query.query;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.servlet.FetchType;

public class Prefetcher extends CallbackQueueThread<PrefetchSpec> { // NO_UCD
	// (use
	// default)

	Prefetcher(final @NonNull Query _query) {
		super("Prefetcher", -1, _query, false);
	}

	/**
	 * @return whether PrefetchSpec was newly added.
	 */
	public synchronized boolean add(final Perspective p, final @NonNull FetchType fetchType,
			final RedrawCallback callback) {
		// System.out.println("Prefetcher.add " + p + " " + fetchType + " " +
		// queue);
		return super.add(new PrefetchSpec(p, fetchType), callback);
	}

	@Override
	protected void processClassSpecificInfos(final Collection<PrefetchSpec> prefetchSpecs) {
		query.prefetchFacetsFromPrefetchSpecs(prefetchSpecs);
	}

}