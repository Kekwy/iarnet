package com.kekwy.iarnet.api;

public class Field {

    private final String name;
    private final DataType type;

    public Field(String name, DataType type) {
        this.name = name;
        this.type = type;
    }

    public DataType getType() {
        return type;
    }

    public String getName() {
        return name;
    }
    
}
