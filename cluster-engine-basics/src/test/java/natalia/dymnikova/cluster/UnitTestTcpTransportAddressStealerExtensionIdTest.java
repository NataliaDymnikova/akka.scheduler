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

package natalia.dymnikova.cluster;

import akka.actor.UntypedActor;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

/**
 * 
 */
public class UnitTestTcpTransportAddressStealerExtensionIdTest {

    @Before
    public void setUp() throws Exception {
        SpringActorProducer.initialize(new FileSystemXmlApplicationContext());
    }

    @Test
    public void shouldSerializeProps() throws Exception {
        final AkkaExtension extension = new AkkaExtension();

        new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(
            extension.props(TestActor.class)
        );
    }

    private class TestActor extends ActorLogic {
        public TestActor(final ActorAdapter adapter) {
            super(adapter);
        }
    }
}
