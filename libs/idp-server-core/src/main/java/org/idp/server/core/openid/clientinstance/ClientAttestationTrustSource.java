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

package org.idp.server.core.openid.clientinstance;

import java.util.Objects;

/**
 * Where the Authorization Server takes its trust from when verifying a Client Attestation JWT.
 *
 * <p>draft-ietf-oauth-attestation-based-client-auth-10 Section 9.8 leaves trust management and key
 * resolution out of scope, so this is an idp-server deployment choice rather than a protocol value.
 * Both modes are on-the-wire identical.
 *
 * <ul>
 *   <li>{@link #registered_instance_key} — the Client Instance signs its own Client Attestation JWT
 *       and the server trusts the Client Instance Key it registered beforehand (after verifying the
 *       platform attestation at registration time)
 *   <li>{@link #attester_jwks} — a Client Attester signs the Client Attestation JWT and the server
 *       trusts the attester keys configured for the client
 *   <li>{@link #undefined} — not configured, or configured with an unknown value. No key resolver
 *       is available, so client authentication fails rather than falling back to a trust source the
 *       operator did not choose.
 * </ul>
 */
public enum ClientAttestationTrustSource {
  registered_instance_key,
  attester_jwks,
  undefined;

  public static ClientAttestationTrustSource of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ClientAttestationTrustSource trustSource : ClientAttestationTrustSource.values()) {
      if (trustSource.name().equals(value)) {
        return trustSource;
      }
    }
    return undefined;
  }

  public boolean isRegisteredInstanceKey() {
    return this == registered_instance_key;
  }

  public boolean isAttesterJwks() {
    return this == attester_jwks;
  }

  public boolean isUndefined() {
    return this == undefined;
  }
}
