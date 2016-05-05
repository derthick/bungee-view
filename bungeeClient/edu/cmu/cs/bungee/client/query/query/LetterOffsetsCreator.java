package edu.cmu.cs.bungee.client.query.query;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.RedrawCallback;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class LetterOffsetsCreator extends CallbackQueueThread<PrefixSpec> { // NO_UCD
	// (use
	// default)

	LetterOffsetsCreator(final @NonNull Query _query) {
		super("LetterOffsetsCreator", -2, _query, true);
	}

	public synchronized boolean add(final @NonNull Perspective facet, final @NonNull String prefix,
			final RedrawCallback callback) {
		final CallbackSpec<PrefixSpec> spec = new CallbackSpec<>(new PrefixSpec(facet, prefix), callback);
		final boolean result = super.add(spec);
		return result;
	}

	@Override
	protected void processClassSpecificInfos(final Collection<PrefixSpec> classSpecificInfos) {
		for (final PrefixSpec info : classSpecificInfos) {
			final Perspective facet = info.facet;
			final String prefix = info.prefix;
			facet.getLetterOffsets(prefix, null);
		}
	}
}

class PrefixSpec implements Comparable<PrefixSpec> {

	final @NonNull Perspective facet;
	final @NonNull String prefix;

	PrefixSpec(final @NonNull Perspective _facet, final @NonNull String _prefix) {
		facet = _facet;
		prefix = _prefix;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "[facet=" + facet + ", prefix=" + prefix + "]");
	}

	@Override
	public int compareTo(final PrefixSpec o) {
		int result = facet.compareTo(o.facet);
		if (result == 0) {
			result = prefix.compareTo(o.prefix);
		}
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + facet.hashCode();
		result = prime * result + prefix.hashCode();
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final PrefixSpec other = (PrefixSpec) obj;
		// if (facet == null) {
		// if (other.facet != null) {
		// return false;
		// }
		// } else
		if (!facet.equals(other.facet)) {
			return false;
		}
		// if (prefix == null) {
		// if (other.prefix != null) {
		// return false;
		// }
		// } else
		if (!prefix.equals(other.prefix)) {
			return false;
		}
		return true;
	}

}
