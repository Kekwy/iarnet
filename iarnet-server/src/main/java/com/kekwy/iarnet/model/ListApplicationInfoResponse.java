package com.kekwy.iarnet.model;

import com.kekwy.iarnet.common.model.ApplicationInfo;
import lombok.Data;

import java.util.List;

@Data
public class ListApplicationInfoResponse {

    private List<ApplicationInfo> applications;

}
