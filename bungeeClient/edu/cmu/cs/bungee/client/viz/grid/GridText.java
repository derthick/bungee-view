package edu.cmu.cs.bungee.client.viz.grid;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.client.viz.bungeeCore.BungeeConstants;
import edu.cmu.cs.bungee.client.viz.markup.BungeeAPText;
import edu.umd.cs.piccolo.event.PInputEvent;

class GridText extends BungeeAPText implements GridElement {

	private final @NonNull GridElementWrapper wrapper;

	/**
	 * constrainWidth/Height default to false; isWrap to true; justification
	 * defaults to LEFT_ALIGNMENT.
	 */
	GridText(final @NonNull GridElementWrapper _wrapper) {
		super(null, _wrapper.art(), null);
		wrapper = _wrapper;
		setTextPaint(BungeeConstants.GRID_FG_COLOR);
		setConstrainWidthToTextWidth(false);
		setConstrainHeightToTextHeight(false);
		maybeSetText(wrapper.getItemName());

		setWrap(true);
	}

	@Override
	public double getImageHeight() {
		return getHeight();
	}

	@Override
	public double getImageWidth() {
		return getWidth();
	}

	@Override
	public @NonNull GridElementWrapper getWrapper() {
		return wrapper;
	}

	@Override
	public void setDisplaySize(final int maxImageW, final int maxImageH) {
		setWidth(maxImageW - 3);
		setHeight(maxImageH - 3);
	}

	@SuppressWarnings("unused")
	@Override
	public boolean isBigEnough(final int edgeW, final int edgeH, final int thumbQuality) {
		return true;
	}

	@Override
	public Bungee art() {
		return getWrapper().art();
	}

	@Override
	public Item getItem() {
		return wrapper.getItem();
	}

	@Override
	public boolean brush(final boolean state, @SuppressWarnings("unused") final PInputEvent e) {
		getWrapper().brush(state);
		return false;
	}

	@Override
	public void printUserAction(@SuppressWarnings("unused") final int modifiers) {
		getWrapper().printUserAction();
	}

	@Override
	public int getModifiersEx(final PInputEvent e) {
		return e.getModifiersEx();
	}

}
