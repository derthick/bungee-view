package edu.cmu.cs.bungee.piccoloUtils.gui;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.isInRange;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public class BoundaryWithLabels extends Boundary {
	private final @NonNull Font font;

	public BoundaryWithLabels(final @NonNull BoundaryCapable _parent, final boolean _isHorizontal,
			final @NonNull Font _font) {
		super(_parent, _isHorizontal);
		font = _font;
	}

	@Override
	boolean maybeSetState(final boolean state) {
		final boolean result = super.maybeSetState(state);
		if (result && state) {
			final List<LabelNoffset> labels = parent.getLabels();
			assert labels != null;
			final int nLabels = labels.size();
			final List<String> names = new ArrayList<>(nLabels);
			final List<Double> offsetPercents = new ArrayList<>(nLabels);
			for (int i = 0; i < nLabels; i++) {
				final LabelNoffset labelNoffset = labels.get(i);
				names.add(labelNoffset.name);
				offsetPercents.add(labelNoffset.offsetPercent);
			}
			arrow.addLabels(names, offsetPercents, font);
		}
		return result;
	}

	public @Nullable APText getLabel(final String s) {
		return arrow.getLabel(s);
	}

	public static class LabelNoffset {
		final String name;
		final double offsetPercent;

		public LabelNoffset(final String _name, final double _offsetPercent) {
			assert isInRange(_offsetPercent, 0.0, 1.0) : _name + " " + _offsetPercent;
			name = _name;
			offsetPercent = _offsetPercent;
		}
	}

}
