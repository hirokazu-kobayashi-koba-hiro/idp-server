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

package org.idp.server.core.openid.oauth.logout;

import org.idp.server.platform.jose.JoseAlgorithmAnalyzer;
import org.idp.server.platform.jose.JoseAlgorithmType;
import org.idp.server.platform.jose.JoseInvalidException;

/**
 * IdTokenHintPattern
 *
 * <p>Identifies the pattern of id_token_hint for RP-Initiated Logout processing.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public enum IdTokenHintPattern {

  /** Signed JWT (JWS) - can be verified by OP */
  JWS,

  /** Symmetrically encrypted JWT (JWE) - can be decrypted by OP using client_secret */
  SYMMETRIC_JWE,

  /** Asymmetrically encrypted JWT (JWE) - cannot be decrypted by OP */
  ASYMMETRIC_JWE;

  /**
   * Parses id_token_hint and determines its pattern.
   *
   * @param idTokenHint the id_token_hint value
   * @return the identified pattern
   * @throws JoseInvalidException if parsing fails
   */
  public static IdTokenHintPattern parse(String idTokenHint) throws JoseInvalidException {
    JoseAlgorithmType algorithmType = JoseAlgorithmAnalyzer.analyze(idTokenHint);

    switch (algorithmType) {
      case UNSIGNED:
      case SIGNATURE:
        return JWS;
      case SYMMETRIC_ENCRYPTION:
        return SYMMETRIC_JWE;
      case ASYMMETRIC_ENCRYPTION:
        return ASYMMETRIC_JWE;
      default:
        throw new JoseInvalidException("Unexpected algorithm type: " + algorithmType);
    }
  }

  public boolean isJws() {
    return this == JWS;
  }

  public boolean isSymmetricJwe() {
    return this == SYMMETRIC_JWE;
  }

  public boolean isAsymmetricJwe() {
    return this == ASYMMETRIC_JWE;
  }
}
