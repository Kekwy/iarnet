package com.kekwy.iarnet.sdk.type;

import com.kekwy.iarnet.sdk.exception.IarnetValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeTokenTest {

    @Nested
    @DisplayName("简单类型")
    class SimpleType {

        @Test
        @DisplayName("String 类型")
        void stringType() {
            Type type = new TypeToken<String>() {
            }.getType();
            assertEquals(String.class, type);
        }

        @Test
        @DisplayName("Integer 类型")
        void integerType() {
            Type type = new TypeToken<Integer>() {
            }.getType();
            assertEquals(Integer.class, type);
        }

        @Test
        @DisplayName("Long 类型")
        void longType() {
            Type type = new TypeToken<Long>() {
            }.getType();
            assertEquals(Long.class, type);
        }

        @Test
        @DisplayName("Boolean 类型")
        void booleanType() {
            Type type = new TypeToken<Boolean>() {
            }.getType();
            assertEquals(Boolean.class, type);
        }

        @Test
        @DisplayName("Void 类型")
        void voidType() {
            Type type = new TypeToken<Void>() {
            }.getType();
            assertEquals(Void.class, type);
        }

        @Test
        @DisplayName("Object 类型")
        void objectType() {
            Type type = new TypeToken<Object>() {
            }.getType();
            assertEquals(Object.class, type);
        }

        @Test
        @SuppressWarnings("rawtypes")
        @DisplayName("原始 List 类型（无泛型参数）")
        void listRawType() {
            Type type = new TypeToken<List>() {
            }.getType();
            assertEquals(List.class, type);
        }

        @Test
        @DisplayName("自定义 record 类型")
        void recordType() {
            Type type = new TypeToken<SampleRecord>() {
            }.getType();
            assertEquals(SampleRecord.class, type);
        }
    }

    @Nested
    @DisplayName("参数化泛型类型")
    class ParameterizedTypeCases {

        @Test
        @DisplayName("List<String>")
        void listOfString() {
            Type type = new TypeToken<List<String>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            assertEquals(List.class, pt.getRawType());
            assertEquals(1, pt.getActualTypeArguments().length);
            assertEquals(String.class, pt.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("Set<Integer>")
        void setOfInteger() {
            Type type = new TypeToken<Set<Integer>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            assertEquals(Set.class, pt.getRawType());
            assertEquals(Integer.class, pt.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("Optional<String>")
        void optionalOfString() {
            Type type = new TypeToken<Optional<String>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            assertEquals(Optional.class, pt.getRawType());
            assertEquals(String.class, pt.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("Map<String, Integer>")
        void mapOfStringInteger() {
            Type type = new TypeToken<Map<String, Integer>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            assertEquals(Map.class, pt.getRawType());
            Type[] args = pt.getActualTypeArguments();
            assertEquals(2, args.length);
            assertEquals(String.class, args[0]);
            assertEquals(Integer.class, args[1]);
        }

        @Test
        @DisplayName("Map<String, List<Integer>> 嵌套泛型")
        void nestedGenerics() {
            Type type = new TypeToken<Map<String, List<Integer>>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            assertEquals(Map.class, pt.getRawType());
            Type[] args = pt.getActualTypeArguments();
            assertEquals(2, args.length);
            assertEquals(String.class, args[0]);
            assertInstanceOf(ParameterizedType.class, args[1]);
            ParameterizedType inner = (ParameterizedType) args[1];
            assertEquals(List.class, inner.getRawType());
            assertEquals(1, inner.getActualTypeArguments().length);
            assertEquals(Integer.class, inner.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("List<List<String>> 多重嵌套")
        void multiLevelNested() {
            Type type = new TypeToken<List<List<String>>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType outer = (ParameterizedType) type;
            assertEquals(List.class, outer.getRawType());
            Type inner = outer.getActualTypeArguments()[0];
            assertInstanceOf(ParameterizedType.class, inner);
            ParameterizedType innerPt = (ParameterizedType) inner;
            assertEquals(List.class, innerPt.getRawType());
            assertEquals(String.class, innerPt.getActualTypeArguments()[0]);
        }

        @Test
        @DisplayName("Map<String, Map<Integer, String>> 多层 Map")
        void multiLevelMap() {
            Type type = new TypeToken<Map<String, Map<Integer, String>>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType outer = (ParameterizedType) type;
            assertEquals(Map.class, outer.getRawType());
            assertEquals(String.class, outer.getActualTypeArguments()[0]);
            Type innerMap = outer.getActualTypeArguments()[1];
            assertInstanceOf(ParameterizedType.class, innerMap);
            ParameterizedType innerPt = (ParameterizedType) innerMap;
            assertEquals(Map.class, innerPt.getRawType());
            assertEquals(Integer.class, innerPt.getActualTypeArguments()[0]);
            assertEquals(String.class, innerPt.getActualTypeArguments()[1]);
        }

        @Test
        @DisplayName("OptionalValue<String> 自定义泛型类")
        void customGenericType() {
            Type type = new TypeToken<OptionalValue<String>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            assertEquals(OptionalValue.class, pt.getRawType());
            assertEquals(String.class, pt.getActualTypeArguments()[0]);
        }
    }

    @Nested
    @DisplayName("数组类型")
    class ArrayType {

        @Test
        @DisplayName("String[]")
        void stringArray() {
            Type type = new TypeToken<String[]>() {
            }.getType();
            assertInstanceOf(Class.class, type);
            assertTrue(((Class<?>) type).isArray());
            assertEquals(String[].class, type);
        }

        @Test
        @DisplayName("int[] 基本类型数组")
        void primitiveArray() {
            Type type = new TypeToken<int[]>() {
            }.getType();
            assertEquals(int[].class, type);
        }

        @Test
        @DisplayName("List<String>[] 泛型数组")
        void genericArray() {
            Type type = new TypeToken<List<String>[]>() {
            }.getType();
            assertInstanceOf(GenericArrayType.class, type);
            GenericArrayType gat = (GenericArrayType) type;
            Type componentType = gat.getGenericComponentType();
            assertInstanceOf(ParameterizedType.class, componentType);
            ParameterizedType pt = (ParameterizedType) componentType;
            assertEquals(List.class, pt.getRawType());
            assertEquals(String.class, pt.getActualTypeArguments()[0]);
        }
    }

    @Nested
    @DisplayName("通配符类型")
    class WildcardTypeCases {

        @Test
        @DisplayName("无界通配符 ?")
        void unboundedWildcard() {
            Type type = new TypeToken<List<?>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            Type arg = pt.getActualTypeArguments()[0];
            assertInstanceOf(WildcardType.class, arg);
            WildcardType wt = (WildcardType) arg;
            assertEquals(0, wt.getLowerBounds().length);
            assertEquals(1, wt.getUpperBounds().length);
            assertEquals(Object.class, wt.getUpperBounds()[0]);
        }

        @Test
        @DisplayName("上界通配符 ? extends Number")
        void upperBoundedWildcard() {
            Type type = new TypeToken<List<? extends Number>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            Type arg = pt.getActualTypeArguments()[0];
            assertInstanceOf(WildcardType.class, arg);
            WildcardType wt = (WildcardType) arg;
            assertEquals(0, wt.getLowerBounds().length);
            assertEquals(1, wt.getUpperBounds().length);
            assertEquals(Number.class, wt.getUpperBounds()[0]);
        }

        @Test
        @DisplayName("下界通配符 ? super String")
        void lowerBoundedWildcard() {
            Type type = new TypeToken<List<? super String>>() {
            }.getType();
            assertInstanceOf(ParameterizedType.class, type);
            ParameterizedType pt = (ParameterizedType) type;
            Type arg = pt.getActualTypeArguments()[0];
            assertInstanceOf(WildcardType.class, arg);
            WildcardType wt = (WildcardType) arg;
            assertEquals(1, wt.getLowerBounds().length);
            assertEquals(String.class, wt.getLowerBounds()[0]);
        }
    }

    @Nested
    @DisplayName("getType 行为")
    class GetTypeBehavior {

        @Test
        @DisplayName("多次调用 getType 返回同一引用")
        void getTypeReturnsSameInstance() {
            TypeToken<String> token = new TypeToken<String>() {
            };
            Type t1 = token.getType();
            Type t2 = token.getType();
            assertSame(t1, t2);
        }

        @Test
        @DisplayName("不同实例的 TypeToken 相同泛型返回等价的 Type")
        void sameGenericSameType() {
            Type t1 = new TypeToken<List<String>>() {
            }.getType();
            Type t2 = new TypeToken<List<String>>() {
            }.getType();
            assertEquals(t1, t2);
        }
    }

    @Nested
    @DisplayName("非法用法")
    class InvalidUsage {

        @SuppressWarnings("rawtypes")
        @Test
        @DisplayName("裸类型 new TypeToken() {} 应抛出异常")
        void rawTypeThrows() {
            IarnetValidationException e = assertThrows(IarnetValidationException.class, () -> {
                new TypeToken() {
                };
            });
            assertTrue(e.getMessage().contains("TypeToken must be created with type parameter"));
        }

        @Test
        @DisplayName("非匿名子类裸类型继承应抛出异常")
        void concreteSubclassRawTypeThrows() {
            IarnetValidationException e = assertThrows(IarnetValidationException.class, RawTypeTokenSubclass::new);
            assertTrue(e.getMessage().contains("TypeToken must be created with type parameter"));
        }
    }

    // 用于裸类型非法用法测试
    @SuppressWarnings("rawtypes")
    private static final class RawTypeTokenSubclass extends TypeToken {
    }

    private record SampleRecord(String name, int value) {
    }
}
