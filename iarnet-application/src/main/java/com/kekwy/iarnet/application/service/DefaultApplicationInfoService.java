package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.ApplicationInfoEntity;
import com.kekwy.iarnet.application.repository.ApplicationInfoRepository;
import com.kekwy.iarnet.model.ApplicationInfo;
import com.kekwy.iarnet.model.ID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultApplicationInfoService implements ApplicationInfoService {

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
    public ApplicationInfo create(ApplicationInfo input) {
        ApplicationInfoEntity entity = new ApplicationInfoEntity();
        entity.setName(input.getName());
        entity.setGitUrl(input.getGitUrl());
        entity.setBranch(input.getBranch() != null ? input.getBranch() : "main");
        entity.setDescription(input.getDescription() != null ? input.getDescription() : "");
        entity.setRunnerEnv(input.getRunnerEnv());
        entity.setStatus("importing");
        entity = repository.save(entity);
        return toApplicationInfo(entity);
    }

    @Override
    public ApplicationInfo update(Long id, ApplicationInfo input) {
        ApplicationInfoEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + id));
        if (input.getName() != null) entity.setName(input.getName());
        if (input.getDescription() != null) entity.setDescription(input.getDescription());
        if (input.getRunnerEnv() != null) entity.setRunnerEnv(input.getRunnerEnv());
        entity = repository.save(entity);
        return toApplicationInfo(entity);
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Override
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", repository.count());
        stats.put("running", repository.countByStatus("running"));
        stats.put("stopped", repository.countByStatus("stopped"));
        stats.put("undeployed", repository.countByStatus("idle"));
        stats.put("failed", repository.countByStatus("error"));
        stats.put("importing", repository.countByStatus("importing"));
        return stats;
    }

    private ApplicationInfo toApplicationInfo(ApplicationInfoEntity entity) {
        ApplicationInfo info = new ApplicationInfo();
        info.setId(ID.of(String.valueOf(entity.getId())));
        info.setName(entity.getName());
        info.setDescription(entity.getDescription());
        info.setGitUrl(entity.getGitUrl());
        info.setBranch(entity.getBranch());
        info.setStatus(entity.getStatus());
        info.setLastDeployed(entity.getLastDeployed());
        info.setRunnerEnv(entity.getRunnerEnv());
        info.setCreatedAt(entity.getCreatedAt());
        return info;
    }
}
