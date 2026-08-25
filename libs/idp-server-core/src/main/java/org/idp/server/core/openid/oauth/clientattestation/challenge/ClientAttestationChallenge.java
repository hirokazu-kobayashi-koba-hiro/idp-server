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

import java.io.Serializable;
import java.time.LocalDateTime;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonReadable;

/**
 * A Challenge the Authorization Server handed to a Client Instance.
 *
 * <p>draft-ietf-oauth-attestation-based-client-auth-10 Section 6. The value is opaque to the
 * client, which echoes it back as the {@code challenge} claim of the Client Attestation PoP JWT.
 *
 * <p>Deliberately reusable until {@link #expiresAt()}: Section 9.7 lets a server that issues a
 * challenge bound to a Client Instance session validate the PoP against the single value expected
 * for that session, with no seen-values store. CIBA polling is that case, and a single-use
 * challenge would force one round-trip per poll. Replay of an individual PoP JWT is detected
 * separately through its {@code jti}.
 */
public class ClientAttestationChallenge implements Serializable, JsonReadable {

  String challenge;
  String tenantId;
  LocalDateTime expiresAt;
  LocalDateTime createdAt;

  public ClientAttestationChallenge() {}

  /** Used when issuing: the tenant is applied by the repository, which scopes every statement. */
  public ClientAttestationChallenge(
      String challenge, LocalDateTime expiresAt, LocalDateTime createdAt) {
    this(challenge, null, expiresAt, createdAt);
  }

  /** Used when reading a stored challenge back. */
  public ClientAttestationChallenge(
      String challenge, String tenantId, LocalDateTime expiresAt, LocalDateTime createdAt) {
    this.challenge = challenge;
    this.tenantId = tenantId;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public String value() {
    return challenge;
  }

  public String tenantId() {
    return tenantId;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public boolean exists() {
    return challenge != null && !challenge.isEmpty();
  }

  public boolean isExpired() {
    return expiresAt == null || SystemDateTime.now().isAfter(expiresAt);
  }

  /** True when this challenge is known to the server and still within its lifetime. */
  public boolean isValid() {
    return exists() && !isExpired();
  }
}
