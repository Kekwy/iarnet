package com.kekwy.iarnet.sdk.source;

import com.kekwy.iarnet.sdk.converter.SourceVisitor;

/**
 * 数据源，产生类型为 T 的元素流。
 */
public interface Source<T> {

    <R> R accept(SourceVisitor<R> visitor);

}
