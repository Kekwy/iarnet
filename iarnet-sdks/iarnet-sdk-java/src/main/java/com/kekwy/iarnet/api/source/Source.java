package com.kekwy.iarnet.api.source;

import com.kekwy.iarnet.api.graph.SourceNode;

/**
 * 数据源，产生类型为 T 的元素流。
 */
public interface Source<T> {

    <R> R accept(SourceVisitor<R> visitor);

}
