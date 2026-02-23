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

package org.idp.server.control_plane.management.tenant.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.jose.JwksGenerationResult;
import org.idp.server.platform.jose.JwksGenerator;
import org.idp.server.platform.json.JsonConverter;

public class AuthorizationServerConfigurationJwksEnricher {

  private AuthorizationServerConfigurationJwksEnricher() {}

  @SuppressWarnings("unchecked")
  public static AuthorizationServerConfiguration enrich(Object authorizationServerObject) {
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

    if (authorizationServerObject == null) {
      authorizationServerObject = new HashMap<String, Object>();
    }

    Map<String, Object> map;
    if (authorizationServerObject instanceof Map) {
      map = new HashMap<>((Map<String, Object>) authorizationServerObject);
    } else {
      map = new HashMap<>(jsonConverter.read(authorizationServerObject, Map.class));
    }

    Object jwksValue = map.get("jwks");
    if (jwksValue == null || (jwksValue instanceof String s && s.isEmpty())) {
      JwksGenerationResult result = JwksGenerator.generateRS256();
      map.put("jwks", result.jwksJson());

      Map<String, Object> extension;
      Object extensionObj = map.get("extension");
      if (extensionObj instanceof Map) {
        extension = new HashMap<>((Map<String, Object>) extensionObj);
      } else {
        extension = new HashMap<>();
      }

      Object tokenKeyId = extension.get("token_signed_key_id");
      if (tokenKeyId == null || (tokenKeyId instanceof String s && s.isEmpty())) {
        extension.put("token_signed_key_id", result.keyId());
      }

      Object idTokenKeyId = extension.get("id_token_signed_key_id");
      if (idTokenKeyId == null || (idTokenKeyId instanceof String s && s.isEmpty())) {
        extension.put("id_token_signed_key_id", result.keyId());
      }

      map.put("extension", extension);
    }

    return jsonConverter.read(map, AuthorizationServerConfiguration.class);
  }
}
