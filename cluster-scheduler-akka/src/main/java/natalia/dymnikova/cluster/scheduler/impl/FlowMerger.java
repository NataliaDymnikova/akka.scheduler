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
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;
import natalia.dymnikova.cluster.scheduler.impl.AkkaBackedRemoteObservable.StageContainer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 *
 */
@Lazy
@Component
public class FlowMerger {

    public Stage createMergeStages(final List<Stage> previous,
                                   final StageContainer currentStage,
                                   final Optional<Address> nextAddress) {
        // TODO: doesn't create two mergers in one host
        final List<Stage> children = new ArrayList<>();
        previous.stream()
                .map(Stage::getAddress)
                .distinct()
                .forEach(address -> children.add(Stage.newBuilder()
                        .setOperator(currentStage.remoteBytes)
                        .setAddress(address)
                        .setType(currentStage.stageType)
                        .setId(- previous.stream().filter(s -> s.getAddress().equals(address)).findFirst().get().getId())
                        .addAllStages(previous.stream()
                                .filter(stage -> stage.getAddress().equals(address))
                                .collect(toList()))
                        .build()));

        return nextAddress.map(addr ->
                Stage.newBuilder()
                        .setOperator(currentStage.remoteBytes)
                        .setAddress(addr.toString())
                        .setType(currentStage.stageType)
                        .setId(currentStage.id)
                        .addAllStages(children)
                        .build())
                .orElseThrow(() -> new NoSuchElementException("No candidate for stage " + currentStage.action));
    }
}
