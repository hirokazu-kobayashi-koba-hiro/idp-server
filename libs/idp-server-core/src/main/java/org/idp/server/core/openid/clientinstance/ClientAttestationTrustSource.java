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
 * <p>draft-ietf-oauth-attestation-based-client-auth-11 Section 10.8 leaves establishing trust in
 * the Client Attester out of scope, so which of these a deployment picks is its own choice rather
 * than a protocol value. The same section does name the shapes, and {@link #attester_jwks} and
 * {@link #x5c} are two of the three it describes. All modes are on-the-wire identical apart from
 * what the JOSE header carries.
 *
 * <ul>
 *   <li>{@link #registered_instance_key} — the Client Instance signs its own Client Attestation JWT
 *       and the server trusts the Client Instance Key it registered beforehand (after verifying the
 *       platform attestation at registration time)
 *   <li>{@link #attester_jwks} — a Client Attester signs the Client Attestation JWT and the server
 *       trusts the attester keys configured for the client
 *   <li>{@link #x5c} — a Client Attester signs the Client Attestation JWT and carries its
 *       certificate chain in the {@code x5c} JOSE header; the server trusts a configured root and
 *       validates the chain to it. Unlike {@link #attester_jwks} the attester can replace its
 *       signing key without every relying client being reconfigured, which is why deployments with
 *       a certificate hierarchy — EUDI Wallet among them — are shaped this way
 *   <li>{@link #undefined} — not configured, or configured with an unknown value. No key resolver
 *       is available, so client authentication fails rather than falling back to a trust source the
 *       operator did not choose.
 * </ul>
 */
public enum ClientAttestationTrustSource {
  registered_instance_key,
  attester_jwks,
  x5c,
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

  public boolean isX5c() {
    return this == x5c;
  }

  public boolean isUndefined() {
    return this == undefined;
  }
}
