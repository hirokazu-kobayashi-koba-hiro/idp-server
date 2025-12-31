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
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.idp.server.platform.exception.UnSupportedException;

/**
 * JsonWebEncrypterFactory
 *
 * <p>Creates JWE encrypters for both asymmetric and symmetric key algorithms.
 *
 * <p>For symmetric algorithms, the key is derived from the client_secret as specified in OpenID
 * Connect Core 1.0 Section 10.2. For asymmetric algorithms, the client's public key is used.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Encryption">OIDC Core Section
 *     10.2</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518">RFC 7518 - JWA</a>
 */
public class JsonWebEncrypterFactory {

  String jweAlgorithm;
  String publicKeys;
  String secret;

  /**
   * Creates an encrypter factory.
   *
   * <p>Provide all parameters; the factory will determine which to use based on the algorithm:
   *
   * <ul>
   *   <li>For symmetric algorithms (A128KW, A256KW, dir, etc.): uses secret
   *   <li>For asymmetric algorithms (RSA1_5, RSA-OAEP, ECDH-ES, etc.): uses publicKeys
   * </ul>
   *
   * @param jweAlgorithm the JWE algorithm
   * @param publicKeys the client's public keys (JWKS format), used for asymmetric encryption
   * @param secret the client_secret for key derivation, used for symmetric encryption
   */
  public JsonWebEncrypterFactory(String jweAlgorithm, String publicKeys, String secret) {
    this.jweAlgorithm = jweAlgorithm;
    this.publicKeys = publicKeys;
    this.secret = secret;
  }

  /**
   * Creates the appropriate JWE encrypter based on the algorithm.
   *
   * @return the JWE encrypter
   * @throws JsonWebKeyInvalidException if key is invalid
   * @throws JOSEException if encrypter creation fails
   */
  public JWEEncrypter create() throws JsonWebKeyInvalidException, JOSEException {
    JWEAlgorithm algorithm = JWEAlgorithm.parse(jweAlgorithm);

    if (SymmetricJweAlgorithms.contains(algorithm)) {
      return createSymmetricEncrypter(algorithm);
    }
    return createAsymmetricEncrypter(algorithm);
  }

  /**
   * Creates an encrypter for symmetric key algorithms.
   *
   * <p>OpenID Connect Core 1.0 Section 10.2 specifies that symmetric encryption keys are derived
   * from the client_secret value.
   *
   * @param algorithm the JWE algorithm
   * @return the JWE encrypter
   * @throws JOSEException if encrypter creation fails
   */
  private JWEEncrypter createSymmetricEncrypter(JWEAlgorithm algorithm) throws JOSEException {
    if (secret == null || secret.isEmpty()) {
      throw new JOSEException(
          "client_secret is required for symmetric JWE encryption but was not provided");
    }

    SecretKey secretKey = deriveSecretKey(algorithm);

    if (algorithm.equals(JWEAlgorithm.DIR)) {
      return new DirectEncrypter(secretKey);
    }
    return new AESEncrypter(secretKey);
  }

  /**
   * Derives a secret key from client_secret for the given algorithm.
   *
   * <p>OpenID Connect Core 1.0 Section 16.19 specifies minimum key lengths: - A128KW: 16 octets
   * (128 bits) - A192KW: 24 octets (192 bits) - A256KW/dir: 32 octets (256 bits)
   *
   * @param algorithm the JWE algorithm
   * @return the derived secret key
   */
  private SecretKey deriveSecretKey(JWEAlgorithm algorithm) {
    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    int keyLength = SymmetricJweAlgorithms.getRequiredKeyLength(algorithm);

    byte[] keyBytes = new byte[keyLength];
    System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, keyLength));

    return new SecretKeySpec(keyBytes, "AES");
  }

  /**
   * Creates an encrypter for asymmetric key algorithms.
   *
   * @param algorithm the JWE algorithm
   * @return the JWE encrypter
   * @throws JsonWebKeyInvalidException if key is invalid
   * @throws JOSEException if encrypter creation fails
   */
  private JWEEncrypter createAsymmetricEncrypter(JWEAlgorithm algorithm)
      throws JsonWebKeyInvalidException, JOSEException {
    JsonWebKeys jsonWebKeys = JwkParser.parseKeys(publicKeys);
    JsonWebKey jsonWebKey = jsonWebKeys.findByAlgorithm(jweAlgorithm);

    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    switch (jsonWebKeyType) {
      case EC -> {
        return new ECDHEncrypter((ECPublicKey) jsonWebKey.toPublicKey());
      }
      case RSA -> {
        return new RSAEncrypter((RSAPublicKey) jsonWebKey.toPublicKey());
      }
      default -> {
        throw new UnSupportedException(
            String.format("unsupported encryption key type (%s)", jsonWebKeyType.name()));
      }
    }
  }
}
