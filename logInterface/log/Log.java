package log;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.URLQuery;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolox.PFrame;

final class Log extends PFrame {

	protected static final long serialVersionUID = 6232063290939677736L;

	private JDBCSample jdbc;

	private static final BasicService BASIC_JNLP_SERVICE = maybeGetBasicService();

	private Chart chart;

	@SuppressWarnings("unused")
	public static void main(final String[] args) {
		// System.out.println("Starting log.Log");
		new Log(args);
	}

	private Log(final String[] args) {
		// gross hack to pass arguments to initialize
		super(args.length > 0 ? args[0] : null, false, null);
	}

	@Override
	public void initialize() {
		getCanvas().setPanEventHandler(null);
		getCanvas().setZoomEventHandler(null);
		final String argString = getTitle();
		// System.out.println("argString=" + argString);
		assert argString != null;
		final URLQuery argURLQuery = new URLQuery(argString);
		setTitle("Bungee View Log");
		final String dbs = argURLQuery.getArgument("dbs");
		assert UtilString.isNonEmptyString(dbs) : "Empty db name list";
		final String[] dbNames = UtilString.splitComma(dbs);
		final String host = argURLQuery.getArgument("host");
		final String user = argURLQuery.getArgument("user");
		final String pass = argURLQuery.getArgument("pass");
		try {
			jdbc = JDBCSample.getMySqlJDBCSample(host, Util.nonNull(dbNames[0]), user, pass);
			read(dbNames);
			Histograms.printHistograms(jdbc, dbNames);
			jdbc.close();
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Rectangle getDefaultFrameBounds() {
		return new Rectangle(0, 0, 1000, 740);
	}

	private void setSize() {
		final double w = getWidth();
		final double h = getHeight();
		final PLayer layer = getCanvas().getLayer();
		chart.setOffset(140, 140);
		chart.setBounds(0, 0, w - 280, h - 280);
		// chart.setBounds(0, 0, 200, 200);
		layer.addChild(chart);
		chart.draw();
	}

	private void read(final String[] dbNames) throws SQLException {
		chart = new Chart();
		for (final String dbName : dbNames) {
			final String table = dbName + ".user_actions";
			try (ResultSet rs = jdbc.sqlQuery("SELECT session, COUNT(*), MIN(timestamp), MAX(timestamp), client FROM "
					+ table + " GROUP BY session");) {
				while (rs.next()) {
					final int sessionID = rs.getInt(1);
					final int nOps = rs.getInt(2);
					final Date minDate = rs.getTimestamp(3);
					final Date maxDate = rs.getTimestamp(4);
					final String IP = rs.getString(5);
					final Session session = new Session(sessionID, dbName, nOps, minDate, maxDate, IP);
					chart.addSession(session);
				}
			}
		}
		setSize();
	}

	private static BasicService maybeGetBasicService() {
		BasicService s = null;
		try {
			s = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
		} catch (final UnavailableServiceException e) {
			System.err.println("jnlp.BasicService is not available");
		}
		return s;
	}

	static void showDocument(final String URLstring) {
		System.out.println("showDocument " + URLstring);
		if (BASIC_JNLP_SERVICE != null) {
			try {
				BASIC_JNLP_SERVICE.showDocument(new URL(URLstring));
			} catch (final MalformedURLException e) {
				e.printStackTrace();
			}
		} else {
			try {
				Runtime.getRuntime().exec("C:\\Program Files\\Mozilla Firefox\\firefox.exe \"" + URLstring + "\"");
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}
}
