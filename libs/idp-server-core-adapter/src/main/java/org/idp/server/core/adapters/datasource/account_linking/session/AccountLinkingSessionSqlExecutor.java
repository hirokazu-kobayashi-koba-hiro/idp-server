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

package org.idp.server.core.adapters.datasource.account_linking.session;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.AccountLinkingSessionStatus;
import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AccountLinkingSessionSqlExecutor {

  void insert(Tenant tenant, AccountLinkingSession session);

  /**
   * Conditional status update.
   *
   * @return affected rows; anything but 1 means this caller lost the race
   */
  int updateStatus(
      Tenant tenant,
      AccountLinkingState state,
      AccountLinkingSessionStatus from,
      AccountLinkingSessionStatus to,
      LocalDateTime now);

  void update(Tenant tenant, AccountLinkingSession session);

  void delete(Tenant tenant, AccountLinkingState state);

  Map<String, String> selectOne(Tenant tenant, AccountLinkingState state);
}
