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

package natalia.dymnikova.util.sequence;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 
 * based on https://github.com/jakobk/sequence-streams
 */
public class Sequence implements Spliterator<Element> {

    public static Stream<Element> sequence(long start, long end, int step) {
        return StreamSupport.stream(new Sequence(start, end, step), false);
    }

    private long start;
    private final long end;
    private final int step;

    public Sequence(long start, long end) {
        this(start, end, 1);
    }

    public Sequence(long start, long end, int step) {
        this.start = start;
        this.end = end;
        this.step = step;
    }

    @Override
    public boolean tryAdvance(Consumer<? super Element> action) {
        if (start < end) {
            action.accept(new Element(start, Math.min(start + step, end)));
            start += step;
            return true;
        }
        return false;
    }

    @Override
    public Spliterator<Element> trySplit() {
        if (start >= end) {
            return null;
        }
        final Sequence prefix = new Sequence(
            start, start + (((end - start) / step / 2) * step) - 1, step
        );
        this.start = prefix.end + 1;
        return prefix;
    }

    @Override
    public long estimateSize() {
        return (end - start) / step;
    }

    @Override
    public int characteristics() {
        return ORDERED | DISTINCT | SORTED | IMMUTABLE | NONNULL | SIZED;
    }

    @Override
    public Comparator<? super Element> getComparator() {
        return Element::compare;
    }
}
