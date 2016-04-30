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

import akka.actor.Address;
import com.google.protobuf.ByteString;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.cluster.scheduler.akka.Flow.StageType.Merge;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class FlowMergerTest {

    private FlowMerger merger = new FlowMerger();
    private final Address address = Address.apply("akka", "natalia-dymnikova", "host0", 0);
    private Optional<Address> nextAddress = Optional.of(address);
    private StageContainer mergeStage = new StageContainer(
            emptyList(),
            null,
            ByteString.EMPTY,
            Merge,
            emptyList()
    );
    private List<Stage> stagesWithDifferentAddresses = Arrays.asList(
            Stage.newBuilder()
                    .setAddress("akka://natalia-dymnikova@host1:1")
                    .setId(4)
                    .build(),
            Stage.newBuilder()
                    .setAddress("akka://natalia-dymnikova@host2:2")
                    .setId(2)
                    .build(),
            Stage.newBuilder()
                    .setAddress("akka://natalia-dymnikova@host2:2")
                    .setId(3)
                    .build()
    );

    @Before
    public void setUp() throws Exception {
        mergeStage.id = 1;

    }

    @Test
    public void shouldHaveMergeType() throws Exception {
        final Stage mergeStages = merger.createMergeStages(stagesWithDifferentAddresses, mergeStage, nextAddress);
        assertThat(
                mergeStages.getType(),
                is(Merge)
        );
    }

    @Test
    public void shouldHaveAddressOfNextStage() throws Exception {
        final Stage mergeStages = merger.createMergeStages(stagesWithDifferentAddresses, mergeStage, nextAddress);
        assertThat(
                mergeStages.getAddress(),
                is(address.toString())
        );
    }

    @Test
    public void shouldHaveTwoStagesWithMergeType() throws Exception {
        final Stage mergeStages = merger.createMergeStages(stagesWithDifferentAddresses, mergeStage, nextAddress);
        assertThat(
                mergeStages.getStagesList().stream().map(Stage::getType).distinct().collect(toList()),
                contains(Merge)
        );
    }

    @Test
    public void shouldHaveTwoStagesWitAddressesOfPreviousStages() throws Exception {
        final Stage mergeStages = merger.createMergeStages(stagesWithDifferentAddresses, mergeStage, nextAddress);
        assertThat(
                mergeStages.getStagesList().stream().map(Stage::getAddress).collect(toList()),
                containsInAnyOrder("akka://natalia-dymnikova@host1:1", "akka://natalia-dymnikova@host2:2")
        );
    }

    @Test
    public void shouldSetSameIdAsMerge() throws Exception {
        final Stage mergeStages = merger.createMergeStages(stagesWithDifferentAddresses, mergeStage, nextAddress);
        assertThat(
                mergeStages.getId(),
                is(mergeStage.id)
        );
    }

    @Test
    public void shouldSetSameIdToSubMergeAsMinusOneOfIds() throws Exception {
        final Stage mergeStages = merger.createMergeStages(stagesWithDifferentAddresses, mergeStage, nextAddress);
        assertThat(
                mergeStages.getStagesList().get(1).getId(),
                is(-2)
        );
    }
}