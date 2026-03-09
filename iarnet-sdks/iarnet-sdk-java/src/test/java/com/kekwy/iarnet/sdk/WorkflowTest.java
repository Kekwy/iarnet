package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.source.ConstantSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowTest {

    @Test
    void testBatch() {
        Workflow workflow = Workflow.create();
        workflow.source(new ConstantSource<>(List.of(1, 2, 3, 4, 5)))
                .batch(2)
                .sink(list -> {
                    assertEquals(2, list.size());
                });
    }

    @Test
    void testBranch() {
        Workflow workflow = Workflow.create();
        BranchedFlow<Integer> branchedFlow = workflow.source(new ConstantSource<>(List.of(1, 2, 3, 4, 5)))
                .branch(i -> i % 2 == 0);

        branchedFlow.matched().sink(i -> assertEquals(0, i % 2));
        branchedFlow.unmatched().sink(i -> assertEquals(1, i % 2));
    }

    @Test
    void testFold() {
        Workflow workflow = Workflow.create();
        workflow.source(new ConstantSource<>(List.of("a", "b", "a")))
                .keyBy(s -> s)
                .fold(0, (acc, v) -> acc + 1)
                .sink(i -> {});
    }

    @Test
    void testCorrelate() {
        Workflow workflow = Workflow.create();
        Flow<String> flow1 = workflow.source(new ConstantSource<>(List.of("a", "b")));
        Flow<Integer> flow2 = workflow.source(new ConstantSource<>(List.of(1, 2)));

        flow1.correlate(flow2, s -> s, i -> String.valueOf(i), new CorrelateFunction<String, Integer, String>() {
            @Override
            public String onLeft(String value) {
                return value;
            }

            @Override
            public String onRight(Integer value) {
                return String.valueOf(value);
            }
        }).sink(s -> {});
    }
}
