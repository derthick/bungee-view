package edu.cmu.cs.bungee.javaExtensions.psk.cmdline;

import java.util.Vector;

/*
 * Each Token object encapsulates one command
 * line argumet or switch.
 *
 * @version  1.0, 11/02/1998
 * @author Panos Kougiouris
 */

public abstract class Token {
	String name() {
		return m_name;
	}

	// TODO Remove unused code found by UCDetector
	// public int NumberOfValues() {
	// return m_values.size();
	// }

	String extendedName() {
		if (isSwitch()) {
			return "-" + name();
		} else {
			return name();
		}
	}

	// -----------------------------------------------
	// Subclasses should implement these
	// -----------------------------------------------

	public abstract Object toObject(String lexeme);

	// All but bool (where merely the appearence of the flag
	// signifies the existence) return true;
	@SuppressWarnings("static-method")
	public boolean hasOneOrMoreArgs() {
		return true;
	}

	public abstract String type();

	// Also implement these two methods
	// <Type> getValue(int i);
	// <Type> getValue();

	protected Token(final String a_name, final String a_message, final String a_environment_variable,
			final int aTokenOptions // of
	// type
	// TokenOptions
	) {
		m_name = a_name;
		m_message = a_message;
		m_env_variable = a_environment_variable;
		m_flags = aTokenOptions;
		m_firstTime = true;
		m_values = new Vector<>(1);
	}

	// -----------------------------------------------
	// These methods are used by the ApplicationSettings class
	// Thast' why they are protected
	// -----------------------------------------------

	// If we match the switch
	// we parse as many command line arguments as they apply to this
	// switch and then return just before the next one we do not
	// recognize
	protected boolean parseSwitch(final StringArrayIterator cmdLineArgs) throws Exception {
		if (this.isArgument()) {
			return false;
		}
		if ((this.isUsed()) && (!this.allowsMultipleValues())) {
			return false;
		}
		if (cmdLineArgs.get().substring(1).indexOf(name()) != 0) {
			return false;
		}

		// after the match what remains e.g. if we are -t and
		// argument is -tom then rest == 'om'
		String lexeme = cmdLineArgs.get().substring(1 + name().length());
		if (lexeme.length() == 0) {
			// the "-t foo" or "-t" case
			if (this.hasOneOrMoreArgs()) {
				// move to the "foo"
				cmdLineArgs.moveNext();
				if (!cmdLineArgs.EOF()) {
					lexeme = cmdLineArgs.get();
				} else {
					String str = String.valueOf("Argument expected for option ");
					str += this.extendedName();
					throw new Exception(str);
				}
			}
		} else {
			// "-tfoo" case
			if (!this.hasOneOrMoreArgs()) {
				final String str = "No Argument expected for option " + this.name();
				throw new Exception(str);
			}
		}

		this.addValueFromLexeme(lexeme);
		this.setUsed();

		/*
		 * If you comment out these lines then "-l 1 2 3" will be permitted. Now
		 * this should be "-l 1 -l 2 -l 3"
		 *
		 * if (allowsMultipleValues()) { cmdLineArgs.moveNext(); // if it
		 * supports multiple parse more arguments while ((!cmdLineArgs.EOF()) &&
		 * (!isASwitch(cmdLineArgs.get()))) {
		 * this.AddValueFromLexeme(cmdLineArgs.get()); cmdLineArgs.moveNext(); }
		 * cmdLineArgs.movePrevious(); }
		 */

		return true;
	}

	protected boolean parseArgument(final StringArrayIterator cmdLineArgs) {
		if (isSwitch()) {
			return false;
		}
		if ((isUsed()) && (!allowsMultipleValues())) {
			return false;
		}

		// if it supports multiple parse more arguments
		while ((!cmdLineArgs.EOF()) && (!isASwitch(cmdLineArgs.get()))) {
			this.addValueFromLexeme(cmdLineArgs.get());
			this.setUsed();
			cmdLineArgs.moveNext();
			if (!allowsMultipleValues()) {
				break;
			}
		}

		return true;
	}

	protected void printUsage(final java.io.PrintStream str) {
		if (!this.polarity()) {
			str.print("[");
		}
		str.print(this.extendedName() + " ");
		str.print(this.type());
		if (this.allowsMultipleValues()) {
			str.print(" ...");
		}
		if (!this.polarity()) {
			str.print("]");
		}
		str.print(" ");
	}

	protected void printUsageExtended(final java.io.PrintStream str) {
		str.print("\t");
		str.print(this.extendedName() + " ");
		str.print("'" + this.m_message + "' ");
		if (hasEnvironmentVariable()) {
			str.print(" Environment: $" + this.m_env_variable);
		}
		if (!this.polarity()) {
			str.print(" Default: ");
			str.print(this.getDefaultValue());
		}
		str.println();
	}

	private boolean hasEnvironmentVariable() {
		return this.m_env_variable.compareTo("") != 0;
	}

	// TODO Remove unused code found by UCDetector
	// protected String getEnvironmentVariable() {
	// return this.m_env_variable;
	// }

	protected boolean polarity() {
		return (m_flags & optRequired) == optRequired;
	}

	protected boolean isSwitch() {
		return !isArgument();
	}

	private boolean isArgument() {
		return (m_flags & optArgument) == optArgument;
	}

	private boolean allowsMultipleValues() {
		return (m_flags & optMultiple) == optMultiple;
	}

	protected boolean isUsed() {
		return (m_flags & optAlreadyUsed) == optAlreadyUsed;
	}

	private void setUsed() {
		m_flags |= optAlreadyUsed;
	}

	private void addValueFromLexeme(final String lexeme) {
		if (m_firstTime) {
			setValueFromLexeme(lexeme, 0);
		} else {
			assert this.allowsMultipleValues();
			m_values.addElement(toObject(lexeme));
		}
		m_firstTime = false;
	}

	private void setValueFromLexeme(final String lexeme, final int i) {
		m_values.setSize(java.lang.Math.max(m_values.size(), i + 1));
		m_values.setElementAt(toObject(lexeme), i);
	}

	private String getDefaultValue() {
		if (m_defaultValue == null) {
			return null;
		}
		return m_defaultValue.toString();
	}

	protected void setDefaultValue(final Object obj) {
		m_defaultValue = obj;
		m_values.setSize(java.lang.Math.max(m_values.size(), 1));
		m_values.setElementAt(obj, 0);
	}

	protected static boolean isASwitch(final String arg) {
		return (arg.charAt(0) == '-');
	}

	private final String m_name;
	private final String m_message;
	private int m_flags;
	private final String m_env_variable;
	protected final Vector<Object> m_values;
	protected Object m_defaultValue;
	private boolean m_firstTime;

	private static final int optAlreadyUsed = 16;
	/**
	 * this argument must be supplied on command line (no default value)
	 */
	public static final int optRequired = 1;
	/**
	 * Normal program argument (not a switch)
	 */
	private static final int optArgument = 2;
	/**
	 * allows multiple values
	 */
	private static final int optMultiple = 4;
	/**
	 * signaled by -arg_name
	 */
	public static final int optSwitch = 8;

}

/*
 * This is a utility class. It encapsulates an array and an index that points to
 * the current object
 *
 * @version 1.0,11/01/1998 @author Panos Kougiouris
 */
