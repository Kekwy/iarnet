package com.kekwy.iarnet.api.annotation;

public @interface Resource {
    double cpu() default 1.0;
    double gpu() default 0.0;
    String memory() default "512Mi";
    String[] tags() default  {};
}
