package org.idp.server.core.adapters.datasource.identity.event;

import java.util.List;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserLifecycleEventResultSqlExecutor {

  void insert(
      Tenant tenant,
      UserLifecycleEvent userLifecycleEvent,
      List<UserLifecycleEventResult> userLifecycleEventResults);
}
