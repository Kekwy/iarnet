package com.kekwy.iarnet.api.example;

import com.kekwy.iarnet.api.*;
import com.kekwy.iarnet.api.annotation.Function;
import com.kekwy.iarnet.api.annotation.Resource;

import java.util.Arrays;

public class Main {


    @Function(replica = 2, resource = @Resource(cpu = 2.0, memory = "1Gi"))
    private static String helloFunction(String name) {
        return "Hello, " + name + "!";
    }

    public static void main(String[] args) {
        Workflow wf = Workflow.create();
        Flow<String> input = wf.source(FileSource.of("data.txt"));
        Flow<String> words = input
                .flatMap(line -> Arrays.asList(line.split(" ")))
                .filter(w -> w.length() > 3)
                .map(String::toLowerCase);

        Task checkpoint = wf.task("checkpoint", ctx -> {
            // save state
        });
        words.after(checkpoint).sink(PrintSink.of());

        wf.execute();
    }

}
