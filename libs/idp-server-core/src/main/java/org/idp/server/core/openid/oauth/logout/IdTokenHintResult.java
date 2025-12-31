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

import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * IdTokenHintResult
 *
 * <p>Result of id_token_hint processing. Contains parsed claims and resolved client configuration.
 */
public class IdTokenHintResult {

  private final JsonWebTokenClaims claims;
  private final ClientConfiguration clientConfiguration;

  public IdTokenHintResult(JsonWebTokenClaims claims, ClientConfiguration clientConfiguration) {
    this.claims = claims;
    this.clientConfiguration = clientConfiguration;
  }

  public JsonWebTokenClaims claims() {
    return claims;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }
}
