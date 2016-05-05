package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.StringAlign;
import edu.cmu.cs.bungee.javaExtensions.UtilColor;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.UtilString.Justification;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge.EndpointType;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge.LabelLocation;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph;
import edu.cmu.cs.bungee.javaExtensions.graph.Node;
import edu.cmu.cs.bungee.piccoloUtils.gui.Arrow.ArrowPart;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolo.util.PBounds;

/**
 * A Piccolo rendering of a javaExtensions.graph.Graph
 */
public class LazyGraph<NODETYPE extends Comparable<? super NODETYPE>> extends LazyPNode {

	private static final int ARROW_HEAD_AND_TAIL_SIZE = 8;
	/**
	 * Margin added around childen's bounds on all 4 sides.
	 */
	private static final double MARGIN = 8.0;
	@SuppressWarnings("null")
	private static final @NonNull Font font = PText.DEFAULT_FONT;
	private static final @NonNull Paint DEFAULT_TEXT_COLOR = UtilColor.WHITE;
	private static final @NonNull Paint DEFAULT_BG_COLOR = UtilColor.BLACK;

	// copied from BungeeConstants
	private static final @NonNull Color POSITIVE_ASSOCIATION_COLOR = new Color(0x509950);
	private static final @NonNull Color NEGATIVE_ASSOCIATION_COLOR = new Color(0x8e784f);

	private final @NonNull Graph<NODETYPE> abstractGraph;
	private final @NonNull PNode title;

	private final @NonNull Map<Node<NODETYPE>, APText> nodeLabelMap = new HashMap<>();

	/**
	 * There may be empty space on the right or bottom due to rotated bounding
	 * boxes for curved edges.
	 */
	public LazyGraph(final @NonNull Graph<NODETYPE> _abstractGraph, final @NonNull PNode _title) {
		abstractGraph = _abstractGraph;
		title = _title;
		addChild(title);
		if (_abstractGraph.getNumNodes() > 0) {
			_abstractGraph.layout();
		}
		draw();
		setMinWidth(0.0);
	}

	protected @NonNull Font getLabelFont(@SuppressWarnings("unused") final @NonNull Node<NODETYPE> node) {
		return font;
	}

	protected @NonNull Font getLabelFont(@SuppressWarnings("unused") final @NonNull Edge<NODETYPE> edge) {
		return font;
	}

	protected Paint getLabelColor(@SuppressWarnings("unused") final @NonNull Node<NODETYPE> node) {
		return DEFAULT_TEXT_COLOR;
	}

	protected Stroke getStroke(@SuppressWarnings("unused") final Edge<NODETYPE> edge) {
		return LazyPPath.getStrokeInstance(1);
	}

	private static final @NonNull StringAlign nEdgesFormat = new StringAlign(2, Justification.RIGHT);

	private static final @NonNull DecimalFormat edgesFormat = new DecimalFormat("#");

	// public static void printToFile(final Graph<?> graph1, final String
	// status, final Color textColor) {
	// final String label = graph1.getNumEdges() > 0 ? "Influence Diagram" : "No
	// dependencies";
	// final LazyGraph<?> lazyGraph = new LazyGraph<>(graph1, setTitle(label,
	// textColor));
	// lazyGraph.printToFile(status);
	// }

	public void printToFile(final String status) {
		final String baseName = "C:\\Documents and Settings\\mad\\Desktop\\Bungee\\Misc\\InfluenceDiagrams\\"
				+ abstractGraph.label + " " + status + " "
				+ nEdgesFormat.format(abstractGraph.getNumDirectedEdges(), edgesFormat) + " ";
		final File jpgFile = edu.cmu.cs.bungee.javaExtensions.UtilFiles.uniquifyFilename(baseName, ".jpg");
		abstractGraph.layout();
		draw();

		if (getBounds().isEmpty()) {
			setMinWidth(0.0);
		}
		try {
			edu.cmu.cs.bungee.piccoloUtils.gui.PiccoloUtil.savePNodeAsJPEG(this, jpgFile, 72, 85);
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private void draw() {
		removeAllChildren();
		setPaint(DEFAULT_BG_COLOR);
		addAllNodes();
		addAllEdges();
	}

	private void addAllEdges() {
		final Collection<Edge<NODETYPE>> outsideEdges = getOutsideEdges();
		for (final Edge<NODETYPE> edge : abstractGraph.getEdges()) {
			final List<Node<NODETYPE>> edgeNodes = edge.getNodes();
			final Node<NODETYPE> node1 = edgeNodes.get(0);
			final Node<NODETYPE> node2 = edgeNodes.get(1);
			final APText apText1 = nodeLabelMap.get(node1);
			final APText apText2 = nodeLabelMap.get(node2);
			assert !apText1.getFullBounds().isEmpty() : apText1.getText();
			assert !apText2.getFullBounds().isEmpty() : apText2.getText();
			@SuppressWarnings("null")
			final Point2D end1 = edge.getEndingPoint(apText1.getFullBounds());
			@SuppressWarnings("null")
			final Point2D end2 = edge.getEndingPoint(apText2.getFullBounds());
			final String edgeLabel = edge.getLabel(LabelLocation.CENTER_LABEL);
			final Paint arrowColor = edgeLabel == null ? DEFAULT_TEXT_COLOR
					: (edgeLabel.startsWith("-") ? NEGATIVE_ASSOCIATION_COLOR : POSITIVE_ASSOCIATION_COLOR);

			final Arrow arrow = new Arrow(arrowColor, ARROW_HEAD_AND_TAIL_SIZE, 1, 1);
			arrow.addLabels(edge.getLabels(), getLabelFont(edge));
			@SuppressWarnings("null")
			final EndpointType type1 = edge.getEndpoint(node1).getType();
			arrow.setVisible(ArrowPart.LEFT_TAIL, type1 == EndpointType.CIRCLE);
			arrow.setVisible(ArrowPart.LEFT_HEAD, type1 == EndpointType.ARROW);
			@SuppressWarnings("null")
			final EndpointType type2 = edge.getEndpoint(node2).getType();
			arrow.setVisible(ArrowPart.RIGHT_TAIL, type2 == EndpointType.CIRCLE);
			arrow.setVisible(ArrowPart.RIGHT_HEAD, type2 == EndpointType.ARROW);
			if (outsideEdges.contains(edge)) {
				arrow.arcH = (float) Point2D.distance(end1.getX(), end1.getY(), end2.getX(), end2.getY());
			}
			arrow.setEndpoints(end1.getX(), end1.getY(), end2.getX(), end2.getY());
			addChild(arrow);
		}
	}

	/**
	 * @return edges that arc outside the layout circle.
	 */
	private Collection<Edge<NODETYPE>> getOutsideEdges() {
		final Collection<Edge<NODETYPE>> result = new LinkedList<>();
		final NavigableSet<Edge<NODETYPE>> insideEdges = new TreeSet<>(abstractGraph.getEdges());
		while (abstractGraph.nEdgeCrossings(insideEdges) > 0 && result.size() < 2) {
			int maxCrossings = 0;
			Edge<NODETYPE> bestEdge = null;
			for (final Edge<NODETYPE> edge : insideEdges) {
				final int nCrossings = abstractGraph.nCrossings(edge, insideEdges);
				if (nCrossings > maxCrossings) {
					maxCrossings = nCrossings;
					bestEdge = edge;
				}
			}
			assert bestEdge != null;
			result.add(bestEdge);
			insideEdges.remove(bestEdge);
		}
		return result;
	}

	private void addAllNodes() {
		nodeLabelMap.clear();
		for (final Node<NODETYPE> node : abstractGraph.getNodes()) {
			assert node != null;
			final Font labelFont = getLabelFont(node);
			final APText nodeLabel = APText.oneLineLabel(labelFont);
			nodeLabel.setTextPaint(getLabelColor(node));
			nodeLabel.maybeSetText(PiccoloUtil.truncateText(node.getLabel(), Graph.LABEL_W, labelFont));
			nodeLabel.setOffset(Math.rint(node.getCenterX() - nodeLabel.getWidth() / 2.0),
					Math.rint(node.getCenterY() - nodeLabel.getHeight() / 2.0));
			addChild(nodeLabel);
			nodeLabelMap.put(node, nodeLabel);
		}
	}

	// Called only by PopupSummary.colorInfluenceDiagram
	public Collection<NODETYPE> getNodeObjects() {
		final Set<Node<NODETYPE>> keySet = nodeLabelMap.keySet();
		final Collection<NODETYPE> result = new ArrayList<>(keySet.size());
		for (final Node<NODETYPE> node : keySet) {
			result.add(node.object);
		}
		return result;
	}

	// Compute title width; layout title to get its height; set our bounds
	// accordingly.
	//
	// If newWidth > fullBounds.width, center graph horizontally using
	// excessWidth.
	public boolean setMinWidth(final double newWidth) {
		// Figure out width without title; then set title width accordingly.
		title.removeFromParent();
		setBounds(0.0, 0.0, 1.0, 1.0);
		final PBounds fullBounds = getFullBounds();
		final double excessWidth = Math.max(2.0 * MARGIN, (newWidth - fullBounds.width));
		final double x = Math.rint(fullBounds.x - excessWidth / 2.0);
		final double w = Math.rint(fullBounds.width + excessWidth);
		title.setWidth(w - 2.0 * MARGIN);

		addChild(title);
		// force recomputation of title's fullBounds
		final double titleH = Math.rint(title.getFullBounds().getHeight());
		title.setOffset(x + MARGIN, -titleH - MARGIN);

		// adjust y & h for titleH
		final double y = -2.0 * MARGIN - titleH;
		final double h = Math.rint(getFullBounds().height + 2.0 * MARGIN);
		return setBounds(x, y, w, h);
	}

	@Override
	public boolean setWidth(@SuppressWarnings("unused") final double newWidth) {
		assert false : "Use setMinWidth";
		return false;
	}

	public int getNumNodes() {
		return nodeLabelMap.size();
	}

	public int getNumEdges() {
		return abstractGraph.getEdges().size();
	}

	// TODO Remove unused code found by UCDetector
	// public Collection<Edge<NODETYPE>> nonPrimaryEdges() {
	// final List<Node<NODETYPE>> childrenReference = getChildrenReference();
	// assert childrenReference != null;
	// return abstractGraph.nonPrimaryEdges(childrenReference);
	// }

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append(getNumNodes()).append(" Nodes: ").append(getNodeObjects());
		buf.append(getNumEdges()).append(" NonBiasEdges: ").append(abstractGraph.getEdges());
		return UtilString.toString(this, buf);
	}

}
