package com.kekwy.iarnet.controller;

import com.kekwy.iarnet.application.ApplicationFacade;
import com.kekwy.iarnet.config.SupportedLangProperties;
import com.kekwy.iarnet.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/application")
public class ApplicationController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);

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
        log.info("GET /apps - 获取应用列表");
        List<ApplicationInfo> apps = applicationFacade.listApplicationInfo();
        if (apps == null) {
            log.warn("获取应用列表失败: listApplicationInfo 返回 null");
            return Response.fail("获取应用列表失败");
        }
        log.debug("应用列表数量: {}", apps.size());
        ListApplicationInfoResponse response = new ListApplicationInfoResponse();
        response.setApplications(apps);
        return Response.ok(response);
    }

    /** 创建应用 POST /api/application/apps */
    @PostMapping("/apps")
    public Response<ApplicationInfo> createApplication(@RequestBody CreateApplicationRequest body) {
        log.info("POST /apps - 创建应用: name={}, gitUrl={}, branch={}", body.getName(), body.getGitUrl(), body.getBranch());
        if (body.getName() == null || body.getName().isBlank()) {
            log.warn("创建应用失败: 缺少必填字段 name");
            return Response.fail("缺少必填字段: name");
        }
        if (body.getGitUrl() == null || body.getGitUrl().isBlank()) {
            log.warn("创建应用失败: 缺少必填字段 git_url");
            return Response.fail("缺少必填字段: git_url");
        }
        if (body.getRunnerEnv() == null || body.getRunnerEnv().isBlank()) {
            log.warn("创建应用失败: 缺少必填字段 runner_env");
            return Response.fail("缺少必填字段: runner_env");
        }
        ApplicationInfo input = new ApplicationInfo();
        input.setName(body.getName());
        input.setGitUrl(body.getGitUrl());
        input.setBranch(body.getBranch() != null && !body.getBranch().isBlank() ? body.getBranch() : "main");
        input.setDescription(body.getDescription());
        input.setRunnerEnv(body.getRunnerEnv());

        ApplicationInfo created = applicationFacade.createApplication(input);
        log.info("应用创建成功: id={}, name={}", created.getId() != null ? created.getId().getValue() : "?", created.getName());
        return Response.ok(created);
    }

    /** 获取应用统计 GET /api/application/stats */
    @GetMapping("/stats")
    public Response<ApplicationStatsResponse> getApplicationStats() {
        log.debug("GET /stats - 获取应用统计");
        var statsMap = applicationFacade.getApplicationStats();
        if (statsMap == null) {
            log.warn("获取应用统计失败");
            return Response.fail("获取应用统计失败");
        }

        ApplicationStatsResponse response = new ApplicationStatsResponse();
        response.setTotal(statsMap.getOrDefault("total", 0L));
        response.setRunning(statsMap.getOrDefault("running", 0L));
        response.setStopped(statsMap.getOrDefault("stopped", 0L));
        response.setUndeployed(statsMap.getOrDefault("undeployed", 0L));
        response.setFailed(statsMap.getOrDefault("failed", 0L));
        response.setImporting(statsMap.getOrDefault("importing", 0L));
        return Response.ok(response);
    }

    /** 获取支持的编程语言列表 GET /api/application/supported-langs */
    @GetMapping("/supported-langs")
    public Response<SupportedLangsResponse> getSupportedLangs() {
        log.debug("GET /supported-langs - 获取支持的编程语言");
        SupportedLangsResponse response = new SupportedLangsResponse();
        List<String> envs = supportedLangProperties.getSupportedLang();
        response.setSupportedLangs(envs != null ? envs : List.of());
        return Response.ok(response);
    }

    /** 更新应用 PUT /api/application/apps/:id */
    @PutMapping("/apps/{id}")
    public Response<ApplicationInfo> updateApplication(@PathVariable Long id, @RequestBody UpdateApplicationRequest body) {
        log.info("PUT /apps/{} - 更新应用: name={}, runnerEnv={}", id, body.getName(), body.getRunnerEnv());
        ApplicationInfo input = new ApplicationInfo();
        input.setName(body.getName());
        input.setDescription(body.getDescription());
        input.setRunnerEnv(body.getRunnerEnv());

        ApplicationInfo updated = applicationFacade.updateApplication(id, input);
        log.info("应用更新成功: id={}, name={}", id, updated.getName());
        return Response.ok(updated);
    }

    /** 删除应用 DELETE /api/application/apps/:id */
    @DeleteMapping("/apps/{id}")
    public Response<Void> deleteApplication(@PathVariable Long id) {
        log.info("DELETE /apps/{} - 删除应用", id);
        applicationFacade.deleteApplication(id);
        log.info("应用删除成功: id={}", id);
        return Response.ok();
    }
}
