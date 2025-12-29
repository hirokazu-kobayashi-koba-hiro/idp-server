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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory;
import com.nimbusds.jwt.EncryptedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * JsonWebEncDecrypterFactory
 *
 * <p>Creates JWE decrypters for both asymmetric and symmetric key algorithms.
 *
 * <p>For symmetric algorithms, the key is derived from the client_secret as specified in OpenID
 * Connect Core 1.0 Section 10.2.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Encryption">OIDC Core Section
 *     10.2</a>
 */
public class JsonWebEncDecrypterFactory {

  /**
   * Symmetric key management algorithms.
   *
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518">RFC 7518 - JWA</a>
   */
  private static final Set<JWEAlgorithm> SYMMETRIC_ALGORITHMS =
      Set.of(
          JWEAlgorithm.A128KW,
          JWEAlgorithm.A192KW,
          JWEAlgorithm.A256KW,
          JWEAlgorithm.A128GCMKW,
          JWEAlgorithm.A192GCMKW,
          JWEAlgorithm.A256GCMKW,
          JWEAlgorithm.DIR,
          JWEAlgorithm.PBES2_HS256_A128KW,
          JWEAlgorithm.PBES2_HS384_A192KW,
          JWEAlgorithm.PBES2_HS512_A256KW);

  JsonWebEncryption jsonWebEncryption;
  String privateJwks;
  String secret;
  DefaultJWEDecrypterFactory defaultJWEDecrypterFactory;

  public JsonWebEncDecrypterFactory(
      JsonWebEncryption jsonWebEncryption, String privateJwks, String secret) {
    this.jsonWebEncryption = jsonWebEncryption;
    this.privateJwks = privateJwks;
    this.secret = secret;
    this.defaultJWEDecrypterFactory = new DefaultJWEDecrypterFactory();
  }

  public JsonWebEncryptionDecrypter create()
      throws JsonWebKeyInvalidException, JoseInvalidException {
    try {
      EncryptedJWT encryptedJWT = jsonWebEncryption.value();
      JWEHeader header = encryptedJWT.getHeader();
      JWEAlgorithm algorithm = header.getAlgorithm();

      if (isSymmetricAlgorithm(algorithm)) {
        return createSymmetricDecrypter(header, algorithm);
      } else {
        return createAsymmetricDecrypter(header);
      }
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  private boolean isSymmetricAlgorithm(JWEAlgorithm algorithm) {
    return SYMMETRIC_ALGORITHMS.contains(algorithm);
  }

  /**
   * Creates a decrypter for symmetric key algorithms.
   *
   * <p>OpenID Connect Core 1.0 Section 10.2 specifies that symmetric encryption keys are derived
   * from the client_secret value. The key is constructed from the octets of the UTF-8
   * representation of the client_secret.
   *
   * @param header the JWE header
   * @param algorithm the JWE algorithm
   * @return the decrypter
   * @throws JoseInvalidException if secret is not provided or key derivation fails
   * @throws JOSEException if decrypter creation fails
   */
  private JsonWebEncryptionDecrypter createSymmetricDecrypter(
      JWEHeader header, JWEAlgorithm algorithm) throws JoseInvalidException, JOSEException {

    if (secret == null || secret.isEmpty()) {
      throw new JoseInvalidException(
          "client_secret is required for symmetric JWE decryption but was not provided");
    }

    SecretKey secretKey = deriveSecretKey(algorithm);
    JWEDecrypter decrypter = defaultJWEDecrypterFactory.createJWEDecrypter(header, secretKey);
    return new JsonWebEncryptionDecrypter(decrypter);
  }

  /**
   * Derives a secret key from client_secret for the given algorithm.
   *
   * <p>OpenID Connect Core 1.0 Section 16.19 specifies minimum key lengths: - HS256/A128KW: 32
   * octets - HS384/A192KW: 48 octets - HS512/A256KW: 64 octets
   *
   * @param algorithm the JWE algorithm
   * @return the derived secret key
   */
  private SecretKey deriveSecretKey(JWEAlgorithm algorithm) {
    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    int keyLength = getRequiredKeyLength(algorithm);

    byte[] keyBytes = new byte[keyLength];
    System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, keyLength));

    return new SecretKeySpec(keyBytes, "AES");
  }

  /**
   * Gets the required key length in bytes for the algorithm.
   *
   * @param algorithm the JWE algorithm
   * @return the key length in bytes
   */
  private int getRequiredKeyLength(JWEAlgorithm algorithm) {
    if (algorithm.equals(JWEAlgorithm.A128KW)
        || algorithm.equals(JWEAlgorithm.A128GCMKW)
        || algorithm.equals(JWEAlgorithm.PBES2_HS256_A128KW)) {
      return 16; // 128 bits
    } else if (algorithm.equals(JWEAlgorithm.A192KW)
        || algorithm.equals(JWEAlgorithm.A192GCMKW)
        || algorithm.equals(JWEAlgorithm.PBES2_HS384_A192KW)) {
      return 24; // 192 bits
    } else if (algorithm.equals(JWEAlgorithm.A256KW)
        || algorithm.equals(JWEAlgorithm.A256GCMKW)
        || algorithm.equals(JWEAlgorithm.PBES2_HS512_A256KW)
        || algorithm.equals(JWEAlgorithm.DIR)) {
      return 32; // 256 bits
    }
    return 32; // default to 256 bits
  }

  /**
   * Creates a decrypter for asymmetric key algorithms.
   *
   * @param header the JWE header
   * @return the decrypter
   * @throws JsonWebKeyInvalidException if key is invalid
   * @throws JoseInvalidException if decrypter creation fails
   * @throws JOSEException if decrypter creation fails
   */
  private JsonWebEncryptionDecrypter createAsymmetricDecrypter(JWEHeader header)
      throws JsonWebKeyInvalidException, JoseInvalidException, JOSEException {

    String keyId = jsonWebEncryption.keyId();
    JsonWebKeys privateKeys = JwkParser.parseKeys(privateJwks);
    JsonWebKey privateKey = privateKeys.findBy(keyId);
    JWEDecrypter decrypter =
        defaultJWEDecrypterFactory.createJWEDecrypter(header, privateKey.toPrivateKey());
    return new JsonWebEncryptionDecrypter(decrypter);
  }
}
