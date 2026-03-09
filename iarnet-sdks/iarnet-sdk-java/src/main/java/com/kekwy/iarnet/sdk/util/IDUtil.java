package com.kekwy.iarnet.sdk.util;

import java.util.UUID;

public class IDUtil {

    public static String genUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
