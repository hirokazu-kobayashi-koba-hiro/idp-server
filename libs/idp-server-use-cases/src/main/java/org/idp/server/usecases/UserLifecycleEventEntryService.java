package org.idp.server.usecases;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.identity.UserLifecycleEvent;
import org.idp.server.core.identity.UserLifecycleEventApi;
import org.idp.server.core.identity.deletion.UserRelatedDataDeletionExecutor;
import org.idp.server.core.identity.deletion.UserRelatedDataDeletionExecutors;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

@Transaction
public class UserLifecycleEventEntryService implements UserLifecycleEventApi {

  UserRelatedDataDeletionExecutors userRelatedDataDeletionExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventEntryService.class);

  public UserLifecycleEventEntryService(
      UserRelatedDataDeletionExecutors userRelatedDataDeletionExecutors) {
    this.userRelatedDataDeletionExecutors = userRelatedDataDeletionExecutors;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent) {
    log.info(
        "UserLifecycleEventEntryService.handle: " + userLifecycleEvent.lifecycleOperation().name());

    for (UserRelatedDataDeletionExecutor executor : userRelatedDataDeletionExecutors) {

      if (executor.shouldExecute(userLifecycleEvent)) {
        log.info("UserLifecycleEventEntryService.execute: " + executor.name());
        executor.execute(userLifecycleEvent);
      }
    }
  }
}
