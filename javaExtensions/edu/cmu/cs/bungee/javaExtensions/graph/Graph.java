package edu.cmu.cs.bungee.javaExtensions.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge.LabelLocation;
import edu.cmu.cs.bungee.javaExtensions.permutations.PermutationIterator;

/**
 * Node/Edge data structure
 *
 * A Node has a type; x,y location; and label.
 *
 * An Edge has 2 Endpoints and 3 labels.
 */
public class Graph<NODETYPE extends Comparable<? super NODETYPE>> {

	public interface GraphWeigher<NODETYPE extends Comparable<? super NODETYPE>> {

		double weight(Graph<NODETYPE> graph, Node<NODETYPE> cause, Node<NODETYPE> caused);
	}

	private static final int GRAPH_EDGE_LENGTH = 250;
	public static final float LABEL_W = GRAPH_EDGE_LENGTH * 0.7f;

	public @NonNull String label = "Graph"; // NO_UCD (use final)

	@Nullable
	List<Node<NODETYPE>> bestPerm = null;
	int bestEdgeCrossings = Integer.MAX_VALUE;

	private final Map<Set<Node<NODETYPE>>, Edge<NODETYPE>> edgesLookupTable;
	private final @NonNull NavigableSet<Edge<NODETYPE>> edges;
	private final @NonNull Set<Edge<NODETYPE>> unmodifiableEdges;
	private final @NonNull Map<Node<NODETYPE>, Integer> nodesTable;
	private final @NonNull Map<NODETYPE, Node<NODETYPE>> namesToNodesTable;
	// private final GraphWeigher<NODETYPE> weigher;

	private int nodeIndex;
	// private NavigableSet<Edge<NODETYPE>> reverseEdges;

	public Graph() {
		// weigher = _weigher;
		edgesLookupTable = new HashMap<>();
		edges = new TreeSet<>();
		unmodifiableEdges = Util.nonNull(Collections.unmodifiableSet(edges));
		nodesTable = new HashMap<>();
		namesToNodesTable = new HashMap<>();
	}

	public int nEdgeCrossings(final @NonNull NavigableSet<Edge<NODETYPE>> insideEdges) {
		return nEdgeCrossings(bestPerm, insideEdges);
	}

	private boolean checkForEdgesToNowhere() {
		for (final Edge<NODETYPE> edge : getEdges()) {

			// assert edge.getNumDirections() > 0;
			for (final Node<NODETYPE> node : edge.getNodes()) {
				assert node != null;
				assert hasNode(node);
			}
		}
		return true;
	}

	private int nodeIndex(final @NonNull Node<NODETYPE> node) {
		final Integer index = nodesTable.get(node);
		return index == null ? -1 : index.intValue();
	}

	private boolean hasNode(final @NonNull Node<NODETYPE> node) {
		return nodeIndex(node) > -1;
	}

	private Node<NODETYPE> getNode(final @NonNull String nodeLabel) {
		return namesToNodesTable.get(nodeLabel);
	}

	public Node<NODETYPE> addNode(final @NonNull NODETYPE object, final @NonNull String name) {
		assert getNode(name) == null;
		final Node<NODETYPE> node = new Node<>(object, name);
		ensureNode(node);
		return node;
	}

	private void ensureNode(final @NonNull Node<NODETYPE> node) {
		if (!hasNode(node)) {
			nodesTable.put(node, Integer.valueOf(nodeIndex++));
			assert node.object != null;
			namesToNodesTable.put(node.object, node);
			assert checkForEdgesToNowhere();
		}
	}

	private void removeNodes(final @NonNull Set<Node<NODETYPE>> nodesToRemove) {
		for (final Node<NODETYPE> node : nodesToRemove) {
			assert node != null;
			assert hasNode(node);
			assert node != null;
			for (final Edge<NODETYPE> edge : getEdges(node)) {
				assert edge != null;
				removeEdge(edge);
			}
			nodesTable.remove(node);
			assert node.object != null;
			namesToNodesTable.remove(node.object);
			assert checkForEdgesToNowhere();
		}
	}

	public @Nullable Edge<NODETYPE> lookupEdge(final @NonNull Node<NODETYPE> node1,
			final @NonNull Node<NODETYPE> node2) {
		final Set<Node<NODETYPE>> nodes = new HashSet<>(2);
		nodes.add(node1);
		nodes.add(node2);
		return lookupEdge(nodes);
	}

	private @Nullable Edge<NODETYPE> lookupEdge(final @NonNull Set<Node<NODETYPE>> nodes) {
		final Edge<NODETYPE> edge = edgesLookupTable.get(nodes);
		assert edge != null || assertEdgeArgsValid(nodes);
		return edge;
	}

	private boolean assertEdgeArgsValid(final @NonNull Set<Node<NODETYPE>> nodes) {
		assert nodes.size() == 2;
		for (final Node<NODETYPE> node : nodes) {
			assert node != null;
			assert hasNode(node) : node + " " + this;
		}
		return true;
	}

	// Only called by GraphicalModel.addEdge
	public @NonNull Edge<NODETYPE> ensureEdge(final @NonNull Node<NODETYPE> node1,
			final @NonNull Node<NODETYPE> node2) {
		Edge<NODETYPE> edge = lookupEdge(node1, node2);
		if (edge == null) {
			edge = addEdge(node1, node2);
		}
		assert edge != null;
		return edge;
	}

	// Only called by ensureEdge
	private @NonNull Edge<NODETYPE> addEdge(final @NonNull Node<NODETYPE> node1, final @NonNull Node<NODETYPE> node2) {
		assert lookupEdge(node1, node2) == null;
		final Edge<NODETYPE> edge = new Edge<>(node1, node2);
		final Set<Node<NODETYPE>> nodes = new HashSet<>(2);
		nodes.add(node1);
		nodes.add(node2);
		edgesLookupTable.put(nodes, edge);
		edges.add(edge);
		// reverseEdges = null;
		assert checkForEdgesToNowhere();
		return edge;
	}

	private void removeEdge(final @NonNull Edge<NODETYPE> edge) {
		assert edges.contains(edge);
		final Set<Node<NODETYPE>> nodes = new HashSet<>(edge.getNodes());
		edgesLookupTable.remove(nodes);
		edges.remove(edge);
		// reverseEdges = null;
		assert checkForEdgesToNowhere();
	}

	public int getNumDirectedEdges() {
		int result = 0;
		for (final Edge<NODETYPE> edge : getEdges()) {
			result += edge.getNumDirectedEdges();
		}
		return result;
	}

	public int getNumEdges() {
		return edges.size();
	}

	public @NonNull Set<Edge<NODETYPE>> getEdges() {
		return unmodifiableEdges;
	}

	private @NonNull Set<Edge<NODETYPE>> getEdges(final @NonNull Node<NODETYPE> node) {
		assert hasNode(node);
		final Set<Edge<NODETYPE>> result = new HashSet<>();
		for (final Edge<NODETYPE> edge : getEdges()) {
			if (edge.hasNode(node)) {
				result.add(edge);
			}
		}
		return result;
	}

	private @NonNull Iterator<Edge<NODETYPE>> getNodeEdgeIterator(final @NonNull Node<NODETYPE> node) {
		return new NodeEdgeIterator(node);
	}

	private class NodeEdgeIterator implements Iterator<Edge<NODETYPE>> {
		private final @NonNull Node<NODETYPE> node;
		private final @NonNull Iterator<Edge<NODETYPE>> edgeIterator;

		private @Nullable Edge<NODETYPE> next;

		NodeEdgeIterator(final @NonNull Node<NODETYPE> _node) {
			node = _node;
			edgeIterator = Util.nonNull(getEdges().iterator());
			peek();
		}

		private void peek() {
			next = null;
			while (edgeIterator.hasNext() && next == null) {
				final Edge<NODETYPE> edge = edgeIterator.next();
				if (edge.hasNode(node)) {
					next = edge;
				}
			}
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public @NonNull Edge<NODETYPE> next() {
			final Edge<NODETYPE> result = next;
			if (result == null) {
				throw new NoSuchElementException();
			}
			peek();
			return result;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * @param leftNode
	 *            index of proposed left node
	 * @param rightNode
	 *            index of proposed right node
	 * @return whether this left/right layout is better than the reverse
	 */
	private boolean moreCaused(final @NonNull Node<NODETYPE> leftNode, final @NonNull Node<NODETYPE> rightNode) {
		final int causeScore = netCauses(leftNode);
		final int causedScore = netCauses(rightNode);
		final boolean result = causeScore == causedScore ? nodeIndex(leftNode) < nodeIndex(rightNode)
				: causeScore < causedScore;
		return result;
	}

	private int netCauses(final @NonNull Node<NODETYPE> node) {
		int score = 0;
		for (final Iterator<Edge<NODETYPE>> it = getNodeEdgeIterator(node); it.hasNext();) {
			final Edge<NODETYPE> edge = it.next();
			if (edge.canCause(node)) {
				score++;
			}
			final Node<NODETYPE> distal = edge.getDistalNode(node);
			if (edge.canCause(distal)) {
				score--;
			}
		}
		return score;
	}

	private int nMoreCaused(final @NonNull List<Node<NODETYPE>> nodes) {
		int result = 0;
		for (int causeIndex = 0; causeIndex < nodes.size(); causeIndex++) {
			final Node<NODETYPE> cause = nodes.get(causeIndex);
			assert cause != null;
			for (int causedIndex = causeIndex + 1; causedIndex < nodes.size(); causedIndex++) {
				final Node<NODETYPE> caused = nodes.get(causedIndex);
				assert caused != null;
				if (moreCaused(cause, caused)) {
					result++;
				}
			}
		}
		return result;
	}

	/**
	 * Place node centers in a circle, ordered to minimize edge crossings.
	 */
	public void layout() {
		final int nNodes = getNumNodes();
		assert nNodes > 0;
		final List<Node<NODETYPE>> nodes = new ArrayList<>(getNodes());

		// use centerY as scratchpad for "moreCaused"
		for (final Node<NODETYPE> node : nodes) {
			node.setCenterY(netCauses(node) * nNodes + nodeIndex(node));
		}

		Collections.sort(nodes);
		for (final PermutationIterator<Node<NODETYPE>> it = new PermutationIterator<>(nodes); it.hasNext();) {
			final List<Node<NODETYPE>> perm = it.next();
			if (perm.get(0) == nodes.get(0)
					&& (nNodes < 3 || perm.get(1).getCenterY() < perm.get(nNodes - 1).getCenterY())) {

				// Canonicalize by starting at 0 and preferring arrows that
				// point to the right.

				final int nEdgeCrossings = nEdgeCrossings2(perm, edges);
				if (nEdgeCrossings < bestEdgeCrossings || nEdgeCrossings == bestEdgeCrossings
						&& nMoreCaused(perm) > nMoreCaused(Util.nonNull(bestPerm))) {
					bestEdgeCrossings = nEdgeCrossings;
					bestPerm = new ArrayList<>(perm);
				}
			}
		}
		assert bestPerm != null;
		arrangeInCircle(0, GRAPH_EDGE_LENGTH, GRAPH_EDGE_LENGTH, bestPerm);
	}

	public int getNumNodes() {
		return nodesTable.size();
	}

	/**
	 * Arranges the nodes in the graph clockwise in a circle, starting at 12
	 * o'clock.
	 *
	 *
	 * @param centerX
	 * @param centerY
	 *            x/y coordinates of the center of the circle.
	 * @param radius
	 *            The radius of the circle in pixels; a good default is 150.
	 */
	private void arrangeInCircle(final int centerX, final int centerY, final int radius,
			final @NonNull List<Node<NODETYPE>> perm) {

		final double rad = 2.0 * Math.PI / getNumNodes();
		double phi = .75 * 2.0 * Math.PI; // start from 12 o'clock.

		for (final Node<NODETYPE> node : perm) {
			final int x = centerX + (int) (radius * Math.cos(phi));
			final int y = centerY + (int) (radius * Math.sin(phi));

			node.setCenterX(x);
			node.setCenterY(y);

			phi += rad;
		}
	}

	public Set<Node<NODETYPE>> getNodes() {
		return Collections.unmodifiableSet(nodesTable.keySet());
	}

	private int nEdgeCrossings(final List<Node<NODETYPE>> perm, final NavigableSet<Edge<NODETYPE>> insideEdges) {
		int nCrosses = 0;
		for (final Edge<NODETYPE> edge : insideEdges) {
			nCrosses += nTailsetCrossings(edge, perm, insideEdges);
		}
		return nCrosses;
	}

	private int nEdgeCrossings2(final List<Node<NODETYPE>> perm, final NavigableSet<Edge<NODETYPE>> _edges) {
		int nCrosses = 0;
		for (final Edge<NODETYPE> edge : _edges) {
			nCrosses += nTailsetCrossings2(edge, perm, _edges);
		}
		return nCrosses;
	}

	public int nCrossings(final Edge<NODETYPE> edge1, final NavigableSet<Edge<NODETYPE>> edges0) {
		int nCrosses = nTailsetCrossings(edge1, bestPerm, edges0);
		nCrosses += nTailsetCrossings(edge1, bestPerm, edges0.descendingSet());
		return nCrosses;
	}

	/**
	 * @param perm
	 * @param insideEdges
	 * @return number of edges in edges.tailSet(edge1, false) that edge1 crosses
	 */
	private int nTailsetCrossings(final Edge<NODETYPE> edge1, final List<Node<NODETYPE>> perm,
			final NavigableSet<Edge<NODETYPE>> insideEdges) {
		int nCrosses = 0;
		final int edge1node1 = perm.indexOf(edge1.getRightNode());
		final int edge1node2 = perm.indexOf(edge1.getLeftNode());
		final double edge1min = Math.min(edge1node1, edge1node2);
		final double edge1max = Math.max(edge1node1, edge1node2);
		for (final Edge<NODETYPE> edge2 : insideEdges.tailSet(edge1, false)) {
			final int between1 = edgeCrossingsInternal(perm.indexOf(edge2.getRightNode()), edge1min, edge1max);
			if (between1 != 0) {
				final int between2 = edgeCrossingsInternal(perm.indexOf(edge2.getLeftNode()), edge1min, edge1max);
				if (between1 * between2 < 0) {
					nCrosses++;
				}
			}
		}
		return nCrosses;
	}

	/**
	 * @param perm
	 * @param _edges
	 * @return number of edges in edges.tailSet(edge1, false) that edge1 crosses
	 */
	private int nTailsetCrossings2(final Edge<NODETYPE> edge1, final List<Node<NODETYPE>> perm,
			final NavigableSet<Edge<NODETYPE>> _edges) {
		int nCrosses = 0;
		final int edge1node1 = perm.indexOf(edge1.getRightNode());
		final int edge1node2 = perm.indexOf(edge1.getLeftNode());
		final double edge1min = Math.min(edge1node1, edge1node2);
		final double edge1max = Math.max(edge1node1, edge1node2);
		for (final Edge<NODETYPE> edge2 : _edges.tailSet(edge1, false)) {
			final int between1 = edgeCrossingsInternal(perm.indexOf(edge2.getRightNode()), edge1min, edge1max);
			if (between1 != 0) {
				final int between2 = edgeCrossingsInternal(perm.indexOf(edge2.getLeftNode()), edge1min, edge1max);
				if (between1 * between2 < 0) {
					nCrosses++;
				}
			}
		}
		return nCrosses;
	}

	// 1 if min<node1<max; 0 if node1==min || node1==max; -1 otherwise
	private static int edgeCrossingsInternal(final double node1, final double min, final double max) {
		return node1 > min ? (node1 > max ? -1 : node1 == max ? 0 : 1) : (node1 == min ? 0 : -1);
	}

	/**
	 * Makes this graph undirected, and removes nodes that do not lie on a path
	 * between two core nodes.
	 */
	public void removeUnconnectedNodes() {
		final Set<Node<NODETYPE>> nodesToRemove = new HashSet<>();
		for (final Node<NODETYPE> node : getNodes()) {
			// System.out.println("Graph.removeUnconnectedNodes "+node+"
			// nEdges="+getEdges(node).size());
			assert node != null;
			if (getEdges(node).size() == 0) {
				nodesToRemove.add(node);
			}
		}
		removeNodes(nodesToRemove);
	}

	/**
	 * Remove edges whose center label is "0"
	 *
	 * @return the number of edges removed.
	 */
	public int removeZeroWeightEdges() {
		int result = 0;
		for (final Edge<NODETYPE> edge : (new ArrayList<>(getEdges()))) {
			if ("0".equals(edge.getLabel(LabelLocation.CENTER_LABEL))) {
				removeEdge(edge);
				result++;
			}
		}
		return result;
	}

	/**
	 * Makes this graph undirected, and removes nodes that do not lie on a path
	 * between two core nodes.
	 *
	 * @return the number of edges removed.
	 */
	public int removeNonprimaryEdges(final @NonNull Collection<NODETYPE> primaryNodeObjects) {
		final List<Node<NODETYPE>> primaryNodes = new LinkedList<>();
		for (final Node<NODETYPE> node : getNodes()) {
			if (primaryNodeObjects.contains(node.object)) {
				primaryNodes.add(node);
			}
		}
		return removeNonprimaryEdges(primaryNodes);
	}

	/**
	 * Makes this graph undirected, and removes nodes that do not lie on a path
	 * between two primaryNodes.
	 *
	 * @return the number of edges removed.
	 */
	private int removeNonprimaryEdges(final @NonNull List<Node<NODETYPE>> primaryNodes) {
		final int result = retainEdges(primaryEdges(primaryNodes));
		final Set<Node<NODETYPE>> nodesToRemove = new HashSet<>();
		for (final Node<NODETYPE> node : getNodes()) {
			assert node != null;
			if (!primaryNodes.contains(node) && getEdges(node).size() == 0) {
				nodesToRemove.add(node);
			}
		}
		removeNodes(nodesToRemove);
		return result;
	}

	private @NonNull Collection<Edge<NODETYPE>> primaryEdges(final @NonNull List<Node<NODETYPE>> primaryNodes) {
		final Collection<Edge<NODETYPE>> primaryEdges = new HashSet<>();
		for (final Node<NODETYPE> primaryNode1 : primaryNodes) {
			assert primaryNode1 != null;
			for (final Node<NODETYPE> primaryNode2 : primaryNodes.subList(primaryNodes.indexOf(primaryNode1) + 1,
					primaryNodes.size())) {
				assert primaryNode2 != null;
				final Edge<NODETYPE> edge = lookupEdge(primaryNode1, primaryNode2);
				if (edge != null) {
					primaryEdges.add(edge);
				}
			}
		}
		return primaryEdges;
	}

	public @NonNull Collection<Edge<NODETYPE>> nonPrimaryEdges(final @NonNull List<Node<NODETYPE>> primaryNodes) {
		final Collection<Edge<NODETYPE>> nonPrimaryEdges = new HashSet<>();
		for (final Node<NODETYPE> primaryNode1 : nonPrimaryNodes(primaryNodes)) {
			assert primaryNode1 != null;
			for (final Node<NODETYPE> node2 : getNodes()) {
				if (node2 != primaryNode1) {
					assert node2 != null;
					final Edge<NODETYPE> edge = lookupEdge(primaryNode1, node2);
					if (edge != null) {
						nonPrimaryEdges.add(edge);
					}
				}
			}
		}
		return nonPrimaryEdges;
	}

	private @NonNull Collection<Node<NODETYPE>> nonPrimaryNodes(final @NonNull List<Node<NODETYPE>> primaryNodes) {
		final Collection<Node<NODETYPE>> nonPrimaryNodes = new HashSet<>(getNodes());
		nonPrimaryNodes.removeAll(primaryNodes);
		return nonPrimaryNodes;
	}

	/**
	 * @return remove edges not in edgesToRemove; return the number of edges
	 *         removed.
	 */
	private int retainEdges(final @NonNull Collection<Edge<NODETYPE>> edgesToRemove) {
		int result = 0;
		for (final Edge<NODETYPE> edge : (new ArrayList<>(getEdges()))) {
			if (!edgesToRemove.contains(edge)) {
				// System.out.println("Removing satellite node " + node);
				assert edge != null;
				removeEdge(edge);
				result++;
			}
		}
		return result;
	}

	// /**
	// * downstream means in the direction of the arrow
	// *
	// * @return all edges in this graph that lie on a colliderless path between
	// * two primary nodes
	// */
	// private Collection<Edge<NODETYPE>> pathEdges(final Node<NODETYPE>
	// primary1, final Node<NODETYPE> primary2,
	// final double threshold) {
	// final Collection<Edge<NODETYPE>> pathEdges = new HashSet<>();
	// final Collection<Node<NODETYPE>> nodeStack = new HashSet<>();
	// nodeStack.add(primary1);
	// pathEdgesInternal(pathEdges, primary2, nodeStack, primary1, false,
	// threshold, 1.0);
	// return pathEdges;
	// }
	//
	// /**
	// * Depth-first sesarch, backtracking if we're in a loop, or meet a
	// collider.
	// * If we reach a goal node, add the current path to pathEdges
	// */
	// private boolean pathEdgesInternal(final Collection<Edge<NODETYPE>>
	// pathEdges, final Node<NODETYPE> goalNode,
	// final Collection<Node<NODETYPE>> nodeStack, final Node<NODETYPE> node,
	// final boolean downstreamOnly,
	// final double threshold, final double strength) {
	// boolean result = false;
	// for (final Iterator<Edge<NODETYPE>> it = getNodeEdgeIterator(node);
	// it.hasNext();) {
	// final Edge<NODETYPE> edge = it.next();
	// final Node<NODETYPE> adj = edge.getDistalNode(node);
	// // System.out.println("RSNI " + node + " " + adj + " " + seenArrow);
	// final boolean isDownstream = edge.canCause(adj);
	// // It's always OK to go downstream, but once you have, you can't go
	// // up again
	// if (!nodeStack.contains(adj) && (!downstreamOnly || isDownstream)) {
	// final double beta = threshold > 0 && weigher != null
	// ? Math.abs(weigher.weight(this, edge.getCausingNode(),
	// edge.getCausedNode())) : 1.0;
	// final double substrength = strength * beta;
	// if (substrength >= threshold) {
	// boolean add = goalNode == adj;
	// if (!add) {
	// nodeStack.add(adj);
	// add = pathEdgesInternal(pathEdges, goalNode, nodeStack, adj,
	// downstreamOnly || isDownstream,
	// threshold, substrength);
	// nodeStack.remove(adj);
	// }
	// if (add) {
	// pathEdges.add(edge);
	// result = true;
	// }
	// }
	// }
	// }
	// return result;
	// }

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		for (final Edge<NODETYPE> edge : getEdges()) {
			buf.append(edge).append("\n");
		}
		return UtilString.toString(this, buf);
	}

}
