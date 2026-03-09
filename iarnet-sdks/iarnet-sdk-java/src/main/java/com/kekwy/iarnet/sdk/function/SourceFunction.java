package com.kekwy.iarnet.sdk.function;

import java.util.Optional;

@FunctionalInterface
public interface SourceFunction<O> {
    Optional<O> next() throws Exception;
}
