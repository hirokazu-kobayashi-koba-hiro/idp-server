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
package org.idp.server.core.openid.extension.attestation;

import org.idp.server.core.openid.clientinstance.ClientInstance;

/**
 * The keys trusted to verify a Client Attestation JWT, and — for a self-signed attestation — the
 * registered Client Instance they belong to.
 */
public class TrustedAttestationKeys {

  String jwks;
  ClientInstance clientInstance;

  TrustedAttestationKeys(String jwks, ClientInstance clientInstance) {
    this.jwks = jwks;
    this.clientInstance = clientInstance;
  }

  /** Keys of a Client Attester; no instance is registered behind them. */
  public static TrustedAttestationKeys of(String jwks) {
    return new TrustedAttestationKeys(jwks, new ClientInstance());
  }

  /** The key of a registered Client Instance. */
  public static TrustedAttestationKeys ofInstance(ClientInstance clientInstance) {
    return new TrustedAttestationKeys(clientInstance.instanceKeyAsJwks(), clientInstance);
  }

  public static TrustedAttestationKeys none() {
    return new TrustedAttestationKeys(null, new ClientInstance());
  }

  public String jwks() {
    return jwks;
  }

  public boolean exists() {
    return jwks != null && !jwks.isEmpty();
  }

  public ClientInstance clientInstance() {
    return clientInstance;
  }
}
