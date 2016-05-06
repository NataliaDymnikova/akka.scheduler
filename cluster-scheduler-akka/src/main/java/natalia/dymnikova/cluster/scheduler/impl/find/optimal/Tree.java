// Copyright (c) 2016 Natalia Dymnikova
// Available via the MIT license
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
// TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
// CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
// OR OTHER DEALINGS IN THE SOFTWARE.

package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import java.util.ArrayList;
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
