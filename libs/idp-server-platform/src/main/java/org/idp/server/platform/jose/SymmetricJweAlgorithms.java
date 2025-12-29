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

import com.nimbusds.jose.JWEAlgorithm;
import java.util.Set;

/**
 * SymmetricJweAlgorithms
 *
 * <p>Defines symmetric key management algorithms for JWE. These algorithms use keys derived from a
 * shared secret (e.g., client_secret).
 *
 * <p>RFC 7518 Section 4.1 classifies key management algorithms:
 *
 * <ul>
 *   <li>Key Wrapping (A128KW, A192KW, A256KW, A128GCMKW, A192GCMKW, A256GCMKW)
 *   <li>Direct Key Agreement (dir)
 * </ul>
 *
 * <p>OpenID Connect Core 1.0 Section 10.2 specifies that symmetric encryption keys for ID Token
 * encryption are derived from the client_secret value.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518#section-4.1">RFC 7518 Section 4.1</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Encryption">OIDC Core Section
 *     10.2</a>
 */
public class SymmetricJweAlgorithms {

  private static final Set<JWEAlgorithm> VALUES =
      Set.of(
          JWEAlgorithm.A128KW,
          JWEAlgorithm.A192KW,
          JWEAlgorithm.A256KW,
          JWEAlgorithm.A128GCMKW,
          JWEAlgorithm.A192GCMKW,
          JWEAlgorithm.A256GCMKW,
          JWEAlgorithm.DIR);

  private SymmetricJweAlgorithms() {}

  /**
   * Returns all symmetric JWE algorithms.
   *
   * @return the set of symmetric JWE algorithms
   */
  public static Set<JWEAlgorithm> values() {
    return VALUES;
  }

  /**
   * Checks if the specified algorithm is a symmetric JWE algorithm.
   *
   * @param algorithm the JWE algorithm to check
   * @return true if symmetric, false otherwise
   */
  public static boolean contains(JWEAlgorithm algorithm) {
    return VALUES.contains(algorithm);
  }

  /**
   * Gets the required key length in bytes for the algorithm.
   *
   * <p>OpenID Connect Core 1.0 Section 16.19 specifies minimum key lengths:
   *
   * <ul>
   *   <li>A128KW, A128GCMKW: 16 octets (128 bits)
   *   <li>A192KW, A192GCMKW: 24 octets (192 bits)
   *   <li>A256KW, A256GCMKW, dir: 32 octets (256 bits)
   * </ul>
   *
   * @param algorithm the JWE algorithm
   * @return the key length in bytes
   */
  public static int getRequiredKeyLength(JWEAlgorithm algorithm) {
    if (algorithm.equals(JWEAlgorithm.A128KW) || algorithm.equals(JWEAlgorithm.A128GCMKW)) {
      return 16; // 128 bits
    } else if (algorithm.equals(JWEAlgorithm.A192KW) || algorithm.equals(JWEAlgorithm.A192GCMKW)) {
      return 24; // 192 bits
    } else if (algorithm.equals(JWEAlgorithm.A256KW)
        || algorithm.equals(JWEAlgorithm.A256GCMKW)
        || algorithm.equals(JWEAlgorithm.DIR)) {
      return 32; // 256 bits
    }
    return 32; // default to 256 bits
  }
}
