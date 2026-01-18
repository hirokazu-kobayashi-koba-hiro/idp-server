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
 * JwtCredential abstracts the key material used for JWT signature verification.
 *
 * <p>This class provides a unified interface for both symmetric (shared secret) and asymmetric
 * (public key via JWKS) credentials.
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * // Symmetric credential (HMAC)
 * JwtCredential credential = JwtCredential.symmetric("shared-secret-value");
 *
 * // Asymmetric credential (RSA/EC)
 * JwtCredential credential = JwtCredential.asymmetric("{\"keys\":[...]}");
 *
 * // Verify signature
 * JwtSignatureVerifier verifier = new JwtSignatureVerifier();
 * verifier.verify(jws, credential);
 * }</pre>
 */
public class JwtCredential {

  private final JwtCredentialType type;
  private final String secret;
  private final String jwks;

  private JwtCredential(JwtCredentialType type, String secret, String jwks) {
    this.type = type;
    this.secret = secret;
    this.jwks = jwks;
  }

  /**
   * Creates a symmetric credential using a shared secret.
   *
   * @param secret the shared secret for HMAC signing
   * @return symmetric JWT credential
   */
  public static JwtCredential symmetric(String secret) {
    return new JwtCredential(JwtCredentialType.SYMMETRIC, secret, null);
  }

  /**
   * Creates an asymmetric credential using JWKS.
   *
   * @param jwks the JSON Web Key Set containing the public key
   * @return asymmetric JWT credential
   */
  public static JwtCredential asymmetric(String jwks) {
    return new JwtCredential(JwtCredentialType.ASYMMETRIC, null, jwks);
  }

  /**
   * Creates a credential from both secret and JWKS, determining type automatically.
   *
   * <p>If JWKS is provided, asymmetric is preferred. Otherwise, symmetric is used.
   *
   * @param secret the shared secret (may be null)
   * @param jwks the JWKS (may be null)
   * @return JWT credential
   */
  public static JwtCredential of(String secret, String jwks) {
    if (jwks != null && !jwks.isEmpty()) {
      return asymmetric(jwks);
    }
    if (secret != null && !secret.isEmpty()) {
      return symmetric(secret);
    }
    return new JwtCredential(JwtCredentialType.NONE, null, null);
  }

  public boolean isSymmetric() {
    return type == JwtCredentialType.SYMMETRIC;
  }

  public boolean isAsymmetric() {
    return type == JwtCredentialType.ASYMMETRIC;
  }

  public boolean isNone() {
    return type == JwtCredentialType.NONE;
  }

  public String secret() {
    return secret;
  }

  public String jwks() {
    return jwks;
  }

  public JwtCredentialType type() {
    return type;
  }

  public boolean exists() {
    return type != JwtCredentialType.NONE;
  }

  public enum JwtCredentialType {
    SYMMETRIC,
    ASYMMETRIC,
    NONE
  }
}
