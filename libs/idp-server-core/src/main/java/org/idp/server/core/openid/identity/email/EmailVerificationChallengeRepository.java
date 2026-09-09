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

package org.idp.server.core.openid.identity.email;

import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface EmailVerificationChallengeRepository {

  void register(Tenant tenant, EmailVerificationChallenge challenge);

  /**
   * Loads a challenge for update, scoped to its owner.
   *
   * <p>{@code userIdentifier} is a query predicate, not something the caller checks afterwards: a
   * challenge that belongs to another user is indistinguishable from one that does not exist. The
   * row is locked so two concurrent verifies cannot both consume the same code.
   *
   * @return an empty challenge ({@code exists() == false}) when there is no such row for this owner
   */
  EmailVerificationChallenge findForUpdate(
      Tenant tenant,
      EmailVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier);

  /** Records a failed attempt so the retry cap can bite. */
  void countUpAttempts(Tenant tenant, EmailVerificationChallenge challenge);

  void delete(Tenant tenant, EmailVerificationChallengeIdentifier identifier);

  /** Drops every outstanding challenge of a user, so a committed change invalidates the others. */
  void deleteAllBy(Tenant tenant, UserIdentifier userIdentifier);
}
