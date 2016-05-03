package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by dyma on 03.05.16.
 */
public class Tree<T> implements Iterable<T> {

    private final T root;
    private final List<Tree<T>> children;

    public Tree(final T root) {
        this(root, new ArrayList<>());
    }

    public Tree(final T root, final List<Tree<T>> children) {
        this.root = root;
        this.children = children;
    }

    public List<Tree<T>> getChildren() {
        return children;
    }

    public T getRoot() {
        return root;
    }

    public void addChildren(final List<Tree<T>> children) {
        this.children.addAll(children);
    }

    public void addChildren(final Tree<T> child) {
        children.add(child);
    }

    public Stream<T> stream() {
        return toList().stream();
    }

    @Override
    public Iterator<T> iterator() {
        final List<T> elements = toList();

        return new Iterator<T>() {

            @Override
            public boolean hasNext() {
                return elements.isEmpty();
            }

            @Override
            public T next() {
                return elements.remove(0);
            }
        };
    }

    public List<T> toList() {
        final List<T> elements = new ArrayList<>();
        toList(elements);
        return elements;
    }

    private void toList(final List<T> elements) {
        elements.add(root);
        children.forEach(child -> child.toList(elements));
    }
}
