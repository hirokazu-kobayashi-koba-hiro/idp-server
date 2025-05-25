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

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.Header;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.Base64URL;
import java.text.ParseException;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;

/** JoseType */
public enum JoseType {
  plain,
  signature,
  encryption;

  public static JoseType parse(String jose) throws JoseInvalidException {
    try {
      String headerValue = jose.split("\\.")[0];
      Base64URL header = new Base64URL(headerValue);
      JsonConverter jsonConverter = JsonConverter.defaultInstance();
      Map<String, Object> headerPayload = jsonConverter.read(header.decodeToString(), Map.class);
      Algorithm alg = Header.parseAlgorithm(headerPayload);

      if (alg.equals(Algorithm.NONE)) {
        return plain;
      } else if (alg instanceof JWSAlgorithm) {
        return signature;
      } else if (alg instanceof JWEAlgorithm) {
        return encryption;
      } else {
        throw new JoseInvalidException("Unexpected algorithm type: " + alg);
      }
    } catch (ParseException e) {
      throw new JoseInvalidException("parse failed, invalid jose header", e);
    }
  }

  public boolean isPlain() {
    return this == plain;
  }
}
