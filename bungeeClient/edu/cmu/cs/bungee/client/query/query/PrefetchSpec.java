package edu.cmu.cs.bungee.client.query.query;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.servlet.FetchType;

class PrefetchSpec implements Comparable<PrefetchSpec> {

	final @NonNull FetchType fetchType;
	final Perspective p;

	PrefetchSpec(final Perspective _p, final @NonNull FetchType _fetchType) {
		p = _p;
		fetchType = _fetchType;
	}

	@Override
	public int compareTo(final PrefetchSpec o) {
		int result = p.compareTo(o.p);
		if (result == 0) {
			result = fetchType.ordinal() - o.fetchType.ordinal();
		}
		return result;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, "p=" + p + "; fetchType=" + fetchType);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + fetchType.hashCode();
		result = prime * result + ((p == null) ? 0 : p.hashCode());
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
		final PrefetchSpec other = (PrefetchSpec) obj;
		if (fetchType != other.fetchType) {
			return false;
		}
		if (p == null) {
			if (other.p != null) {
				return false;
			}
		} else if (!p.equals(other.p)) {
			return false;
		}
		return true;
	}
}