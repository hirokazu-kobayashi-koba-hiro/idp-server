package org.idp.server.core.security;

import org.idp.server.core.tenant.TenantIdentifier;

public interface SecurityEventApi {
  void handle(TenantIdentifier tenantIdentifier, SecurityEvent securityEvent);
}
