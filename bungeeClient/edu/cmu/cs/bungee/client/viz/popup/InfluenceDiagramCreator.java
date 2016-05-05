package edu.cmu.cs.bungee.client.viz.popup;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.explanation.Explanation;
import edu.cmu.cs.bungee.client.query.explanation.ExplanationTask;
import edu.cmu.cs.bungee.client.query.explanation.NonAlchemyExplanation;
import edu.cmu.cs.bungee.client.query.query.Query;
import edu.cmu.cs.bungee.javaExtensions.threads.UpdateThread;

/**
 * This UpdateThread computes an caches an Explanation, and then queues
 * popup.showInfluenceDiagram().
 */
public class InfluenceDiagramCreator extends UpdateThread<ExplanationTask> { // NO_UCD
	// (use
	// default)

	final @NonNull PopupSummary popup;
	private final int queryVersion;

	InfluenceDiagramCreator(final @NonNull PopupSummary _popup) {
		super("InfluenceDiagramCreator", -4);
		popup = _popup;
		queryVersion = query().version();
		setExitOnError(popup.art.getExitOnError());
	}

	@Override
	protected void process(final @NonNull ExplanationTask explanationTask) {
		if (explanationTask.isTaskCurrent()) {
			Explanation explanation = popup.lookupExplanation(explanationTask.primaryFacets());
			if (explanation == null) {
				try {
					explanation = NonAlchemyExplanation.getExplanation(explanationTask);
					if (explanation != null) {
						popup.addExplanation(explanationTask, explanation);
					}
				} catch (final AssertionError e) {
					if (explanationTask.isTaskCurrent()) {
						throw (e);
						// Otherwise, isQueryVersionCurrent will call exit
					}
				}
			}
			if (explanation != null && explanationTask.isTaskCurrent()) {
				query().queryInvokeLater(getInfluenceDiagramShower(explanation));
			}
		}
	}

	public boolean isCurrent(final @NonNull Perspective popupFacet) {
		final boolean isCurrent = (popupFacet == popup.facet) && query().isQueryVersionCurrent(queryVersion)
				&& !getExited();
		assert isQueueEmpty() : this;
		// if (!isCurrent) {
		// super.exit();
		// }
		return isCurrent;
	}

	private @NonNull Query query() {
		return popup.query();
	}

	private @NonNull Runnable getInfluenceDiagramShower(final @NonNull Explanation explanation) {
		return new Runnable() {

			@Override
			public void run() {
				try {
					popup.showInfluenceDiagram(explanation);
				} catch (final Throwable e) {
					popup.art.stopReplayer();
					throw (e);
				}
			}
		};
	}

	@Override
	public boolean getExitOnError(final @NonNull ExplanationTask explanationTask) {
		return super.getExitOnError(explanationTask) && explanationTask.isTaskCurrent();
	}

	@Override
	public synchronized void exit() {
		popup.art.stopReplayer();
		super.exit();
	}
}