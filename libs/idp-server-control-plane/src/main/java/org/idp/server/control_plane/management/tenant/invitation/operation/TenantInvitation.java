/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant.invitation.operation;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.date.SystemDateTime;

public class TenantInvitation {

  String id;
  String tenantId;
  String tenantName;
  String email;
  String roleId;
  String roleName;
  String url;
  String status;
  int expiresIn;
  LocalDateTime createdAt;
  LocalDateTime expiresAt;
  LocalDateTime updatedAt;

  public TenantInvitation() {}

  public TenantInvitation(
      String id,
      String tenantId,
      String tenantName,
      String email,
      String roleId,
      String roleName,
      String url,
      String status,
      int expiresIn,
      LocalDateTime createdAt,
      LocalDateTime expiresAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.tenantName = tenantName;
    this.email = email;
    this.roleId = roleId;
    this.roleName = roleName;
    this.url = url;
    this.status = status;
    this.expiresIn = expiresIn;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.updatedAt = updatedAt;
  }

  public TenantInvitation updateWithStatus(String status) {
    return new TenantInvitation(
        id,
        tenantId,
        tenantName,
        email,
        roleId,
        roleName,
        url,
        status,
        expiresIn,
        createdAt,
        expiresAt,
        SystemDateTime.now());
  }

  public String id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public String tenantName() {
    return tenantName;
  }

  public String email() {
    return email;
  }

  public String roleId() {
    return roleId;
  }

  public String roleName() {
    return roleName;
  }

  public String url() {
    return url;
  }

  public String status() {
    return status;
  }

  public int expiresIn() {
    return expiresIn;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("tenant_id", tenantId);
    map.put("tenant_name", tenantName);
    map.put("email", email);
    map.put("role_id", roleId);
    map.put("role_name", roleName);
    map.put("url", url);
    map.put("status", status);
    map.put("expires_in", expiresIn);
    map.put("created_at", createdAt.toString());
    map.put("expires_at", expiresAt.toString());
    map.put("updated_at", updatedAt.toString());

    return map;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
