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

import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.scheduler.Remote;
import natalia.dymnikova.cluster.scheduler.RemoteFunction;
import natalia.dymnikova.cluster.scheduler.RemoteOperator;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.akka.Flow;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorImpl;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithFunction;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.RemoteOperatorWithSubscriber;
import natalia.dymnikova.util.AutowireHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Observable.Operator;

import java.io.*;
import java.lang.reflect.Field;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.reflect.Modifier.isStatic;
import static natalia.dymnikova.util.MoreThrowables.propagateUnchecked;
import static natalia.dymnikova.util.MoreThrowables.unchecked;

/**
 * 
 */
@Component
public class Codec {

    @Autowired
    private AutowireHelper autowireHelper;

    public <T extends Serializable> byte[] pack(final RemoteSupplier<T> f) {
        return pack((Remote) f);
    }

    public <T extends Serializable> byte[] pack(final Remote f) {
        return packObject(sanitize(f));
    }

    public <T extends Serializable> Supplier<Observable<T>> unpackSupplier(final byte[] b) {
        return (Supplier<Observable<T>>) unpack(b);
    }

    public Serializable unpack(final byte[] b) {
        return unpack(new ByteArrayInputStream(b));
    }

    public Serializable unpack(final InputStream inputStream) {
        try {
            try (final ObjectInputStream in = new ObjectInputStream(inputStream)){
                return (Serializable) in.readObject();
            }
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        } catch (final ClassNotFoundException e) {
            throw unchecked(e);
        }
    }

    public <I extends Serializable, O extends Serializable> byte[] pack(final RemoteFunction<I, O> f) {
        return pack((Remote) f);
    }

    public <I extends Serializable, O extends Serializable> byte[] pack(final RemoteOperator<I, O> f) {
        return pack((Remote) f);
    }

    public <I extends Serializable, O extends Serializable> Operator<I, O> unpackOperator(final byte[] b) {
        return (Operator<I, O>) unpack(b);
    }

    public Remote unpackRemote(final byte[] b) {
        return (Remote) unpack(b);
    }

    public Remote unpackAndAutowire(final ByteString operatorBytes) {
        final Remote operator = unpackRemote(operatorBytes.toByteArray());

        if (operator instanceof RemoteOperatorWithFunction) {
            return new RemoteOperatorWithFunction<>(autowireHelper.autowire(((RemoteOperatorWithFunction) operator).delegate));
        } else if (operator instanceof RemoteOperatorWithSubscriber) {
            return new RemoteOperatorWithSubscriber<>(autowireHelper.autowire(((RemoteOperatorWithSubscriber) operator).delegate));
        } else {
            return autowireHelper.autowire(operator);
        }
    }

    public byte[] packObject(final Object funct) {
        try {
            // TODO make sense to reuse Codec and inner ObjectOutputStream at least for a single RemoteObservable
            final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
            final ObjectOutputStream os = new ObjectOutputStream(arrayOutputStream);
            os.writeObject(funct);
            os.close();
            return arrayOutputStream.toByteArray();
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    private static Object sanitize(final Object f) {
        if (f instanceof RemoteOperatorImpl) {
            doSanitize(((RemoteOperatorImpl) f).delegate);
            return f;
        } else {
            return doSanitize(f);
        }
    }

    private static Object doSanitize(final Object f) {
        final Class<?> aClass = f.getClass();
        if (!isStatic(aClass.getModifiers())) {
            final Class<?> enclosingClass = aClass.getEnclosingClass();
            if (enclosingClass != null) {
                for (final Field field : aClass.getDeclaredFields()) {
                    if (enclosingClass.equals(field.getType())) {
                        field.setAccessible(true);
                        try {
                            field.set(f, null);
                        } catch (final IllegalAccessException e) {
                            throw unchecked(e);
                        }
                    }
                }
            }
        }
        return f;
    }

    public <T extends Serializable> RemoteSubscriber<T> unpackSubscriber(final byte[] b) {
        return (RemoteSubscriber<T>) unpack(b);
    }
}
