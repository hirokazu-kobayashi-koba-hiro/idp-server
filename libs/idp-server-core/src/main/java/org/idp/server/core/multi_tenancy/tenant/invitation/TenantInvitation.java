package org.idp.server.core.multi_tenancy.tenant.invitation;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TenantInvitation {

  String id;
  String tenantId;
  String tenantName;
  String email;
  String roleId;
  String roleName;
  String url;
  int expiresIn;
  LocalDateTime createdAt;
  LocalDateTime expiresAt;

  public TenantInvitation() {}

  public TenantInvitation(
      String id,
      String tenantId,
      String tenantName,
      String email,
      String roleId,
      String roleName,
      String url,
      int expiresIn,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.tenantName = tenantName;
    this.email = email;
    this.roleId = roleId;
    this.roleName = roleName;
    this.url = url;
    this.expiresIn = expiresIn;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
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

  public int expiresIn() {
    return expiresIn;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
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
    map.put("expires_in", expiresIn);
    map.put("created_at", createdAt.toString());
    map.put("expires_at", expiresAt.toString());

    return map;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
