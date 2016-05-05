package edu.cmu.cs.bungee.client.viz.informedia;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

import javax.jnlp.BasicService;

import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.javaExtensions.URLQuery;
import edu.cmu.cs.bungee.javaExtensions.Util;

/**
 * Stub for testing InformediaClient
 */
class InformediaServer extends InformediaThread {

	public static void main(final String[] args) {
		// System.out.println("InformediaServer.main "
		// + UtilString.valueOfDeep(args));
		try {
			// Windows shell needs & escaped as ^&
			final URL url = new URL(
					"http://cityscape.inf.cs.cmu.edu/bungeelamp/applet8.html?db=elamp^&informediaPort=15213");
			final String server = url.getHost();
			new InformediaServer(null, server, 15214).start();
			showURL(url);
		} catch (final MalformedURLException e) {
			e.printStackTrace();
		}
	}

	InformediaServer(final Bungee _art, final String server,
			final int _listenPort) {
		super("Informedia Server", _art, server, _listenPort, _listenPort - 1);
	}

	private static void showURL(final URL url) {
		final BasicService basicJNLPservice = Util.maybeGetBasicService();
		if (basicJNLPservice != null) {
			basicJNLPservice.showDocument(url);
		} else {
			try {
				Desktop.getDesktop().browse(new URI(url.toString()));
			} catch (IOException | URISyntaxException e) {
				e.printStackTrace();
			}
			// art.showDocument(url.toString());
		}
	}

	@Override
	protected Map<String, String> processCommand(final String command,
			final URLQuery args) {
		// System.out.println("InformediaServer.processInput: " + command + " "
		// + args);
		switch (command) {
		case "testImport":
			mport();
			break;
		case "testExport":
			xport(args);
			break;
		case "stop":
			interrupt();
			break;
		case "playMovie":
		case "playSegment":
		case "newVideoSet":
			break;

		default:
			System.err
					.println("InformediaServer.processInput: Unknown command: "
							+ command + " " + args);
			break;
		}
		return null;
	}

	public void mport() {
		// System.out.println("InformediaServer.mport ");

		// Use TreeMap so "command" comes first (in alphabetical order)
		final Map<String, String> args = new TreeMap<>();
		args.put("command", "import");
		sendCommand(args);
	}

	public void xport(final URLQuery args2) {
		// System.out.println("InformediaServer.xport " + args2);

		final String selectedItem = args2.getArgument(selectedItemTerm);
		final String itemIDs = args2.getArgument(infIDsArg);

		final Map<String, String> args = new TreeMap<>();
		args.put("command", "export");
		args.put("name", "Informedia query with " + itemIDs.split("%2C").length
				+ " " + infIDsArg);
		args.put(selectedItemTerm, selectedItem);
		args.put(infIDsArg, itemIDs);
		sendCommand(args);
	}

	@Override
	protected PrintWriter ensureOutputWriter() {
		final PrintWriter result = super.ensureOutputWriter();
		new InformediaServerResponseListener().start();
		return result;
	}

	private class InformediaServerResponseListener extends Thread {

		public InformediaServerResponseListener() {
			super("InformediaServerResponseListener");
		}

		@Override
		public void run() {
			try (final BufferedReader in = new BufferedReader(
					new InputStreamReader(sendSocket.getInputStream()));) {

				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					System.out
							.println("InformediaServerResponseListener.run Response from IDVL Client: "
									+ inputLine);
				}
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}
}