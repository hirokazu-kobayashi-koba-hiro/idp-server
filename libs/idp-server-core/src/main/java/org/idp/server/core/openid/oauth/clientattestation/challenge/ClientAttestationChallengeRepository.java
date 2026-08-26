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

package org.idp.server.core.openid.oauth.clientattestation.challenge;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Stores the Challenges issued for Attestation-Based Client Authentication.
 *
 * <p>Backed by the database rather than the cache: the challenge is a security control, and the
 * cache has a no-operation implementation that would silently accept every value.
 *
 * <p>There is no consume operation. A challenge stays valid for its whole lifetime by design (see
 * {@link ClientAttestationChallenge}).
 */
public interface ClientAttestationChallengeRepository {

  void register(Tenant tenant, ClientAttestationChallenge challenge);

  /** Returns the challenge, or a non-existing one when unknown. */
  ClientAttestationChallenge find(Tenant tenant, String challenge);
}
