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

import com.nimbusds.jose.*;

/**
 * NestedJsonWebEncryptionCreator
 *
 * <p>Creates nested JWE (signed then encrypted JWT). Supports both asymmetric and symmetric key
 * encryption.
 *
 * <p>For asymmetric encryption, the client's public key (from publicKeys) is used. For symmetric
 * encryption (A128KW, A256KW, dir, etc.), the key is derived from the client_secret as specified in
 * OpenID Connect Core 1.0 Section 10.2.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7516#appendix-A.2">RFC 7516 Appendix
 *     A.2</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Encryption">OIDC Core Section
 *     10.2</a>
 */
public class NestedJsonWebEncryptionCreator {

  JsonWebSignature jsonWebSignature;
  String jweAlgorithm;
  String encryptionMethod;
  String publicKeys;
  String secret;

  /**
   * Creates a nested JWE creator.
   *
   * <p>Provide all parameters; the creator will determine which to use based on the algorithm:
   *
   * <ul>
   *   <li>For symmetric algorithms (A128KW, A256KW, dir, etc.): uses secret
   *   <li>For asymmetric algorithms (RSA1_5, RSA-OAEP, ECDH-ES, etc.): uses publicKeys
   * </ul>
   *
   * @param jsonWebSignature the signed JWT to encrypt
   * @param jweAlgorithm the JWE algorithm
   * @param encryptionMethod the content encryption method (e.g., A128CBC-HS256)
   * @param publicKeys the client's public keys (JWKS format), used for asymmetric encryption
   * @param secret the client_secret for key derivation, used for symmetric encryption
   */
  public NestedJsonWebEncryptionCreator(
      JsonWebSignature jsonWebSignature,
      String jweAlgorithm,
      String encryptionMethod,
      String publicKeys,
      String secret) {
    this.jsonWebSignature = jsonWebSignature;
    this.jweAlgorithm = jweAlgorithm;
    this.encryptionMethod = encryptionMethod;
    this.publicKeys = publicKeys;
    this.secret = secret;
  }

  /**
   * Creates the encrypted JWT.
   *
   * @return the serialized JWE
   * @throws JoseInvalidException if encryption fails
   */
  public String create() throws JoseInvalidException {
    try {
      JWEAlgorithm algorithm = JWEAlgorithm.parse(jweAlgorithm);
      EncryptionMethod method = EncryptionMethod.parse(encryptionMethod);

      JsonWebEncrypterFactory factory =
          new JsonWebEncrypterFactory(jweAlgorithm, publicKeys, secret);
      JWEEncrypter jweEncrypter = factory.create();

      JWEObject jweObject =
          new JWEObject(
              new JWEHeader.Builder(algorithm, method).contentType("JWT").build(),
              new Payload(jsonWebSignature.value()));
      jweObject.encrypt(jweEncrypter);
      return jweObject.serialize();
    } catch (JsonWebKeyInvalidException | JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
