package com.kekwy.iarnet.sdk.function;

import java.util.Optional;

@FunctionalInterface
public interface InputFunction<O> {
    Optional<O> next() throws Exception;
}
