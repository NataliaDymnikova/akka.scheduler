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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import natalia.dymnikova.cluster.SpringAkkaExtensionId.AkkaExtension;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class AutostartActorsBeanPostProcessorTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ActorSystem actorSystem;

    @Spy
    private AkkaExtension extension = new AkkaExtension();

    @InjectMocks
    private AutostartActorsBeanPostProcessor processor;

    @Test
    public void shouldStartActors() throws Exception {
        doReturn(new String[] {"actor1", "actor2", "actor3"}).when(applicationContext).getBeanNamesForAnnotation(
                AutostartActor.class
        );

        doReturn(Actor1.class).when(applicationContext).getType("actor1");
        doReturn(Actor2.class).when(applicationContext).getType("actor2");
        doReturn(Actor3.class).when(applicationContext).getType("actor3");

        doAnswer(invocation -> mock(ActorRef.class)).when(actorSystem).actorOf(isA(Props.class), anyString());

        processor.onStart();

        verify(actorSystem).actorOf(isA(Props.class), eq("path1"));
        verify(actorSystem).actorOf(isA(Props.class), eq("path2"));
        verify(actorSystem).actorOf(isA(Props.class), eq("path3"));
    }

    @AutostartActor("path1")
    public static class Actor1 extends ActorLogic {
        public Actor1(final ActorAdapter adapter) {
            super(adapter);
        }
    }

    @AutostartActor("path2")
    public static class Actor2 extends ActorLogic {
        public Actor2(final ActorAdapter adapter) {
            super(adapter);
        }
    }

    @AutostartActor("path3")
    public static class Actor3 extends ActorLogic {
        public Actor3(final ActorAdapter adapter) {
            super(adapter);
        }
    }

}
