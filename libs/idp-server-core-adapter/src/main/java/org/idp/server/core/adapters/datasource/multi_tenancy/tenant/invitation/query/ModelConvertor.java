package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;

class ModelConvertor {

  static TenantInvitation convert(Map<String, String> result) {
    String id = result.get("id");
    String tenantId = result.get("tenant_id");
    String tenantName = result.get("tenant_name");
    String email = result.get("email");
    String role = result.get("role");
    String url = result.get("url");
    int expiresIn = Integer.parseInt(result.get("expires_in"));
    LocalDateTime createdAt = LocalDateTime.parse(result.get("created_at"));
    LocalDateTime expiresAt = LocalDateTime.parse(result.get("expires_at"));
    return new TenantInvitation(
        id, tenantId, tenantName, email, role, url, expiresIn, createdAt, expiresAt);
  }
}
