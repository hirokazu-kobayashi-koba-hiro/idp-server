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
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.AccountLinkingSessionStatus;
import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.core.adapters.datasource.account_linking.ModelConverter;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AccountLinkingSessionDataSource
    implements AccountLinkingSessionCommandRepository, AccountLinkingSessionQueryRepository {

  AccountLinkingSessionSqlExecutor executor;

  public AccountLinkingSessionDataSource(AccountLinkingSessionSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Tenant tenant, AccountLinkingSession session) {
    executor.insert(tenant, session);
  }

  @Override
  public boolean claim(
      Tenant tenant,
      AccountLinkingState state,
      AccountLinkingSessionStatus from,
      AccountLinkingSessionStatus to,
      LocalDateTime now) {
    return executor.updateStatus(tenant, state, from, to, now) == 1;
  }

  @Override
  public void update(Tenant tenant, AccountLinkingSession session) {
    executor.update(tenant, session);
  }

  @Override
  public void delete(Tenant tenant, AccountLinkingState state) {
    executor.delete(tenant, state);
  }

  @Override
  public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {
    executor.deleteAll(tenant, userIdentifier);
  }

  @Override
  public List<AccountLinkingSession> findList(Tenant tenant, UserIdentifier userIdentifier) {
    List<Map<String, String>> results = executor.selectListByUser(tenant, userIdentifier);
    if (results == null || results.isEmpty()) {
      return List.of();
    }
    return results.stream().map(ModelConverter::convertSession).toList();
  }

  @Override
  public List<AccountLinkingSession> findParkedList(Tenant tenant, int limit) {
    List<Map<String, String>> results = executor.selectParkedList(tenant, limit);
    if (results == null || results.isEmpty()) {
      return List.of();
    }
    return results.stream().map(ModelConverter::convertSession).toList();
  }

  @Override
  public AccountLinkingSession find(Tenant tenant, AccountLinkingState state) {
    // The state arrives on URLs anyone can open, and may be missing. Without one there is no
    // session to find, and querying with a null would fail rather than come back empty.
    if (state == null || !state.exists()) {
      return new AccountLinkingSession();
    }
    Map<String, String> result = executor.selectOne(tenant, state);

    if (result == null || result.isEmpty()) {
      return new AccountLinkingSession();
    }

    return ModelConverter.convertSession(result);
  }
}
