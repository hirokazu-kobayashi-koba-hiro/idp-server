package org.idp.server.core.adapters.datasource.security.hook.result;

import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.SecurityEventHookResult;

public interface SecurityEventHoolResultSqlExecutor {

  void insert(Tenant tenant, SecurityEvent securityEvent, List<SecurityEventHookResult> results);
}
