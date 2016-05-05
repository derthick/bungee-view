package edu.cmu.cs.bungee.populate;

import java.util.Hashtable;

/**
 * Maps from a field name (like 651a or topic) to a Field.
 *
 */
class Decoder {

	private final Hashtable<String, Field> codes = new Hashtable<>();

	public Field decode(final String name) {
		return codes.get(name);
	}

	void addCode(final String name, final Field field) {
		// System.out.println("addCode " + name + " " + field);
		codes.put(name, field);
	}

}
