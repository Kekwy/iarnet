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
        if (body.getLang() == null || body.getLang().isBlank()) {
            log.warn("创建应用失败: 缺少必填字段 lang");
            return Response.fail("缺少必填字段: lang");
        }
        ApplicationInfo input = new ApplicationInfo();
        input.setName(body.getName());
        input.setGitUrl(body.getGitUrl());
        input.setBranch(body.getBranch() != null && !body.getBranch().isBlank() ? body.getBranch() : "main");
        input.setDescription(body.getDescription());
        input.setLang(body.getLang());

        ApplicationInfo created = applicationFacade.createApplication(input);
        log.info("应用创建成功: id={}, name={}", created.getId() != null ? created.getId().getValue() : "?", created.getName());
        return Response.ok(created);
    }

    /**
     * 启动应用（构建 + 运行），与 HTTP 请求异步执行。
     *
     * 前端调用该接口仅负责触发任务，很快返回。
     * 实际的构建/运行过程在后台异步执行，结果会反映到 ApplicationInfo.status / lastError 上，
     * 前端可以通过轮询「获取应用列表」或单个详情接口获取最新状态和错误信息。
     */
    @PostMapping("/apps/{id}/launch")
    public Response<Void> launchApplication(@PathVariable("id") String id) {
        log.info("POST /apps/{}/launch - 触发应用构建与部署", id);

        if (id == null || id.isBlank()) {
            return Response.fail("应用 ID 不能为空");
        }

        ID appId = ID.of(id);

        // 异步执行构建与运行，避免阻塞 HTTP 请求
        new Thread(() -> {
            try {
                boolean ok = applicationFacade.launchApplication(appId);
                if (!ok) {
                    log.warn("异步启动应用失败: id={}", id);
                }
            } catch (Exception e) {
                log.error("异步启动应用时发生异常: id={}", id, e);
                // 具体错误信息已在 DefaultLaunchService 中写入 lastError，前端可主动轮询获取
            }
        }, "launch-app-" + id).start();

        // 立即返回，提示任务已提交
        return Response.ok("应用构建与部署任务已提交", null);
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

    /**
     * 获取指定应用最近一次 Maven 构建日志。
     *
     * GET /api/application/apps/{id}/build-log
     */
    @GetMapping("/apps/{id}/build-log")
    public Response<String> getBuildLog(@PathVariable("id") String id) {
        log.info("GET /apps/{}/build-log - 获取应用构建日志", id);
        if (id == null || id.isBlank()) {
            return Response.fail("应用 ID 不能为空");
        }

        try {
            return applicationFacade.getBuildLog(ID.of(id))
                    .map(Response::ok)
                    .orElseGet(() -> Response.ok(""));
        } catch (IllegalArgumentException e) {
            log.warn("获取应用构建日志失败: 参数错误, id={}, msg={}", id, e.getMessage());
            return Response.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取应用构建日志失败: id={}, msg={}", id, e.getMessage(), e);
            return Response.fail("获取构建日志失败: " + e.getMessage());
        }
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
    public Response<ApplicationInfo> updateApplication(@PathVariable String id, @RequestBody UpdateApplicationRequest body) {
        log.info("PUT /apps/{} - 更新应用: name={}, lang={}", id, body.getName(), body.getLang());
        ApplicationInfo input = new ApplicationInfo();
        input.setName(body.getName());
        input.setDescription(body.getDescription());
        input.setLang(body.getLang());

        ApplicationInfo updated = applicationFacade.updateApplication(ID.of(id), input);
        log.info("应用更新成功: id={}, name={}", id, updated.getName());
        return Response.ok(updated);
    }

    /** 删除应用 DELETE /api/application/apps/:id */
    @DeleteMapping("/apps/{id}")
    public Response<Void> deleteApplication(@PathVariable String id) {
        log.info("DELETE /apps/{} - 删除应用", id);
        applicationFacade.deleteApplication(ID.of(id));
        log.info("应用删除成功: id={}", id);
        return Response.ok();
    }
}
