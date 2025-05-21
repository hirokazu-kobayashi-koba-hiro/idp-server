package org.idp.server.platform.security;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface SecurityEventApi {
  void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent);
}
