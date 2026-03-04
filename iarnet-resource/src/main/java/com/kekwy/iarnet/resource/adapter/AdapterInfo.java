package com.kekwy.iarnet.resource.adapter;

import com.kekwy.iarnet.proto.adapter.ResourceCapacity;

import java.time.Instant;
import java.util.List;

/**
 * 记录一个已注册 Adapter 的元数据与实时状态。
 */
public class AdapterInfo {

    public enum Status {
        ONLINE, OFFLINE
    }

    private final String adapterId;
    private final String name;
    private final String description;
    private final String adapterType;
    private final List<String> tags;

    private volatile ResourceCapacity capacity;
    private volatile ResourceCapacity usage;
    private volatile Instant lastHeartbeat;
    private volatile Status status;

    public AdapterInfo(String adapterId, String name, String description,
                       String adapterType, ResourceCapacity capacity, List<String> tags) {
        this.adapterId = adapterId;
        this.name = name;
        this.description = description;
        this.adapterType = adapterType;
        this.capacity = capacity;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.lastHeartbeat = Instant.now();
        this.status = Status.ONLINE;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAdapterType() {
        return adapterType;
    }

    public List<String> getTags() {
        return tags;
    }

    public ResourceCapacity getCapacity() {
        return capacity;
    }

    public ResourceCapacity getUsage() {
        return usage;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public Status getStatus() {
        return status;
    }

    public void setCapacity(ResourceCapacity capacity) {
        this.capacity = capacity;
    }

    public void updateUsage(ResourceCapacity usage) {
        this.usage = usage;
        this.lastHeartbeat = Instant.now();
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "AdapterInfo{id=" + adapterId + ", name=" + name +
                ", type=" + adapterType + ", status=" + status + "}";
    }
}
