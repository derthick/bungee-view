package edu.cmu.cs.bungee.javaExtensions;

/*
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import org.eclipse.jdt.annotation.NonNull;

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Class that handles the URL query with arguments.
 *
 * @author Johan Känngård, http://dev.kanngard.net
 */
@Immutable
public class URLQuery implements Serializable {
	protected static final long serialVersionUID = -959016825795947094L;

	/**
	 * The actual query part of the URL, after the ? character.
	 */
	private final String query;

	/**
	 * The query arguments.
	 */
	private Hashtable<String, Vector<String>> arguments = null;

	/**
	 * Creates a new URLQuery object with the specified query String.
	 *
	 * @param query1
	 *            the URL query part (after the ? character).
	 */
	public @Immutable URLQuery(final @NonNull String query1) {
		assert!query1.contains("?") : "Argument should be the part of the URL after the '?': " + query1;
		query = query1;
	}

	/**
	 * @return true iff key.equalsIgnoreCase("true").
	 */
	public boolean getBooleanArgument(final String key) {
		return Boolean.valueOf(getArgument(key, "false"));
	}

	/**
	 * @return value of key, or Integer.MIN_VALUE if not present.
	 */
	public int getIntArgument(final String key) {
		final String arg = getArgument(key).trim();
		return arg.length() == 0 ? Integer.MIN_VALUE : Integer.parseInt(arg);
	}

	/**
	 * Returns the first specified URL argument as a String.
	 *
	 * @param key
	 *            the argument key to get the value of.
	 * @return the value of the specified URL query key argument value, or "" if
	 *         the key is not present.
	 */
	public @NonNull String getArgument(final String key) {
		return Util.nonNull(getArgument(key, ""));
	}

	/**
	 * Returns the first specified URL argument as a String.
	 *
	 * @param key
	 *            the argument key to get the value of.
	 * @return the value of the specified URL query key argument value, or "" if
	 *         the key is not present.
	 */
	public String getArgument(final String key, final String defaultValue) {
		if (getArguments() == null) {
			return defaultValue;
		}
		final Vector<String> v = arguments.get(key);

		if (v == null || v.size() < 1) {
			return defaultValue;
		}
		return v.elementAt(0);
	}

	/**
	 * Parses the specified URL and extracts the arguments from the query part.
	 *
	 * @return a Hashtable containing the argument keys as keys and Vectors with
	 *         the argument values as values.
	 */
	private Hashtable<String, Vector<String>> getArguments() {
		if (arguments != null) {
			return arguments;
		}
		if (query == null) {
			return null;
		}
		final Hashtable<String, Vector<String>> table = new Hashtable<>();

		String pair = null;
		String key = null;
		String value = null;
		int index = 0;
		Vector<String> v = null;
		final StringTokenizer tok = new StringTokenizer(query, "&");

		while (tok.hasMoreElements()) {
			pair = (String) tok.nextElement();
			index = pair.indexOf('=');

			if (index == -1) {
				key = pair;
				value = "";
			} else {
				key = pair.substring(0, index);
				try {
					value = URLDecoder.decode(pair.substring(index + 1), "UTF-8");
				} catch (final UnsupportedEncodingException e) {
					value = pair.substring(index + 1);
				}
			}

			if (table.containsKey(key)) {
				v = table.get(key);
				v.addElement(value);
			} else {
				v = new Vector<>();
				v.addElement(value);
				table.put(key, v);
			}
		}
		arguments = table;
		return arguments;
	}

	/**
	 * Returns a String representation of this URLQuery.
	 *
	 * @return the original query that was used when created this URLQuery.
	 */
	@Override
	public String toString() {
		return UtilString.toString(this, query);
	}

	/**
	 * Returns a String representation of this URLQuery.
	 *
	 * @return the original query that was used when created this URLQuery.
	 */
	public String format() {
		return query.replace('&', '\n');
	}
}
