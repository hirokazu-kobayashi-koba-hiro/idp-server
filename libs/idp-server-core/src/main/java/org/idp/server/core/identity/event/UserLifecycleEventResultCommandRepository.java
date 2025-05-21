package org.idp.server.core.identity.event;

import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserLifecycleEventResultCommandRepository {

  void register(
      Tenant tenant,
      UserLifecycleEvent userLifecycleEvent,
      List<UserLifecycleEventResult> userLifecycleEventResults);
}
