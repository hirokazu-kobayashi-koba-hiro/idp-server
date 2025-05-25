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

package org.idp.server.core.extension.identity.verified;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.plugin.AccessTokenCustomClaimsCreator;
import org.idp.server.platform.json.JsonNodeWrapper;

public class AccessTokenVerifiedClaimsCreator implements AccessTokenCustomClaimsCreator {

  @Override
  public boolean shouldCreate(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    if (!authorizationServerConfiguration.enabledAccessTokenVerifiedClaims()) {
      return false;
    }

    User user = authorizationGrant.user();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
    return user.hasVerifiedClaims() && userVerifiedClaims.contains("claims");
  }

  @Override
  public Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    HashMap<String, Object> map = new HashMap<>();
    User user = authorizationGrant.user();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();

    Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();
    map.put("verified_claims", userClaims);

    return map;
  }
}
