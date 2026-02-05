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

package org.idp.server.platform.jose;

/**
 * JwtSignatureVerifier provides unified JWT signature verification.
 *
 * <p>This class abstracts the common signature verification logic used by:
 *
 * <ul>
 *   <li>Client authentication (client_secret_jwt, private_key_jwt)
 *   <li>Device authentication (device_secret_jwt, private_key_jwt)
 *   <li>JWT Bearer Grant
 * </ul>
 *
 * @see JsonWebSignatureVerifierFactory
 * @see JoseHandler
 */
public class JwtSignatureVerifier {

  /**
   * Verifies JWT signature using symmetric key (HMAC).
   *
   * @param jws the JSON Web Signature to verify
   * @param secret the shared secret for HMAC verification
   * @throws JoseInvalidException if signature verification fails
   */
  public void verifyWithSecret(JsonWebSignature jws, String secret) throws JoseInvalidException {
    try {
      JsonWebSignatureVerifierFactory factory =
          new JsonWebSignatureVerifierFactory(jws, "", secret);
      JsonWebSignatureVerifier verifier = factory.create().getLeft();
      verifier.verify(jws);
    } catch (JsonWebKeyInvalidException e) {
      throw new JoseInvalidException("Invalid key: " + e.getMessage(), e);
    } catch (JsonWebKeyNotFoundException e) {
      throw new JoseInvalidException("Key not found: " + e.getMessage(), e);
    }
  }

  /**
   * Verifies JWT signature using asymmetric key (RSA/EC).
   *
   * @param jws the JSON Web Signature to verify
   * @param jwks the JSON Web Key Set containing the public key
   * @throws JoseInvalidException if signature verification fails
   */
  public void verifyWithJwks(JsonWebSignature jws, String jwks) throws JoseInvalidException {
    try {
      JsonWebSignatureVerifierFactory factory = new JsonWebSignatureVerifierFactory(jws, jwks, "");
      JsonWebSignatureVerifier verifier = factory.create().getLeft();
      verifier.verify(jws);
    } catch (JsonWebKeyInvalidException e) {
      throw new JoseInvalidException("Invalid key: " + e.getMessage(), e);
    } catch (JsonWebKeyNotFoundException e) {
      throw new JoseInvalidException("Key not found: " + e.getMessage(), e);
    }
  }

  /**
   * Verifies JWT signature using the provided credential.
   *
   * @param jws the JSON Web Signature to verify
   * @param credential the JWT credential containing key material
   * @throws JoseInvalidException if signature verification fails
   */
  public void verify(JsonWebSignature jws, JwtCredential credential) throws JoseInvalidException {
    if (credential.isSymmetric()) {
      verifyWithSecret(jws, credential.secret());
    } else if (credential.isAsymmetric()) {
      verifyWithJwks(jws, credential.jwks());
    } else {
      throw new JoseInvalidException("Invalid credential type");
    }
  }
}
