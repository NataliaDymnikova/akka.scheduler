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

package natalia.dymnikova.util;

import org.junit.Test;

import static natalia.dymnikova.util.MoreThrowables.unchecked;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * 
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class MoreThrowablesTest {

    @Test
    public void shouldFormatMessageWithNoParameters() throws Exception {
        assertThat(
            unchecked("Message without parameters", "a", "b", "c", "d", "e").getMessage(),
            is("Message without parameters")
        );
    }

    @Test
    public void shouldFormatMessageWithOneParameterAtTheBeginning() throws Exception {
        assertThat(
            unchecked("{} Message with one parameter", "a", "b", "c", "d", "e").getMessage(),
            is("a Message with one parameter")
        );
    }

    @Test
    public void shouldFormatMessageWithOneParameterAtTheEnd() throws Exception {
        assertThat(
            unchecked("Message with one parameter {}", "a", "b", "c", "d", "e").getMessage(),
            is("Message with one parameter a")
        );
    }

    @Test
    public void shouldFormatMessageWithTwoParameterAtTheBeginningAndEnd() throws Exception {
        assertThat(
            unchecked("{} Message with two parameters {}", "a", "b", "c", "d", "e").getMessage(),
            is("a Message with two parameters b")
        );
    }

    @Test
    public void shouldFormatMessageWithManyParameters() throws Exception {
        assertThat(
            unchecked("{} Message {} with {} many {} parameters {}", "a", "b", "c", "d", "e").getMessage(),
            is("a Message b with c many d parameters e")
        );
    }

    @Test
    public void shouldLeavePlaceholderIfLackingParameters() throws Exception {
        assertThat(
            unchecked("{} Message {} without {} parameters {}").getMessage(),
            is("{} Message {} without {} parameters {}")
        );
    }
}