package edu.cmu.cs.bungee.javaExtensions.psk.cmdline;

import java.util.Hashtable;
import java.util.Map;

public class MyApplicationSettings extends ApplicationSettings {
	private final Map<String, Token> options = new Hashtable<>();

	public MyApplicationSettings() {
	}

	public void addStringToken(final String optionName,
			final String documentation, final int tokenOptions,
			final String defaultValue) {
		final Token token = new StringToken(optionName, documentation, "",
				tokenOptions, defaultValue);
		options.put(optionName, token);
		addToken(token);
	}

	public void addIntegerToken(final String optionName,
			final String documentation, final int tokenOptions,
			final int defaultValue) {
		final Token token = new IntegerToken(optionName, documentation, "",
				tokenOptions, defaultValue);
		options.put(optionName, token);
		addToken(token);
	}

	public void addBooleanToken(final String optionName,
			final String documentation, final int tokenOptions) {
		final Token token = new BooleanToken(optionName, documentation, "",
				tokenOptions);
		options.put(optionName, token);
		addToken(token);
	}

	public String getStringValue(final String optionName) {
		final StringToken token = (StringToken) options.get(optionName);
		if (token == null) {
			return null;
		} else {
			return token.getValue();
		}
	}

	public int getIntegerValue(final String optionName) {
		return ((IntegerToken) options.get(optionName)).getValue();
	}

	public boolean getBooleanValue(final String optionName) {
		final BooleanToken token = (BooleanToken) options.get(optionName);
		if (token == null) {
			return false;
		} else {
			return token.getValue();
		}
	}

}
