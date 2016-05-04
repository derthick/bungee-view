/*
 * Copyright (c) Ian F. Darwin, http://www.darwinsys.com/, 1996-2002.
 * All rights reserved. Software written by Ian F. Darwin and others.
 * $Id: LICENSE,v 1.8 2004/02/09 03:33:38 ian Exp $
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Java, the Duke mascot, and all variants of Sun's Java "steaming coffee
 * cup" logo are trademarks of Sun Microsystems. Sun's, and James Gosling's,
 * pioneering role in inventing and promulgating (and standardizing) the Java
 * language and environment is gratefully acknowledged.
 *
 * The pioneering role of Dennis Ritchie and Bjarne Stroustrup, of AT&T, for
 * inventing predecessor languages C and C++ is also gratefully acknowledged.
 */
package edu.cmu.cs.bungee.javaExtensions;

import java.text.FieldPosition;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParsePosition;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilString.Justification;

// public class StringAlignDemo {
//
// /** Demonstrate and test StringAlign class */
// public static void main(String[] argv) {
// String[] mesg = {"JavaFun", "JavaFun!" };
// for (int i=0; i<mesg.length; i++) {
// System.out.println("Input String \"" + mesg[i] + "\"");
// dump(StringAlign.JUST_LEFT, 5,
// new StringAlign(5, StringAlign.JUST_LEFT).format(mesg[i]));
// dump(StringAlign.JUST_LEFT, 10,
// new StringAlign(10, StringAlign.JUST_LEFT).format(mesg[i]));
// dump(StringAlign.JUST_CENTER, 5,
// new StringAlign(5, StringAlign.JUST_CENTER).format(mesg[i]));
// dump(StringAlign.JUST_CENTER, 10,
// new StringAlign(10, StringAlign.JUST_CENTER).format(mesg[i]));
// dump(StringAlign.JUST_RIGHT, 5,
// new StringAlign(5, StringAlign.JUST_RIGHT).format(mesg[i]));
// dump(StringAlign.JUST_RIGHT, 10,
// new StringAlign(10, StringAlign.JUST_RIGHT).format(mesg[i]));
// }
// }
//
// private static void dump(int format, int len, String s) {
// System.out.print((char)format + "[" + len + "]");
// System.out.print(" ==> \"");
// System.out.print(s);
// System.out.print('"');
// System.out.println();
// }
// }

/**
 * Bare-minimum String formatter (string aligner). XXX When 1.5 is common,
 * change from ints to enum for alignment.
 */
public class StringAlign extends Format {
	protected static final long serialVersionUID = 1L;

	// public enum Justification {
	// JUST_LEFT, JUST_CENTRE, JUST_RIGHT
	// }

	// /* Constant for left justification. */
	// public static final int JUST_LEFT = 'l';
	// /* Constant for centering. */
	// private static final int JUST_CENTRE = 'c';
	// /* Centering Constant, for those who spell "centre" the American way. */
	// // TODO Remove unused code found by UCDetector
	// // public static final int JUST_CENTER = JUST_CENTRE;
	// /** Constant for right-justified Strings. */
	// public static final int JUST_RIGHT = 'r';

	/** Current justification */
	private final Justification just;
	/** Current max length */
	private final int maxChars;

	/**
	 * Construct a StringAlign formatter; length and alignment are passed to the
	 * Constructor instead of each format() call as the expected common use is
	 * in repetitive formatting e.g., page numbers.
	 *
	 * @param _maxChars
	 *            - the length of the output
	 * @param _just
	 *            - one of JUST_LEFT, JUST_CENTRE or JUST_RIGHT
	 */
	public StringAlign(final int _maxChars, final Justification _just) {
		switch (_just) {
		case LEFT:
		case CENTER:
		case RIGHT:
			just = _just;
			break;
		default:
			throw new IllegalArgumentException("invalid justification arg.");
		}
		if (_maxChars < 0) {
			throw new IllegalArgumentException("maxChars must be positive.");
		}
		maxChars = _maxChars;
	}

	/**
	 * Format a String.
	 *
	 * @param obj
	 *            _ the string to be aligned.
	 * @parm where - the StringBuilder to append it to.
	 * @param ignore
	 *            - a FieldPosition (may be null, not used but specified by the
	 *            general contract of Format).
	 */
	@Override
	public @NonNull StringBuffer format(final Object obj, final StringBuffer where, final FieldPosition ignore) {
		return format(obj, where, maxChars, just);
	}

	// TODO Remove unused code found by UCDetector
	// public StringBuilder format(final Object obj, final StringBuilder where)
	// {
	// return format(obj, where, null);
	// }

	// TODO Remove unused code found by UCDetector
	// public String format(final Object obj, final Format format) {
	// return format(format.format(obj));
	// }

	/**
	 * @param n
	 * @param format
	 *            Use this format to get a string, and then pad according to
	 *            this StringFormat.
	 */
	public @NonNull String format(final int n, final @NonNull NumberFormat format) {
		return format(Util.nonNull(format.format(n)));
	}

	public @NonNull String format(final double obj, final @NonNull NumberFormat format) {
		return format(Util.nonNull(format.format(obj)));
	}

	private static final void pad(final @NonNull StringBuffer where, final int howMany) {
		for (int i = 0; i < howMany; i++) {
			where.append(' ');
		}
	}

	/** Convenience Routine */
	private @NonNull String format(final @NonNull String s) {
		return Util.nonNull(format(s, null, null).toString());
	}

	/** ParseObject is required, but not useful here. */
	@Override
	public Object parseObject(final String source, @SuppressWarnings("unused") final ParsePosition pos) {
		return source;
	}

	private static @NonNull StringBuffer format(final Object obj, StringBuffer where, final int maxChars,
			final Justification just2) {
		if (where == null) {
			where = new StringBuffer();
		}

		final String s = obj == null ? "<null>" : obj.toString();
		final String wanted = s.substring(0, Math.min(s.length(), maxChars));

		// Get the spaces in the right place.
		switch (just2) {
		case RIGHT:
			pad(where, maxChars - wanted.length());
			where.append(wanted);
			break;
		case CENTER:
			final int toAdd = maxChars - wanted.length();
			pad(where, toAdd / 2);
			where.append(wanted);
			pad(where, toAdd - toAdd / 2);
			break;
		case LEFT:
			where.append(wanted);
			pad(where, maxChars - wanted.length());
			break;
		default:
			assert false : just2;
			break;
		}
		return where;
	}

	// TODO Remove unused code found by UCDetector
	// public static String format(final Object obj, final int maxChars,
	// final int just) {
	// return format(obj, null, maxChars, just).toString();
	// }

}
