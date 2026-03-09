package com.kekwy.iarnet.sdk.converter;

import com.kekwy.iarnet.sdk.sink.PrintSink;

public interface SinkVisitor<R> {

    R visit(PrintSink sink);

}
