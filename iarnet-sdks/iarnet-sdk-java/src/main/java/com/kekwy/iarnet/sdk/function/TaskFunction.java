package com.kekwy.iarnet.sdk.function;

import java.io.Serializable;

@FunctionalInterface
public interface TaskFunction<I, O> extends Serializable {

    O apply(I input);

}
