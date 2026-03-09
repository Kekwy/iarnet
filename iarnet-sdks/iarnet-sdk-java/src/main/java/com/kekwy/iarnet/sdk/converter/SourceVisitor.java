package com.kekwy.iarnet.sdk.converter;

import com.kekwy.iarnet.sdk.source.ConstantSource;
import com.kekwy.iarnet.sdk.source.FileSource;

public interface SourceVisitor<R> {

    R visit(ConstantSource<?> source);

    R visit(FileSource source);

}
