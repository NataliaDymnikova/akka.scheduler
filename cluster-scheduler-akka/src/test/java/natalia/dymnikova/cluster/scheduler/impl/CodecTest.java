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

package natalia.dymnikova.cluster.scheduler.impl;

import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteFunction;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import org.junit.Test;
import rx.Observable.Operator;
import rx.Subscriber;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * 
 */
public class CodecTest {

    private Codec codec = new Codec();

    @Test
    @SuppressWarnings("Convert2Lambda")
    public void shouldPackInnerAnonSupplier() throws Exception {
        final byte[] body = codec.pack(new RemoteSupplier<String>() {
            @Override
            public String get() {
                return "test";
            }
        });

        assertThat(deserialize(body), instanceOf(Supplier.class));
    }

    @Test
    @SuppressWarnings("Convert2Lambda")
    public void unPackedInnerAnonSupplierShouldBeCallable() throws Exception {
        final Supplier supplier = codec.unpackSupplier(codec.pack(new RemoteSupplier<String>() {
            @Override
            public String get() {
                return "test";
            }
        }));

        assertThat(supplier.get(), is("test"));
    }

    @Test
    @SuppressWarnings("Convert2Lambda")
    public void shouldUnpackSupplierAsRemote() throws Exception {
        final Remote remote = codec.unpackRemote(codec.pack(new RemoteSupplier<String>() {
            @Override
            public String get() {
                return "test";
            }
        }));

        assertThat(remote, allOf(instanceOf(Supplier.class), instanceOf(RemoteSupplier.class)));
    }

    @Test
    public void shouldPackLambdaSupplier() throws Exception {
        final byte[] body = codec.pack((RemoteSupplier<String>) () -> "test");

        assertThat(deserialize(body), instanceOf(Supplier.class));
    }

    @Test
    public void unPackedLambdaSupplierShouldBeCallable() throws Exception {
        final Supplier supplier = codec.unpackSupplier(codec.pack((RemoteSupplier<String>) () -> "test"));

        assertThat(supplier.get(), is("test"));
    }

    @Test
    public void shouldPackStaticMethodReferenceConsumer() throws Exception {
        final byte[] body = codec.pack((RemoteSupplier<String>) CodecTest::staticSuipplyMethod);

        assertThat(deserialize(body), instanceOf(Supplier.class));
    }

    @Test(expected = UncheckedIOException.class)
    public void mayFailToPackMethodReferenceConsumer() throws Exception {
        final byte[] body = codec.pack((RemoteSupplier<String>) this::supplyMethod);

        assertThat(deserialize(body), instanceOf(Consumer.class));
    }

    @Test
    @SuppressWarnings("Convert2Lambda")
    public void shouldPackInnerAnonFunction() throws Exception {
        final byte[] body = codec.pack(new RemoteFunction<String, String>() {
            @Override
            public String apply(final String s) {
                return s;
            }
        });

        assertThat(deserialize(body), instanceOf(Function.class));
    }

    @Test
    public void shouldPackLambdaFunction() throws Exception {
        final byte[] body = codec.pack((RemoteFunction<String, String>) s -> s);

        assertThat(deserialize(body), instanceOf(Function.class));
    }

    @Test
    @SuppressWarnings("Convert2Lambda")
    public void shouldPackInnerAnonOperator() throws Exception {
        final byte[] body = codec.pack(new RemoteOperator<String, String>() {
            @Override
            public Subscriber<? super String> call(final Subscriber<? super String> subscriber) {
                return subscriber;
            }
        });

        assertThat(deserialize(body), instanceOf(Operator.class));
    }

    @Test
    public void shouldPackLambdaOperator() throws Exception {
        final byte[] body = codec.pack((RemoteOperator<String, String>) subscriber -> subscriber);

        assertThat(deserialize(body), instanceOf(Operator.class));
    }

    private static String staticSuipplyMethod() {
        return "aaa";
    }

    private String supplyMethod() {
        return "aaaa";
    }

    private static Object deserialize(final byte[] body) throws Exception {
        try (final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(body))) {
            return in.readObject();
        }
    }
}