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

package org.idp.server.authentication.interactors.attribute_verification;

import org.idp.server.core.openid.authentication.AuthenticationInteractionResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Bounds how often entered values can be guessed, per named interaction, in two places.
 *
 * <ul>
 *   <li>Per transaction: the failures already recorded under the interaction's name. Read from the
 *       database, so it holds even when the cache is unavailable.
 *   <li>Per user, across authorization requests: a counter in the cache that starting a new request
 *       does not reset. The window starts at the first counted attempt.
 * </ul>
 */
class AttributeVerificationAttempts {

  CacheStore cacheStore;

  AttributeVerificationAttempts(CacheStore cacheStore) {
    this.cacheStore = cacheStore;
  }

  /**
   * Whether no further guess is allowed. Counts this attempt against the user before the comparison
   * is made, so concurrent attempts cannot all slip under the limit.
   */
  boolean exhausted(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      User user,
      String interaction,
      AttributeVerificationConfig config) {
    return exhaustedInTransaction(transaction, type, interaction, config)
        || exhaustedForUser(key(tenant, user, interaction), config);
  }

  /** A match clears the user's count. */
  void clear(Tenant tenant, User user, String interaction) {
    cacheStore.delete(key(tenant, user, interaction));
  }

  private static boolean exhaustedInTransaction(
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      String interaction,
      AttributeVerificationConfig config) {
    AuthenticationInteractionResults results = transaction.interactionResults();
    if (results == null || !results.contains(type.name())) {
      return false;
    }
    AuthenticationInteractionResult recorded = results.get(type.name());
    if (!recorded.hasInteractions()) {
      return false;
    }
    AuthenticationInteractionResult byInteraction = recorded.interactions().get(interaction);
    return byInteraction != null && byInteraction.failureCount() >= config.maxAttempts();
  }

  private boolean exhaustedForUser(String key, AttributeVerificationConfig config) {
    long attempts = cacheStore.increment(key, config.lockoutSeconds());
    return attempts > config.maxAttempts();
  }

  private static String key(Tenant tenant, User user, String interaction) {
    return String.format(
        "attribute_verification_attempt:%s:%s:%s",
        tenant.identifierValue(), user.sub(), interaction);
  }
}
