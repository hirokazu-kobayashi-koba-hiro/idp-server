package org.idp.server.core.security;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface SecurityEventApi {
  void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent);
}
