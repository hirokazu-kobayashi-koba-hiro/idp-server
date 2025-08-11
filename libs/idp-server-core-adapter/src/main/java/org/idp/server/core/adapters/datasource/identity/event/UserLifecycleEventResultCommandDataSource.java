/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.identity.event;

import java.util.List;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventResult;
import org.idp.server.core.openid.identity.event.UserLifecycleEventResultCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
    if (userLifecycleEventResults.isEmpty()) {
      return;
    }
    UserLifecycleEventResultSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, userLifecycleEvent, userLifecycleEventResults);
  }
}
