package log;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;

class Chart extends LazyPNode {

	// private static final long serialVersionUID = -7793419482063330906L;

	// private final Log log;

	private final Set<Session> sessions = new HashSet<>();
	private final Set<String> dbNames = new HashSet<>();
	private int maxOps = 0;
	private Date minDate = new Timestamp(Long.MAX_VALUE);
	private Date maxDate = new Timestamp(0);

	private Xaxis xAxis;
	private Yaxis yAxis;

	Chart() {
		// log = _log;
	}

	void addSession(final Session session) {
		sessions.add(session);
		dbNames.add(session.db);
		maxOps = Math.max(maxOps, session.nOps);
		if (session.start.compareTo(minDate) < 0) {
			minDate = session.start;
		}
		if (session.end.compareTo(maxDate) > 0) {
			maxDate = session.end;
		}
	}

	void draw() {
		// System.out.println("draw " + getBounds());
		// setPaint(Color.yellow);
		xAxis = new Xaxis(minDate, maxDate);
		yAxis = new Yaxis(dbNames);
		redraw();
	}

	void redraw() {
		removeAllChildren();
		addChild(xAxis);
		xAxis.setOffset(0, getHeight());
		xAxis.draw(getWidth());
		yAxis.setOffset(0, 0);
		addChild(yAxis);
		yAxis.draw(100, getHeight());
		for (final Session session : sessions) {
			session.setOffset(xAxis.encode(session.start),
					yAxis.encode(session.db));
			session.setSize(maxOps);
			addChild(session);
			// System.out.println(session + " " + session.getOffset());
		}
	}

	static void showDocument(final String URLstring) {
		Log.showDocument(URLstring);
	}

	public void highlight(final String IPaddress) {
		for (final Session session : sessions) {
			session.highlight(IPaddress);
		}
	}
}
