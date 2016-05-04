package edu.cmu.cs.bungee.javaExtensions.psk.cmdline;

public class StringArrayIterator {
	public StringArrayIterator(final String[] aStrings) {
		m_index = 0;
		m_strings = aStrings;
	}

	public boolean EOF() {
		return m_index >= m_strings.length;
	}

	public void moveNext() {
		m_index++;
	}

	public String get() {
		// System.out.println("get '" + m_strings[m_index] + "'");
		return m_strings[m_index];
	}

	private final String[] m_strings;
	private int m_index;
}