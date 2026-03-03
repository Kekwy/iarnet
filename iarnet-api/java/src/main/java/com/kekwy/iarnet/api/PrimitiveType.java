package com.kekwy.iarnet.api;

public final class PrimitiveType implements DataType {

    private final TypeKind kind;

    private PrimitiveType(TypeKind kind) {
        this.kind = kind;
    }

    public static final PrimitiveType STRING =
            new PrimitiveType(TypeKind.STRING);

    public static final PrimitiveType INT32 =
            new PrimitiveType(TypeKind.INT32);

    public static final PrimitiveType INT64 =
            new PrimitiveType(TypeKind.INT64);

    public static final PrimitiveType DOUBLE =
            new PrimitiveType(TypeKind.DOUBLE);

    public static final PrimitiveType BOOLEAN =
            new PrimitiveType(TypeKind.BOOLEAN);

    @Override
    public TypeKind getKind() {
        return kind;
    }
}