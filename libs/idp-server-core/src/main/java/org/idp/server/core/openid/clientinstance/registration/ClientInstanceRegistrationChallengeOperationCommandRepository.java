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

package org.idp.server.core.openid.clientinstance.registration;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ClientInstanceRegistrationChallengeOperationCommandRepository {

  /**
   * Delete expired client instance registration challenges across <strong>all tenants</strong>
   * (system-wide batch).
   *
   * <p>Expiry is the only condition, never {@code used_at}. A consumed row is deliberately kept
   * while it is still valid so a replay stays distinguishable from an unknown challenge in the
   * audit trail (see the table comment); dropping it early would turn the first into the second.
   * Once expired that distinction buys nothing, because both are rejected either way.
   *
   * <p>The {@code tenant} argument carries the admin tenant context used by the caller for audit /
   * logging purposes; it is intentionally not applied as a SQL filter.
   *
   * @param tenant admin tenant context (not used as SQL filter)
   * @param limit max number of rows to delete in one batch
   * @return number of rows deleted
   */
  int deleteExpired(Tenant tenant, int limit);
}
