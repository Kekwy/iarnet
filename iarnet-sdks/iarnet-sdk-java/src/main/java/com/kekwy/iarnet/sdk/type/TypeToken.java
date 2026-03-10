package com.kekwy.iarnet.sdk.type;

import com.kekwy.iarnet.sdk.exception.IarnetValidationException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 显式保留泛型信息的 TypeToken，参考 Flink TypeHint / Gson TypeToken。
 * <p>
 * 用法：
 * <pre>{@code
 * Type type = new TypeToken<List<String>>() {}.getType();
 * Type type2 = new TypeToken<Map<String, List<User>>>() {}.getType();
 * }</pre>
 */
public abstract class TypeToken<T> {

    private final Type type;

    protected TypeToken() {
        Type superClass = getClass().getGenericSuperclass();
        if (!(superClass instanceof ParameterizedType)) {
            throw new IarnetValidationException(
                    "TypeToken must be created with type parameter, e.g. new TypeToken<List<String>>() {}");
        }
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }
}
