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
import com.nimbusds.jose.util.Base64URL;
import java.text.ParseException;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;

/**
 * JoseAlgorithmAnalyzer
 *
 * <p>Analyzes JOSE (JWS/JWE) tokens to determine their algorithm characteristics.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518">RFC 7518 - JWA</a>
 */
public class JoseAlgorithmAnalyzer {

  /**
   * Analyzes a JOSE token and returns its algorithm type.
   *
   * @param jose the JOSE token string
   * @return the algorithm type
   * @throws JoseInvalidException if parsing fails
   */
  public static JoseAlgorithmType analyze(String jose) throws JoseInvalidException {
    try {
      String headerValue = jose.split("\\.")[0];
      Base64URL header = new Base64URL(headerValue);
      JsonConverter jsonConverter = JsonConverter.defaultInstance();
      Map<String, Object> headerPayload = jsonConverter.read(header.decodeToString(), Map.class);
      Algorithm alg = Header.parseAlgorithm(headerPayload);

      if (alg.equals(Algorithm.NONE)) {
        return JoseAlgorithmType.UNSIGNED;
      } else if (alg instanceof JWSAlgorithm) {
        return JoseAlgorithmType.SIGNATURE;
      } else if (alg instanceof JWEAlgorithm) {
        JWEAlgorithm jweAlgorithm = (JWEAlgorithm) alg;
        if (SymmetricJweAlgorithms.contains(jweAlgorithm)) {
          return JoseAlgorithmType.SYMMETRIC_ENCRYPTION;
        } else {
          return JoseAlgorithmType.ASYMMETRIC_ENCRYPTION;
        }
      } else {
        throw new JoseInvalidException("Unexpected algorithm type: " + alg);
      }
    } catch (ParseException e) {
      throw new JoseInvalidException("parse failed, invalid JOSE header", e);
    }
  }
}
