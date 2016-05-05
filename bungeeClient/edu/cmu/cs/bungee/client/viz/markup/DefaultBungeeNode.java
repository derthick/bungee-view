package edu.cmu.cs.bungee.client.viz.markup;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.umd.cs.piccolo.event.PInputEvent;

abstract class DefaultBungeeNode extends LazyPNode implements KnowsBungee {

	private final @NonNull Bungee art;

	DefaultBungeeNode(final @NonNull Bungee _art) {
		art = _art;
	}

	@Override
	public void setMouseDoc(final @Nullable String doc) {
		art().setClickDesc(doc);
	}

	@Override
	public @NonNull Bungee art() {
		return art;
	}

	protected Query query() {
		return art.getQuery();
	}

	@Override
	public int getModifiersEx(final @NonNull PInputEvent e) {
		return art.getModifiersEx(e);
	}

}
