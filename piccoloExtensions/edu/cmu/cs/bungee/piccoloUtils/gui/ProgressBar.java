package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;

import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolox.PFrame;

public class ProgressBar extends PFrame {

	protected static final long serialVersionUID = 1L;

	private static final int BAR_W = 200;

	private static final int BAR_H = 10;

	private static final int BAR_OFFSET = 5;

	private final double minValue;

	private double maxValue; // NO_UCD (use final)

	private int percent;

	private final PNode bar;

	private final InputStream is;

	private final APText status;

	public ProgressBar(final InputStream stream, final String name) {
		// gross hack to pass arguments to initialize
		super(name, false, null);
		is = stream;
		minValue = 0;
		try {
			maxValue = is.available();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		final PNode BGbar = new PNode();
		BGbar.setPaint(Color.lightGray);
		BGbar.setOffset(BAR_OFFSET, BAR_OFFSET);
		BGbar.setWidth(BAR_W);
		BGbar.setHeight(BAR_H);

		bar = new PNode();
		bar.setPaint(Color.blue);
		bar.setOffset(BAR_OFFSET, BAR_OFFSET);
		bar.setHeight(BAR_H);

		status = new APText();
		status.setOffset(BAR_OFFSET, BAR_OFFSET * 2 + BAR_H);

		final PCanvas canvas = getCanvas();
		canvas.setPanEventHandler(null);
		canvas.setZoomEventHandler(null);
		final PLayer layer = canvas.getLayer();
		layer.addChild(BGbar);
		layer.addChild(bar);
		layer.addChild(status);

		setBounds(getDefaultFrameBounds());
	}

	// TODO Remove unused code found by UCDetector
	// public void setProgress() {
	// try {
	// setProgress(maxValue - is.available());
	// } catch (final IOException e) {
	// e.printStackTrace();
	// }
	// }

	public void setProgress(final String s) {
		try {
			status.maybeSetText(s);
			setProgress(maxValue - is.available());
		} catch (final IOException e) {
			// This can happen if we're at end of file.
		}
	}

	public void setProgress(final double value) {
		final int newPercent = (int) (100 * (value - minValue) / (maxValue - minValue));
		// if (newPercent != percent) {
		percent = newPercent;
		if (percent >= 100) {
			// setVisible(false);
			dispose();
		} else {
			bar.setWidth(percent * BAR_W / 100.0);
			repaint();
			// getCanvas().paintImmediately();
			// System.out.println("progess " + bar.getWidth());
		}
		// }
	}

	@Override
	public Rectangle getDefaultFrameBounds() {
		return new Rectangle(100, 100, 10 + BAR_W + 2 * BAR_OFFSET, 30 + 2
				* BAR_H + 3 * BAR_OFFSET);
	}
}
