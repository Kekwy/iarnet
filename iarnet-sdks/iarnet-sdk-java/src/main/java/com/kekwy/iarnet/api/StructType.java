package com.kekwy.iarnet.api;

import java.util.List;

public class StructType implements DataType {

    private final List<Field> fields;

    public StructType(List<Field> fields) {
        this.fields = fields;
    }

    public List<Field> getFields() {
        return fields;
    }

    @Override
    public TypeKind getKind() {
        return TypeKind.STRUCT;
    }

}
