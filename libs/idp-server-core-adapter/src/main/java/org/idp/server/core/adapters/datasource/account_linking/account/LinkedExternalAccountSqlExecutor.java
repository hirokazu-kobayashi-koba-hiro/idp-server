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
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface LinkedExternalAccountSqlExecutor {

  void insert(Tenant tenant, LinkedExternalAccount account);

  void update(Tenant tenant, LinkedExternalAccount account);

  void delete(Tenant tenant, LinkedExternalAccount account);

  void deleteAll(Tenant tenant, UserIdentifier userIdentifier);

  List<Map<String, String>> selectListByUser(Tenant tenant, UserIdentifier userIdentifier);

  Map<String, String> selectByAlias(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias);

  Map<String, String> selectByUserAndFederatedUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId);

  int countByOtherUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId);

  int countByProvider(Tenant tenant, UserIdentifier userIdentifier, ExternalIdpProvider provider);

  /** Reads the row with a row lock held for the current transaction (SELECT ... FOR UPDATE). */
  Map<String, String> selectByAliasForUpdate(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias);
}
