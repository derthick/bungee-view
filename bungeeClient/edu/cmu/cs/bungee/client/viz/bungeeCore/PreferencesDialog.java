package edu.cmu.cs.bungee.client.viz.bungeeCore;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

class PreferencesDialog extends JPanel implements ActionListener {

	protected static final long serialVersionUID = 1L;

	private final Bungee art;

	private JCheckBox popups;
	private JCheckBox openClose;
	private JLabel textFieldLabel;
	private JSpinner fontSpinner;
	private JCheckBox arrows;
	private JCheckBox boundaries;
	private JCheckBox tagLists;
	private JCheckBox zoom;
	private JCheckBox checkboxes;
	private JCheckBox shortcuts;
	private JCheckBox brushing;
	private JCheckBox pvalues;
	private JCheckBox medians;
	private JCheckBox sortMenus;
	private JCheckBox editing;
	private JCheckBox debugGraph;
	private JCheckBox graph;

	private JSpinner columnsSpinner;

	private final Window window;

	PreferencesDialog(final Bungee _art, final Window _window) {
		art = _art;
		window = _window;
		final Preferences preferences = art.getPreferences();
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

		final JPanel leftPane = new JPanel(new BorderLayout());
		leftPane.add(generalPreferencesPanel(preferences), BorderLayout.PAGE_START);
		leftPane.add(expertPreferencesPanel(preferences), BorderLayout.CENTER);

		add(leftPane, BorderLayout.LINE_START);

		add(superExpertPreferencesPanel(preferences));

		add(okCancelPanel());
	}

	private JPanel generalPreferencesPanel(final Preferences preferences) {
		final JPanel generalPreferencesPane = new JPanel(new GridLayout(0, 1));
		generalPreferencesPane.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("General Preferences"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		final JPanel fontPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		final int fontSize = art.getFontSize();
		final int maxFontSizeThatFitsInWindow = Math.max(fontSize, art.maxFontSizeThatFitsInWindow());
		fontSpinner = new JSpinner(
				new SpinnerNumberModel(fontSize, art.minLegibleFontSize(), maxFontSizeThatFitsInWindow, 1));
		fontPanel.add(fontSpinner);
		textFieldLabel = new JLabel("Font size");
		fontPanel.add(textFieldLabel);
		generalPreferencesPane.add(fontPanel);

		final JPanel columnsPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		final int maxGridColumns = art.getGrid().maxColumnsIfScrollbar();
		columnsSpinner = new JSpinner(nColumnsSpinnerModel(maxGridColumns));
		setEditorWidth(columnsSpinner, "Bungee's choice");
		final int desiredColumns = Math.min(preferences.nColumns, maxGridColumns);
		// System.out
		// .println("PreferencesDialog.generalPreferencesPanel desiredColumns="
		// + desiredColumns + " maxGridColumns=" + maxGridColumns);
		columnsSpinner.setValue(desiredColumns > 0 ? Integer.valueOf(desiredColumns) : "Bungee's choice");
		columnsPanel.add(columnsSpinner);
		textFieldLabel = new JLabel("Number of columns in Matches pane");
		columnsPanel.add(textFieldLabel);
		generalPreferencesPane.add(columnsPanel);

		popups = new JCheckBox("Show popups on tag mouse-over (otherwise show in header)");
		popups.setSelected(preferences.popups);
		generalPreferencesPane.add(popups);

		openClose = new JCheckBox("Show +/- buttons to open/close tag hierarchies in Selected Result pane");
		openClose.setSelected(preferences.openClose);
		generalPreferencesPane.add(openClose);

		return generalPreferencesPane;
	}

	private JPanel expertPreferencesPanel(final Preferences preferences) {
		final JPanel expertFeaturesPane = new JPanel(new GridLayout(0, 1));
		expertFeaturesPane.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Expert features"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		final JLabel multipleSelectionLabel = new JLabel("Allow multiple selection and negation by:");
		expertFeaturesPane.add(multipleSelectionLabel);

		checkboxes = new JCheckBox("using checkboxes");
		checkboxes.setSelected(preferences.checkboxes);
		shortcuts = new JCheckBox("using control and shift keys");
		shortcuts.setSelected(preferences.shortcuts);
		final JPanel multipleSelectionCheckboxesPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		final JPanel multipleSelectionShortcutPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		multipleSelectionCheckboxesPanel.add(Box.createRigidArea(new Dimension(25, 1)));
		multipleSelectionCheckboxesPanel.add(checkboxes);
		multipleSelectionShortcutPanel.add(Box.createRigidArea(new Dimension(25, 1)));
		multipleSelectionShortcutPanel.add(shortcuts);
		expertFeaturesPane.add(multipleSelectionCheckboxesPanel);
		expertFeaturesPane.add(multipleSelectionShortcutPanel);

		brushing = new JCheckBox(
				"Use linked highlighting to show which matches have a tag, and which tags a match has");
		brushing.setSelected(preferences.brushing);
		expertFeaturesPane.add(brushing);

		pvalues = new JCheckBox("Show the p-value for the difference between the percentage of items "
				+ "that satisfy the filters for items with the tag "
				+ "and the percentage for items with another tag in the same category");
		pvalues.setSelected(preferences.pvalues);
		expertFeaturesPane.add(pvalues);

		medians = new JCheckBox(
				"For tag categories that have a natural order, draw an arrow that compares the median tag for all items "
						+ "with the median tag for filtered items");
		medians.setSelected(preferences.medians);
		expertFeaturesPane.add(medians);

		sortMenus = new JCheckBox("Show a menu for sorting matches by a tag category");
		sortMenus.setSelected(preferences.sortMenus);
		expertFeaturesPane.add(sortMenus);

		arrows = new JCheckBox("Use arrow keys to navigate through Tags and Matches");
		arrows.setSelected(preferences.arrows);
		expertFeaturesPane.add(arrows);

		boundaries = new JCheckBox(
				"Show draggable boundaries between Tag Wall|Top Tags|Matching|Selected panes on mouse over");
		boundaries.setSelected(preferences.boundaries);
		expertFeaturesPane.add(boundaries);

		tagLists = new JCheckBox(
				"Allow clicking on the open tag category label, to show a scrolling list of all tags in the category");
		tagLists.setSelected(preferences.tagLists);
		expertFeaturesPane.add(tagLists);

		zoom = new JCheckBox(
				"Show alphabetic prefixes above tag bars, and allow zooming by clicking, typing, or dragging");
		zoom.setSelected(preferences.zoom);
		expertFeaturesPane.add(zoom);

		graph = new JCheckBox("Show Influence Diagrams with popup tag details");
		graph.setSelected(preferences.graph);
		expertFeaturesPane.add(graph);

		final JPanel shortcutPanel = new JPanel();
		final JButton expert = new JButton("All expert features");
		expert.setActionCommand("expert");
		expert.addActionListener(this);
		shortcutPanel.add(expert);

		final JButton beginner = new JButton("No expert features");
		beginner.setActionCommand("beginner");
		beginner.addActionListener(this);
		shortcutPanel.add(beginner);

		expertFeaturesPane.add(shortcutPanel);
		return expertFeaturesPane;
	}

	private JPanel okCancelPanel() {

		final JButton ok = new JButton("OK");
		ok.setActionCommand("OK");
		ok.addActionListener(this);

		final JButton cancel = new JButton("Cancel");
		cancel.setActionCommand("Cancel");
		cancel.addActionListener(this);

		final JPanel okCancelPanel = new JPanel(new FlowLayout());
		okCancelPanel.add(ok);
		okCancelPanel.add(cancel);
		return okCancelPanel;

	}

	private JPanel superExpertPreferencesPanel(final Preferences preferences) {

		final JPanel superExpertFeaturesPane = new JPanel(new GridLayout(0, 1));
		superExpertFeaturesPane.setBorder(
				BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Do not try this at home"),
						BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		if (art.getQuery().isEditable()) {
			editing = new JCheckBox("Allow updating the database by clicking with the middle mouse button");
			editing.setSelected(preferences.editing);
			superExpertFeaturesPane.add(editing);
		}
		debugGraph = new JCheckBox("Show debugging information on Influence Diagrams");
		debugGraph.setSelected(preferences.debugGraph);
		superExpertFeaturesPane.add(debugGraph);
		return superExpertFeaturesPane;
	}

	private static SpinnerListModel nColumnsSpinnerModel(final int maxCols) {
		final List<Object> items = new LinkedList<>();
		items.add("Bungee's choice");
		for (int i = 1; i <= maxCols; i++) {
			items.add(Integer.valueOf(i));
		}
		return new SpinnerListModel(items);
	}

	private static void setEditorWidth(final JSpinner spinner, final String value) {
		final JFormattedTextField ftf = getTextField(spinner);
		if (ftf != null) {
			ftf.setColumns((int) (0.6 * value.length()));
		}
	}

	private static JFormattedTextField getTextField(final JSpinner spinner) {
		final JComponent editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor) {
			return ((JSpinner.DefaultEditor) editor).getTextField();
		} else {
			System.err.println("Warning: Unexpected editor type: " + spinner.getEditor().getClass()
					+ " isn't a descendant of DefaultEditor");
			return null;
		}
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		if ("expert".equals(e.getActionCommand())) {
			setExpertFeatures(true);
		} else if ("beginner".equals(e.getActionCommand())) {
			setExpertFeatures(false);
		} else if ("OK".equals(e.getActionCommand())) {
			art.setPreferences(getPreferences());
			window.dispose();
		} else if ("Cancel".equals(e.getActionCommand())) {
			window.dispose();
		}
	}

	private @NonNull Preferences getPreferences() {
		final StringBuilder buf = new StringBuilder();
		buf.append("fontSize=").append(((Integer) fontSpinner.getValue()).intValue());
		final Object nColumnsValue = columnsSpinner.getValue();
		if (nColumnsValue instanceof Integer) {
			buf.append(",nColumns=").append(((Integer) nColumnsValue).intValue());
		}
		if (arrows.isSelected()) {
			buf.append(",arrows");
		}
		if (boundaries.isSelected()) {
			buf.append(",boundaries");
		}
		if (brushing.isSelected()) {
			buf.append(",brushing");
		}
		if (checkboxes.isSelected()) {
			buf.append(",checkboxes");
		}
		if (medians.isSelected()) {
			buf.append(",medians");
		}
		if (openClose.isSelected()) {
			buf.append(",openClose");
		}
		if (popups.isSelected()) {
			buf.append(",popups");
		}
		if (pvalues.isSelected()) {
			buf.append(",pvalues");
		}
		if (shortcuts.isSelected()) {
			buf.append(",shortcuts");
		}
		if (sortMenus.isSelected()) {
			buf.append(",sortMenus");
		}
		if (tagLists.isSelected()) {
			buf.append(",tagLists");
		}
		if (zoom.isSelected()) {
			buf.append(",zoom");
		}
		if (editing != null && editing.isSelected()) {
			buf.append(",editing");
		}
		if (debugGraph.isSelected()) {
			buf.append(",debugGraph");
		}
		if (graph.isSelected()) {
			buf.append(",graph");
		}
		// System.out.println("kk " + new Preferences(null, buf.toString(),
		// true));
		return art.getPreferences(null, UtilString.bufToString(buf), true);
	}

	private void setExpertFeatures(final boolean state) {
		arrows.setSelected(state);
		boundaries.setSelected(state);
		tagLists.setSelected(state);
		zoom.setSelected(state);
		checkboxes.setSelected(state);
		shortcuts.setSelected(state);
		brushing.setSelected(state);
		pvalues.setSelected(state);
		medians.setSelected(state);
		sortMenus.setSelected(state);
		graph.setSelected(state);
	}

	/**
	 * Create the GUI and show it. For thread safety, this method should be
	 * invoked from the event dispatch thread.
	 */
	static void createAndShowGUI(final Bungee art) { // NO_UCD (unused code)
		art.createAndShowGUI();
	}

}
