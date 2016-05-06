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

package natalia.dymnikova.akka.scheduler;

import akka.cluster.Cluster;
import akka.cluster.Member;
import natalia.dymnikova.cluster.scheduler.RemoteFunction;
import natalia.dymnikova.cluster.scheduler.RemoteSubscriber;
import natalia.dymnikova.cluster.scheduler.RemoteSupplier;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedScheduler;
import natalia.dymnikova.configuration.ConfigValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import rx.Observable;
import rx.Producer;
import scala.collection.immutable.SortedSet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static natalia.dymnikova.test.Wait.waitFor;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static rx.Observable.from;

/**
 *
 */
public class FlowTest {
    private static final Logger log = LoggerFactory.getLogger(FlowTest.class);

    @Rule
    public IntegrationTestCtxRule integrationTestCtx = new IntegrationTestCtxRule(
            this
    );

    @Autowired
    private AkkaBackedScheduler scheduler;

    @Autowired
    private TestConsumerAdapter consumerAdapter;

    @Autowired
    private FutureLongComponent futureCount;

    @Autowired
    private Cluster cluster;

    @ConfigValue("natalia-dymnikova.scheduler.subscriber.on-start-pre-fetch-elements")
    private long totalRequestedElements = 11;

    @Before
    public void setUp() throws Exception {
        cluster.join(cluster.selfAddress());
        waitFor(
                () -> cluster.readView().members(),
                ((Predicate<SortedSet<Member>>) SortedSet<Member>::isEmpty).negate(),
                ofSeconds(15), ofSeconds(1)
        );
    }

    @Test
    public void shouldCheckWork() throws Exception {
        final RemoteFunction<String, String> function = new TestStageFunction();

        scheduler.createObservable(new TestSupplier())
                .map(function)
                .map(function)
                .subscribe(new TestConsumer())
                .get(15, SECONDS);

        assertThat(
                consumerAdapter.get(15, SECONDS),
                contains("111", "222", "333", "444", "555")
        );
    }

    @Test
    public void shouldGetNotMoreDataThanNeed() throws Exception {
        final RemoteFunction<String, String> function = new TestStageFunction();

        scheduler.createObservable(new TestSupplierWithFuture())
                .map(function)
                .map(function)
                .subscribe(new TestConsumer())
        .get(15, SECONDS);

        assertThat(
                futureCount.get(15, SECONDS).summ,
                is(totalRequestedElements)
        );
    }

    private static class TestSupplier implements RemoteSupplier<Observable<String>> {

        @Override
        public Observable<String> get() {
            return Observable.just("1", "2", "3", "4", "5");
        }
    }

    private static class TestSupplierWithFuture implements RemoteSupplier<Observable<String>> {

        @Autowired
        FutureLongComponent future;

        @Override
        public Observable<String> get() {
            return from(getList(1000))
                    .doOnRequest(l -> future.add(l))
                    .doOnError(e -> future.error(e))
                    .doOnCompleted(() -> future.complete());
        }

        private List<String> getList(final int count) {
            final List<String> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(Integer.toString(i));
            }
            return list;
        }
    }


    private static class TestConsumer implements RemoteSubscriber<String> {
        private static final Logger log = LoggerFactory.getLogger(TestConsumer.class);

        @Autowired
        private TestConsumerAdapter list;

        @Override
        public void onStart(final Producer producer) {
            log.info("onStart(" + producer + ")");
        }

        @Override
        public void onCompleted() {
            log.info("onCompleted()");
            list.onCompleted();
        }

        @Override
        public void onError(final Throwable e) {
            log.info("onError(" + e + ")");
            list.onError(e);
        }

        @Override
        public void onNext(final String o) {
            log.info("onNext(" + o + ")");
            list.onNext(o);
        }
    }

    private static class TestStageFunction implements RemoteFunction<String, String> {
        @Override
        public String apply(final String s) {
            return s + s.charAt(0);
        }
    }
}
