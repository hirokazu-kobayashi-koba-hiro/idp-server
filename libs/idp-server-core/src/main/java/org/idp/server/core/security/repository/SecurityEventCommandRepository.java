package org.idp.server.core.security.repository;

import org.idp.server.core.security.SecurityEvent;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventCommandRepository {
  void register(Tenant tenant, SecurityEvent securityEvent);
}
