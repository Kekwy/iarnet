package com.kekwy.iarnet.model;

import lombok.Data;

@Data
public class ApplicationStatsResponse {

    private long total;
    private long running;
    private long stopped;
    private long undeployed;
    private long failed;
    private long importing;
}
