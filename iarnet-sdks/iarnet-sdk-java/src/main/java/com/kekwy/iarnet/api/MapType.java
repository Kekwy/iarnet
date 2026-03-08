package com.kekwy.iarnet.api;

public final class MapType implements DataType {

    private final DataType keyType;
    private final DataType valueType;

    public MapType(DataType keyType, DataType valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public DataType getKeyType() {
        return keyType;
    }

    public DataType getValueType() {
        return valueType;
    }

    @Override
    public TypeKind getKind() {
        return TypeKind.MAP;
    }
}