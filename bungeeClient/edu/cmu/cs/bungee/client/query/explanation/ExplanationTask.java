package edu.cmu.cs.bungee.client.query.explanation;

import java.util.SortedSet;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import jdk.nashorn.internal.ir.annotations.Immutable;

@Immutable
public interface ExplanationTask {

	public boolean isTaskCurrent();

	public @NonNull SortedSet<Perspective> primaryFacets();

	public @Immutable @NonNull ExplanationTask getModifiedInstance(@NonNull SortedSet<Perspective> facets);

}
