package edu.cmu.cs.bungee.piccoloUtils.gui;

class MenuClickHandler extends MyInputEventHandler<Menu> {

	public MenuClickHandler() {
		super(Menu.class);
	}

	@Override
	public boolean click(final Menu node) {
		node.pick();
		return true;
	}

	@Override
	public boolean exit(final Menu node) {
		node.setMouseDoc(false);
		if (!node.visible) {
			// if label was expanded, it shouldn't be now
			final ExpandableText label = node.label;
			label.contract();
			node.setHeight(label.getHeight());
		}

		// return false so ExpandableTextHover will also be called.
		return false;
	}

	@Override
	public boolean enter(final Menu node) {
		node.setMouseDoc(true);

		// return false so ExpandableTextHover will also be called.
		return false;
	}

}