package com.kekwy.iarnet.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;

import java.util.UUID;

@Data
public class ID {
    private String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ID of(String value) {
        ID id = new ID();
        id.value = value;
        return id;
    }
}
