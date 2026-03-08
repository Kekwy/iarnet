package com.kekwy.iarnet.api.function;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 用于在运行时保留泛型类型信息的辅助类。
 * <p>
 * 使用方式示例：
 * <pre>
 *   TypeRef&lt;List&lt;String&gt;&gt; type = new TypeRef&lt;&gt;() {};
 * </pre>
 */
public abstract class TypeRef<T> {

    private final Type type;

    protected TypeRef() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType parameterizedType) {
            this.type = parameterizedType.getActualTypeArguments()[0];
        } else {
            throw new IllegalStateException("TypeRef must be created with generic type information");
        }
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return type.getTypeName();
    }
}

