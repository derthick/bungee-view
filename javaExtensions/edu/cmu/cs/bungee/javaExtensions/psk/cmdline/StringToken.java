package edu.cmu.cs.bungee.javaExtensions.psk.cmdline;

//Copyright (c) 1998 Panos Kougiouris All Rights Reserved

/*
 * The Implemetation of Tokens for Strings
 *
 * @version 1.0, 11/02/1998
 * @author  Panos Kougiouris
 */

public class StringToken extends Token {
	public StringToken(final String a_name, final String a_message,
			final String a_environment_variable, final int aTokenOptions,
			final String a_def_value) {
		super(a_name, a_message, a_environment_variable, aTokenOptions);
		setDefaultValue(a_def_value);
	}

	@Override
	public String type() {
		return "<String>";
	}

	public String getValue() {
		return getValue(0);
	}

	private String getValue(final int i) {
		return (String) m_values.elementAt(i);
	}

	@Override
	public Object toObject(final String lexeme) {
		return lexeme;
	}
}