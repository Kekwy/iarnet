package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.ApplicationInfoEntity;
import com.kekwy.iarnet.application.repository.ApplicationInfoRepository;
import com.kekwy.iarnet.common.enums.AppStatus;
import com.kekwy.iarnet.common.model.ApplicationInfo;
import com.kekwy.iarnet.common.model.ID;
import com.kekwy.iarnet.common.util.IDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultApplicationInfoService implements ApplicationInfoService {

    private static final Logger log = LoggerFactory.getLogger(DefaultApplicationInfoService.class);

    private ApplicationInfoRepository repository;

    @Autowired
    public void setRepository(ApplicationInfoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ApplicationInfo> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toApplicationInfo).toList();
    }

    @Override
    public ApplicationInfo getByID(ID id) {
        return repository.findById(id.getValue()).map(
                this::toApplicationInfo
        ).orElse(null);
    }

    @Override
    public ApplicationInfo create(ApplicationInfo input) {
        log.info("创建应用: name={}, gitUrl={}, branch={}, lang={}", input.getName(), input.getGitUrl(), input.getBranch(), input.getLang());
        ApplicationInfoEntity entity = new ApplicationInfoEntity();
        // 优先使用外部传入的 ID（例如由 Facade 预先生成并用于 workspace），否则自行生成
        String idValue = input.getId() != null ? input.getId().getValue() : IDUtil.genAppID().getValue();
        entity.setApplicationID(idValue);
        entity.setName(input.getName());
        entity.setGitUrl(input.getGitUrl());
        entity.setBranch(input.getBranch() != null ? input.getBranch() : "main");
        entity.setDescription(input.getDescription() != null ? input.getDescription() : "");
        entity.setLang(input.getLang());
        entity.setStatus(AppStatus.APP_STATUS_IDLE.getName());
        entity.setLastError(null);
        entity = repository.save(entity);
        log.info("应用已持久化: id={}, status={}", entity.getApplicationID(), entity.getStatus());
        return toApplicationInfo(entity);
    }

    @Override
    public ApplicationInfo update(ID id, ApplicationInfo input) {
        log.info("更新应用 id={}: name={}, description={}, lang={}", id, input.getName(), input.getDescription(), input.getLang());
        ApplicationInfoEntity entity = repository.findById(id.getValue())
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + id));
        if (input.getName() != null) entity.setName(input.getName());
        if (input.getDescription() != null) entity.setDescription(input.getDescription());
        if (input.getLang() != null) entity.setLang(input.getLang());
        entity = repository.save(entity);
        log.info("应用更新已持久化: id={}", id);
        return toApplicationInfo(entity);
    }

    @Override
    public void delete(ID id) {
        log.info("删除应用: id={}", id);
        repository.deleteById(id.getValue());
        log.info("应用已删除: id={}", id);
    }

    @Override
    public Map<String, Long> getStats() {
        log.debug("统计应用数量");
        Map<String, Long> stats = new HashMap<>();
        // 总数
        stats.put("total", repository.count());
        // 与 AppStatus 中的状态名称保持一致
        stats.put("running", repository.countByStatus(AppStatus.APP_STATUS_RUNNING.getName()));
        stats.put("stopped", repository.countByStatus(AppStatus.APP_STATUS_STOPPED.getName()));
        stats.put("undeployed", repository.countByStatus(AppStatus.APP_STATUS_IDLE.getName()));
        // 失败：兼容旧数据中的 "error" 状态
        long failedByNewStatus = repository.countByStatus(AppStatus.APP_STATUS_FAILED.getName());
        long failedByOldStatus = repository.countByStatus("error");
        stats.put("failed", failedByNewStatus + failedByOldStatus);
        // importing 状态只作为兼容历史数据使用，当前逻辑已不再写入该状态
        stats.put("importing", repository.countByStatus("importing"));
        return stats;
    }

    private ApplicationInfo toApplicationInfo(ApplicationInfoEntity entity) {
        ApplicationInfo info = new ApplicationInfo();
        info.setId(ID.of(entity.getApplicationID()));
        info.setName(entity.getName());
        info.setDescription(entity.getDescription());
        info.setGitUrl(entity.getGitUrl());
        info.setBranch(entity.getBranch());
        info.setStatus(AppStatus.parse(entity.getStatus()));
        info.setLastDeployed(entity.getLastDeployed());
        info.setLang(entity.getLang());
        info.setLastError(entity.getLastError());
        info.setCreatedAt(entity.getCreatedAt());
        return info;
    }
}
