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

package org.idp.server.account_linking.repository;

import java.util.List;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.LinkedExternalAccountIdentifier;
import org.idp.server.account_linking.LinkedExternalAccountQueries;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface LinkedExternalAccountQueryRepository {

  List<LinkedExternalAccount> findList(Tenant tenant, UserIdentifier userIdentifier);

  LinkedExternalAccount find(Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias);

  /** The tenant's links matching the filters, newest first, for the management API. */
  List<LinkedExternalAccount> findList(Tenant tenant, LinkedExternalAccountQueries queries);

  /** How many of the tenant's links match the filters, regardless of limit and offset. */
  long findTotalCount(Tenant tenant, LinkedExternalAccountQueries queries);

  /** How many of the tenant's links have their tokens encrypted under this data key. */
  long countByDataKey(Tenant tenant, String dataKeyId);

  /** One link by its identifier, whoever's it is. */
  LinkedExternalAccount find(Tenant tenant, LinkedExternalAccountIdentifier identifier);

  /** As {@link #find(Tenant, LinkedExternalAccountIdentifier)}, with the row locked. */
  LinkedExternalAccount lock(Tenant tenant, LinkedExternalAccountIdentifier identifier);

  /**
   * Finds this user's own link to the given external account, if any.
   *
   * <p>Used to tell a re-link from a new link. Scoped to the user because several users may hold a
   * link to the same external account depending on the tenant's duplicate link policy.
   */
  LinkedExternalAccount findByUserAndFederatedUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId);

  /** Whether anyone other than {@code userIdentifier} already links the given external account. */
  boolean existsForOtherUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId);

  /**
   * Reads the row with a row lock held for the current transaction (SELECT ... FOR UPDATE).
   *
   * <p>Refreshing without this breaks the link outright on providers that rotate refresh tokens:
   * two concurrent refreshes both spend the same refresh token, and the loser is left holding one
   * the provider has already invalidated. Retrying does not recover it, so an optimistic check is
   * not enough.
   */
  LinkedExternalAccount lock(Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias);
}
