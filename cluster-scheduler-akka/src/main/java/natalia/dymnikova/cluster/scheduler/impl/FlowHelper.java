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

import natalia.dymnikova.cluster.scheduler.akka.Flow.SetFlow;
import natalia.dymnikova.cluster.scheduler.akka.Flow.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Integer.compare;
import static java.util.stream.Collectors.toList;

/**
 *
 */
public class FlowHelper {

    public static Stage getStage(final SetFlow flow, final int id) {
        return getStageList(flow.getStage()).stream().filter(stage -> stage.getId() == id).findFirst().get();
    }

    public static int getNextStageNumber(final SetFlow flow, final int id) {
        final Stage stage = getStage(flow, id);
        return getNumberOfStage(flow, nextStage(stage, flow.getStage()));
    }

    public static List<Integer> getPreviousStageNumbers(final SetFlow flow, final int id) {
        return getPreviousStages(flow, id).stream().map(stage -> getNumberOfStage(flow, stage)).collect(toList());
    }

    public static int getNumberOfStage(final SetFlow flow, final Stage stage) {
        return stage.getId();
    }

    public static List<Stage> getPreviousStages(final SetFlow flow, final int id) {
        return getStage(flow, id).getStagesList();
    }


    public static List<Stage> getStageList(final Stage stage) {
        final List<Stage> stages = new ArrayList<>();
        stages.addAll(stage.getStagesList().stream().flatMap(st -> getStageList(st).stream()).collect(toList()));
        stages.add(0, stage);
        return stages;
    }

    private static Stage nextStage(final Stage toFind, final Stage currStage) {
        if (currStage.getStagesList().contains(toFind)) {
            return currStage;
        } else {
            return currStage.getStagesList().stream()
                    .map(st -> nextStage(toFind, st))
                    .filter(st -> st != null)
                    .findAny()
                    .orElse(null);
        }
    }

}
