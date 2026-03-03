package com.kekwy.iarnet.api;

public final class ArrayType implements DataType {

    private final DataType elementType;

    public ArrayType(DataType elementType) {
        this.elementType = elementType;
    }

    public DataType getElementType() {
        return elementType;
    }

    @Override
    public TypeKind getKind() {
        return TypeKind.ARRAY;
    }
}