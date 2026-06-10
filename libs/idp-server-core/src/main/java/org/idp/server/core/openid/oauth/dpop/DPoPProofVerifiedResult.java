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

package org.idp.server.core.openid.oauth.dpop;

import org.idp.server.platform.jose.JsonWebKey;

/**
 * Result of a successful DPoP proof verification.
 *
 * <p>Contains the verified public key and its JWK Thumbprint for token binding.
 */
public class DPoPProofVerifiedResult {

  JsonWebKey publicKey;
  JwkThumbprint jwkThumbprint;

  public DPoPProofVerifiedResult() {}

  public DPoPProofVerifiedResult(JsonWebKey publicKey, JwkThumbprint jwkThumbprint) {
    this.publicKey = publicKey;
    this.jwkThumbprint = jwkThumbprint;
  }

  public JsonWebKey publicKey() {
    return publicKey;
  }

  public JwkThumbprint jwkThumbprint() {
    return jwkThumbprint;
  }

  public boolean exists() {
    return jwkThumbprint != null && jwkThumbprint.exists();
  }
}
