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

import java.time.LocalDateTime;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.AccountLinkingSessionStatus;
import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AccountLinkingSessionCommandRepository {

  void register(Tenant tenant, AccountLinkingSession session);

  /**
   * Moves the stored row from {@code from} to {@code to}, only if it is still in {@code from} and
   * unexpired.
   *
   * <p>Claiming happens <em>before</em> the side effect it guards. At the callback that means the
   * status is taken before the authorization code is exchanged, so a request that loses the race
   * never touches a code the winner is already redeeming — codes are single use, and a second
   * redemption is what makes a provider revoke the grant.
   *
   * @return {@code false} when no row matched, i.e. this caller lost the race
   */
  boolean claim(
      Tenant tenant,
      AccountLinkingState state,
      AccountLinkingSessionStatus from,
      AccountLinkingSessionStatus to,
      LocalDateTime now);

  /** Writes the non-status payload of an already claimed transition. */
  void update(Tenant tenant, AccountLinkingSession session);

  void delete(Tenant tenant, AccountLinkingState state);
}
