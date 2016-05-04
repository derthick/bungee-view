//Copyright (c) 1998 Panos Kougiouris All Rights Reserved
package edu.cmu.cs.bungee.javaExtensions.psk.cmdline;

class BooleanToken extends Token {
	/**
	 * The mere appearance means true, so the default value is always false. If
	 * you want a switch whose default is true, you have to negate the sense:
	 * e.g. dont_do_it instead of do_it.
	 */
	BooleanToken(final String a_name, final String a_message,
			final String a_environment_variable, final int aTokenOptions
	// boolean a_def_value
	) {
		super(a_name, a_message, a_environment_variable, aTokenOptions);
		setDefaultValue(Boolean.FALSE);
	}

	@Override
	public String type() {
		return "";
	}

	public boolean getValue() {
		return getValue(0);
	}

	private boolean getValue(final int i) {
		final Boolean in = (Boolean) m_values.elementAt(i);
		return in.booleanValue();
	}

	@Override
	public Object toObject(String lexeme) {
		if (lexeme.length() == 0) {
			final Boolean bl = (Boolean) m_defaultValue;
			boolean val = bl.booleanValue();
			val = !val;
			lexeme = (val ? "true" : "false");
		}
		return Boolean.valueOf(lexeme);
	}

	@Override
	public boolean hasOneOrMoreArgs() {
		return false;
	}
}