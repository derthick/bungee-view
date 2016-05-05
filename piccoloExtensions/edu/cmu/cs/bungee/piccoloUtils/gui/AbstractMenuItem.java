package edu.cmu.cs.bungee.piccoloUtils.gui;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class AbstractMenuItem implements MenuItem {

	private @NonNull String label;
	protected final @NonNull String enabledMouseDoc;
	protected @Nullable String disabledMouseDoc;

	protected AbstractMenuItem(final @NonNull String _label) {
		this(_label, _label);
	}

	public AbstractMenuItem(final @NonNull String _label, final @NonNull String _enabledMouseDoc) {
		this(_label, _enabledMouseDoc, _enabledMouseDoc + " is disabled");
	}

	public AbstractMenuItem(final @NonNull String _label, final @NonNull String _enabledMouseDoc,
			final @Nullable String _disabledMouseDoc) {
		super();
		label = _label;
		enabledMouseDoc = _enabledMouseDoc;
		disabledMouseDoc = _disabledMouseDoc;
	}

	@Override
	// No-op command. Our answer to graying out menu items. mouse doc should
	// explain why it's "gray"
	public @Nullable String doCommand() {
		assert isEnabled() : this;
		return null;
	}

	@Override
	public @NonNull String getLabel() {
		return label;
	}

	public void setLabel(final @NonNull String _label) {
		label = _label;
	}

	@Override
	public @NonNull String getMouseDoc() {
		final String result = isEnabled() ? enabledMouseDoc
				: disabledMouseDoc == null ? enabledMouseDoc + " is disabled" : disabledMouseDoc;
		assert result != null && UtilString.isNonEmptyString(result) : this + " " + isEnabled() + " " + result;
		return result;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

}
