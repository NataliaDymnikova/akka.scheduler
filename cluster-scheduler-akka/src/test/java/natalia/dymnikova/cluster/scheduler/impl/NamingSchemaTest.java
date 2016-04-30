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

import natalia.dymnikova.cluster.scheduler.akka.Flow;
import org.junit.Test;

import static natalia.dymnikova.cluster.scheduler.impl.MessagesHelper.flowMessage;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteFlowControlActorPath;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.remoteStageActorPath;
import static natalia.dymnikova.cluster.scheduler.impl.NamingSchema.stageName;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * 
 */
public class NamingSchemaTest {

    final Flow.SetFlow flow = flowMessage();

    @Test
    public void shouldComputeRemoteFlowControlActorPath() throws Exception {
        assertThat(
                remoteFlowControlActorPath(flow, 0).toString(),
                is("akka://system@host2:0/user/compute-pool/test-flow")
        );
    }

    @Test
    public void shouldComputeRemoteFlowControlActorPathForSecondHost() throws Exception {
        assertThat(
                remoteFlowControlActorPath(flow, 1).toString(),
                is("akka://system@host1:0/user/compute-pool/test-flow")
        );
    }

    @Test
    public void shouldComputeRemoteFlowControlActorPathForThirdHost() throws Exception {
        assertThat(
                remoteFlowControlActorPath(flow, 2).toString(),
                is("akka://system@host0:0/user/compute-pool/test-flow")
        );
    }

    @Test
    public void shouldComputeStageActorPath() throws Exception {
        assertThat(
                stageName(1), is("001")
        );
    }

    @Test
    public void shouldComputeRemoteStageActorPath() throws Exception {
        assertThat(
                remoteStageActorPath(flow, 0).toString(),
                is("akka://system@host2:0/user/compute-pool/test-flow/000")
        );
    }

    @Test
    public void shouldComputeRemoteStageActorPathForSecondStage() throws Exception {
        assertThat(
                remoteStageActorPath(flow, 1).toString(),
                is("akka://system@host1:0/user/compute-pool/test-flow/001")
        );
    }

    @Test
    public void shouldComputeRemoteStageActorPathForThird() throws Exception {
        assertThat(
                remoteStageActorPath(flow, 2).toString(),
                is("akka://system@host0:0/user/compute-pool/test-flow/002")
        );
    }
}