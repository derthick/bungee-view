package edu.cmu.cs.bungee.client.query.explanation;

import static edu.cmu.cs.bungee.javaExtensions.UtilString.formatCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.UtilArray;
import edu.cmu.cs.bungee.javaExtensions.UtilMath;
import edu.cmu.cs.bungee.javaExtensions.UtilString;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge.LabelLocation;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph;
import edu.cmu.cs.bungee.javaExtensions.graph.Node;

public class GraphicalModel extends Distribution { // NO_UCD (use default)

	static final double DEFAULT_EDGE_COST = 0.2; // 0.06;
	/**
	 * If weights get too big, predicted probabilities go to zero, and KL and z
	 * go to infinity.
	 */
	protected static final double MAX_WEIGHT = 15.0;
	private static final boolean IS_SYMMETRIC = true;

	/**
	 * {node1, node2) ⇒ weight
	 */
	private final @NonNull double[] weights;
	/**
	 * Non-existent edges are stored as exp(0.0)=1.0. // uncached are coded
	 * -1.0. // 2015 I dont think so.
	 */
	private final @NonNull double[] expWeights;
	private final @NonNull double[] energies;
	private final @NonNull double[] expEnergies;
	/**
	 * Conceptually Immutable
	 */
	private final @NonNull int[][] edgeIndexes;
	private final @NonNull int[][] edgeStates;
	private final @NonNull double[] logDistribution;
	private int nNonBiasEdges = 0;
	private int nEdgesPlusBiases = 0;

	private boolean edgesFixed = false;

	/**
	 * A GraphicalModel with the specified facets and edges, but weights are all
	 * zero.
	 */
	GraphicalModel(final @NonNull SortedSet<Perspective> _facets, final @NonNull Set<SimpleEdge> nonBiasEdges,
			final boolean _isSymmetric, final int count) {
		super(_facets, count);
		assert _isSymmetric;
		assert !_isSymmetric || assertEdgesCanonical(nonBiasEdges);

		energies = new double[nStates];
		expEnergies = new double[nStates];
		Arrays.fill(expEnergies, 1.0);
		logDistribution = new double[nStates];
		edgeIndexes = computeEdgeIndexes(nonBiasEdges);
		weights = new double[nEdgesPlusBiases];
		expWeights = new double[nEdgesPlusBiases];
		Arrays.fill(expWeights, 1.0);
		edgeStates = edgeStates();
		cacheWeightsInfo();
	}

	public @NonNull double[] getLogDistribution() {
		// defensive copy
		return Util.nonNull(Arrays.copyOf(logDistribution, logDistribution.length));
	}

	private static boolean assertEdgesCanonical(final Set<SimpleEdge> edges) {
		for (final SimpleEdge edge : edges) {
			assert edge.causeP.compareTo(edge.causedP) < 0 : edges + " " + edge;
		}
		return true;
	}

	int nUsedFacets() {
		int nUsedFacets = 0;
		for (final Perspective p : facets) {
			if (nEdges(p) > 0) {
				nUsedFacets++;
			}
		}
		return nUsedFacets;
	}

	/**
	 * @return Perspectives for unconnected nodes.
	 */
	@NonNull
	Collection<Perspective> unusedFacets() {
		final Collection<Perspective> unusedFacets = new LinkedList<>();
		for (final Perspective p : facets) {
			if (nEdges(p) == 0) {
				unusedFacets.add(p);
			}
		}
		return unusedFacets;
	}

	/**
	 * @return numbe of non-bias edges where p is either causeP or causedP.
	 */
	private int nEdges(final Perspective p) {
		int npEdges = 0;
		for (final SimpleEdge edge : getEdges(false)) {
			if (edge.causeP == p || edge.causedP == p) {
				npEdges++;
			}
		}
		return npEdges;
	}

	private boolean hasEdge(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		return edgeIndexes[facetIndex(causeP)][facetIndex(causedP)] >= 0;
	}

	protected boolean hasEdge(final int cause, final int caused) {
		return edgeIndexes[cause][caused] >= 0;
	}

	double getExpWeightOrExpZero(final int cause, final int caused) {
		final int edgeIndex = edgeIndexes[cause][caused];
		final double expWeight = edgeIndex < 0 ? 1.0 : expWeights[edgeIndex];
		assert expWeight >= 0.0 : expWeight + " " + cause + " " + caused + " " + getWeight(cause, caused);
		assert !Double.isNaN(expWeight);
		assert !Double.isInfinite(expWeight);
		return expWeight;
	}

	private int edgeIndex(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		return edgeIndexes[facetIndex(causeP)][facetIndex(causedP)];
	}

	double getWeightOrZero(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		return hasEdge(causeP, causedP) ? getWeight(causeP, causedP) : 0.0;
	}

	protected double getWeightOrZero(final int cause, final int caused) {
		final int edgeIndex = edgeIndexes[cause][caused];
		return edgeIndex < 0 ? 0.0 : weights[edgeIndex];
	}

	double getWeight(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		return getWeight(facetIndex(causeP), facetIndex(causedP));
	}

	protected double getWeight(final int cause, final int caused) {
		assert hasEdge(cause, caused) : getFacet(cause) + " " + getFacet(caused) + " " + this;
		return weights[edgeIndexes[cause][caused]];
	}

	// TODO Remove unused code found by UCDetector
	// protected double getStdDevNormalizedWeight(final @NonNull Perspective
	// causeP, final @NonNull Perspective causedP) {
	// return getWeight(causeP, causedP) * causeP.stdDev(null);
	// }

	protected @NonNull double[] getWeights() {
		return Util.nonNull(ArrayUtils.clone(weights));
	}

	double[] getWeights(final Set<SimpleEdge> _edges) {
		final double[] result = new double[_edges.size()];
		int i = 0;
		for (final SimpleEdge edge : _edges) {
			final Perspective causeP = edge.causeP;
			final Perspective causedP = edge.causedP;
			result[i++] = getWeightOrZero(causeP, causedP);
		}
		return result;
	}

	/**
	 * Only called by NonAlchemyExplanation.optimizeWeights during BURN_IN.
	 *
	 * This just "remembers" the weight; If isChange, MUST call resetWeights
	 * afterwards to cache other info.
	 */
	protected boolean setWeight(final int cause, final int caused, double weight) {
		assert !Double.isInfinite(weight) && !Double.isNaN(weight) : weight;
		weight = UtilMath.constrain(weight, -MAX_WEIGHT, MAX_WEIGHT);

		final int edgeIndex = edgeIndexes[cause][caused];
		final boolean isChange = weights[edgeIndex] != weight;

		weights[edgeIndex] = weight;
		return isChange;
	}

	/**
	 * set weights for each [causeP, causedP] pair to match those in
	 * graphicalModel.
	 */
	void setWeights(final GraphicalModel graphicalModelToCopyFrom, final Set<SimpleEdge> _edges) {
		final double[] newWeights = getWeights();
		for (final SimpleEdge edge : _edges) {
			final Perspective causeP = edge.causeP;
			final Perspective causedP = edge.causedP;
			final int edgeIndex = edgeIndex(causeP, causedP);
			newWeights[edgeIndex] = graphicalModelToCopyFrom.getWeight(causeP, causedP);
		}
		setWeights(newWeights);
	}

	/**
	 * Updates weights, expWeights, energies, expEnergies, z().
	 *
	 * @return whether weights changed
	 */
	protected boolean setWeights(final @NonNull double[] _weights) {
		boolean isChange = false;
		decacheOdds();
		Arrays.fill(energies, 0.0);
		Arrays.fill(expEnergies, 1.0);

		for (int edgeIndex = 0; edgeIndex < nEdgesPlusBiases; edgeIndex++) {
			final double weight = _weights[edgeIndex];
			assert !Double.isInfinite(weight) && !Double.isNaN(weight) : weight;
			if (weights[edgeIndex] != weight) {
				isChange = true;
				final double expWeight = Math.exp(weight);
				assert expWeight > 0.0 && !Double.isNaN(expWeight) && !Double.isInfinite(expWeight) : weights[edgeIndex]
						+ " ⇒ " + weight + "\n" + UtilString.valueOfDeep(_weights);
				weights[edgeIndex] = weight;
				expWeights[edgeIndex] = expWeight;

				final int[] statesAffected = edgeStates[edgeIndex];
				for (final int element : statesAffected) {
					final int state = element;
					energies[state] += weight;
					expEnergies[state] *= expWeight;
				}
			}
		}
		if (isChange) {
			z();
		}
		return isChange;
	}

	/**
	 * Cache expWeights, energies, expEnergies, z() based on weights.
	 */
	protected void cacheWeightsInfo() {
		final double[] startWeights = getWeights();
		for (int edgeIndex = 0; edgeIndex < nEdgesPlusBiases; edgeIndex++) {
			// Make sure setWeights will think every weight changed.
			weights[edgeIndex] += 1.0;
		}
		setWeights(startWeights);
	}

	/**
	 * @return a canonical order for nonBiasEdges + all possible biases.
	 */
	private @NonNull int[][] computeEdgeIndexes(final Set<SimpleEdge> nonBiasEdges) {
		final int[][] edgeIndexes1 = new int[nFacets][];
		for (int cause = 0; cause < nFacets; cause++) {
			// Create structure and biases
			edgeIndexes1[cause] = new int[nFacets];
			Arrays.fill(edgeIndexes1[cause], -1);
			edgeIndexes1[cause][cause] = cause;
		}
		for (final SimpleEdge edge : nonBiasEdges) {
			final int cause1 = facetIndex(edge.causeP);
			final int caused1 = facetIndex(edge.causedP);
			assert cause1 != caused1 : "Biases are implicit";
			assert !edgesFixed;
			assert cause1 < nFacets && caused1 < nFacets : "facets=" + facets + " cause=" + cause1 + " caused="
					+ caused1;
			assert edgeIndexes1[cause1][caused1] < 0;
			edgeIndexes1[cause1][caused1] = 1;
			edgeIndexes1[caused1][cause1] = 1;
		}
		// The above sets edgeIndexes[...] to 1; now set them to edgeIndex
		int edgeIndex = nFacets;
		for (int cause = 0; cause < nFacets; cause++) {
			for (int caused = cause + 1; caused < nFacets; caused++) {
				if (edgeIndexes1[cause][caused] >= 0) {
					edgeIndexes1[caused][cause] = edgeIndex;
					edgeIndexes1[cause][caused] = edgeIndex++;
				}
			}
		}
		edgesFixed = true;
		nNonBiasEdges = nonBiasEdges.size();
		nEdgesPlusBiases = nNonBiasEdges + nFacets;
		return edgeIndexes1;
	}

	void removeEdge(final int cause, final int caused) {
		assert hasEdge(cause, caused);
		assert !edgesFixed;
		edgeIndexes[cause][caused] = -1;
		edgeIndexes[caused][cause] = -1;
		if (cause != caused) {
			nNonBiasEdges--;
		}
		nEdgesPlusBiases--;
	}

	/**
	 * @return [cause, caused] in this order [0, 1], [0, 2], [0, 3], [1, 2], [1,
	 *         3], [2, 3]
	 *
	 *         i.e. for (int cause = 0; cause < nFacets; cause++) { for (int
	 *         caused = cause; caused <nFacets; caused++) {
	 *
	 *         for xvec, these follow the bias weights
	 */
	protected EdgeIterator getEdgeIterator() {
		return new EdgeIterator();
	}

	/**
	 * Never returns biases.
	 */
	class EdgeIterator implements Iterator<int[]> {

		// current edge
		private int cause = 0;
		private int caused = -1;

		// next edge
		private int nextCause = -1;
		private int nextCaused = -1;

		@Override
		public boolean hasNext() {
			if (nextCause < 0) {
				nextCause = cause;
				nextCaused = caused + 1;
				for (; nextCause < nFacets; nextCause++) {
					for (; nextCaused < nFacets; nextCaused++) {
						if (nextCause != nextCaused && hasEdge(nextCause, nextCaused)
								&& (!IS_SYMMETRIC || nextCause < nextCaused)) {
							return true;
						}
					}
					nextCaused = 0;
				}
			}
			return nextCause < nFacets;
		}

		@Override
		public int[] next() {
			if (hasNext()) {
				cause = nextCause;
				caused = nextCaused;
				assert cause != caused;
				nextCause = -1;
				final int[] edge = { cause, caused };
				return edge;
			} else {
				throw new NoSuchElementException();
			}
		}

		@Override
		public void remove() {
			assert false : this;
			if (caused > 0) {
				removeEdge(cause, caused);
			} else {
				throw new IllegalStateException();
			}
		}
	}

	private int[][] stateWeights;

	protected int[][] stateWeights() {
		if (stateWeights == null) {
			stateWeights = new int[nStates][];
			for (int state = 0; state < stateWeights.length; state++) {
				int[] wts = new int[0];
				int argIndex = nFacets;
				for (int cause = 0; cause < nFacets; cause++) {
					for (int caused = cause; caused < nFacets; caused++) {
						if (hasEdge(cause, caused)) {
							if (UtilMath.isBit(state, caused)) {
								if (cause == caused) {
									wts = ArrayUtils.add(wts, 0, cause);
								} else if (UtilMath.isBit(state, cause)) {
									wts = ArrayUtils.add(wts, 0, argIndex);
								}
							}
							if (caused > cause) {
								argIndex++;
							}
						}
					}
				}
				stateWeights[state] = wts;
			}
		}
		return stateWeights;
	}

	private @NonNull int[][] edgeStates() {
		final int[][] edgeStates1 = new int[nEdgesPlusBiases][];
		final int[] tempStates = new int[nStates];
		for (int cause = 0; cause < nFacets; cause++) {
			for (int caused = cause; caused < nFacets; caused++) {
				if (hasEdge(cause, caused)) {
					int stateIndex = 0;
					for (int state = 0; state < nStates; state++) {
						if (UtilMath.isBit(state, cause) && UtilMath.isBit(state, caused)) {
							tempStates[stateIndex++] = state;
						}
					}
					final int[] es = new int[stateIndex];
					System.arraycopy(tempStates, 0, es, 0, stateIndex);
					final int edgeIndex = edgeIndexes[cause][caused];
					edgeStates1[edgeIndex] = es;
				}
			}
		}
		return edgeStates1;
	}

	/**
	 * @return never NaN or Infinite.
	 */
	double weightOrSigmoid(final @NonNull Perspective causeP, final @NonNull Perspective causedP) {
		return weightOrSigmoid(facetIndex(causeP), facetIndex(causedP));
	}

	private double weightOrSigmoid(final int cause, final int caused) {
		double result;
		if (Explanation.USE_SIGMOID) {
			final double expw = getExpWeightOrExpZero(cause, caused);
			result = expw / (expw + 1.0);
		} else {
			result = getWeightOrZero(cause, caused);
		}
		return result;
	}

	/**
	 * @return roundToInt(100.0 * weight) or roundToInt(200.0 * (sigmoid-0.5))
	 *
	 *         either way, in [-100, 100]
	 */
	private static String formatWeightOrSigmoid(final double weightOrSigmoid) {
		String result;
		if (Double.isNaN(weightOrSigmoid)) {
			result = "?";
		} else if (Explanation.USE_SIGMOID) {
			result = Integer.toString(UtilMath.roundToInt(200.0 * (weightOrSigmoid - 0.5)));
		} else {
			result = Integer.toString(UtilMath.roundToInt(100.0 * weightOrSigmoid));
		}
		return result;
	}

	/**
	 * Computes probDist and logDistribution as a side effect
	 */
	private double z() {
		double z = UtilArray.kahanSum(expEnergies);
		if (Double.isInfinite(z) || Double.isNaN(z)) {
			z = zInfinite();
		} else {
			zFinite(z);
		}
		assert checkDist();
		assert z >= 0.0 : z;
		return z;
	}

	/**
	 * Only called by z().
	 */
	private void zFinite(final double z) {
		final double logZ = Math.log(z);
		for (int state = 0; state < nStates; state++) {
			probDist[state] = expEnergies[state] / z;
			logDistribution[state] = energy(state) - logZ;
		}
	}

	/**
	 * Only called by z().
	 */
	private double zInfinite() {
		double z = 0.0;
		for (int state = 0; state < nStates; state++) {
			if (Double.isInfinite(expEnergies[state])) {
				z++;
				probDist[state] = 1.0;
			} else {
				probDist[state] = 0.0;
			}
		}
		final double logZ = Math.log(z);
		for (int state = 0; state < nStates; state++) {
			probDist[state] /= z;
			logDistribution[state] = energy(state) - logZ;
		}
		return z;
	}

	private double energy(final int state) {
		final double energy = energies[state];
		assert !Double.isNaN(energy) && !Double.isInfinite(energy) : UtilString.valueOfDeep(energies);
		return energy;
	}

	/**
	 * Add nodes and edges. Only called by Explanation.buildGraph
	 *
	 * @param nullModel
	 *            this is just to label the "before" weights.
	 */
	protected @NonNull Graph<Perspective> buildGraph(final @NonNull double[] Rs,
			final @NonNull double[][] rNormalizedWeights, final double KL, final @NonNull Explanation nullModel,
			final boolean debug) {
		final Graph<Perspective> graph = new Graph<>();
		final Map<Perspective, Node<Perspective>> nodeMap = ensureNodes(Rs, graph, nullModel);
		for (final Iterator<int[]> it = getEdgeIterator(); it.hasNext();) {
			final int[] edge = it.next();
			final Node<Perspective> causeNode = nodeMap.get(getFacet(edge[0]));
			final Node<Perspective> causedNode = nodeMap.get(getFacet(edge[1]));
			assert causeNode != null && causedNode != null;
			addEdge(rNormalizedWeights, graph, causeNode, causedNode, nullModel, debug);
			if (IS_SYMMETRIC) {
				addEdge(rNormalizedWeights, graph, causedNode, causeNode, nullModel, debug);
			}
		}
		assert !graph.getNodes().isEmpty() : graph2string(Rs, rNormalizedWeights, KL);
		return graph;
	}

	private @NonNull Map<Perspective, Node<Perspective>> ensureNodes(
			@SuppressWarnings("unused") final @NonNull double[] Rs, final @NonNull Graph<Perspective> graph,
			@SuppressWarnings("unused") final Explanation nullModel) {
		final Map<Perspective, Node<Perspective>> nodeMap = new IdentityHashMap<>(facets.size());
		// final Collection<Perspective> primaryFacets = this !=
		// nullModel.predicted ? nullModel.facets()
		// : Collections.EMPTY_SET;
		for (final Perspective facet : facets) {
			assert facet != null;
			final StringBuilder buf = new StringBuilder();
			// final int facetIndex = facetIndex(facet);
			// final boolean isInNullModel = primaryFacets.contains(facet);
			// Prefix with space so edge line doesn't merge with any minus sign

			// buf.append(" ");
			// if (isInNullModel) {
			// buf.append(formatWeightOrSigmoid(nullModel.predicted.weightOrSigmoid(facet,
			// facet))).append(" > ");
			// }
			// buf.append(formatWeightOrSigmoid(weightOrSigmoid(facetIndex,
			// facetIndex)));
			//
			// buf.append(" r=").append(formatWeightOrSigmoid(Rs[facetIndex]));
			buf.append(" ").append(facet.getNameNow());

			final String string = buf.toString();
			assert string != null;
			final Node<Perspective> node = graph.addNode(facet, string);
			nodeMap.put(facet, node);
		}
		return nodeMap;
	}

	protected @NonNull String graph2string(final @NonNull double[] Rs, final @NonNull double[][] RnormalizedWeights,
			final double KL) {
		final StringBuilder buf = new StringBuilder();
		buf.append("printGraph ").append(this).append(" KL=").append(KL);

		// buf.append("pred=").append(printCounts()).append("
		// obs=").append(observedDistForNormalization.printCounts());

		for (final Perspective caused : facets) {
			assert caused != null;
			buf.append(getWeight(caused, caused)).append(" (").append(Rs[facetIndex(caused)]).append(") ")
					.append(caused);
		}
		for (final Iterator<int[]> it = getEdgeIterator(); it.hasNext();) {
			final int[] edge = it.next();
			final Perspective cause = getFacet(edge[0]);
			final Perspective caused = getFacet(edge[1]);
			final double weight = weightOrSigmoid(cause, caused);
			buf.append("σ=").append(formatWeightOrSigmoid(weight)).append(" (")
					.append(formatWeightOrSigmoid(RnormalizedWeights[facetIndex(caused)][facetIndex(cause)]))
					.append(") ").append(cause).append(" ⇒ (")
					.append(formatWeightOrSigmoid(RnormalizedWeights[facetIndex(cause)][facetIndex(caused)]))
					.append(") ").append(caused);
		}
		buf.append("\n");
		return UtilString.bufToString(buf);
	}

	// This is called twice; once for each direction.
	private void addEdge(final @NonNull double[][] rNormalizedWeights, final @NonNull Graph<Perspective> graph,
			final @NonNull Node<Perspective> causeNode, final @NonNull Node<Perspective> causedNode,
			final @NonNull Explanation nullModel, final boolean debug) {
		final Edge<Perspective> edge = graph.ensureEdge(causeNode, causedNode);
		final Perspective causeP = causeNode.object;
		final Perspective causedP = causedNode.object;
		final int cause = facetIndex(causeP);
		final int caused = facetIndex(causedP);
		final SortedSet<Perspective> primaryFacets = nullModel.facets();
		final boolean isInNullModel = this != nullModel.predicted && primaryFacets.contains(causeP)
				&& primaryFacets.contains(causedP);

		if (debug) {
			// Left,Right:
			// я: [ <100*nullModel.rNormalizedWeight forward/backward> > ]
			// <100*rNormalizedWeight forward/backward>
			String label = "я: " + formatWeightOrSigmoid(rNormalizedWeights[cause][caused]) + "/"
					+ formatWeightOrSigmoid(rNormalizedWeights[caused][cause]);
			if (isInNullModel) {
				label = formatWeightOrSigmoid(nullModel.getRNormalizedWeight(causeP, causedP)) + "/"
						+ formatWeightOrSigmoid(nullModel.getRNormalizedWeight(causedP, causeP)) + " > " + label;
			}
			edge.setLabel("        " + label + "        ", causedNode);

			// Center:
			// σ: [ <100*sigmoid(nullModel.weight)> > ]
			// <100*sigmoid(weight)>
			edge.setLabel("σ: "
					+ (isInNullModel
							? formatWeightOrSigmoid(nullModel.predicted.weightOrSigmoid(causeP, causedP)) + " > " : "")
					+ formatWeightOrSigmoid(weightOrSigmoid(causeP, causedP)), LabelLocation.CENTER_LABEL);
		} else {
			String label = formatWeightOrSigmoid(weightOrSigmoid(causeP, causedP));
			if (isInNullModel && nFacets > nullModel.nFacets()) {
				label = formatWeightOrSigmoid(nullModel.predicted.weightOrSigmoid(causeP, causedP)) + " > " + label;
			}
			edge.setLabel(label, LabelLocation.CENTER_LABEL);
		}
	}

	// private static double averageRNormalizedWeight(final double[][]
	// rNormalizedWeights, final int cause,
	// final int caused) {
	// return (rNormalizedWeights[cause][caused] +
	// rNormalizedWeights[cause][caused]) / 2.0;
	// }

	/**
	 * @param facets
	 * @return [[cause1, caused1], ... Does not return biases
	 */
	protected static @NonNull Set<SimpleEdge> allEdges(final @NonNull Collection<Perspective> facets) {
		final HashSet<SimpleEdge> edges = new HashSet<>();
		for (final Perspective caused : facets) {
			assert caused != null;
			for (final Perspective cause : facets) {
				assert cause != null;
				if (caused != cause) {
					edges.add(SimpleEdge.getInstance(cause, caused, null));
				}
			}
		}
		return edges;
	}

	protected @NonNull int[][] edgesToIndexes(final @NonNull Set<SimpleEdge> _edges) {
		final int[][] intEdges = new int[_edges.size()][2];
		final int edgeIndex = 0;
		for (final SimpleEdge edge : _edges) {
			final int[] intEdge = new int[2];
			intEdge[0] = facetIndex(edge.causeP);
			intEdge[1] = facetIndex(edge.causedP);
			intEdges[edgeIndex] = intEdge;
		}
		return intEdges;
	}

	private Set<SimpleEdge> edges = null;
	private Set<SimpleEdge> edgesNbiases = null;

	/**
	 * @param includeBiases
	 * @return get Subset Of Edges Among facets (and possibly biases for
	 *         facets).
	 *
	 *         return value can be modified
	 */
	protected @NonNull Set<SimpleEdge> getEdges(final boolean includeBiases) {
		if (edges == null) {
			edges = new HashSet<>();
			for (final Iterator<int[]> it = getEdgeIterator(); it.hasNext();) {
				final int[] edge = it.next();
				edges.add(SimpleEdge.getInstance(getFacet(edge[0]), getFacet(edge[1]), this));
			}
			edgesNbiases = new HashSet<>(edges);
			for (final Perspective p : facets) {
				assert p != null;
				edgesNbiases.add(SimpleEdge.getInstance(p, p, this));
			}
		}
		final Set<SimpleEdge> result = includeBiases ? edgesNbiases : edges;
		assert result != null;
		return new HashSet<>(result);
	}

	/**
	 * @param includeBiases
	 * @return get Subset Of Edges Among perspectives.
	 */
	@NonNull
	public Set<SimpleEdge> getEdgesAmong(final Collection<Perspective> _facets, final boolean includeBiases) {
		final HashSet<SimpleEdge> result = new HashSet<>();
		for (final Iterator<int[]> it = getEdgeIterator(); it.hasNext();) {
			final int[] edge = it.next();
			final Perspective cause = getFacet(edge[0]);
			final Perspective caused = getFacet(edge[1]);
			if (_facets.contains(cause) && _facets.contains(caused)) {
				result.add(SimpleEdge.getInstance(cause, caused, this));
			}
		}
		if (includeBiases) {
			for (final Perspective p : _facets) {
				assert p != null;
				result.add(SimpleEdge.getInstance(p, p, this));
			}
		}
		return result;
	}

	static class SimpleEdge implements Comparable<SimpleEdge> {
		// causeP <= causedP
		final @NonNull Perspective causeP;
		final @NonNull Perspective causedP;
		final @Nullable GraphicalModel graphicalModel;

		static @NonNull SimpleEdge getInstance(final @NonNull Perspective _causeP, final @NonNull Perspective _causedP,
				@Nullable final GraphicalModel _graphicalModel) {
			SimpleEdge edge;
			if (_causeP.compareTo(_causedP) < 0) {
				edge = new SimpleEdge(_causeP, _causedP, _graphicalModel);
			} else {
				edge = new SimpleEdge(_causedP, _causeP, _graphicalModel);
			}
			return edge;
		}

		private SimpleEdge(final @NonNull Perspective _causeP, final @NonNull Perspective _causedP,
				@Nullable final GraphicalModel _graphicalModel) {
			causeP = _causeP;
			causedP = _causedP;
			graphicalModel = _graphicalModel;
		}

		@Override
		public String toString() {
			return UtilString
					.toString(this,
							causeP.nameNid() + (causeP == causedP ? " [BIAS]" : ", " + causedP.nameNid())
									+ (graphicalModel != null ? " weight="
											+ Explanation.formatDouble(graphicalModel.getWeight(causeP, causedP))
											: ""));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int hash = 1;
			hash = prime * hash + // ((causeP == null) ? 0 :
					causeP.hashCode();
			hash = prime * hash + // ((causedP == null) ? 0 :
					causedP.hashCode();
			return hash;
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
			final SimpleEdge other = (SimpleEdge) obj;
			if (!causeP.equals(other.causeP)) {
				return false;
			}
			if (!causedP.equals(other.causedP)) {
				return false;
			}
			return true;
		}

		@Override
		public int compareTo(final SimpleEdge o) {
			int result = causeP.compareTo(o.causeP);
			if (result == 0) {
				result = causedP.compareTo(o.causedP);
			}
			return result;
		}
	}

	protected static @NonNull Collection<SimpleEdge> getEdgesTo(final @NonNull Collection<Perspective> x,
			final @NonNull Perspective caused) {
		final Collection<SimpleEdge> edges = new ArrayList<>(x.size());
		for (final Perspective p : x) {
			assert p != null;
			edges.add(SimpleEdge.getInstance(p, caused, null));
		}
		return edges;
	}

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append(facets);
		buf.append(" nNonBiasEdges=").append(nNonBiasEdges);
		buf.append(" counts: ").append(UtilString.valueOfDeep(updateCountsFromProbDist()));
		buf.append(" edges: ").append(formatCollection(Util.nonNull(edgesNbiases)));
		return UtilString.toString(this, buf);
	}

	protected int nNonBiasEdges() {
		return nNonBiasEdges;
	}

	protected int getNumEdgesPlusBiases() {
		return nEdgesPlusBiases;
	}

	protected double bigWeightPenalty() {
		double penalty = 0.0;
		for (int edgeIndex = 0; edgeIndex < nEdgesPlusBiases; edgeIndex++) {
			final double excess = Math.abs(weights[edgeIndex]) - MAX_WEIGHT;
			if (excess > 0.0) {
				penalty += excess * excess;
			}
		}
		return penalty;
	}

	protected double[] bigWeightGradient() {
		final int nWeights = weights.length;
		final double[] gradients = new double[nWeights];
		for (int edgeIndex = 0; edgeIndex < nWeights; edgeIndex++) {
			final double w = weights[edgeIndex];
			final double excess = Math.abs(w) - MAX_WEIGHT;
			final double gradient = excess > 0.0 ? 2.0 * excess * Math.signum(w) : 0.0;
			assert !Double.isNaN(gradient) : excess + " " + w;
			gradients[edgeIndex] = gradient;
		}
		return gradients;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((edgesNbiases == null) ? 0 : edgesNbiases.hashCode());
		result = prime * result + Arrays.hashCode(logDistribution);
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final GraphicalModel other = (GraphicalModel) obj;
		if (edgesNbiases == null) {
			if (other.edgesNbiases != null) {
				return false;
			}
		} else if (!edgesNbiases.equals(other.edgesNbiases)) {
			return false;
		}
		if (!Arrays.equals(logDistribution, other.logDistribution)) {
			return false;
		}
		return true;
	}

}
