package com.kekwy.iarnet.api;

public record Resource(double cpu, String memory, double gpu) {
    public static Resource of(double cpu, String memory) {
        return new Resource(cpu, memory, 0);
    }

    public static Resource of(double cpu, String memory, double gpu) {
        return new Resource(cpu, memory, gpu);
    }
}
