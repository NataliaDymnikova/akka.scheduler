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

package natalia.dymnikova.test;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import rx.Observable;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;

/**
 */
public class MoreMatchers {
    public static Matcher<? super Path> dirExists() {
        return new BaseMatcher<Path>() {
            @Override
            public boolean matches(final Object item) {
                return exists((Path) item) && isDirectory((Path) item);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                description.appendText("directory ").appendText(String.valueOf(item)).appendText(" does not exist");
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("directory to exist");
            }
        };
    }

    public static <T> Matcher<List<T>> listWithSize(final int size) {
        return (Matcher<List<T>>) (Object) Matchers.iterableWithSize(size);
    }

    public static <T> Matcher<List<T>> listContains(final T... items) {
        return (Matcher<List<T>>) (Object) Matchers.contains(items);
    }
}
