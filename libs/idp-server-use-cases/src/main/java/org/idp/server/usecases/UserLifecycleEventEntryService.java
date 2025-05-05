package org.idp.server.usecases;

import java.util.HashMap;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserLifecycleEvent;
import org.idp.server.core.identity.UserLifecycleEventApi;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

@Transaction
public class UserLifecycleEventEntryService implements UserLifecycleEventApi {

  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventEntryService.class);

  @Override
  public void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent) {
    log.info(
        "UserLifecycleEventEntryService.handle: " + userLifecycleEvent.lifecycleOperation().name());
    User user = userLifecycleEvent.user();

    // TODO delete fido keys
    HashMap<String, Object> stringObjectHashMap = user.multiFactorAuthentication();
  }
}
