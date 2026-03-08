package com.kekwy.iarnet.api.sink;

public interface SinkVisitor<R> {

    R visit(PrintSink sink);

}
