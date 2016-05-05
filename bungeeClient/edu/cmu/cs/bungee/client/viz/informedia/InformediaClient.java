package edu.cmu.cs.bungee.client.viz.informedia;

import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.ArrayUtils;

import edu.cmu.cs.bungee.client.query.Item;
import edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee;
import edu.cmu.cs.bungee.javaExtensions.URLQuery;
import edu.cmu.cs.bungee.javaExtensions.UtilString;

// If IDVL says "video file not available" need to map network drive \\inf\dfs

/*
 * To sign jar (password is 8 digit insecure)

 cd "C:\Program Files\apache-tomcat-7.0.47\webapps\bungee"
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 bungeeApplet.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 bungeeClient.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 bungeeClientServlet-non-shrunk.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 ConvertFromRaw-non-shrunk.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 Interval-non-shrunk.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 JavaExtensions-non-shrunk.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 NLP-non-shrunk.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 PiccoloExtensions-non-shrunk.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 showInBrowser-non-shrunk.jar BungeeView
 "C:\Program Files\Java\jdk1.8.0_25\bin\jarsigner" -keystore myKeys -storepass 58195819 statistics-non-shrunk.jar BungeeView

 */

// mv bungeeClientSigned.jar bungeeClientSignedOLD.jar
// mv bungeeClient.jar bungeeClientSigned.jar

// To renew certificate
// cd C:\Program Files\apache-tomcat-7.0.47\webapps\bungee
// "C:\Program Files\Java\jdk1.8.0_25\bin\keytool" -keystore myKeys -delete -alias bungeeview
// "C:\Program Files\Java\jdk1.8.0_25\bin\keytool" -keystore myKeys -genkey -alias bungeeview

public class InformediaClient extends InformediaThread {

	private final InformediaServer informediaServer;

	public InformediaClient(final Bungee _art, final int _listenPort, final boolean _isTesting) {
		super("InformediaClient", _art, "localhost", _listenPort, _listenPort + 1);
		if (_isTesting) {
			informediaServer = new InformediaServer(art, "localhost", sendPort);
			// informediaServer.start();
		} else {
			informediaServer = null;
		}
	}

	@Override
	public void run() {
		informediaServer.start();
		super.run();
	}

	@Override
	protected Map<String, String> processCommand(final String command, final URLQuery args) {
		Map<String, String> result = null;
		switch (command) {
		case "import":
			result = mport(args);
			break;
		case "export":
			result = xport(args);
			break;

		default:
			assert false : command;
			break;
		}
		return result;
	}

	/**
	 * @param args
	 *            specification of infIDs to send to IDVL
	 * @return args encoded for IDVL
	 */
	private Map<String, String> mport(final URLQuery args) {
		int startIndex = 0;
		int maxOffsetExclusive = query().getOnCount();
		final int start = args.getIntArgument("startIndex");
		if (start != Integer.MIN_VALUE) {
			startIndex = start;
		}
		final int end = args.getIntArgument("endIndex");
		if (end != Integer.MIN_VALUE) {
			maxOffsetExclusive = end;
		}
		final Item selectedItem = art.getSelectedItem();
		// System.out.println("InformediaClient.import " + startIndex + "-"
		// + maxOffsetExclusive + " " + selectedItem);
		final Map<String, String> result = new Hashtable<>();
		result.put("name", query().getName(null));
		result.put(infIDsArg, getInfIDsString(startIndex, maxOffsetExclusive));
		if (selectedItem != null) {
			result.put(selectedItemTerm, String.valueOf(getInfID(selectedItem)));
		}
		return result;
	}

	/**
	 * @param args
	 *            specification of movies/segments to read from IDVL
	 * @return null (no response needed for export command)
	 */
	private Map<String, String> xport(final URLQuery args) {
		final String nameArg = args.getArgument("name");
		final String name = nameArg.length() > 0 ? nameArg : "Informedia query";

		final int selectedInfID = args.getIntArgument(selectedItemTerm);

		final String infIDs = args.getArgument(infIDsArg, null);

		// System.out.println("InformediaClient.export " + selectedInfID + " "
		// + name + "\n " + infIDs);

		final Runnable doExport = new Runnable() {

			@Override
			public void run() {
				art.clearQuery();
				Item[] items = {};
				if (infIDs != null) {
					final String[] itemIDStrings = infIDs.split(",");
					final int[] itemIDs = new int[itemIDStrings.length];
					for (int i = 0; i < itemIDStrings.length; i++) {
						itemIDs[i] = Integer.parseInt(itemIDStrings[i].trim());
					}
					items = getItems(itemIDs);
					art.addInformediaQuery(name, items);
				}
				if (selectedInfID > 0) {
					final Item selectedItem = getItem(selectedInfID);
					assert selectedItem != null : selectedInfID;
					assert ArrayUtils.contains(items, selectedItem) : selectedItem + " "
							+ UtilString.valueOfDeep(items);
					art.getGrid().clickThumb(selectedItem);
				}
			}
		};

		query().invokeWhenQueryValid(doExport);
		return null;
	}

	// public void stopServer() {
	// if (informediaServer != null) {
	// informediaServer.mport();
	// } else {
	// final Map<String, String> args = new TreeMap<>();
	// args.put("command", "stop");
	// sendCommand(args);
	// }
	// }

	public void testImport() {
		if (informediaServer != null) {
			informediaServer.mport();
		} else {
			final Map<String, String> args = new TreeMap<>();
			args.put("command", "testImport");
			sendCommand(args);
		}
	}

	public void testExport(final String queryName, final int selectedItem, final String itemIDs) {
		final Map<String, String> args = new TreeMap<>();
		args.put("command", "testExport");
		args.put("name", queryName);
		args.put(selectedItemTerm, Integer.toString(selectedItem));
		args.put(infIDsArg, itemIDs);
		if (informediaServer != null) {
			informediaServer.xport(new URLQuery(argify(args)));
		} else {
			sendCommand(args);
		}
	}

	/**
	 * Tell IDVL to create a new tab for BV's current query
	 */
	public void newVideoSet() {
		final Map<String, String> args = new TreeMap<>();
		args.put("command", "newVideoSet");
		args.put("name", query().getName(null));
		args.put(infIDsArg, getInfIDsString(0, query().getOnCount()));
		sendCommand(args);
	}
}