package com.kekwy.iarnet.api.source;

public interface SourceVisitor<R> {

    R visit(ConstantSource<?> source);

    R visit(FileSource source);

}
