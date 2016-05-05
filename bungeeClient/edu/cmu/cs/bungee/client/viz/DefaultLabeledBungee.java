package edu.cmu.cs.bungee.client.viz;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.piccoloUtils.gui.DefaultLabeledPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.umd.cs.piccolo.PNode;

public abstract class DefaultLabeledBungee<V, Z extends PNode> extends DefaultLabeledPNode<V, Z> {

	public DefaultLabeledBungee(final int width, final int labelW, final @NonNull V _parentV,
			final @NonNull LazyPNode _parentPNode) {
		super(width, labelW, _parentV, _parentPNode, true, false);
	}

	protected abstract @NonNull Bungee art();

	protected abstract @NonNull Perspective perspective();

	protected @NonNull Query query() {
		return perspective().query();
	}

}
