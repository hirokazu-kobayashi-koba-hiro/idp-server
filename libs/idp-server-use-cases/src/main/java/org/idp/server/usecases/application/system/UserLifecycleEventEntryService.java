package org.idp.server.usecases.application.system;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.identity.event.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

@Transaction
public class UserLifecycleEventEntryService implements UserLifecycleEventApi {

  UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap;
  UserLifecycleEventResultCommandRepository resultCommandRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventEntryService.class);

  public UserLifecycleEventEntryService(
      UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap,
      UserLifecycleEventResultCommandRepository resultCommandRepository) {
    this.userLifecycleEventExecutorsMap = userLifecycleEventExecutorsMap;
    this.resultCommandRepository = resultCommandRepository;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent) {
    log.info("UserLifecycleEventEntryService.handle: " + userLifecycleEvent.lifecycleType().name());

    List<UserLifecycleEventResult> results = new ArrayList<>();
    UserLifecycleEventExecutors userLifecycleEventExecutors =
        userLifecycleEventExecutorsMap.find(userLifecycleEvent.lifecycleType());

    for (UserLifecycleEventExecutor executor : userLifecycleEventExecutors) {

      if (executor.shouldExecute(userLifecycleEvent)) {
        log.info("UserLifecycleEventEntryService.execute: " + executor.name());
        UserLifecycleEventResult deletionResult = executor.execute(userLifecycleEvent);
        results.add(deletionResult);
      }
    }

    resultCommandRepository.register(userLifecycleEvent.tenant(), userLifecycleEvent, results);
  }
}
