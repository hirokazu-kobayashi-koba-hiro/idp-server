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

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.util.UUID;

public class JwksGenerator {

  private JwksGenerator() {}

  public static JwksGenerationResult generateRS256() {
    try {
      String kid = UUID.randomUUID().toString();
      RSAKey rsaKey =
          new RSAKeyGenerator(2048)
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(new Algorithm("RS256"))
              .keyID(kid)
              .generate();
      JWKSet jwkSet = new JWKSet(rsaKey);
      String jwksJson = JSONObjectUtils.toJSONString(jwkSet.toJSONObject(false));
      return new JwksGenerationResult(jwksJson, kid);
    } catch (JOSEException e) {
      throw new RuntimeException("Failed to generate RS256 key pair", e);
    }
  }
}
