package edu.cmu.cs.bungee.javaExtensions.graph;

import org.eclipse.jdt.annotation.NonNull;

import edu.cmu.cs.bungee.javaExtensions.UtilString;

public class Node<T extends Comparable<? super T>> implements Comparable<Node<T>> {

	private @NonNull String label;

	private int centerX;
	private int centerY;
	public final @NonNull T object;

	Node(final @NonNull T _object, final @NonNull String _label) {
		assert _object != null;
		label = _label;
		object = _object;
	}

	public int getCenterX() {
		return centerX;
	}

	public void setCenterX(final int center_X) {
		centerX = center_X;
	}

	public int getCenterY() {
		return centerY;
	}

	public void setCenterY(final int _centerY) {
		centerY = _centerY;
	}

	public void setLabel(final @NonNull String _label) {
		label = _label;
	}

	public @NonNull String getLabel() {
		return label;
	}

	// TODO Remove unused code found by UCDetector
	// Rectangle2D getRectangle(final Font font, final FontRenderContext frc) {
	// final Rectangle2D rect = font.getStringBounds(label, frc);
	// final double w = rect.getWidth();
	// final double h = rect.getHeight();
	// return new Rectangle2D.Double(centerX - w / 2, centerY - h / 2, w, h);
	// }

	@Override
	public String toString() {
		return UtilString.toString(this, label);
	}

	@Override
	public int compareTo(final Node<T> arg0) {
		if (arg0 == this) {
			return 0;
		}
		int result = 0;
		// if (object instanceof Comparable && argObj instanceof Comparable)
		result = object.compareTo(arg0.object);
		if (result == 0) {
			result = label.compareTo(arg0.label);
		}
		assert result != 0 : "What now?";
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + label.hashCode();
		result = prime * result + // ((object == null) ? 0 :
				object.hashCode();
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
		final Node<?> other = (Node<?>) obj;
		if (!label.equals(other.label)) {
			return false;
		}
		// if (object == null) {
		// if (other.object != null) {
		// return false;
		// }
		// } else
		if (!object.equals(other.object)) {
			return false;
		}
		return true;
	}

}
