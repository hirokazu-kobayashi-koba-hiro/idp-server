package org.idp.server.core.security;

import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface SecurityEventApi {
  void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent);
}
