/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
