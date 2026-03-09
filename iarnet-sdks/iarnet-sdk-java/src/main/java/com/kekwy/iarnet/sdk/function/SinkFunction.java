package com.kekwy.iarnet.sdk.function;

import java.io.Serializable;

@FunctionalInterface
public interface SinkFunction<I> extends Serializable {

    void accept(I input);

}
