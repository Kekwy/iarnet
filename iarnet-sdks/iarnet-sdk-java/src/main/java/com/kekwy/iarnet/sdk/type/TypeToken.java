package com.kekwy.iarnet.sdk.type;

import com.kekwy.iarnet.sdk.exception.IarnetValidationException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 显式保留泛型信息的类型标记。
 * <p>
 * 利用 Java 类型擦除后匿名子类仍可获取泛型参数的机制，在 DSL 中提供类型提示。
 * 当 {@link com.kekwy.iarnet.sdk.util.TypeExtractor} 无法从 lambda 反射推断输出类型时，
 * 可通过 {@link com.kekwy.iarnet.sdk.Flow#returns(TypeToken)} 或
 * {@link com.kekwy.iarnet.sdk.dsl.Tasks#pythonTask(String, String, TypeToken)} 等方式传入。
 * <p>
 * 用法（必须使用匿名子类 + 泛型参数）：
 * <pre>{@code
 * Type type = new TypeToken<List<String>>() {}.getType();
 * Type type2 = new TypeToken<Map<String, List<User>>>() {}.getType();
 * }</pre>
 *
 * @param <T> 要保留的类型
 * @see com.kekwy.iarnet.sdk.util.TypeExtractor
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

    /**
     * 返回保留的泛型类型。
     *
     * @return 该 TypeToken 所表示的 {@link Type}
     */
    public Type getType() {
        return type;
    }
}
