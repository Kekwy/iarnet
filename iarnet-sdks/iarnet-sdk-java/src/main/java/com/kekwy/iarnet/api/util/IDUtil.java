package com.kekwy.iarnet.api.util;

import java.util.UUID;

public class IDUtil {

    public static String genUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
