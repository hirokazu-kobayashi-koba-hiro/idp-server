package org.idp.server.core.security.repository;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.SecurityEvent;

public interface SecurityEventCommandRepository {
  void register(Tenant tenant, SecurityEvent securityEvent);
}
