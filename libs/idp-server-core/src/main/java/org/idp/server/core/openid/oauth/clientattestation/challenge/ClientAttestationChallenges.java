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

import java.time.LocalDateTime;
import org.idp.server.platform.crypto.ServerNonceCodec;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Issues the Challenges of draft-ietf-oauth-attestation-based-client-auth-11 Section 6 and
 * recognizes them when they come back, without storing them.
 *
 * <p>A Challenge proves freshness: that the Client Attestation PoP JWT carrying it was made after
 * this server handed it out. It is reusable for its whole lifetime (Section 12.1 offers issuing
 * challenges without storing the seen ones as one of its three approaches, and CIBA polling is the
 * case that makes reuse worth having), so recognizing it needs nothing but the value itself: a
 * {@link ServerNonceCodec} value, MAC'd with a key only this server holds, bound to the tenant and
 * to this purpose, carrying its expiry.
 *
 * <p>Nothing is written when a Challenge is issued. The endpoint that issues them is
 * unauthenticated (Section 6.3), so an issuance that wrote a row would let anyone grow a table.
 *
 * <p>Not bound to a Client Instance: the issuing request carries no credential to bind to. Whose
 * PoP carries the Challenge is already settled by the PoP's signature with the instance key.
 */
public class ClientAttestationChallenges {

  static final String PURPOSE = "client_attestation_challenge";

  ServerNonceCodec codec;

  public ClientAttestationChallenges(ServerNonceCodec codec) {
    this.codec = codec;
  }

  public ClientAttestationChallenge issue(Tenant tenant, int expiresInSeconds) {
    LocalDateTime now = SystemDateTime.now();
    LocalDateTime expiresAt = now.plusSeconds(expiresInSeconds);
    String value =
        codec.issue(PURPOSE, tenant.identifierValue(), SystemDateTime.toEpochSecond(expiresAt));
    return new ClientAttestationChallenge(value, expiresAt, now);
  }

  /**
   * The Challenge the value stands for, or a non-existing one when it is not a Challenge this
   * server issued for the tenant. Whether it is still valid is {@link
   * ClientAttestationChallenge#isValid()}.
   */
  public ClientAttestationChallenge find(Tenant tenant, String value) {
    Long expiresAt = codec.verify(PURPOSE, tenant.identifierValue(), value);
    if (expiresAt == null) {
      return new ClientAttestationChallenge();
    }
    return new ClientAttestationChallenge(value, SystemDateTime.fromEpochSecond(expiresAt), null);
  }
}
