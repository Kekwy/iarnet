package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;

public class Row {

    private final Object value;
    private final DataType dataType;

    public Row(Object value, DataType dataType) {
        this.value = value;
        this.dataType = dataType;
    }

    public Object getValue() {
        return value;
    }

    public DataType getDataType() {
        return dataType;
    }

}
