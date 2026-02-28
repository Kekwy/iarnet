package com.kekwy.iarnet.controller;

import com.kekwy.iarnet.application.ApplicationFacade;
import com.kekwy.iarnet.config.SupportedLangProperties;
import com.kekwy.iarnet.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/application")
public class ApplicationController {

    private ApplicationFacade applicationFacade;
    private SupportedLangProperties supportedLangProperties;

    @Autowired
    public void setApplicationFacade(ApplicationFacade applicationFacade) {
        this.applicationFacade = applicationFacade;
    }

    @Autowired
    public void setSupportedLangProperties(SupportedLangProperties supportedLangProperties) {
        this.supportedLangProperties = supportedLangProperties;
    }

    /** 获取应用列表 GET /api/application/apps */
    @GetMapping("/apps")
    public Response<ListApplicationInfoResponse> getApplicationList() {
        List<ApplicationInfo> apps = applicationFacade.listApplicationInfo();
        if (apps == null) return Response.fail("获取应用列表失败");
        ListApplicationInfoResponse response = new ListApplicationInfoResponse();
        response.setApplications(apps);
        return Response.ok(response);
    }

    /** 创建应用 POST /api/application/apps */
    @PostMapping("/apps")
    public Response<ApplicationInfo> createApplication(@RequestBody CreateApplicationRequest body) {
        if (body.getName() == null || body.getName().isBlank()) {
            return Response.fail("缺少必填字段: name");
        }
        if (body.getGitUrl() == null || body.getGitUrl().isBlank()) {
            return Response.fail("缺少必填字段: git_url");
        }
        if (body.getRunnerEnv() == null || body.getRunnerEnv().isBlank()) {
            return Response.fail("缺少必填字段: runner_env");
        }
        ApplicationInfo input = new ApplicationInfo();
        input.setName(body.getName());
        input.setGitUrl(body.getGitUrl());
        input.setBranch(body.getBranch() != null && !body.getBranch().isBlank() ? body.getBranch() : "main");
        input.setDescription(body.getDescription());
        input.setRunnerEnv(body.getRunnerEnv());

        ApplicationInfo created = applicationFacade.createApplication(input);
        return Response.ok(created);
    }

    /** 获取应用统计 GET /api/application/stats */
    @GetMapping("/stats")
    public Response<ApplicationStatsResponse> getApplicationStats() {
        var statsMap = applicationFacade.getApplicationStats();
        if (statsMap == null) return Response.fail("获取应用统计失败");

        ApplicationStatsResponse response = new ApplicationStatsResponse();
        response.setTotal(statsMap.getOrDefault("total", 0L));
        response.setRunning(statsMap.getOrDefault("running", 0L));
        response.setStopped(statsMap.getOrDefault("stopped", 0L));
        response.setUndeployed(statsMap.getOrDefault("undeployed", 0L));
        response.setFailed(statsMap.getOrDefault("failed", 0L));
        response.setImporting(statsMap.getOrDefault("importing", 0L));
        return Response.ok(response);
    }

    /** 获取运行环境列表 GET /api/application/runner-environments */
    @GetMapping("/supported-langs")
    public Response<SupportedLangsResponse> getSupportedLangs() {
        SupportedLangsResponse response = new SupportedLangsResponse();
        List<String> envs = supportedLangProperties.getSupportedLang();
        response.setSupportedLangs(envs != null ? envs : List.of());
        return Response.ok(response);
    }

    /** 更新应用 PUT /api/application/apps/:id */
    @PutMapping("/apps/{id}")
    public Response<ApplicationInfo> updateApplication(@PathVariable Long id, @RequestBody UpdateApplicationRequest body) {
        ApplicationInfo input = new ApplicationInfo();
        input.setName(body.getName());
        input.setDescription(body.getDescription());
        input.setRunnerEnv(body.getRunnerEnv());

        ApplicationInfo updated = applicationFacade.updateApplication(id, input);
        return Response.ok(updated);
    }

    /** 删除应用 DELETE /api/application/apps/:id */
    @DeleteMapping("/apps/{id}")
    public Response<Void> deleteApplication(@PathVariable Long id) {
        applicationFacade.deleteApplication(id);
        return Response.ok();
    }
}
