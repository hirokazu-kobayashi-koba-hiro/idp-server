package org.idp.server.core.adapters.datasource.security.hook;

import java.util.List;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.hook.SecurityEventHookResult;

public interface SecurityEventHoolResultSqlExecutor {

  void insert(Tenant tenant, SecurityEvent securityEvent, List<SecurityEventHookResult> results);
}
