package edu.cmu.cs.bungee.client.query.explanation;

import org.eclipse.jdt.annotation.NonNull;

interface GreedySubsetResult {
	void maybePrintToFile(@NonNull GreedySubsetResult nullModel);

	void printGraph();

	void setEval(double eval);
}
