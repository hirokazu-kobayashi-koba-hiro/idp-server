package org.idp.server.platform.security.repository;

import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventCommandRepository {
  void register(Tenant tenant, SecurityEvent securityEvent);
}
