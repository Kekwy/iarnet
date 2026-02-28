package com.kekwy.iarnet.api.source;

import java.util.Iterator;

/**
 * 数据源，产生类型为 T 的元素流。
 */
public interface Source<T> extends Iterable<T> {

    @Override
    Iterator<T> iterator();
}
