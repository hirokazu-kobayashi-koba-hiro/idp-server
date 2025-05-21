package org.idp.server.platform.security.repository;

import java.util.List;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHookResultCommandRepository {
  void register(Tenant tenant, SecurityEvent securityEvent, List<SecurityEventHookResult> results);
}
