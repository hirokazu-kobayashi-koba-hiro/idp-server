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
import org.idp.server.core.oidc.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.plugin.AccessTokenCustomClaimsCreator;
import org.idp.server.core.oidc.type.oauth.Scopes;
import org.idp.server.platform.json.JsonNodeWrapper;

public class AccessTokenSelectiveVerifiedClaimsCreator implements AccessTokenCustomClaimsCreator {

  private static final String prefix = "verified_claims:";

  @Override
  public boolean shouldCreate(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    if (!authorizationServerConfiguration.enabledAccessTokenSelectiveVerifiedClaims()) {
      return false;
    }

    Scopes scopes = authorizationGrant.scopes();
    if (!scopes.hasScopeMatchedPrefix(prefix)) {
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

    Scopes scopes = authorizationGrant.scopes();
    Scopes filteredScopes = scopes.filterMatchedPrefix(prefix);
    User user = authorizationGrant.user();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();

    Map<String, Object> verified = new HashMap<>();
    Map<String, Object> verifiedClaims = new HashMap<>();
    Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();

    for (String scope : filteredScopes) {
      String claimName = scope.substring(prefix.length());

      if (userClaims.containsKey(claimName)) {
        verifiedClaims.put(claimName, userClaims.get(claimName));
      }
    }

    verified.put("verified_claims", verifiedClaims);

    return verified;
  }
}
