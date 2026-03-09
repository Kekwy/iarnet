package com.kekwy.iarnet.sdk.function;

import java.io.Serializable;
import java.util.Optional;

@FunctionalInterface
public interface UnionFunction<T, U, V> extends Serializable {

    V union(Optional<T> t, Optional<U> u);

}
