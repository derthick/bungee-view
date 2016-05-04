package edu.cmu.cs.bungee.javaExtensions;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A generic tree structure, where every node has an associated object, the
 * treeObject
 */
public class GenericTree<T> extends AbstractCollection<T> implements Serializable {

	protected static final long serialVersionUID = 1L;

	private final @Nullable String objectDesc;

	private final @NonNull T treeObject;

	/**
	 * @return the object associated with this subtree
	 */
	public @NonNull T getTreeObject() {
		return treeObject;
	}

	protected @NonNull List<GenericTree<T>> children = UtilArray.EMPTY_LIST;
	// protected final @NonNull List<GenericTree<T>> unmodifiableChildren =
	// UtilArray.unmodifiableList(children);

	/**
	 * @param _treeObject
	 *            _treeObject the object associated with this node
	 * @param _desc
	 *            a description of this subtree
	 */
	public GenericTree(final @NonNull T _treeObject, final @Nullable String _desc) {
		treeObject = _treeObject;
		objectDesc = _desc;
	}

	// public T treeObject() {
	// return treeObject;
	// }

	// /**
	// * @return the subtrees under this node
	// */
	// public @NonNull List<GenericTree<T>> getChildren() {
	// return unmodifiableChildren;
	// }

	/**
	 * @return the number of child subtrees
	 */
	public int nChildren() {
		return children.size();
	}

	/**
	 * @return the description of this node, or null
	 */
	public @Nullable String description() {
		return objectDesc;
	}

	// TODO Remove unused code found by UCDetector
	// /**
	// * @param treeObject1
	// * the object to search for
	// * @return Is treeObject1 the treeObject() of this or one of its
	// * descendents.
	// */
	// public boolean isMember(final T treeObject1) {
	// boolean result = treeObject() == treeObject1;
	// for (final Iterator<GenericTree<T>> it = children.iterator(); it
	// .hasNext() && !result;) {
	// final GenericTree<T> child = it.next();
	// result = child.isMember(treeObject1);
	// }
	// return result;
	// }

	// TODO Remove unused code found by UCDetector
	// /**
	// * @param treeObjects
	// * set of objects to search for
	// * @return Is one of treeObjects the treeObject() of this or one of its
	// * descendents.
	// */
	// public boolean isMember(final Set<T> treeObjects) {
	// boolean result = treeObjects.contains(treeObject());
	//
	// for (final Iterator<GenericTree<T>> it = children.iterator(); it
	// .hasNext() && !result;) {
	// final GenericTree<T> child = it.next();
	// result = child.isMember(treeObjects);
	// }
	// return result;
	// }

	// /**
	// * // * @param index // * 0-based index of child to delete //
	// */
	// public void removeChild(final int index) {
	// children.remove(index);
	// // System.out.println("removeChild " + this);
	// }
	//
	// /**
	// * @param treeObject1
	// * child to delete
	// * @return was anything deleted?
	// */
	// public boolean removeChild(final T treeObject1) {
	// // System.out.println("removeChild");
	// return children.remove(treeObject1);
	// }

	/**
	 * @param child
	 *            subtree to add (after all current children)
	 * @return was anything added?
	 */
	public boolean addChild(final @NonNull GenericTree<T> child) {
		return ensureChildren().add(child);
	}

	private @NonNull List<GenericTree<T>> ensureChildren() {
		if (children == Collections.EMPTY_LIST) {
			children = new ArrayList<>();
		}
		assert children != null;
		return children;
	}

	@Override
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		toStringInternal(buf, "\n");
		return UtilString.toString(this, buf);
	}

	private void toStringInternal(final @NonNull StringBuilder buf, final @NonNull String indent) {
		// if (getTreeObject() != null) {
		buf.append(indent).append(getTreeObject());
		// } else {
		// buf.append(indent).append("[Generic Tree Root]");
		// }

		for (final GenericTree<T> genericTree : children) {
			genericTree.toStringInternal(buf, indent + " ");
		}
	}

	/**
	 * @return a ListIterator over children
	 */
	public @NonNull Iterator<GenericTree<T>> childIterator() {
		final Iterator<GenericTree<T>> result = children.iterator();
		assert result != null;
		return result;
	}

	@Override
	public @NonNull Iterator<T> iterator() {
		return new TreeIterator<>(this);
	}

	@Override
	public int size() {
		int result = 1;
		for (final GenericTree<T> child : children) {
			result += child.size();
		}
		return result;
	}

	public int maxDepth() {
		int result = 0;
		for (final GenericTree<T> child : children) {
			result = Math.max(result, child.size());
		}
		return result + 1;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + children.hashCode();
		result = prime * result + ((objectDesc != null) ? objectDesc.hashCode() : 0);
		result = prime * result + treeObject.hashCode();
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
		final GenericTree<?> other = (GenericTree<?>) obj;
		// if (children == null) {
		// if (other.children != null) {
		// return false;
		// }
		// } else
		if (!children.equals(other.children)) {
			return false;
		}
		if (objectDesc != null) {
			if (!objectDesc.equals(other.objectDesc)) {
				return false;
			}
		} else {
			if (other.objectDesc != null) {
				return false;
			}
		}
		// if (treeObject == null) {
		// if (other.treeObject != null) {
		// return false;
		// }
		// } else
		if (!treeObject.equals(other.treeObject)) {
			return false;
		}
		return true;
	}

}

/**
 * Pre-order, depth-first
 *
 * @param <S>
 */
class TreeIterator<S> implements Iterator<S> {
	private final @NonNull GenericTree<S> root;

	private @NonNull List<GenericTree<S>> currentPath = new LinkedList<>();

	TreeIterator(final @NonNull GenericTree<S> tree) {
		super();
		root = tree;
	}

	@Override
	public boolean hasNext() {
		if (currentPath.isEmpty()) {
			return true;
		}
		GenericTree<S> child = null;
		for (final GenericTree<S> parent : currentPath) {
			assert parent != null;
			if (nextChild(parent, child) != null) {
				return true;
			}
			child = parent;
		}
		return false;
	}

	@Override
	public @NonNull S next() {
		if (currentPath.isEmpty()) {
			currentPath.add(0, root);
			return root.getTreeObject();
		}
		GenericTree<S> child = null;
		GenericTree<S> nextChild = null;
		GenericTree<S> nextChildParent = null;
		for (final GenericTree<S> parent : currentPath) {
			assert parent != null;
			nextChild = nextChild(parent, child);
			if (nextChild != null) {
				nextChildParent = parent;
				break;
			}
			child = parent;
		}
		currentPath = UtilArray.tailList(currentPath, nextChildParent);
		assert nextChild != null;
		currentPath.add(0, nextChild);
		return nextChild.getTreeObject();
	}

	private @Nullable GenericTree<S> nextChild(final @NonNull GenericTree<S> parent,
			final @Nullable GenericTree<S> child) {
		final List<GenericTree<S>> children = parent.children;
		final int nextChildIndex = child == null ? 0 : children.indexOf(child) + 1;
		final GenericTree<S> nextChild = parent.nChildren() > nextChildIndex ? children.get(nextChildIndex) : null;
		return nextChild;
	}

}