package com.kekwy.iarnet.api.annotation;

public @interface Function {
    int replica() default 1;
    boolean external() default false;
    Resource resource();
}
