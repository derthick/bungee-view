package edu.cmu.cs.bungee.javaExtensions.graph;

import java.awt.geom.Line2D;
import java.awt.geom.Line2D.Double;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * There can only be one Edge between 2 Nodes, but it can be directional in that
 * there is a left Node and a right Node.
 */
public class Edge<NodeObjectType extends Comparable<? super NodeObjectType>>
		implements Comparable<Edge<NodeObjectType>> {

	public enum EndpointType {
		ARROW, CIRCLE, NONE
	}

	public enum LabelLocation {
		LEFT_LABEL, CENTER_LABEL, RIGHT_LABEL
	}

	private final @NonNull List<String> labels = UtilArray.getArrayList(null, null, null);
	private final @NonNull List<String> labelsUnmodifiable;
	private final @NonNull Endpoint<NodeObjectType> leftEndpoint;
	private final @NonNull Endpoint<NodeObjectType> rightEndpoint;

	// left is nodes.get(0); right is nodes.get(1)
	private final @Immutable @NonNull List<Node<NodeObjectType>> nodes;

	Edge(final @NonNull Node<NodeObjectType> leftNode, final @NonNull Node<NodeObjectType> rightNode) {
		super();
		labelsUnmodifiable = UtilArray.unmodifiableList(labels);
		leftEndpoint = new Endpoint<>(leftNode);
		rightEndpoint = new Endpoint<>(rightNode);
		final List<Node<NodeObjectType>> _nodes = new ArrayList<>(2);
		_nodes.add(leftNode);
		_nodes.add(rightNode);
		nodes = UtilArray.unmodifiableList(_nodes);
	}

	boolean hasNode(final @NonNull Node<NodeObjectType> node1) {
		return node1 == getRightNode() || node1 == getLeftNode();
	}

	// private int getMask(Node<NodeObjectType> caused) {
	// assert hasNode(caused);
	// return caused == getNode1() ? 1 : 2;
	// }

	// boolean isNull() {
	// return orientation == NEITHER;
	// }

	// TODO Remove unused code found by UCDetector
	// public void setBidirectional() {
	// leftEndpoint.type = EndpointType.ARROW;
	// rightEndpoint.type = EndpointType.ARROW;
	// }

	// TODO Remove unused code found by UCDetector
	// void removeDirection(final Node<NodeObjectType> caused) {
	// // if (edge.getNode2().getName().equals("p2772")
	// // && edge.getNode1().getName().equals(
	// // "lithograph"))
	// // return false;
	//
	// // String name = getDistalNode(caused) + "--" + caused;
	// // // System.out.println("Removing weak edge " + name);
	// // printMe(name);
	//
	// assert hasNode(caused);
	// assert canCause(caused);
	// getEndpoint(caused).type = EndpointType.NONE;
	// // orientation &= ~getMask(caused);
	// }

	public @NonNull Endpoint<?> getEndpoint(final @NonNull Node<NodeObjectType> node1) {
		assert hasNode(node1);
		return node1 == getRightNode() ? leftEndpoint : rightEndpoint;
	}

	// TODO Remove unused code found by UCDetector
	// void addDirection(final Node<NodeObjectType> caused) {
	// assert hasNode(caused);
	// // if (!canCause(caused))
	// // System.out.println("addDirection "+this);
	// getEndpoint(caused).type = EndpointType.ARROW;
	// // orientation |= getMask(caused);
	// }

	public void setDirection(final @NonNull Node<NodeObjectType> caused) {
		assert hasNode(caused);
		getEndpoint(caused).setType(EndpointType.ARROW);
		getEndpoint(getDistalNode(caused)).setType(EndpointType.NONE);
		// orientation = getMask(caused);
		// System.out.println("setDirection "+this);
	}

	/**
	 * @return {<leftNode>, <rightNode>}
	 */
	public @NonNull List<Node<NodeObjectType>> getNodes() {
		return nodes;
	}

	public @NonNull Node<NodeObjectType> getLeftNode() {
		return leftEndpoint.getNode();
	}

	public @NonNull Node<NodeObjectType> getRightNode() {
		return rightEndpoint.getNode();
	}

	@NonNull
	Node<NodeObjectType> getDistalNode(final @NonNull Node<NodeObjectType> proximalNode) {
		assert proximalNode == getRightNode() || proximalNode == getLeftNode();
		return proximalNode == getRightNode() ? getLeftNode() : getRightNode();
	}

	boolean canCause(final @NonNull Node<NodeObjectType> caused) {
		return getEndpoint(caused).getType() == EndpointType.ARROW;
		// return (orientation & getMask(caused)) > 0;
	}

	public @NonNull List<String> getLabels() {
		return labelsUnmodifiable;
	}

	public @Nullable String getLabel(final @NonNull LabelLocation labelLocation) {
		return labels.get(labelLocation.ordinal());
	}

	public void setLabel(final @Nullable String label, final @NonNull Node<NodeObjectType> node) {
		setLabel(label, getPosition(node));
	}

	public void setLabel(final @Nullable String label, final @NonNull LabelLocation labelLocation) {
		// assert label.indexOf('@') == -1;
		labels.set(labelLocation.ordinal(), label);
	}

	/**
	 * @return LabelLocation.LEFT_LABEL or LabelLocation.RIGHT_LABEL
	 */
	private @NonNull LabelLocation getPosition(final @NonNull Node<NodeObjectType> node) {
		assert node == getLeftNode() || node == getRightNode();
		return node == getLeftNode() ? LabelLocation.LEFT_LABEL : LabelLocation.RIGHT_LABEL;
	}

	public int getNumDirectedEdges() {
		return (canCause(getRightNode()) ? 1 : 0) + (canCause(getLeftNode()) ? 1 : 0);
		// switch (orientation) {
		// case NEITHER:
		// return 0;
		// case FORWARD:
		// case BACKWARD:
		// return 1;
		// case BIDIRECTIONAL:
		// return 2;
		// default:
		// assert false;
		// return -1;
		// }
	}

	// public void setEndpoint(int x, int y, Node<NodeObjectType> node) {
	// assert hasNode(node);
	// if (node == node1) {
	// x1 = x;
	// y1 = y;
	// } else {
	// x2 = x;
	// y2 = y;
	// }
	// }
	//
	// public int getX(Node<NodeObjectType> node) {
	// assert hasNode(node);
	// return node == node1 ? x1 : x2;
	// }
	//
	// public int getY(Node<NodeObjectType> node) {
	// assert hasNode(node);
	// return node == node1 ? y1 : y2;
	// }

	// TODO Remove unused code found by UCDetector
	// public Point2D getEndpoint(final Node<NodeObjectType> node,
	// final Font font, final FontRenderContext frc) {
	// assert hasNode(node);
	// final Rectangle2D rect = node.getRectangle(font, frc);
	// return getEndpoint(rect);
	// }

	/**
	 * @param rect
	 * @return the point where the line segment between the centers of our nodes
	 *         intersects rect (which is the fullBounds of one of the nodes).
	 */
	public @NonNull Point2D getEndingPoint(final @NonNull Rectangle2D rect) {
		final Line2D centerToCenterLine = getCenterToCenterLine();
		assert rect.contains(centerToCenterLine.getP1()) != rect.contains(centerToCenterLine.getP2()) : rect + " "
				+ centerToCenterLine.getP1() + " " + centerToCenterLine.getP2();
		final double x1 = rect.getX();
		final double y1 = rect.getY();
		final double x2 = x1 + rect.getWidth();
		final double y2 = y1 + rect.getHeight();
		Point2D result = getIntersection(centerToCenterLine, new Line2D.Double(x1, y1, x2, y1));
		if (result == null) {
			result = getIntersection(centerToCenterLine, new Line2D.Double(x2, y1, x2, y2));
		}
		if (result == null) {
			result = getIntersection(centerToCenterLine, new Line2D.Double(x1, y2, x2, y2));
		}
		if (result == null) {
			result = getIntersection(centerToCenterLine, new Line2D.Double(x1, y1, x1, y2));
		}
		assert result != null : rect + " (" + centerToCenterLine.getX1() + ", " + centerToCenterLine.getY1() + ") ("
				+ centerToCenterLine.getX2() + ", " + centerToCenterLine.getY2() + ")";
		// System.out.println("dd " + rect + " " + result);
		assert result.getX() >= x1 - 0.000001 : result.getX() + " " + x1;
		assert result.getX() <= x2 + 0.000001 : result.getX() + " " + x2;
		assert result.getY() >= y1 - 0.000001 : result.getY() + " " + y1;
		assert result.getY() <= y2 + 0.000001 : result.getY() + " " + y2;
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * @param edge2
	// * @return whether the edges cross
	// *
	// * see <a
	// * href="http://local.wasp.uwa.edu.au/~pbourke/geometry/lineline2d/"
	// * >Intersection point of two lines< /a>
	// */
	// Point2D getIntersection(final Edge<NodeObjectType> edge2) {
	// // if (getNodes().contains(edge2.getNode1())
	// // || getNodes().contains(edge2.getNode2()))
	// // return null;
	// if (minX() >= edge2.maxX() || maxX() <= edge2.minX()
	// || minY() >= edge2.maxY() || maxY() <= edge2.minY()) {
	// return null;
	// }
	// return getIntersection(getCenterToCenterLine(),
	// edge2.getCenterToCenterLine());
	// }

	// private int maxX() {
	// return Math
	// .max(getRightNode().getCenterX(), getLeftNode().getCenterX());
	// }

	// private int minX() {
	// return Math
	// .min(getRightNode().getCenterX(), getLeftNode().getCenterX());
	// }
	//
	// private int maxY() {
	// return Math
	// .max(getRightNode().getCenterY(), getLeftNode().getCenterY());
	// }
	//
	// private int minY() {
	// return Math
	// .min(getRightNode().getCenterY(), getLeftNode().getCenterY());
	// }

	private @NonNull Double getCenterToCenterLine() {
		final Node<NodeObjectType> node11 = getLeftNode();
		final Node<NodeObjectType> node12 = getRightNode();
		final int x11 = node11.getCenterX();
		final int y11 = node11.getCenterY();
		final int x12 = node12.getCenterX();
		final int y12 = node12.getCenterY();
		// System.out.println("Gcc "+x11+" "+y11+" "+x12+" "+y12+" ");
		return new Line2D.Double(x11, y11, x12, y12);
	}

	private static @Nullable Point2D getIntersection(final @NonNull Line2D edge1, final @NonNull Line2D edge2) {
		// Find intersection of the lines determined by each edge's line segment
		final double x11 = edge1.getX1();
		final double y11 = edge1.getY1();
		final double x12 = edge1.getX2();
		final double y12 = edge1.getY2();
		final double x21 = edge2.getX1();
		final double y21 = edge2.getY1();
		final double x22 = edge2.getX2();
		final double y22 = edge2.getY2();

		final double denom = (y22 - y21) * (x12 - x11) - (x22 - x21) * (y12 - y11);
		final double numeratorA = (x22 - x21) * (y11 - y21) - (y22 - y21) * (x11 - x21);
		final double numeratorB = (x12 - x11) * (y11 - y21) - (y12 - y11) * (x11 - x21);
		Point2D.Double result = null;
		if (denom == 0) {
			if (numeratorA == 0) {
				// lines are coincident
				assert numeratorB == 0;
				if (x21 == x22) {
					if (between(y11, y21, y12)) {
						result = new Point2D.Double(x21, y21);
					} else if (between(y11, y22, y12)) {
						result = new Point2D.Double(x22, y22);
					}
				} else {
					if (between(x11, x21, x12)) {
						result = new Point2D.Double(x21, y21);
					} else if (between(x11, x22, x12)) {
						result = new Point2D.Double(x22, y22);
					}
				}
			} else {
				// lines are parallel. result remains null.
			}
		} else {
			final double ua = numeratorA / denom;
			if (ua > 0 && ua < 1) {
				final double ub = numeratorB / denom;
				if (ub > 0 && ub < 1) {
					result = new Point2D.Double(x11 + ua * (x12 - x11), y11 + ua * (y12 - y11));
				}
			}

			// } else if (between(numeratorA, 0, denom)
			// && between(numeratorB, 0, denom)) {
			// double ua = numeratorA / denom;
			// result = new Point2D.Double(x11 + ua * (x12 - x11), y11 + ua
			// * (y12 - y11));
		}
		return result;
	}

	private static boolean between(final double x, final double x1, final double x2) {
		return x > Math.min(x1, x2) && x < Math.max(x1, x2);
	}

	private boolean isArrowhead(final @NonNull Node<NodeObjectType> node) {
		return canCause(node) && !canCause(getDistalNode(node));
	}

	@Override
	public String toString() {
		final String connector = " " + leftEndpoint.symbol("<") + "-" + rightEndpoint.symbol(">") + " ";

		final Node<NodeObjectType> caused = isArrowhead(getRightNode()) ? getRightNode() : getLeftNode();
		// String connector = isArrowhead(caused) ? " --> " : " --- ";
		return UtilString.toString(this, getDistalNode(caused).getLabel() + connector + caused.getLabel());
	}

	public @NonNull Node<NodeObjectType> getCausedNode() {
		assert isArrowhead(getRightNode()) || isArrowhead(getLeftNode());
		return isArrowhead(getRightNode()) ? getRightNode() : getLeftNode();
	}

	public @NonNull Node<NodeObjectType> getCausingNode() {
		assert isArrowhead(getRightNode()) || isArrowhead(getLeftNode());
		return isArrowhead(getRightNode()) ? getLeftNode() : getRightNode();
	}

	public static class Endpoint<NodeObjectType extends Comparable<? super NodeObjectType>> { // NO_UCD
																								// (use
																								// default)
		private @NonNull EndpointType type = EndpointType.NONE;
		private final @NonNull Node<NodeObjectType> node;

		Endpoint(final @NonNull Node<NodeObjectType> _node) {
			node = _node;
		}

		// Endpoint(final EndpointType _type, final Node<NodeObjectType> _node)
		// {
		// type = _type;
		// node = _node;
		// }

		@NonNull
		String symbol(final @NonNull String arrowSymbol) {
			switch (type) {
			case ARROW:
				return arrowSymbol;
			case CIRCLE:
				return "o";
			case NONE:
				return "-";

			default:
				assert false;
				return "?";
			}
		}

		public @NonNull EndpointType getType() {
			return type;
		}

		void setType(final @NonNull EndpointType _type) {
			type = _type;
		}

		@NonNull
		Node<NodeObjectType> getNode() {
			return node;
		}

	}

	@Override
	public int compareTo(final Edge<NodeObjectType> edge0) {
		int result = nodes.get(0).compareTo(edge0.nodes.get(0));
		if (result == 0) {
			result = nodes.get(1).compareTo(edge0.nodes.get(1));
		}
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + nodes.hashCode();
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Edge<?> other = (Edge<?>) obj;
		if (!nodes.equals(other.nodes)) {
			return false;
		}
		return true;
	}

}
