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

import com.google.protobuf.ByteString;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import static com.google.protobuf.ByteString.copyFrom;
import static natalia.dymnikova.util.MoreByteStrings.wrap;

/**
 * 
 */
public class ByteStringInterner {

    public static final double putThreshold = .66;

    private final Map<ByteString, String> strings = new HashMap<>(1024);
    private final Random random = new Random();

    public String intern(final ByteString bs) {
        String s = strings.get(bs);
        if (s == null) {
            s = bs.toStringUtf8();
            if (random.nextFloat() > putThreshold) {
                put(bs, s);
            }
        }
        return s;
    }

    public String intern(final byte[] bytes, final int offset, final int count) {
        final ByteString bs = wrap(bytes, offset, count);
        String s = strings.get(bs);
        if (s == null) {
            s = bs.toStringUtf8();
            if (random.nextFloat() > putThreshold) {
                put(copyFrom(bytes, offset, count), s);
            }
        }
        return s;
    }

    private void put(final ByteString bs, final String s) {
        strings.put(bs, s);
    }

    public Set<Entry<ByteString, String>> internedEntries() {
        return strings.entrySet();
    }
}
