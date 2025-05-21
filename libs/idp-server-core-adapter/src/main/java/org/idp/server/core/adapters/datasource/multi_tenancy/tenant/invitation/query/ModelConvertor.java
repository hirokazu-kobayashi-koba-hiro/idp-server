package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;

class ModelConvertor {

  static TenantInvitation convert(Map<String, String> result) {
    String id = result.get("id");
    String tenantId = result.get("tenant_id");
    String tenantName = result.get("tenant_name");
    String email = result.get("email");
    String roleId = result.get("role_id");
    String roleName = result.get("role_name");
    String url = result.get("url");
    String status = result.get("status");
    int expiresIn = Integer.parseInt(result.get("expires_in"));
    LocalDateTime createdAt = LocalDateTime.parse(result.get("created_at"));
    LocalDateTime expiresAt = LocalDateTime.parse(result.get("expires_at"));
    LocalDateTime updatedAt = LocalDateTime.parse(result.get("updated_at"));
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
        updatedAt);
  }
}
