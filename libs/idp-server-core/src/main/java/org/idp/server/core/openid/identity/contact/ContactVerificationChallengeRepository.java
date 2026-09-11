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

package org.idp.server.core.openid.identity.contact;

import java.util.List;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ContactVerificationChallengeRepository {

  void register(Tenant tenant, ContactVerificationChallenge challenge);

  /**
   * Loads a challenge for update, scoped to its owner.
   *
   * <p>{@code userIdentifier} is a query predicate, not something the caller checks afterwards: a
   * challenge that belongs to another user is indistinguishable from one that does not exist. The
   * row is locked so two concurrent verifies cannot both consume the same code.
   *
   * @return an empty challenge ({@code exists() == false}) when there is no such row for this owner
   */
  ContactVerificationChallenge findForUpdate(
      Tenant tenant,
      ContactVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier);

  /** Records a failed attempt so the retry cap can bite. */
  void countUpAttempts(Tenant tenant, ContactVerificationChallenge challenge);

  /**
   * Whether this user already had a code sent for this operation within {@code cooldownSeconds}.
   *
   * <p>Answered in SQL rather than by loading the row, so the check costs one count and never
   * brings a live code into memory for a caller that is about to be refused.
   */
  boolean sentWithinCooldown(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ContactVerificationOperation operation,
      int cooldownSeconds);

  void delete(Tenant tenant, ContactVerificationChallengeIdentifier identifier);

  /** Management-side lookup by id alone, without the owner predicate the self-service path uses. */
  ContactVerificationChallenge find(
      Tenant tenant, ContactVerificationChallengeIdentifier identifier);

  /** Management-side listing, for support diagnosing a delivery complaint. */
  List<ContactVerificationChallenge> findList(
      Tenant tenant, ContactVerificationChallengeQueries queries);

  long findTotalCount(Tenant tenant, ContactVerificationChallengeQueries queries);

  /**
   * Drops a user's outstanding challenges on one channel, so a committed change invalidates the
   * others. Scoped to the channel: a committed email must not discard an in-flight phone challenge.
   */
  void deleteAllBy(Tenant tenant, UserIdentifier userIdentifier, ContactChannel channel);
}
