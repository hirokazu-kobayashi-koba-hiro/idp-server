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

package org.idp.server.core.openid.discovery;

import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JwkParser;

public class JwksResponseCreator {

  AuthorizationServerConfiguration authorizationServerConfiguration;

  public JwksResponseCreator(AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public Map<String, Object> create() {
    try {
      String jwks = authorizationServerConfiguration.jwks();
      return JwkParser.parsePublicKeys(jwks);
    } catch (JsonWebKeyInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
