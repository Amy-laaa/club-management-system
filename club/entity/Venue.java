package com.club.entity;

import java.time.LocalDateTime;

/**
 * 场地实体, 对应 t_venue (设计说明书 表 2-16)。
 */
public class Venue {
    private Long venueId;
    private String venueName;
    private Integer capacity;
    private Integer status;         // 0停用 1可用
    private LocalDateTime createdAt;

    public Long getVenueId() { return venueId; }
    public void setVenueId(Long venueId) { this.venueId = venueId; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
