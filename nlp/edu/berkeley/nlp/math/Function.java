package edu.berkeley.nlp.math;

/**
 */
interface Function {
	int dimension();

	double valueAt(double[] x);
}
