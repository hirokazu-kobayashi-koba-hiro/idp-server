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

package org.idp.server.core.adapters.datasource.account_linking.account;

import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.adapters.datasource.account_linking.ModelConverter;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class LinkedExternalAccountDataSource
    implements LinkedExternalAccountCommandRepository, LinkedExternalAccountQueryRepository {

  LinkedExternalAccountSqlExecutor executor;

  public LinkedExternalAccountDataSource(LinkedExternalAccountSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Tenant tenant, LinkedExternalAccount account) {
    executor.insert(tenant, account);
  }

  @Override
  public void update(Tenant tenant, LinkedExternalAccount account) {
    executor.update(tenant, account);
  }

  @Override
  public void delete(Tenant tenant, LinkedExternalAccount account) {
    executor.delete(tenant, account);
  }

  @Override
  public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {
    executor.deleteAll(tenant, userIdentifier);
  }

  @Override
  public List<LinkedExternalAccount> findList(Tenant tenant, UserIdentifier userIdentifier) {
    return executor.selectListByUser(tenant, userIdentifier).stream()
        .map(ModelConverter::convertAccount)
        .toList();
  }

  @Override
  public LinkedExternalAccount find(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
    return convert(executor.selectByAlias(tenant, userIdentifier, alias));
  }

  @Override
  public LinkedExternalAccount findByUserAndFederatedUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId) {
    return convert(
        executor.selectByUserAndFederatedUser(tenant, userIdentifier, provider, federatedUserId));
  }

  @Override
  public boolean existsForOtherUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId) {
    return executor.countByOtherUser(tenant, userIdentifier, provider, federatedUserId) > 0;
  }

  @Override
  public int countByProvider(
      Tenant tenant, UserIdentifier userIdentifier, ExternalIdpProvider provider) {
    return executor.countByProvider(tenant, userIdentifier, provider);
  }

  @Override
  public LinkedExternalAccount lock(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
    return convert(executor.selectByAliasForUpdate(tenant, userIdentifier, alias));
  }

  private LinkedExternalAccount convert(Map<String, String> result) {
    if (result == null || result.isEmpty()) {
      return new LinkedExternalAccount();
    }
    return ModelConverter.convertAccount(result);
  }
}
