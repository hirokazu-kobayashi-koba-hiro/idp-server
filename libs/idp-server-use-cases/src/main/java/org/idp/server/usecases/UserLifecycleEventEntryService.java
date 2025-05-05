package org.idp.server.usecases;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.identity.event.*;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

@Transaction
public class UserLifecycleEventEntryService implements UserLifecycleEventApi {

  UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap;
  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventEntryService.class);

  public UserLifecycleEventEntryService(
      UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap) {
    this.userLifecycleEventExecutorsMap = userLifecycleEventExecutorsMap;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent) {
    log.info("UserLifecycleEventEntryService.handle: " + userLifecycleEvent.lifecycleType().name());

    List<UserLifecycleEventResult> result = new ArrayList<>();
    UserLifecycleEventExecutors userLifecycleEventExecutors =
        userLifecycleEventExecutorsMap.find(userLifecycleEvent.lifecycleType());

    for (UserLifecycleEventExecutor executor : userLifecycleEventExecutors) {

      if (executor.shouldExecute(userLifecycleEvent)) {
        log.info("UserLifecycleEventEntryService.execute: " + executor.name());
        UserLifecycleEventResult deletionResult = executor.execute(userLifecycleEvent);
        result.add(deletionResult);
      }
    }
  }
}
