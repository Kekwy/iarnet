package com.kekwy.iarnet.application.executor;

import com.kekwy.iarnet.proto.ir.WorkflowGraph;

public interface Executor {

    void submit(WorkflowGraph graph);

}
