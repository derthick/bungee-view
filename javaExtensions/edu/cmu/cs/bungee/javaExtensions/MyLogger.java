package edu.cmu.cs.bungee.javaExtensions;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.annotation.NonNull;

public class MyLogger implements Serializable {

	public static final @NonNull Level SEVERE = Util.nonNull(Level.SEVERE);
	public static final @NonNull Level WARNING = Util.nonNull(Level.WARNING);
	public static final @NonNull Level INFO = Util.nonNull(Level.INFO);
	public static final @NonNull Level FINE = Util.nonNull(Level.FINE);

	public static final @NonNull Level DEFAULT_LOGGER_LEVEL = WARNING;

	private final Logger logger;

	private final String sourceClass;

	public static MyLogger getMyLogger(final Class<?> _class) {
		MyLogger result = null;
		try {
			result = new MyLogger(_class);
		} catch (final java.security.AccessControlException e) {
			System.err.println("MyLogger.getMyLogger failed (java.security.AccessControlEkception).");
			e.printStackTrace();
		}
		return result;
	}

	private MyLogger(final Class<?> _class) {
		this(_class, DEFAULT_LOGGER_LEVEL);
	}

	private MyLogger(final Class<?> _class, final Level level) {
		final String className = _class.getName();
		logger = Logger.getLogger(className);
		setLevel(level);
		sourceClass = UtilString.shortClassName(_class);

		try {
			logger.addHandler(new FileHandler(logfilenameForClassName(sourceClass)));
			logger.addHandler(new ConsoleHandler());
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private static String logfilenameForClassName(final String shortClassName) {
		final String catalinaHome = System.getenv("CATALINA_HOME");
		if (!new File(catalinaHome).exists()) {
			System.err.println(
					"The enviroment variable CATALINA_HOME=" + catalinaHome + ". That directory does not exist.");
		}
		final String dateString = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		final String[] pathComponents = { catalinaHome, "logs",
				shortClassName + "_FileHandler." + dateString + ".log" };
		final String pathSeparator = System.getProperties().getProperty("file.separator");
		assert pathSeparator != null;
		final String filename = UtilString.join(pathComponents, pathSeparator);
		return filename;
	}

	boolean isLoggable(final Level _level) {
		return logger.isLoggable(_level);
	}

	public Level getLevel() { // NO_UCD (unused code)
		return logger.getLevel();
	}

	public void setLevel(final Level _level) { // NO_UCD (unused code)
		logger.setLevel(_level);
	}

	// TODO Remove unused code found by UCDetector
	// public static boolean myAssertp(final MyLogger myLogger, final boolean
	// condition, final String msg,
	// final String sourceClass, final String sourceMethod) {
	// if (myLogger != null) {
	// return myLogger.myAssertp(condition, msg, sourceMethod);
	// } else if (!condition) {
	// System.err.println("MyLogger.logp " + SEVERE + ": " + sourceClass +
	// "." + sourceMethod + ": " + msg);
	// throw new AssertionError(msg);
	// }
	// return condition;
	// }

	public boolean myAssertp(final boolean condition, final Object msg, final String sourceMethod)
			throws AssertionError {
		if (!condition) {
			final AssertionError assertionError = new AssertionError(msg);
			logp(msg.toString(), SEVERE, assertionError, sourceMethod);
			throw assertionError;
		}
		return condition;
	}

	public static void logp(final MyLogger myLogger, final Object msg, final Level level, final String sourceClass,
			final String sourceMethod) {
		if (myLogger != null) {
			myLogger.logp(msg, level, sourceMethod);
		} else {
			System.err.println("MyLogger.logp " + level + ": " + sourceClass + "." + sourceMethod + ": " + msg);
		}
	}

	public void logp(final Object message, final Level _level, final String sourceMethod) {
		logger.logp(_level, sourceClass, sourceMethod, message.toString());
	}

	public static void logp(final MyLogger myLogger, final Object msg, final Level level, final Throwable e,
			final String sourceClass, final String sourceMethod) {
		if (myLogger != null) {
			myLogger.logp(msg.toString(), level, e, sourceMethod);
		} else {
			System.err
					.println("MyLogger.logp " + level + ": " + sourceClass + "." + sourceMethod + ": " + msg + " " + e);
		}
	}

	void logp(final Object message, final Level _level, final Throwable e, final String sourceMethod) {
		logger.logp(_level, sourceClass, sourceMethod, message.toString(), e);
	}
}
