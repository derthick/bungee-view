package edu.cmu.cs.bungee.client.viz.informedia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.javaExtensions.URLQuery;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

abstract class InformediaThread extends Thread {
	/**
	 * HttpURLConnection.getInputStream barfs if the URL sent to the BV server
	 * is too long. Empirically, 5000 seems to avoid errors.
	 */
	// private static final int MAX_ARG_LENGTH = 5000;

	protected final Bungee art;

	protected Query query() {
		// Don't cache this, because it may be null at init time.
		return art.getQuery();
	}

	/**
	 * Can be "Movie" or "Segment"
	 */
	protected final @NonNull String itemLevel;
	/**
	 * "selectedMovie" or "selectedSegment"
	 */
	protected final String selectedItemTerm;
	/**
	 * "segments" or "movies"
	 */
	protected final String infIDsArg;
	private final int listenPort;
	protected final int sendPort;
	protected Socket sendSocket;
	private PrintWriter outputWriter;
	private final String host;

	InformediaThread(final String name, final Bungee _art, final String _host, final int _listenPort,
			final int _sendPort) {
		super(name);
		// System.out.println("InformediaThread " + name + " _art=" + _art
		// + " _host=" + _host + " _listenPort=" + _listenPort
		// + " _sendPort=" + _sendPort);
		art = _art;
		itemLevel = computeItemLevel();
		selectedItemTerm = selectedItemTerm();
		infIDsArg = infIDsArg();
		listenPort = _listenPort;
		sendPort = _sendPort;
		host = _host;
	}

	private @NonNull static String computeItemLevel() {
		return "Movie";
	}

	private String selectedItemTerm() {
		return "selected" + UtilString.capitalize(itemLevel);
	}

	private String infIDsArg() {
		return (itemLevel + "s").toLowerCase();
	}

	protected String getInfIDsString(final int startOffset, final int endOffsetExclusive) {
		return UtilString.join(getInfIDs(art.getItems(startOffset, endOffsetExclusive)));
	}

	private static int[] getInfIDs(final Item[] items) {
		int[] result = null;
		result = new int[items.length];
		for (int i = 0; i < items.length; i++) {
			result[i] = items[i].getID();
		}
		// System.out.println("InformediaThread.getInfIDs("
		// + UtilString.valueOfDeep(items) + ") returning "
		// + (result == null ? 0 : result.length) + " " + infIDsArg + "\n"
		// + UtilString.valueOfDeep(result));
		assert!UtilArray.hasDuplicates(result);
		return result;
	}

	// protected static String truncateArgString(String arg) {
	// if (arg.length() > MAX_ARG_LENGTH) {
	// final String truncated = arg.substring(0,
	// arg.lastIndexOf(',', MAX_ARG_LENGTH));
	// System.err
	// .println("InformediaThread.truncateArgString Warning: Truncating list "
	// + arg.substring(0, 20)
	// + "... to "
	// + truncated.length() + " characters\n" + truncated);
	// arg = truncated;
	// }
	// // System.out.println("InformediaThread.truncateArgString return " +
	// // arg.length() + " " +
	// // arg);
	// return arg;
	// }

	/**
	 * @param map
	 *            attributes->values
	 * @return URL query formatted command string
	 */
	protected static @NonNull String argify(final Map<String, String> map) {
		final StringBuilder buf = new StringBuilder();
		for (final Map.Entry<String, String> entry : map.entrySet()) {
			if (buf.length() > 0) {
				buf.append("&");
			}
			try {
				buf.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8"));
			} catch (final UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return Util.nonNull(buf.toString());
	}

	protected static int getInfID(final Item item) {
		final Item[] items = { item };
		return getInfIDs(items)[0];
	}

	protected static Item getItem(final int infID) {
		final int[] infIDs = { infID };
		final Item[] items = getItems(infIDs);
		// Might not have any items for this infID
		return items.length > 0 ? items[0] : null;
	}

	protected @NonNull static Item[] getItems(final int[] infIDs) {
		final Item[] result = new Item[infIDs.length];
		for (int i = 0; i < infIDs.length; i++) {
			result[i] = Item.ensureItem(infIDs[i]);
		}
		assert!UtilArray.hasDuplicates(result);
		assert result != null;
		return result;
	}

	@Override
	// Run does the listening. BV calls the functions that do the talking.
	public void run() {
		System.out.println(getName() + " listening on " + listenPort);
		try (ServerSocket serverSocket = new ServerSocket(listenPort); Socket listenSocket = serverSocket.accept();) {
			// serverSocket.accept blocks until a connection is made.

			listen(listenSocket);
			// listen processes inputs for that connection.
		} catch (final IOException e) {
			e.printStackTrace();
			return;
		}
	}

	private void listen(final Socket listenerSocket) {
		try (final BufferedReader in = new BufferedReader(new InputStreamReader(listenerSocket.getInputStream()));
				PrintWriter responseWriter = new PrintWriter(listenerSocket.getOutputStream(), true);) {

			// System.out.println(getName() + ".listen listening on port "
			// + listenerSocket.getPort());
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				System.out.println("Command received by " + getName() + ": " + inputLine);
				final Map<String, String> output = processInput(inputLine);
				if (output != null) {
					final String outputLine = argify(output);
					System.out.println(getName() + " responding " + outputLine);
					responseWriter.println(outputLine);
				}
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private Map<String, String> processInput(final @NonNull String commandString) {
		final URLQuery args = new URLQuery(commandString);
		return processCommand(args.getArgument("command"), args);
	}

	abstract protected Map<String, String> processCommand(String command, URLQuery args);

	protected PrintWriter ensureOutputWriter() {
		if (sendSocket == null || sendSocket.isClosed()) {
			// System.out.println(getName()
			// + ".ensureOutputWriter create socket on port " + sendPort);
			try {
				sendSocket = new Socket(host, sendPort);
				outputWriter = new PrintWriter(sendSocket.getOutputStream(), true);
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
		return outputWriter;
	}

	protected void sendCommand(final Map<String, String> args) {
		final String outputLine = argify(args);
		System.out.println(getName() + " sending: " + outputLine);
		ensureOutputWriter().println(outputLine);
	}

}