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

package org.idp.server.security.event.hook.ssf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.platform.date.SystemDateTime;

public class SecurityEventTokenCreator {

  SecurityEventTokenEntity securityEventTokenEntity;
  String privateKey;

  public SecurityEventTokenCreator(
      SecurityEventTokenEntity securityEventTokenEntity, String privateKey) {
    this.securityEventTokenEntity = securityEventTokenEntity;
    this.privateKey = privateKey;
  }

  public SecurityEventToken create() {

    try {
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();

      Map<String, Object> claims = new HashMap<>();
      claims.put("iss", securityEventTokenEntity.issuerValue());
      claims.put("jti", UUID.randomUUID().toString());
      claims.put("iat", SystemDateTime.now().toEpochSecond(SystemDateTime.zoneOffset));
      claims.put("aud", securityEventTokenEntity.clientIdValue());
      claims.put("events", securityEventTokenEntity.eventAsMap());

      Map<String, Object> headers = new HashMap<>();

      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(claims, headers, privateKey);
      String jws = jsonWebSignature.serialize();

      return new SecurityEventToken(jws);
    } catch (JsonWebKeyInvalidException e) {
      throw new SecurityEventTokenCreationFailedException(
          "security event token creation is failed.", e);
    }
  }
}
