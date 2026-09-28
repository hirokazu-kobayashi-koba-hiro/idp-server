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

/**
 * Stores registration challenges.
 *
 * <p>Backed by the database rather than the cache: the challenge is a security control (single use
 * and server-side authorization data), and the cache has a no-operation implementation that would
 * silently disable it.
 */
public interface ClientInstanceRegistrationChallengeRepository {

  void register(Tenant tenant, ClientInstanceRegistrationChallenge challenge);

  /** Returns the challenge, or a non-existing one when unknown. */
  ClientInstanceRegistrationChallenge find(Tenant tenant, String challenge);

  /**
   * Marks the challenge as consumed.
   *
   * @return true when this call consumed it, false when it had already been consumed
   */
  boolean consume(Tenant tenant, String challenge);
}
