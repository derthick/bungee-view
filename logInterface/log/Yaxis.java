package log;

import java.awt.Color;
import java.awt.Component;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;

class Yaxis extends LazyPNode {

	// private static final long serialVersionUID = -7702964186782371079L;

	private final SortedSet<String> values = new TreeSet<>();

	private final double tickLength = 6;

	Yaxis(final Set<String> _values) {
		values.addAll(_values);
	}

	void draw(final double width, final double length) {
		setBounds(0, 0, 1, length);
		int i = 0;
		final int nTicks = values.size();
		for (final String name : values) {
			final double y = length * (1 - i++ / (double) (nTicks - 1));

			final LazyPNode tick = new LazyPNode();
			tick.setBounds(-tickLength / 2, y, tickLength, 1);
			tick.setPaint(Color.black);
			addChild(tick);

			final APText label = getLabel(name, width - tickLength);
			label.setOffset(-tickLength, y);
			addChild(label);
		}
	}

	private static APText getLabel(final String s, final double w) {
		final APText label = new APText();
		label.setJustification(Component.RIGHT_ALIGNMENT);
		label.setConstrainWidthToTextWidth(false);
		label.setWidth(w);
		label.maybeSetText(s);
		label.setX(-w);
		label.setY(-label.getHeight() / 2);
		return label;
	}

	double encode(final String value) {
		final int index = values.headSet(value).size();
		final int nTicks = values.size();
		final double y = getHeight() * (1 - index / (double) (nTicks - 1));
		return Math.rint(y);
	}
}
