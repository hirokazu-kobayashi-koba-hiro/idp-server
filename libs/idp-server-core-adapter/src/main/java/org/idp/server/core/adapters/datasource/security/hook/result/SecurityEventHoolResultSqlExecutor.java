package org.idp.server.core.adapters.datasource.security.hook.result;

import java.util.List;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.hook.SecurityEventHookResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHoolResultSqlExecutor {

  void insert(Tenant tenant, SecurityEvent securityEvent, List<SecurityEventHookResult> results);
}
