package edu.cmu.cs.bungee.javaExtensions.psk.cmdline;

//Copyright (c) 1998 Panos Kougiouris All Rights Reserved

/*
 * The class that contains all the tokens and
 * initiates the parsing
 *
 * @version  1.0, 11/04/1998
 * @author Panos Kougiouris
 */
public class ApplicationSettings {
	private static final int OPT_DO_NOT_IGNORE_UNKNOWN = 1;
	private static final int OPT_IGNORE_UNKNOWN = 2;

	public ApplicationSettings() {
		this(OPT_DO_NOT_IGNORE_UNKNOWN);
	}

	private ApplicationSettings(final int aApplicationSettingsOptions) {
		m_argDescriptions = new java.util.Vector<>();
		m_flags = aApplicationSettingsOptions;
	}

	public void addToken(final Token argum) {
		for (int i = 0; i < m_argDescriptions.size(); i++) {
			final Token argDesc = m_argDescriptions.elementAt(i);

			// Make sure we have no argument clash
			if (argDesc.name().compareTo(argum.name()) == 0) {
				// System.err.print("ApplicationSettings ERROR: option \"");
				// System.err.print(argum.name());
				// System.err.println("\"is used more than once");
				throw new IllegalArgumentException(
						"ApplicationSettings ERROR: option \"" + argum.name() + "\"is used more than once");
			}

			// make sure there is only one that is non switch
			if (!argDesc.isSwitch() && !argum.isSwitch()) {
				final StringBuilder msg = new StringBuilder();
				msg.append("ApplicationSettings ERROR: arguments defined in both '").append(argum.name());
				msg.append("' and '").append(argDesc.name()).append("'");
				msg.append("Arguments should be defined only once.");
				// System.exit(-2);
				throw new IllegalArgumentException(msg.toString());
			}
		}
		m_argDescriptions.addElement(argum);
	}

	public boolean parseArgs(final String[] args) {
		m_cmdLineArgs = new StringArrayIterator(args);

		// setEnvironmentValues();
		try {
			parseInternal();
			return true;
		} catch (final Exception e) {
			// We've already printed an error message
			return false;
		}
	}

	private void printUsage(final String reason) throws Exception {
		// Print the first line
		System.err.print("Error: ");
		System.err.println(reason + "\n");
		// Print the usage line
		System.err.print("Usage: ");
		System.err.print(m_programName + " ");
		for (int i = 0; i < m_argDescriptions.size(); i++) {
			final Token arg = m_argDescriptions.elementAt(i);
			arg.printUsage(System.err);
		}
		System.err.println("\n");

		// Print the explanations
		for (int i = 0; i < m_argDescriptions.size(); i++) {
			final Token arg = m_argDescriptions.elementAt(i);
			arg.printUsageExtended(System.err);
		}
		final Exception exception = new Exception(reason);
		// exception.printStackTrace();
		throw exception;
	}

	// ------------------------------------------------

	// This functions should be called only once
	private void parseNonSwitch() throws Exception {
		for (int i = 0; i < m_argDescriptions.size(); i++) {
			final Token argDesc = m_argDescriptions.elementAt(i);

			if (!argDesc.parseArgument(m_cmdLineArgs)) {
				continue;
			}

			// here should be the end...
			if (!m_cmdLineArgs.EOF()) {
				this.printUsage("too many commeand line arguments.");
			}
			return;
		}

		String str = "Unexpected argument ";
		str += m_cmdLineArgs.get();
		if (!ignoreUnknownSwitches()) {
			this.printUsage(str);
		}
	}

	private void parseInternal() throws Exception {
		// skip the name of the program
		m_programName = "program";

		while (!m_cmdLineArgs.EOF()) {
			try {
				// System.out.println(m_cmdLineArgs.get()+"
				// "+Token.isASwitch(m_cmdLineArgs.get()));
				if (Token.isASwitch(m_cmdLineArgs.get())) {
					this.parseSwitch();
				} else {
					this.parseNonSwitch();
					break;
				}
			} catch (final Exception ex) {
				String str = ex.getMessage();
				if (ex.getClass() == NumberFormatException.class) {
					str = str + " ";
					str = str + " wrong argument type";
				}
				// most likely 'argument expected'
				this.printUsage(str);
			}
			m_cmdLineArgs.moveNext();
		}

		for (int i = 0; i < m_argDescriptions.size(); i++) {
			final Token argDesc = m_argDescriptions.elementAt(i);
			if (!argDesc.isUsed() && argDesc.polarity()) {
				String str;
				str = "missing required argument. Name: ";
				str += argDesc.extendedName();
				this.printUsage(str);
			}
		}
	}

	private void parseSwitch() throws Exception {
		int i = 0;
		for (i = 0; i < m_argDescriptions.size(); i++) {
			final Token argDesc = m_argDescriptions.elementAt(i);
			if (argDesc.parseSwitch(m_cmdLineArgs)) {
				return;
			}
		}

		// We tried all the tokens and no one recognized
		if (i >= m_argDescriptions.size()) {
			String str = String.valueOf("Unknown option ");
			str += m_cmdLineArgs.get();
			if (!ignoreUnknownSwitches()) {
				this.printUsage(str);
			}
		}
	}

	// protected void setEnvironmentValues() {
	// for (int i = 0; i < m_argDescriptions.size(); i++) {
	// Token argDesc = ((Token) m_argDescriptions.elementAt(i));
	// if (argDesc.hasEnvironmentVariable()) {
	// String str = util.GetEnvVariable(argDesc
	// .getEnvironmentVariable());
	// if (str.length() != 0) {
	// argDesc.SetValueFromLexeme(str, 0);
	// }
	// }
	// }
	// }

	private boolean ignoreUnknownSwitches() {
		return (m_flags & OPT_IGNORE_UNKNOWN) != 0;
	}

	// Data members
	private StringArrayIterator m_cmdLineArgs;
	private String m_programName;
	private final java.util.Vector<Token> m_argDescriptions;
	// Vector of Argv objects
	private final int m_flags;
}
