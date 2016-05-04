//Copyright (c) 1998 Panos Kougiouris All Rights Reserved
package edu.cmu.cs.bungee.javaExtensions.psk.cmdline;

class IntegerToken extends Token {
	IntegerToken(final String a_name, final String a_message,
			final String a_environment_variable, final int aTokenOptions,
			final int a_def_value) {
		super(a_name, a_message, a_environment_variable, aTokenOptions);
		setDefaultValue(Integer.valueOf(a_def_value));
	}

	@Override
	public String type() {
		return "<Integer>";
	}

	public int getValue() {
		return getValue(0);
	}

	private int getValue(final int i) {
		final Integer in = (Integer) m_values.elementAt(i);
		return in.intValue();
	}

	@Override
	public Object toObject(final String lexeme) {
		return Integer.valueOf(lexeme);
	}
}