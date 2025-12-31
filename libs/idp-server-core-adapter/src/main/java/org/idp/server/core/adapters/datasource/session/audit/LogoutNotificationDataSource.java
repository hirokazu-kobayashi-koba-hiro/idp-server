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

package org.idp.server.core.adapters.datasource.session.audit;

import org.idp.server.core.openid.session.logout.LogoutNotification;
import org.idp.server.core.openid.session.logout.LogoutNotificationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class LogoutNotificationDataSource implements LogoutNotificationRepository {

  private final LogoutNotificationSqlExecutor executor;

  public LogoutNotificationDataSource(LogoutNotificationSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void save(Tenant tenant, LogoutNotification notification) {
    executor.insert(tenant, notification);
  }

  @Override
  public void update(Tenant tenant, LogoutNotification notification) {
    executor.update(tenant, notification);
  }
}
