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

package org.idp.server.basic.jose;

import java.util.Map;

/** JoseContext */
public class JoseContext {

  JsonWebSignature jsonWebSignature = new JsonWebSignature();
  JsonWebTokenClaims claims = new JsonWebTokenClaims();
  JsonWebSignatureVerifier jwsVerifier = new JsonWebSignatureVerifier();
  JsonWebKey jsonWebKey = new JsonWebKey();

  public JoseContext() {}

  public JoseContext(
      JsonWebSignature jsonWebSignature,
      JsonWebTokenClaims claims,
      JsonWebSignatureVerifier jwsVerifier,
      JsonWebKey jsonWebKey) {
    this.jsonWebSignature = jsonWebSignature;
    this.claims = claims;
    this.jwsVerifier = jwsVerifier;
    this.jsonWebKey = jsonWebKey;
  }

  public JsonWebSignature jsonWebSignature() {
    return jsonWebSignature;
  }

  public JsonWebTokenClaims claims() {
    return claims;
  }

  public Map<String, Object> claimsAsMap() {
    return claims.toMap();
  }

  public JsonWebSignatureVerifier jwsVerifier() {
    return jwsVerifier;
  }

  public JsonWebKey jsonWebKey() {
    return jsonWebKey;
  }

  public void verifySignature() throws JoseInvalidException {
    if (hasJsonWebSignature()) {
      jwsVerifier.verify(jsonWebSignature);
    }
  }

  public boolean hasJsonWebSignature() {
    return jsonWebSignature.exists();
  }

  public boolean exists() {
    return claims.exists();
  }

  public boolean isSymmetricKey() {
    return jsonWebSignature.isSymmetricType();
  }
}
