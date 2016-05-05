package edu.cmu.cs.bungee.client.viz.header;

import java.awt.Font;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.markup.Markup;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.markup.MarkupViz;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;

/**
 * The Query description in the Header
 */
class HeaderQueryDescription extends MarkupViz {

	HeaderQueryDescription(final @NonNull Bungee _art) {
		super(_art, BungeeConstants.HEADER_FG_COLOR);
		setColor(BungeeConstants.HEADER_BG_COLOR);
		enableExpandableText(true);
	}

	void setFont(@SuppressWarnings("unused") final Font font) {
		validate(getWidth());
	}

	void validate(final double w) {
		// System.out.println("HeaderQueryDescription.validate " + w + " " +
		// art.lineH());

		// Try to capture the enters and exits with margins
		final double buttonMargin = art.buttonMargin();
		setMargins(buttonMargin, buttonMargin);

		// allow for margins
		if (setBounds(0.0, 0.0, Math.max(2.0 * buttonMargin + 1.0, w), art.lineH() + 2.0 * buttonMargin)) {
			layout();
			// layoutBestFit();
		}
	}

	@Override
	public void layout() {
		if (((Header) getParent()).isInitted()) {
			super.layout();
			// System.out.println("HQD.layout isIncomplete=" + isIncomplete());
			if (isIncomplete()) {
				assert getChildrenCount() > 0 : getBounds() + " " + getContent();
				final String ellipses = "...";
				final double ellipsesW = art.getStringWidth(ellipses);
				final double oldW = getWidth();

				// Shouldn't be needed, but got 803.0000000000001 once.
				final double ellipsesOffset = Math.rint(oldW - ellipsesW);
				setWidth(ellipsesOffset);
				super.layout();
				setWidth(oldW);

				final APText ellipsesAPText = art.oneLineLabel();
				// new BungeeAPText(ellipses, art, null);
				ellipsesAPText.setOffset(ellipsesOffset, getChild(0).getYOffset());
				ellipsesAPText.setTextPaint(BungeeConstants.TEXT_FG_COLOR);
				ellipsesAPText.setText(ellipses);
				addChild(ellipsesAPText);
			}
		}
	}

	// Only called by Header.queryValidRedraw
	void setDescription(final int queryVersion, final @Nullable Pattern textSearchPattern) {
		final Markup description = art.getQuery().headerQueryDescription();
		setContent(description);
		layout();
		queryValidRedraw(queryVersion, textSearchPattern);
	}

	// // protected void mayHideTransients(HeaderQueryDescription node) {
	// // Summary.this.mayHideTransients();
	// // }
	// }

	// public void mayHideTransients(HeaderQueryDescription node) {
	// // ((Summary) getParent()).mayHideTransients();
	// }
	//
	// }

}