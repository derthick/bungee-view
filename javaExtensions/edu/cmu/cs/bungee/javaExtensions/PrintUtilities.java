package edu.cmu.cs.bungee.javaExtensions;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.RepaintManager;

/**
 * A simple utility class that lets you very simply print an arbitrary
 * component. Just pass the component to the PrintUtilities.printComponent. The
 * component you want to print doesn't need a print method and doesn't have to
 * implement any interface or do anything special at all.
 * <P>
 * If you are going to be printing many times, it is marginally more efficient
 * to first do the following:
 *
 * <PRE>
 * PrintUtilities printHelper = new PrintUtilities(theComponent);
 * </PRE>
 *
 * then later do printHelper.print(). But this is a very tiny difference, so in
 * most cases just do the simpler
 * PrintUtilities.printComponent(componentToBePrinted).
 *
 * 7/99 Marty Hall, http://www.apl.jhu.edu/~hall/java/ May be freely used or
 * adapted.
 */

public class PrintUtilities implements Printable {
	private final Component componentToBePrinted;

	public static void printComponent(final Component c) {
		new PrintUtilities(c).print();
	}

	private PrintUtilities(final Component componentToBePrinted1) {
		componentToBePrinted = componentToBePrinted1;
	}

	private void print() {
		final PrinterJob printJob = PrinterJob.getPrinterJob();
		printJob.setPrintable(this);
		if (printJob.printDialog()) {
			try {
				printJob.print();
			} catch (final PrinterException pe) {
				System.out.println("Error printing: " + pe);
			}
		}
	}

	@Override
	public int print(final Graphics g, final PageFormat pageFormat, final int pageIndex) {
		// System.out.println("printing " + pageIndex);
		if (pageIndex > 0) {
			return (NO_SUCH_PAGE);
		} else {
			final Graphics2D g2d = (Graphics2D) g;
			g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
			final double scaleW = pageFormat.getImageableWidth() / componentToBePrinted.getWidth();
			final double scaleH = pageFormat.getImageableHeight() / componentToBePrinted.getHeight();
			final double scale = Math.min(scaleW, scaleH);
			g2d.scale(scale, scale);
			disableDoubleBuffering(componentToBePrinted);
			componentToBePrinted.paint(g2d);
			enableDoubleBuffering(componentToBePrinted);
			return (PAGE_EXISTS);
		}
	}

	/**
	 * The speed and quality of printing suffers dramatically if any of the
	 * containers have double buffering turned on. So this turns if off
	 * globally.
	 *
	 * @see #enableDoubleBuffering
	 */
	private static void disableDoubleBuffering(final Component c) {
		final RepaintManager currentManager = RepaintManager.currentManager(c);
		currentManager.setDoubleBufferingEnabled(false);
	}

	/** Re-enables double buffering globally. */

	private static void enableDoubleBuffering(final Component c) {
		final RepaintManager currentManager = RepaintManager.currentManager(c);
		currentManager.setDoubleBufferingEnabled(true);
	}
}
