package log;

import java.awt.Color;
import java.text.DateFormat;
import java.util.Date;

import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.piccoloUtils.gui.APText;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPNode;
import edu.cmu.cs.bungee.piccoloUtils.gui.LazyPPath;
import edu.cmu.cs.bungee.piccoloUtils.gui.MyInputEventHandler;

class Session extends LazyPPath {

	// protected static final long serialVersionUID = -2589191367326808627L;

	final int ID;
	final int nOps;
	final Date start;
	final Date end;
	final String db;
	final String IPaddress;
	private static final int MAX_EDGE = 30;
	private static final Color NORMAL_COLOR = Color.cyan;
	private static final Color HIGHLIGHT_COLOR = Color.black;

	Session(final int sessionID, final String dbName, final int opCount, final Date minDate, final Date maxDate,
			final String IP) {
		ID = sessionID;
		nOps = opCount;
		start = minDate;
		end = maxDate;
		db = dbName;
		IPaddress = IP;

		setStroke(LazyPPath.getStrokeInstance(1));
		setStrokePaint(NORMAL_COLOR);
	}

	void setSize(final int maxSize) {
		final double edgeRatio = Math.sqrt(maxSize) / MAX_EDGE;
		final float edge = (float) (Math.sqrt(nOps) / edgeRatio);
		setBounds(-edge / 2, -edge / 2, edge, edge);

		final float[] Xs = { -edge / 2, edge / 2, edge / 2, -edge / 2, -edge / 2 };
		final float[] Ys = { -edge / 2, -edge / 2, edge / 2, edge / 2, -edge / 2 };
		setPathToPolyline(Xs, Ys);

		final LazyPNode child = new LazyPNode();
		child.setPaint(Color.white);
		child.setTransparency(0.001f);
		child.setBounds(getBounds());
		addChild(child);
		child.addInputEventListener(new SessionEventHandler());
	}

	String elapsedTime() {
		final long elapsedS = (end.getTime() - start.getTime()) / 1000;
		final long elapsedM = elapsedS / 60;
		final long elapsedS1 = elapsedS % 60;
		return elapsedM + ":" + elapsedS1;
	}

	@Override
	public String toString() {
		return UtilString.toString(this, start + " elapsed time=" + elapsedTime() + " nOps=" + nOps + " " + db);
	}

	private class SessionEventHandler extends MyInputEventHandler<Session> {

		private final APText popup = new APText();

		SessionEventHandler() {
			super(Session.class);
			popup.setOffset(0, 30);
		}

		@Override
		public boolean enter(@SuppressWarnings("unused") final Session node) {
			popup.maybeSetText(DateFormat.getDateTimeInstance().format(start) + "\nelapsed time: " + elapsedTime()
					+ "\nnOps: " + nOps + "\nIP address: " + IPaddress);
			addChild(popup);
			((Chart) getParent()).highlight(IPaddress);
			return true;
		}

		@Override
		public boolean exit(@SuppressWarnings("unused") final Session node) {
			removeChild(popup);
			((Chart) getParent()).highlight(null);
			return true;
		}

		@Override
		public boolean click(@SuppressWarnings("unused") final Session node) {
			final String URLstring = "http://localhost/bungee/bungee.jsp?db=" + db + "&session=" + ID;
			Chart.showDocument(URLstring);
			return true;
		}
	}

	void highlight(final String address) {
		final Color color = IPaddress.equals(address) ? HIGHLIGHT_COLOR : NORMAL_COLOR;
		setStrokePaint(color);
	}

}
