package org.idp.server.core.adapters.datasource.identity.event;

import java.util.List;
import org.idp.server.core.identity.event.UserLifecycleEvent;
import org.idp.server.core.identity.event.UserLifecycleEventResult;
import org.idp.server.core.identity.event.UserLifecycleEventResultCommandRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class UserLifecycleEventResultCommandDataSource
    implements UserLifecycleEventResultCommandRepository {

  UserLifecycleEventResultSqlExecutors executors;

  public UserLifecycleEventResultCommandDataSource() {
    this.executors = new UserLifecycleEventResultSqlExecutors();
  }

  @Override
  public void register(
      Tenant tenant,
      UserLifecycleEvent userLifecycleEvent,
      List<UserLifecycleEventResult> userLifecycleEventResults) {
    UserLifecycleEventResultSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, userLifecycleEvent, userLifecycleEventResults);
  }
}
